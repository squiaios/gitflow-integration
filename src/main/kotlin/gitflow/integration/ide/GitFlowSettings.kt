package gitflow.integration.ide

import gitflow.integration.core.FlowType
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.XmlSerializerUtil

/**
 * Per-user, per-project defaults for the git flow dialogs. They are stored in the workspace file
 * rather than in a shared one: they are preferences, not part of the repository's workflow.
 */
@Service(Service.Level.PROJECT)
@State(name = "GitFlowSettings", storages = [Storage(StoragePathMacros.WORKSPACE_FILE)])
class GitFlowSettings : PersistentStateComponent<GitFlowSettings.State> {

    class State {
        var fetchBeforeStart: Boolean = false
        var fetchBeforeFinish: Boolean = false
        var pushAfterFinish: Boolean = false
        var keepBranch: Boolean = false
        var noFastForward: Boolean = true
        var releaseTagMessage: String = "Release $NAME_PLACEHOLDER"
        var hotfixTagMessage: String = "Hotfix $NAME_PLACEHOLDER"
    }

    private var myState = State()

    override fun getState(): State = myState

    override fun loadState(state: State) {
        XmlSerializerUtil.copyBean(state, myState)
    }

    /** The default tag message for a release or hotfix, with the placeholder replaced by its name. */
    fun tagMessage(type: FlowType, name: String): String {
        val template = if (type == FlowType.HOTFIX) myState.hotfixTagMessage else myState.releaseTagMessage
        return template.replace(NAME_PLACEHOLDER, name)
    }

    companion object {
        const val NAME_PLACEHOLDER = "{name}"

        fun getInstance(project: Project): GitFlowSettings = project.getService(GitFlowSettings::class.java)
    }
}
