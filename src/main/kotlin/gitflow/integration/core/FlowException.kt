package gitflow.integration.core

/** Aborts a git flow operation; carries what to tell the user. Never serialized. */
internal class FlowException private constructor(
    val msg: Msg,
    val detail: String,
    val isConflict: Boolean,
) : Exception(msg.toString(), null, false, false) {

    companion object {
        fun of(key: String, vararg args: Any?): FlowException = FlowException(Msg(key, *args), "", false)

        fun of(msg: Msg): FlowException = FlowException(msg, "", false)

        fun withDetail(detail: String, key: String, vararg args: Any?): FlowException =
            FlowException(Msg(key, *args), detail, false)

        /** A git command failed. */
        fun git(args: List<String>, result: GitResult): FlowException {
            val shown = args.take(4).joinToString(" ")
            return FlowException(Msg("error.git", shown), result.message, false)
        }

        /** A merge or rebase stopped on conflicts; [files] lists them. */
        fun conflict(files: String, key: String, vararg args: Any?): FlowException =
            FlowException(Msg(key, *args), files, true)
    }
}
