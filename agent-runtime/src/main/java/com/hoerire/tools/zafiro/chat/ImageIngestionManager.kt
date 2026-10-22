package com.hoerire.tools.zafiro.chat

import android.net.Uri
import com.hoerire.tools.okia.ImageSaver
import com.hoerire.tools.zafiro.util.AppContext
import com.hoerire.tools.zafiro.chat.agentic.AndroidImageLoader
import com.hoerire.tools.zafiro.chat.agentic.IngestedImage
import com.hoerire.tools.zafiro.chat.agentic.image.ImageCodec
import com.hoerire.tools.zafiro.chat.agentic.image.IngestResult
import com.hoerire.tools.zafiro.util.ToolOutputTruncator
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File

internal class ImageIngestionManager {

    val imageLoader: AndroidImageLoader? = try {
        AndroidImageLoader()
    } catch (e: Exception) {
        null
    }

    private var imageCodec: ImageCodec? = null

    private suspend fun ensureImageCodec(): ImageCodec? {
        imageCodec?.let { return it }
        return withTimeoutOrNull(2_000) { AppContext.await().applicationContext }?.let {
            ImageCodec(it).also { codec -> imageCodec = codec }
        }
    }

    /**
     * 私有存储路径集合，注入 PromptComposer 环境块。
     * Context 不可用时返回空集合（环境块不渲染）。
     */
    suspend fun sandboxPaths(): Set<String> {
        val context = try {
            withTimeoutOrNull(2_000) { AppContext.await().applicationContext }
        } catch (e: Exception) {
            null
        } ?: return emptySet()
        return setOf(
            File(context.filesDir, "image_cache").absolutePath,
            File(context.filesDir, "downloads").absolutePath,
            File(context.filesDir, ToolOutputTruncator.EXPORT_DIR_NAME).absolutePath,
        )
    }

        suspend fun ensureImageSaver(): ImageSaver? {
        val codec = ensureImageCodec() ?: return null
        return ImageSaver { base64 ->
            when (val result = codec.ingestBase64(base64)) {
                is IngestResult.Ok -> result.image.path
                is IngestResult.Err -> null
            }
        }
    }

    /** 相册 URI → ingest 落盘 → path。失败返回 null（UI 静默丢弃）。 */
    suspend fun ingestUserImage(uriString: String): IngestedImage? {
        val codec = ensureImageCodec() ?: return null
        val result = codec.ingestUri(Uri.parse(uriString))
        return when (result) {
            is IngestResult.Ok -> IngestedImage(result.image.path)
            is IngestResult.Err -> null
        }
    }
}
