package gitflow.integration.ide.ui

import gitflow.integration.core.FlowBranch
import gitflow.integration.core.FlowType
import gitflow.integration.ide.FlowContext
import gitflow.integration.ide.GitFlowBundle
import gitflow.integration.ide.GitFlowDataKeys
import gitflow.integration.ide.GitFlowOperations
import gitflow.integration.ide.GitFlowService
import com.intellij.dvcs.repo.VcsRepositoryManager
import com.intellij.dvcs.repo.VcsRepositoryMappingListener
import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.ColoredTreeCellRenderer
import com.intellij.ui.DoubleClickListener
import com.intellij.ui.PopupHandler
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.TreeUIHelper
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.Alarm
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.tree.TreeUtil
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryChangeListener
import java.awt.BorderLayout
import java.awt.event.MouseEvent
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreePath

/**
 * The content of the Git Flow tool window. It shows the flow branches of one repository, grouped
 * by type, and hosts the plugin's actions: their toolbar and the tree's context menu act on the
 * repository and branch it provides through [GitFlowDataKeys].
 */
internal class GitFlowPanel(private val project: Project) : SimpleToolWindowPanel(true, true), Disposable {

    private val service = GitFlowService.getInstance(project)
    private val model = DefaultTreeModel(DefaultMutableTreeNode())
    private val tree = Tree(model)
    private val repositoryBox = ComboBox<GitRepository>()
    private val top = JPanel(BorderLayout())
    private val refreshAlarm = Alarm(Alarm.ThreadToUse.SWING_THREAD, this)

    private var repository: GitRepository? = null

    /** Set while the repository box is being refilled, so that doing so does not trigger a refresh. */
    private var updatingRepositoryBox = false

    init {
        tree.isRootVisible = false
        tree.showsRootHandles = true
        tree.cellRenderer = Renderer()
        TreeUIHelper.getInstance().installTreeSpeedSearch(tree)
        tree.emptyText.setText(GitFlowBundle.message("toolwindow.loading"))

        repositoryBox.renderer = object : ColoredListCellRenderer<GitRepository>() {
            override fun customizeCellRenderer(
                list: JList<out GitRepository>,
                value: GitRepository?,
                index: Int,
                selected: Boolean,
                hasFocus: Boolean,
            ) {
                if (value != null) {
                    append(value.root.name)
                }
            }
        }
        repositoryBox.addActionListener {
            if (!updatingRepositoryBox) {
                repository = repositoryBox.selectedItem as GitRepository?
                refresh()
            }
        }
        top.add(repositoryBox, BorderLayout.CENTER)
        top.border = JBUI.Borders.empty(2, 4)
        top.isVisible = false

        val content = JPanel(BorderLayout())
        content.add(top, BorderLayout.NORTH)
        content.add(ScrollPaneFactory.createScrollPane(tree, true), BorderLayout.CENTER)
        setContent(content)

        val toolbar = ActionManager.getInstance().createActionToolbar("GitFlowToolWindow", toolbarActions(), false)
        toolbar.targetComponent = tree
        setToolbar(toolbar.component)

        object : DoubleClickListener() {
            override fun onDoubleClick(event: MouseEvent): Boolean {
                val branch = selectedBranch()
                val target = repository
                if (branch == null || branch.current || target == null) {
                    return false
                }
                GitFlowOperations.checkout(project, target, branch)
                return true
            }
        }.installOn(tree)
        PopupHandler.installPopupMenu(tree, "GitFlow.ToolWindow.Popup", "GitFlowToolWindowPopup")

        val connection = project.messageBus.connect(this)
        connection.subscribe(GitRepository.GIT_REPO_CHANGE, GitRepositoryChangeListener { scheduleRefresh() })
        connection.subscribe(
            VcsRepositoryManager.VCS_REPOSITORY_MAPPING_UPDATED,
            VcsRepositoryMappingListener { scheduleRefresh() },
        )

        refresh()
    }

    private fun toolbarActions(): ActionGroup {
        val actions = ActionManager.getInstance()
        val group = DefaultActionGroup()
        group.add(RefreshAction())
        group.addSeparator()
        group.add(actions.getAction("GitFlow.Init"))
        group.add(actions.getAction("GitFlow.StartMenu"))
        group.add(actions.getAction("GitFlow.Finish"))
        group.add(actions.getAction("GitFlow.Publish"))
        return group
    }

    override fun uiDataSnapshot(sink: DataSink) {
        super.uiDataSnapshot(sink)
        sink.set(GitFlowDataKeys.REPOSITORY, repository)
        sink.set(GitFlowDataKeys.BRANCH, selectedBranch())
    }

    override fun dispose() {
        // Everything registered with this panel as parent is disposed by the platform.
    }

    private fun selectedBranch(): FlowBranch? {
        val node = tree.selectionPath?.lastPathComponent as? DefaultMutableTreeNode
        return node?.userObject as? FlowBranch
    }

