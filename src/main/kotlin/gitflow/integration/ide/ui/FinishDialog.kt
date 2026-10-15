package gitflow.integration.ide.ui

import gitflow.integration.core.FinishOptions
import gitflow.integration.core.FlowBranch
import gitflow.integration.ide.FlowContext
import gitflow.integration.ide.GitFlowBundle
import gitflow.integration.ide.GitFlowSettings
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.UIUtil
import javax.swing.JComponent

/** Asks how a flow branch should be finished. The options offered depend on the branch type. */
class FinishDialog(
    project: Project,
    private val context: FlowContext,
    private val branch: FlowBranch,
) : DialogWrapper(project) {

    private val type = branch.type
    private val defaults = GitFlowSettings.getInstance(project).state

    private val fetch = JBCheckBox(GitFlowBundle.message("option.fetch"))
    private val push = JBCheckBox(GitFlowBundle.message("option.push"))
    private val keep = JBCheckBox(GitFlowBundle.message("option.keep"))

    // feature and bugfix
    private val noFastForward = JBCheckBox(GitFlowBundle.message("option.noFastForward"))
    private val squash = JBCheckBox(GitFlowBundle.message("option.squash"))
    private val rebase = JBCheckBox(GitFlowBundle.message("option.rebase", context.config.develop))

    // release and hotfix
    private val tag = JBCheckBox(GitFlowBundle.message("option.tag", context.config.tagName(branch.name)))
    private val tagMessage = JBTextField(30)
    private val backMerge = JBCheckBox(GitFlowBundle.message("option.backMerge", context.config.develop))

    init {
        title = GitFlowBundle.message("finish.title", GitFlowBundle.typeName(type), branch.name)

        val hasRemote = context.remote != null
        fetch.isSelected = hasRemote && defaults.fetchBeforeFinish
        fetch.isEnabled = hasRemote
        push.isSelected = hasRemote && defaults.pushAfterFinish
        push.isEnabled = hasRemote
        keep.isSelected = defaults.keepBranch

        noFastForward.isSelected = defaults.noFastForward
        squash.addActionListener { updateEnabledState() }

        tag.isSelected = true
        tag.addActionListener { updateEnabledState() }
        backMerge.isSelected = true
        tagMessage.text = GitFlowSettings.getInstance(project).tagMessage(type, branch.name)
        updateEnabledState()
        init()
    }

    private fun updateEnabledState() {
        // A squash is a single new commit: it cannot be rebased, and it is never a merge commit.
        noFastForward.isEnabled = !squash.isSelected
        rebase.isEnabled = !squash.isSelected
        if (squash.isSelected) {
            rebase.isSelected = false
        }
        tagMessage.isEnabled = tag.isSelected
    }

    override fun createCenterPanel(): JComponent {
        val config = context.config
        val summary = JBLabel(
            if (type.versioned) {
                GitFlowBundle.message("finish.target.versioned", branch.fullName, config.master, config.develop)
            } else {
                GitFlowBundle.message("finish.target", branch.fullName, config.develop)
            },
        )
        summary.foreground = UIUtil.getContextHelpForeground()

        val form = FormBuilder.createFormBuilder().addComponent(summary).addVerticalGap(8)
        if (type.versioned) {
            form.addComponent(tag)
                .addLabeledComponent(GitFlowBundle.message("option.tagMessage"), tagMessage)
                .addComponent(backMerge)
        } else {
            form.addComponent(noFastForward).addComponent(squash).addComponent(rebase)
        }
        return form.addVerticalGap(8)
            .addComponent(fetch)
            .addComponent(push)
            .addComponent(keep)
            .panel
    }

    fun options(): FinishOptions {
        val options = FinishOptions()
        options.fetch = fetch.isSelected
        options.push = push.isSelected
        options.keep = keep.isSelected
        if (type.versioned) {
            options.tag = tag.isSelected
            options.tagMessage = tagMessage.text.trim()
            options.backMerge = backMerge.isSelected
        } else {
            options.noFastForward = noFastForward.isSelected
            options.squash = squash.isSelected
            options.rebase = rebase.isSelected
        }
        return options
    }
}
