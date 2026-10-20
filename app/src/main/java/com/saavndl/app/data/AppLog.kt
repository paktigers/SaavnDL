package com.saavndl.app.data

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AppLog {
    private var file: File? = null

    fun init(ctx: Context) {
        file = File(ctx.filesDir, "app.log")
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            write("CRASH on thread ${t.name}", e)
            prev?.uncaughtException(t, e)
        }
    }

    fun e(tag: String, e: Throwable) = write(tag, e)

    private fun write(tag: String, e: Throwable?) {
        val f = file ?: return
        runCatching {
            if (f.length() > 200_000) f.writeText("")
            val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
            f.appendText("[$time] $tag\n" + (e?.let { Log.getStackTraceString(it) } ?: "") + "\n")
        }
    }

    fun read(): String = runCatching { file?.readText().orEmpty() }.getOrDefault("")

    fun clear() { runCatching { file?.writeText("") } }
}
