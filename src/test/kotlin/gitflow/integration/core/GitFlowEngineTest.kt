package gitflow.integration.core

import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/** Runs the engine against real git repositories. */
class GitFlowEngineTest {

    @TempDir
    lateinit var tmp: Path

    private lateinit var repo: TestRepo
    private lateinit var engine: GitFlowEngine

    @BeforeEach
    fun setUp() {
        repo = TestRepo.create(tmp.resolve("work"))
        repo.commit("README.md", "hello\n", "Initial commit")
        engine = GitFlowEngine(repo)
    }

    // -------------------------------------------------------------------- helpers

    private fun assertSuccess(result: FlowResult) {
        assertTrue(result.isSuccess) {
            "expected success but was ${result.status}: ${result.error} ${result.detail}"
        }
    }

    private fun assertFailure(result: FlowResult, errorKey: String) {
        assertEquals(FlowResult.Status.FAILED, result.status) { result.error.toString() }
        assertEquals(errorKey, result.error!!.key)
    }

    private fun init() {
        assertSuccess(engine.init(InitOptions()))
    }

    private fun start(type: FlowType, name: String) {
        assertSuccess(engine.start(type, name, null, StartOptions()))
    }

    private fun keys(messages: List<Msg>): List<String> = messages.map { it.key }

    // ----------------------------------------------------------------------- init

    @Test
    fun initCreatesDevelopWritesTheConfigAndChecksItOut() {
        val result = engine.init(InitOptions())

        assertSuccess(result)
        assertTrue(engine.isInitialized())
        assertTrue(repo.branchExists("develop"))
        assertEquals("develop", repo.current())
        assertEquals("main", repo.git("config", "gitflow.branch.master"))
        assertEquals("develop", repo.git("config", "gitflow.branch.develop"))
        assertEquals("feature/", repo.git("config", "gitflow.prefix.feature"))
        assertEquals("support/", repo.git("config", "gitflow.prefix.support"))
        assertEquals("", repo.git("config", "gitflow.prefix.versiontag"))
    }

    @Test
    fun initInAnEmptyRepositoryCreatesTheInitialCommit() {
        val empty = TestRepo.create(tmp.resolve("empty"))
        val emptyEngine = GitFlowEngine(empty)

        assertSuccess(emptyEngine.init(InitOptions()))

        assertTrue(empty.branchExists("main"))
        assertTrue(empty.branchExists("develop"))
        assertEquals(1, empty.commitCount("main"))
    }

    @Test
    fun initRefusesToRunTwiceUnlessForced() {
        init()

        assertFailure(engine.init(InitOptions()), "error.alreadyInitialized")

        val forced = InitOptions()
        forced.force = true
        forced.versionTagPrefix = "v"
        assertSuccess(engine.init(forced))
        assertEquals("v", engine.loadConfig().versionTagPrefix)
    }

    @Test
    fun initFailsWhenTheProductionBranchDoesNotExist() {
        val options = InitOptions()
        options.master = "master"

        assertFailure(engine.init(options), "error.init.masterMissing")
        assertFalse(engine.isInitialized())
    }

    @Test
    fun initValidatesTheNamesItIsGiven() {
        val same = InitOptions()
        same.develop = "main"
        assertFailure(engine.init(same), "error.init.sameBranch")

        val badBranch = InitOptions()
        badBranch.develop = "dev elop"
        assertFailure(engine.init(badBranch), "validate.name.space")

        val emptyPrefix = InitOptions()
        emptyPrefix.prefixes[FlowType.HOTFIX] = ""
        assertFailure(engine.init(emptyPrefix), "error.init.prefixEmpty")

        val duplicate = InitOptions()
        duplicate.prefixes[FlowType.BUGFIX] = "feature/"
        assertFailure(engine.init(duplicate), "error.init.prefixDuplicate")
    }

    @Test
    fun initHonoursCustomPrefixesAndAnExistingDevelopBranch() {
        repo.git("branch", "dev")
        val options = InitOptions()
        options.develop = "dev"
        options.prefixes[FlowType.FEATURE] = "feat-"
        options.versionTagPrefix = "v"

        assertSuccess(engine.init(options))

        val config = engine.loadConfig()
        assertEquals("dev", config.develop)
        assertEquals("feat-", config.prefix(FlowType.FEATURE))
        assertEquals("v", config.versionTagPrefix)
    }

