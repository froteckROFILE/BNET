package com.bnet.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.delay
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.abs

private val Green = Color(0xFF26E889)
private val Cyan = Color(0xFF5DE7FF)
private val Red = Color(0xFFFF4E5E)
private val Dark = Color(0xFF050907)
private val Panel = Color(0xFF0D1C14)

data class Evidence(val path: String, val createdAt: String, val status: String)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme(colorScheme = darkColorScheme(primary = Green, background = Dark, surface = Panel)) { GuardApp(this) } }
    }
}

@Composable
private fun GuardApp(context: Context) {
    val prefs = remember { context.getSharedPreferences("bnet_guard", Context.MODE_PRIVATE) }
    var screen by remember { mutableStateOf(if (prefs.getString("owner_signature", null).isNullOrBlank()) "enroll" else "home") }
    var guard by remember { mutableStateOf(false) }
    var alert by remember { mutableStateOf(false) }
    var faceDetected by remember { mutableStateOf(false) }
    var enrollmentCount by remember { mutableIntStateOf(0) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var notice by remember { mutableStateOf("") }
    var evidence by remember { mutableStateOf(loadEvidence(context)) }
    var showPin by remember { mutableStateOf(false) }
    var pinInput by remember { mutableStateOf("") }
    val permissions = arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        if (!grants.values.all { it }) notice = "La caméra et le microphone sont nécessaires au mode garde."
    }
    LaunchedEffect(Unit) {
        if (permissions.any { ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED }) permissionLauncher.launch(permissions)
    }
    LaunchedEffect(guard, alert) {
        if (guard && !alert) {
            while (guard && !alert) {
                delay(2800)
                val capture = imageCapture ?: continue
                captureAndInspect(context, capture, prefs) { unknown, file ->
                    if (unknown) {
                        alert = true
                        guard = false
                        evidence = loadEvidence(context)
                        speakAlarm(context)
                    } else file?.delete()
                }
            }
        }
    }
    Surface(Modifier.fillMaxSize(), color = Dark) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(16.dp)) {
            Header(onSettings = { screen = "settings" })
            when {
                alert -> AlertScreen(evidence, onStop = { showPin = true })
                screen == "enroll" -> EnrollmentScreen(context, { imageCapture = it }, enrollmentCount, faceDetected, { faceDetected = it }, { ctx -> imageCapture?.let { capture -> takeEnrollmentPhoto(ctx, capture) { enrollmentCount++; if (enrollmentCount >= 5) { prefs.edit().putString("owner_signature", loadSignature(ctx)).apply(); screen = "home"; notice = "Visage propriétaire enregistré localement." } } } })
                screen == "proofs" -> ProofsScreen(evidence, onBack = { screen = "home" })
                screen == "settings" -> SettingsScreen(prefs, onBack = { screen = "home" })
                screen == "home" -> HomeScreen(guard, faceDetected, evidence, onGuard = { if (guard) guard = false else showPin = true }, onProofs = { screen = "proofs" }, onCamera = { imageCapture = it }, onFace = { faceDetected = it })
            }
            if (notice.isNotBlank()) Text(notice, color = Green, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(8.dp))
        }
    }
    if (showPin) {
        AlertDialog(onDismissRequest = { showPin = false; pinInput = "" }, title = { Text(if (guard) "Désactiver le mode garde" else "Code propriétaire") }, text = { OutlinedTextField(pinInput, { pinInput = it.take(8) }, label = { Text("Code") }, singleLine = true) }, confirmButton = { Button(onClick = {
            val stored = prefs.getString("owner_pin", "1234").orEmpty()
            if (pinInput == stored) { showPin = false; pinInput = ""; if (alert) { alert = false; screen = "home" } else guard = !guard } else notice = "Code incorrect."
        }) { Text("Valider") } }, dismissButton = { TextButton(onClick = { showPin = false; pinInput = "" }) { Text("Annuler") } })
    }
}

