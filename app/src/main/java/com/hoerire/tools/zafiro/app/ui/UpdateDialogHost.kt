package com.hoerire.tools.zafiro.app.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.hoerire.tools.zafiro.app.BuildConfig
import com.hoerire.tools.zafiro.app.R
import com.hoerire.tools.zafiro.repo.UpdateCheckHolder
import kotlinx.coroutines.launch

/**
 * 冷启动检测到新版本时弹出的对话框。
 *
 * 挂在 ZafiroApp 之外的一层，不影响主界面布局；AlertDialog 自身浮在最上层。
 */
@Composable
fun UpdateDialogHost() {
    val result by UpdateCheckHolder.result.collectAsState()
    val current = result ?: return
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val remote = current.remoteVersion.orEmpty()

    AlertDialog(
        onDismissRequest = { scope.launch { UpdateCheckHolder.dismiss() } },
        title = { Text(stringResource(R.string.update_dialog_title, remote)) },
        text = {
            Text(
                buildString {
                    append(stringResource(R.string.update_dialog_current, BuildConfig.VERSION_NAME))
                    current.releaseNotes?.let { notes ->
                        append("\n\n")
                        append(notes.trim().take(800))
                    }
                },
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val url = current.releaseUrl
                    if (!url.isNullOrBlank()) {
                        runCatching {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        }
                    }
                    scope.launch { UpdateCheckHolder.dismiss() }
                },
            ) { Text(stringResource(R.string.update_dialog_open)) }
        },
        dismissButton = {
            TextButton(onClick = { scope.launch { UpdateCheckHolder.dismiss() } }) {
                Text(stringResource(R.string.update_dialog_later))
            }
        },
    )
}
