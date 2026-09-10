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
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import java.io.File
import java.util.UUID

data class InternetContact(val id: String, val number: String, val nickname: String)
data class InternetVoice(val id: String, val peer: String, val mine: Boolean, val mediaPath: String, val createdAt: String)
data class SharedContact(val id: String, val number: String, val mine: Boolean)
data class InternetText(val id: String, val peer: String, val mine: Boolean, val body: String, val createdAt: String)

class InternetManager(private val context: Context) {
    val callManager = InternetCallManager(context)
    val status = MutableStateFlow("Internet BNET non connecté")
    val serverNumber = MutableStateFlow("")
    val connected = MutableStateFlow(false)
    val contacts = MutableStateFlow<List<InternetContact>>(emptyList())
    val voices = MutableStateFlow<List<InternetVoice>>(emptyList())
    val sharedContacts = MutableStateFlow<List<SharedContact>>(emptyList())
    val texts = MutableStateFlow<List<InternetText>>(emptyList())
    val displayName = MutableStateFlow("")
    val avatarUrl = MutableStateFlow("")
    private val client = OkHttpClient()
    private val prefs = context.getSharedPreferences("bnet_internet", Context.MODE_PRIVATE)
    private val jsonType = "application/json".toMediaType()
    private var recorder: MediaRecorder? = null
    private var recordingFile: File? = null

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
                callManager.start(number)
                markOnline(token)
                loadOwnProfile(token)
                loadContacts()
                loadVoices()
                loadSharedContacts()
                loadTexts()
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

    private fun token(): String? = prefs.getString("access_token", null)

    fun loadContacts() {
        val token = token() ?: return
        api("/rest/v1/contacts?select=id,contact_number,nickname&order=created_at.desc", token) { code, body ->
            val remote = if (code !in 200..299) emptyList() else runCatching {
                val array = JSONArray(body)
                List(array.length()) { i -> array.getJSONObject(i).let { InternetContact(it.getString("id"), it.getString("contact_number"), it.optString("nickname")) } }
            }.getOrDefault(emptyList())
            val all = (remote + loadLocalContacts()).distinctBy { it.number }
            contacts.value = all
        }
    }

    fun addContact(number: String, nickname: String, done: (String) -> Unit) {
        val token = token() ?: return done("Internet BNET non connecté")
        val canonical = normalizeBnetNumber(number) ?: return done("Format invalide. Exemple : +260910-12345678")
        val cleanName = nickname.trim().ifBlank { "Contact BNET" }
        val body = JSONObject().put("owner_id", currentUserId(token)).put("contact_number", canonical).put("nickname", cleanName).toString()
        api("/rest/v1/contacts", token, "POST", body) { code, _ ->
            if (code in 200..299) { loadContacts(); done("Contact ajouté : $canonical") }
            else if (code == 409) done("Ce contact existe déjà")
            else { saveLocalContact(canonical, cleanName); done("Contact enregistré sur ce téléphone. Le serveur a refusé ($code).") }
        }
    }

    fun deleteContact(id: String) {
        val token = token() ?: return
        api("/rest/v1/contacts?id=eq.$id", token, "DELETE") { code, _ ->
            removeLocalContact(id)
            if (code in 200..299) loadContacts() else contacts.value = loadLocalContacts()
        }
    }

    private fun normalizeBnetNumber(value: String): String? {
        val compact = value.trim().replace(" ", "")
        val withPlus = if (compact.startsWith("+")) compact else "+$compact"
        val digits = withPlus.filter { it.isDigit() }
        if (digits.length != 14) return null
        return "+${digits.substring(0, 6)}-${digits.substring(6)}"
    }

    private fun loadLocalContacts(): List<InternetContact> = runCatching {
        val array = JSONArray(prefs.getString("local_contacts", "[]"))
        List(array.length()) { i -> array.getJSONObject(i).let { InternetContact("local-${it.getString("number")}", it.getString("number"), it.optString("name")) } }
    }.getOrDefault(emptyList())

    private fun saveLocalContact(number: String, name: String) {
        val all = loadLocalContacts().filterNot { it.number == number } + InternetContact("local-$number", number, name)
        val array = JSONArray(); all.forEach { array.put(JSONObject().put("number", it.number).put("name", it.nickname)) }
        prefs.edit().putString("local_contacts", array.toString()).apply(); contacts.value = (contacts.value + all).distinctBy { it.number }
    }

    private fun removeLocalContact(id: String) {
        val all = loadLocalContacts().filterNot { it.id == id }
        val array = JSONArray(); all.forEach { array.put(JSONObject().put("number", it.number).put("name", it.nickname)) }
        prefs.edit().putString("local_contacts", array.toString()).apply()
    }

