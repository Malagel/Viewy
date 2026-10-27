package com.malagel.viewy.settings

fun interface AppDataReset {
    /** True means accepted, never proof of completion. The process normally terminates. */
    fun request(): Boolean
}

interface ResetLocales {
    fun read(): String
    fun set(tags: String)
}

/** Clear the system-owned override before deletion; restore it if deletion is rejected. */
class LocaleSafeAppDataReset(
    private val clearUserData: () -> Boolean,
    private val locales: ResetLocales,
) : AppDataReset {
    override fun request(): Boolean {
        val previous = locales.read()
        var accepted = false
        try {
            locales.set("")
            accepted = clearUserData()
            return accepted
        } finally {
            if (!accepted) locales.set(previous)
        }
    }
}

enum class ResetStatus { IDLE, CONFIRMING, REQUESTING, ACCEPTED, FAILED }

/** No Android dependencies; tests inject a fake and can never erase real app data. */
class ResetController(private val reset: AppDataReset) {
    var status = ResetStatus.IDLE
        private set

    val locked: Boolean get() = status == ResetStatus.REQUESTING || status == ResetStatus.ACCEPTED
    fun ask() { if (!locked) status = ResetStatus.CONFIRMING }
    fun cancel() { if (status == ResetStatus.CONFIRMING) status = ResetStatus.IDLE }
    fun dismissError() { if (status == ResetStatus.FAILED) status = ResetStatus.IDLE }
    fun confirm() {
        if (status != ResetStatus.CONFIRMING) return
        status = ResetStatus.REQUESTING
        status = try {
            if (reset.request()) ResetStatus.ACCEPTED else ResetStatus.FAILED
        } catch (_: Exception) {
            ResetStatus.FAILED
        }
    }
}
