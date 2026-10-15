package gitflow.integration.ide

import com.intellij.openapi.util.IconLoader
import javax.swing.Icon

/** Icons of the plugin. The dark variants (`*_dark.svg`) are picked up automatically. */
object GitFlowIcons {

    @JvmField
    val GIT_FLOW: Icon = IconLoader.getIcon("/icons/gitFlow.svg", GitFlowIcons::class.java)
}
