package com.bnet.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.*
import android.media.RingtoneManager
import android.net.Uri
import android.util.Base64
import androidx.core.content.ContextCompat
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

enum class CallState { IDLE, OUTGOING, INCOMING, ACTIVE }
data class ChatMessage(val peer: String, val text: String, val mine: Boolean, val imageSource: String? = null)

class MeshManager(private val context: Context, val myNumber: String) {
    private val client = Nearby.getConnectionsClient(context)
    val status = MutableStateFlow("Radar arrêté")
    val peers = MutableStateFlow<Map<String, String>>(emptyMap())
    val onlinePeers = MutableStateFlow<Map<String, String>>(emptyMap())
    val callState = MutableStateFlow(CallState.IDLE)
    val remoteNumber = MutableStateFlow("")
    val messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    private val connected = mutableMapOf<String, String>()
    private val pendingNames = mutableMapOf<String, String>()
    private var activeEndpoint: String? = null
    private val audioExecutor = Executors.newCachedThreadPool()
    private val recording = AtomicBoolean(false)
    private var recorder: AudioRecord? = null
    private var player: AudioTrack? = null
    private var audioPipe: PipedOutputStream? = null
    private var incomingRingtone: Ringtone? = null
    private var ringback: ToneGenerator? = null
    private val incomingFiles = mutableMapOf<Long, Payload>()
    private val incomingFileOwners = mutableMapOf<Long, Pair<String, String>>()
    private val completedFiles = mutableSetOf<Long>()

