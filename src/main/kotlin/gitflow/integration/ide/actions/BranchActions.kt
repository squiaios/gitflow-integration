package gitflow.integration.ide.actions

import gitflow.integration.core.FlowBranch
import gitflow.integration.ide.GitFlowDataKeys
import gitflow.integration.ide.GitFlowOperations
import com.intellij.openapi.actionSystem.AnActionEvent

/** Actions on the branch selected in the Git Flow tool window; unavailable elsewhere. */
object BranchActions {

    /** Checks the branch out; for a branch that only exists on the remote, creates a tracking branch. */
    class Checkout : GitFlowAction() {
        override fun update(e: AnActionEvent) {
            e.presentation.isEnabledAndVisible = otherBranch(e) != null && repository(e) != null
        }

        override fun actionPerformed(e: AnActionEvent) {
            val branch = otherBranch(e)
            val repository = repository(e)
            val project = e.project
            if (branch != null && repository != null && project != null) {
                GitFlowOperations.checkout(project, repository, branch)
            }
        }
    }

    /** Deletes the branch locally and, on request, on the remote. */
    class Delete : GitFlowAction() {
        override fun update(e: AnActionEvent) {
            e.presentation.isEnabledAndVisible = otherBranch(e) != null && repository(e) != null
        }

        override fun actionPerformed(e: AnActionEvent) {
            val branch = otherBranch(e)
            val repository = repository(e)
            val project = e.project
            if (branch != null && repository != null && project != null) {
                GitFlowOperations.delete(project, repository, branch)
            }
        }
    }
}

/** The selected branch, provided it is not the checked-out one. */
private fun otherBranch(e: AnActionEvent): FlowBranch? {
    val branch = e.getData(GitFlowDataKeys.BRANCH)
    return if (branch == null || branch.current) null else branch
}
