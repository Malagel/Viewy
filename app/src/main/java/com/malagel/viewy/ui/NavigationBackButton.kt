package com.malagel.viewy.ui

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.Role
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
    val interactions = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(12.dp)
    Box(modifier.size(48.dp).clickable(enabled = enabled, role = Role.Button,
        interactionSource = interactions, indication = null, onClick = onClick), contentAlignment = Alignment.Center) {
        Surface(
            modifier = Modifier.size(width = 44.dp, height = 32.dp).clip(shape).indication(interactions, ripple()), shape = shape,
            color = if (enabled || !dimWhenDisabled) colors.containerColor else colors.disabledContainerColor,
            contentColor = if (enabled || !dimWhenDisabled) colors.contentColor else colors.disabledContentColor,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = label, modifier = Modifier.size(20.dp))
            }
        }
    }
}
