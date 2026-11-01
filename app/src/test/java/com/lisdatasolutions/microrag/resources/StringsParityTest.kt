package com.lisdatasolutions.microrag.resources

import com.google.common.truth.Truth.assertWithMessage
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Test
import org.w3c.dom.Element

/**
 * F12 T059 (research R13, FR-029): every text of the app exists in Spanish and in English with the same
 * format arguments, and the untranslatable ones (language names, «SHA-256», «—») live only in Spanish.
 */
class StringsParityTest {

    private data class Text(val translatable: Boolean, val formatted: Boolean, val values: List<String>)

    private fun read(folder: String): Map<String, Text> {
        val files = File("src/main/res/$folder").listFiles { file -> file.name.startsWith("strings") && file.name.endsWith(".xml") }.orEmpty()
        assertWithMessage("strings files in $folder").that(files).isNotEmpty()
        val texts = mutableMapOf<String, Text>()
        for (file in files) {
            val root = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).documentElement
            val children = root.childNodes
            for (i in 0 until children.length) {
                val element = children.item(i) as? Element ?: continue
                val key = "${element.tagName}/${element.getAttribute("name")}"
                val values = when (element.tagName) {
                    "string" -> listOf(element.textContent)
                    "plurals" -> (0 until element.getElementsByTagName("item").length).map { element.getElementsByTagName("item").item(it).textContent }
                    else -> continue
                }
                assertWithMessage("$key defined twice in $folder").that(texts).doesNotContainKey(key)
                texts[key] = Text(
                    translatable = element.getAttribute("translatable") != "false",
                    formatted = element.getAttribute("formatted") != "false",
                    values = values,
                )
            }
        }
        return texts
    }

    private val spanish = read("values")
    private val english = read("values-en")

    @Test
    fun everyTranslatableTextHasItsEnglishOne_andNothingElse() {
        val translatable = spanish.filterValues { it.translatable }.keys
        assertWithMessage("texts without English").that(translatable - english.keys).isEmpty()
        assertWithMessage("English texts without Spanish").that(english.keys - spanish.keys).isEmpty()
    }

    @Test
    fun untranslatableTextsLiveOnlyInSpanish() {
        val untranslatable = spanish.filterValues { !it.translatable }.keys
        assertWithMessage("untranslatable texts in values-en").that(untranslatable intersect english.keys).isEmpty()
        assertWithMessage("translatable=\"false\" in values-en").that(english.filterValues { !it.translatable }.keys).isEmpty()
    }

    @Test
    fun eachPairHasTheSameFormatArguments() {
        for ((key, text) in english) {
            val original = spanish[key] ?: continue
            assertWithMessage("formatted attribute of $key").that(text.formatted).isEqualTo(original.formatted)
            if (!text.formatted) continue
            assertWithMessage("format arguments of $key").that(arguments(text.values)).isEqualTo(arguments(original.values))
        }
    }

    /** `%1$s`, `%d`…, as a sorted set over every value of the text (plural items included). */
    private fun arguments(values: List<String>): Set<String> =
        values.flatMap { FORMAT.findAll(it).map { match -> match.value } }.toSortedSet()

    private companion object {
        val FORMAT = Regex("%(\\d+\\$)?[-#+ 0,(]*\\d*(\\.\\d+)?[sdfxXeEgGc%]")
    }
}
