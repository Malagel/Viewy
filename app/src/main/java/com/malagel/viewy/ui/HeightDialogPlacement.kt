package com.malagel.viewy.ui

/** One opening's observed keyboard peak. Freeze before drawing; never follow later IME changes. */
internal data class HeightDialogPlacement(
    val maximumKeyboardBottomPx: Int = 0,
    val frozenKeyboardBottomPx: Int? = null,
) {
    fun observe(bottomPx: Int): HeightDialogPlacement = if (frozenKeyboardBottomPx != null) this else
        copy(maximumKeyboardBottomPx = maxOf(maximumKeyboardBottomPx, bottomPx))

    fun freeze(): HeightDialogPlacement = if (frozenKeyboardBottomPx != null) this else
        copy(frozenKeyboardBottomPx = maximumKeyboardBottomPx)
}
