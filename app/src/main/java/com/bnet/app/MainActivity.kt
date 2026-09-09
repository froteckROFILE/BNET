package com.bnet.app

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

private val Green = Color(0xFF39FF88)
private val Dark = Color(0xFF030A07)
private val Panel = Color(0xFF0C1B13)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val number = BnetNumber.getOrCreate(this)
        val mesh = MeshManager(this, number)
        setContent { MaterialTheme(colorScheme = darkColorScheme(primary = Green, surface = Panel)) { BnetApp(number, mesh) } }
    }
}

@Composable
private fun BnetApp(number: String, mesh: MeshManager) {
    var splash by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) { delay(3400); splash = false }
    if (splash) BnetSplash() else BnetScreen(number, mesh)
}

@Composable
private fun BnetSplash() {
    val transition = rememberInfiniteTransition(label = "portal")
    val angle by transition.animateFloat(0f, 360f, infiniteRepeatable(tween(1800, easing = LinearEasing)), label = "rotation")
    var shown by remember { mutableIntStateOf(0) }
    val title = "BNET"
    LaunchedEffect(Unit) { repeat(title.length) { delay(300); shown++ } }
    Surface(Modifier.fillMaxSize(), color = Dark) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Canvas(Modifier.size(180.dp)) {
                val pad = 22.dp.toPx(); val diameter = size.minDimension - pad * 2
                drawArc(Green.copy(alpha = .18f), angle, 305f, false, Offset(pad, pad), Size(diameter, diameter), style = Stroke(8.dp.toPx(), cap = StrokeCap.Round))
                drawArc(Green, angle + 55f, 210f, false, Offset(pad + 15, pad + 15), Size(diameter - 30, diameter - 30), style = Stroke(4.dp.toPx(), cap = StrokeCap.Round))
                val r = diameter / 2; val rad = Math.toRadians(angle.toDouble())
                drawCircle(Green, 8.dp.toPx(), Offset(center.x + r * cos(rad).toFloat(), center.y + r * sin(rad).toFloat()))
            }
            Text(title.take(shown), fontSize = 48.sp, fontWeight = FontWeight.Black, letterSpacing = 9.sp, color = Color.White)
            Spacer(Modifier.height(14.dp)); Text("RÉSEAU HUMAIN LOCAL", color = Green, letterSpacing = 2.sp)
            Spacer(Modifier.height(40.dp)); Text("développé par ABDNOUR LABED", color = Color.Gray, fontSize = 13.sp)
        }
    }
}

@Composable
fun BnetScreen(myNumber: String, mesh: MeshManager) {
    var tab by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf("") }
    val status by mesh.status.collectAsState(); val peers by mesh.peers.collectAsState(); val callState by mesh.callState.collectAsState(); val remote by mesh.remoteNumber.collectAsState()
    val permissions = buildList {
        add(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= 31) { add(Manifest.permission.BLUETOOTH_SCAN); add(Manifest.permission.BLUETOOTH_ADVERTISE); add(Manifest.permission.BLUETOOTH_CONNECT) }
        if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.NEARBY_WIFI_DEVICES)
        if (Build.VERSION.SDK_INT < 32) add(Manifest.permission.ACCESS_FINE_LOCATION)
    }.toTypedArray()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        if (grants.values.all { it }) mesh.start() else message = "Autorise les appareils à proximité et le microphone."
    }
    DisposableEffect(Unit) { onDispose { mesh.stop() } }
    Surface(Modifier.fillMaxSize(), color = Dark) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column { Text("BNET", fontSize = 27.sp, fontWeight = FontWeight.Black, color = Green); Text(myNumber, color = Color.White, fontSize = 13.sp) }
                AssistChip(onClick = { launcher.launch(permissions) }, label = { Text(if (peers.isEmpty()) "RADAR" else "${peers.size} EN LIGNE") })
            }
            Text(status, color = Color.Gray, fontSize = 13.sp, modifier = Modifier.padding(vertical = 8.dp))
            if (callState != CallState.IDLE) CallPanel(callState, remote, mesh)
            else if (tab == 0) DialerScreen(peers, mesh) { message = it }
            else MessengerScreen(peers, mesh) { message = it }
            if (message.isNotBlank()) Text(message, color = Color(0xFFFF9B93), fontSize = 13.sp, modifier = Modifier.padding(6.dp))
            Spacer(Modifier.weight(1f))
            NavigationBar(containerColor = Panel) {
                NavigationBarItem(selected = tab == 0, onClick = { tab = 0 }, icon = { Text("☎", fontSize = 22.sp) }, label = { Text("Appels") })
                NavigationBarItem(selected = tab == 1, onClick = { tab = 1 }, icon = { Text("✉", fontSize = 22.sp) }, label = { Text("Messages") })
            }
        }
    }
}

