package ir.laxipatem.lexiflash.res

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/**
 * Guards the two resource folders against drifting apart.
 *
 * A key that exists in English but not in Persian leaves an untranslated label
 * in the UI; the reverse means dead weight. The test parses the XML directly so
 * it needs no Android runtime.
 */
class StringsParityTest {

    private val valuesDir = File("src/main/res/values")
    private val persianDir = File("src/main/res/values-fa")

    private data class Resources(
        val strings: Map<String, String>,
        val plurals: Map<String, Map<String, String>>
    )

    private fun readResources(file: File): Resources {
        assertTrue("missing resource file: ${file.absolutePath}", file.isFile)
        val document = DocumentBuilderFactory.newInstance()
            .apply { isNamespaceAware = false }
            .newDocumentBuilder()
            .parse(file)
        val root = document.documentElement

        val strings = LinkedHashMap<String, String>()
        val plurals = LinkedHashMap<String, MutableMap<String, String>>()

        var node = root.firstChild
        while (node != null) {
            if (node is Element) {
                val name = node.getAttribute("name")
                when (node.tagName) {
                    "string" -> strings[name] = node.textContent.trim()
                    "plurals" -> {
                        val items = LinkedHashMap<String, String>()
                        var child = node.firstChild
                        while (child != null) {
                            if (child is Element && child.tagName == "item") {
                                items[child.getAttribute("quantity")] = child.textContent.trim()
                            }
                            child = child.nextSibling
                        }
                        plurals[name] = items
                    }
                }
            }
            node = node.nextSibling
        }
        return Resources(strings, plurals)
    }

    private val english by lazy { readResources(File(valuesDir, "strings.xml")) }
    private val persian by lazy { readResources(File(persianDir, "strings.xml")) }

    @Test
    fun `every english string has a persian counterpart`() {
        val missing = english.strings.keys - persian.strings.keys
        assertTrue("Missing Persian translations for: $missing", missing.isEmpty())
    }

    @Test
    fun `every persian string has an english counterpart`() {
        val extra = persian.strings.keys - english.strings.keys
        assertTrue("Persian keys with no English source: $extra", extra.isEmpty())
    }

    @Test
    fun `plurals exist in both languages`() {
        assertEquals(english.plurals.keys, persian.plurals.keys)
    }

    @Test
    fun `plurals define the same quantities`() {
        for ((name, quantities) in english.plurals) {
            val counterpart = persian.plurals[name]
            if (counterpart == null) {
                // This test fails and names the offender rather than hiding it.
                throw AssertionError("Plural \"$name\" is missing from values-fa")
            }
            assertEquals(
                "Plural \"$name\" has different quantity sets",
                quantities.keys,
                counterpart.keys
            )
        }
    }

    @Test
    fun `no string is blank`() {
        for ((name, value) in english.strings) {
            assertTrue("English string \"$name\" is blank", value.isNotEmpty())
        }
        for ((name, value) in persian.strings) {
            assertTrue("Persian string \"$name\" is blank", value.isNotEmpty())
        }
    }

    @Test
    fun `string names use snake case`() {
        val pattern = Regex("^[a-z][a-z0-9_]*$")
        for (name in english.strings.keys + english.plurals.keys) {
            assertTrue("String name \"$name\" is not snake_case", pattern.matches(name))
        }
    }

    @Test
    fun `persian strings actually contain persian text`() {
        // Some keys are legitimately language-neutral: numbers, format-only
        // placeholders and proper nouns. Everything else must be translated.
        val neutral = setOf(
            "settings_version", "deck_license_apache", "about_license_name",
            "catalog_url_hint", "about_license_id", "app_id"
        )
        // A pure format string such as "%1$d / %2$d" carries no prose to translate.
        fun isFormatOnly(value: String): Boolean =
            value.all { it.isWhitespace() || it in "%$0123456789.,:/-" } && value.isNotBlank()

        val offenders = persian.strings
            .filterKeys { it !in neutral && !it.startsWith("cd_") }
            .filterValues { value ->
                value.none { it in '\u0600'..'\u06FF' } && !isFormatOnly(value)
            }
            .keys
        assertTrue("Persian strings with no Persian characters: $offenders", offenders.isEmpty())
    }

    @Test
    fun `the app name uses a real zero width non joiner`() {
        val name = persian.strings["app_name"].orEmpty()
        assertTrue(
            "app_name must contain U+200C (ZWNJ), not a literal escape: $name",
            name.contains('\u200C')
        )
        assertTrue("app_name must not contain a literal backslash", !name.contains('\\'))
        // فلش + ZWNJ + لکس = seven code points.
        assertEquals(7, name.length)
    }

    @Test
    fun `format placeholders match between languages`() {
        val placeholder = Regex("%\\d+\\$[sd]|%[sd]")
        for ((name, englishValue) in english.strings) {
            val persianValue = persian.strings[name] ?: continue
            val englishPlaceholders = placeholder.findAll(englishValue).map { it.value }.toSet()
            val persianPlaceholders = placeholder.findAll(persianValue).map { it.value }.toSet()
            assertEquals(
                "Placeholders differ for \"$name\"",
                englishPlaceholders,
                persianPlaceholders
            )
        }
    }

    @Test
    fun `the app name is correct in both languages`() {
        assertEquals("LexiFlash Persian", english.strings["app_name"])
        assertEquals("فلش\u200cلکس", persian.strings["app_name"])
    }

    @Test
    fun `the trademark disclaimer is present verbatim`() {
        val disclaimer = english.strings["about_disclaimer"].orEmpty()
        assertTrue(
            "The disclaimer must name TED, Interstellar and rights holders: $disclaimer",
            disclaimer.contains("Not affiliated with TED, Interstellar or any rights holder.")
        )
    }

    @Test
    fun `no resource mentions the old project name`() {
        val banned = "Learn English with Interstellar"
        for ((name, value) in english.strings + persian.strings) {
            assertTrue("Resource \"$name\" mentions the old name", !value.contains(banned))
        }
    }
}