    @Test
    fun notInitializedUntilBothBranchesExist() {
        init()
        repo.git("checkout", "-q", "main")
        repo.git("branch", "-D", "develop")

        assertFalse(engine.isInitialized())
        assertFailure(engine.start(FlowType.FEATURE, "x", null, StartOptions()), "error.notInitialized")
    }

    // ---------------------------------------------------------------------- start

    @Test
    fun startCreatesTheBranchFromDevelopAndChecksItOut() {
        init()
        repo.commit("dev.txt", "d", "work on develop")

        val result = engine.start(FlowType.FEATURE, "login", null, StartOptions())

        assertSuccess(result)
        assertEquals("feature/login", repo.current())
        assertEquals(repo.rev("develop"), repo.rev("feature/login"))
        assertEquals(listOf("step.started"), keys(result.steps))
    }

    @Test
    fun featureAndBugfixCanStartFromAnyCommit() {
        init()
        repo.commit("a.txt", "a", "second")
        val first = repo.git("rev-list", "--max-parents=0", "HEAD")

        assertSuccess(engine.start(FlowType.BUGFIX, "old", first, StartOptions()))

        assertEquals(first, repo.rev("bugfix/old"))
    }

    @Test
    fun startRejectsAnExistingBranchAndInvalidNames() {
        init()
        start(FlowType.FEATURE, "login")
        repo.git("checkout", "-q", "develop")

        assertFailure(engine.start(FlowType.FEATURE, "login", null, StartOptions()), "error.branch.exists")
        assertFailure(engine.start(FlowType.FEATURE, "bad name", null, StartOptions()), "validate.name.space")
        assertFailure(engine.start(FlowType.FEATURE, "", null, StartOptions()), "validate.name.malformed")
        assertFailure(engine.start(FlowType.FEATURE, "x", "no-such-ref", StartOptions()), "error.base.invalid")
    }

    @Test
    fun onlyOneReleaseAndOneHotfixAtATime() {
        init()
        start(FlowType.RELEASE, "1.0.0")
        repo.git("checkout", "-q", "develop")
        assertFailure(engine.start(FlowType.RELEASE, "1.1.0", null, StartOptions()), "error.exists.single")

        start(FlowType.HOTFIX, "1.0.1")
        repo.git("checkout", "-q", "develop")
        assertFailure(engine.start(FlowType.HOTFIX, "1.0.2", null, StartOptions()), "error.exists.single")
    }

    @Test
    fun releaseStartsFromDevelopAndHotfixFromProduction() {
        init()
        repo.commit("dev.txt", "d", "develop only")

        start(FlowType.RELEASE, "1.0.0")
        assertEquals(repo.rev("develop"), repo.rev("release/1.0.0"))

        repo.git("checkout", "-q", "develop")
        start(FlowType.HOTFIX, "1.0.1")
        assertEquals(repo.rev("main"), repo.rev("hotfix/1.0.1"))
        assertFalse(repo.hasFile("hotfix/1.0.1", "dev.txt"))
    }

    @Test
    fun supportRequiresAStartingPoint() {
        init()
        repo.git("tag", "1.0.0", "main")

        assertFailure(engine.start(FlowType.SUPPORT, "1.x", null, StartOptions()), "error.base.required")
        assertFailure(engine.start(FlowType.SUPPORT, "1.x", "  ", StartOptions()), "error.base.required")

        assertSuccess(engine.start(FlowType.SUPPORT, "1.x", "1.0.0", StartOptions()))
        assertEquals("support/1.x", repo.current())
    }

    // --------------------------------------------------------- finish: feature / bugfix

    @Test
    fun finishFeatureMergesWithAMergeCommitAndDeletesTheBranch() {
        init()
        start(FlowType.FEATURE, "login")
        repo.commit("login.txt", "l", "add login")

        val result = engine.finish(FlowType.FEATURE, "login", FinishOptions())

        assertSuccess(result)
        assertEquals("develop", repo.current())
        assertTrue(repo.hasFile("develop", "login.txt"))
        assertEquals(2, repo.parentCount("develop"), "merge commit expected")
        assertFalse(repo.branchExists("feature/login"))
        assertEquals(listOf("step.merged", "step.branchDeleted"), keys(result.steps))
    }

    @Test
    fun finishFeatureCanFastForward() {
        init()
        start(FlowType.FEATURE, "login")
        repo.commit("login.txt", "l", "add login")
        val tip = repo.rev("feature/login")
        val options = FinishOptions()
        options.noFastForward = false

        assertSuccess(engine.finish(FlowType.FEATURE, "login", options))

        assertEquals(tip, repo.rev("develop"))
        assertEquals(1, repo.parentCount("develop"))
    }

