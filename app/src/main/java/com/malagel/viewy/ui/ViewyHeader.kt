package com.malagel.viewy.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.malagel.viewy.BuildConfig
import com.malagel.viewy.R

private val ViewyWordmarkFont = FontFamily(Font(R.font.nunito_bold, weight = FontWeight.Bold))

@Composable
internal fun ViewyHeader(compact: Boolean, overflow: Boolean, active: Boolean, onDebug: () -> Unit, onCloseWorkspace: () -> Unit) {
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
        if (BuildConfig.DEBUG) LabeledIconButton(R.string.show_diagnostics, "⋯", onDebug)
        if (overflow) LabeledIconButton(R.string.close, "×", onCloseWorkspace)
        val configurationsLabel = stringResource(R.string.configurations_placeholder)
        FilledTonalIconButton(
            onClick = {}, enabled = false, shape = CircleShape,
            modifier = Modifier.size(52.dp).semantics { contentDescription = configurationsLabel },
        ) {
            Icon(painterResource(R.drawable.ic_settings), contentDescription = null, modifier = Modifier.size(28.dp))
        }
    }
}

@Composable
internal fun InactiveMeasurementScreen(compact: Boolean, onDebug: () -> Unit, modeControl: @Composable () -> Unit) {
    Column(
        Modifier.widthIn(max = 600.dp).fillMaxSize().padding(
            start = if (compact) 12.dp else 20.dp, end = if (compact) 12.dp else 20.dp,
            top = 8.dp, bottom = if (compact) 16.dp else 32.dp,
        ), horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ViewyHeader(compact, false, false, onDebug, {})
        Spacer(Modifier.weight(1f))
        Row(Modifier.widthIn(max = 320.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.size(if (compact) 72.dp else 88.dp))
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { modeControl() }
        }
    }
}
