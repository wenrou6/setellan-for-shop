package com.qiutool.app.core

import android.content.Context
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AppLogger {
    private const val MAX_LOG_BYTES = 512 * 1024
    private const val KEEP_LOG_BYTES = 256 * 1024

    private val lock = Any()
    private val timeFormat = ThreadLocal.withInitial {
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    }

    @Volatile
    private var logFile: File? = null

    fun init(context: Context) {
        synchronized(lock) {
            val dir = File(context.cacheDir, "qiutool/logs").apply { mkdirs() }
            logFile = File(dir, "qiutool.log")
        }
        i("QiuTool", "logger initialized")
    }

    fun d(tag: String, message: String) {
        Log.d(tag, message)
        append("D", tag, message, null)
    }

    fun i(tag: String, message: String) {
        Log.i(tag, message)
        append("I", tag, message, null)
    }

    fun w(tag: String, message: String, throwable: Throwable? = null) {
        if (throwable == null) {
            Log.w(tag, message)
        } else {
            Log.w(tag, message, throwable)
        }
        append("W", tag, message, throwable)
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        if (throwable == null) {
            Log.e(tag, message)
        } else {
            Log.e(tag, message, throwable)
        }
        append("E", tag, message, throwable)
    }

    fun readRecentLines(maxLines: Int = 1500): List<String> {
        val file = logFile ?: return emptyList()
        return synchronized(lock) {
            runCatching {
                if (!file.isFile) return@synchronized emptyList()
                file.readLines(Charsets.UTF_8).takeLast(maxLines)
            }.getOrElse { emptyList() }
        }
    }

    private fun append(level: String, tag: String, message: String, throwable: Throwable?) {
        val file = logFile ?: return
        synchronized(lock) {
            runCatching {
                file.parentFile?.mkdirs()
                trimIfNeeded(file)
                val now = checkNotNull(timeFormat.get()).format(Date())
                file.appendText("$now $level/$tag $message\n", Charsets.UTF_8)
                if (throwable != null) {
                    file.appendText(stackTraceOf(throwable), Charsets.UTF_8)
                    file.appendText("\n", Charsets.UTF_8)
                }
            }
        }
    }

    private fun trimIfNeeded(file: File) {
        if (!file.isFile || file.length() <= MAX_LOG_BYTES) return
        val bytes = file.readBytes()
        val keep = bytes.takeLast(KEEP_LOG_BYTES).toByteArray()
        file.writeBytes("... log truncated ...\n".toByteArray(Charsets.UTF_8) + keep)
    }

    private fun stackTraceOf(throwable: Throwable): String {
        val writer = StringWriter()
        throwable.printStackTrace(PrintWriter(writer))
        return writer.toString()
    }
}