    @Test
    fun finishFeatureCanSquash() {
        init()
        start(FlowType.FEATURE, "login")
        repo.commit("one.txt", "1", "first")
        repo.commit("two.txt", "2", "second")
        val before = repo.commitCount("develop")
        val options = FinishOptions()
        options.squash = true

        val result = engine.finish(FlowType.FEATURE, "login", options)

        assertSuccess(result)
        assertEquals(before + 1, repo.commitCount("develop"))
        assertEquals(1, repo.parentCount("develop"))
        assertTrue(repo.hasFile("develop", "one.txt") && repo.hasFile("develop", "two.txt"))
        assertFalse(repo.branchExists("feature/login"))
        assertTrue("step.squashed" in keys(result.steps))
    }

    @Test
    fun finishFeatureCanRebaseFirst() {
        init()
        start(FlowType.FEATURE, "login")
        repo.commit("login.txt", "l", "add login")
        repo.git("checkout", "-q", "develop")
        repo.commit("other.txt", "o", "develop moved on")
        val developTip = repo.rev("develop")
        repo.git("checkout", "-q", "feature/login")
        val options = FinishOptions()
        options.rebase = true

        val result = engine.finish(FlowType.FEATURE, "login", options)

        assertSuccess(result)
        assertEquals(developTip, repo.rev("develop^1"))
        assertEquals(developTip, repo.rev("develop^2^"), "feature commits were rebased onto the old develop tip")
        assertTrue("step.rebased" in keys(result.steps))
    }

    @Test
    fun finishFeatureCanKeepTheBranch() {
        init()
        start(FlowType.BUGFIX, "typo")
        repo.commit("typo.txt", "t", "fix typo")
        val options = FinishOptions()
        options.keep = true

        val result = engine.finish(FlowType.BUGFIX, "typo", options)

        assertSuccess(result)
        assertTrue(repo.branchExists("bugfix/typo"))
        assertTrue("step.kept" in keys(result.steps))
    }

    @Test
    fun finishRefusesADirtyWorkingTree() {
        init()
        start(FlowType.FEATURE, "login")
        repo.commit("login.txt", "l", "add login")
        Files.writeString(repo.dir.resolve("README.md"), "changed\n")

        val result = engine.finish(FlowType.FEATURE, "login", FinishOptions())

        assertFailure(result, "error.dirtyTree")
        assertTrue(result.detail.contains("README.md"))
        assertTrue(repo.branchExists("feature/login"))
    }

    @Test
    fun finishRejectsUnknownBranchesAndSupportBranches() {
        init()

        assertFailure(engine.finish(FlowType.FEATURE, "ghost", FinishOptions()), "error.branch.missing")

        repo.git("tag", "1.0.0", "main")
        assertSuccess(engine.start(FlowType.SUPPORT, "1.x", "1.0.0", StartOptions()))
        assertFailure(engine.finish(FlowType.SUPPORT, "1.x", FinishOptions()), "error.support.notFinishable")
    }

    @Test
    fun aConflictStopsTheFinishAndRunningItAgainResumesIt() {
        init()
        repo.commit("conf.txt", "base\n", "base")
        start(FlowType.FEATURE, "login")
        repo.commit("conf.txt", "feature\n", "feature change")
        repo.git("checkout", "-q", "develop")
        repo.commit("conf.txt", "develop\n", "develop change")

        val conflict = engine.finish(FlowType.FEATURE, "login", FinishOptions())

        assertEquals(FlowResult.Status.CONFLICT, conflict.status)
        assertEquals("error.conflict", conflict.error!!.key)
        assertEquals("develop", conflict.error.args[0])
        assertEquals("feature/login", conflict.error.args[1])
        assertEquals("conf.txt", conflict.detail)
        assertTrue(repo.branchExists("feature/login"), "branch must survive a conflict")

        repo.resolveConflict("conf.txt", "resolved\n")
        val resumed = engine.finish(FlowType.FEATURE, "login", FinishOptions())

        assertSuccess(resumed)
        assertEquals(listOf("step.alreadyMerged", "step.branchDeleted"), keys(resumed.steps))
        assertFalse(repo.branchExists("feature/login"))
        assertEquals("resolved", repo.git("show", "develop:conf.txt"))
    }

    // ------------------------------------------------------ finish: release / hotfix

