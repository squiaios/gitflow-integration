package gitflow.integration.ide.actions

import gitflow.integration.ide.GitFlowOperations
import com.intellij.openapi.actionSystem.AnActionEvent

/** Pushes the selected flow branch, or the checked-out one, and sets it to track the remote branch. */
class PublishAction : GitFlowAction() {

    override fun update(e: AnActionEvent) {
        val repository = repository(e)
        if (repository == null) {
            e.presentation.isEnabledAndVisible = false
            return
        }
        val branch = targetBranch(e, repository)
        e.presentation.isVisible = true
        e.presentation.isEnabled = branch != null && branch.local && !branch.remote
    }

    override fun actionPerformed(e: AnActionEvent) {
        val repository = repository(e)
        val branch = repository?.let { targetBranch(e, it) }
        val project = e.project
        if (repository != null && branch != null && project != null) {
            GitFlowOperations.publish(project, repository, branch.fullName)
        }
    }
}
