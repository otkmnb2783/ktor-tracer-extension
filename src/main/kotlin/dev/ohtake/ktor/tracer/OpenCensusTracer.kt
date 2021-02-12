package dev.ohtake.ktor.tracer

import com.sun.org.apache.xpath.internal.operations.*
import io.ktor.application.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.routing.*
import io.ktor.util.*
import io.ktor.util.pipeline.*
import io.opencensus.contrib.http.util.*
import io.opencensus.trace.*
import io.opencensus.trace.propagation.*
import io.opencensus.trace.samplers.*

class OpenCensusTracer(
    private val filter: (ApplicationCall) -> Boolean,
    private val spanNameHandler: (ApplicationCall, RoutingPath) -> String,
    private var requestAttributeHandler: ((ApplicationCall, Span) -> Unit)?,
    private val responseAttributeHandler: ((ApplicationCall, Span) -> Unit)?,
    private val loggingRequestBody: Boolean
) {

    class Configuration {
        var sampler: Sampler = Samplers.alwaysSample()
        var loggingRequestBody = false
        var filter: (ApplicationCall) -> Boolean = { call: ApplicationCall ->
            val path = call.request.path()
            when {
                path.contains("/metrics") -> false
                else -> true
            }
        }
        var spanNameHandler: (ApplicationCall, RoutingPath) -> String = { call: ApplicationCall, path: RoutingPath ->
            "${call.request.httpMethod.value} /${path.parts.map { it.value }.firstOrNull() ?: ""}"
        }
        var requestAttributeHandler: ((ApplicationCall, Span) -> Unit)? = null
        var responseAttributeHandler: ((ApplicationCall, Span) -> Unit)? = null
    }

    private fun shouldTracking(call: ApplicationCall): Boolean {
        return filter(call)
    }

    private suspend fun intercept(
        context: PipelineContext<Unit, ApplicationCall>,
        traceSampler: Sampler
    ) {
        val tracer = Tracing.getTracer()
        val textFormat = Tracing.getPropagationComponent().traceContextFormat
        val call = context.call

        val spanContext = try {
            textFormat.extract(
                call.request,
                KtorTextFormatGetter
            )
        } catch (e: SpanContextParseException) {
            null
        }

        val path = RoutingPath.parse(call.request.path())

        tracer.spanBuilderWithRemoteParent(spanNameHandler(call, path), spanContext).apply {
            setSpanKind(Span.Kind.SERVER)
            setRecordEvents(true)
            setSampler(traceSampler)
        }.startScopedSpan()
            .use {
                val span = tracer.currentSpan
                span.messageEvent(
                    event = MessageEvent.builder(
                        MessageEvent.Type.SENT,
                        System.currentTimeMillis()
                    ).setUncompressedMessageSize(
                        call.request.header("content-length")?.toLong() ?: 0
                    ).build()
                )
                try {
                    setRequestAttribute(call, span)
                    context.proceed()
                } finally {
                    setResponseAttributes(call, span)
                    span.messageEvent(
                        event = MessageEvent.builder(
                            MessageEvent.Type.RECEIVED,
                            System.currentTimeMillis()
                        ).setUncompressedMessageSize(
                            call.response.headers["content-length"]?.toLong() ?: 0
                        ).build()
                    )
                }
            }
    }

    private suspend fun setRequestAttribute(call: ApplicationCall, span: Span) {
        span.attribute(HttpTraceAttributeConstants.HTTP_HOST, call.request.host())
        span.attribute(
            HttpTraceAttributeConstants.HTTP_METHOD,
            call.request.httpMethod.value
        )
        span.attribute(HttpTraceAttributeConstants.HTTP_PATH, call.request.path().replace("//", "/"))
        span.attribute(
            HttpTraceAttributeConstants.HTTP_USER_AGENT,
            call.request.userAgent() ?: "NoAgent"
        )
        span.attribute(HttpTraceAttributeConstants.HTTP_URL, call.request.uri)
        if (loggingRequestBody) {
            span.attribute("http.request_body", String(call.receive<ByteArray>()))
        }
        requestAttributeHandler?.invoke(call, span)
    }

    private fun setResponseAttributes(call: ApplicationCall, span: Span) {
        val status = call.response.status()?.value ?: 0
        span.setStatus(status)
        span.attribute(HttpTraceAttributeConstants.HTTP_STATUS_CODE, status)
        span.attribute(HttpTraceAttributeConstants.HTTP_STATUS_CODE, call.response.status()?.value)
        responseAttributeHandler?.invoke(call, span)
    }

    companion object Feature : ApplicationFeature<Application, Configuration, OpenCensusTracer> {

        override val key: AttributeKey<OpenCensusTracer> = AttributeKey(OpenCensusTracer::class.java.simpleName)
        val routeKey = AttributeKey<Route>("Route")

        override fun install(
            pipeline: Application,
            configure: Configuration.() -> Unit
        ): OpenCensusTracer {
            val configuration = Configuration().apply(configure)

            if (configuration.loggingRequestBody && pipeline.featureOrNull(DoubleReceive) == null) {
                throw IllegalStateException("Logging payloads requires DoubleReceive feature to be installed")
            }

            val feature = OpenCensusTracer(
                filter = configuration.filter,
                spanNameHandler = configuration.spanNameHandler,
                requestAttributeHandler = configuration.requestAttributeHandler,
                responseAttributeHandler = configuration.responseAttributeHandler,
                loggingRequestBody = configuration.loggingRequestBody
            )
            pipeline.intercept(ApplicationCallPipeline.Call) {
                if (!feature.shouldTracking(call)) return@intercept
                feature.intercept(this, configuration.sampler)
            }
            pipeline.environment.monitor.subscribe(Routing.RoutingCallStarted) {
                it.attributes.computeIfAbsent(routeKey) { it.route }
            }
            return feature
        }
    }
}

object KtorTextFormatGetter : TextFormat.Getter<ApplicationRequest>() {
    override fun get(carrier: ApplicationRequest, key: String): String? {
        return carrier.headers[key]
    }
}

fun Route.function(): String {
    val selectors = mutableListOf<RouteSelector>()
    var p = parent
    while (p != null) {
        val s = p.selector
        if (s.isSupport()) selectors.add(s)
        p = p.parent
    }
    return "/${selectors.toList().reversed().joinToString("/")}"
}

fun RouteSelector.isSupport(): Boolean {
    return when (this) {
        is PathSegmentConstantRouteSelector,
        is PathSegmentParameterRouteSelector,
        is PathSegmentOptionalParameterRouteSelector,
        is PathSegmentWildcardRouteSelector,
        is PathSegmentTailcardRouteSelector,
        is OrRouteSelector,
        is AndRouteSelector -> true
        else -> false
    }
}