    @Test
    fun finishReleaseMergesTagsAndMergesBack() {
        init()
        repo.commit("dev.txt", "d", "develop work")
        start(FlowType.RELEASE, "1.0.0")
        repo.commit("version.txt", "1.0.0", "bump version")
        val options = FinishOptions()
        options.tagMessage = "Version 1.0.0"

        val result = engine.finish(FlowType.RELEASE, "1.0.0", options)

        assertSuccess(result)
        assertEquals("develop", repo.current())
        assertTrue(repo.hasFile("main", "version.txt"))
        assertTrue(repo.hasFile("main", "dev.txt"))
        assertEquals("tag", repo.git("cat-file", "-t", "1.0.0"), "annotated tag expected")
        assertEquals("Version 1.0.0", repo.git("tag", "-l", "--format=%(contents:subject)", "1.0.0"))
        assertEquals(repo.rev("main"), repo.rev("1.0.0^{commit}"), "tag sits on the production merge commit")
        assertTrue(repo.isAncestor("1.0.0", "develop"), "tag was merged back into develop")
        assertTrue(repo.hasFile("develop", "version.txt"))
        assertFalse(repo.branchExists("release/1.0.0"))
        assertEquals(listOf("step.merged", "step.tagged", "step.merged", "step.branchDeleted"), keys(result.steps))
    }

    @Test
    fun finishUsesTheVersionTagPrefixAndADefaultMessage() {
        val options = InitOptions()
        options.versionTagPrefix = "v"
        assertSuccess(engine.init(options))
        start(FlowType.RELEASE, "2.0.0")
        repo.commit("a.txt", "a", "work")

        assertSuccess(engine.finish(FlowType.RELEASE, "2.0.0", FinishOptions()))

        assertEquals("Release 2.0.0", repo.git("tag", "-l", "--format=%(contents:subject)", "v2.0.0"))
    }

    @Test
    fun finishCanSkipTheTagAndTheBackMerge() {
        init()
        start(FlowType.RELEASE, "1.0.0")
        repo.commit("version.txt", "1.0.0", "bump")
        val options = FinishOptions()
        options.tag = false
        options.backMerge = false

        assertSuccess(engine.finish(FlowType.RELEASE, "1.0.0", options))

        assertEquals("", repo.git("tag", "-l"))
        assertFalse(repo.hasFile("develop", "version.txt"))
        assertTrue(repo.hasFile("main", "version.txt"))
        assertEquals("main", repo.current())
        assertFalse(repo.branchExists("release/1.0.0"))
    }

    @Test
    fun finishHotfixMergesIntoProductionAndDevelop() {
        init()
        repo.git("checkout", "-q", "main")
        start(FlowType.HOTFIX, "1.0.1")
        repo.commit("fix.txt", "f", "urgent fix")

        val result = engine.finish(FlowType.HOTFIX, "1.0.1", FinishOptions())

        assertSuccess(result)
        assertTrue(repo.hasFile("main", "fix.txt"))
        assertTrue(repo.hasFile("develop", "fix.txt"))
        assertEquals("Hotfix 1.0.1", repo.git("tag", "-l", "--format=%(contents:subject)", "1.0.1"))
        assertFalse(repo.branchExists("hotfix/1.0.1"))
        assertTrue(result.warnings.isEmpty())
    }

    @Test
    fun finishingAHotfixWarnsWhenAReleaseIsOpen() {
        init()
        start(FlowType.RELEASE, "2.0.0")
        repo.git("checkout", "-q", "develop")
        start(FlowType.HOTFIX, "1.0.1")
        repo.commit("fix.txt", "f", "urgent fix")

        val result = engine.finish(FlowType.HOTFIX, "1.0.1", FinishOptions())

        assertSuccess(result)
        assertEquals(listOf("warn.hotfixReleaseOpen"), keys(result.warnings))
        assertEquals("release/2.0.0", result.warnings[0].args[0])
    }

    @Test
    fun aConflictOnTheBackMergeIsResumableAndDoesNotTagTwice() {
        init()
        repo.commit("conf.txt", "base\n", "base")
        start(FlowType.RELEASE, "1.0.0")
        repo.commit("conf.txt", "release\n", "release change")
        repo.git("checkout", "-q", "develop")
        repo.commit("conf.txt", "develop\n", "develop change")

        val conflict = engine.finish(FlowType.RELEASE, "1.0.0", FinishOptions())

        assertEquals(FlowResult.Status.CONFLICT, conflict.status)
        assertEquals("develop", conflict.error!!.args[0])
        assertEquals("1.0.0", conflict.error.args[1], "the tag is what gets merged back")
        assertTrue(repo.branchExists("release/1.0.0"))
        val tagTarget = repo.rev("1.0.0^{commit}")

        repo.resolveConflict("conf.txt", "resolved\n")
        val resumed = engine.finish(FlowType.RELEASE, "1.0.0", FinishOptions())

        assertSuccess(resumed)
        assertEquals(tagTarget, repo.rev("1.0.0^{commit}"), "the existing tag is reused")
        assertTrue("step.alreadyMerged" in keys(resumed.steps))
        assertTrue("step.tagExists" in keys(resumed.steps))
        assertFalse(repo.branchExists("release/1.0.0"))
    }

