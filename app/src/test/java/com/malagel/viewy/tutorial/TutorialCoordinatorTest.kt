package com.malagel.viewy.tutorial

import com.malagel.viewy.domain.MeasurementMethod
import com.malagel.viewy.domain.MeasurementMode
import com.malagel.viewy.measurement.MeasurementSelection
import org.junit.Assert.*
import org.junit.Test

class TutorialCoordinatorTest {
    private val method = MeasurementMethod.KNOWN_OBJECT_HEIGHT
    private val seen = MemorySeenStore()
    private val coordinator = TutorialCoordinator(seen, setOf(method))
    private var selection = MeasurementSelection()

    private fun navigate(next: MeasurementSelection, activated: MeasurementMethod? = null) {
        coordinator.selectionChanged(next, activated)
        selection = next
    }

    private fun activate() {
        navigate(selection.openModes())
        navigate(selection.browse(method.mode))
        navigate(selection.activate(method), activated = method)
    }

    private fun hidden() = coordinator.methodEntered(coordinator.state.navigationToken)

    @Test fun inactiveStartupAndBrowsingNeverPresentTutorialsOrWriteCompletion() {
        coordinator.openManually()
        hidden()
        navigate(selection.browse(method.mode))
        navigate(selection.back())
        navigate(selection.browse(MeasurementMode.CALIBRATION))
        navigate(selection.openModes())
        hidden()
        assertNull(coordinator.state.card)
        assertNull(coordinator.state.pendingMethod)
        assertTrue(seen.ids.isEmpty())
    }

    @Test fun firstActivationWaitsForActualMethodEntranceAndDismissalRecordsStableId() {
        activate()
        assertEquals(method, coordinator.state.pendingMethod)
        assertNull(coordinator.state.card)
        assertFalse(seen.hasSeen(method.id))
        hidden()
        val card = coordinator.state.card!!
        assertEquals(method, card.method)
        assertEquals(TutorialPage.INSTRUCTIONS, card.page)
        assertNull(coordinator.state.pendingMethod)
        assertFalse(seen.hasSeen(method.id))
        coordinator.dismiss(card.id)
        assertNull(coordinator.state.card)
        assertEquals(setOf("known_object_height"), seen.ids)
        activate()
        hidden()
        assertNull(coordinator.state.card)
    }

    @Test fun manualReopeningStartsInstructionsAndNeverResetsCompletion() {
        activate()
        hidden()
        val first = coordinator.state.card!!
        coordinator.changePage(first.id, TutorialPage.HOW_IT_WORKS)
        assertEquals(first.copy(page = TutorialPage.HOW_IT_WORKS), coordinator.state.card)
        assertTrue(seen.ids.isEmpty())
        coordinator.changePage(first.id, TutorialPage.INSTRUCTIONS)
        assertEquals(first, coordinator.state.card)
        coordinator.changePage(first.id, TutorialPage.HOW_IT_WORKS)
        coordinator.dismiss(first.id) // Close/outside/Back use the same whole-card action.
        coordinator.openManually()
        val reopened = coordinator.state.card!!
        assertNotEquals(first.id, reopened.id)
        assertEquals(TutorialPage.INSTRUCTIONS, reopened.page)
        assertTrue(seen.hasSeen(method.id))
        coordinator.dismiss(reopened.id)
        assertNull(coordinator.state.card)
    }

    @Test fun threeSectionsNavigateInOrderWithoutCompletingUntilDismissal() {
        activate()
        hidden()
        val card = coordinator.state.card!!
        assertNull(TutorialPage.INSTRUCTIONS.previous)
        assertEquals(TutorialPage.IMPORTANT, TutorialPage.INSTRUCTIONS.next)
        assertEquals(TutorialPage.HOW_IT_WORKS, TutorialPage.IMPORTANT.next)
        assertNull(TutorialPage.HOW_IT_WORKS.next)
        for (page in TutorialPage.entries) {
            coordinator.changePage(card.id, page)
            assertEquals(card.copy(page = page), coordinator.state.card)
            assertTrue(seen.ids.isEmpty())
        }
        assertEquals(TutorialPage.IMPORTANT, TutorialPage.HOW_IT_WORKS.previous)
        assertEquals(TutorialPage.INSTRUCTIONS, TutorialPage.IMPORTANT.previous)
        for (page in TutorialPage.entries) {
            val id = coordinator.state.card!!.id
            coordinator.changePage(id, page)
            coordinator.dismiss(id)
            assertTrue(seen.hasSeen(method.id))
            coordinator.openManually()
            assertEquals(TutorialPage.INSTRUCTIONS, coordinator.state.card!!.page)
        }
    }

