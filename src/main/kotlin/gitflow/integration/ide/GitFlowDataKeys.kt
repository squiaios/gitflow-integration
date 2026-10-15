package gitflow.integration.ide

import gitflow.integration.core.FlowBranch
import com.intellij.openapi.actionSystem.DataKey
import git4idea.repo.GitRepository

/** Data supplied by the Git Flow tool window to the actions it hosts. */
object GitFlowDataKeys {

    /** The repository shown in the tool window. */
    @JvmField
    val REPOSITORY: DataKey<GitRepository> = DataKey.create("gitflow.integration.repository")

    /** The flow branch selected in the tool window's tree. */
    @JvmField
    val BRANCH: DataKey<FlowBranch> = DataKey.create("gitflow.integration.branch")
}
