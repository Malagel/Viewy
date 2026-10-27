package com.malagel.viewy.settings

import org.junit.Assert.*
import org.junit.Test

class AppDataResetTest {
    @Test fun onlyExplicitConfirmationCanRequestDeletionAndAcceptanceIsTerminal() {
        var requests = 0
        val controller = ResetController { requests++; true }
        controller.confirm()
        assertEquals(0, requests)
        controller.ask()
        assertEquals(ResetStatus.CONFIRMING, controller.status)
        controller.cancel()
        controller.confirm()
        assertEquals(0, requests)
        controller.ask()
        controller.confirm()
        assertEquals(1, requests)
        assertEquals(ResetStatus.ACCEPTED, controller.status)
        assertTrue(controller.locked)
        controller.confirm()
        controller.ask()
        controller.cancel()
        controller.dismissError()
        assertEquals(1, requests)
        assertEquals(ResetStatus.ACCEPTED, controller.status)
    }

    @Test fun rejectionAndExceptionReportFailureAndPermitAnExplicitRetry() {
        for (throwError in listOf(false, true)) {
            var requests = 0
            val controller = ResetController {
                requests++
                if (throwError) throw SecurityException("test rejection") else false
            }
            controller.ask()
            controller.confirm()
            assertEquals(ResetStatus.FAILED, controller.status)
            assertFalse(controller.locked)
            controller.confirm()
            assertEquals(1, requests)
            controller.dismissError()
            controller.ask()
            controller.confirm()
            assertEquals(2, requests)
        }
    }

    @Test fun systemLocaleIsClearedBeforeTheRequestAndRestoredOnlyOnFailure() {
        for (outcome in listOf("accepted", "rejected", "exception")) {
            val changes = mutableListOf<String>()
            var currentTags = "es"
            val reset = LocaleSafeAppDataReset(clearUserData = {
                assertEquals("", currentTags)
                changes += "request"
                if (outcome == "exception") throw IllegalStateException("test")
                outcome == "accepted"
            }, locales = object : ResetLocales {
                override fun read() = currentTags
                override fun set(tags: String) { changes += tags; currentTags = tags }
            })
            val controller = ResetController(reset)
            controller.ask()
            controller.confirm()
            assertEquals(if (outcome == "accepted") ResetStatus.ACCEPTED else ResetStatus.FAILED, controller.status)
            assertEquals(if (outcome == "accepted") "" else "es", currentTags)
            assertEquals(if (outcome == "accepted") listOf("", "request") else listOf("", "request", "es"), changes)
        }
    }

    @Test fun localeFailureCannotProceedToDeletion() {
        var requests = 0
        val controller = ResetController(LocaleSafeAppDataReset({ requests++; true }, object : ResetLocales {
            override fun read(): String = throw SecurityException("test")
            override fun set(tags: String) = Unit
        }))
        controller.ask()
        controller.confirm()
        assertEquals(0, requests)
        assertEquals(ResetStatus.FAILED, controller.status)
    }

    @Test fun reentrantRequestsAreBlockedWhileAndroidIsHandlingTheFirstRequest() {
        lateinit var controller: ResetController
        var requests = 0
        controller = ResetController {
            requests++
            assertEquals(ResetStatus.REQUESTING, controller.status)
            assertTrue(controller.locked)
            controller.ask()
            controller.confirm()
            controller.cancel()
            true
        }
        controller.ask()
        controller.confirm()
        assertEquals(1, requests)
        assertEquals(ResetStatus.ACCEPTED, controller.status)
    }
}
