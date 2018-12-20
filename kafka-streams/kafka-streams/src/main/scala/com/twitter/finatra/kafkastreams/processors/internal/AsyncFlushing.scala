package com.twitter.finatra.kafkastreams.processors.internal

import com.twitter.finagle.stats.StatsReceiver
import com.twitter.finatra.kafkastreams.processors.MessageTimestamp
import com.twitter.finatra.streams.transformer.internal.{OnClose, OnInit}
import com.twitter.util.{Await, Duration, Future, Return, Throw}
import java.util.concurrent.Semaphore

/**
 * The AsyncFlushing trait allows outstanding futures to be tracked to completion when the flush()
 * method is called
 */
trait AsyncFlushing[K1, V1, K2, V2] extends Flushing with OnInit with OnClose {

  @volatile private var outstandingFutures = Future.Unit

  @volatile private var asyncFailure: Throwable = _

  private val addPermits = new Semaphore(maxOutstandingFuturesPerTask, /* fairness = */ false)

  private val outstandingFuturesGauge =
    statsReceiver.addGauge("outstandingFutures")(numOutstandingFutures)

  /* Protected */

  protected def statsReceiver: StatsReceiver

  protected def maxOutstandingFuturesPerTask: Int

  protected def flushTimeout: Duration

  protected def addFuture(
    key: K1,
    value: V1,
    future: Future[Iterable[(K2, V2, MessageTimestamp)]]
  ): Unit = {
    throwIfAsyncFailure()

    addPermits.acquire()

    outstandingFutures = outstandingFutures
      .join(future.respond {
        case Throw(t) =>
          addPermits.release()
          onFutureFailure(key, value, t)
        case Return(fr) =>
          addPermits.release()
          onFutureSuccess(key, value, fr)
      }).unit
  }

  protected def onFutureSuccess(
    key: K1,
    value: V1,
    result: Iterable[(K2, V2, MessageTimestamp)]
  ): Unit = {
    debug(s"FutureSuccess $key $value $result")
  }

  protected def onFutureFailure(key: K1, value: V1, t: Throwable): Unit = {
    error("Async asyncFailure: " + t)
    setAsyncFailure(t)
  }

  protected def setAsyncFailure(e: Throwable): Unit = {
    asyncFailure = e
  }

  override def onFlush(): Unit = {
    debug(s"Flush: Waiting on async results")
    Await.result(outstandingFutures, flushTimeout)
    outstandingFutures = Future.Unit
    assert(numOutstandingFutures == 0)
    debug(s"Finished waiting on async results")
  }

  protected def throwIfAsyncFailure(): Unit = {
    if (asyncFailure != null) {
      throw asyncFailure
    }
  }

  protected def numOutstandingFutures: Int = {
    maxOutstandingFuturesPerTask - addPermits.availablePermits
  }

  override def onClose(): Unit = {
    super.onClose()
    debug("Close")
    outstandingFuturesGauge.remove()
  }
}
