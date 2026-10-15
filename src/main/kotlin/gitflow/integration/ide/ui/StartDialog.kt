package gitflow.integration.ide.ui

import gitflow.integration.core.FlowType
import gitflow.integration.core.RefNames
import gitflow.integration.core.StartOptions
import gitflow.integration.ide.FlowContext
import gitflow.integration.ide.GitFlowBundle
import gitflow.integration.ide.GitFlowSettings
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.CollectionComboBoxModel
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import javax.swing.JComponent
import javax.swing.event.DocumentEvent

/** Asks for the name of a new flow branch and, where it makes sense, its starting point. */
class StartDialog(
    project: Project,
    private val context: FlowContext,
    private val type: FlowType,
) : DialogWrapper(project) {

    private val name = JBTextField(28)
    private val preview = JBLabel()
    private val base = ComboBox<String>()
    private val fetch = JBCheckBox(GitFlowBundle.message("option.fetch"))

    init {
        title = GitFlowBundle.message("start.title", GitFlowBundle.typeName(type))

        name.document.addDocumentListener(object : DocumentAdapter() {
            override fun textChanged(e: DocumentEvent) {
                updatePreview()
            }
        })
        updatePreview()

        base.isEditable = true
        val candidates = baseCandidates()
        val selected = if (type == FlowType.SUPPORT && context.tags.isNotEmpty()) {
            context.tags[0]
        } else {
            context.config.baseBranch(type)
        }
        base.model = CollectionComboBoxModel(candidates, selected)

        fetch.isSelected = context.remote != null && GitFlowSettings.getInstance(project).state.fetchBeforeStart
        fetch.isEnabled = context.remote != null
        init()
    }

    /** Branches and tags a feature, bugfix or support branch may start from, the usual base first. */
    private fun baseCandidates(): List<String> {
        val candidates = LinkedHashSet<String>()
        candidates.add(context.config.baseBranch(type))
        candidates.addAll(context.localBranches)
        candidates.addAll(context.tags)
        return candidates.toList()
    }

    private fun updatePreview() {
        val shown = context.config.prefix(type) + name.text.trim()
        preview.text = GitFlowBundle.message("start.preview", shown)
    }

    override fun createCenterPanel(): JComponent {
        val form = FormBuilder.createFormBuilder()
            .addLabeledComponent(GitFlowBundle.message("start.name", GitFlowBundle.typeTitle(type)), name)
            .addComponentToRightColumn(preview)
        if (type.customBase) {
            form.addLabeledComponent(GitFlowBundle.message("start.base"), base)
        } else {
            form.addLabeledComponent(GitFlowBundle.message("start.base"), JBLabel(context.config.baseBranch(type)))
        }
        return form.addComponent(fetch).panel
    }

    override fun getPreferredFocusedComponent(): JComponent = name

    override fun doValidate(): ValidationInfo? {
        val shortName = name.text.trim()
        if (shortName.isEmpty()) {
            return ValidationInfo(GitFlowBundle.message("validate.name.empty"), name)
        }
        val branch = context.config.branchName(type, shortName)
        val problem = RefNames.validateBranchName(branch)
        if (problem != null) {
            return ValidationInfo(GitFlowBundle.message(problem), name)
        }
        if (branch in context.localBranches) {
            return ValidationInfo(GitFlowBundle.message("error.branch.exists", branch), name)
        }
        if (type.versioned) {
            val existing = context.flowBranches.firstOrNull { it.type == type }
            if (existing != null) {
                return ValidationInfo(GitFlowBundle.message("error.exists.single", type.id, existing.name), name)
            }
        }
        if (type == FlowType.SUPPORT && base().isEmpty()) {
            return ValidationInfo(GitFlowBundle.message("error.base.required"), base)
        }
        return null
    }

    fun name(): String = name.text.trim()

    /** The chosen starting point; empty for the type's default. */
    fun base(): String {
        if (!type.customBase) {
            return ""
        }
        val item = base.editor.item
        return item?.toString()?.trim() ?: ""
    }

    fun options(): StartOptions {
        val options = StartOptions()
        options.fetch = fetch.isSelected
        return options
    }
}
