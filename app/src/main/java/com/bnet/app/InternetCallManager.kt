package com.bnet.app

import android.content.Context
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.util.Base64
import kotlinx.coroutines.flow.MutableStateFlow
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import org.webrtc.*
import java.io.IOException
import java.net.URLEncoder
import java.util.UUID

enum class InternetCallStatus { IDLE, INCOMING, RINGING, CONNECTING, CONNECTED, ENDED }

class InternetCallManager(private val context: Context) {
    val status = MutableStateFlow(InternetCallStatus.IDLE)
    val peerNumber = MutableStateFlow("")
    private val client = OkHttpClient()
    private val handler = Handler(Looper.getMainLooper())
    private val prefs = context.getSharedPreferences("bnet_internet", Context.MODE_PRIVATE)
    private val jsonType = "application/json".toMediaType()
    private var myNumber = ""
    private var callId = ""
    private var pendingOffer: SessionDescription? = null
    private val pendingCandidates = mutableListOf<IceCandidate>()
    private var factory: PeerConnectionFactory? = null
    private var peer: PeerConnection? = null
    private var audioSource: AudioSource? = null
    private var audioTrack: AudioTrack? = null
    private var polling = false

    fun start(number: String) {
        myNumber = number
        if (factory == null) {
            PeerConnectionFactory.initialize(PeerConnectionFactory.InitializationOptions.builder(context).createInitializationOptions())
            factory = PeerConnectionFactory.builder().createPeerConnectionFactory()
        }
        if (!polling) { polling = true; poll() }
    }

    fun call(number: String) {
        if (status.value != InternetCallStatus.IDLE && status.value != InternetCallStatus.ENDED) return
        if (number.isBlank() || number == myNumber) return
        callId = UUID.randomUUID().toString(); peerNumber.value = number
        status.value = InternetCallStatus.RINGING
        createPeer()
        peer?.createOffer(object : SimpleSdpObserver() {
            override fun onCreateSuccess(sdp: SessionDescription) {
                peer?.setLocalDescription(SimpleSdpObserver(), sdp)
                send("offer", JSONObject().put("sdp", sdp.description))
            }
        }, MediaConstraints())
    }

    fun accept() {
        val offer = pendingOffer ?: return
        status.value = InternetCallStatus.CONNECTING
        createPeer()
        peer?.setRemoteDescription(object : SimpleSdpObserver() {
            override fun onSetSuccess() {
                pendingCandidates.forEach { peer?.addIceCandidate(it) }; pendingCandidates.clear()
                peer?.createAnswer(object : SimpleSdpObserver() {
                    override fun onCreateSuccess(sdp: SessionDescription) {
                        peer?.setLocalDescription(SimpleSdpObserver(), sdp)
                        send("answer", JSONObject().put("sdp", sdp.description))
                    }
                }, MediaConstraints())
            }
        }, offer)
        pendingOffer = null
    }

    fun hangup() {
        if (callId.isNotBlank() && peerNumber.value.isNotBlank()) send("hangup", JSONObject())
        closeCall()
    }