    @Test
    fun finishFailsWhenTheTagAlreadyExistsElsewhere() {
        init()
        start(FlowType.RELEASE, "1.0.0")
        repo.commit("a.txt", "a", "work")
        repo.git("tag", "1.0.0", "main")

        val result = engine.finish(FlowType.RELEASE, "1.0.0", FinishOptions())

        assertFailure(result, "error.tag.exists")
        assertTrue(repo.branchExists("release/1.0.0"))
    }

    // ------------------------------------------------ remote: publish, track, delete

    private fun addRemote(): TestRepo {
        val remote = TestRepo.createBare(tmp.resolve("remote.git"))
        repo.git("remote", "add", "origin", remote.dir.toString())
        repo.git("push", "-q", "-u", "origin", "main")
        return remote
    }

    @Test
    fun publishPushesTheBranchAndSetsTheUpstream() {
        val remote = addRemote()
        init()
        start(FlowType.FEATURE, "login")
        repo.commit("login.txt", "l", "add login")

        assertSuccess(engine.publish("feature/login"))

        assertEquals("origin/feature/login", repo.git("rev-parse", "--abbrev-ref", "feature/login@{upstream}"))
        assertEquals(repo.rev("feature/login"), remote.rev("refs/heads/feature/login"))
    }

    @Test
    fun publishFailsWithoutARemote() {
        init()
        start(FlowType.FEATURE, "login")

        assertFailure(engine.publish("feature/login"), "error.noRemote")
    }

    @Test
    fun trackRecreatesALocalBranchFromTheRemote() {
        addRemote()
        init()
        start(FlowType.FEATURE, "login")
        repo.commit("login.txt", "l", "add login")
        assertSuccess(engine.publish("feature/login"))
        repo.git("checkout", "-q", "develop")
        repo.git("branch", "-D", "feature/login")

        assertSuccess(engine.track("feature/login"))

        assertEquals("feature/login", repo.current())
        assertTrue(repo.hasFile("feature/login", "login.txt"))
        assertEquals("origin/feature/login", repo.git("rev-parse", "--abbrev-ref", "feature/login@{upstream}"))
    }

    @Test
    fun checkoutTracksARemoteOnlyBranch() {
        addRemote()
        init()
        start(FlowType.FEATURE, "login")
        assertSuccess(engine.publish("feature/login"))
        repo.git("checkout", "-q", "develop")
        repo.git("branch", "-D", "feature/login")

        assertSuccess(engine.checkout("feature/login"))

        assertEquals("feature/login", repo.current())
    }

    @Test
    fun listBranchesReportsWhereEachBranchLives() {
        addRemote()
        init()
        start(FlowType.FEATURE, "local-only")
        repo.git("checkout", "-q", "develop")
        start(FlowType.FEATURE, "shared")
        assertSuccess(engine.publish("feature/shared"))
        repo.git("checkout", "-q", "develop")
        start(FlowType.BUGFIX, "remote-only")
        assertSuccess(engine.publish("bugfix/remote-only"))
        repo.git("checkout", "-q", "develop")
        repo.git("branch", "-D", "bugfix/remote-only")
        repo.git("checkout", "-q", "feature/shared")

        val branches = engine.listBranches()

        assertEquals(
            listOf(
                FlowBranch(FlowType.FEATURE, "local-only", "feature/local-only", true, false, false),
                FlowBranch(FlowType.FEATURE, "shared", "feature/shared", true, true, true),
                FlowBranch(FlowType.BUGFIX, "remote-only", "bugfix/remote-only", false, true, false),
            ),
            branches,
        )
    }

    @Test
    fun currentFlowBranchIsEmptyOnLongLivedBranches() {
        init()
        assertNull(engine.currentFlowBranch())

        start(FlowType.FEATURE, "login")
        val current = engine.currentFlowBranch()!!
        assertEquals(FlowType.FEATURE, current.type)
        assertEquals("login", current.name)
        assertEquals("feature/login", current.fullName)
    }