@Composable
private fun Header(onSettings: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column { Text("BNET GUARD", color = Green, fontSize = 25.sp, fontWeight = FontWeight.Black); Text("PROTECTION LOCALE", color = Color.Gray, fontSize = 11.sp, letterSpacing = 2.sp) }
        TextButton(onClick = onSettings) { Text("RÉGLAGES", color = Cyan) }
    }
}

@Composable
private fun HomeScreen(guard: Boolean, face: Boolean, evidence: List<Evidence>, onGuard: () -> Unit, onProofs: () -> Unit, onCamera: (ImageCapture) -> Unit, onFace: (Boolean) -> Unit) {
    Spacer(Modifier.height(12.dp))
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Panel)) {
        Column(Modifier.padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(if (guard) "MODE GARDE ACTIF" else "MODE GARDE INACTIF", color = if (guard) Green else Color.LightGray, fontSize = 19.sp, fontWeight = FontWeight.Black)
            Text(if (guard) "Caméra frontale surveillée • état visible" else "Active la protection volontairement", color = Color.Gray, fontSize = 12.sp)
            Spacer(Modifier.height(12.dp))
            CameraPreview(onCaptureReady = onCamera, onFaceDetected = onFace)
            Spacer(Modifier.height(12.dp))
            Text(if (face) "Visage détecté" else "Aucun visage détecté", color = if (face) Green else Color.Gray)
            Spacer(Modifier.height(12.dp))
            Button(onClick = onGuard, colors = ButtonDefaults.buttonColors(containerColor = if (guard) Red else Green), modifier = Modifier.fillMaxWidth()) { Text(if (guard) "DÉSACTIVER LE MODE GARDE" else "ACTIVER LE MODE GARDE", color = Dark, fontWeight = FontWeight.Bold) }
        }
    }
    Spacer(Modifier.height(12.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = onProofs, modifier = Modifier.weight(1f)) { Text("PREUVES (${evidence.size})") }
        OutlinedButton(onClick = { }, modifier = Modifier.weight(1f)) { Text("URGENCE") }
    }
    Text("Le mode garde est visible et doit être activé volontairement. BNET Guard ne garantit pas l’identification d’un inconnu.", color = Color(0xFFFFC86A), fontSize = 11.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 14.dp))
}

@Composable
private fun EnrollmentScreen(context: Context, onCaptureReady: (ImageCapture) -> Unit, count: Int, face: Boolean, onFace: (Boolean) -> Unit, take: (Context) -> Unit) {
    Text("ENREGISTRER MON VISAGE", color = Green, fontWeight = FontWeight.Black, fontSize = 19.sp, modifier = Modifier.padding(top = 16.dp))
    Text("Prends 5 clichés avec des angles et lumières différents. Les signatures restent sur ce téléphone.", color = Color.LightGray, fontSize = 12.sp, modifier = Modifier.padding(vertical = 8.dp))
    CameraPreview(onCaptureReady = onCaptureReady, onFaceDetected = onFace)
    Text("Clichés valides : $count / 5", color = if (count >= 5) Green else Color.White, modifier = Modifier.padding(10.dp))
    Text(if (face) "Visage détecté : tu peux capturer." else "Place ton visage dans le cadre.", color = if (face) Green else Color.Gray, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    Button(onClick = { take(context) }, enabled = face && count < 5, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) { Text("CAPTURER LE CLICHÉ") }
}

@Composable
private fun AlertScreen(evidence: List<Evidence>, onStop: () -> Unit) {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("ALERTE BNET GUARD", color = Red, fontSize = 27.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(top = 22.dp))
        Text("Visage non reconnu — preuves capturées", color = Color.White, textAlign = TextAlign.Center, modifier = Modifier.padding(8.dp))
        Text("5 • 4 • 3 • 2 • 1", color = Red, fontSize = 38.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(12.dp))
        Text("Alarme active", color = Color(0xFFFFC86A), fontSize = 18.sp)
        Spacer(Modifier.height(18.dp)); Text("${evidence.size} cliché(s) conservé(s) • transmission en attente", color = Color.LightGray)
        Spacer(Modifier.height(28.dp)); Button(onClick = onStop, colors = ButtonDefaults.buttonColors(containerColor = Red)) { Text("ARRÊTER AVEC LE CODE", color = Color.White) }
    }
}