    private fun scheduleRefresh() {
        refreshAlarm.cancelAllRequests()
        refreshAlarm.addRequest({ refresh() }, 300)
    }

    /** Reloads the tree: reads git off the EDT, then rebuilds the tree on it. */
    fun refresh() {
        val repositories = service.repositories()
        updateRepositoryBox(repositories)
        val target = repository
        if (target == null) {
            show(null)
            return
        }
        ApplicationManager.getApplication().executeOnPooledThread {
            val context = service.readContext(target)
            ApplicationManager.getApplication().invokeLater(
                {
                    if (!project.isDisposed && target == repository) {
                        show(context)
                    }
                },
                ModalityState.any(),
            )
        }
    }

    /** Keeps the repository chooser in step with the project's repositories; hidden when there is only one. */
    private fun updateRepositoryBox(repositories: List<GitRepository>) {
        if (repository == null || repository !in repositories) {
            repository = repositories.firstOrNull()
        }
        updatingRepositoryBox = true
        try {
            repositoryBox.removeAllItems()
            repositories.forEach { repositoryBox.addItem(it) }
            repositoryBox.selectedItem = repository
        } finally {
            updatingRepositoryBox = false
        }
        top.isVisible = repositories.size > 1
    }

    private fun show(context: FlowContext?) {
        val previous = selectedBranch()
        val root = DefaultMutableTreeNode()
        if (context == null) {
            setEmptyText("toolwindow.noRepository", null, null)
        } else if (!context.initialized) {
            setEmptyText("toolwindow.notInitialized", "toolwindow.initialize") { initialize(context) }
        } else {
            for (type in FlowType.entries) {
                val typeNode = DefaultMutableTreeNode(type)
                for (branch in context.flowBranches) {
                    if (branch.type == type) {
                        typeNode.add(DefaultMutableTreeNode(branch))
                    }
                }
                if (typeNode.childCount > 0) {
                    root.add(typeNode)
                }
            }
            if (root.childCount == 0) {
                setEmptyText("toolwindow.noBranches", "toolwindow.startFeature") {
                    GitFlowOperations.start(project, context.repository, FlowType.FEATURE)
                }
            }
        }
        model.setRoot(root)
        TreeUtil.expandAll(tree)
        if (previous != null) {
            selectBranch(root, previous.fullName)
        }
    }

    private fun initialize(context: FlowContext) {
        GitFlowOperations.init(project, context.repository)
    }

    private fun setEmptyText(messageKey: String, linkKey: String?, onLink: (() -> Unit)?) {
        val emptyText = tree.emptyText
        emptyText.setText(GitFlowBundle.message(messageKey))
        if (linkKey != null && onLink != null) {
            emptyText.appendSecondaryText(GitFlowBundle.message(linkKey), SimpleTextAttributes.LINK_ATTRIBUTES) { onLink() }
        }
    }

    private fun selectBranch(root: DefaultMutableTreeNode, fullName: String) {
        val nodes = root.depthFirstEnumeration().toList().filterIsInstance<DefaultMutableTreeNode>()
        for (node in nodes) {
            val branch = node.userObject as? FlowBranch
            if (branch != null && branch.fullName == fullName) {
                tree.selectionPath = TreePath(node.path)
                return
            }
        }
    }

    private inner class RefreshAction :
        DumbAwareAction(GitFlowBundle.message("action.refresh.text"), null, AllIcons.Actions.Refresh) {

        override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

        override fun actionPerformed(e: AnActionEvent) {
            service.invalidateAll()
            refresh()
        }
    }

    private class Renderer : ColoredTreeCellRenderer() {
        override fun customizeCellRenderer(
            tree: JTree,
            value: Any?,
            selected: Boolean,
            expanded: Boolean,
            leaf: Boolean,
            row: Int,
            hasFocus: Boolean,
        ) {
            when (val item = (value as DefaultMutableTreeNode).userObject) {
                is FlowType -> {
                    icon = AllIcons.Nodes.Folder
                    append(GitFlowBundle.typePlural(item), SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES)
                }
                is FlowBranch -> {
                    icon = AllIcons.Vcs.Branch
                    append(
                        item.name,
                        if (item.current) SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES else SimpleTextAttributes.REGULAR_ATTRIBUTES,
                    )
                    append("  " + where(item), SimpleTextAttributes.GRAYED_ATTRIBUTES)
                }
            }
        }

        private fun where(branch: FlowBranch): String {
            val parts = mutableListOf<String>()
            if (branch.current) {
                parts.add(GitFlowBundle.message("branch.current"))
            }
            parts.add(
                GitFlowBundle.message(
                    when {
                        branch.local && branch.remote -> "branch.localRemote"
                        branch.local -> "branch.localOnly"
                        else -> "branch.remoteOnly"
                    },
                ),
            )
            return parts.joinToString(", ")
        }
    }
}
