package me.rerere.rikkahub.plugin.loader

import android.content.Context
import java.io.File

/**
 * 运行时环境提取器
 * 负责检测本地运行时环境（如 Python）是否已解压就绪，
 * 并提供执行命令所需的环境变量前缀（PATH 等）。
 */
object RuntimeExtractor {

    private const val TAG = "RuntimeExtractor"

    /** 运行时目录 */
    private const val RUNTIME_DIR = "runtime"

    /**
     * 获取运行时环境变量前缀
     *
     * 如果运行时目录已就绪（包含 bin/ 子目录），返回形如
     * "export PATH=/data/data/com.orangechat/files/runtime/bin:$PATH\n" 的前缀字符串，
     * 可拼接到命令前执行。
     *
     * 如果运行时未就绪，返回 null。
     */
    fun getEnvPrefixIfAvailable(context: Context): String? {
        val runtimeDir = File(context.filesDir, RUNTIME_DIR)
        val binDir = File(runtimeDir, "bin")
        if (binDir.isDirectory) {
            val binPath = binDir.absolutePath
            return "export PATH=$binPath:\$PATH\n"
        }
        return null
    }
}