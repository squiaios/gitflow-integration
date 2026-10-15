package gitflow.integration.ide

import gitflow.integration.core.FlowResult
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import git4idea.repo.GitRepository
import org.jetbrains.annotations.Nls

/** Reports the outcome of an operation to the user. */
internal object GitFlowNotifier {

    const val GROUP_ID = "Git Flow"

    /** Lines of git output shown below the error; the rest is in the Git console. */
    private const val MAX_DETAIL_LINES = 8

    fun show(project: Project, repository: GitRepository, @Nls title: String, result: FlowResult, retry: (() -> Unit)?) {
        val (type, heading) = when (result.status) {
            FlowResult.Status.SUCCESS ->
                (if (result.warnings.isEmpty()) NotificationType.INFORMATION else NotificationType.WARNING) to title
            FlowResult.Status.CONFLICT ->
                NotificationType.WARNING to GitFlowBundle.message("notification.conflict", title)
            FlowResult.Status.FAILED ->
                NotificationType.ERROR to GitFlowBundle.message("notification.failed", title)
        }

        val notification = NotificationGroupManager.getInstance()
            .getNotificationGroup(GROUP_ID)
            .createNotification(heading, content(result), type)
        if (result.isConflict) {
            notification.addAction(
                NotificationAction.createSimple(GitFlowBundle.message("notification.resolve")) {
                    GitFlowOperations.resolveConflicts(project, repository)
                },
            )
            if (retry != null) {
                notification.addAction(
                    NotificationAction.createSimpleExpiring(GitFlowBundle.message("notification.finishAgain")) {
                        retry()
                    },
                )
            }
        }
        notification.notify(project)
    }

    private fun content(result: FlowResult): String {
        val lines = mutableListOf<String>()
        if (!result.isSuccess) {
            result.error?.let { lines.add(GitFlowBundle.message(it)) }
            result.detail.lines().filter { it.isNotBlank() }.take(MAX_DETAIL_LINES).forEach { lines.add(it) }
            if (result.isConflict) {
                lines.add(GitFlowBundle.message("notification.conflict.hint"))
            }
        }
        for (step in result.steps) {
            lines.add("✓ " + GitFlowBundle.message(step))
        }
        for (warning in result.warnings) {
            lines.add("⚠ " + GitFlowBundle.message(warning))
        }
        return lines.joinToString("<br>") { StringUtil.escapeXmlEntities(it) }
    }
}
