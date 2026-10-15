package gitflow.integration.ide

import gitflow.integration.core.FlowType
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale
import java.util.Properties
import java.util.TreeSet
import java.util.regex.Pattern
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The message bundles cannot be exercised without an IDE, so these tests check them for the
 * mistakes that would only show at runtime: a missing translation, an unknown key, a placeholder
 * that differs between languages, or an apostrophe that would swallow the text around it.
 */
class BundleTest {

    /**
     * The raw contents of a bundle file. A ResourceBundle would not do: it also reports the keys it
     * inherits from the English bundle, which would hide a missing translation.
     */
    private fun bundle(locale: Locale): Properties {
        val file = "/messages/GitFlowBundle" +
            (if (locale.language.isEmpty()) "" else "_" + locale.language) + ".properties"
        BundleTest::class.java.getResourceAsStream(file).use { stream ->
            assertNotNull(stream, "$file is not on the classpath")
            val properties = Properties()
            properties.load(InputStreamReader(stream!!, StandardCharsets.UTF_8))
            return properties
        }
    }

    private fun keys(bundle: Properties): Set<String> = TreeSet(bundle.stringPropertyNames())

    private fun placeholders(text: String): Set<String> {
        val found = TreeSet<String>()
        val matcher = PLACEHOLDER.matcher(text)
        while (matcher.find()) {
            found.add(matcher.group(1))
        }
        return found
    }

    @Test
    fun everyKeyIsTranslated() {
        val english = keys(bundle(Locale.ROOT))
        val french = keys(bundle(Locale.FRENCH))

        val untranslated = TreeSet(english)
        untranslated.removeAll(french)
        val orphaned = TreeSet(french)
        orphaned.removeAll(english)

        assertEquals(emptySet<String>(), untranslated, "keys missing from GitFlowBundle_fr.properties")
        assertEquals(emptySet<String>(), orphaned, "keys of GitFlowBundle_fr.properties that English does not have")
    }

    @Test
    fun translationsUseTheSamePlaceholders() {
        val english = bundle(Locale.ROOT)
        val french = bundle(Locale.FRENCH)
        val mismatches = mutableListOf<String>()
        for (key in keys(english)) {
            if (placeholders(english.getProperty(key)) != placeholders(french.getProperty(key))) {
                mismatches.add(key)
            }
        }
        assertEquals(emptyList<String>(), mismatches)
    }

    /**
     * IntelliJ runs a message through MessageFormat only when it is given arguments, where a lone
     * apostrophe starts a quoted section. So an apostrophe is doubled exactly in the messages that
     * have placeholders, and nowhere else.
     */
    @Test
    fun apostrophesFollowTheMessageFormatRule() {
        val wrong = mutableListOf<String>()
        for (locale in listOf(Locale.ROOT, Locale.FRENCH)) {
            val bundle = bundle(locale)
            for (key in keys(bundle)) {
                val text = bundle.getProperty(key)
                val parameterised = placeholders(text).isNotEmpty()
                val loneQuote = text.replace("''", "").contains("'")
                val doubledQuote = text.contains("''")
                if ((parameterised && loneQuote) || (!parameterised && doubledQuote)) {
                    wrong.add("$locale:$key")
                }
            }
        }
        assertEquals(emptyList<String>(), wrong)
    }

    @Test
    fun everyMessageTheEngineCanProduceHasText() {
        val used = TreeSet<String>()
        val key = Pattern.compile("\"((?:error|step|warn|validate)\\.[\\w.]+)\"")
        Files.list(Path.of("src/main/kotlin/gitflow/integration/core")).use { files ->
            for (file in files.filter { it.toString().endsWith(".kt") }.toList()) {
                val matcher = key.matcher(Files.readString(file))
                while (matcher.find()) {
                    used.add(matcher.group(1))
                }
            }
        }
        assertTrue(used.size > 30, "the scan found ${used.size} keys; has the source moved?")

        val defined = keys(bundle(Locale.ROOT))
        used.removeAll(defined)
        assertEquals(emptySet<String>(), used, "messages used by the engine but missing from the bundle")
    }

    @Test
    fun everyFlowTypeHasItsNames() {
        val english = bundle(Locale.ROOT)
        for (type in FlowType.entries) {
            for (suffix in listOf("", ".title", ".plural")) {
                assertTrue(english.containsKey("type." + type.id + suffix), "type." + type.id + suffix)
            }
        }
    }

    private companion object {
        val PLACEHOLDER: Pattern = Pattern.compile("\\{(\\d+)}")
    }
}