    fun shareContact(recipient: String, sharedNumber: String, done: (String) -> Unit) {
        val token = token() ?: return done("Internet BNET non connecté")
        if (recipient.isBlank()) return done("Sélectionne d’abord le destinataire")
        if (recipient == sharedNumber) return done("Choisis un autre contact à partager")
        val data = JSONObject().put("sender_id", currentUserId(token)).put("recipient_number", recipient)
            .put("body", "Contact BNET : $sharedNumber").put("message_type", "contact").put("shared_contact_number", sharedNumber)
        api("/rest/v1/internet_messages", token, "POST", data.toString()) { code, _ ->
            if (code in 200..299) { loadSharedContacts(); done("Contact partagé avec $recipient") } else done("Partage refusé ($code)")
        }
    }

    fun loadSharedContacts() {
        val token = token() ?: return
        val uid = currentUserId(token)
        api("/rest/v1/internet_messages?message_type=eq.contact&select=id,sender_id,shared_contact_number&order=created_at.desc&limit=30", token) { code, body ->
            if (code !in 200..299) return@api
            sharedContacts.value = runCatching { val a = JSONArray(body); List(a.length()) { i -> a.getJSONObject(i).let { j ->
                SharedContact(j.getString("id"), j.getString("shared_contact_number"), j.getString("sender_id") == uid)
            } } }.getOrDefault(emptyList())
        }
    }

    fun sendText(recipient: String, message: String, done: (String) -> Unit) {
        val token = token() ?: return done("Internet BNET non connecté")
        val clean = message.trim()
        if (recipient.isBlank()) return done("Choisis un contact")
        if (clean.isBlank()) return done("Écris un message")
        val data = JSONObject().put("sender_id", currentUserId(token)).put("recipient_number", recipient)
            .put("body", clean.take(4000)).put("message_type", "text")
        api("/rest/v1/internet_messages", token, "POST", data.toString()) { code, _ ->
            if (code in 200..299) { loadTexts(); done("") } else done("Message refusé ($code)")
        }
    }

    fun loadTexts() {
        val token = token() ?: return
        val uid = currentUserId(token)
        api("/rest/v1/internet_messages?message_type=eq.text&select=id,sender_id,recipient_number,body,created_at&order=created_at.asc&limit=200", token) { code, body ->
            if (code !in 200..299) return@api
            val parsed = runCatching { val a = JSONArray(body); List(a.length()) { i -> a.getJSONObject(i).let { j ->
                val mine = j.getString("sender_id") == uid
                InternetText(j.getString("id"), if (mine) j.getString("recipient_number") else j.getString("sender_id"), mine, j.getString("body"), j.optString("created_at"))
            } } }.getOrDefault(emptyList())
            texts.value = parsed
            parsed.filter { !it.mine && !it.peer.startsWith("+") }.map { it.peer }.distinct().forEach { senderId -> resolveSender(senderId) }
        }
    }

    private fun resolveSender(senderId: String) {
        val token = token() ?: return
        api("/rest/v1/profiles?id=eq.$senderId&select=bnet_number", token) { code, body ->
            if (code !in 200..299) return@api
            val number = runCatching { JSONArray(body).optJSONObject(0)?.optString("bnet_number").orEmpty() }.getOrDefault("")
            if (number.isNotBlank()) texts.value = texts.value.map { if (!it.mine && it.peer == senderId) it.copy(peer = number) else it }
        }
    }

