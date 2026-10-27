package gitflow.integration.ide

import gitflow.integration.core.FlowType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GitFlowSettingsTest {

    @Test
    fun theDefaultsAreConservative() {
        val state = GitFlowSettings().state

        assertFalse(state.fetchBeforeStart)
        assertFalse(state.fetchBeforeFinish)
        assertFalse(state.pushAfterFinish)
        assertFalse(state.keepBranch)
        assertTrue(state.noFastForward)
    }

    @Test
    fun aReleaseOrHotfixPushesByDefaultWhateverWasRemembered() {
        val settings = GitFlowSettings()

        assertTrue(settings.pushByDefault(FlowType.RELEASE))
        assertTrue(settings.pushByDefault(FlowType.HOTFIX))
        assertFalse(settings.pushByDefault(FlowType.FEATURE))
        assertFalse(settings.pushByDefault(FlowType.BUGFIX))

        settings.state.pushAfterFinish = true
        assertTrue(settings.pushByDefault(FlowType.FEATURE))
        assertTrue(settings.pushByDefault(FlowType.BUGFIX))
    }

    @Test
    fun tagMessagesExpandTheNamePlaceholder() {
        val settings = GitFlowSettings()

        assertEquals("Release 1.2.0", settings.tagMessage(FlowType.RELEASE, "1.2.0"))
        assertEquals("Hotfix 1.2.1", settings.tagMessage(FlowType.HOTFIX, "1.2.1"))

        settings.state.releaseTagMessage = "Version {name} ({name})"
        assertEquals("Version 2.0.0 (2.0.0)", settings.tagMessage(FlowType.RELEASE, "2.0.0"))
        assertEquals("Hotfix 2.0.1", settings.tagMessage(FlowType.HOTFIX, "2.0.1"))
    }
}
