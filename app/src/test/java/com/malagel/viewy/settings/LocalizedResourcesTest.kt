package com.malagel.viewy.settings

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.*
import org.junit.Test

class LocalizedResourcesTest {
    private fun resource(path: String): File = File("src/main/res/$path").takeIf { it.exists() }
        ?: File("app/src/main/res/$path")
    private fun strings(path: String): Map<String, String> {
        val nodes = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(resource(path)).getElementsByTagName("string")
        return (0 until nodes.length).associate { index ->
            val node = nodes.item(index)
            node.attributes.getNamedItem("name").nodeValue to node.textContent
        }
    }

    @Test fun spanishCoversAllProseAndPreservesFormatArgumentsAndEquations() {
        val english = strings("values/strings.xml")
        val spanish = strings("values-es/strings.xml")
        val sharedNames = setOf("language_english", "language_spanish")
        assertEquals(english.keys - sharedNames, spanish.keys)
        val placeholders = Regex("%[0-9]+\\\$[a-z]")
        spanish.forEach { (key, text) ->
            assertTrue("Empty Spanish resource: $key", text.isNotBlank())
            assertEquals("Arguments: $key", placeholders.findAll(english.getValue(key)).map { it.value }.toList(),
                placeholders.findAll(text).map { it.value }.toList())
        }
        for (key in listOf("tutorial_known_height_equation", "tutorial_known_height_height_equation")) {
            assertEquals(english.getValue(key), spanish.getValue(key))
        }
        assertEquals("Ajustes", spanish.getValue("settings"))
        assertEquals("Use device language", english.getValue("language_device"))
    }

    @Test fun systemLanguagePickerAdvertisesExactlyEnglishAndSpanish() {
        val nodes = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(resource("xml/locales_config.xml"))
            .getElementsByTagName("locale")
        assertEquals(listOf("en", "es"), (0 until nodes.length).map { nodes.item(it).attributes.getNamedItem("android:name").nodeValue })
    }
}
