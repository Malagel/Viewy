package com.malagel.viewy.ui

import android.view.WindowManager
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.stopScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.focusProperties
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import com.malagel.viewy.R
import com.malagel.viewy.tutorial.TutorialCard
import com.malagel.viewy.tutorial.TutorialPage
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

// Programmatic paging is short and deterministic; touch flings keep the pager's own spec.
private val tutorialPageAnimation = tween<Float>(220, easing = FastOutSlowInEasing)

/** Shared two-page tutorial and a separate full-screen documentation destination. */
@Composable
internal fun MethodTutorialDialog(
    card: TutorialCard,
    onDismiss: (Long) -> Unit,
    onPage: (Long, TutorialPage) -> Unit,
    onBack: (Long) -> Unit,
    onDocumentation: (Long) -> Unit,
) {
    val content = methodTutorials[card.method] ?: return
    val title = stringResource(content.title)
    val pager = key(card.id) { rememberPagerState(initialPage = card.page.ordinal) { TutorialPage.entries.size } }
    val publishPage by rememberUpdatedState(onPage)
    val dismiss by rememberUpdatedState(onDismiss)
    val documentationOpen by rememberUpdatedState(card.documentationOpen)
    var closing by rememberSaveable(card.id) { mutableStateOf(false) }
    var entered by rememberSaveable(card.id) { mutableStateOf(false) }
    var systemBackInProgress by remember(card.id) { mutableStateOf(false) }
    val visibility = remember(card.id) { Animatable(if (entered) 1f else 0f) }
    val dimVisibility = remember(card.id) { Animatable(if (entered) 1f else 0f) }
    val travel = with(LocalDensity.current) { 24.dp.toPx() }
    LaunchedEffect(card.id, closing) {
        if (closing) {
            coroutineScope {
                launch { visibility.animateTo(0f, tween(180, easing = FastOutSlowInEasing)) }
                launch { dimVisibility.animateTo(0f, tween(240, easing = FastOutSlowInEasing)) }
            }
            dismiss(card.id)
        } else {
            entered = true
            coroutineScope {
                launch { visibility.animateTo(1f, tween(240, easing = FastOutSlowInEasing)) }
                launch { dimVisibility.animateTo(1f, tween(240, easing = FastOutSlowInEasing)) }
            }
        }
    }
    val settled by remember(pager, card.page) {
        derivedStateOf { !pager.isScrollInProgress && pager.settledPage == card.page.ordinal }
    }
    val cardInteractive by remember(visibility, closing) { derivedStateOf { visibility.value == 1f && !closing } }
    // Initializing from retained navigation makes a recreated dialog appear settled.
    val documentationTransition = updateTransition(card.documentationOpen, label = "documentation")
    val documentationProgress = documentationTransition.animateFloat(
        transitionSpec = { tween(260, easing = FastOutSlowInEasing) }, label = "documentation progress",
    ) { if (it) 1f else 0f }
    val documentationCoverage by remember {
        derivedStateOf {
            documentationCoversTutorial(documentationTransition.currentState,
                documentationTransition.targetState, documentationTransition.isRunning)
        }
    }
    val documentationMoving by remember {
        derivedStateOf {
            documentationTransition.currentState != documentationTransition.targetState || documentationTransition.isRunning
        }
    }
    val pageScope = rememberCoroutineScope()
    val moveToPage: (TutorialPage) -> Unit = { page ->
        pageScope.launch {
            pager.animateScrollToPage(page.ordinal, animationSpec = tutorialPageAnimation)
        }
    }
    // Button/Back requests and swipes share one pager. Only settled pages enter retained state.
    LaunchedEffect(card.id, card.page, systemBackInProgress, closing) {
        if (!systemBackInProgress && !closing &&
            (pager.currentPage != card.page.ordinal || pager.currentPageOffsetFraction != 0f)) {
            pager.animateScrollToPage(card.page.ordinal, animationSpec = tutorialPageAnimation)
        }
    }
    LaunchedEffect(pager) {
        snapshotFlow { if (pager.isScrollInProgress || systemBackInProgress || closing) null else pager.settledPage }
            .distinctUntilChanged().drop(1).collect { index ->
            // An interrupted button animation may settle back on its original page.
            if (index != null && !documentationOpen) publishPage(card.id, TutorialPage.entries[index])
        }
    }
    Dialog(
        onDismissRequest = { if (!documentationCoverage && !systemBackInProgress) closing = true },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false, dismissOnBackPress = false),
    ) {
        // This transient window owns all entrance/exit motion; platform window motion must not stack with it.
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        val normalDimAmount = remember(window) { window?.attributes?.dimAmount ?: 0f }
        @Suppress("DEPRECATION") // Dialog/older-Android compatibility; Compose paints behind transparent bars.
        SideEffect {
            window?.apply {
                // DialogProperties handles edge-to-edge layout, but this separate window
                // also needs transparent system bars. Compose supplies their background.
                addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
                statusBarColor = android.graphics.Color.TRANSPARENT
                navigationBarColor = android.graphics.Color.TRANSPARENT
                isStatusBarContrastEnforced = false
                isNavigationBarContrastEnforced = false
                WindowCompat.getInsetsController(this, decorView).apply {
                    isAppearanceLightStatusBars = false
                    isAppearanceLightNavigationBars = false
                }
            }
            window?.setWindowAnimations(0)
            // Compose owns the scrim so removing the native window cannot cut off its fade.
            window?.setDimAmount(0f)
        }
        PredictiveBackHandler { progress ->
            if (documentationCoverage) {
                // Consume Back throughout exit; a repeated request must not page/dismiss help.
                // Predictive cancellation never changes the retained destination.
                systemBackInProgress = true
                try {
                    progress.collect { }
                    if (documentationOpen && !closing) onBack(card.id)
                } finally {
                    systemBackInProgress = false
                }
                return@PredictiveBackHandler
            }
            val sourcePage = TutorialPage.entries[pager.settledPage]
            systemBackInProgress = true
            try {
                with(pager) {
                    // Stop the drag AND remove its offset while preventing competing mutations.
                    scroll(MutatePriority.PreventUserInput) {
                        updateCurrentPage(sourcePage.ordinal, 0f)
                        updateTargetPage(sourcePage.ordinal)
                        progress.collect { }
                    }
                }
                if (!closing) {
                    val previous = sourcePage.previous
                    if (previous == null) closing = true
                    else {
                        // Keep input/publication suspended until the committed destination is aligned.
                        pager.animateScrollToPage(previous.ordinal, animationSpec = tutorialPageAnimation)
                        publishPage(card.id, previous)
                    }
                }
            } catch (cancelled: CancellationException) {
                withContext(NonCancellable) {
                    with(pager) {
                        scroll(MutatePriority.PreventUserInput) {
                            updateCurrentPage(sourcePage.ordinal, 0f)
                            updateTargetPage(sourcePage.ordinal)
                        }
                    }
                    publishPage(card.id, sourcePage)
                }
                throw cancelled
            } finally {
                // Cancellation restores pager input; only a committed system Back navigates.
                systemBackInProgress = false
            }
        }
        Box(Modifier.fillMaxSize().clipToBounds().reserveSystemBackGestures(!closing)) {
            Box(Modifier.fillMaxSize().graphicsLayer { alpha = dimVisibility.value }
                .background(Color.Black.copy(alpha = normalDimAmount)))
            BoxWithConstraints(
                Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.displayCutout)),
                contentAlignment = Alignment.Center,
            ) {
                val cardHeight = (maxHeight - 32.dp).coerceAtLeast(0.dp).coerceAtMost(640.dp)
                Box(Modifier.matchParentSize().pointerInput(card.id) { detectTapGestures { if (!documentationCoverage && !systemBackInProgress) closing = true } })
                NavigationInputGuard(documentationCoverage || !cardInteractive || systemBackInProgress, Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Surface(
                            modifier = Modifier.padding(16.dp).widthIn(max = 560.dp).fillMaxWidth()
                                .height(cardHeight)
                                .graphicsLayer {
                                    alpha = visibility.value
                                    translationY = (1f - visibility.value) * travel
                                    translationX = -documentationProgress.value * travel
                                }
                                .semantics { paneTitle = title }
                                .pointerInput(Unit) { detectTapGestures { /* Consume empty card taps. */ } },
                            shape = MaterialTheme.shapes.extraLarge,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        ) {
                            HorizontalPager(
                                state = pager,
                                // Both small pages are ready before a button starts the slide.
                                beyondViewportPageCount = 1,
                                modifier = Modifier.fillMaxSize(),
                                userScrollEnabled = !documentationCoverage && cardInteractive && !systemBackInProgress,
                                flingBehavior = PagerDefaults.flingBehavior(pager, snapPositionalThreshold = 0.1f),
                                overscrollEffect = null,
                                key = { TutorialPage.entries[it] },
                            ) { index ->
                                val page = TutorialPage.entries[index]
                                val interactive = settled && page == card.page && !documentationCoverage && cardInteractive && !systemBackInProgress
                                // Do not add a touch-consuming overlay during a drag: the pager owns it.
                                Box(Modifier.fillMaxSize().focusProperties {
                                    canFocus = interactive
                                    onEnter = { if (!interactive) cancelFocusChange() }
                                }.focusGroup().then(if (interactive) Modifier else Modifier.clearAndSetSemantics {})) {
                                    TutorialPageContent(content, page,
                                        covered = documentationCoverage,
                                        animateIllustration = !documentationCoverage && (pager.isScrollInProgress || pager.settledPage == index),
                                        onNext = { if (interactive) page.next?.let(moveToPage) },
                                        onBack = { if (interactive) page.previous?.let(moveToPage) },
                                        onDismiss = { if (interactive) closing = true },
                                        onDocumentation = { if (interactive) onDocumentation(card.id) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
            if (documentationCoverage) {
                NavigationInputGuard(documentationMoving || systemBackInProgress, Modifier.fillMaxSize()
                    .graphicsLayer {
                        translationX = (1f - documentationProgress.value) * size.width
                        alpha = documentationProgress.value
                    }) {
                    MethodDocumentationScreen(card.method, onBack = {
                        if (documentationOpen && !documentationMoving && !systemBackInProgress) onBack(card.id)
                    })
                }
            }
        }
    }
}

/** Includes a reversed entrance whose current and target states are both closed. */
internal fun documentationCoversTutorial(current: Boolean, target: Boolean, running: Boolean): Boolean =
    current || target || running

@Composable
private fun TutorialPageContent(
    content: MethodTutorialContent, page: TutorialPage, covered: Boolean, animateIllustration: Boolean,
    onNext: () -> Unit, onBack: () -> Unit, onDismiss: () -> Unit, onDocumentation: () -> Unit,
) {
    val scroll = rememberScrollState()
    // A footer tap can open documentation while prose is still flinging. Freeze the
    // position seen at coverage, rather than letting a hidden fling change it later.
    val coveredOffset = if (covered) scroll.value else null
    LaunchedEffect(covered) {
        if (coveredOffset != null) {
            scroll.stopScroll(MutatePriority.PreventUserInput)
            scroll.scrollTo(coveredOffset)
        }
    }
    Column(Modifier.fillMaxSize()) {
        Text(stringResource(page.titleResource()),
            Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp).semantics { heading() },
            style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Column(
            Modifier.weight(1f).verticalScroll(scroll, enabled = !covered)
                .padding(vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            content.pages.getValue(page).forEach { section ->
                section.title?.let {
                    Text(stringResource(it), Modifier.padding(horizontal = 24.dp).semantics { heading() }, style = MaterialTheme.typography.titleMedium)
                }
                section.blocks.forEach { block ->
                    when (block) {
                        TutorialBlock.KnownHeightIllustration -> KnownHeightTutorialAnimation(
                            contentDescription = stringResource(R.string.tutorial_known_height_animation_description),
                            animate = animateIllustration,
                            modifier = Modifier.padding(horizontal = 12.dp).widthIn(max = 440.dp).fillMaxWidth().align(Alignment.CenterHorizontally),
                        )
                        is TutorialBlock.Paragraph -> Text(stringResource(block.text), Modifier.padding(horizontal = 24.dp), style = MaterialTheme.typography.bodyLarge)
                        is TutorialBlock.BulletList -> Column(Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
        TutorialPageActions(page, onNext, onBack, onDismiss, onDocumentation)
    }
}

/** Shared fixed footer; page content scrolls without moving these actions. */
@Composable
private fun TutorialPageActions(
    page: TutorialPage, onNext: () -> Unit, onBack: () -> Unit, onDismiss: () -> Unit, onDocumentation: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (page == TutorialPage.IMPORTANT) {
            Box(Modifier.weight(1f).align(Alignment.Bottom).heightIn(min = 48.dp)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null,
                    role = Role.Button, onClick = onDocumentation), contentAlignment = Alignment.BottomStart) {
                Text(stringResource(R.string.tutorial_how_it_works), Modifier.fillMaxWidth().padding(start = 12.dp, bottom = 10.dp),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Start)
            }
        } else Spacer(Modifier.weight(1f))
        if (page == TutorialPage.INSTRUCTIONS) {
            MeasurementActionButton(label = nextDescription(TutorialPage.IMPORTANT), onClick = onNext) {
                Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null, modifier = Modifier.size(28.dp))
            }
        } else {
            MeasurementActionButton(label = stringResource(R.string.tutorial_go_back), onClick = onBack) {
                Icon(painterResource(R.drawable.ic_chevron_left), contentDescription = null, modifier = Modifier.size(28.dp))
            }
            MeasurementActionButton(label = stringResource(R.string.close), onClick = onDismiss) {
                Icon(painterResource(R.drawable.ic_close), contentDescription = null, modifier = Modifier.size(28.dp))
            }
        }
    }
}

@Composable
private fun nextDescription(page: TutorialPage): String =
    stringResource(R.string.tutorial_next_section, stringResource(page.titleResource()))
