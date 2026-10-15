package gitflow.integration.core

import java.util.EnumMap

/** Settings for initialising git flow in a repository. */
class InitOptions {

    /** Production branch; must already exist unless the repository has no commit yet. */
    var master: String = "main"

    /** Integration branch; created from the production branch when missing. */
    var develop: String = "develop"

    val prefixes: MutableMap<FlowType, String> = EnumMap(FlowType::class.java)

    /** Prepended to release and hotfix names to build tag names. */
    var versionTagPrefix: String = ""

    /** Overwrite an existing git flow configuration. */
    var force: Boolean = false

    init {
        for (type in FlowType.entries) {
            prefixes[type] = type.defaultPrefix
        }
    }

    companion object {
        /** Options carrying the values of an existing configuration. */
        fun from(config: FlowConfig): InitOptions {
            val options = InitOptions()
            if (config.master.isNotBlank()) {
                options.master = config.master
            }
            if (config.develop.isNotBlank()) {
                options.develop = config.develop
            }
            options.prefixes.putAll(config.prefixes)
            options.versionTagPrefix = config.versionTagPrefix
            return options
        }
    }
}
