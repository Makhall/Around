package com.around.app.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.toColorInt
import com.around.app.data.model.Avatar
import com.around.app.data.model.AvatarType

@Composable
fun AvatarBadge(
    avatar: Avatar,
    displayName: String,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
) {
    val parsedColor = remember(avatar.value) {
        runCatching { Color(avatar.value.toColorInt()) }.getOrNull()
    }
    val background = if (avatar.type == AvatarType.COLOR) {
        parsedColor ?: MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val initial = displayName.trim().firstOrNull()?.uppercase() ?: "?"

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(background),
        contentAlignment = Alignment.Center,
    ) {
        if (avatar.type == AvatarType.EMOJI) {
            Text(
                text = avatar.value,
                fontSize = (size.value * 0.48f).sp,
            )
        } else {
            Text(
                text = initial,
                color = if (background.luminance() > 0.5f) Color(0xFF1A1A1A) else Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = (size.value * 0.4f).sp,
            )
        }
    }
}
