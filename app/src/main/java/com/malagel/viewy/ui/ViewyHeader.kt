package com.malagel.viewy.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.malagel.viewy.R

private val ViewyWordmarkFont = FontFamily(Font(R.font.nunito_bold, weight = FontWeight.Bold))

@Composable
internal fun ViewyHeader(compact: Boolean, overflow: Boolean, active: Boolean, onHelp: () -> Unit, onCloseWorkspace: () -> Unit, onSettings: () -> Unit, diagnosticsEnabled: Boolean = false, onDiagnostics: () -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().padding(top = if (compact) 4.dp else 12.dp, bottom = if (compact) 12.dp else 28.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineLarge.copy(
                fontFamily = ViewyWordmarkFont, fontWeight = FontWeight.Bold,
                fontSize = if (compact) 30.sp else 38.sp,
                lineHeight = if (compact) 38.sp else 46.sp,
            ))
            if (active && !compact) Text(stringResource(R.string.measurement_method_subtitle), style = MaterialTheme.typography.bodySmall)
        }
        if (overflow) LabeledIconButton(R.string.close, "×", onCloseWorkspace)
        if (active) {
            val helpLabel = stringResource(R.string.how_to_use)
            val diagnosticsLabel = stringResource(R.string.diag_open)
            val haptic = LocalHapticFeedback.current
            val helpInteractions = remember { MutableInteractionSource() }
            // One gesture owner for the full 48 dp target. Consume disabled holds too,
            // so releasing a long press never falls through to the tutorial click.
            val longPress = {
                if (diagnosticsEnabled) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onDiagnostics()
                }
            }
            Box(Modifier.size(48.dp).clearAndSetSemantics {
                contentDescription = helpLabel
                role = Role.Button
                onClick(label = helpLabel) { onHelp(); true }
                if (diagnosticsEnabled) onLongClick(label = diagnosticsLabel) { longPress(); true }
            }.combinedClickable(
                interactionSource = helpInteractions, indication = null,
                role = Role.Button, onClickLabel = helpLabel, onClick = onHelp,
                onLongClickLabel = if (diagnosticsEnabled) diagnosticsLabel else null,
                hapticFeedbackEnabled = false, onLongClick = longPress,
            ), contentAlignment = Alignment.Center) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    // Keep the 48 dp gesture target, but draw all interaction feedback
                    // only inside the visible 40 dp circle.
                    modifier = Modifier.size(40.dp).clip(CircleShape).indication(helpInteractions, ripple())) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("?", fontSize = with(LocalDensity.current) { 20.dp.toSp() }, modifier = Modifier.clearAndSetSemantics {})
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
        }
        val settingsLabel = stringResource(R.string.settings)
        FilledTonalIconButton(
            onClick = onSettings, shape = CircleShape,
            // A slightly richer purple than the shared tonal actions, using theme roles.
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = lerp(MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.primaryContainer, 0.25f),
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ),
            modifier = Modifier.size(52.dp).semantics { contentDescription = settingsLabel },
        ) {
            Icon(painterResource(R.drawable.ic_settings), contentDescription = null, modifier = Modifier.size(28.dp))
        }
    }
}

@Composable
internal fun InactiveMeasurementScreen(compact: Boolean, onSettings: () -> Unit, modeControl: @Composable () -> Unit) {
    Column(
        Modifier.widthIn(max = 600.dp).fillMaxSize().padding(
            start = if (compact) 12.dp else 20.dp, end = if (compact) 12.dp else 20.dp,
            top = 8.dp, bottom = if (compact) 16.dp else 32.dp,
        ), horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ViewyHeader(compact, false, false, {}, {}, onSettings)
        Spacer(Modifier.weight(1f))
        Row(Modifier.widthIn(max = 320.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.size(if (compact) 72.dp else 88.dp))
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { modeControl() }
        }
    }
}