    @Test
    fun finishWithPushUpdatesTheRemoteAndDeletesTheRemoteBranch() {
        val remote = addRemote()
        init()
        repo.git("push", "-q", "origin", "develop")
        start(FlowType.FEATURE, "login")
        repo.commit("login.txt", "l", "add login")
        assertSuccess(engine.publish("feature/login"))
        val options = FinishOptions()
        options.push = true

        val result = engine.finish(FlowType.FEATURE, "login", options)

        assertSuccess(result)
        assertEquals(repo.rev("develop"), remote.rev("refs/heads/develop"))
        assertFalse(remote.tryGit("rev-parse", "--verify", "--quiet", "refs/heads/feature/login").ok)
        assertFalse(repo.branchExists("feature/login"))
    }

    @Test
    fun finishReleaseWithPushPublishesProductionDevelopAndTheTag() {
        val remote = addRemote()
        init()
        repo.git("push", "-q", "origin", "develop")
        start(FlowType.RELEASE, "1.0.0")
        repo.commit("version.txt", "1.0.0", "bump")
        val options = FinishOptions()
        options.push = true

        assertSuccess(engine.finish(FlowType.RELEASE, "1.0.0", options))

        assertEquals(repo.rev("main"), remote.rev("refs/heads/main"))
        assertEquals(repo.rev("develop"), remote.rev("refs/heads/develop"))
        assertEquals(repo.rev("refs/tags/1.0.0"), remote.rev("refs/tags/1.0.0"))
    }

    @Test
    fun startWithFetchWarnsWhenTheBaseIsBehindTheRemote() {
        val remote = addRemote()
        init()
        repo.git("push", "-q", "origin", "develop")
        // Someone else advances develop on the remote.
        val other = TestRepo.create(tmp.resolve("other"))
        other.git("remote", "add", "origin", remote.dir.toString())
        other.git("fetch", "-q", "origin")
        other.git("checkout", "-q", "-b", "develop", "origin/develop")
        other.commit("theirs.txt", "t", "their work")
        other.git("push", "-q", "origin", "develop")
        val options = StartOptions()
        options.fetch = true

        val result = engine.start(FlowType.FEATURE, "login", null, options)

        assertSuccess(result)
        assertEquals(listOf("warn.baseBehindRemote"), keys(result.warnings))
        assertEquals(listOf("step.fetched", "step.started"), keys(result.steps))
    }

    @Test
    fun startRefusesABranchThatAlreadyExistsOnTheRemote() {
        addRemote()
        init()
        start(FlowType.FEATURE, "login")
        assertSuccess(engine.publish("feature/login"))
        repo.git("checkout", "-q", "develop")
        repo.git("branch", "-D", "feature/login")

        assertFailure(engine.start(FlowType.FEATURE, "login", null, StartOptions()), "error.branch.existsRemote")
    }

    @Test
    fun deleteRemovesTheLocalAndRemoteBranches() {
        val remote = addRemote()
        init()
        start(FlowType.FEATURE, "login")
        assertSuccess(engine.publish("feature/login"))
        repo.git("checkout", "-q", "develop")

        assertSuccess(engine.delete("feature/login", true, true))

        assertFalse(repo.branchExists("feature/login"))
        assertFalse(remote.tryGit("rev-parse", "--verify", "--quiet", "refs/heads/feature/login").ok)
    }

    @Test
    fun deleteRefusesTheCurrentBranchAndUnmergedWork() {
        init()
        start(FlowType.FEATURE, "login")
        repo.commit("login.txt", "l", "add login")

        assertFailure(engine.delete("feature/login", false, false), "error.delete.current")

        repo.git("checkout", "-q", "develop")
        val unmerged = engine.delete("feature/login", false, false)
        assertFailure(unmerged, "error.git")
        assertTrue(repo.branchExists("feature/login"))

        assertSuccess(engine.delete("feature/login", true, false))
        assertFalse(repo.branchExists("feature/login"))
    }

    @Test
    fun localBranchesAndTagsAreListed() {
        init()
        repo.git("tag", "-a", "-m", "one", "1.0.0", "main")
        repo.commit("a.txt", "a", "next")
        repo.git("tag", "-a", "-m", "two", "1.1.0")

        assertEquals(listOf("develop", "main"), engine.localBranches())
        assertEquals(setOf("1.0.0", "1.1.0"), engine.recentTags(10).toSet())
        assertEquals(1, engine.recentTags(1).size)
        assertNull(engine.remote())
    }
}
