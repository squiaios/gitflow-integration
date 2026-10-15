package gitflow.integration.ide

import gitflow.integration.core.FinishOptions
import gitflow.integration.core.FlowBranch
import gitflow.integration.core.FlowType
import gitflow.integration.ide.ui.DeleteBranchDialog
import gitflow.integration.ide.ui.FinishDialog
import gitflow.integration.ide.ui.InitDialog
import gitflow.integration.ide.ui.StartDialog
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import git4idea.merge.GitConflictResolver
import git4idea.repo.GitRepository

/**
 * The user-facing git flow operations: gather input in dialogs, then hand the work to
 * [GitFlowService]. Shared by the actions and the tool window. Call from the EDT.
 */
object GitFlowOperations {

    /**
     * The git flow actions as a popup menu (the `GitFlow.Actions` group), acting on
     * [repository]. The context is built here rather than taken from the caller, because the
     * caller's own component - a toolbar button, the branches popup - may be gone by the time an
     * action runs.
     */
    fun createMenuPopup(project: Project, repository: GitRepository): JBPopup {
        val context = SimpleDataContext.builder()
            .add(CommonDataKeys.PROJECT, project)
            .add(GitFlowDataKeys.REPOSITORY, repository)
            .build()
        val actions = ActionManager.getInstance().getAction("GitFlow.Actions") as ActionGroup
        return JBPopupFactory.getInstance().createActionGroupPopup(
            GitFlowBundle.message("popup.title"), actions, context,
            JBPopupFactory.ActionSelectionAid.SPEEDSEARCH, true,
        )
    }

    fun init(project: Project, repository: GitRepository) {
        val service = GitFlowService.getInstance(project)
        service.loadContext(repository) { context ->
            val dialog = InitDialog(project, context)
            if (!dialog.showAndGet()) {
                return@loadContext
            }
            val options = dialog.options()
            service.run(repository, GitFlowBundle.message("op.init")) { engine -> engine.init(options) }
        }
    }

    fun start(project: Project, repository: GitRepository, type: FlowType) {
        val service = GitFlowService.getInstance(project)
        service.loadContext(repository) { context ->
            if (!ensureInitialized(project, repository, context)) {
                return@loadContext
            }
            val dialog = StartDialog(project, context, type)
            if (!dialog.showAndGet()) {
                return@loadContext
            }
            val name = dialog.name()
            val base = dialog.base()
            val options = dialog.options()
            if (context.remote != null) {
                GitFlowSettings.getInstance(project).state.fetchBeforeStart = options.fetch
            }
            service.run(repository, GitFlowBundle.message("op.start", GitFlowBundle.typeName(type), name)) { engine ->
                engine.start(type, name, base, options)
            }
        }
    }

    fun finish(project: Project, repository: GitRepository, branch: FlowBranch) {
        val service = GitFlowService.getInstance(project)
        service.loadContext(repository) { context ->
            if (!ensureInitialized(project, repository, context)) {
                return@loadContext
            }
            val dialog = FinishDialog(project, context, branch)
            if (!dialog.showAndGet()) {
                return@loadContext
            }
            val options = dialog.options()
            remember(project, branch, options, context.remote != null)
            runFinish(project, repository, branch, options)
        }
    }

    private fun runFinish(project: Project, repository: GitRepository, branch: FlowBranch, options: FinishOptions) {
        GitFlowService.getInstance(project).run(
            repository,
            GitFlowBundle.message("op.finish", GitFlowBundle.typeName(branch.type), branch.name),
            retry = { runFinish(project, repository, branch, options) },
        ) { engine -> engine.finish(branch.type, branch.name, options) }
    }

    /** Next time the dialog opens with the choices just made. */
    private fun remember(project: Project, branch: FlowBranch, options: FinishOptions, hasRemote: Boolean) {
        val settings = GitFlowSettings.getInstance(project).state
        settings.keepBranch = options.keep
        if (hasRemote) {
            settings.fetchBeforeFinish = options.fetch
            settings.pushAfterFinish = options.push
        }
        if (!branch.type.versioned && !options.squash) {
            settings.noFastForward = options.noFastForward
        }
    }

    fun publish(project: Project, repository: GitRepository, branch: String) {
        GitFlowService.getInstance(project).run(repository, GitFlowBundle.message("op.publish", branch)) { engine ->
            engine.publish(branch)
        }
    }

    /** Checks the branch out; a branch that only exists on the remote gets a tracking branch first. */
    fun checkout(project: Project, repository: GitRepository, branch: FlowBranch) {
        GitFlowService.getInstance(project).run(repository, GitFlowBundle.message("op.checkout", branch.fullName)) { engine ->
            engine.checkout(branch.fullName)
        }
    }

    fun delete(project: Project, repository: GitRepository, branch: FlowBranch) {
        val dialog = DeleteBranchDialog(project, branch)
        if (!dialog.showAndGet()) {
            return
        }
        val force = dialog.force()
        val alsoRemote = dialog.alsoRemote()
        GitFlowService.getInstance(project).run(repository, GitFlowBundle.message("op.delete", branch.fullName)) { engine ->
            engine.delete(branch.fullName, force, alsoRemote)
        }
    }

    /** Opens the IDE's merge tool on the files left conflicted by an interrupted finish. */
    fun resolveConflicts(project: Project, repository: GitRepository) {
        val params = GitConflictResolver.Params(project)
        GitConflictResolver(project, listOf(repository.root), params).mergeNoProceedInBackground()
    }

    /** Offers to initialise git flow first when it is not set up; returns whether to carry on. */
    private fun ensureInitialized(project: Project, repository: GitRepository, context: FlowContext): Boolean {
        if (context.initialized) {
            return true
        }
        val answer = Messages.showYesNoDialog(
            project,
            GitFlowBundle.message("prompt.notInitialized"),
            GitFlowBundle.message("plugin.name"),
            GitFlowBundle.message("prompt.initialize"),
            Messages.getCancelButton(),
            Messages.getQuestionIcon(),
        )
        if (answer == Messages.YES) {
            init(project, repository)
        }
        return false
    }
}
