package com.malagel.viewy.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.malagel.viewy.R
import com.malagel.viewy.domain.MeasurementMethod

/** Background fills the window; inset padding protects the fixed header and scrollable body. */
@Composable
internal fun MethodDocumentationScreen(method: MeasurementMethod, onBack: () -> Unit) {
    val content = remember(method) { methodDocumentation(method) } ?: return
    val title = stringResource(R.string.tutorial_how_it_works)
    Surface(Modifier.fillMaxSize().semantics { paneTitle = title }
        .pointerInput(Unit) { detectTapGestures { /* Consume empty surface taps. */ } }) {
        AdaptivePageColumn(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.displayCutout)),
            header = { ScreenHeader(onBack = onBack, title = title) },
        ) { scrollModifier ->
            // Short documents need no lazy virtualization. Scroll state is saveable across recreation.
            Column(scrollModifier.fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Column(Modifier.widthIn(max = 720.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    content.blocks.forEach { block -> DocumentationContentBlock(block) }
                }
            }
        }
    }
}

@Composable
private fun DocumentationContentBlock(block: DocumentationBlock) {
    when (block) {
        is DocumentationBlock.Heading -> Text(stringResource(block.text), Modifier.semantics { heading() },
            style = if (block.subheading) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge)
        is DocumentationBlock.Paragraph -> Text(stringResource(block.text), style = MaterialTheme.typography.bodyLarge)
        is DocumentationBlock.BulletList -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            block.items.forEach { item ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.semantics(mergeDescendants = true) {}) {
                    Text("•", Modifier.clearAndSetSemantics {}, style = MaterialTheme.typography.bodyLarge)
                    Text(stringResource(item), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
        is DocumentationBlock.Image -> {
            val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
            // painterResource remembers decoded PNGs / VectorDrawable painters by resource and theme.
            val painter = painterResource(if (dark) block.darkDrawable else block.drawable)
            val intrinsic = painter.intrinsicSize
            val ratio = intrinsic.width / intrinsic.height
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Image(painter, contentDescription = stringResource(block.description),
                    modifier = Modifier.fillMaxWidth().then(
                        if (ratio.isFinite() && ratio > 0f) Modifier.aspectRatio(ratio) else Modifier),
                    contentScale = ContentScale.Fit,
                    colorFilter = if (block.monochrome) ColorFilter.tint(MaterialTheme.colorScheme.onSurface) else null)
                block.caption?.let { Text(stringResource(it), Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodyMedium) }
            }
        }
    }
}
