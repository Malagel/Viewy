package com.malagel.viewy.ui

import org.junit.Assert.*
import org.junit.Test

class MainLayoutPolicyTest {
    @Test fun representativeAvailableWindowsUseExistingPortraitOrAnIntentionalFallback() {
        data class Case(val width: Float, val height: Float, val font: Float,
            val standardChrome: Float, val compactChrome: Float, val expected: MainLayout)
        // Chrome is measured by Compose at runtime. These representative budgets include
        // header, wrapped instructions, padding and full-size capture/navigation targets.
        val cases = listOf(
            Case(393f, 852f, 1f, 280f, 220f, MainLayout.STANDARD),
            Case(360f, 800f, 1f, 280f, 220f, MainLayout.STANDARD),
            Case(320f, 568f, 1.5f, 370f, 250f, MainLayout.COMPACT),
            Case(280f, 360f, 2f, 460f, 330f, MainLayout.CONSTRAINED),
            Case(412f, 915f, 2f, 430f, 280f, MainLayout.COMPACT),
            Case(800f, 1280f, 1f, 280f, 220f, MainLayout.STANDARD),
            Case(1280f, 800f, 1f, 280f, 220f, MainLayout.STANDARD),
            Case(600f, 500f, 1f, 280f, 220f, MainLayout.COMPACT),
        )
        cases.forEach { case ->
            assertEquals(case.toString(), case.expected, mainLayoutPolicy(case.width, case.height, case.font,
                case.standardChrome, case.compactChrome))
        }
    }

    @Test fun measuredChromeRatherThanDeviceCategoryProtectsTheViewfinder() {
        assertEquals(MainLayout.STANDARD, mainLayoutPolicy(393f, 700f, 1f, 460f, 300f))
        assertEquals(MainLayout.COMPACT, mainLayoutPolicy(393f, 700f, 1f, 461f, 300f))
        assertEquals(MainLayout.COMPACT, mainLayoutPolicy(393f, 700f, 1f, 500f, 460f))
        assertEquals(MainLayout.CONSTRAINED, mainLayoutPolicy(393f, 700f, 1f, 500f, 461f))
        assertEquals(MainLayout.CONSTRAINED, mainLayoutPolicy(279f, 1000f, 1f, 200f, 180f))
    }

    @Test fun largeTextIsASignalRatherThanALinearGeometryMultiplier() {
        assertEquals(MainLayout.COMPACT, mainLayoutPolicy(412f, 915f, 2f, 350f, 250f))
        assertEquals(MainLayout.CONSTRAINED, mainLayoutPolicy(412f, 915f, 2f, 800f, 700f))
        assertEquals(MainLayout.CONSTRAINED, mainLayoutPolicy(280f, 360f, 1f, 200f, 180f))
    }

    @Test fun chromeScrollsWithThePageBeforeItCanConsumeTheBody() {
        assertTrue(pinPageChrome(568, 100, 80, 96))
        assertTrue(pinPageChrome(276, 100, 80, 96))
        assertFalse(pinPageChrome(275, 100, 80, 96))
        assertFalse(pinPageChrome(200, 240, 100, 96))
    }

    @Test fun headerReflowsWithoutShrinkingItsWordmarkOrActionTargets() {
        assertFalse(headerNeedsActionRow(353, 120, 108))
        assertFalse(headerNeedsActionRow(256, 148, 108))
        assertTrue(headerNeedsActionRow(256, 149, 108))
        assertTrue(headerNeedsActionRow(256, 120, 156))
    }

    @Test fun resizingAnOpenWorkspaceDoesNotCollapseIt() {
        assertTrue(keepWorkspaceOpen(false, false, true, true))
        assertTrue(keepWorkspaceOpen(true, true, false, true))
        assertTrue(keepWorkspaceOpen(true, false, true, false)) // Also retain an unsaved editor.
        assertFalse(keepWorkspaceOpen(false, true, true, true)) // Explicit close stays closed.
        assertFalse(keepWorkspaceOpen(false, false, true, false)) // Fresh entry remains intentional.
    }

    @Test fun fittingStopsAtReadableTextAndKeepsNormalSizesWhenTheyFit() {
        assertEquals(45, readableTextSize(45, minimumHeightNumberSp) { true })
        assertEquals(32, readableTextSize(45, minimumHeightNumberSp) { it <= 32 })
        assertEquals(24, readableTextSize(45, minimumHeightNumberSp) { false })
        assertEquals(96, readableTextSize(96, 28, 2) { true })
        assertEquals(28, readableTextSize(96, 28, 2) { false })
    }
}
