package com.malagel.viewy.ui

/** Available content bounds, after host insets. Text geometry is supplied by Compose. */
internal enum class MainLayout { STANDARD, COMPACT, CONSTRAINED }

internal fun mainLayoutPolicy(
    widthDp: Float, heightDp: Float, fontScale: Float,
    standardChromeDp: Float, compactChromeDp: Float,
): MainLayout {
    // Preserve the established portrait presentation whenever its chrome and viewfinder fit.
    val compact = heightDp < 500f || widthDp < 320f || fontScale > 1.5f
    if (!compact && heightDp - standardChromeDp >= minimumViewfinderDp) return MainLayout.STANDARD
    return if (widthDp >= 280f && heightDp - compactChromeDp >= minimumViewfinderDp)
        MainLayout.COMPACT else MainLayout.CONSTRAINED
}

// Fits the 96 dp zoom control, ratio label and a separate bottom status target.
internal const val minimumViewfinderDp = 240f
internal const val minimumHeightNumberSp = 24

/** Keep readable text even when no candidate fits; its container must then expose scrolling. */
internal fun readableTextSize(maximumSp: Int, minimumSp: Int, stepSp: Int = 1, fits: (Int) -> Boolean): Int =
    (maximumSp downTo minimumSp step stepSp).firstOrNull(fits) ?: minimumSp

internal fun headerNeedsActionRow(width: Int, wordmarkWidth: Int, actionsWidth: Int): Boolean =
    wordmarkWidth + actionsWidth > width

internal fun pinPageChrome(height: Int, header: Int, footer: Int, minimumBody: Int): Boolean =
    height - header - footer >= minimumBody

/** Once opened, keep the same dialog window through resizing until explicitly collapsed. */
internal fun keepWorkspaceOpen(open: Boolean, wasConstrained: Boolean, constrained: Boolean, hasHeight: Boolean): Boolean =
    open || (!wasConstrained && constrained && hasHeight)
