package gitflow.integration.ide.ui

import gitflow.integration.ide.GitFlowBundle
import gitflow.integration.ide.GitFlowIcons
import gitflow.integration.ide.GitFlowOperations
import gitflow.integration.ide.GitFlowService
import com.intellij.dvcs.repo.VcsRepositoryManager
import com.intellij.dvcs.repo.VcsRepositoryMappingListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryChangeListener
import javax.swing.Icon
import org.jetbrains.annotations.Nls

/**
 * Status bar entry for git flow, in the family of "File encoding" or the PHP language level: it
 * shows where the current branch stands in the flow, and opens the git flow actions when clicked.
 *
 *  - on a flow branch: its type and name ("Feature: login");
 *  - git flow not initialized: "Git Flow: off";
 *  - otherwise (develop, production, any other branch): "Git Flow".
 */
internal class GitFlowStatusBarWidget(private val project: Project) :
    StatusBarWidget, StatusBarWidget.MultipleTextValuesPresentation {

    private data class Text(@Nls val label: String, @Nls val tooltip: String)

    private var statusBar: StatusBar? = null

    override fun ID(): String = GitFlowStatusBarWidgetFactory.ID

    override fun getPresentation(): StatusBarWidget.WidgetPresentation = this

    override fun install(statusBar: StatusBar) {
        this.statusBar = statusBar
        val connection = project.messageBus.connect(this)
        connection.subscribe(GitRepository.GIT_REPO_CHANGE, GitRepositoryChangeListener { refresh() })
        connection.subscribe(GitFlowService.CONFIG_CHANGED, Runnable { refresh() })
        // Repositories may be detected after the widget is created.
        connection.subscribe(VcsRepositoryManager.VCS_REPOSITORY_MAPPING_UPDATED, VcsRepositoryMappingListener { refresh() })
        // With several repositories, the one the widget describes follows the file being edited.
        connection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, object : FileEditorManagerListener {
            override fun selectionChanged(event: FileEditorManagerEvent) {
                refresh()
            }
        })
        refresh()
    }

    private fun refresh() {
        val bar = statusBar ?: return
        ApplicationManager.getApplication().invokeLater(
            { bar.updateWidget(ID()) }, ModalityState.any(), project.disposed,
        )
    }

    /** Never runs git: it reads the cached configuration, so it is cheap enough for every repaint. */
    private fun text(): Text {
        val service = GitFlowService.getInstance(project)
        val repository = service.currentRepository()
            // Not expected (the widget only exists in projects with a repository), but never blank.
            ?: return idle()
        val config = service.loadedConfig(repository)
        if (config != null && !config.isConfigured) {
            return Text(GitFlowBundle.message("statusbar.off"), GitFlowBundle.message("statusbar.tooltip.off"))
        }
        val branch = repository.currentBranchName
        if (config != null && branch != null) {
            val flowBranch = config.classify(branch)
            if (flowBranch != null) {
                val type = GitFlowBundle.typeTitle(flowBranch.type)
                return Text(
                    GitFlowBundle.message("statusbar.branch", type, flowBranch.name),
                    GitFlowBundle.message("statusbar.tooltip.branch", GitFlowBundle.typeName(flowBranch.type), branch),
                )
            }
        }
        return idle()
    }

    private fun idle() =
        Text(GitFlowBundle.message("statusbar.idle"), GitFlowBundle.message("statusbar.tooltip.idle"))

    override fun getSelectedValue(): String = text().label

    override fun getTooltipText(): String = text().tooltip

    override fun getIcon(): Icon = GitFlowIcons.GIT_FLOW

    /** The same actions as the Git | Git Flow menu, acting on the repository the widget describes. */
    override fun getPopup(): JBPopup? {
        val repository = GitFlowService.getInstance(project).currentRepository() ?: return null
        return GitFlowOperations.createMenuPopup(project, repository)
    }
}
