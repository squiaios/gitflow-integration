package gitflow.integration.core

/**
 * Runs git commands in one repository. A non-zero exit code is reported through
 * [GitResult], never thrown.
 *
 * The IDE supplies an implementation built on its own git executable and credential handling;
 * tests supply one that launches the `git` binary directly.
 */
fun interface GitRunner {

    /** Runs `git <args>` with the repository root as working directory. */
    fun run(args: List<String>): GitResult

    fun run(vararg args: String): GitResult = run(args.toList())
}
