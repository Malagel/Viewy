package com.malagel.viewy.measurement

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormatSymbols
import java.util.Locale

/** Entry units are local to a draft; the global presentation preference remains meters/feet. */
enum class HeightEntryUnit(val metersPerUnit: BigDecimal, val label: String) {
    METERS(BigDecimal.ONE, "m"), CENTIMETERS(BigDecimal("0.01"), "cm"),
    FEET(BigDecimal("0.3048"), "ft"), INCHES(BigDecimal("0.0254"), "in");

    val alternative: HeightEntryUnit get() = when (this) {
        METERS -> CENTIMETERS
        CENTIMETERS -> METERS
        FEET -> INCHES
        INCHES -> FEET
    }
}

/** Canonical point-decimal text and an independent physical value: switches never reparse rounded text. */
@ConsistentCopyVisibility
data class HeightDraft private constructor(
    val text: String,
    val unit: HeightEntryUnit,
    private val physicalMeters: BigDecimal?,
    val correctionFailed: Boolean = false,
    val rejectedDigitId: Long = 0,
) {
    val meters: Double? get() = physicalMeters?.toDouble()?.takeIf(::isSupportedHeight)

    val atDigitLimit: Boolean get() = text.count(Char::isDigit) >= maximumDigits

    fun displayText(locale: Locale): String {
        // Keep very small, valid converted heights honest without exposing a long decimal tail.
        val value = if (meters != null && text.toBigDecimalOrNull()?.signum() == 0) "<0.0001" else text
        return value.replace('.', decimalSeparator(locale))
    }

    fun key(character: Char): HeightDraft {
        val next = when {
            character in '0'..'9' -> if (text == "0") character.toString() else text + character
            character == '.' || character == ',' -> if ('.' in text) return this else if (text.isEmpty()) "0." else "$text."
            else -> return this
        }
        if (character in '0'..'9' && next.count(Char::isDigit) > maximumDigits) {
            return copy(rejectedDigitId = rejectedDigitId + 1)
        }
        return edited(next)
    }

    internal fun failedCorrection(): HeightDraft = copy(correctionFailed = true)

    fun backspace(): HeightDraft = edited(text.dropLast(1))

    private fun edited(value: String): HeightDraft = copy(text = value,
        physicalMeters = value.toBigDecimalOrNull()?.multiply(unit.metersPerUnit), correctionFailed = false)

    fun switchUnit(): HeightDraft = convertedTo(unit.alternative)

    fun inSystem(preference: HeightUnit): HeightDraft {
        val metric = unit == HeightEntryUnit.METERS || unit == HeightEntryUnit.CENTIMETERS
        return if (metric == (preference == HeightUnit.METERS)) this
        else convertedTo(if (preference == HeightUnit.METERS) HeightEntryUnit.METERS else HeightEntryUnit.FEET)
    }

    private fun convertedTo(next: HeightEntryUnit): HeightDraft = copy(unit = next,
        text = if (meters != null) physicalMeters!!.entryText(next) else text, correctionFailed = false)

    companion object {
        const val maximumDigits = 10
        private const val convertedFractionDigits = 4

        fun open(meters: Double?, preference: HeightUnit): HeightDraft {
            val unit = if (preference == HeightUnit.METERS) HeightEntryUnit.METERS else HeightEntryUnit.FEET
            val physical = meters?.let(BigDecimal::valueOf)
            return HeightDraft(physical?.entryText(unit) ?: "", unit, physical)
        }

        fun decimalSeparator(locale: Locale): Char =
            if (DecimalFormatSymbols.getInstance(locale).decimalSeparator == ',') ',' else '.'

        private fun BigDecimal.entryText(unit: HeightEntryUnit): String =
            divide(unit.metersPerUnit, convertedFractionDigits, RoundingMode.DOWN).stripTrailingZeros().toPlainString()
    }
}

/** One temporary editing session, retained by the ViewModel; drafts never mutate saved evidence. */
class HeightEditor(private val workflow: KnownHeightWorkflow) {
    var draft: HeightDraft? = null
        private set

    private var hadPendingCapture = false

    fun open(preference: HeightUnit) {
        if (draft != null) return
        hadPendingCapture = workflow.state.top != null && !workflow.state.completed
        draft = HeightDraft.open(workflow.state.heightMeters, preference)
    }

    fun key(character: Char) { draft = draft?.key(character) }
    fun backspace() { draft = draft?.backspace() }
    fun switchUnit() { draft = draft?.switchUnit() }
    fun preferenceChanged(preference: HeightUnit) { draft = draft?.inSystem(preference) }
    fun cancel() { draft = null }

    fun confirm(): Boolean {
        val current = draft ?: return false
        val meters = current.meters ?: return false
        if (workflow.state.completed) {
            if (!workflow.correctCompletedHeight(meters)) {
                draft = current.failedCorrection()
                return false
            }
        } else if (meters != workflow.state.heightMeters) {
            workflow.setHeight(meters, MeasurementNotice.ACQUISITION_SUSPENDED.takeIf {
                hadPendingCapture && workflow.state.notice == MeasurementNotice.ACQUISITION_SUSPENDED
            })
        }
        draft = null
        return true
    }
}
