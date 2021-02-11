package dev.ohtake.example

import dev.ohtake.ktor.tracer.*
import io.ktor.application.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.jackson.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.netty.*
import io.opencensus.common.*
import io.opencensus.exporter.trace.jaeger.*
import io.opencensus.trace.*
import io.opencensus.trace.samplers.*
import org.slf4j.event.Level
import java.util.*

fun main(args: Array<String>): Unit = EngineMain.main(args)

@Suppress("unused", "UNUSED_PARAMETER")
@kotlin.jvm.JvmOverloads
fun Application.module(testing: Boolean = false) {

    val configuration = JaegerExporterConfiguration
        .builder()
        .apply {
            setServiceName("sample")
            setDeadline(Duration.create(60 * 15, 0))
            setThriftEndpoint("http://127.0.0.1:14268/api/traces")
        }.build()
    JaegerTraceExporter.createAndRegister(configuration)

    install(CallLogging) {
        level = Level.INFO
        filter { call -> call.request.path().startsWith("/") }
    }

    install(ContentNegotiation) {
        jackson()
    }

    install(DoubleReceive)

    install(OpenCensusTracer) {
        requestBody = true
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

    val controller = HelloController()

    routing {
        get("/health-check") {
            call.respond("OK")
        }
        get("/") {
            call.respond(controller.hello())
        }
        post("/customer") {
            call.respond(controller.create(customer = call.receive()))
        }
        patch("/customer/{id}") {
            val id = call.parameters["id"] ?: throw NotFoundException()
            val customer = call.receive<Customer>()
            call.respond(controller.update(customer = customer.copy(id = id)))
        }
        delete("/customer/{id}") {
            val id = call.parameters["id"] ?: throw NotFoundException()
            val customer = call.receive<Customer>()
            controller.delete(customer = customer.copy(id = id))
            call.respond(HttpStatusCode.NoContent)
        }
    }
}

fun builder(spanName: String, lambda: SpanBuilder.() -> Unit = {}): SpanBuilder {
    val span = Tracing.getTracer().currentSpan
    return Tracing.getTracer()
        .spanBuilderWithExplicitParent(spanName, span)
        .setRecordEvents(true)
        .setSampler(Samplers.alwaysSample())
        .apply {
            span.annotation("controller", mapOf())
            lambda()
        }
}

class HelloController {

    suspend fun hello(): String {
        return builder("controller#hello")
            .startScopedSpan()
            .use {
                "world!"
            }
    }

    suspend fun create(customer: Customer): Customer {
        return builder("controller#create")
            .startScopedSpan()
            .use {
                customer.copy(id = UUID.randomUUID().toString())
            }
    }

    suspend fun update(customer: Customer): Customer {
        return builder("controller#update")
            .startScopedSpan()
            .use {
                customer
            }
    }

    suspend fun delete(customer: Customer) {
        builder("controller#update")
            .startScopedSpan()
            .use {
                customer
            }
    }
}

data class Customer(
    val id: String = "",
    val firstName: String,
    val lastName: String
)