@Composable
private fun DialerScreen(peers: Map<String, String>, mesh: MeshManager, report: (String) -> Unit) {
    var dial by remember { mutableStateOf("") }
    Text("Téléphones à portée", fontWeight = FontWeight.Bold)
    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 130.dp)) {
        items(peers.entries.toList(), key = { it.key }) { peer ->
            PeerCard(peer.value, "APPELER") { if (!mesh.callEndpoint(peer.key)) report("Connexion en préparation, réessaie dans 2 secondes.") }
        }
    }
    OutlinedTextField(dial, { dial = it.take(16) }, label = { Text("Numéro BNET") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), shape = RoundedCornerShape(18.dp))
    val keys = listOf("1","2","3","4","5","6","7","8","9","+","0","⌫")
    keys.chunked(3).forEach { row -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) { row.forEach { key -> TextButton(onClick = { if (key == "⌫") dial = dial.dropLast(1) else if (dial.length < 16) dial += key }, modifier = Modifier.size(86.dp, 48.dp)) { Text(key, fontSize = 22.sp) } } } }
    Button(onClick = { if (!mesh.callNumber(dial)) report("Numéro absent du radar.") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) { Text("APPELER SUR BNET") }
}

@Composable
private fun MessengerScreen(peers: Map<String, String>, mesh: MeshManager, report: (String) -> Unit) {
    val messages by mesh.messages.collectAsState(); var selected by remember { mutableStateOf("") }; var draft by remember { mutableStateOf("") }
    Text("Messagerie locale", fontWeight = FontWeight.Bold)
    if (peers.isEmpty()) Text("Active le radar pour trouver un contact.", color = Color.Gray, modifier = Modifier.padding(16.dp))
    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 105.dp)) { items(peers.entries.toList(), key = { it.key }) { peer -> PeerCard(peer.value, if (selected == peer.key) "CHOISI" else "ÉCRIRE") { selected = peer.key } } }
    HorizontalDivider(Modifier.padding(vertical = 8.dp), color = Color.DarkGray)
    LazyColumn(Modifier.fillMaxWidth().height(190.dp)) {
        items(messages) { item ->
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = if (item.mine) Arrangement.End else Arrangement.Start) {
                Card(colors = CardDefaults.cardColors(containerColor = if (item.mine) Color(0xFF126B3D) else Panel)) { Column(Modifier.padding(10.dp).widthIn(max = 245.dp)) { Text(item.peer, color = Color.LightGray, fontSize = 10.sp); Text(item.text) } }
            }
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(draft, { draft = it.take(500) }, label = { Text("Message") }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(18.dp))
        Spacer(Modifier.width(8.dp)); Button(onClick = { if (selected.isBlank()) report("Choisis d’abord un numéro.") else if (mesh.sendMessage(selected, draft)) draft = "" else report("Message non envoyé.") }) { Text("➤") }
    }
}

@Composable
private fun PeerCard(number: String, action: String, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(vertical = 3.dp).clickable(onClick = onClick), colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(15.dp)) {
        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) { Text(number); Text(action, color = Green, fontWeight = FontWeight.Bold, fontSize = 12.sp) }
    }
}

@Composable
private fun CallPanel(state: CallState, remote: String, mesh: MeshManager) {
    var seconds by remember { mutableIntStateOf(0) }
    LaunchedEffect(state) { seconds = 0; if (state == CallState.ACTIVE) while (true) { delay(1000); seconds++ } }
    val time = "%02d:%02d".format(seconds / 60, seconds % 60)
    Card(Modifier.fillMaxWidth().padding(vertical = 28.dp), colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(28.dp)) {
        Column(Modifier.fillMaxWidth().padding(30.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(when (state) { CallState.INCOMING -> "APPEL BNET ENTRANT"; CallState.OUTGOING -> "SONNERIE…"; CallState.ACTIVE -> "INTERPHONE ACTIF"; else -> "" }, color = Green, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(18.dp)); Text(remote, fontSize = 23.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(10.dp)); Text(if (state == CallState.ACTIVE) time else "— —", fontSize = 30.sp, color = Color.White)
            Spacer(Modifier.height(28.dp))
            if (state == CallState.INCOMING) Row {
                Button(onClick = mesh::acceptCall) { Text("Décrocher") }; Spacer(Modifier.width(12.dp))
                Button(onClick = mesh::declineCall, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB91C1C))) { Text("Refuser") }
            } else Button(onClick = mesh::hangUp, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB91C1C))) { Text("Raccrocher") }
        }
    }
}
