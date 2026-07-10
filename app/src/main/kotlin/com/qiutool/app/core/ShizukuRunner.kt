package com.qiutool.app.core

import android.content.pm.PackageManager
import android.os.Build
import kotlinx.coroutines.suspendCancellableCoroutine
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import kotlin.coroutines.resume

object ShizukuRunner {
    private const val REQ_CODE = 9527

    fun isBinderAlive(): Boolean = try {
        Shizuku.pingBinder()
    } catch (e: Throwable) {
        AppLogger.w("QiuTool", "shizuku ping failed: ${e.message}")
        false
    }

    fun isPermissionGranted(): Boolean {
        return try {
            if (!Shizuku.pingBinder()) return false
            if (Shizuku.isPreV11()) return false
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (e: Throwable) {
            AppLogger.w("QiuTool", "shizuku checkPermission failed: ${e.message}")
            false
        }
    }

    /**
     * 异步申请 Shizuku 权限。返回授权结果。如果 binder 不在或不支持会立刻返回 false。
     */
    suspend fun requestPermission(): Boolean {
        if (!isBinderAlive()) return false
        if (Shizuku.isPreV11()) return false
        if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) return true
        if (Shizuku.shouldShowRequestPermissionRationale()) {
            // 用户之前选过「拒绝并不再询问」
            return false
        }
        return suspendCancellableCoroutine { cont ->
            val listener = object : Shizuku.OnRequestPermissionResultListener {
                override fun onRequestPermissionResult(requestCode: Int, grantResult: Int) {
                    if (requestCode != REQ_CODE) return
                    Shizuku.removeRequestPermissionResultListener(this)
                    if (cont.isActive) cont.resume(grantResult == PackageManager.PERMISSION_GRANTED)
                }
            }
            Shizuku.addRequestPermissionResultListener(listener)
            cont.invokeOnCancellation {
                runCatching { Shizuku.removeRequestPermissionResultListener(listener) }
            }
            try {
                Shizuku.requestPermission(REQ_CODE)
            } catch (e: Throwable) {
                Shizuku.removeRequestPermissionResultListener(listener)
                if (cont.isActive) cont.resume(false)
            }
        }
    }

    /** 通过 Shizuku 高权限进程执行 sh 命令。Shizuku.newProcess 在 13.x 是 @hide，反射调用。 */
    fun exec(cmd: String): Int {
        return try {
            val method = Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            )
            method.isAccessible = true
            val process = method.invoke(null, arrayOf("sh", "-c", cmd), null, null) as Process
            val exit = process.waitFor()
            if (exit != 0) {
                val err = BufferedReader(InputStreamReader(process.errorStream)).readText()
                AppLogger.w("QiuTool", "shizuku exec exit=$exit cmd=$cmd err=$err")
            }
            exit
        } catch (e: Throwable) {
            AppLogger.w("QiuTool", "shizuku exec failed: ${e.message}")
            -1
        }
    }

    fun copy(src: File, dest: File): Boolean {
        val parent = dest.parentFile?.absolutePath ?: return false
        val pq = PermissionMethod.shQuote(parent)
        val sq = PermissionMethod.shQuote(src.absolutePath)
        val dq = PermissionMethod.shQuote(dest.absolutePath)
        val cmd = "mkdir -p $pq && cp -f $sq $dq && chmod 644 $dq"
        return exec(cmd) == 0
    }
}
