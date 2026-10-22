package com.hoerire.tools.zafiro.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import com.hoerire.tools.zafiro.app.ui.nav.PageTitleSpec
import com.hoerire.tools.zafiro.app.ui.nav.TopBarActionSpec

data class PageChromeContribution(
    val titleSpec: PageTitleSpec? = null,
    val titleKey: Any? = null,
    /**
     * 自定义标题槽：非空时顶掉 [titleSpec]，渲染任意 composable。
     *
     * 供页面活着时随时会变的活内容使用（配置切换器等）。实例必须由提供方
     * `remember` 住 —— 壳层以实例相等判断标题是否变化，每次重组新建实例会
     * 反复重放切页动画。
     */
    val titleContent: (@Composable () -> Unit)? = null,
    val leftAction: TopBarActionSpec? = null,
    val rightAction: TopBarActionSpec? = null,
    val menuItems: List<PageChromeMenuItem> = emptyList(),
    val barItems: List<PageChromeMenuItem> = emptyList(),
    val backHandler: PageBackHandler? = null,
    val overlay: (@Composable () -> Unit)? = null,
    val isTitleOccluded: Boolean = false,
    val actionsVisible: Boolean = true,
) {
    companion object {
        val Empty = PageChromeContribution()
    }
}

data class PageBackHandler(
    val shouldConsumeBack: () -> Boolean,
    val onConsumeBack: () -> Unit,
)

data class PageChromeMenuItem(
    val key: String,
    val title: String,
    val onClick: () -> Unit,
)

class PageChromeHost {
    private val contributions = mutableStateMapOf<String, PageChromeContribution>()

    fun stateFor(entryId: String): PageChromeContribution {
        return contributions[entryId] ?: PageChromeContribution.Empty
    }

    fun registrarFor(entryId: String): PageChromeRegistrar {
        return PageChromeRegistrar(entryId = entryId, host = this)
    }

    internal fun setContribution(entryId: String, contribution: PageChromeContribution) {
        contributions[entryId] = contribution
    }

    internal fun clearContribution(entryId: String) {
        contributions.remove(entryId)
    }

    internal fun syncWithStack(activeEntryIds: Set<String>) {
        contributions.keys.retainAll(activeEntryIds)
    }
}

class PageChromeRegistrar internal constructor(
    private val entryId: String,
    private val host: PageChromeHost,
) {
    fun setContribution(contribution: PageChromeContribution) {
        host.setContribution(entryId, contribution)
    }

    fun clearContribution() {
        host.clearContribution(entryId)
    }
}

val LocalPageChrome = staticCompositionLocalOf<PageChromeRegistrar> {
    error("No PageChromeRegistrar provided")
}

@Composable
fun rememberPageChromeHost(): PageChromeHost {
    return remember { PageChromeHost() }
}

@Composable
fun RegisterPageChrome(contribution: PageChromeContribution) {
    val registrar = LocalPageChrome.current

    DisposableEffect(registrar, contribution) {
        registrar.setContribution(contribution)
        onDispose {
            // 不在页面暂时离开组合树时清空：保留栈中条目的 PageChromeContribution，
            // 保证返回时首帧即可拿到正确标题与动作，由切页动画统一平滑滚动进场。
            // 实际已出栈条目由 PageChromeHost.syncWithStack 统一清理。
        }
    }
}

/**
 * 顶栏标题下方的平铺条目条（1.7.0 原味）：
 * 取代左右浮动按钮，条目直接以 pill 形式平铺在标题下。
 */
@Composable
fun PageChromeBarRow(items: List<PageChromeMenuItem>) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        // SpaceEvenly: 左边缘 / 条目间 / 右边缘 四处留白等分
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEach { item ->
            Text(
                text = item.title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.65f))
                    .clickable { item.onClick() }
                    .padding(horizontal = 14.dp, vertical = 2.dp),
            )
        }
    }
}
