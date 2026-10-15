package gitflow.integration.ide.ui

import gitflow.integration.core.FlowType
import gitflow.integration.core.InitOptions
import gitflow.integration.core.RefNames
import gitflow.integration.ide.FlowContext
import gitflow.integration.ide.GitFlowBundle
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.CollectionComboBoxModel
import com.intellij.ui.TitledSeparator
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import java.util.EnumMap
import javax.swing.JComponent

/** Asks for the production and development branches, the branch prefixes and the tag prefix. */
class InitDialog(project: Project, private val context: FlowContext) : DialogWrapper(project) {

    private val master = branchBox()
    private val develop = branchBox()
    private val prefixes = EnumMap<FlowType, JBTextField>(FlowType::class.java)
    private val tagPrefix = JBTextField()

    init {
        title = GitFlowBundle.message(if (context.initialized) "init.title.reconfigure" else "init.title")

        val defaults = defaults(context)
        master.model = CollectionComboBoxModel(context.localBranches, defaults.master)
        develop.model = CollectionComboBoxModel(context.localBranches, defaults.develop)
        for (type in FlowType.entries) {
            prefixes[type] = JBTextField(defaults.prefixes.getValue(type), 16)
        }
        tagPrefix.text = defaults.versionTagPrefix
        tagPrefix.emptyText.setText(GitFlowBundle.message("init.tagPrefix.empty"))
        init()
    }

    override fun createCenterPanel(): JComponent {
        val form = FormBuilder.createFormBuilder()
            .addLabeledComponent(GitFlowBundle.message("init.master"), master)
            .addLabeledComponent(GitFlowBundle.message("init.develop"), develop)
            .addComponent(TitledSeparator(GitFlowBundle.message("init.prefixes")))
        for (type in FlowType.entries) {
            form.addLabeledComponent(
                GitFlowBundle.message("init.prefix", GitFlowBundle.typeTitle(type)),
                prefixes.getValue(type),
            )
        }
        return form
            .addLabeledComponent(GitFlowBundle.message("init.tagPrefix"), tagPrefix)
            .panel
    }

    override fun getPreferredFocusedComponent(): JComponent = develop

    override fun doValidate(): ValidationInfo? {
        val masterName = text(master)
        val developName = text(develop)
        val invalid = branchProblem(masterName, master) ?: branchProblem(developName, develop)
        if (invalid != null) {
            return invalid
        }
        if (masterName == developName) {
            return ValidationInfo(GitFlowBundle.message("error.init.sameBranch"), develop)
        }

        val seen = HashMap<String, FlowType>()
        for (type in FlowType.entries) {
            val field = prefixes.getValue(type)
            val prefix = field.text
            if (prefix.isEmpty()) {
                return ValidationInfo(GitFlowBundle.message("error.init.prefixEmpty", type.id), field)
            }
            val problem = RefNames.validateBranchName(prefix + "x")
            if (problem != null) {
                return ValidationInfo(GitFlowBundle.message(problem), field)
            }
            val other = seen.put(prefix, type)
            if (other != null) {
                return ValidationInfo(GitFlowBundle.message("error.init.prefixDuplicate", other.id, type.id), field)
            }
        }
        val tag = tagPrefix.text
        if (tag.isNotEmpty() && RefNames.validateBranchName(tag + "x") != null) {
            return ValidationInfo(GitFlowBundle.message("error.init.tagPrefixInvalid"), tagPrefix)
        }
        return null
    }

    fun options(): InitOptions {
        val options = InitOptions()
        options.master = text(master)
        options.develop = text(develop)
        for (type in FlowType.entries) {
            options.prefixes[type] = prefixes.getValue(type).text
        }
        options.versionTagPrefix = tagPrefix.text
        options.force = context.initialized
        return options
    }

    private companion object {
        fun branchBox(): ComboBox<String> {
            val box = ComboBox<String>()
            box.isEditable = true
            return box
        }

        /** The current configuration when there is one, otherwise sensible guesses from the repository. */
        fun defaults(context: FlowContext): InitOptions {
            if (context.initialized || context.config.isConfigured) {
                return InitOptions.from(context.config)
            }
            val options = InitOptions()
            val branches = context.localBranches
            if ("main" in branches) {
                options.master = "main"
            } else if ("master" in branches) {
                options.master = "master"
            } else if (context.currentBranch.isNotEmpty()) {
                options.master = context.currentBranch
            }
            return options
        }

        fun branchProblem(name: String, field: JComponent): ValidationInfo? =
            RefNames.validateBranchName(name)?.let { ValidationInfo(GitFlowBundle.message(it), field) }

        fun text(box: ComboBox<String>): String {
            val item = box.editor.item
            return item?.toString()?.trim() ?: ""
        }
    }
}
