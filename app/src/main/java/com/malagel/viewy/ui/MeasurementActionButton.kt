package com.malagel.viewy.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** Matching circular actions beside Capture, with a comfortably sized touch target. */
@Composable
internal fun MeasurementActionButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    FilledTonalButton(
        onClick = onClick,
        shape = CircleShape,
        contentPadding = PaddingValues(0.dp),
        modifier = modifier.size(56.dp).semantics { contentDescription = label },
    ) { content() }
}
