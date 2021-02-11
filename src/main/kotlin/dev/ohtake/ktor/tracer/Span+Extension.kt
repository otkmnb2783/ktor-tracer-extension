package dev.ohtake.ktor.tracer

import io.opencensus.trace.*

fun Span.setStatus(statusCode: Int) {
    when (statusCode) {
        in 200..399 -> setStatus(Status.OK)
        400 -> setStatus(Status.INVALID_ARGUMENT)
        504 -> setStatus(Status.DEADLINE_EXCEEDED)
        404 -> setStatus(Status.NOT_FOUND)
        403 -> setStatus(Status.PERMISSION_DENIED)
        401 -> setStatus(Status.UNAUTHENTICATED)
        429 -> setStatus(Status.RESOURCE_EXHAUSTED)
        500 -> setStatus(Status.INTERNAL)
        501 -> setStatus(Status.UNIMPLEMENTED)
        503 -> setStatus(Status.UNAVAILABLE)
        else -> setStatus(Status.UNKNOWN)
    }
}

fun Span.messageEvent(event: MessageEvent) {
    addMessageEvent(event)
}

fun Span.attribute(key: String, value: String?) {
    if (value.isNullOrEmpty()) return
    putAttribute(key, AttributeValue.stringAttributeValue(value))
}

fun Span.attribute(key: String, value: Int?) {
    if (value == null) return
    putAttribute(key, AttributeValue.longAttributeValue(value.toLong()))
}

fun Span.annotation(description: String, attributions: Map<String, String>?) {
    if (attributions.isNullOrEmpty()) return
    addAnnotation(
        description,
        attributions.map { it.key to AttributeValue.stringAttributeValue(it.value) }.toMap()
    )
}
