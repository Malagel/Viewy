package com.malagel.viewy.tutorial

import android.content.SharedPreferences
import com.malagel.viewy.domain.MeasurementMethod
import com.malagel.viewy.measurement.MeasurementSelection
import java.lang.reflect.Proxy
import org.junit.Assert.*
import org.junit.Test

class LocalTutorialSeenStoreTest {
    @Test fun completionIsNamespacedByStableIdAndSurvivesStoreReconstruction() {
        val file = PreferenceFile()
        val store = LocalTutorialSeenStore(file.preferences)
        assertFalse(store.hasSeen("known_object_height"))
        store.markSeen("another_method")
        assertFalse(store.hasSeen("known_object_height"))
        store.markSeen("known_object_height")
        val restored = LocalTutorialSeenStore(file.preferences)
        assertTrue(restored.hasSeen("known_object_height"))
        assertTrue(restored.hasSeen("another_method"))
        assertFalse(restored.hasSeen("a_future_method"))
        assertEquals(setOf("seen.known_object_height", "seen.another_method"), file.values.keys)
    }

    @Test fun newCoordinatorUsesPersistedCompletionWithoutPersistingSelectionOrPage() {
        val file = PreferenceFile()
        val method = MeasurementMethod.KNOWN_OBJECT_HEIGHT
        val browsing = MeasurementSelection().browse(method.mode)
        fun activate(coordinator: TutorialCoordinator) {
            coordinator.selectionChanged(browsing)
            coordinator.selectionChanged(browsing.activate(method), method)
            coordinator.methodEntered(coordinator.state.navigationToken)
        }
        val first = TutorialCoordinator(LocalTutorialSeenStore(file.preferences), setOf(method))
        activate(first)
        assertTrue(file.values.isEmpty())
        first.dismiss(first.state.card!!.id)
        val nextLaunch = TutorialCoordinator(LocalTutorialSeenStore(file.preferences), setOf(method))
        assertNull(nextLaunch.state.card)
        activate(nextLaunch)
        assertNull(nextLaunch.state.card)
        nextLaunch.openManually()
        assertEquals(TutorialPage.INSTRUCTIONS, nextLaunch.state.card!!.page)
        assertEquals(mapOf("seen.known_object_height" to true), file.values)
    }

    /** Exercises the Android preference adapter API without an Android runtime or new test dependency. */
    private class PreferenceFile {
        val values = mutableMapOf<String, Boolean>()
        val preferences: SharedPreferences = proxy(SharedPreferences::class.java) { name, args ->
            when (name) {
                "getBoolean" -> values[args[0]] ?: args[1]
                "edit" -> editor()
                else -> error("Unexpected preference call: $name")
            }
        }

        private fun editor(): SharedPreferences.Editor {
            val pending = mutableMapOf<String, Boolean>()
            lateinit var editor: SharedPreferences.Editor
            editor = proxy(SharedPreferences.Editor::class.java) { name, args ->
                when (name) {
                    "putBoolean" -> { pending[args[0] as String] = args[1] as Boolean; editor }
                    "apply" -> { values.putAll(pending); null }
                    else -> error("Unexpected editor call: $name")
                }
            }
            return editor
        }

        private fun <T : Any> proxy(type: Class<T>, action: (String, Array<out Any?>) -> Any?): T =
            checkNotNull(type.cast(Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { _, method, args ->
                action(method.name, args ?: emptyArray())
            }))
    }
}
