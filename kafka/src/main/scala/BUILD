scala_library(
    sources = rglobs("*.scala"),
    compiler_option_sets = {"fatal_warnings"},
    provides = scala_artifact(
        org = "com.twitter",
        name = "finatra-kafka",
        repo = artifactory,
    ),
    strict_deps = False,
    dependencies = [
        "3rdparty/jvm/org/apache/kafka:kafka-clients",
        "finagle/finagle-core/src/main/scala",
        "finatra/inject/inject-core",
        "finatra/inject/inject-slf4j",
        "finatra/inject/inject-utils",
        "finatra/kafka/src/main/java",
        "finatra/utils",
        "scrooge/scrooge-serializer/src/main/scala",
        "util/util-codec/src/main/scala",
    ],
    exports = [
        "3rdparty/jvm/org/apache/kafka:kafka-clients",
        "finagle/finagle-core/src/main/scala",
        "finatra/inject/inject-core",
        "finatra/inject/inject-slf4j",
        "finatra/inject/inject-utils",
        "finatra/utils",
        "scrooge/scrooge-serializer/src/main/scala",
        "util/util-codec/src/main/scala",
    ],
)
