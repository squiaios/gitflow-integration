package gitflow.integration.ide.actions

import gitflow.integration.ide.GitFlowBundle
import gitflow.integration.ide.GitFlowOperations
import com.intellij.openapi.actionSystem.AnActionEvent

/** Finishes the selected flow branch, or the checked-out one: merges it back and cleans up. */
class FinishAction : GitFlowAction() {

    override fun update(e: AnActionEvent) {
        val repository = repository(e)
        if (repository == null) {
            e.presentation.isEnabledAndVisible = false
            return
        }
        val branch = targetBranch(e, repository)
        val finishable = branch != null && branch.type.finishable && branch.local
        e.presentation.isVisible = true
        e.presentation.isEnabled = finishable
        e.presentation.text = if (branch != null && finishable) {
            GitFlowBundle.message("action.finish.text.branch", GitFlowBundle.typeName(branch.type), branch.name)
        } else {
            GitFlowBundle.message("action.finish.text")
        }
    }

    override fun actionPerformed(e: AnActionEvent) {
        val repository = repository(e)
        val branch = repository?.let { targetBranch(e, it) }
        val project = e.project
        if (repository != null && branch != null && project != null) {
            GitFlowOperations.finish(project, repository, branch)
        }
    }
}
