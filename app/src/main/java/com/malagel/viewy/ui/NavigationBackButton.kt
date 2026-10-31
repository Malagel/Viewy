package com.malagel.viewy.ui

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.malagel.viewy.R

/** Shared navigation affordance with a full touch target and localized destination label. */
@Composable
internal fun NavigationBackButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    label: String = stringResource(R.string.back),
    enabled: Boolean = true,
    dimWhenDisabled: Boolean = true,
) {
    val colors = IconButtonDefaults.filledTonalIconButtonColors()
    Box(modifier.size(48.dp), contentAlignment = Alignment.Center) {
        FilledTonalIconButton(onClick = onClick, enabled = enabled,
            modifier = Modifier.size(width = 44.dp, height = 32.dp), shape = RoundedCornerShape(12.dp),
            colors = if (dimWhenDisabled) colors else colors.copy(
                disabledContainerColor = colors.containerColor, disabledContentColor = colors.contentColor),
        ) {
            Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = label, modifier = Modifier.size(20.dp))
        }
    }
}
