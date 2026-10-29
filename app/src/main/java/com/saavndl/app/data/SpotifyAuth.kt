package com.saavndl.app.data

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom

/** Spotify login with the Authorization Code + PKCE flow (needs only a Client ID, no secret). */
object SpotifyAuth {
    const val REDIRECT = "saavndl://callback"
    private const val TOKEN_URL = "https://accounts.spotify.com/api/token"
    private lateinit var sp: SharedPreferences
    private val _connected = MutableStateFlow(false)
    private val _message = MutableStateFlow("")
    val connected: StateFlow<Boolean> = _connected
    val message: StateFlow<String> = _message

    fun init(ctx: Context) {
        sp = ctx.getSharedPreferences("spotify_auth", Context.MODE_PRIVATE)
        _connected.value = !sp.getString("refresh", null).isNullOrBlank()
    }

    private fun b64url(b: ByteArray) = Base64.encodeToString(b, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)

    private fun random(n: Int) = ByteArray(n).also { SecureRandom().nextBytes(it) }

    /** Opens the Spotify login page in the browser. */
    fun start(ctx: Context, clientId: String) {
        val verifier = b64url(random(64))
        val challenge = b64url(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)))
        val state = b64url(random(16))
        sp.edit().putString("verifier", verifier).putString("state", state).apply()
        _message.value = "Waiting for Spotify login…"
        val uri = Uri.Builder().scheme("https").authority("accounts.spotify.com").path("authorize")
            .appendQueryParameter("client_id", clientId.trim())
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("redirect_uri", REDIRECT)
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("code_challenge", challenge)
            .appendQueryParameter("state", state)
            .appendQueryParameter("scope", "playlist-read-private playlist-read-collaborative")
            .build()
        ctx.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun save(o: JSONObject) {
        val e = sp.edit()
        e.putString("access", o.getString("access_token"))
        e.putLong("expires", System.currentTimeMillis() + o.optLong("expires_in", 3600) * 1000 - 60_000)
        if (o.has("refresh_token")) e.putString("refresh", o.getString("refresh_token"))
        e.apply()
        _connected.value = !sp.getString("refresh", null).isNullOrBlank()
    }

    /** Called when the browser comes back to saavndl://callback?code=... */
    suspend fun handleRedirect(uri: Uri) = withContext(Dispatchers.IO) {
        val err = uri.getQueryParameter("error")
        if (err != null) { _message.value = "Spotify login was cancelled ($err)."; return@withContext }
        val code = uri.getQueryParameter("code")
        if (code == null) { _message.value = "Spotify did not return a login code."; return@withContext }
        if (uri.getQueryParameter("state") != sp.getString("state", null)) { _message.value = "Login check failed. Please try again."; return@withContext }
        val verifier = sp.getString("verifier", null)
        if (verifier == null) { _message.value = "Login session expired. Please try again."; return@withContext }
        val cid = SettingsStore.state.value.spotifyId.trim()
        val form = "grant_type=authorization_code&code=${Uri.encode(code)}&redirect_uri=${Uri.encode(REDIRECT)}" +
            "&client_id=${Uri.encode(cid)}&code_verifier=${Uri.encode(verifier)}"
        val (c, b) = httpPostForm(TOKEN_URL, form)
        if (c !in 200..299) {
            _message.value = "Spotify login failed (HTTP $c). Check the Client ID and that $REDIRECT is added as a Redirect URI in your Spotify app."
            return@withContext
        }
        save(JSONObject(b))
        _message.value = "Connected to Spotify."
    }

    /** A valid access token, refreshed when needed; null if not connected. */
    suspend fun accessToken(): String? = withContext(Dispatchers.IO) {
        val acc = sp.getString("access", null)
        if (acc != null && System.currentTimeMillis() < sp.getLong("expires", 0L)) return@withContext acc
        val refresh = sp.getString("refresh", null) ?: return@withContext null
        val cid = SettingsStore.state.value.spotifyId.trim()
        val (c, b) = httpPostForm(TOKEN_URL, "grant_type=refresh_token&refresh_token=${Uri.encode(refresh)}&client_id=${Uri.encode(cid)}")
        if (c in 200..299) {
            save(JSONObject(b))
            sp.getString("access", null)
        } else {
            if (c == 400 || c == 401) disconnect()
            null
        }
    }

    fun disconnect() {
        sp.edit().clear().apply()
        _connected.value = false
        _message.value = "Disconnected."
    }
}
