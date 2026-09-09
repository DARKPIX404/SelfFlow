package com.selfflow.app

import android.content.Intent
import android.content.pm.PackageInfo
import android.os.Build
import androidx.core.content.FileProvider
import com.getcapacitor.JSObject
import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginMethod
import com.getcapacitor.annotation.CapacitorPlugin
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

/**
 * In-app обновление: версия приложения, скачивание APK релиза с GitHub и
 * передача системному установщику (PackageInstaller через ACTION_VIEW +
 * FileProvider). Скачивание — фоновый поток с событиями прогресса.
 */
@CapacitorPlugin(name = "SelfFlowUpdater")
class UpdaterPlugin : Plugin() {

    @PluginMethod
    fun getAppInfo(call: PluginCall) {
        val pInfo: PackageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageInfo(context.packageName, 0)
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0)
        }
        val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            pInfo.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            pInfo.versionCode.toLong()
        }
        val ret = JSObject()
        ret.put("versionName", pInfo.versionName ?: "")
        ret.put("versionCode", code)
        call.resolve(ret)
    }

    @PluginMethod
    fun download(call: PluginCall) {
        val url = call.getString("url") ?: return call.reject("no url")
        thread {
            try {
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.connectTimeout = 15_000
                conn.readTimeout = 60_000
                conn.instanceFollowRedirects = true
                conn.connect()
                if (conn.responseCode !in 200..299) {
                    call.reject("HTTP ${conn.responseCode}")
                    conn.disconnect()
                    return@thread
                }
                val total = conn.contentLength
                val file = File(context.cacheDir, UPDATE_FILE)
                BufferedInputStream(conn.inputStream).use { input ->
                    FileOutputStream(file).use { out ->
                        val buf = ByteArray(64 * 1024)
                        var downloaded = 0L
                        var lastPct = -1
                        while (true) {
                            val read = input.read(buf)
                            if (read < 0) break
                            out.write(buf, 0, read)
                            downloaded += read
                            if (total > 0) {
                                val pct = ((downloaded * 100) / total).toInt()
                                if (pct != lastPct && pct % 5 == 0) {
                                    lastPct = pct
                                    notifyListeners("downloadProgress", JSObject().put("percent", pct))
                                }
                            }
                        }
                        out.flush()
                    }
                }
                conn.disconnect()
                notifyListeners("downloadProgress", JSObject().put("percent", 100))
                val ret = JSObject()
                ret.put("path", file.absolutePath)
                call.resolve(ret)
            } catch (e: Exception) {
                call.reject("download failed: ${e.message}")
            }
        }
    }

    @PluginMethod
    fun install(call: PluginCall) {
        val path = call.getString("path") ?: return call.reject("no path")
        val file = File(path)
        if (!file.exists()) return call.reject("apk not found")
        val uri = FileProvider.getUriForFile(
            context,
            context.packageName + ".fileprovider",
            file,
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        call.resolve()
    }

    companion object {
        const val UPDATE_FILE = "selfflow-update.apk"
    }
}
