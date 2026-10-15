package gitflow.integration.ide

import gitflow.integration.core.FlowConfig
import gitflow.integration.core.FlowResult
import gitflow.integration.core.GitFlowEngine
import gitflow.integration.ide.ui.GitFlowStatusBarWidgetFactory
import com.intellij.dvcs.DvcsUtil
import com.intellij.dvcs.repo.VcsRepositoryManager
import com.intellij.dvcs.repo.VcsRepositoryMappingListener
import com.intellij.ide.ActivityTracker
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.wm.impl.status.widget.StatusBarWidgetsManager
import com.intellij.util.messages.Topic
import git4idea.GitUtil
import git4idea.branch.GitBranchUtil
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryChangeListener
import git4idea.repo.GitRepositoryManager
import java.util.concurrent.ConcurrentHashMap
import org.jetbrains.annotations.Nls

/**
 * Entry point of the plugin's IDE side: finds the repository to act on, builds engines, and runs
 * operations in the background, reporting their outcome with a notification.
 */
@Service(Service.Level.PROJECT)
class GitFlowService(private val project: Project) : Disposable {

    private data class CachedConfig(val config: FlowConfig, val loadedAt: Long)

    private val configs = ConcurrentHashMap<String, CachedConfig>()
    private val loading: MutableSet<String> = ConcurrentHashMap.newKeySet()

    init {
        val connection = project.messageBus.connect(this)
        connection.subscribe(GitRepository.GIT_REPO_CHANGE, GitRepositoryChangeListener { invalidate(it) })
        // The status bar widget only exists in projects that have a git repository, which may be
        // known only after startup: tell the status bar to look again whenever the mappings change.
        connection.subscribe(
            VcsRepositoryManager.VCS_REPOSITORY_MAPPING_UPDATED,
            VcsRepositoryMappingListener {
                project.getService(StatusBarWidgetsManager::class.java)
                    .updateWidget(GitFlowStatusBarWidgetFactory::class.java)
            },
        )
    }

    override fun dispose() {
        configs.clear()
    }

    fun repositories(): List<GitRepository> = GitRepositoryManager.getInstance(project).repositories

    /**
     * The repository an action should act on: the one shown in the Git Flow tool window if the
     * action was invoked from there, else the one of the current file, else the first one.
     */
    fun guessRepository(context: DataContext): GitRepository? {
        GitFlowDataKeys.REPOSITORY.getData(context)?.let { return it }
        GitBranchUtil.guessRepositoryForOperation(project, context)?.let { return it }
        return repositories().firstOrNull()
    }

    /**
     * The repository that project-wide UI such as the status bar describes: the one of the file
     * being edited, else the one at the project root, else the first. Unlike
     * [guessRepository] it needs no data context, and unlike the IDE's own guess it never
     * comes back empty while the project has a repository - with no file open, which is the state
     * of a freshly opened project, that guess has nothing to go on. EDT only.
     */
    fun currentRepository(): GitRepository? {
        val manager = GitRepositoryManager.getInstance(project)
        val all = manager.repositories
        if (all.isEmpty()) {
            return null
        }
        if (all.size == 1) {
            return all[0]
        }
        for (file in FileEditorManager.getInstance(project).selectedFiles) {
            manager.getRepositoryForFileQuick(file)?.let { return it }
        }
        val projectDir = project.guessProjectDir()
        val atRoot = projectDir?.let { manager.getRepositoryForRootQuick(it) }
        return atRoot ?: all[0]
    }

    fun engine(repository: GitRepository): GitFlowEngine = GitFlowEngine(IdeGitRunner(project, repository))

    /**
     * The git flow configuration of [repository] as last read: possibly stale, and
     * [empty][FlowConfig.empty] before the first read completes. Never runs git, so it
     * is safe in action updates; see [CONFIG_TTL_MS].
     */
    fun cachedConfig(repository: GitRepository): FlowConfig = loadedConfig(repository) ?: FlowConfig.empty()

    /**
     * Like [cachedConfig], but `null` while the first read is still under way, for
     * callers that must not mistake "not read yet" for "not initialized".
     */
    fun loadedConfig(repository: GitRepository): FlowConfig? {
        val cached = configs[key(repository)]
        if (cached == null || System.currentTimeMillis() - cached.loadedAt >= CONFIG_TTL_MS) {
            reloadConfigAsync(repository)
        }
        return cached?.config
    }

