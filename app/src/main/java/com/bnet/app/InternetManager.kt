package com.bnet.app

import android.content.Context
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

class InternetManager(private val context: Context) {
    val status = MutableStateFlow("Internet BNET non connecté")
    val serverNumber = MutableStateFlow("")
    val connected = MutableStateFlow(false)
    private val client = OkHttpClient()
    private val prefs = context.getSharedPreferences("bnet_internet", Context.MODE_PRIVATE)
    private val jsonType = "application/json".toMediaType()

    fun connect() {
        status.value = "Connexion sécurisée à BNET…"
        val token = prefs.getString("access_token", null)
        if (token == null) createAnonymousAccount() else loadProfile(token, 0)
    }

    private fun createAnonymousAccount() {
        val request = Request.Builder()
            .url("${BuildConfig.SUPABASE_URL}/auth/v1/signup")
            .header("apikey", BuildConfig.SUPABASE_KEY)
            .post("{}".toRequestBody(jsonType)).build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { status.value = "Serveur BNET inaccessible" }
            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) { status.value = "Inscription Internet refusée (${response.code})"; return }
                val data = runCatching { JSONObject(body) }.getOrNull()
                val token = data?.optString("access_token").orEmpty()
                if (token.isBlank()) { status.value = "Réponse d’inscription invalide"; return }
                prefs.edit().putString("access_token", token).apply()
                loadProfile(token, 0)
            }
        })
    }

    private fun loadProfile(token: String, attempt: Int) {
        val request = Request.Builder()
            .url("${BuildConfig.SUPABASE_URL}/rest/v1/profiles?select=bnet_number&limit=1")
            .header("apikey", BuildConfig.SUPABASE_KEY)
            .header("Authorization", "Bearer $token").get().build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { status.value = "Connexion Internet interrompue" }
            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string().orEmpty()
                if (response.code == 401) {
                    prefs.edit().remove("access_token").apply(); createAnonymousAccount(); return
                }
                val number = runCatching { JSONArray(body).optJSONObject(0)?.optString("bnet_number").orEmpty() }.getOrDefault("")
                if (number.isBlank() && attempt < 4) {
                    Handler(Looper.getMainLooper()).postDelayed({ loadProfile(token, attempt + 1) }, 700); return
                }
                if (number.isBlank()) { status.value = "Profil BNET absent — vérifie le schéma SQL"; return }
                serverNumber.value = number; connected.value = true; status.value = "Internet BNET connecté"
                markOnline(token)
            }
        })
    }

    private fun markOnline(token: String) {
        val request = Request.Builder()
            .url("${BuildConfig.SUPABASE_URL}/rest/v1/profiles?id=eq.${currentUserId(token)}")
            .header("apikey", BuildConfig.SUPABASE_KEY).header("Authorization", "Bearer $token")
            .header("Prefer", "return=minimal")
            .patch("{\"last_seen\":\"${java.time.Instant.now()}\"}".toRequestBody(jsonType)).build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) = Unit
            override fun onResponse(call: Call, response: Response) { response.close() }
        })
    }

    private fun currentUserId(token: String): String = runCatching {
        val payload = token.split('.')[1]
        val decoded = android.util.Base64.decode(payload, android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP)
        JSONObject(String(decoded)).optString("sub")
    }.getOrDefault("")
}
