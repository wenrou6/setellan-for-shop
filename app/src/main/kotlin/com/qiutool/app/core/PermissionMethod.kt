package com.qiutool.app.core

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

/**
 * 写入需要权限的目录（如 /sdcard/Android/data/<其他包>）的统一路径。
 *
 * 四种方式：
 *  - all_files: MANAGE_EXTERNAL_STORAGE，Android 11+ 仍受限于 /Android/data 但模拟器/root 设备可写
 *  - root: 通过 `su sh` 拷贝
 *  - shizuku: 通过 Shizuku.newProcess 拷贝（绕过 su，但同样系统权限）
 *  - none: 直接 File 拷贝
 */
object PermissionMethod {
    const val NONE = "none"
    const val ALL_FILES = "all_files"
    const val SHIZUKU = "shizuku"
    const val ROOT = "root"

    private const val SHIZUKU_PKG = "moe.shizuku.privileged.api"

    fun isAllFilesGranted(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager()

    fun openAllFilesSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                intent.data = Uri.parse("package:${context.packageName}")
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(intent)
            } catch (e: Exception) {
                val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(intent)
            }
        }
    }

    fun isShizukuInstalled(context: Context): Boolean = try {
        context.packageManager.getPackageInfo(SHIZUKU_PKG, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    /** 探测 root：3 秒超时，跑 `su -c id` */
    fun isRootAvailable(): Boolean {
        var proc: Process? = null
        return try {
            proc = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
            val watchdog = Thread {
                try {
                    Thread.sleep(3000)
                    proc?.destroy()
                } catch (_: InterruptedException) {
                }
            }.apply {
                isDaemon = true
                start()
            }
            val exit = proc.waitFor()
            watchdog.interrupt()
            if (exit != 0) {
                AppLogger.w("QiuTool", "root probe timeout — killing su")
                false
            } else {
                true
            }
        } catch (e: Exception) {
            AppLogger.w("QiuTool", "root probe failed: ${e.message}")
            proc?.destroy()
            false
        }
    }

    /** 用于 sh -c 内单引号字符串：把 ' 换成 '\'' */
    internal fun shQuote(s: String): String = "'" + s.replace("'", "'\\''") + "'"

    /** 把 [src] 复制到 [dest]。按 [method] 选拷贝通道。 */
    fun copyTo(src: File, dest: File, method: String): Boolean {
        return when (method) {
            ROOT -> copyWithRoot(src, dest)
            SHIZUKU -> ShizukuRunner.copy(src, dest)
            else -> copyDirect(src, dest)
        }
    }

    private fun copyDirect(src: File, dest: File): Boolean = try {
        dest.parentFile?.mkdirs()
        src.copyTo(dest, overwrite = true)
        true
    } catch (e: Exception) {
        AppLogger.w("QiuTool", "direct copy failed: ${e.message}")
        false
    }

    private fun copyWithRoot(src: File, dest: File): Boolean {
        val parent = dest.parentFile?.absolutePath ?: return false
        val cmd = "mkdir -p ${shQuote(parent)} && cp -f ${shQuote(src.absolutePath)} ${shQuote(dest.absolutePath)} && chmod 644 ${shQuote(dest.absolutePath)}"
        return try {
            val proc = Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
            val exit = proc.waitFor()
            if (exit != 0) {
                val err = BufferedReader(InputStreamReader(proc.errorStream)).readText()
                AppLogger.w("QiuTool", "root cp exit=$exit err=$err")
            }
            exit == 0
        } catch (e: Exception) {
            AppLogger.w("QiuTool", "root cp failed: ${e.message}")
            false
        }
    }
}
