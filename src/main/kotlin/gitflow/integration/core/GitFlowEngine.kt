package gitflow.integration.core

/**
 * Native implementation of git flow on top of plain git commands.
 *
 * It needs no `git flow` executable and reads and writes the `gitflow.*` git config keys.
 * Every operation is resumable: finishing a branch checks what is already merged or tagged
 * before acting, so after a conflict you resolve it, commit, and run the same operation again -
 * exactly as with the command line tool.
 *
 * Methods return a [FlowResult] instead of throwing; they never touch the user interface.
 */
class GitFlowEngine(private val git: GitRunner) {

    // ------------------------------------------------------------------ queries

    fun loadConfig(): FlowConfig {
        val result = git.run("config", "--get-regexp", "^gitflow\\.")
        return if (result.ok) FlowConfig.parse(result.stdout) else FlowConfig.empty()
    }

    /** Configured, with two distinct long-lived branches that both exist locally. */
    fun isInitialized(config: FlowConfig = loadConfig()): Boolean =
        config.isConfigured
            && config.master != config.develop
            && refExists("refs/heads/" + config.master)
            && refExists("refs/heads/" + config.develop)

    /** The checked-out branch; empty when HEAD is detached. */
    fun currentBranch(): String {
        val result = git.run("branch", "--show-current")
        return if (result.ok) result.out else ""
    }

    fun currentFlowBranch(): FlowBranch? {
        val config = loadConfig()
        val current = currentBranch()
        if (!config.isConfigured || current.isEmpty()) {
            return null
        }
        return config.classify(current)?.let { ref ->
            FlowBranch(ref.type, ref.name, current, true, remoteBranchExists(current), true)
        }
    }

    fun localBranches(): List<String> {
        val result = git.run("for-each-ref", "--format=%(refname:short)", "refs/heads")
        return if (result.ok) result.lines() else emptyList()
    }

    /** Most recent tags first, at most [limit]. */
    fun recentTags(limit: Int): List<String> {
        val result = git.run("tag", "--list", "--sort=-creatordate")
        if (!result.ok) {
            return emptyList()
        }
        return result.lines().take(limit)
    }

    /** Remote (when any) used for fetching and pushing: `origin`, else the first remote. */
    fun remote(): String? = remoteName()

    /**
     * All flow branches, local and remote, ordered by type then name. The configuration is loaded
     * when not given.
     */
    fun listBranches(config: FlowConfig = loadConfig()): List<FlowBranch> {
        if (!config.isConfigured) {
            return emptyList()
        }
        val result = git.run("for-each-ref", "--format=%(refname)", "refs/heads", "refs/remotes")
        if (!result.ok) {
            return emptyList()
        }
        val remotes = remotes()
        val current = currentBranch()
        // branch name -> {existsLocally, existsOnRemote}
        val found = LinkedHashMap<String, BooleanArray>()
        for (ref in result.lines()) {
            val name: String
            val local: Boolean
            if (ref.startsWith("refs/heads/")) {
                name = ref.removePrefix("refs/heads/")
                local = true
            } else if (ref.startsWith("refs/remotes/")) {
                val rest = ref.removePrefix("refs/remotes/")
                val owner = remotes.filter { rest.startsWith("$it/") }.maxByOrNull { it.length } ?: continue
                name = rest.substring(owner.length + 1)
                local = false
            } else {
                continue
            }
            if (name == "HEAD" || config.classify(name) == null) {
                continue
            }
            found.getOrPut(name) { BooleanArray(2) }[if (local) 0 else 1] = true
        }
        return found
            .map { (name, where) ->
                val ref = checkNotNull(config.classify(name))
                FlowBranch(ref.type, ref.name, name, where[0], where[1], name == current)
            }
            .sortedWith(compareBy<FlowBranch> { it.type }.thenBy { it.name })
    }

    // --------------------------------------------------------------------- init

