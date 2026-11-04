package com.malagel.viewy.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.malagel.viewy.R
import com.malagel.viewy.domain.MeasurementMethod
import com.malagel.viewy.domain.MeasurementMode
import com.malagel.viewy.measurement.SelectionDestination

@Composable
internal fun HomeScreen(
    destination: SelectionDestination, onSettings: () -> Unit, onBack: () -> Unit,
    onMode: (MeasurementMode) -> Unit, onSelect: (MeasurementMethod) -> Unit, interactive: Boolean,
) {
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        val compact = rememberMainLayout(maxWidth, maxHeight) != MainLayout.STANDARD
        Column(Modifier.widthIn(max = 600.dp).fillMaxSize().padding(horizontal = if (compact) 12.dp else 20.dp, vertical = 8.dp)) {
            ViewyHeader(compact, false, false, {}, {}, onSettings)
            Box(Modifier.weight(1f).fillMaxWidth().padding(bottom = 16.dp), contentAlignment = Alignment.Center) {
                Surface(
                    Modifier.widthIn(max = 480.dp).fillMaxWidth().heightIn(max = 520.dp).fillMaxHeight(),
                    shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainer,
                ) {
                    SelectionLists(destination, interactive, Modifier.fillMaxSize().padding(vertical = 12.dp), onBack, onMode, onSelect)
                }
            }
        }
    }
}

@Composable
internal fun HomeControl(onHome: () -> Unit) {
    MeasurementActionButton(label = stringResource(R.string.home), onClick = onHome) {
        Icon(painterResource(R.drawable.ic_home), contentDescription = null, modifier = Modifier.size(24.dp))
    }
}
