package com.malagel.viewy.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.malagel.viewy.R
import com.malagel.viewy.domain.MeasurementMethod
import com.malagel.viewy.measurement.HeightDraft
import com.malagel.viewy.measurement.HeightUnit
import com.malagel.viewy.onboarding.OnboardingPage
import com.malagel.viewy.onboarding.OnboardingState
import com.malagel.viewy.settings.AppPreferences
import com.malagel.viewy.settings.SettingsUiState
import com.malagel.viewy.tutorial.TutorialPage
import com.malagel.viewy.ui.theme.ViewyTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/** Component tests deliberately never mount a ViewModel, camera or sensor adapter. */
@RunWith(Parameterized::class)
class ResponsiveLayoutTest(private val width: Int, private val height: Int, private val font: Float, private val language: String) {
    @get:Rule val compose = createComposeRule()

    private fun content(body: @Composable () -> Unit) {
        compose.setContent {
            DeviceConfigurationOverride(
                DeviceConfigurationOverride.ForcedSize(DpSize(width.dp, height.dp)) then
                    DeviceConfigurationOverride.FontScale(font) then
                    DeviceConfigurationOverride.Locales(LocaleList(language)),
            ) { ViewyTheme { body() } }
        }
    }

    @Test fun mainPolicyUsesMeasuredTextAndKeepsNormalPortraitsStandard() {
        var layout: MainLayout? = null
        content {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val policy = rememberMainLayout(maxWidth, maxHeight)
                SideEffect { layout = policy }
            }
        }
        compose.runOnIdle {
            if (font == 1f && height >= 800) assertEquals(MainLayout.STANDARD, layout)
            if (width == 280 && height == 360) assertEquals(MainLayout.CONSTRAINED, layout)
            assertNotNull(layout)
        }
    }

    @Test fun keypadKeepsUsableTargetsAndDraftAcrossResizing() {
        var draft by mutableStateOf(HeightDraft.open(null, HeightUnit.METERS))
        var resized by mutableStateOf(false)
        var enter = ""
        var cancel = ""
        var centimeters = ""
        var pixelsPerDp = 1f
        var confirmed: Double? = null
        var cancelled = false
        content {
            DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(
                if (resized) DpSize(280.dp, 360.dp) else DpSize(width.dp, height.dp))) {
                enter = stringResource(R.string.height_enter)
                cancel = stringResource(R.string.cancel)
                centimeters = stringResource(R.string.height_unit_centimeters)
                pixelsPerDp = LocalDensity.current.density
                EmbeddedHeightEditor(draft, true, false, { draft = draft.key(it) },
                    { draft = draft.backspace() }, { draft = draft.switchUnit() },
                    { cancelled = true }, { confirmed = draft.meters })
            }
        }
        compose.onNodeWithText("1").performScrollTo().assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1.0, draft.meters!!, 0.0) }
        compose.onNodeWithContentDescription(centimeters).performScrollTo().performClick()
        compose.runOnIdle { resized = true }
        compose.onNodeWithText("2").performScrollTo().assertIsDisplayed().also { key ->
            assertTrue(key.fetchSemanticsNode().boundsInRoot.height / pixelsPerDp >= 48f)
            assertTrue(key.fetchSemanticsNode().boundsInRoot.width / pixelsPerDp >= 48f)
        }
        compose.onNodeWithText(enter).performScrollTo().assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(1.0, confirmed!!, 0.0) }
        compose.onNodeWithContentDescription(cancel).performScrollTo().performClick()
        compose.runOnIdle { assertTrue(cancelled) }
    }

    @Test fun veryNarrowKeypadScrollsRatherThanCompressingTargets() {
        var density = 1f
        var entered = false
        var enter = ""
        content {
            DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(DpSize(160.dp, 360.dp))) {
                density = LocalDensity.current.density
                enter = stringResource(R.string.height_enter)
                EmbeddedHeightEditor(HeightDraft.open(1.0, HeightUnit.METERS), true, false,
                    {}, {}, {}, {}, { entered = true })
            }
        }
        compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.HorizontalScrollAxisRange))[0]
            .performSemanticsAction(SemanticsActions.ScrollBy) { it(10_000f, 0f) }
        val key = compose.onNodeWithText("3").performScrollTo().assertIsDisplayed()
        assertTrue(key.fetchSemanticsNode().boundsInRoot.width / density >= 48f)
        compose.onNodeWithText(enter).performScrollTo().assertIsEnabled().performClick()
        compose.runOnIdle { assertTrue(entered) }
    }

    @Test fun permissionAndFooterActionsRemainReachable() {
        var allow = ""
        var next = ""
        var requested = false
        var continued = false
        content {
            allow = stringResource(R.string.camera_permission_allow)
            next = stringResource(R.string.onboarding_not_now)
            OnboardingPageContent(OnboardingPage.PERMISSIONS, OnboardingState(page = OnboardingPage.PERMISSIONS),
                SettingsUiState(preferences = AppPreferences()), {}, { continued = true }, { requested = true })
        }
        compose.onNodeWithText(allow).performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithText(next).performScrollTo().assertIsDisplayed().performClick()
        compose.runOnIdle { assertTrue(requested); assertTrue(continued) }
    }

    @Test fun tutorialDocumentationBackAndCloseRemainReachable() {
        var documentation = ""
        var back = ""
        var close = ""
        val actions = mutableSetOf<String>()
        content {
            documentation = stringResource(R.string.tutorial_how_it_works)
            back = stringResource(R.string.tutorial_go_back)
            close = stringResource(R.string.close)
            TutorialPageContent(methodTutorials.getValue(MeasurementMethod.KNOWN_OBJECT_HEIGHT), TutorialPage.IMPORTANT,
                covered = false, animateIllustration = false, onNext = {}, onBack = { actions += "back" },
                onDismiss = { actions += "close" }, onDocumentation = { actions += "documentation" })
        }
        compose.onNodeWithText(documentation).performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithContentDescription(back).performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithContentDescription(close).performScrollTo().assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(setOf("documentation", "back", "close"), actions) }
    }

    @Test fun longResultsKeepReadableTextAndScrollableUnitsInSmallContainers() {
        var unit by mutableStateOf("km")
        val number = if (language == "es") "123.456.789.012.345,67" else "123,456,789,012,345.67"
        content { Box(Modifier.width(200.dp).height(160.dp)) { ResultDistance(number, number, unit) } }
        for (label in listOf("m", "ft", "km", "mi")) {
            compose.runOnIdle { unit = label }
            val layouts = mutableListOf<TextLayoutResult>()
            compose.onNodeWithText(number).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertTrue(layouts.single().layoutInput.style.fontSize.value >= 28f)
            compose.onNodeWithText(label).performScrollTo().assertIsDisplayed()
        }
    }

    @Test fun backButtonOwnsTheFullTargetAndHonorsDisabledState() {
        var enabled by mutableStateOf(true)
        var clicks = 0
        var density = 1f
        content {
            density = LocalDensity.current.density
            NavigationBackButton({ clicks++ }, label = "Back target", enabled = enabled)
        }
        val back = compose.onNodeWithContentDescription("Back target")
        assertEquals(48f, back.fetchSemanticsNode().boundsInRoot.width / density, 0.5f)
        assertEquals(48f, back.fetchSemanticsNode().boundsInRoot.height / density, 0.5f)
        back.performTouchInput { click(topLeft + androidx.compose.ui.geometry.Offset(2f, 2f)) }
        compose.runOnIdle { assertEquals(1, clicks); enabled = false }
        back.assertIsNotEnabled()
    }

    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{0}x{1}, font={2}, {3}")
        fun configurations(): List<Array<Any>> = listOf(
            Triple(393, 852, 1f), Triple(360, 800, 1f), Triple(320, 568, 1.5f), Triple(280, 360, 2f),
            Triple(412, 915, 2f), Triple(800, 1280, 1f), Triple(1280, 800, 1f), Triple(600, 500, 1f),
        ).flatMap { (width, height, font) -> listOf("en", "es").map { arrayOf<Any>(width, height, font, it) } }
    }
}