    /** Writes the git flow configuration, creating the integration branch when needed. */
    fun init(options: InitOptions): FlowResult {
        val run = Run()
        try {
            validate(options)
            if (!options.force && isInitialized(loadConfig())) {
                throw FlowException.of("error.alreadyInitialized")
            }
            val master = options.master.trim()
            val develop = options.develop.trim()
            val remote = remoteName()

            if (!refExists("HEAD")) {
                // Brand new repository: git flow needs a commit to branch from.
                if (master != currentBranch()) {
                    run.must("checkout", "--orphan", master)
                }
                run.must("commit", "--allow-empty", "-m", "Initial commit")
                run.step("step.initialCommit", master)
            } else if (!refExists("refs/heads/$master")) {
                if (remote != null && refExists("refs/remotes/$remote/$master")) {
                    run.must("branch", "--track", master, "$remote/$master")
                    run.step("step.branchTracked", master, remote)
                } else {
                    throw FlowException.of("error.init.masterMissing", master)
                }
            }
            if (!refExists("refs/heads/$develop")) {
                if (remote != null && refExists("refs/remotes/$remote/$develop")) {
                    run.must("branch", "--track", develop, "$remote/$develop")
                    run.step("step.branchTracked", develop, remote)
                } else {
                    run.must("branch", develop, master)
                    run.step("step.branchCreated", develop, master)
                }
            }

            run.must("config", FlowConfig.MASTER_KEY, master)
            run.must("config", FlowConfig.DEVELOP_KEY, develop)
            for (type in FlowType.entries) {
                run.must("config", type.configKey, options.prefixes.getValue(type))
            }
            run.must("config", FlowConfig.VERSION_TAG_KEY, options.versionTagPrefix)
            run.step("step.configured")

            run.checkout(develop)
            return run.success()
        } catch (e: FlowException) {
            return run.failure(e)
        }
    }

    private fun validate(options: InitOptions) {
        val master = options.master.trim()
        val develop = options.develop.trim()
        for (branch in listOf(master, develop)) {
            RefNames.validateBranchName(branch)?.let { throw FlowException.of(it) }
        }
        if (master == develop) {
            throw FlowException.of("error.init.sameBranch")
        }
        val seen = LinkedHashMap<String, FlowType>()
        for (type in FlowType.entries) {
            val prefix = options.prefixes[type]
            if (prefix.isNullOrEmpty()) {
                throw FlowException.of("error.init.prefixEmpty", type.id)
            }
            RefNames.validateBranchName(prefix + "x")?.let { throw FlowException.of(it) }
            val other = seen.put(prefix, type)
            if (other != null) {
                throw FlowException.of("error.init.prefixDuplicate", other.id, type.id)
            }
        }
        if (options.versionTagPrefix.isNotEmpty()
            && RefNames.validateBranchName(options.versionTagPrefix + "x") != null
        ) {
            throw FlowException.of("error.init.tagPrefixInvalid")
        }
    }

    // -------------------------------------------------------------------- start

    /**
     * Creates `<prefix><name>` and checks it out.
     *
     * @param base starting point for feature, bugfix and support branches; required for support,
     *             defaults to the long-lived branch of the type otherwise, and is ignored for
     *             release and hotfix branches
     */
    fun start(type: FlowType, name: String, base: String?, options: StartOptions): FlowResult {
        val run = Run()
        try {
            val config = run.requireInitialized()
            RefNames.validateBranchName(config.branchName(type, name))?.let { throw FlowException.of(it) }
            val branch = config.branchName(type, name)
            if (refExists("refs/heads/$branch")) {
                throw FlowException.of("error.branch.exists", branch)
            }
            val remote = remoteName()
            if (options.fetch) {
                run.fetch(remote)
            }
            if (remote != null && refExists("refs/remotes/$remote/$branch")) {
                throw FlowException.of("error.branch.existsRemote", branch)
            }
            if (type.versioned) {
                for (existing in listBranches(config)) {
                    if (existing.type == type) {
                        throw FlowException.of("error.exists.single", type.id, existing.name)
                    }
                }
            }

            val start = resolveStart(config, type, base)
            run.warnIfBehindRemote(remote, start)
            run.must("checkout", "-b", branch, start)
            run.step("step.started", branch, start)
            return run.success()
        } catch (e: FlowException) {
            return run.failure(e)
        }
    }

    private fun resolveStart(config: FlowConfig, type: FlowType, base: String?): String {
        if (!type.customBase) {
            return config.baseBranch(type)
        }
        val requested = base?.trim() ?: ""
        if (requested.isEmpty()) {
            if (type == FlowType.SUPPORT) {
                throw FlowException.of("error.base.required")
            }
            return config.baseBranch(type)
        }
        if (commitOf(requested) == null) {
            throw FlowException.of("error.base.invalid", requested)
        }
        return requested
    }