@Composable
private fun ProofsScreen(evidence: List<Evidence>, onBack: () -> Unit) {
    TextButton(onClick = onBack) { Text("‹ Accueil") }
    Text("PREUVES LOCALES", color = Green, fontWeight = FontWeight.Black, fontSize = 19.sp)
    if (evidence.isEmpty()) Text("Aucun événement enregistré.", color = Color.Gray, modifier = Modifier.padding(20.dp))
    LazyColumn(Modifier.fillMaxSize()) { items(evidence) { item -> Card(Modifier.fillMaxWidth().padding(vertical = 4.dp), colors = CardDefaults.cardColors(containerColor = Panel)) { Column(Modifier.padding(13.dp)) { Text(item.createdAt, fontWeight = FontWeight.Bold); Text(item.status, color = Color.LightGray, fontSize = 12.sp); Text(item.path, color = Color.Gray, fontSize = 10.sp) } } } }
}

@Composable
private fun SettingsScreen(prefs: android.content.SharedPreferences, onBack: () -> Unit) {
    var email by remember { mutableStateOf(prefs.getString("email", "").orEmpty()) }
    var pin by remember { mutableStateOf(prefs.getString("owner_pin", "1234").orEmpty()) }
    TextButton(onClick = onBack) { Text("‹ Accueil") }
    Text("RÉGLAGES BNET GUARD", color = Green, fontWeight = FontWeight.Black, fontSize = 19.sp)
    OutlinedTextField(email, { email = it.take(120) }, label = { Text("E-mail de destination") }, modifier = Modifier.fillMaxWidth().padding(top = 14.dp), singleLine = true)
    OutlinedTextField(pin, { pin = it.take(8) }, label = { Text("Code propriétaire") }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), singleLine = true)
    Button(onClick = { prefs.edit().putString("email", email).putString("owner_pin", pin.ifBlank { "1234" }).apply(); onBack() }, modifier = Modifier.fillMaxWidth().padding(top = 14.dp)) { Text("ENREGISTRER") }
    Text("La transmission e-mail nécessite une passerelle HTTPS configurée. Sans connexion, les preuves restent dans la file locale.", color = Color.Gray, fontSize = 11.sp, modifier = Modifier.padding(top = 18.dp))
}

@Composable
private fun CameraPreview(onCaptureReady: (ImageCapture) -> Unit, onFaceDetected: (Boolean) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }
    AndroidView(factory = { PreviewView(context).also { view ->
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            val provider = future.get()
            val preview = Preview.Builder().build().also { it.surfaceProvider = view.surfaceProvider }
            val capture = ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build()
            val analysis = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
            val detector = FaceDetection.getClient(FaceDetectorOptions.Builder().setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST).build())
            analysis.setAnalyzer(executor) { proxy: ImageProxy ->
                val media = proxy.image
                if (media == null) { proxy.close(); return@setAnalyzer }
                detector.process(InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees)).addOnSuccessListener { onFaceDetected(it.isNotEmpty()) }.addOnCompleteListener { proxy.close() }
            }
            try { provider.unbindAll(); provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_FRONT_CAMERA, preview, capture, analysis); onCaptureReady(capture) } catch (_: Exception) { }
        }, ContextCompat.getMainExecutor(context))
        view
    })
}

