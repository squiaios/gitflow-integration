package gitflow.integration.ide.actions

import gitflow.integration.ide.GitFlowOperations
import com.intellij.openapi.actionSystem.AnActionEvent
import javax.swing.JComponent

/**
 * Opens the list of git flow actions as a popup menu. It exists because some places - the branches
 * popup, the main toolbar - take plain actions only, not a sub-menu: this one action stands for the
 * whole menu there.
 */
class ShowMenuAction : GitFlowAction() {

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = repository(e) != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project
        val repository = repository(e)
        if (project == null || repository == null) {
            return
        }
        val popup = GitFlowOperations.createMenuPopup(project, repository)
        val button = e.inputEvent?.component as? JComponent
        if (e.isFromActionToolbar && button != null) {
            popup.showUnderneathOf(button)
        } else {
            // From the branches popup, which closes first: there is no component left to anchor to.
            popup.showInFocusCenter()
        }
    }
}