    fun updateProfile(name: String, image: Uri?, done: (String) -> Unit) {
        val token = token() ?: return done("Internet BNET non connecté")
        if (image == null) return patchProfile(token, name, null, done)
        val bytes = runCatching { context.contentResolver.openInputStream(image)?.use { it.readBytes() } }.getOrNull()
            ?: return done("Photo illisible")
        val path = "${currentUserId(token)}/avatar.jpg"
        val request = Request.Builder().url("${BuildConfig.SUPABASE_URL}/storage/v1/object/bnet-avatars/$path")
            .header("apikey", BuildConfig.SUPABASE_KEY).header("Authorization", "Bearer $token").header("x-upsert", "true")
            .post(bytes.toRequestBody("image/jpeg".toMediaType())).build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) = done("Échec d’envoi de la photo")
            override fun onResponse(call: Call, response: Response) {
                response.close()
                if (response.isSuccessful) patchProfile(token, name, path, done) else done("Photo refusée (${response.code})")
            }
        })
    }

    private fun patchProfile(token: String, name: String, avatar: String?, done: (String) -> Unit) {
        val data = JSONObject().put("display_name", name.take(60)); if (avatar != null) data.put("avatar_path", avatar)
        api("/rest/v1/profiles?id=eq.${currentUserId(token)}", token, "PATCH", data.toString()) { code, _ ->
            if (code in 200..299) { loadOwnProfile(token); done("Profil mis à jour") } else done("Profil refusé ($code)")
        }
    }

    private fun loadOwnProfile(token: String) {
        api("/rest/v1/profiles?id=eq.${currentUserId(token)}&select=display_name,avatar_path", token) { code, body ->
            if (code !in 200..299) return@api
            runCatching { JSONArray(body).optJSONObject(0) }.getOrNull()?.let {
                displayName.value = it.optString("display_name")
                val path = it.optString("avatar_path")
                avatarUrl.value = if (path.isBlank()) "" else "${BuildConfig.SUPABASE_URL}/storage/v1/object/public/bnet-avatars/$path"
            }
        }
    }

    fun startVoice(): String {
        if (recorder != null) return "Enregistrement déjà actif"
        val file = File(context.cacheDir, "voice-${UUID.randomUUID()}.m4a")
        return runCatching {
            @Suppress("DEPRECATION")
            val mediaRecorder = if (android.os.Build.VERSION.SDK_INT >= 31) MediaRecorder(context) else MediaRecorder()
            recorder = mediaRecorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC); setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC); setAudioEncodingBitRate(64000); setAudioSamplingRate(44100)
                setOutputFile(file.absolutePath); prepare(); start()
            }
            recordingFile = file; "Enregistrement vocal…"
        }.getOrElse { recorder = null; "Microphone indisponible" }
    }

    fun stopVoiceAndSend(recipient: String, done: (String) -> Unit) {
        val active = recorder ?: return done("Aucun enregistrement actif")
        runCatching { active.stop() }; active.release(); recorder = null
        val file = recordingFile ?: return done("Audio absent")
        recordingFile = null
        val token = token() ?: return done("Internet BNET non connecté")
        if (recipient.isBlank()) return done("Choisis un contact")
        val path = "${currentUserId(token)}/$recipient/${UUID.randomUUID()}.m4a"
        val request = Request.Builder().url("${BuildConfig.SUPABASE_URL}/storage/v1/object/bnet-private/$path")
            .header("apikey", BuildConfig.SUPABASE_KEY).header("Authorization", "Bearer $token")
            .post(file.readBytes().toRequestBody("audio/mp4".toMediaType())).build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) = done("Échec d’envoi audio")
            override fun onResponse(call: Call, response: Response) {
                response.close(); if (!response.isSuccessful) return done("Audio refusé (${response.code})")
                val data = JSONObject().put("sender_id", currentUserId(token)).put("recipient_number", recipient)
                    .put("body", "Message vocal").put("message_type", "voice").put("media_path", path)
                api("/rest/v1/internet_messages", token, "POST", data.toString()) { code, _ ->
                    file.delete(); if (code in 200..299) { loadVoices(); done("Message vocal envoyé") } else done("Message non enregistré ($code)")
                }
            }
        })
    }

    fun loadVoices() {
        val token = token() ?: return
        val uid = currentUserId(token)
        api("/rest/v1/internet_messages?message_type=eq.voice&select=id,sender_id,recipient_number,media_path,created_at&order=created_at.asc&limit=100", token) { code, body ->
            if (code !in 200..299) return@api
            voices.value = runCatching { val a = JSONArray(body); List(a.length()) { i -> a.getJSONObject(i).let { j ->
                val mine = j.getString("sender_id") == uid
                InternetVoice(j.getString("id"), if (mine) j.getString("recipient_number") else "Reçu", mine, j.getString("media_path"), j.optString("created_at"))
            } } }.getOrDefault(emptyList())
        }
    }

    fun playVoice(path: String, done: (String) -> Unit) {
        val token = token() ?: return done("Internet BNET non connecté")
        val request = Request.Builder().url("${BuildConfig.SUPABASE_URL}/storage/v1/object/authenticated/bnet-private/$path")
            .header("apikey", BuildConfig.SUPABASE_KEY).header("Authorization", "Bearer $token").get().build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) = done("Audio inaccessible")
            override fun onResponse(call: Call, response: Response) {
                val bytes = response.body?.bytes(); response.close()
                if (!response.isSuccessful || bytes == null) return done("Lecture refusée (${response.code})")
                val file = File(context.cacheDir, "play-${UUID.randomUUID()}.m4a"); file.writeBytes(bytes)
                runCatching { MediaPlayer().apply { setDataSource(file.absolutePath); prepare(); setOnCompletionListener { it.release(); file.delete() }; start() } }
                    .onSuccess { done("Lecture du message vocal") }.onFailure { done("Format audio illisible") }
            }
        })
    }

    private fun api(path: String, token: String, method: String = "GET", body: String? = null, done: (Int, String) -> Unit) {
        val builder = Request.Builder().url(BuildConfig.SUPABASE_URL + path).header("apikey", BuildConfig.SUPABASE_KEY)
            .header("Authorization", "Bearer $token").header("Content-Type", "application/json").header("Prefer", "return=minimal")
        val requestBody = (body ?: "").toRequestBody(jsonType)
        when (method) { "POST" -> builder.post(requestBody); "PATCH" -> builder.patch(requestBody); "DELETE" -> builder.delete(); else -> builder.get() }
        client.newCall(builder.build()).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) = done(0, "")
            override fun onResponse(call: Call, response: Response) { val text = response.body?.string().orEmpty(); done(response.code, text) }
        })
    }
}
