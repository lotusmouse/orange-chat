package me.rerere.rikkahub.data.ai.tools

import android.app.DownloadManager
import android.content.Context
import android.os.Environment
import androidx.core.net.toUri
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart

/**
 * download_file 工具: 通过 Android 系统的 DownloadManager 把文件下载到公共 Downloads 目录.
 *
 * 设计:
 *  - 调用后立即返回 download_id, 实际下载在系统后台进行 (由 DownloadManager 调度).
 *  - 无需运行时权限: DownloadManager 自己管理下载通知. Android 13+ 上下载通知需要
 *    POST_NOTIFICATIONS 权限 (在设置页开关卡片里提示用户授权), 没权限下载仍能进行,
 *    只是看不到进度通知.
 *  - 文件落到 Environment.DIRECTORY_DOWNLOADS (公共 Downloads 目录), 用户可在系统文件
 *    管理器里看到.
 *
 * 移植自 rikkahub-agent 的 DownloadTool.kt (位于 data/ai/tools/local 包), 适配 orangechat
 * 的 SystemTools 目录结构 —— 这里和其它系统工具放在一起 (非 local 子包).
 */
fun downloadTool(context: Context): Tool = Tool(
    name = "download_file",
    description = """
        Queue a file download via Android's DownloadManager. Files land in the public Downloads
        directory. Returns immediately with a download_id; the actual download proceeds in the
        background.
    """.trimIndent().replace("\n", " "),
    parameters = {
        InputSchema.Obj(
            properties = buildJsonObject {
                put("url", buildJsonObject {
                    put("type", "string")
                    put("description", "The URL of the file to download")
                })
                put("filename", buildJsonObject {
                    put("type", "string")
                    put("description", "Optional filename to save as (defaults to last URL path segment)")
                })
            },
            required = listOf("url")
        )
    },
    execute = {
        val params = it.jsonObject
        val url = params["url"]?.jsonPrimitive?.contentOrNull
            ?: error("url is required")
        val filenameParam = params["filename"]?.jsonPrimitive?.contentOrNull?.takeIf { s -> s.isNotEmpty() }

        try {
            val uri = url.toUri()
            val rawName = filenameParam
                ?: uri.lastPathSegment
                ?: "download_${System.currentTimeMillis()}"
            val name = rawName.substringAfterLast('/').trimStart('.').ifEmpty {
                "download_${System.currentTimeMillis()}"
            }
            val request = DownloadManager.Request(uri)
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(true)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name)
                .setTitle(name)
            val dm = context.getSystemService(DownloadManager::class.java)
                ?: return@Tool listOf(
                    UIMessagePart.Text(
                        buildJsonObject { put("error", "DownloadManager unavailable") }.toString()
                    )
                )
            val id = dm.enqueue(request)
            val payload = buildJsonObject {
                put("success", true)
                put("download_id", id)
                put("filename", name)
            }
            listOf(UIMessagePart.Text(payload.toString()))
        } catch (e: IllegalArgumentException) {
            listOf(
                UIMessagePart.Text(
                    buildJsonObject { put("error", e.message ?: "invalid argument") }.toString()
                )
            )
        } catch (e: SecurityException) {
            listOf(
                UIMessagePart.Text(
                    buildJsonObject { put("error", e.message ?: "security error") }.toString()
                )
            )
        }
    }
)
