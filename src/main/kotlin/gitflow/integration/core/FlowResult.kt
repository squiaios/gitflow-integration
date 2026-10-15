package gitflow.integration.core

/**
 * Outcome of a git flow operation.
 *
 * Even a failed operation reports the [steps] that completed before the failure, so the
 * user can see how far it got. A [Status.CONFLICT] leaves the repository mid-merge (or
 * mid-rebase): resolve the conflicts, then run the same operation again to resume it.
 */
class FlowResult internal constructor(
    val status: Status,
    steps: List<Msg>,
    warnings: List<Msg>,
    /** Why the operation stopped; `null` on success. */
    val error: Msg?,
    /** Raw git output or the list of conflicting files; empty when there is nothing to add. */
    val detail: String,
) {

    enum class Status { SUCCESS, CONFLICT, FAILED }

    /** What was done, in order. */
    val steps: List<Msg> = steps.toList()

    /** Non-fatal problems worth the user's attention. */
    val warnings: List<Msg> = warnings.toList()

    val isSuccess: Boolean
        get() = status == Status.SUCCESS

    val isConflict: Boolean
        get() = status == Status.CONFLICT
}
