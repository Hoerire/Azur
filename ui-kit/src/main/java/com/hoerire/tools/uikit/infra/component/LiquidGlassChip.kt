package com.hoerire.tools.uikit.infra.component

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.hoerire.tools.uikit.infra.shape.G2FieldShape

/**
 * 与输入框（LiquidTextFieldContainer）同款液态玻璃容器：
 * 同一 backdrop 透镜效果（vibrancy + blur + lens）+ 主色相着色。
 * 只负责外观，内边距与内容由调用方给。
 */
@Composable
fun LiquidGlassChip(
    modifier: Modifier = Modifier,
    warn: Boolean = false,
    shapeRadius: Dp = 36.dp,
    content: @Composable () -> Unit,
) {
    val backdrop = rememberLayerBackdrop()
    val shape = G2FieldShape(shapeRadius)
    val colorScheme = MaterialTheme.colorScheme
    val surfaceColor = if (warn) {
        colorScheme.errorContainer.copy(alpha = 0.64f)
    } else {
        colorScheme.surfaceContainerHigh.copy(alpha = 0.64f)
    }
    val tintColor = if (warn) {
        colorScheme.error.copy(alpha = 0.32f)
    } else {
        colorScheme.primaryContainer.copy(alpha = 0.32f)
    }
    Box(
        modifier = modifier
            .clip(shape)
            .drawBackdrop(
                backdrop = backdrop,
                shape = { shape },
                effects = {
                    vibrancy()
                    blur(2.dp.toPx())
                    lens(12.dp.toPx(), 24.dp.toPx())
                },
                onDrawSurface = {
                    drawRect(tintColor, blendMode = BlendMode.Hue)
                    drawRect(surfaceColor)
                },
            ),
    ) {
        content()
    }
}
