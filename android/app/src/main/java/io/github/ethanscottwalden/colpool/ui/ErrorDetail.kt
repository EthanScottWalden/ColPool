package io.github.ethanscottwalden.colpool.ui

import io.github.ethanscottwalden.colpool.BuildConfig

/**
 * A short technical detail line for unexpected errors, shown in debug builds only so developers can see
 * what went wrong. Release builds never show it ([forDisplay] returns null). It never contains the
 * email or password: anything that looks like an email, and any value in `secrets`, is replaced.
 */
object ErrorDetail {
    private const val MAX_MESSAGE = 200
    private val EMAIL = Regex("[^\\s@<>\"',;:()]+@[^\\s@<>\"',;:()]+")

    /** `ClassName: message` (class simple name only), redacted and shortened. */
    fun format(e: Throwable, secrets: List<String> = emptyList()): String {
        val name = e.javaClass.simpleName.ifEmpty { "Exception" }
        var message = e.message?.trim().orEmpty()
        for (s in secrets.filter { it.isNotBlank() }.sortedByDescending { it.length }) {
            message = message.replace(s, "[redacted]", ignoreCase = true)
        }
        message = EMAIL.replace(message, "[email]").replace(Regex("\\s+"), " ")
        if (message.length > MAX_MESSAGE) message = message.take(MAX_MESSAGE) + "..."
        return if (message.isEmpty()) name else "$name: $message"
    }

    /** The detail line in debug builds, null in release builds. [enabled] is injectable for tests. */
    fun forDisplay(e: Throwable, secrets: List<String> = emptyList(), enabled: Boolean = BuildConfig.DEBUG): String? =
        if (enabled) format(e, secrets) else null
}