private fun captureAndInspect(context: Context, capture: ImageCapture, prefs: android.content.SharedPreferences, done: (Boolean, File?) -> Unit) {
    val file = File.createTempFile("guard-check-", ".jpg", context.cacheDir)
    capture.takePicture(ImageCapture.OutputFileOptions.Builder(file).build(), ContextCompat.getMainExecutor(context), object : ImageCapture.OnImageSavedCallback {
        override fun onError(exception: ImageCaptureException) { file.delete(); done(false, null) }
        override fun onImageSaved(output: ImageCapture.OutputFileResults) {
            val detector = FaceDetection.getClient(FaceDetectorOptions.Builder().setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST).build())
            detector.process(InputImage.fromFilePath(context, android.net.Uri.fromFile(file))).addOnSuccessListener { faces ->
                val saved = prefs.getString("owner_signature", null)
                val current = signature(file)
                val distance = if (saved.isNullOrBlank()) 0.0 else compare(saved, current)
                val unknown = faces.isNotEmpty() && distance > 0.23
                if (unknown) {
                    val savedFile = File(context.filesDir, "evidence-${System.currentTimeMillis()}.jpg")
                    runCatching { file.copyTo(savedFile, overwrite = true) }
                    file.delete()
                    done(true, savedFile)
                } else done(false, file)
            }.addOnFailureListener { done(false, file) }
        }
    })
}

private fun takeEnrollmentPhoto(context: Context, capture: ImageCapture, onDone: (File) -> Unit) {
    val file = File(context.filesDir, "enroll-${System.currentTimeMillis()}.jpg")
    capture.takePicture(ImageCapture.OutputFileOptions.Builder(file).build(), ContextCompat.getMainExecutor(context), object : ImageCapture.OnImageSavedCallback {
        override fun onError(exception: ImageCaptureException) { }
        override fun onImageSaved(output: ImageCapture.OutputFileResults) { onDone(file) }
    })
}

private fun loadSignature(context: Context): String {
    val files = context.filesDir.listFiles()?.filter { it.name.startsWith("enroll-") }.orEmpty().takeLast(8)
    if (files.isEmpty()) return ""
    val vectors = files.map { vector(it) }
    val values = ArrayList<String>(64)
    for (index in 0 until 64) {
        var total = 0.0
        for (sample in vectors) total += sample.getOrElse(index) { 0.0 }
        values += "%.4f".format(Locale.US, total / vectors.size.toDouble())
    }
    return values.joinToString(",")
}

private fun signature(file: File): String {
    return vector(file).joinToString(",") { value -> "%.4f".format(Locale.US, value) }
}
private fun vector(file: File): List<Double> {
    val bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: return List(64) { 0.0 }
    val out = mutableListOf<Double>()
    for (y in 0 until 8) for (x in 0 until 8) {
        val px = bitmap.getPixel((x + 1) * bitmap.width / 9, (y + 1) * bitmap.height / 9)
        out += ((0.299 * ((px shr 16) and 255)) + (0.587 * ((px shr 8) and 255)) + (0.114 * (px and 255))) / 255.0
    }
    bitmap.recycle(); return out
}
private fun compare(a: String, b: String): Double {
    val left = a.split(',').mapNotNull { token -> token.toDoubleOrNull() }
    val right = b.split(',').mapNotNull { token -> token.toDoubleOrNull() }
    if (left.size != right.size || left.isEmpty()) return 1.0
    var total = 0.0
    for (index in left.indices) total += abs(left[index] - right[index])
    return total / left.size.toDouble()
}
private fun loadEvidence(context: Context): List<Evidence> = context.filesDir.listFiles()?.filter { it.name.startsWith("evidence-") }?.sortedByDescending { it.lastModified() }?.map { Evidence(it.absolutePath, SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date(it.lastModified())), "Conservé localement • envoi en attente") }.orEmpty()

private fun speakAlarm(context: Context) {
    val speech = tts ?: TextToSpeech(context) { result ->
        if (result == TextToSpeech.SUCCESS) tts?.speak("Don't touch the phone. Three, two, one, alarm.", TextToSpeech.QUEUE_FLUSH, null, "bnet-alarm")
    }.also { tts = it }
    speech.setSpeechRate(0.9f)
    speech.speak("Don't touch the phone. Three, two, one, alarm.", TextToSpeech.QUEUE_FLUSH, null, "bnet-alarm")
    val tone = ToneGenerator(AudioManager.STREAM_ALARM, 100); tone.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 5000)
}
private var tts: TextToSpeech? = null
