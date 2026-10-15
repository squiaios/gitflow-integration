package gitflow.integration.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class RefNamesTest {

    @Test
    fun acceptsOrdinaryBranchNames() {
        for (name in listOf("feature/login", "release/1.2.0", "hotfix/v1.0.1", "feature/a/b-c_d", "x")) {
            assertNull(RefNames.validateBranchName(name), name)
        }
    }

    @Test
    fun rejectsAnEmptyName() {
        assertEquals("validate.name.empty", RefNames.validateBranchName("  ")!!.key)
        assertEquals("validate.name.empty", RefNames.validateBranchName(null)!!.key)
    }

    @Test
    fun rejectsIllegalCharacters() {
        for (name in listOf(
            "feature/a~1", "feature/a^", "feature/a:b", "feature/a?", "feature/a*",
            "feature/a[0]", "feature/a\\b",
        )) {
            assertEquals("validate.name.illegalChar", RefNames.validateBranchName(name)!!.key, name)
        }
        assertEquals("~", RefNames.validateBranchName("feature/a~1")!!.args[0])
    }

    @Test
    fun rejectsSpacesAndControlCharactersWithTheirOwnMessages() {
        assertEquals("validate.name.space", RefNames.validateBranchName("feature/a b")!!.key)
        assertEquals("validate.name.control", RefNames.validateBranchName("feature/a\tb")!!.key)
    }

    @Test
    fun rejectsMalformedNames() {
        for (name in listOf(
            "-x", "/x", "x/", "x.", "a..b", "a//b", "a@{b", "@", "feature/.hidden",
            "feature/x.lock",
        )) {
            assertEquals("validate.name.malformed", RefNames.validateBranchName(name)!!.key, name)
        }
    }
}
