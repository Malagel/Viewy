package com.malagel.viewy.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.malagel.viewy.R
import com.malagel.viewy.onboarding.*
import com.malagel.viewy.settings.SettingsUiState

@Composable
fun OnboardingScreen(
    state: OnboardingState,
    model: OnboardingViewModel,
    preferences: SettingsUiState,
    onComplete: () -> Unit,
    onRetry: () -> Unit,
    animateEntry: Boolean = false,
) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        model.result(OnboardingPermission.CAMERA, it)
    }
    DisposableEffect(owner, context, model) {
        fun refresh() = model.refresh(OnboardingPermission.CAMERA,
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
        refresh()
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) refresh() }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    val pages = updateTransition(state.page, label = "Onboarding page")
    val entrance = remember(model) { Animatable(if (animateEntry) 0f else 1f) }
    LaunchedEffect(entrance) { entrance.animateTo(1f, tween(300)) }
    val entering by remember(entrance) { derivedStateOf { entrance.value < 1f } }
    val moving = pages.currentState != pages.targetState || pages.isRunning
    val blocked = entering || moving || preferences.writing || state.requesting
    // At Welcome, ordinary Activity Back/exit remains available.
    BackHandler(enabled = state.page != OnboardingPage.WELCOME || blocked) { if (!blocked) model.back(state.page) }
    val offset = with(LocalDensity.current) { 32.dp.roundToPx() } *
        if (LocalLayoutDirection.current == LayoutDirection.Ltr) 1 else -1
    val entranceOffset = with(LocalDensity.current) { 12.dp.toPx() }
    Surface(Modifier.fillMaxSize()) {
        pages.AnimatedContent(
            modifier = Modifier.fillMaxSize().safeDrawingPadding().graphicsLayer {
                alpha = entrance.value
                translationY = (1f - entrance.value) * entranceOffset
            }.clipToBounds(),
            transitionSpec = {
                val direction = if (targetState.ordinal > initialState.ordinal) 1 else -1
                ((fadeIn(tween(220)) + slideInHorizontally(tween(220)) { direction * offset }) togetherWith
                    (fadeOut(tween(220)) + slideOutHorizontally(tween(220)) { -direction * offset })).using(null)
            },
        ) { page ->
            NavigationInputGuard(blocked || page != state.page, Modifier.fillMaxSize()) {
                OnboardingPageContent(page, state, preferences, onRetry,
                    onContinue = { if (!blocked && page == state.page) {
                        if (page == OnboardingPage.COMPLETION) model.finish(onComplete) else model.next(page)
                    } },
                    onPermission = { permission ->
                        if (!blocked && page == state.page && model.request(permission)) {
                            launcher.launch(when (permission) { OnboardingPermission.CAMERA -> Manifest.permission.CAMERA })
                        }
                    })
            }
        }
    }
}

