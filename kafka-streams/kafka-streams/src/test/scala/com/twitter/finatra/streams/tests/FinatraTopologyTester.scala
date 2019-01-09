package com.twitter.finatra.streams.tests

import com.github.nscala_time.time.DurationBuilder
import com.google.inject.Module
import com.twitter.conversions.DurationOps._
import com.twitter.finagle.stats.{InMemoryStatsReceiver, StatsReceiver}
import com.twitter.finatra.kafka.modules.KafkaBootstrapModule
import com.twitter.finatra.kafka.test.utils.InMemoryStatsUtil
import com.twitter.finatra.kafkastreams.KafkaStreamsTwitterServer
import com.twitter.finatra.kafkastreams.test.TestDirectoryUtils
import com.twitter.finatra.streams.converters.time._
import com.twitter.finatra.streams.flags.FinatraTransformerFlags
import com.twitter.finatra.streams.query.{
  QueryableFinatraKeyValueStore,
  QueryableFinatraWindowStore
}
import com.twitter.finatra.streams.transformer.domain.TimeWindowed
import com.twitter.finatra.streams.transformer.internal.domain.Timer
import com.twitter.inject.{AppAccessor, Injector, Logging, TwitterModule}
import com.twitter.util.Duration
import java.util.Properties
import org.apache.kafka.common.serialization.Serde
import org.apache.kafka.streams.state.KeyValueStore
import org.apache.kafka.streams.{Topology, TopologyTestDriver}
import org.joda.time.{DateTime, DateTimeUtils}

object FinatraTopologyTester {

  /**
   * FinatraTopologyTester is provides useful testing utilities integrating Kafka's
   * [[TopologyTestDriver]] and a Finatra Streams server.
   *
   * @param kafkaApplicationId    The application.id of the Kafka Streams server being tested.
   *                              The application.id is used to name the changelog topics, so we recommend
   *                              setting this value to the same value used in your production service
   * @param server                A KafkaStreamsTwitterServer containing the streams topology to be
   *                              tested.
   * @param startingWallClockTime The starting wall clock time for each individual test. Note, that
   *                              publishing a message using TopologyTesterTopic#pipeInput will use
   *                              the current mocked wall clock time unless an explicit publish time
   *                              is specified.
   * @param flags                 Additional application level flags that you may have.
   * @param thriftQueryable       Enable if your service is exposing queryable state using the
   *                              com.twitter.finatra.streams.queryable.thrift.QueryableState trait.
   * @param overrideModules       Finatra override modules which redefine production bindings.
   */
  def apply(
    kafkaApplicationId: String,
    server: KafkaStreamsTwitterServer,
    startingWallClockTime: DateTime,
    flags: Map[String, String] = Map(),
    overrideModules: Seq[Module] = Seq(),
    thriftQueryable: Boolean = false,
    finatraTransformer: Boolean = false,
    emitWatermarkPerMessage: Boolean = true,
    autoWatermarkInterval: Duration = 0.millis
  ): FinatraTopologyTester = {
    AppAccessor.callInits(server)

    AppAccessor.callParseArgs(
      server = server,
      args = kafkaStreamsArgs(
        kafkaApplicationId = kafkaApplicationId,
        otherFlags = flags,
        thriftQueryable = thriftQueryable,
        finatraTransformer = finatraTransformer,
        emitWatermarkPerMessage = emitWatermarkPerMessage,
        autoWatermarkInterval = autoWatermarkInterval
      )
    )

    val inMemoryStatsReceiver = new InMemoryStatsReceiver
    AppAccessor.callAddFrameworkOverrideModules(
      server,
      overrideModules.toList :+ new TwitterModule {
        override def configure(): Unit = {
          bind[StatsReceiver].toInstance(inMemoryStatsReceiver)
        }
      }
    )

    val injector = AppAccessor.loadAndSetInstalledModules(server)

    FinatraTopologyTester(
      properties = server.createKafkaStreamsProperties(),
      topology = server.createKafkaStreamsTopology(),
      inMemoryStatsReceiver = inMemoryStatsReceiver,
      injector = injector,
      startingWallClockTime = startingWallClockTime
    )
  }

  private def kafkaStreamsArgs(
    kafkaApplicationId: String,
    otherFlags: Map[String, String] = Map(),
    thriftQueryable: Boolean,
    finatraTransformer: Boolean,
    emitWatermarkPerMessage: Boolean,
    autoWatermarkInterval: Duration
  ): Map[String, String] = {
    val kafkaStreamsAndOtherFlags = otherFlags ++ Map(
      "kafka.application.id" -> kafkaApplicationId,
      KafkaBootstrapModule.kafkaBootstrapServers.name -> "127.0.0.1:12345",
      "kafka.state.dir" -> TestDirectoryUtils.newTempDirectory().toString
    )

    addFinatraTransformerFlags(
      flags = addThriftQueryableFlags(
        thriftQueryable = thriftQueryable,
        otherFlagsPlusKafkaStreamsRequiredFlags = kafkaStreamsAndOtherFlags
      ),
      finatraTransformer = finatraTransformer,
      emitWatermarkPerMessage = emitWatermarkPerMessage,
      autoWatermarkInterval = autoWatermarkInterval
    )
  }

  private def addThriftQueryableFlags(
    thriftQueryable: Boolean,
    otherFlagsPlusKafkaStreamsRequiredFlags: Map[String, String]
  ) = {
    if (thriftQueryable) {
      Map(
        "kafka.application.num.instances" -> "1",
        "kafka.num.queryable.partitions" -> "1",
        "kafka.current.shard" -> "0"
      ) ++ otherFlagsPlusKafkaStreamsRequiredFlags
    } else {
      otherFlagsPlusKafkaStreamsRequiredFlags
    }
  }

