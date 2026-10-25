package com.malagel.viewy.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.malagel.viewy.R
import com.malagel.viewy.ui.theme.ViewyTheme

@Composable
fun ViewyScreen(modifier: Modifier = Modifier) {
    Scaffold(modifier = modifier) { insets ->
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize().padding(insets),
            contentAlignment = Alignment.TopCenter,
        ) {
            // Scroll on short windows or large font scales; cap width on tablets.
            val previewHeight = (maxHeight - 280.dp).coerceIn(240.dp, 680.dp)
            Column(
                modifier = Modifier.widthIn(max = 600.dp).fillMaxWidth()
                    .verticalScroll(rememberScrollState()).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineLarge)
                    Text(
                        stringResource(R.string.foundation_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Surface(
                    modifier = Modifier.fillMaxWidth().height(previewHeight),
                    shape = RoundedCornerShape(32.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Reticle(Modifier.size(44.dp))
                        Text(
                            stringResource(R.string.preview_placeholder),
                            modifier = Modifier.align(Alignment.BottomCenter).padding(24.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = {}, enabled = false) {
                        Icon(painterResource(R.drawable.ic_settings), stringResource(R.string.settings))
                    }
                    Spacer(Modifier.weight(1f))
                    val measureDescription = stringResource(R.string.measure)
                    Button(
                        onClick = {},
                        enabled = false,
                        modifier = Modifier.size(88.dp).semantics { contentDescription = measureDescription },
                        shape = CircleShape,
                        contentPadding = PaddingValues(0.dp),
                        colors = ButtonDefaults.buttonColors(
                            disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                            disabledContentColor = MaterialTheme.colorScheme.primary,
                        ),
                    ) {
                        val color = MaterialTheme.colorScheme.primary
                        Canvas(Modifier.size(42.dp)) {
                            drawCircle(color, style = Stroke(width = 2.dp.toPx()))
                            drawCircle(color, radius = 12.dp.toPx())
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = {}, enabled = false) {
                        Icon(painterResource(R.drawable.ic_pin), stringResource(R.string.pinned_object))
                    }
                }
                Text(
                    stringResource(R.string.controls_hint),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun Reticle(modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
    Canvas(modifier) {
        val stroke = 1.5.dp.toPx()
        val gap = 6.dp.toPx()
        drawLine(color, Offset(center.x, 0f), Offset(center.x, center.y - gap), stroke, StrokeCap.Round)
        drawLine(color, Offset(center.x, center.y + gap), Offset(center.x, size.height), stroke, StrokeCap.Round)
        drawLine(color, Offset(0f, center.y), Offset(center.x - gap, center.y), stroke, StrokeCap.Round)
        drawLine(color, Offset(center.x + gap, center.y), Offset(size.width, center.y), stroke, StrokeCap.Round)
        drawCircle(color, radius = 2.dp.toPx())
    }
}

@Preview(name = "Light", showBackground = true)
@Composable
private fun LightPreview() {
    ViewyTheme(darkTheme = false) { ViewyScreen() }
}

@Preview(name = "Dark", showBackground = true)
@Composable
private fun DarkPreview() {
    ViewyTheme(darkTheme = true) { ViewyScreen() }
}