@Composable
private fun OnboardingPageContent(
    page: OnboardingPage, state: OnboardingState, preferences: SettingsUiState,
    onRetry: () -> Unit, onContinue: () -> Unit, onPermission: (OnboardingPermission) -> Unit,
) {
    val title = stringResource(when (page) {
        OnboardingPage.WELCOME -> R.string.onboarding_welcome
        OnboardingPage.PERMISSIONS -> R.string.onboarding_permissions
        OnboardingPage.COMPLETION -> R.string.onboarding_complete
    })
    Column(Modifier.fillMaxSize().padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 28.dp).semantics { paneTitle = title },
        horizontalAlignment = Alignment.CenterHorizontally) {
        BoxWithConstraints(Modifier.weight(1f).widthIn(max = 480.dp).fillMaxWidth()) {
            Box(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).heightIn(min = maxHeight),
                contentAlignment = Alignment.Center) {
                Column(Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    if (page == OnboardingPage.WELCOME) BasicText(stringResource(R.string.app_name),
                        modifier = Modifier.fillMaxWidth(), maxLines = 1,
                        autoSize = TextAutoSize.StepBased(minFontSize = 24.sp, maxFontSize = 72.sp, stepSize = 2.sp),
                        style = MaterialTheme.typography.displayLarge.copy(fontFamily = ViewyWordmarkFont,
                            fontWeight = FontWeight.Bold, fontSize = 72.sp, lineHeight = 1.2.em,
                            color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center))
                    Text(title, Modifier.semantics { heading() }, style = if (page == OnboardingPage.WELCOME)
                        MaterialTheme.typography.headlineMedium else MaterialTheme.typography.displaySmall.copy(
                            fontFamily = ViewyWordmarkFont, fontWeight = FontWeight.Bold, fontSize = 40.sp, lineHeight = 48.sp),
                        textAlign = TextAlign.Center)
                    when (page) {
                        OnboardingPage.WELCOME -> {
                            Text(stringResource(R.string.onboarding_description), style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                            Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
                                Text(stringResource(R.string.onboarding_accuracy_disclaimer), Modifier.padding(20.dp),
                                    style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
                            }
                        }
                        OnboardingPage.PERMISSIONS -> OnboardingPermission.entries.forEach { permission ->
                            PermissionRow(permission, state.permissions.getValue(permission)) { onPermission(permission) }
                        }
                        OnboardingPage.COMPLETION -> Unit
                    }
                }
            }
        }
        Column(Modifier.widthIn(max = 480.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (preferences.storageError) {
                Text(stringResource(R.string.settings_storage_error), textAlign = TextAlign.Center)
                TextButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
            }
            val completed = preferences.preferences?.onboardingCompleted == true
            val completing = page == OnboardingPage.COMPLETION && (preferences.writing || completed)
            val buttonColors = ButtonDefaults.buttonColors()
            // Saving blocks input immediately, but must not gray out the outgoing button.
            Button(onClick = onContinue,
                enabled = state.canContinue && (page != OnboardingPage.COMPLETION || (preferences.canEdit && !completed)),
                colors = if (completing) ButtonDefaults.buttonColors(
                    disabledContainerColor = buttonColors.containerColor,
                    disabledContentColor = buttonColors.contentColor,
                ) else buttonColors,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                Text(stringResource(when {
                    page == OnboardingPage.COMPLETION -> R.string.onboarding_enter
                    page == OnboardingPage.PERMISSIONS && !state.allPermissionsGranted -> R.string.onboarding_not_now
                    else -> R.string.onboarding_continue
                }),
                    textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun PermissionRow(permission: OnboardingPermission, status: PermissionStatus, onRequest: () -> Unit) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(when (permission) { OnboardingPermission.CAMERA -> R.string.onboarding_camera }),
                    Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                val granted = status == PermissionStatus.GRANTED
                // Both states stay measured, including wrapped labels at large font scales.
                Box(Modifier.widthIn(max = 128.dp), contentAlignment = Alignment.Center) {
                    PermissionStateContent(!granted) {
                        TextButton(onClick = onRequest, enabled = !granted && status != PermissionStatus.REQUESTING,
                            colors = ButtonDefaults.textButtonColors(disabledContentColor = MaterialTheme.colorScheme.primary)) {
                            Text(stringResource(R.string.camera_permission_allow), style = MaterialTheme.typography.labelLarge,
                                textAlign = TextAlign.Center)
                        }
                    }
                    PermissionStateContent(granted) {
                        Text(stringResource(R.string.onboarding_permission_granted), Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge,
                            textAlign = TextAlign.Center)
                    }
                }
            }
            Box {
                PermissionStateContent(status != PermissionStatus.DENIED) {
                    Text(stringResource(R.string.camera_permission_explanation), style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                PermissionStateContent(status == PermissionStatus.DENIED) {
                    Text(stringResource(R.string.onboarding_permission_denied), style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** Crossfade measured content without changing its parent's bounds or exposing hidden controls. */
@Composable
private fun PermissionStateContent(visible: Boolean, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val opacity by animateFloatAsState(if (visible) 1f else 0f, tween(180), label = "Permission state")
    Box(modifier.graphicsLayer { alpha = opacity }.then(if (visible) Modifier else Modifier.clearAndSetSemantics {})) {
        content()
    }
}
