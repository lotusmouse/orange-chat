package me.rerere.rikkahub.utils

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.util.Log

/**
 * 运行时初始化 ContentProvider
 * 
 * 在应用启动时自动提取 Python/Node.js 运行时。
 * 通过 AndroidManifest.xml 注册，无需修改其他代码。
 */
class RuntimeInitProvider : ContentProvider() {

    companion object {
        private const val TAG = "RuntimeInitProvider"
    }

    override fun onCreate(): Boolean {
        val context = context ?: return false
        Log.d(TAG, "RuntimeInitProvider.onCreate() - checking runtime extraction")
        try {
            RuntimeExtractor.extractIfNeeded(context)
            Log.d(TAG, "Runtime extraction check completed. Available: ${RuntimeExtractor.isRuntimeExtracted(context)}")
        } catch (e: Exception) {
            Log.e(TAG, "Runtime extraction failed", e)
        }
        return true
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int = 0
}