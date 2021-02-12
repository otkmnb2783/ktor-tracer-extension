# ktor-tracer-extension

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![Jitpack](https://jitpack.io/v/otkmnb2783/ktor-tracer-extension.svg)](https://jitpack.io/#otkmnb2783/ktor-tracer-extension)

This ktor-tracer-extension is an extension that allows tracing of Ktor requests through OpenCensus.

## Installation

Step 1. Add the [JitPack](https://jitpack.io/) repository to your build file

```kotlin
repositories {
    ...
    maven(url = "https://jitpack.io")
}
```

Step 2. Add the dependency

```kotlin
implementation("ch.qos.logback:logback-classic:$logbackVersion")
implementation("io.ktor:ktor-server-core:$ktorVersion")
implementation("io.opencensus:opencensus-api:$opencensusVersion")
implementation("io.opencensus:opencensus-exporter-trace-jaeger:$opencensusVersion")
implementation("io.opencensus:opencensus-exporter-trace-util:$opencensusVersion")
implementation("io.opencensus:opencensus-contrib-http-util:$opencensusVersion")
runtimeOnly("io.opencensus:opencensus-impl:$opencensusVersion")
implementation("com.github.otkmnb2783:ktor-tracer-extension:<this_library_current_version>")
```

## Usage

```kotlin

fun main(args: Array<String>): Unit = EngineMain.main(args)

@OptIn(KtorExperimentalLocationsAPI::class)
@Suppress("unused", "UNUSED_PARAMETER")
@kotlin.jvm.JvmOverloads
fun Application.module(testing: Boolean = false) {
    install(DoubleReceive)

    install(OpenCensusTracer) {
        loggingRequestBody = true
        filter = { call ->
            val path = call.request.path()
            when {
                path.contains("/health-check") ||
                    path.contains("/metrics") -> false
                else -> true
            }
        }
        requestAttributeHandler = { call: ApplicationCall, span: Span ->
            span.attribute("user", "xxxxxx")
        }
        responseAttributeHandler = { call: ApplicationCall, span: Span ->
            span.attribute("http.api", call.attributes.getOrNull(OpenCensusTracer.routeKey)?.function() ?: "")
        }
    }
}
```
