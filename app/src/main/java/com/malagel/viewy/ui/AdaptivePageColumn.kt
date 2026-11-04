package com.malagel.viewy.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp

/** Pin chrome when it leaves a readable body; otherwise one scroll owner holds the whole page. */
@Composable
internal fun AdaptivePageColumn(
    modifier: Modifier = Modifier,
    header: @Composable () -> Unit = {},
    footer: @Composable () -> Unit = {},
    bodyScroll: ScrollState = rememberScrollState(),
    pageScroll: ScrollState = rememberScrollState(),
    scrollEnabled: Boolean = true,
    body: @Composable (Modifier) -> Unit,
) {
    BoxWithConstraints(modifier) {
        val viewportHeight = constraints.maxHeight
        SubcomposeLayout(Modifier.verticalScroll(pageScroll, enabled = scrollEnabled)) { constraints ->
            val loose = Constraints(maxWidth = constraints.maxWidth)
            val top = subcompose("header", header).map { it.measure(loose) }
            val bottom = subcompose("footer", footer).map { it.measure(loose) }
            val topHeight = top.sumOf { it.height }
            val bottomHeight = bottom.sumOf { it.height }
            val pinned = pinPageChrome(viewportHeight, topHeight, bottomHeight, 96.dp.roundToPx())
            val bodyHeight = (viewportHeight - topHeight - bottomHeight).coerceAtLeast(0)
            val middle = subcompose("body") {
                body(if (pinned) Modifier.verticalScroll(bodyScroll, enabled = scrollEnabled).heightIn(min = bodyHeight.toDp()) else Modifier)
            }.map { it.measure(if (pinned) loose.copy(minHeight = bodyHeight, maxHeight = bodyHeight) else loose) }
            val total = topHeight + middle.sumOf { it.height } + bottomHeight
            layout(constraints.maxWidth, maxOf(viewportHeight, total)) {
                var y = 0
                (top + middle + bottom).forEach { child ->
                    child.placeRelative((constraints.maxWidth - child.width) / 2, y)
                    y += child.height
                }
            }
        }
    }
}
