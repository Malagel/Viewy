package com.malagel.viewy.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.malagel.viewy.R

@Composable
internal fun wordmarkStyle(compact: Boolean): TextStyle = MaterialTheme.typography.headlineLarge.copy(
    fontFamily = ViewyWordmarkFont, fontWeight = FontWeight.Bold,
    fontSize = if (compact) 30.sp else 38.sp, lineHeight = if (compact) 38.sp else 46.sp,
)

internal data class HeaderGeometry(val separateActions: Boolean, val height: Dp)

/** Measure the same strings/styles as the header, including nonlinear system text scaling. */
@Composable
internal fun headerGeometry(width: Dp, compact: Boolean, overflow: Boolean = false): HeaderGeometry {
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val wordmark = stringResource(R.string.app_name)
    val subtitle = stringResource(R.string.measurement_method_subtitle)
    val style = wordmarkStyle(compact)
    val subtitleStyle = MaterialTheme.typography.bodySmall
    return remember(width, compact, overflow, density, measurer, wordmark, subtitle, style, subtitleStyle) {
        with(density) {
            val available = width.roundToPx().coerceAtLeast(1)
            val actions = (108.dp + if (overflow) 48.dp else 0.dp).roundToPx()
            val logo = measurer.measure(wordmark, style, softWrap = false)
            val separate = headerNeedsActionRow(available, logo.size.width, actions)
            val textWidth = if (separate) available else (available - actions).coerceAtLeast(1)
            val logoHeight = measurer.measure(wordmark, style, constraints = Constraints(maxWidth = textWidth)).size.height
            val subtitleHeight = if (compact) 0 else 4.dp.roundToPx() +
                measurer.measure(subtitle, subtitleStyle, constraints = Constraints(maxWidth = textWidth)).size.height
            val textHeight = logoHeight + subtitleHeight
            val contentHeight = if (separate) textHeight + 52.dp.roundToPx() else maxOf(textHeight, 52.dp.roundToPx())
            HeaderGeometry(separate, contentHeight.toDp() + if (compact) 16.dp else 40.dp)
        }
    }
}

/** Shared Home/measurement decision, independent of sensor snapshots and workflow state. */
@Composable
internal fun rememberMainLayout(width: Dp, height: Dp): MainLayout {
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val top = stringResource(R.string.measurement_stage_top)
    val bottom = stringResource(R.string.measurement_stage_bottom)
    val standardStage = MaterialTheme.typography.titleLarge
    val compactStage = MaterialTheme.typography.titleMedium
    val boundedWidth = width.coerceAtMost(600.dp)
    val standardHeader = headerGeometry((boundedWidth - 40.dp).coerceAtLeast(1.dp), false)
    val compactHeader = headerGeometry((boundedWidth - 24.dp).coerceAtLeast(1.dp), true)
    return remember(width, height, density, measurer, top, bottom, standardStage, compactStage, standardHeader, compactHeader) {
        with(density) {
            fun stageHeight(style: TextStyle, margin: Dp): Dp {
                val constraints = Constraints(maxWidth = (boundedWidth - margin).roundToPx().coerceAtLeast(1))
                return maxOf(measurer.measure(top, style, constraints = constraints).size.height,
                    measurer.measure(bottom, style, constraints = constraints).size.height).toDp()
            }
            // Outer padding, header/preview/caption gaps, and the existing shutter sizes.
            val standardChrome = 8.dp + 32.dp + 24.dp + 88.dp + standardHeader.height + stageHeight(standardStage, 40.dp)
            val compactChrome = 8.dp + 16.dp + 24.dp + 72.dp + compactHeader.height + stageHeight(compactStage, 24.dp)
            mainLayoutPolicy(width.value, height.value, fontScale, standardChrome.value, compactChrome.value)
        }
    }
}
