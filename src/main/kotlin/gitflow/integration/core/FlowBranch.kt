package gitflow.integration.core

/**
 * A git flow branch.
 *
 * @param type     kind of flow branch
 * @param name     short name, without the type prefix
 * @param fullName complete branch name, including the prefix
 * @param local    exists as a local branch
 * @param remote   exists on a remote
 * @param current  is the checked-out branch
 */
data class FlowBranch(
    val type: FlowType,
    val name: String,
    val fullName: String,
    val local: Boolean,
    val remote: Boolean,
    val current: Boolean,
)
