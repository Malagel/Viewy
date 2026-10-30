package com.malagel.viewy.tutorial

import com.malagel.viewy.domain.MeasurementMethod
import com.malagel.viewy.measurement.MeasurementSelection

interface TutorialSeenStore {
    fun hasSeen(methodId: String): Boolean
    fun markSeen(methodId: String)
}

enum class TutorialPage {
    INSTRUCTIONS, IMPORTANT, HOW_IT_WORKS;

    val previous: TutorialPage? get() = entries.getOrNull(ordinal - 1)
    val next: TutorialPage? get() = entries.getOrNull(ordinal + 1)
}

/** Temporary presentation state, retained with the ViewModel, never written to preferences. */
data class TutorialCard(val id: Long, val method: MeasurementMethod, val page: TutorialPage = TutorialPage.INSTRUCTIONS)

data class TutorialState(
    val navigationToken: Long = 0,
    val methodEntering: Boolean = false,
    val pendingMethod: MeasurementMethod? = null,
    val card: TutorialCard? = null,
)

/** Main-thread coordinator. Has no access to measurement inputs, acquisition or workflow actions. */
class TutorialCoordinator(
    private val seen: TutorialSeenStore,
    private val supportedMethods: Set<MeasurementMethod>,
) {
    var state = TutorialState()
        private set
    private var selection = MeasurementSelection()
    private var enteredToken: Long? = null
    private var nextCardId = 0L

    fun selectionChanged(next: MeasurementSelection, activated: MeasurementMethod? = null) {
        if (next == selection) return
        val validActivation = activated != null && selection.browsingMode == activated.mode &&
            next == selection.activate(activated)
        selection = next
        enteredToken = null
        // Navigation cancels a pending/visible tutorial without recording a dismissal.
        state = TutorialState(navigationToken = state.navigationToken + 1, methodEntering = next.activeMethod != null)
        if (validActivation && activated in supportedMethods && !seen.hasSeen(activated.id)) {
            state = state.copy(pendingMethod = activated)
        }
    }

    /** Acknowledged only when the measurement destination has settled (also after recreation). */
    fun methodEntered(token: Long) {
        if (token != state.navigationToken || selection.isOpen) return
        enteredToken = token
        state = state.copy(methodEntering = false)
        state.pendingMethod?.takeIf { it == selection.activeMethod }?.let(::show)
    }

    fun openManually() {
        val method = selection.activeMethod ?: return
        if (selection.isOpen || method !in supportedMethods || state.card != null) return
        if (enteredToken == state.navigationToken) show(method)
        else state = state.copy(pendingMethod = method)
    }

    private fun show(method: MeasurementMethod) {
        state = state.copy(pendingMethod = null, card = TutorialCard(++nextCardId, method))
    }

    fun changePage(cardId: Long, page: TutorialPage) {
        val card = state.card?.takeIf { it.id == cardId } ?: return
        state = state.copy(card = card.copy(page = page))
    }

    fun dismiss(cardId: Long) {
        val card = state.card?.takeIf { it.id == cardId } ?: return
        seen.markSeen(card.method.id)
        state = state.copy(card = null)
    }
}
