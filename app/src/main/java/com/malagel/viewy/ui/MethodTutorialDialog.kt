package com.malagel.viewy.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.malagel.viewy.R
import com.malagel.viewy.tutorial.TutorialCard
import com.malagel.viewy.tutorial.TutorialPage

/** One stationary card with shared section navigation. Does not own measurement state. */
@Composable
internal fun MethodTutorialDialog(
    card: TutorialCard,
    onDismiss: (Long) -> Unit,
    onPage: (Long, TutorialPage) -> Unit,
) {
    val content = methodTutorials[card.method] ?: return
    val title = stringResource(content.title)
    val dismiss = { onDismiss(card.id) }
    val pages = key(card.id) { updateTransition(card.page, label = "Tutorial section") }
    val settled = pages.currentState == pages.targetState && !pages.isRunning
    val previous = card.page.previous
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
                            androidx.compose.animation.AnimatedVisibility(visible = previous != null,
                                enter = fadeIn(tween(150)), exit = fadeOut(tween(150))) {
                                TextButton(onClick = { if (settled) previous?.let { onPage(card.id, it) } },
                                    enabled = settled && previous != null,
                                    modifier = if (previous == null) Modifier.clearAndSetSemantics {} else Modifier,
                                    colors = ButtonDefaults.textButtonColors(disabledContentColor = MaterialTheme.colorScheme.primary)) {
                                    Text(stringResource(R.string.tutorial_go_back))
                                }
                            }
                        }
                        TextButton(onClick = dismiss, modifier = Modifier.heightIn(min = 48.dp)) {
                            Text(stringResource(R.string.close))
                        }
                    }
                    pages.AnimatedContent(
                        modifier = Modifier.weight(1f).fillMaxWidth().clipToBounds(),
                        transitionSpec = {
                            val direction = if (targetState.ordinal > initialState.ordinal) 1 else -1
                            (slideInHorizontally(
                                tween(260, easing = FastOutSlowInEasing), initialOffsetX = { it * direction },
                            ) togetherWith slideOutHorizontally(
                                tween(260, easing = FastOutSlowInEasing), targetOffsetX = { -it * direction },
                            )).using(null)
                        },
                    ) { page ->
                        // Each entry owns its scroll state; outgoing content cannot receive input.
                        NavigationInputGuard(!settled || page != card.page, Modifier.fillMaxSize()) {
                            TutorialPageContent(content, page) {
                                if (settled && page == card.page) page.next?.let { onPage(card.id, it) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TutorialPageContent(content: MethodTutorialContent, page: TutorialPage, onNext: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(page.titleResource()), Modifier.semantics { heading() },
                style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            content.pages.getValue(page).forEach { section ->
                section.title?.let {
                    Text(stringResource(it), Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium)
                }
                section.blocks.forEach { block ->
                    when (block) {
                        TutorialBlock.KnownHeightIllustration -> KnownHeightTutorialAnimation(
                            contentDescription = stringResource(R.string.tutorial_known_height_animation_description),
                            modifier = Modifier.widthIn(max = 400.dp).fillMaxWidth().align(Alignment.CenterHorizontally),
                        )
                        is TutorialBlock.Paragraph -> Text(stringResource(block.text), style = MaterialTheme.typography.bodyLarge)
                        is TutorialBlock.Equation -> TutorialEquation(block.notation, block.spoken)
                        is TutorialBlock.BulletList -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            block.items.forEach { item ->
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.semantics(mergeDescendants = true) {}) {
                                    val color = LocalContentColor.current
                                    val density = LocalDensity.current
                                    val dotSize = with(density) { 6.sp.toDp() }
                                    val firstLineHeight = with(density) { MaterialTheme.typography.bodyLarge.lineHeight.toDp() }
                                    Box(Modifier.width(dotSize).height(firstLineHeight).clearAndSetSemantics {}, contentAlignment = Alignment.Center) {
                                        Canvas(Modifier.size(dotSize)) { drawCircle(color) }
                                    }
                                    Text(stringResource(item), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                                }
                            }
                        }
                    }
                }
            }
        }
        page.next?.let { next ->
            val nextDescription = stringResource(R.string.tutorial_next_section, stringResource(next.titleResource()))
            val actionModifier = if (page == TutorialPage.INSTRUCTIONS) {
                Modifier.align(Alignment.End).padding(end = 12.dp, bottom = 12.dp)
            } else {
                Modifier.align(Alignment.Start).padding(start = 12.dp, bottom = 12.dp)
            }
            if (page == TutorialPage.INSTRUCTIONS) {
                MeasurementActionButton(label = nextDescription, onClick = onNext, modifier = actionModifier) {
                    Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null, modifier = Modifier.size(28.dp))
                }
            } else {
                TextButton(
                    onClick = onNext,
                    modifier = actionModifier.heightIn(min = 48.dp).semantics { contentDescription = nextDescription },
                ) {
                    Text(stringResource(next.titleResource()), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
