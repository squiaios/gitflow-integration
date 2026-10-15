package gitflow.integration.ide.actions

import gitflow.integration.core.FlowBranch
import gitflow.integration.ide.GitFlowDataKeys
import gitflow.integration.ide.GitFlowService
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import git4idea.repo.GitRepository

/**
 * Base of the git flow actions. They are hidden in projects without a git repository, and act on
 * the repository the event points at (see [GitFlowService.guessRepository]).
 */
abstract class GitFlowAction : DumbAwareAction() {

    /**
     * Updates run in the background under a read lock, which is also why they must never start git:
     * they read the configuration through [GitFlowService.cachedConfig].
     */
    final override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    protected fun repository(e: AnActionEvent): GitRepository? {
        val project = e.project ?: return null
        return GitFlowService.getInstance(project).guessRepository(e.dataContext)
    }

    /**
     * The flow branch the action applies to: the one selected in the tool window, otherwise the
     * checked-out branch if it is a flow branch.
     */
    protected fun targetBranch(e: AnActionEvent, repository: GitRepository): FlowBranch? {
        e.getData(GitFlowDataKeys.BRANCH)?.let { return it }
        val current = repository.currentBranchName
        val project = e.project
        if (current == null || project == null) {
            return null
        }
        val config = GitFlowService.getInstance(project).cachedConfig(repository)
        if (!config.isConfigured) {
            return null
        }
        return config.classify(current)?.let { ref ->
            FlowBranch(ref.type, ref.name, current, true, repository.getBranchTrackInfo(current) != null, true)
        }
    }
}
