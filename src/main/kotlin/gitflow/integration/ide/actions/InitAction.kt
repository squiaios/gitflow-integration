package gitflow.integration.ide.actions

import gitflow.integration.ide.GitFlowBundle
import gitflow.integration.ide.GitFlowOperations
import gitflow.integration.ide.GitFlowService
import com.intellij.openapi.actionSystem.AnActionEvent

/** Initialises git flow in the repository, or changes its configuration when already initialised. */
class InitAction : GitFlowAction() {

    override fun update(e: AnActionEvent) {
        val repository = repository(e)
        e.presentation.isEnabledAndVisible = repository != null
        val project = e.project
        if (repository != null && project != null) {
            val configured = GitFlowService.getInstance(project).cachedConfig(repository).isConfigured
            e.presentation.text = GitFlowBundle.message(if (configured) "action.reconfigure.text" else "action.init.text")
        }
    }

    override fun actionPerformed(e: AnActionEvent) {
        val repository = repository(e)
        val project = e.project
        if (repository != null && project != null) {
            GitFlowOperations.init(project, repository)
        }
    }
}
