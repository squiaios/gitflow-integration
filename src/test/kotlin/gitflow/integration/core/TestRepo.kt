package gitflow.integration.core

import java.io.IOException
import java.io.InputStream
import java.io.UncheckedIOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CompletableFuture

/** A throw-away git repository that also serves as the engine's [GitRunner]. */
internal class TestRepo private constructor(val dir: Path) : GitRunner {

    override fun run(args: List<String>): GitResult {
        val command = listOf("git") + args
        val builder = ProcessBuilder(command).directory(dir.toFile())
        val env = builder.environment()
        // Isolate from the developer's own git configuration (hooks, signing, aliases...).
        val nullDevice = if (System.getProperty("os.name").startsWith("Windows")) "NUL" else "/dev/null"
        env["GIT_CONFIG_GLOBAL"] = nullDevice
        env["GIT_CONFIG_NOSYSTEM"] = "1"
        env["GIT_TERMINAL_PROMPT"] = "0"
        env["GIT_EDITOR"] = "true"
        env["GIT_AUTHOR_NAME"] = "Test"
        env["GIT_AUTHOR_EMAIL"] = "test@example.com"
        env["GIT_COMMITTER_NAME"] = "Test"
        env["GIT_COMMITTER_EMAIL"] = "test@example.com"
        return try {
            val process = builder.start()
            process.outputStream.close()
            val out = CompletableFuture.supplyAsync { read(process.inputStream) }
            val err = CompletableFuture.supplyAsync { read(process.errorStream) }
            val exit = process.waitFor()
            GitResult(exit, out.join(), err.join())
        } catch (e: IOException) {
            GitResult.failure(e.message ?: "")
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            GitResult.failure("interrupted")
        }
    }

    /** Runs git and fails the test when it does not succeed; returns trimmed standard output. */
    fun git(vararg args: String): String = git(args.toList())

    fun git(args: List<String>): String {
        val result = run(args)
        if (!result.ok) {
            throw AssertionError("git " + args.joinToString(" ") + " failed: " + result.message)
        }
        return result.out
    }

    fun tryGit(vararg args: String): GitResult = run(args.toList())

    /** Writes [content] to [file], stages it and commits. */
    fun commit(file: String, content: String, message: String) {
        try {
            val path = dir.resolve(file)
            Files.createDirectories(path.parent)
            Files.writeString(path, content)
        } catch (e: IOException) {
            throw UncheckedIOException(e)
        }
        git("add", file)
        git("commit", "-q", "-m", message)
    }

    /** Resolves a merge conflict by taking [content] for [file] and committing the merge. */
    fun resolveConflict(file: String, content: String) {
        try {
            Files.writeString(dir.resolve(file), content)
        } catch (e: IOException) {
            throw UncheckedIOException(e)
        }
        git("add", file)
        git("commit", "-q", "--no-edit")
    }

    fun branchExists(branch: String): Boolean = tryGit("rev-parse", "--verify", "--quiet", "refs/heads/$branch").ok

    fun hasFile(ref: String, file: String): Boolean = tryGit("cat-file", "-e", "$ref:$file").ok

    fun isAncestor(ancestor: String, descendant: String): Boolean =
        tryGit("merge-base", "--is-ancestor", ancestor, descendant).ok

    fun rev(ref: String): String = git("rev-parse", ref)

    fun commitCount(ref: String): Int = git("rev-list", "--count", ref).toInt()

    fun parentCount(ref: String): Int = git("rev-list", "--parents", "-n", "1", ref).split(" ").size - 1

    fun current(): String = git("branch", "--show-current")

    companion object {
        fun create(dir: Path): TestRepo = init(dir, false)

        fun createBare(dir: Path): TestRepo = init(dir, true)

        private fun init(dir: Path, bare: Boolean): TestRepo {
            try {
                Files.createDirectories(dir)
            } catch (e: IOException) {
                throw UncheckedIOException(e)
            }
            val repo = TestRepo(dir)
            repo.git(if (bare) listOf("init", "-q", "--bare", "-b", "main") else listOf("init", "-q", "-b", "main"))
            return repo
        }

        private fun read(stream: InputStream): String {
            try {
                return String(stream.readAllBytes(), Charsets.UTF_8)
            } catch (e: IOException) {
                throw UncheckedIOException(e)
            }
        }
    }
}
