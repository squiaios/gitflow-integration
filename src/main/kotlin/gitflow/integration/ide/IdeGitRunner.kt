package gitflow.integration.ide

import gitflow.integration.core.GitResult
import gitflow.integration.core.GitRunner
import com.intellij.openapi.project.Project
import git4idea.commands.Git
import git4idea.commands.GitCommand
import git4idea.commands.GitLineHandler
import git4idea.repo.GitRepository

/**
 * Runs the engine's git commands through the IDE's git support, so they use the configured git
 * executable and the IDE's credential and SSH handling, and show up in the Git console.
 *
 * Calls block: use it off the EDT only.
 */
internal class IdeGitRunner(
    private val project: Project,
    private val repository: GitRepository,
) : GitRunner {

    override fun run(args: List<String>): GitResult {
        val command = COMMANDS[args[0]] ?: return GitResult.failure("Unsupported git command: ${args[0]}")
        val handler = GitLineHandler(project, repository.root, command)
        handler.addParameters(args.subList(1, args.size))
        handler.setSilent(isReadOnly(args))
        if (command == GitCommand.FETCH || command == GitCommand.PUSH) {
            // Tells the IDE which URLs it may be asked to authenticate against.
            handler.setUrls(repository.remotes.mapNotNull { it.firstUrl })
        }
        val result = Git.getInstance().runCommand(handler)
        return GitResult(result.exitCode, result.outputAsJoinedString, result.errorOutputAsJoinedString)
    }

    private companion object {
        val COMMANDS: Map<String, GitCommand> = mapOf(
            "branch" to GitCommand.BRANCH,
            "checkout" to GitCommand.CHECKOUT,
            "commit" to GitCommand.COMMIT,
            "config" to GitCommand.CONFIG,
            "diff" to GitCommand.DIFF,
            "fetch" to GitCommand.FETCH,
            "for-each-ref" to GitCommand.FOR_EACH_REF,
            "merge" to GitCommand.MERGE,
            "merge-base" to GitCommand.MERGE_BASE,
            "push" to GitCommand.PUSH,
            "rebase" to GitCommand.REBASE,
            "remote" to GitCommand.REMOTE,
            "rev-list" to GitCommand.REV_LIST,
            "rev-parse" to GitCommand.REV_PARSE,
            "status" to GitCommand.STATUS,
            "tag" to GitCommand.TAG,
        )

        /** Commands that only read: kept out of the Git console so it shows what actually changes. */
        val READ_ONLY: Set<String> = setOf(
            "diff", "for-each-ref", "merge-base", "remote", "rev-list", "rev-parse", "status",
        )

        fun isReadOnly(args: List<String>): Boolean {
            val command = args[0]
            return command in READ_ONLY
                || (command == "config" && args.size > 1 && args[1].startsWith("--get"))
                || (command == "branch" && "--show-current" in args)
                || (command == "tag" && "--list" in args)
        }
    }
}