    // ------------------------------------------------------------------- finish

    /** Merges `<prefix><name>` back, tags it when it is a release or hotfix, and cleans up. */
    fun finish(type: FlowType, name: String, options: FinishOptions): FlowResult {
        val run = Run()
        try {
            if (!type.finishable) {
                throw FlowException.of("error.support.notFinishable")
            }
            val config = run.requireInitialized()
            val branch = config.branchName(type, name)
            if (!refExists("refs/heads/$branch")) {
                val remote = remoteName()
                val onRemote = remote != null && refExists("refs/remotes/$remote/$branch")
                throw FlowException.of(if (onRemote) "error.branch.remoteOnly" else "error.branch.missing", branch)
            }
            run.requireCleanTree()
            if (options.fetch) {
                run.fetch(remoteName())
            }
            if (type.versioned) {
                finishVersioned(run, config, type, name, options)
            } else {
                finishIntoDevelop(run, config, type, name, options)
            }
            return run.success()
        } catch (e: FlowException) {
            return run.failure(e)
        }
    }

    /** Feature and bugfix: merge into develop. */
    private fun finishIntoDevelop(run: Run, config: FlowConfig, type: FlowType, name: String, o: FinishOptions) {
        val branch = config.branchName(type, name)
        val develop = config.develop

        if (o.rebase) {
            run.checkout(branch)
            run.rebase(branch, develop)
        }
        run.checkout(develop)
        if (isAncestor(branch, develop)) {
            run.step("step.alreadyMerged", branch, develop)
        } else if (o.squash) {
            run.squash(branch, develop)
        } else {
            run.merge(branch, develop, o.noFastForward)
        }

        val remote = remoteName()
        if (o.push) {
            run.push(remote, develop)
            run.deleteRemoteBranch(remote, branch, o.keep)
        }
        if (!o.keep) {
            run.deleteLocalBranch(branch, develop, o.squash)
        } else {
            run.step("step.kept", branch)
        }
    }

    /** Release and hotfix: merge into production, tag, merge back into develop. */
    private fun finishVersioned(run: Run, config: FlowConfig, type: FlowType, name: String, o: FinishOptions) {
        val branch = config.branchName(type, name)
        val master = config.master
        val develop = config.develop
        val tag = config.tagName(name)

        run.checkout(master)
        if (isAncestor(branch, master)) {
            run.step("step.alreadyMerged", branch, master)
        } else {
            run.merge(branch, master, true)
        }

        var mergeBack = branch
        if (o.tag) {
            val existing = commitOf("refs/tags/$tag")
            if (existing == null) {
                val message = if (o.tagMessage.isBlank()) {
                    (if (type == FlowType.RELEASE) "Release " else "Hotfix ") + name
                } else {
                    o.tagMessage.trim()
                }
                run.must("tag", "-a", "-m", message, tag, master)
                run.step("step.tagged", tag)
            } else if (isAncestor(branch, existing)) {
                run.step("step.tagExists", tag)
            } else {
                throw FlowException.of("error.tag.exists", tag)
            }
            mergeBack = tag
        }

        if (o.backMerge) {
            run.checkout(develop)
            if (isAncestor(mergeBack, develop)) {
                run.step("step.alreadyMerged", mergeBack, develop)
            } else {
                run.merge(mergeBack, develop, true)
            }
            if (type == FlowType.HOTFIX) {
                for (release in listBranches(config)) {
                    if (release.type == FlowType.RELEASE && release.local) {
                        run.warn("warn.hotfixReleaseOpen", release.fullName)
                    }
                }
            }
        }

        val remote = remoteName()
        if (o.push) {
            val refs = mutableListOf(master)
            if (o.backMerge) {
                refs.add(develop)
            }
            if (o.tag) {
                refs.add("refs/tags/$tag")
            }
            run.push(remote, *refs.toTypedArray())
            run.deleteRemoteBranch(remote, branch, o.keep)
        }
        if (!o.keep) {
            run.deleteLocalBranch(branch, master, false)
        } else {
            run.step("step.kept", branch)
        }
    }

    // ------------------------------------------------- publish, track, delete, checkout