    private fun createPeer() {
        peer?.dispose()
        val config = PeerConnection.RTCConfiguration(listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer()
        ))
        config.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
        peer = factory?.createPeerConnection(config, object : PeerConnection.Observer {
            override fun onIceCandidate(candidate: IceCandidate) { send("candidate", JSONObject().put("mid", candidate.sdpMid).put("line", candidate.sdpMLineIndex).put("candidate", candidate.sdp)) }
            override fun onConnectionChange(state: PeerConnection.PeerConnectionState) {
                when (state) {
                    PeerConnection.PeerConnectionState.CONNECTED -> { status.value = InternetCallStatus.CONNECTED; configureAudio(true) }
                    PeerConnection.PeerConnectionState.FAILED, PeerConnection.PeerConnectionState.CLOSED, PeerConnection.PeerConnectionState.DISCONNECTED -> closeCall()
                    else -> Unit
                }
            }
            override fun onSignalingChange(p0: PeerConnection.SignalingState?) = Unit
            override fun onIceConnectionChange(p0: PeerConnection.IceConnectionState?) = Unit
            override fun onIceConnectionReceivingChange(p0: Boolean) = Unit
            override fun onIceGatheringChange(p0: PeerConnection.IceGatheringState?) = Unit
            override fun onIceCandidatesRemoved(p0: Array<out IceCandidate>?) = Unit
            override fun onAddStream(p0: MediaStream?) = Unit
            override fun onRemoveStream(p0: MediaStream?) = Unit
            override fun onDataChannel(p0: DataChannel?) = Unit
            override fun onRenegotiationNeeded() = Unit
            override fun onAddTrack(p0: RtpReceiver?, p1: Array<out MediaStream>?) = Unit
        })
        audioSource = factory?.createAudioSource(MediaConstraints())
        audioTrack = factory?.createAudioTrack("BNET_AUDIO", audioSource)
        audioTrack?.setEnabled(true)
        audioTrack?.let { peer?.addTrack(it, listOf("BNET_STREAM")) }
        configureAudio(false)
    }

    private fun configureAudio(active: Boolean) {
        val audio = context.getSystemService(AudioManager::class.java)
        audio.mode = if (active) AudioManager.MODE_IN_COMMUNICATION else AudioManager.MODE_NORMAL
        audio.isSpeakerphoneOn = false
    }

    private fun poll() {
        val token = token()
        if (token == null || myNumber.isBlank()) { handler.postDelayed(::poll, 1200); return }
        val number = URLEncoder.encode(myNumber, "UTF-8")
        request("/rest/v1/call_signals?recipient_number=eq.$number&processed=eq.false&select=id,sender_number,call_id,signal_type,payload&order=created_at.asc&limit=30", token) { code, body ->
            if (code in 200..299) runCatching {
                val array = JSONArray(body)
                for (i in 0 until array.length()) {
                    val item = array.getJSONObject(i); receive(item); markProcessed(item.getString("id"), token)
                }
            }
            handler.postDelayed(::poll, 900)
        }
    }

    private fun receive(item: JSONObject) {
        val type = item.getString("signal_type")
        val incomingCallId = item.getString("call_id")
        val sender = item.getString("sender_number")
        val payload = item.optJSONObject("payload") ?: JSONObject()
        if (type == "offer") {
            if (status.value != InternetCallStatus.IDLE && status.value != InternetCallStatus.ENDED) {
                val oldPeer = peerNumber.value; peerNumber.value = sender; callId = incomingCallId; send("busy", JSONObject()); peerNumber.value = oldPeer; return
            }
            callId = incomingCallId; peerNumber.value = sender
            pendingOffer = SessionDescription(SessionDescription.Type.OFFER, payload.getString("sdp"))
            status.value = InternetCallStatus.INCOMING
            return
        }
        if (incomingCallId != callId) return
        when (type) {
            "answer" -> {
                status.value = InternetCallStatus.CONNECTING
                peer?.setRemoteDescription(SimpleSdpObserver(), SessionDescription(SessionDescription.Type.ANSWER, payload.getString("sdp")))
            }
            "candidate" -> {
                val candidate = IceCandidate(payload.optString("mid"), payload.optInt("line"), payload.getString("candidate"))
                if (peer?.remoteDescription == null) pendingCandidates += candidate else peer?.addIceCandidate(candidate)
            }
            "hangup", "busy" -> closeCall()
        }
    }

    private fun send(type: String, payload: JSONObject) {
        val token = token() ?: return
        val body = JSONObject().put("sender_id", userId(token)).put("sender_number", myNumber)
            .put("recipient_number", peerNumber.value).put("call_id", callId).put("signal_type", type).put("payload", payload)
        request("/rest/v1/call_signals", token, "POST", body.toString()) { _, _ -> }
    }

    private fun markProcessed(id: String, token: String) = request("/rest/v1/call_signals?id=eq.$id", token, "PATCH", "{\"processed\":true}") { _, _ -> }

    private fun closeCall() {
        peer?.close(); peer?.dispose(); peer = null
        audioTrack?.dispose(); audioTrack = null; audioSource?.dispose(); audioSource = null
        pendingOffer = null; pendingCandidates.clear(); configureAudio(false)
        status.value = InternetCallStatus.ENDED
        handler.postDelayed({ if (status.value == InternetCallStatus.ENDED) { status.value = InternetCallStatus.IDLE; peerNumber.value = ""; callId = "" } }, 1600)
    }

    private fun token() = prefs.getString("access_token", null)
    private fun userId(token: String): String = runCatching {
        val decoded = Base64.decode(token.split('.')[1], Base64.URL_SAFE or Base64.NO_WRAP); JSONObject(String(decoded)).optString("sub")
    }.getOrDefault("")

    private fun request(path: String, token: String, method: String = "GET", body: String? = null, done: (Int, String) -> Unit) {
        val builder = Request.Builder().url(BuildConfig.SUPABASE_URL + path).header("apikey", BuildConfig.SUPABASE_KEY)
            .header("Authorization", "Bearer $token").header("Content-Type", "application/json").header("Prefer", "return=minimal")
        when (method) { "POST" -> builder.post((body ?: "").toRequestBody(jsonType)); "PATCH" -> builder.patch((body ?: "").toRequestBody(jsonType)); else -> builder.get() }
        client.newCall(builder.build()).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) = done(0, "")
            override fun onResponse(call: Call, response: Response) { val text = response.body?.string().orEmpty(); done(response.code, text) }
        })
    }
}

open class SimpleSdpObserver : SdpObserver {
    override fun onCreateSuccess(sdp: SessionDescription?) = Unit
    override fun onSetSuccess() = Unit
    override fun onCreateFailure(error: String?) = Unit
    override fun onSetFailure(error: String?) = Unit
}
