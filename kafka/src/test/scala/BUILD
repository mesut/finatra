scala_library(
    name = "test-deps",
    sources = globs(
        "com/twitter/finatra/kafka/test/*.scala",
        "com/twitter/finatra/kafka/test/utils/*.scala",
    ),
    compiler_option_sets = {"fatal_warnings"},
    provides = scala_artifact(
        org = "com.twitter",
        name = "finatra-kafka-tests",
        repo = artifactory,
    ),
    strict_deps = False,
    dependencies = [
        "3rdparty/jvm/com/google/inject:guice",
        "3rdparty/jvm/junit",
        "3rdparty/jvm/org/apache/kafka",
        "3rdparty/jvm/org/apache/kafka:kafka-clients",
        "3rdparty/jvm/org/apache/kafka:kafka-clients-test",
        "3rdparty/jvm/org/apache/kafka:kafka-streams-test",
        "3rdparty/jvm/org/apache/kafka:kafka-test",
        "3rdparty/jvm/org/apache/zookeeper:zookeeper-client",
        "3rdparty/jvm/org/scalatest",
        "finatra/http/src/test/scala:test-deps",
        "finatra/inject/inject-core/src/main/scala",
        "finatra/inject/inject-core/src/test/scala:test-deps",
        "finatra/inject/inject-server/src/test/scala:test-deps",
        "finatra/inject/inject-slf4j/src/main/scala",
        "finatra/jackson/src/main/scala",
        "finatra/kafka/src/main/scala",
        "finatra/kafka/src/test/thrift:thrift-scala",
        "util/util-slf4j-api/src/main/scala",
    ],
    excludes = [
        exclude(
            org = "com.twitter",
            name = "twitter-server-internal-naming_2.11",
        ),
        exclude(
            org = "com.twitter",
            name = "loglens-log4j-logging_2.11",
        ),
        exclude(
            org = "log4j",
            name = "log4j",
        ),
    ],
    exports = [
        "3rdparty/jvm/com/google/inject:guice",
        "3rdparty/jvm/junit",
        "3rdparty/jvm/org/apache/kafka",
        "3rdparty/jvm/org/apache/kafka:kafka-clients",
        "3rdparty/jvm/org/apache/kafka:kafka-clients-test",
        "3rdparty/jvm/org/apache/kafka:kafka-streams-test",
        "3rdparty/jvm/org/apache/kafka:kafka-test",
        "3rdparty/jvm/org/scalatest",
        "finatra/inject/inject-core/src/main/scala",
        "finatra/inject/inject-core/src/test/scala:test-deps",
        "finatra/inject/inject-server/src/test/scala:test-deps",
        "finatra/inject/inject-slf4j/src/main/scala",
        "finatra/kafka/src/main/scala",
        "util/util-slf4j-api/src/main/scala",
    ],
)

junit_tests(
    sources = rglobs("*.scala"),
    compiler_option_sets = {"fatal_warnings"},
    strict_deps = False,
    dependencies = [
        ":test-deps",
        "3rdparty/jvm/org/apache/zookeeper:zookeeper-server",
    ],
)