    private fun control(text: String) = Payload.fromBytes(text.toByteArray())

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            if (payload.type == Payload.Type.STREAM) {
                payload.asStream()?.asInputStream()?.let(::playIncomingStream)
                return
            }
            if (payload.type == Payload.Type.FILE) {
                incomingFiles[payload.id] = payload
                saveIncomingPhotoIfReady(payload.id)
                return
            }
            val message = payload.asBytes()?.toString(StandardCharsets.UTF_8) ?: return
            when {
                message.startsWith("CALL|") && callState.value == CallState.IDLE -> {
                    activeEndpoint = endpointId; remoteNumber.value = message.removePrefix("CALL|")
                    callState.value = CallState.INCOMING; status.value = "Appel entrant"; startIncomingRingtone()
                }
                message.startsWith("CALL|") -> client.sendPayload(endpointId, control("BUSY"))
                message == "ACCEPT" -> { stopTones(); callState.value = CallState.ACTIVE; status.value = "Interphone connecté"; startAudio() }
                message == "DECLINE" -> finishCall("Appel refusé")
                message == "END" -> finishCall("Appel terminé")
                message == "BUSY" -> finishCall("Téléphone occupé")
                message.startsWith("MSG|") -> {
                    val text = runCatching { String(Base64.decode(message.removePrefix("MSG|"), Base64.NO_WRAP)) }.getOrNull() ?: return
                    val peer = connected[endpointId] ?: "Inconnu"
                    messages.value = messages.value + ChatMessage(peer, text, false)
                    status.value = "Nouveau message de $peer"
                }
                message.startsWith("PHOTO|") -> {
                    val parts = message.split('|', limit = 3)
                    val id = parts.getOrNull(1)?.toLongOrNull() ?: return
                    val name = parts.getOrNull(2)?.replace(Regex("[^A-Za-z0-9._-]"), "_") ?: "photo.jpg"
                    incomingFileOwners[id] = (connected[endpointId] ?: "Inconnu") to name
                    saveIncomingPhotoIfReady(id)
                }
            }
        }
        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            if (update.status == PayloadTransferUpdate.Status.SUCCESS) {
                completedFiles += update.payloadId
                saveIncomingPhotoIfReady(update.payloadId)
            }
        }
    }

    private val lifecycle = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(id: String, info: ConnectionInfo) {
            pendingNames[id] = info.endpointName; peers.value = peers.value + (id to info.endpointName)
            client.acceptConnection(id, payloadCallback)
        }
        override fun onConnectionResult(id: String, result: ConnectionResolution) {
            if (result.status.isSuccess) {
                connected[id] = pendingNames[id] ?: peers.value[id] ?: "Inconnu"
                onlinePeers.value = connected.toMap()
                status.value = "${connected.size} téléphone(s) disponible(s)"
            } else status.value = "Connexion refusée (${result.status.statusCode})"
        }
        override fun onDisconnected(id: String) {
            connected.remove(id); pendingNames.remove(id); peers.value = peers.value - id
            onlinePeers.value = connected.toMap()
            if (id == activeEndpoint) finishCall("Téléphone déconnecté")
            status.value = "${connected.size} téléphone(s) disponible(s)"
        }
    }

    private val discovery = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(id: String, info: DiscoveredEndpointInfo) {
            peers.value = peers.value + (id to info.endpointName); pendingNames[id] = info.endpointName
            status.value = "Téléphone trouvé : ${info.endpointName}"
            if (myNumber < info.endpointName) client.requestConnection(myNumber, id, lifecycle)
                .addOnFailureListener { status.value = "Connexion impossible : ${it.message ?: "erreur"}" }
        }
        override fun onEndpointLost(id: String) { if (!connected.containsKey(id)) peers.value = peers.value - id }
    }

    fun start() {
        client.stopAdvertising(); client.stopDiscovery(); status.value = "Recherche Wi‑Fi BNET…"
        val strategy = Strategy.P2P_CLUSTER
        client.startAdvertising(myNumber, SERVICE_ID, lifecycle, AdvertisingOptions.Builder().setStrategy(strategy).build())
            .addOnFailureListener { status.value = "Émission bloquée : ${it.message ?: "active Wi‑Fi/Bluetooth"}" }
        client.startDiscovery(SERVICE_ID, discovery, DiscoveryOptions.Builder().setStrategy(strategy).build())
            .addOnSuccessListener { status.value = "Radar actif — recherche…" }
            .addOnFailureListener { status.value = "Recherche bloquée : ${it.message ?: "vérifie les permissions"}" }
    }

    fun callNumber(number: String): Boolean {
        if (callState.value != CallState.IDLE) return false
        val wanted = number.filter(Char::isDigit)
        val target = connected.entries.firstOrNull { it.value.filter(Char::isDigit) == wanted } ?: return false
        activeEndpoint = target.key; remoteNumber.value = target.value; callState.value = CallState.OUTGOING
        status.value = "Appel vers ${target.value}…"; startRingback(); client.sendPayload(target.key, control("CALL|$myNumber")); return true
    }
    fun callEndpoint(endpointId: String) = connected[endpointId]?.let(::callNumber) ?: false
    fun acceptCall() { val id = activeEndpoint ?: return; stopTones(); callState.value = CallState.ACTIVE; status.value = "Interphone connecté"; client.sendPayload(id, control("ACCEPT")); startAudio() }
    fun declineCall() { activeEndpoint?.let { client.sendPayload(it, control("DECLINE")) }; finishCall("Appel refusé") }
    fun hangUp() { activeEndpoint?.let { client.sendPayload(it, control("END")) }; finishCall("Appel terminé") }

    fun sendMessage(endpointId: String, text: String): Boolean {
        val peer = connected[endpointId] ?: return false
        if (text.isBlank()) return false
        val encoded = Base64.encodeToString(text.trim().toByteArray(), Base64.NO_WRAP)
        client.sendPayload(endpointId, control("MSG|$encoded"))
        messages.value = messages.value + ChatMessage(peer, text.trim(), true)
        return true
    }

    fun sendPhoto(endpointId: String, uri: Uri): Boolean {
        val peer = connected[endpointId] ?: return false
        val descriptor = context.contentResolver.openFileDescriptor(uri, "r") ?: return false
        val payload = Payload.fromFile(descriptor)
        val name = queryDisplayName(uri) ?: "photo-${System.currentTimeMillis()}.jpg"
        client.sendPayload(endpointId, control("PHOTO|${payload.id}|$name"))
        client.sendPayload(endpointId, payload)
        messages.value = messages.value + ChatMessage(peer, "Photo", true, uri.toString())
        return true
    }

    private fun queryDisplayName(uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }.getOrNull()

    private fun saveIncomingPhotoIfReady(id: Long) {
        if (id !in completedFiles) return
        val payload = incomingFiles[id] ?: return
        val owner = incomingFileOwners[id] ?: return
        val incoming = payload.asFile()?.asParcelFileDescriptor() ?: return
        val directory = File(context.filesDir, "bnet_photos").apply { mkdirs() }
        val destination = File(directory, "${System.currentTimeMillis()}-${owner.second}")
        runCatching {
            FileInputStream(incoming.fileDescriptor).use { input -> FileOutputStream(destination).use { output -> input.copyTo(output) } }
            incoming.close()
            messages.value = messages.value + ChatMessage(owner.first, "Photo reçue", false, destination.absolutePath)
            status.value = "Photo reçue de ${owner.first}"
        }.onFailure { status.value = "Erreur de réception de la photo" }
        incomingFiles.remove(id); incomingFileOwners.remove(id); completedFiles.remove(id)
    }

    private fun startIncomingRingtone() {
        stopTones()
        incomingRingtone = RingtoneManager.getRingtone(context, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE))?.also { it.play() }
    }
    private fun startRingback() { stopTones(); ringback = ToneGenerator(AudioManager.STREAM_VOICE_CALL, 75).also { it.startTone(ToneGenerator.TONE_SUP_RINGTONE) } }
    private fun stopTones() { runCatching { incomingRingtone?.stop() }; incomingRingtone = null; runCatching { ringback?.stopTone(); ringback?.release() }; ringback = null }

    @Suppress("DEPRECATION")
    private fun startAudio() {
        if (recording.getAndSet(true)) return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) { recording.set(false); status.value = "Microphone non autorisé"; return }
        val rate = 16_000
        val recSize = maxOf(AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT), 1280)
        val playSize = maxOf(AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT), 2560)
        recorder = AudioRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION, rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, recSize)
        player = AudioTrack.Builder().setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()).setAudioFormat(AudioFormat.Builder().setSampleRate(rate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build()).setBufferSizeInBytes(playSize).setTransferMode(AudioTrack.MODE_STREAM).build()
        (context.getSystemService(Context.AUDIO_SERVICE) as AudioManager).apply { mode = AudioManager.MODE_IN_COMMUNICATION; isSpeakerphoneOn = true }
        player?.play(); recorder?.startRecording()
        val input = PipedInputStream(8192); audioPipe = PipedOutputStream(input)
        activeEndpoint?.let { client.sendPayload(it, Payload.fromStream(input)) }
        audioExecutor.execute {
            val buffer = ByteArray(640)
            try { while (recording.get()) { val count = recorder?.read(buffer, 0, buffer.size) ?: -1; if (count > 0) audioPipe?.write(buffer, 0, count) } } catch (_: Exception) { }
        }
    }

    private fun playIncomingStream(input: java.io.InputStream) {
        audioExecutor.execute {
            val buffer = ByteArray(640)
            try { while (recording.get()) { val count = input.read(buffer); if (count < 0) break; player?.write(buffer, 0, count) } } catch (_: Exception) { } finally { runCatching { input.close() } }
        }
    }

    private fun stopAudio() {
        recording.set(false); runCatching { audioPipe?.close() }; audioPipe = null
        runCatching { recorder?.stop() }; recorder?.release(); recorder = null
        runCatching { player?.stop() }; player?.release(); player = null
        (context.getSystemService(Context.AUDIO_SERVICE) as AudioManager).mode = AudioManager.MODE_NORMAL
    }
    private fun finishCall(text: String) { stopTones(); stopAudio(); activeEndpoint = null; remoteNumber.value = ""; callState.value = CallState.IDLE; status.value = text }
    fun stop() { finishCall("Radar arrêté"); client.stopAdvertising(); client.stopDiscovery(); client.stopAllEndpoints() }
    companion object { private const val SERVICE_ID = "com.bnet.mesh.v1" }
}
