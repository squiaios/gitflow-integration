package gitflow.integration.ide

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.ui.TitledSeparator
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.UIUtil
import javax.swing.JComponent
import javax.swing.JPanel
import org.jetbrains.annotations.Nls

/** Settings | Version Control | Git Flow: defaults for the start and finish dialogs. */
class GitFlowConfigurable(project: Project) : Configurable {

    private val state = GitFlowSettings.getInstance(project).state

    private lateinit var fetchBeforeStart: JBCheckBox
    private lateinit var fetchBeforeFinish: JBCheckBox
    private lateinit var pushAfterFinish: JBCheckBox
    private lateinit var keepBranch: JBCheckBox
    private lateinit var noFastForward: JBCheckBox
    private lateinit var releaseTagMessage: JBTextField
    private lateinit var hotfixTagMessage: JBTextField

    @Nls
    override fun getDisplayName(): String = GitFlowBundle.message("settings.title")

    override fun createComponent(): JComponent {
        fetchBeforeStart = JBCheckBox(GitFlowBundle.message("settings.fetchBeforeStart"))
        fetchBeforeFinish = JBCheckBox(GitFlowBundle.message("settings.fetchBeforeFinish"))
        pushAfterFinish = JBCheckBox(GitFlowBundle.message("settings.pushAfterFinish"))
        keepBranch = JBCheckBox(GitFlowBundle.message("settings.keepBranch"))
        noFastForward = JBCheckBox(GitFlowBundle.message("settings.noFastForward"))
        releaseTagMessage = JBTextField(30)
        hotfixTagMessage = JBTextField(30)

        val hint = JBLabel(GitFlowBundle.message("settings.tagMessage.hint", GitFlowSettings.NAME_PLACEHOLDER))
        hint.foreground = UIUtil.getContextHelpForeground()

        val panel = FormBuilder.createFormBuilder()
            .addComponent(TitledSeparator(GitFlowBundle.message("settings.defaults")))
            .addComponent(fetchBeforeStart)
            .addComponent(fetchBeforeFinish)
            .addComponent(pushAfterFinish)
            .addComponent(keepBranch)
            .addComponent(noFastForward)
            .addComponent(TitledSeparator(GitFlowBundle.message("settings.tags")))
            .addLabeledComponent(GitFlowBundle.message("settings.releaseTagMessage"), releaseTagMessage)
            .addLabeledComponent(GitFlowBundle.message("settings.hotfixTagMessage"), hotfixTagMessage)
            .addComponentToRightColumn(hint)
            .addComponentFillVertically(JPanel(), 0)
            .panel
        reset()
        return panel
    }

    override fun isModified(): Boolean =
        fetchBeforeStart.isSelected != state.fetchBeforeStart
            || fetchBeforeFinish.isSelected != state.fetchBeforeFinish
            || pushAfterFinish.isSelected != state.pushAfterFinish
            || keepBranch.isSelected != state.keepBranch
            || noFastForward.isSelected != state.noFastForward
            || releaseTagMessage.text != state.releaseTagMessage
            || hotfixTagMessage.text != state.hotfixTagMessage

    override fun apply() {
        state.fetchBeforeStart = fetchBeforeStart.isSelected
        state.fetchBeforeFinish = fetchBeforeFinish.isSelected
        state.pushAfterFinish = pushAfterFinish.isSelected
        state.keepBranch = keepBranch.isSelected
        state.noFastForward = noFastForward.isSelected
        state.releaseTagMessage = releaseTagMessage.text
        state.hotfixTagMessage = hotfixTagMessage.text
    }

    override fun reset() {
        fetchBeforeStart.isSelected = state.fetchBeforeStart
        fetchBeforeFinish.isSelected = state.fetchBeforeFinish
        pushAfterFinish.isSelected = state.pushAfterFinish
        keepBranch.isSelected = state.keepBranch
        noFastForward.isSelected = state.noFastForward
        releaseTagMessage.text = state.releaseTagMessage
        hotfixTagMessage.text = state.hotfixTagMessage
    }
}
