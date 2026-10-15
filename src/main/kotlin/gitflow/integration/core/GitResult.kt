package gitflow.integration.core

/** Outcome of one git invocation. */
data class GitResult(val exitCode: Int, val stdout: String, val stderr: String) {

    val ok: Boolean
        get() = exitCode == 0

    /** Standard output without surrounding whitespace. */
    val out: String
        get() = stdout.trim()

    /** Everything git printed, for display next to an error. */
    val message: String
        get() {
            val errText = stderr.trim()
            val outText = stdout.trim()
            if (errText.isEmpty()) {
                return outText
            }
            return if (outText.isEmpty()) errText else "$outText\n$errText"
        }

    /** Non-blank lines of standard output. */
    fun lines(): List<String> = stdout.lines().map { it.trim() }.filter { it.isNotEmpty() }

    companion object {
        /** A result for a command that could not even be started. */
        fun failure(message: String): GitResult = GitResult(-1, "", message)
    }
}
