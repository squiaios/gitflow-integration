package gitflow.integration.ide.actions

import gitflow.integration.core.FlowType
import gitflow.integration.ide.GitFlowOperations
import com.intellij.openapi.actionSystem.AnActionEvent

/**
 * Starts a flow branch of one type. When git flow is not initialised yet the user is offered to
 * initialise it, so these stay enabled in any repository.
 */
sealed class StartAction(private val type: FlowType) : GitFlowAction() {

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = repository(e) != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val repository = repository(e)
        val project = e.project
        if (repository != null && project != null) {
            GitFlowOperations.start(project, repository, type)
        }
    }

    class Feature : StartAction(FlowType.FEATURE)

    class Bugfix : StartAction(FlowType.BUGFIX)

    class Release : StartAction(FlowType.RELEASE)

    class Hotfix : StartAction(FlowType.HOTFIX)

    class Support : StartAction(FlowType.SUPPORT)
}
