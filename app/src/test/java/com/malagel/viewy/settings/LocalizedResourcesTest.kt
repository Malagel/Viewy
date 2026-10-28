package com.malagel.viewy.settings

import android.content.pm.ActivityInfo
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

    @Test fun coldDefaultLanguageOverrideDoesNotRequireActivityRecreation() {
        val manifest = File("src/main/AndroidManifest.xml").takeIf { it.exists() }
            ?: File("app/src/main/AndroidManifest.xml")
        val activities = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(manifest)
            .getElementsByTagName("activity")
        val main = (0 until activities.length).map { activities.item(it) }
            .single { it.attributes.getNamedItem("android:name").nodeValue == ".MainActivity" }
        val declared = main.attributes.getNamedItem("android:configChanges").nodeValue.split('|').toSet()
        val supported = mapOf(
            "locale" to ActivityInfo.CONFIG_LOCALE,
            "layoutDirection" to ActivityInfo.CONFIG_LAYOUT_DIRECTION,
            "screenLayout" to ActivityInfo.CONFIG_SCREEN_LAYOUT,
            "grammaticalGender" to ActivityInfo.CONFIG_GRAMMATICAL_GENDER,
        )
        // The Android 16 cold device-default -> Spanish trace reports 0x2104,
        // including screenLayout even though the window bounds do not change.
        val observed = ActivityInfo.CONFIG_LOCALE or ActivityInfo.CONFIG_LAYOUT_DIRECTION or ActivityInfo.CONFIG_SCREEN_LAYOUT
        assertEquals(0x2104, observed)
        val handled = declared.fold(0) { mask, name ->
            mask or supported.getValue(name) // Keep handling limited to these configuration dimensions.
        }
        assertEquals("Cold app-locale override would recreate the Activity", 0, observed and handled.inv())
        assertEquals("Grammatical gender must still update in place", 0,
            ActivityInfo.CONFIG_GRAMMATICAL_GENDER and handled.inv())
        // Actual orientation/size/density changes must retain normal recreation rules.
        val ordinaryRecreation = ActivityInfo.CONFIG_ORIENTATION or ActivityInfo.CONFIG_SCREEN_SIZE or
            ActivityInfo.CONFIG_SMALLEST_SCREEN_SIZE or ActivityInfo.CONFIG_DENSITY
        assertEquals(0, ordinaryRecreation and handled)
    }
}
