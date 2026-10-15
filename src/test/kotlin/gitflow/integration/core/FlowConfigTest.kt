package gitflow.integration.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FlowConfigTest {

    @Test
    fun parsesTheOutputOfGitConfigGetRegexp() {
        val config = FlowConfig.parse(
            """
            gitflow.branch.master main
            gitflow.branch.develop dev
            gitflow.prefix.feature feat/
            gitflow.prefix.versiontag v
            gitflow.path.hooks /somewhere
            """.trimIndent() + "\n",
        )

        assertEquals("main", config.master)
        assertEquals("dev", config.develop)
        assertEquals("feat/", config.prefix(FlowType.FEATURE))
        assertEquals("v", config.versionTagPrefix)
        assertTrue(config.isConfigured)
    }

    @Test
    fun anEmptyValueHasNoTrailingSpaceInGitOutput() {
        val config = FlowConfig.parse("gitflow.branch.master main\ngitflow.branch.develop develop\ngitflow.prefix.versiontag\n")

        assertEquals("", config.versionTagPrefix)
    }

    @Test
    fun missingPrefixesFallBackToTheDefaults() {
        val config = FlowConfig.parse("gitflow.branch.master main\ngitflow.branch.develop develop\n")

        for (type in FlowType.entries) {
            assertEquals(type.defaultPrefix, config.prefix(type))
        }
    }

    @Test
    fun anEmptyOutputIsNotConfigured() {
        assertFalse(FlowConfig.parse("").isConfigured)
        assertFalse(FlowConfig.empty().isConfigured)
    }

    @Test
    fun classifiesBranchesByPrefix() {
        val config = FlowConfig.of("main", "develop", emptyMap(), "")

        assertEquals(FlowConfig.FlowRef(FlowType.FEATURE, "login"), config.classify("feature/login"))
        assertEquals(FlowConfig.FlowRef(FlowType.HOTFIX, "1.0.1"), config.classify("hotfix/1.0.1"))
        assertEquals(FlowConfig.FlowRef(FlowType.FEATURE, "a/b"), config.classify("feature/a/b"))
        assertNull(config.classify("develop"))
        assertNull(config.classify("feature/"))
        assertNull(config.classify("other/thing"))
    }

    @Test
    fun theLongestMatchingPrefixWins() {
        val config = FlowConfig.of(
            "main", "develop",
            mapOf(FlowType.FEATURE to "work/", FlowType.BUGFIX to "work/bugs/"), "",
        )

        assertEquals(FlowType.BUGFIX, config.classify("work/bugs/x")!!.type)
        assertEquals(FlowType.FEATURE, config.classify("work/x")!!.type)
    }

    @Test
    fun configurationsWithTheSameValuesAreEqual() {
        val parsed = FlowConfig.parse("gitflow.branch.master main\ngitflow.branch.develop develop\n")

        assertEquals(FlowConfig.of("main", "develop", emptyMap(), ""), parsed)
        assertEquals(FlowConfig.of("main", "develop", emptyMap(), "").hashCode(), parsed.hashCode())
        assertNotEquals(FlowConfig.of("main", "dev", emptyMap(), ""), parsed)
        assertNotEquals(FlowConfig.of("main", "develop", mapOf(FlowType.FEATURE to "f/"), ""), parsed)
        assertNotEquals(FlowConfig.of("main", "develop", emptyMap(), "v"), parsed)
    }

    @Test
    fun buildsBranchAndTagNames() {
        val config = FlowConfig.of("main", "develop", emptyMap(), "v")

        assertEquals("release/1.2.0", config.branchName(FlowType.RELEASE, "1.2.0"))
        assertEquals("v1.2.0", config.tagName("1.2.0"))
        assertEquals("develop", config.baseBranch(FlowType.FEATURE))
        assertEquals("main", config.baseBranch(FlowType.HOTFIX))
    }
}
