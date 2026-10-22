package com.hoerire.tools.zafiro.app.ui.content

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import com.hoerire.tools.uikit.infra.component.LiquidSlider
import com.hoerire.tools.uikit.infra.liquidScreenActionButtonBottomPadding
import com.hoerire.tools.uikit.infra.liquidScreenBottomPadding
import com.hoerire.tools.uikit.infra.liquidScreenTopPadding
import com.hoerire.tools.uikit.infra.nav.pageViewModel
import com.hoerire.tools.uikit.infra.shape.G2CardShape
import com.hoerire.tools.zafiro.app.R
import com.hoerire.tools.zafiro.app.ui.model.MarkdownScaleIntent
import com.hoerire.tools.zafiro.app.ui.model.MarkdownScaleViewModel
import com.hoerire.tools.zafiro.app.ui.model.MaxScaleRatio
import com.hoerire.tools.zafiro.app.ui.model.MinScaleRatio

@Composable
fun MarkdownScalePageContent(
    modifier: Modifier = Modifier,
    viewModel: MarkdownScaleViewModel = pageViewModel(),
) {
    val uiState by viewModel.uiStateFlow.collectAsState()
    val scrollState = rememberScrollState()

    LaunchedEffect(Unit) {
        viewModel.sendIntent(MarkdownScaleIntent.Load)
    }

    Box(modifier = modifier.fillMaxSize()) {
        // 上半部：Markdown 预览流（消费经过 VM 节流后的 previewScale）
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(
                    top = liquidScreenTopPadding(16.dp),
                    bottom = liquidScreenBottomPadding(168.dp),
                )
                .padding(horizontal = 16.dp),
        ) {
            CompositionLocalProvider(LocalMarkdownScale provides uiState.previewScale) {
                AssistantOutputText(
                    text = AssistantMarkdownPreviewText.trim(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        // 下半部：吸底控制器卡片
        val cardShape = G2CardShape(28.dp)
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = liquidScreenActionButtonBottomPadding(8.dp))
                .clip(cardShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh, cardShape)
                .pointerInput(Unit) { detectTapGestures { } }
                .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            // 顶部标题与当前百分比（直接显示 uiState.scalePercent）
            val displayedPercent = uiState.scalePercent
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.ui_settings_markdown_scale_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "$displayedPercent%",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 滑块区域（左侧小 A，右侧大 A，中间 LiquidSlider）
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "A",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 20.dp),
                )

                LiquidSlider(
                    value = { uiState.scale },
                    onValueChange = { newScale ->
                        viewModel.sendIntent(MarkdownScaleIntent.ChangeScale(newScale))
                    },
                    onValueChangeFinished = {
                        viewModel.sendIntent(MarkdownScaleIntent.CommitScale())
                    },
                    valueRange = MinScaleRatio..MaxScaleRatio,
                    modifier = Modifier.weight(1f),
                )

                Text(
                    text = "A",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(start = 20.dp),
                )
            }
        }
    }
}

