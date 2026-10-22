package com.hoerire.tools.zafiro.chat

import com.hoerire.tools.zafiro.api.model.FileRef
import com.hoerire.tools.zafiro.api.text.FilesBlock
import com.hoerire.tools.zafiro.api.text.TurnTextComposer

internal class TurnPrefixComposer {
    private companion object {
        const val LOG_TAG = "zafiro_TurnPrefixComposer"
    }

    fun reset() {
    }

        /** 拼注入前缀：文件块 + 瞬态通知块，排成「块\n\n块」。 */
    fun buildInjectionPrefixes(
        files: List<FileRef>,
        notifications: List<String>,
    ): String? {
        val noticeBody = notifications
            .filter { it.isNotBlank() }
            .joinToString("\n\n")
        val notificationsBlock = noticeBody.takeIf { it.isNotEmpty() }
            ?.let { TurnTextComposer.wrapBlock("notifications", it) }
        val prefixes = listOfNotNull(FilesBlock.block(files), notificationsBlock)
        return prefixes.takeIf { it.isNotEmpty() }?.joinToString("\n\n")
    }

    }