    /** Pushes a local flow branch to the remote and makes it track the remote branch. */
    fun publish(branch: String): FlowResult {
        val run = Run()
        try {
            if (!refExists("refs/heads/$branch")) {
                throw FlowException.of("error.branch.missing", branch)
            }
            val remote = remoteName() ?: throw FlowException.of("error.noRemote")
            run.must("push", "--set-upstream", remote, branch)
            run.step("step.published", branch, remote)
            return run.success()
        } catch (e: FlowException) {
            return run.failure(e)
        }
    }

    /** Creates a local branch tracking a remote flow branch and checks it out. */
    fun track(branch: String): FlowResult {
        val run = Run()
        try {
            val remote = remoteName() ?: throw FlowException.of("error.noRemote")
            run.fetch(remote)
            if (refExists("refs/heads/$branch")) {
                throw FlowException.of("error.branch.exists", branch)
            }
            if (!refExists("refs/remotes/$remote/$branch")) {
                throw FlowException.of("error.remote.branchMissing", branch, remote)
            }
            run.must("checkout", "-b", branch, "--track", "$remote/$branch")
            run.step("step.tracked", branch, remote)
            return run.success()
        } catch (e: FlowException) {
            return run.failure(e)
        }
    }

    /** Deletes a flow branch locally and, optionally, on the remote. */
    fun delete(branch: String, force: Boolean, alsoRemote: Boolean): FlowResult {
        val run = Run()
        try {
            if (branch == currentBranch()) {
                throw FlowException.of("error.delete.current", branch)
            }
            if (refExists("refs/heads/$branch")) {
                run.must("branch", if (force) "-D" else "-d", branch)
                run.step("step.branchDeleted", branch)
            }
            if (alsoRemote) {
                val remote = remoteName() ?: throw FlowException.of("error.noRemote")
                if (refExists("refs/remotes/$remote/$branch")) {
                    run.must("push", remote, "--delete", branch)
                    run.step("step.remoteBranchDeleted", branch, remote)
                }
            }
            return run.success()
        } catch (e: FlowException) {
            return run.failure(e)
        }
    }

    /** Checks out a flow branch, creating a tracking branch first when it only exists remotely. */
    fun checkout(branch: String): FlowResult {
        if (refExists("refs/heads/$branch")) {
            val run = Run()
            try {
                run.must("checkout", branch)
                run.step("step.checkedOut", branch)
                return run.success()
            } catch (e: FlowException) {
                return run.failure(e)
            }
        }
        return track(branch)
    }

    // ------------------------------------------------------------------ helpers

    private fun refExists(ref: String): Boolean = git.run("rev-parse", "--verify", "--quiet", ref).ok

    /** The commit [ref] points to (peeling tags), or `null` when it does not resolve. */
    private fun commitOf(ref: String): String? {
        val result = git.run("rev-parse", "--verify", "--quiet", "$ref^{commit}")
        return if (result.ok) result.out else null
    }

    private fun isAncestor(ancestor: String, descendant: String): Boolean =
        git.run("merge-base", "--is-ancestor", ancestor, descendant).ok

    private fun remotes(): List<String> {
        val result = git.run("remote")
        return if (result.ok) result.lines() else emptyList()
    }

    private fun remoteName(): String? {
        val remotes = remotes()
        if (remotes.isEmpty()) {
            return null
        }
        return if ("origin" in remotes) "origin" else remotes[0]
    }

    private fun remoteBranchExists(branch: String): Boolean {
        val remote = remoteName()
        return remote != null && refExists("refs/remotes/$remote/$branch")
    }

    private fun unmergedFiles(): List<String> {
        val result = git.run("diff", "--name-only", "--diff-filter=U")
        return if (result.ok) result.lines() else emptyList()
    }

