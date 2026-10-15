package gitflow.integration.core

/** The kinds of supporting branches defined by git flow. */
enum class FlowType(
    /** Lower-case identifier, as used in `git flow <id> ...` and in the config keys. */
    val id: String,
    val defaultPrefix: String,
    val base: Base,
    /** Whether the user may pick the starting point (support branches require one). */
    val customBase: Boolean,
) {
    FEATURE("feature", "feature/", Base.DEVELOP, true),
    BUGFIX("bugfix", "bugfix/", Base.DEVELOP, true),
    RELEASE("release", "release/", Base.DEVELOP, false),
    HOTFIX("hotfix", "hotfix/", Base.MASTER, false),
    SUPPORT("support", "support/", Base.MASTER, true);

    /** The long-lived branch a flow branch starts from by default. */
    enum class Base { DEVELOP, MASTER }

    /** Support branches are long-lived and are never finished. */
    val finishable: Boolean
        get() = this != SUPPORT

    /**
     * Release and hotfix branches are merged into production, tagged and merged back, and
     * only one branch of each of these types may exist at a time.
     */
    val versioned: Boolean
        get() = this == RELEASE || this == HOTFIX

    /** The git config key holding this type's branch prefix. */
    val configKey: String
        get() = "gitflow.prefix.$id"
}
