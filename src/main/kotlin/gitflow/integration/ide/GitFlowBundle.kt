package gitflow.integration.ide

import gitflow.integration.core.FlowType
import gitflow.integration.core.Msg
import com.intellij.DynamicBundle
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.PropertyKey

private const val BUNDLE = "messages.GitFlowBundle"

/** Access to the plugin's localized messages (`messages/GitFlowBundle*.properties`). */
object GitFlowBundle : DynamicBundle(GitFlowBundle::class.java, BUNDLE) {

    @Nls
    fun message(@PropertyKey(resourceBundle = BUNDLE) key: String, vararg params: Any): String =
        getMessage(key, *params)

    /** Renders a message produced by the git flow engine. */
    @Nls
    fun message(msg: Msg): String = getMessage(msg.key, *msg.args)

    /** Lower-case name of a flow type, e.g. "feature", for use inside a sentence. */
    @Nls
    fun typeName(type: FlowType): String = getMessage("type.${type.id}")

    /** Plural, capitalized name of a flow type, e.g. "Features", for the tool window's tree. */
    @Nls
    fun typePlural(type: FlowType): String = getMessage("type.${type.id}.plural")

    /** Capitalized name of a flow type, e.g. "Feature", for titles and menus. */
    @Nls
    fun typeTitle(type: FlowType): String = getMessage("type.${type.id}.title")
}