    /** State of one operation: what has been done so far, plus the git steps shared by all of them. */
    private inner class Run {
        private val steps = mutableListOf<Msg>()
        private val warnings = mutableListOf<Msg>()

        fun step(key: String, vararg args: Any?) {
            steps.add(Msg(key, *args))
        }

        fun warn(key: String, vararg args: Any?) {
            warnings.add(Msg(key, *args))
        }

        fun success(): FlowResult = FlowResult(FlowResult.Status.SUCCESS, steps, warnings, null, "")

        fun failure(e: FlowException): FlowResult {
            val status = if (e.isConflict) FlowResult.Status.CONFLICT else FlowResult.Status.FAILED
            return FlowResult(status, steps, warnings, e.msg, e.detail)
        }

        fun must(vararg args: String): GitResult = must(args.toList())

        fun must(args: List<String>): GitResult {
            val result = git.run(args)
            if (!result.ok) {
                throw FlowException.git(args, result)
            }
            return result
        }

        fun requireInitialized(): FlowConfig {
            val config = loadConfig()
            if (!isInitialized(config)) {
                throw FlowException.of("error.notInitialized")
            }
            return config
        }

        fun requireCleanTree() {
            val status = git.run("status", "--porcelain", "--untracked-files=no")
            if (!status.ok) {
                throw FlowException.git(listOf("status"), status)
            }
            if (status.out.isNotEmpty()) {
                throw FlowException.withDetail(status.out, "error.dirtyTree")
            }
        }

        fun fetch(remote: String?) {
            if (remote == null) {
                warn("warn.noRemote")
                return
            }
            must("fetch", "--quiet", remote)
            step("step.fetched", remote)
        }

        /** Warns when [branch] is behind its counterpart on the remote. */
        fun warnIfBehindRemote(remote: String?, branch: String) {
            if (remote == null || !refExists("refs/heads/$branch") || !refExists("refs/remotes/$remote/$branch")) {
                return
            }
            val behind = git.run("rev-list", "--count", "$branch..refs/remotes/$remote/$branch")
            if (behind.ok && behind.out != "0") {
                warn("warn.baseBehindRemote", branch, remote, behind.out)
            }
        }

        fun checkout(branch: String) {
            if (branch != currentBranch()) {
                must("checkout", branch)
            }
        }

        fun merge(ref: String, into: String, noFastForward: Boolean) {
            val args = listOf("merge", if (noFastForward) "--no-ff" else "--ff", "--no-edit", ref)
            val result = git.run(args)
            if (!result.ok) {
                throw failedMerge(args, result, "error.conflict", into, ref)
            }
            step("step.merged", ref, into)
        }

        fun squash(ref: String, into: String) {
            val args = listOf("merge", "--squash", ref)
            val result = git.run(args)
            if (!result.ok) {
                throw failedMerge(args, result, "error.conflict", into, ref)
            }
            // After a resumed squash the changes may already be committed: nothing left to commit.
            if (!git.run("diff", "--cached", "--quiet").ok) {
                must("commit", "-m", "Merge branch '$ref' into $into (squashed)")
            }
            step("step.squashed", ref, into)
        }

        fun rebase(branch: String, onto: String) {
            val args = listOf("rebase", onto)
            val result = git.run(args)
            if (!result.ok) {
                throw failedMerge(args, result, "error.conflict.rebase", branch, onto)
            }
            step("step.rebased", branch, onto)
        }

        private fun failedMerge(args: List<String>, result: GitResult, key: String, vararg keyArgs: Any?): FlowException {
            val conflicts = unmergedFiles()
            return if (conflicts.isEmpty()) {
                FlowException.git(args, result)
            } else {
                FlowException.conflict(conflicts.joinToString("\n"), key, *keyArgs)
            }
        }

        fun push(remote: String?, vararg refs: String) {
            if (remote == null) {
                warn("warn.noRemote")
                return
            }
            must(listOf("push", remote) + refs)
            step("step.pushed", refs.joinToString(", ").replace("refs/tags/", ""), remote)
        }

        fun deleteRemoteBranch(remote: String?, branch: String, keep: Boolean) {
            if (keep || remote == null || !refExists("refs/remotes/$remote/$branch")) {
                return
            }
            must("push", remote, "--delete", branch)
            step("step.remoteBranchDeleted", branch, remote)
        }

        /**
         * Deletes a branch after it has been merged. [mergedInto] is verified first so that
         * git's own "fully merged" test - which looks at the upstream rather than at HEAD - cannot
         * refuse to delete a branch that is in fact merged. A squashed branch never is, as far as
         * git can tell, so it is always deleted forcibly.
         */
        fun deleteLocalBranch(branch: String, mergedInto: String, squashed: Boolean) {
            if (!squashed && !isAncestor(branch, mergedInto)) {
                warn("warn.branchNotMerged", branch, mergedInto)
                return
            }
            val result = git.run("branch", "-D", branch)
            if (result.ok) {
                step("step.branchDeleted", branch)
            } else {
                warn("warn.branchNotDeleted", branch, result.message)
            }
        }
    }
}