    @Test fun duplicateActivationHiddenAndHelpEventsProduceOneCard() {
        activate()
        val token = coordinator.state.navigationToken
        navigate(selection.activate(method), activated = method) // A queued double tap is rejected.
        assertEquals(token, coordinator.state.navigationToken)
        hidden()
        val first = coordinator.state.card
        repeat(3) { hidden(); coordinator.openManually() }
        assertSame(first, coordinator.state.card)
        coordinator.dismiss(first!!.id)
        hidden()
        assertNull(coordinator.state.card)
    }

    @Test fun rapidNavigationCancelsPendingHelpAndRejectsObsoleteEntranceCallbacks() {
        activate()
        val oldToken = coordinator.state.navigationToken
        navigate(selection.openModes())
        coordinator.methodEntered(oldToken)
        assertNull(coordinator.state.card)
        navigate(selection.openModes())
        assertFalse(coordinator.state.methodEntering)
        coordinator.methodEntered(oldToken)
        assertFalse(coordinator.state.methodEntering)
        hidden()
        assertFalse(coordinator.state.methodEntering)
        assertNull(coordinator.state.card) // Browsing dismissal must not reopen help.
        assertTrue(seen.ids.isEmpty())
        activate()
        coordinator.methodEntered(oldToken)
        assertNull(coordinator.state.card)
        hidden()
        assertNotNull(coordinator.state.card)
    }

    @Test fun staleCardCallbacksCannotDismissOrChangeTheNewCard() {
        activate()
        hidden()
        val oldId = coordinator.state.card!!.id
        navigate(selection.openModes()) // Cancel without marking seen.
        coordinator.dismiss(oldId)
        assertTrue(seen.ids.isEmpty())
        activate()
        hidden()
        val current = coordinator.state.card
        coordinator.dismiss(oldId)
        coordinator.changePage(oldId, TutorialPage.HOW_IT_WORKS)
        assertSame(current, coordinator.state.card)
        assertTrue(seen.ids.isEmpty())
    }

    @Test fun retainedCoordinatorAcceptsRecreatedMeasurementDestinationOnceAndKeepsCardPage() {
        activate()
        // An Activity recreated during exit starts with a closed selector and acknowledges it.
        val token = coordinator.state.navigationToken
        coordinator.methodEntered(token)
        val id = coordinator.state.card!!.id
        coordinator.changePage(id, TutorialPage.HOW_IT_WORKS)
        val retained = coordinator.state
        repeat(2) { coordinator.methodEntered(token) }
        assertEquals(retained, coordinator.state)
        assertEquals(TutorialPage.HOW_IT_WORKS, coordinator.state.card!!.page)
        assertTrue(seen.ids.isEmpty())
        coordinator.dismiss(id)
        coordinator.methodEntered(token)
        assertNull(coordinator.state.card)
    }

    @Test fun recreationWhileHomeVisibleCannotAcknowledgeItAsHidden() {
        activate()
        navigate(selection.openModes())
        hidden()
        coordinator.openManually()
        assertNull(coordinator.state.card)
        assertNull(coordinator.state.pendingMethod)
        navigate(selection.browse(method.mode))
        navigate(selection.activate(method), method)
        hidden()
        assertNotNull(coordinator.state.card)
    }

    @Test fun processDeathBeforeDismissalLeavesTutorialEligibleOnNextActivation() {
        activate()
        hidden()
        coordinator.changePage(coordinator.state.card!!.id, TutorialPage.HOW_IT_WORKS)
        val fresh = TutorialCoordinator(seen, setOf(method))
        val browsing = MeasurementSelection().browse(method.mode)
        fresh.selectionChanged(browsing)
        fresh.selectionChanged(browsing.activate(method), method)
        assertNull(fresh.state.card)
        fresh.methodEntered(fresh.state.navigationToken)
        assertEquals(TutorialPage.INSTRUCTIONS, fresh.state.card!!.page)
        assertTrue(seen.ids.isEmpty())
    }

    @Test fun manualRequestDuringMethodEntryWaitsAndUnsupportedMethodsHaveNoTutorial() {
        seen.markSeen(method.id)
        activate()
        coordinator.openManually()
        assertNull(coordinator.state.card)
        hidden()
        assertNotNull(coordinator.state.card)
        val unsupported = TutorialCoordinator(seen, emptySet())
        val browsing = MeasurementSelection().browse(method.mode)
        unsupported.selectionChanged(browsing)
        unsupported.selectionChanged(browsing.activate(method), method)
        unsupported.methodEntered(unsupported.state.navigationToken)
        unsupported.openManually()
        assertNull(unsupported.state.card)
    }

    private class MemorySeenStore : TutorialSeenStore {
        val ids = mutableSetOf<String>()
        override fun hasSeen(methodId: String) = methodId in ids
        override fun markSeen(methodId: String) { ids += methodId }
    }
}
