package gitflow.integration.ide.ui

import gitflow.integration.core.FlowBranch
import gitflow.integration.ide.GitFlowBundle
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.FormBuilder
import javax.swing.JComponent

/** Confirms the deletion of a flow branch and asks whether to force it and to delete it remotely too. */
class DeleteBranchDialog(project: Project, private val branch: FlowBranch) : DialogWrapper(project) {

    private val force = JBCheckBox(GitFlowBundle.message("delete.force"))
    private val alsoRemote = JBCheckBox(GitFlowBundle.message("delete.remote"))

    init {
        title = GitFlowBundle.message("delete.title")
        setOKButtonText(GitFlowBundle.message("delete.ok"))
        force.isEnabled = branch.local
        alsoRemote.isEnabled = branch.remote
        init()
    }

    override fun createCenterPanel(): JComponent =
        FormBuilder.createFormBuilder()
            .addComponent(JBLabel(GitFlowBundle.message("delete.message", branch.fullName)))
            .addVerticalGap(8)
            .addComponent(force)
            .addComponent(alsoRemote)
            .panel

    fun force(): Boolean = force.isSelected

    fun alsoRemote(): Boolean = alsoRemote.isSelected
}
