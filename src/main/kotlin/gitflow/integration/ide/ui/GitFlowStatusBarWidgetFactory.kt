package gitflow.integration.ide.ui

import gitflow.integration.ide.GitFlowBundle
import gitflow.integration.ide.GitFlowService
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory
import git4idea.GitUtil
import org.jetbrains.annotations.Nls

/**
 * Adds the git flow entry to the status bar of projects that have a git repository. It can be
 * hidden like any other widget: right-click the status bar and untick "Git Flow".
 */
class GitFlowStatusBarWidgetFactory : StatusBarWidgetFactory {

    override fun getId(): String = ID

    @Nls
    override fun getDisplayName(): String = GitFlowBundle.message("statusbar.displayName")

    override fun isAvailable(project: Project): Boolean {
        // Instantiating the service makes it watch the repository mappings, so that the widget
        // appears once repositories are detected after startup (see GitFlowService).
        GitFlowService.getInstance(project)
        return GitUtil.hasGitRepositories(project)
    }

    override fun createWidget(project: Project): StatusBarWidget = GitFlowStatusBarWidget(project)

    companion object {
        const val ID = "gitflow.integration.statusBar"
    }
}
