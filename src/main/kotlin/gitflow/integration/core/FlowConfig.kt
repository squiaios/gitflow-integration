package gitflow.integration.core

import java.util.Collections
import java.util.EnumMap

/**
 * The git flow configuration of a repository.
 *
 * It is stored in the repository's git config under the `gitflow.*` keys.
 */
class FlowConfig private constructor(
    /** The production branch (`gitflow.branch.master`). */
    val master: String,
    /** The integration branch (`gitflow.branch.develop`). */
    val develop: String,
    val prefixes: Map<FlowType, String>,
    /** Prepended to the name of a release or hotfix to build its tag. */
    val versionTagPrefix: String,
) {

    fun prefix(type: FlowType): String = prefixes.getValue(type)

    /** True when both long-lived branches are named; says nothing about whether they exist. */
    val isConfigured: Boolean
        get() = master.isNotBlank() && develop.isNotBlank()

    fun branchName(type: FlowType, name: String): String = prefix(type) + name

    fun tagName(name: String): String = versionTagPrefix + name

    /** The long-lived branch a flow type starts from. */
    fun baseBranch(type: FlowType): String = if (type.base == FlowType.Base.DEVELOP) develop else master

    /** Identifies which kind of flow branch [branch] is, if any, and its short name. */
    fun classify(branch: String): FlowRef? {
        var best: FlowType? = null
        for (type in FlowType.entries) {
            val prefix = prefix(type)
            if (prefix.isEmpty() || branch.length <= prefix.length || !branch.startsWith(prefix)) {
                continue
            }
            if (best == null || prefix.length > prefix(best).length) {
                best = type
            }
        }
        return best?.let { FlowRef(it, branch.substring(prefix(it).length)) }
    }

    override fun equals(other: Any?): Boolean =
        other is FlowConfig
            && master == other.master
            && develop == other.develop
            && prefixes == other.prefixes
            && versionTagPrefix == other.versionTagPrefix

    override fun hashCode(): Int = arrayOf(master, develop, prefixes, versionTagPrefix).contentHashCode()

    override fun toString(): String =
        "FlowConfig[master=$master, develop=$develop, prefixes=$prefixes, versionTagPrefix=$versionTagPrefix]"

    /** A flow branch identified by type and short name. */
    data class FlowRef(val type: FlowType, val name: String)

    companion object {
        const val MASTER_KEY = "gitflow.branch.master"
        const val DEVELOP_KEY = "gitflow.branch.develop"
        const val VERSION_TAG_KEY = "gitflow.prefix.versiontag"

        /** A configuration with no branches set: git flow is not initialised. */
        fun empty(): FlowConfig = of("", "", emptyMap(), "")

        /** Builds a configuration, filling every missing prefix with its default. */
        fun of(master: String, develop: String, prefixes: Map<FlowType, String>, versionTagPrefix: String): FlowConfig {
            val all = EnumMap<FlowType, String>(FlowType::class.java)
            for (type in FlowType.entries) {
                all[type] = prefixes[type] ?: type.defaultPrefix
            }
            return FlowConfig(master, develop, Collections.unmodifiableMap(all), versionTagPrefix)
        }

        /**
         * Parses the output of `git config --get-regexp ^gitflow\.`: one `key value` per
         * line, the value being absent when it is empty.
         */
        fun parse(output: String): FlowConfig {
            var master = ""
            var develop = ""
            var tagPrefix = ""
            val prefixes = EnumMap<FlowType, String>(FlowType::class.java)
            for (line in output.lines()) {
                if (line.isBlank()) {
                    continue
                }
                val space = line.indexOf(' ')
                val key = if (space < 0) line else line.substring(0, space)
                val value = if (space < 0) "" else line.substring(space + 1).trim()
                when (key) {
                    MASTER_KEY -> master = value
                    DEVELOP_KEY -> develop = value
                    VERSION_TAG_KEY -> tagPrefix = value
                    else -> {
                        for (type in FlowType.entries) {
                            if (key == type.configKey) {
                                prefixes[type] = value
                            }
                        }
                    }
                }
            }
            return of(master, develop, prefixes, tagPrefix)
        }
    }
}