  private def addFinatraTransformerFlags(
    flags: Map[String, String],
    finatraTransformer: Boolean,
    emitWatermarkPerMessage: Boolean,
    autoWatermarkInterval: Duration
  ): Map[String, String] = {
    if (finatraTransformer) {
      flags ++ Map(
        FinatraTransformerFlags.AutoWatermarkInterval -> s"$autoWatermarkInterval",
        FinatraTransformerFlags.EmitWatermarkPerMessage -> s"$emitWatermarkPerMessage"
      )
    } else {
      flags
    }
  }
}

case class FinatraTopologyTester private (
  properties: Properties,
  topology: Topology,
  inMemoryStatsReceiver: InMemoryStatsReceiver,
  injector: Injector,
  startingWallClockTime: DateTime)
    extends Logging {

  private val inMemoryStatsUtil = new InMemoryStatsUtil(inMemoryStatsReceiver)
  private var _driver: TopologyTestDriver = _

  /* Public */

  def driver: TopologyTestDriver = _driver

  def topic[K, V](
    name: String,
    keySerde: Serde[K],
    valSerde: Serde[V]
  ): TopologyTesterTopic[K, V] = {
    new TopologyTesterTopic(_driver, name, keySerde, valSerde)
  }

  def getKeyValueStore[K, V](name: String): KeyValueStore[K, V] = {
    driver
      .getStateStore(name)
      .asInstanceOf[KeyValueStore[K, V]]
  }

  /**
   * Get a Finatra windowed key value store by name
   * @param name Name of the store
   * @tparam K Key type of the store
   * @tparam V Value type of the store
   * @return KeyValueStore used for time windowed keys
   */
  def getFinatraWindowedStore[K, V](name: String): KeyValueStore[TimeWindowed[K], V] = {
    getKeyValueStore[TimeWindowed[K], V](name)
  }

  /**
   * Get a Finatra timer key value store by name
   * @param name Name of the store
   * @tparam K Key type of the store
   * @tparam V Value type of the store
   * @return KeyValueStore used for timer entries
   */
  def getFinatraTimerStore[K](name: String): KeyValueStore[Timer[K], Array[Byte]] = {
    getKeyValueStore[Timer[K], Array[Byte]](name)
  }

  /**
   * Get a Finatra windowed timer store by name
   * @param name Name of the store
   * @tparam K Key type of the store
   * @tparam V Value type of the store
   * @return KeyValueStore used for time windowed timer entries
   */
  def getFinatraWindowedTimerStore[K](
    name: String
  ): KeyValueStore[Timer[TimeWindowed[K]], Array[Byte]] = {
    getFinatraTimerStore[TimeWindowed[K]](name)
  }

  def reset(): Unit = {
    close()
    createTopologyTester()
    DateTimeUtils.setCurrentMillisFixed(startingWallClockTime.getMillis)
  }

  def close(): Unit = {
    if (_driver != null) {
      _driver.close()
    }
  }

  def setWallClockTime(now: String): Unit = {
    setWallClockTime(new DateTime(now))
  }

  def setWallClockTime(now: DateTime): Unit = {
    DateTimeUtils.setCurrentMillisFixed(now.getMillis)
  }

  def now: DateTime = {
    DateTime.now()
  }

  def currentHour: DateTime = {
    now.hourOfDay().roundFloorCopy()
  }

  def currentMinute: DateTime = {
    now.minuteOfDay().roundFloorCopy()
  }

  def priorMinute: DateTime = {
    priorMinute(1)
  }

  def priorMinute(minutesBack: Int): DateTime = {
    currentMinute.minusMinutes(minutesBack)
  }

  def priorHour: DateTime = {
    priorHour(1)
  }

  def priorHour(hoursBack: Int): DateTime = {
    currentHour.minusHours(hoursBack)
  }

  def advanceWallClockTime(duration: Duration): DateTime = {
    advanceWallClockTime(duration.inMillis)
  }

  def advanceWallClockTime(duration: DurationBuilder): DateTime = {
    advanceWallClockTime(duration.toDuration.getMillis)
  }

  def queryableFinatraKeyValueStore[PK, K, V](
    storeName: String,
    primaryKeySerde: Serde[PK]
  ): QueryableFinatraKeyValueStore[PK, K, V] = {
    new QueryableFinatraKeyValueStore[PK, K, V](
      storeName,
      primaryKeySerde = primaryKeySerde,
      numShards = 1,
      numQueryablePartitions = 1,
      currentShardId = 0
    )
  }

  def queryableFinatraWindowStore[K, V](
    storeName: String,
    windowSize: Duration,
    keySerde: Serde[K]
  ): QueryableFinatraWindowStore[K, V] = {
    new QueryableFinatraWindowStore[K, V](
      storeName,
      windowSize = windowSize,
      keySerde = keySerde,
      numShards = 1,
      numQueryablePartitions = 1,
      currentShardId = 0)
  }

  def stats: InMemoryStatsUtil = inMemoryStatsUtil

  /* Private */

  private def advanceWallClockTime(durationMillis: Long): DateTime = {
    DateTimeUtils.setCurrentMillisFixed(DateTimeUtils.currentTimeMillis() + durationMillis)
    debug(s"Advance wall clock to ${DateTimeUtils.currentTimeMillis().iso8601Millis}")
    _driver.advanceWallClockTime(durationMillis)
    now
  }

  private def createTopologyTester(): Unit = {
    _driver = new TopologyTestDriver(topology, properties)
  }
}
