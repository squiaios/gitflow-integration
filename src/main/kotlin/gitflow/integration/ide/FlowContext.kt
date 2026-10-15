package gitflow.integration.ide

import gitflow.integration.core.FlowBranch
import gitflow.integration.core.FlowConfig
import git4idea.repo.GitRepository

/**
 * Everything the dialogs need to know about a repository, read off the EDT in one go.
 *
 * @param initialized git flow is configured and both long-lived branches exist
 * @param remote      name of the remote used for fetching and pushing, or null when there is none
 */
data class FlowContext(
    val repository: GitRepository,
    val config: FlowConfig,
    val initialized: Boolean,
    val currentBranch: String,
    val remote: String?,
    val localBranches: List<String>,
    val tags: List<String>,
    val flowBranches: List<FlowBranch>,
)
