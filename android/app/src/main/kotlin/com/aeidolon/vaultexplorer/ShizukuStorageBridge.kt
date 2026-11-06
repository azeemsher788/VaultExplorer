package com.aeidolon.vaultexplorer

import android.content.pm.PackageManager
import dev.rikka.shizuku.Shizuku
import java.io.IOException

/**
 * Uses Shizuku only to grant this app Android's MANAGE_EXTERNAL_STORAGE app-op.
 * The normal app filesystem implementation then handles browsing and editing;
 * no file contents are copied through a shell process.
 */
class ShizukuStorageBridge(private val packageName: String) {
    companion object {
        private const val REQUEST_CODE = 4107
    }

    private var pending: ((Boolean) -> Unit)? = null
    private var listenerRegistered = false

    fun enable(callback: (Boolean) -> Unit) {
        if (!Shizuku.pingBinder()) {
            callback(false)
            return
        }
        if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
            grantAppOp(callback)
            return
        }
        if (pending != null) {
            callback(false)
            return
        }
        pending = callback
        if (!listenerRegistered) {
            Shizuku.addRequestPermissionResultListener { requestCode, grantResult ->
                if (requestCode != REQUEST_CODE) return@addRequestPermissionResultListener
                val next = pending
                pending = null
                if (grantResult == PackageManager.PERMISSION_GRANTED) {
                    grantAppOp(next)
                } else {
                    next?.invoke(false)
                }
            }
            listenerRegistered = true
        }
        Shizuku.requestPermission(REQUEST_CODE)
    }

    private fun grantAppOp(callback: ((Boolean) -> Unit)?) {
        Thread {
            val process = try {
                Shizuku.newProcess(
                    arrayOf(
                        "sh",
                        "-c",
                        "appops set $packageName MANAGE_EXTERNAL_STORAGE allow",
                    ),
                    null,
                    null,
                )
            } catch (_: IOException) {
                callback?.invoke(false)
                return@Thread
            }
            val success = try {
                process.waitFor() == 0
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                false
            }
            callback?.invoke(success)
        }.start()
    }
}