    private fun reloadConfigAsync(repository: GitRepository) {
        val key = key(repository)
        if (project.isDisposed || !loading.add(key)) {
            return
        }
        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                store(repository, engine(repository).loadConfig())
            } finally {
                loading.remove(key)
            }
        }
    }

    /** Caches a freshly read configuration and tells whoever displays it if it changed. */
    private fun store(repository: GitRepository, config: FlowConfig) {
        val previous = configs.put(key(repository), CachedConfig(config, System.currentTimeMillis()))
        if (previous == null || previous.config != config) {
            // Actions and the status bar were updated with the old value: have them look again.
            ActivityTracker.getInstance().inc()
            if (!project.isDisposed) {
                project.messageBus.syncPublisher(CONFIG_CHANGED).run()
            }
        }
    }

    /** Marks the cached configuration stale, keeping its value until a fresh one is read. */
    fun invalidate(repository: GitRepository) {
        configs.computeIfPresent(key(repository)) { _, cached -> CachedConfig(cached.config, 0) }
    }

    fun invalidateAll() {
        configs.replaceAll { _, cached -> CachedConfig(cached.config, 0) }
    }

    private fun key(repository: GitRepository): String = repository.root.path

    /** Reads everything the dialogs need. Not for the EDT. */
    fun readContext(repository: GitRepository): FlowContext {
        val engine = engine(repository)
        val config = engine.loadConfig()
        store(repository, config)
        return FlowContext(
            repository,
            config,
            engine.isInitialized(config),
            engine.currentBranch(),
            engine.remote(),
            engine.localBranches(),
            engine.recentTags(50),
            engine.listBranches(config),
        )
    }

    /** Reads the context in the background, then hands it to [onLoaded] on the EDT. */
    fun loadContext(repository: GitRepository, onLoaded: (FlowContext) -> Unit) {
        object : Task.Backgroundable(project, GitFlowBundle.message("progress.loading"), true) {
            private var context: FlowContext? = null

            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = true
                context = readContext(repository)
            }

            override fun onSuccess() {
                context?.let(onLoaded)
            }
        }.queue()
    }

    /**
     * Runs [operation] in the background and reports its outcome. Call from the EDT.
     *
     * @param retry what the "finish again" notification action does after a conflict; may be null
     */
    fun run(
        repository: GitRepository,
        @Nls title: String,
        retry: (() -> Unit)? = null,
        operation: (GitFlowEngine) -> FlowResult,
    ) {
        ApplicationManager.getApplication().assertIsDispatchThread()
        // Unsaved editor content would be missing from commits and could clash with the checkout.
        FileDocumentManager.getInstance().saveAllDocuments()
        object : Task.Backgroundable(project, title, false) {
            private var result: FlowResult? = null

            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = true
                val token = DvcsUtil.workingTreeChangeStarted(project, title)
                try {
                    result = operation(engine(repository))
                } finally {
                    token.finish()
                    GitUtil.refreshVfsInRoot(repository.root)
                    repository.update()
                    // The operation may have changed the configuration (init) without any repository event.
                    invalidate(repository)
                    reloadConfigAsync(repository)
                }
            }

            override fun onSuccess() {
                result?.let { GitFlowNotifier.show(project, repository, title, it, retry) }
            }
        }.queue()
    }

    companion object {
        /**
         * Action updates ask for the configuration whenever their state is refreshed - often - and run
         * in the background *under a read lock*, where git must not be started. So they only
         * ever read this cache; a stale or missing entry is refreshed asynchronously, and the actions
         * are updated again once it arrives. Every operation of ours and every repository change marks
         * the entry stale, and the short expiry picks up changes made by other tools (such as
         * `git flow init`).
         */
        private const val CONFIG_TTL_MS = 10_000L

        /** Published (on the project bus) whenever a repository's git flow configuration changes. */
        @JvmField
        val CONFIG_CHANGED: Topic<Runnable> = Topic.create("Git Flow configuration changed", Runnable::class.java)

        fun getInstance(project: Project): GitFlowService = project.getService(GitFlowService::class.java)
    }
}
