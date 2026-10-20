package com.saavndl.app.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException

data class UpdateInfo(val tag: String, val notes: String, val url: String)

object Updater {
    /** Returns the newest GitHub release if it is newer than the installed version, else null. Throws on network errors. */
    suspend fun check(ctx: Context, repo: String): UpdateInfo? = withContext(Dispatchers.IO) {
        if (!repo.trim().contains("/")) throw IOException("Set your GitHub repo as owner/name in Settings first")
        val (code, body) = httpGetText("https://api.github.com/repos/${repo.trim()}/releases/latest")
        if (code == 404) throw IOException("No release found for $repo")
        if (code !in 200..299) throw IOException("GitHub returned HTTP $code")
        val o = JSONObject(body)
        val tag = o.optString("tag_name")
        val current = ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "0"
        if (!isNewer(tag, current)) return@withContext null
        var url = o.optString("html_url")
        val assets = o.optJSONArray("assets")
        if (assets != null) for (i in 0 until assets.length()) {
            val a = assets.getJSONObject(i)
            if (a.optString("name").endsWith(".apk", true)) { url = a.optString("browser_download_url"); break }
        }
        UpdateInfo(tag, o.optString("body").take(600), url)
    }

    private fun nums(s: String) = Regex("\\d+").findAll(s).map { it.value.toInt() }.toList()

    fun isNewer(tag: String, current: String): Boolean {
        val a = nums(tag); val b = nums(current)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }; val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }
}
