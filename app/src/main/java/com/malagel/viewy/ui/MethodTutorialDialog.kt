package com.malagel.viewy.ui

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.malagel.viewy.R
import com.malagel.viewy.tutorial.TutorialCard
import com.malagel.viewy.tutorial.TutorialPage

/** One stationary card and one modal window for both pages. Does not own measurement state. */
@Composable
internal fun MethodTutorialDialog(
    card: TutorialCard,
    onDismiss: (Long) -> Unit,
    onPage: (Long, TutorialPage) -> Unit,
) {
    val content = methodTutorials[card.method] ?: return
    val title = stringResource(content.title)
    val dismiss = { onDismiss(card.id) }
    Dialog(
        onDismissRequest = dismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        BoxWithConstraints(
            Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.displayCutout)),
            contentAlignment = Alignment.Center,
        ) {
            // The edge-to-edge window needs its own outside-card hit target. A sibling,
            // rather than a clickable parent, keeps scrolling and card controls independent.
            Box(Modifier.matchParentSize().pointerInput(card.id) { detectTapGestures { dismiss() } })
            Surface(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)
                    .widthIn(max = 560.dp).fillMaxWidth()
                    .height((maxHeight - 32.dp).coerceAtLeast(0.dp).coerceAtMost(640.dp))
                    .semantics { paneTitle = title }
                    .pointerInput(Unit) { detectTapGestures { /* Consume taps in empty card space. */ } },
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Column {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) {
                            if (card.page == TutorialPage.DETAILS) TextButton(onClick = { onPage(card.id, TutorialPage.BASIC) }) {
                                Text(stringResource(R.string.tutorial_go_back))
                            }
                        }
                        TextButton(onClick = dismiss, modifier = Modifier.heightIn(min = 48.dp)) {
                            Text(stringResource(R.string.close))
                        }
                    }
                    // Each page starts at the top; hardware-state recomposition never resets it.
                    key(card.id, card.page) {
                        Column(
                            Modifier.weight(1f).verticalScroll(rememberScrollState())
                                .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            Text(title, Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineSmall)
                            val sections = if (card.page == TutorialPage.BASIC) content.basic else content.details
                            sections.forEach { section ->
                                section.title?.let { Text(stringResource(it), Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium) }
                                section.blocks.forEach { block ->
                                    when (block) {
                                        is TutorialBlock.Paragraph -> Text(stringResource(block.text), style = MaterialTheme.typography.bodyLarge)
                                        is TutorialBlock.Equation -> TutorialEquation(block.notation, block.spoken)
                                    }
                                }
                            }
                            if (card.page == TutorialPage.BASIC) {
                                TextButton(onClick = { onPage(card.id, TutorialPage.DETAILS) }) {
                                    Text(stringResource(R.string.tutorial_know_more))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
