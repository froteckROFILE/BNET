package com.bnet.app

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val number = BnetNumber.getOrCreate(this)
        val mesh = MeshManager(this, number)
        setContent { MaterialTheme(colorScheme = darkColorScheme(primary = Green)) { BnetScreen(number, mesh) } }
    }
}

private val Green = Color(0xFF22C55E)

@Composable
fun BnetScreen(myNumber: String, mesh: MeshManager) {
    var dial by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    val status by mesh.status.collectAsState()
    val peers by mesh.peers.collectAsState()
    val callState by mesh.callState.collectAsState()
    val remote by mesh.remoteNumber.collectAsState()
    val permissions = buildList {
        add(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= 31) {
            add(Manifest.permission.BLUETOOTH_SCAN); add(Manifest.permission.BLUETOOTH_ADVERTISE); add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.NEARBY_WIFI_DEVICES)
        if (Build.VERSION.SDK_INT < 32) add(Manifest.permission.ACCESS_FINE_LOCATION)
    }.toTypedArray()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        if (grants.values.all { it }) mesh.start() else message = "Autorise Appareils à proximité et Microphone dans les réglages."
    }
    DisposableEffect(Unit) { onDispose { mesh.stop() } }

    Surface(Modifier.fillMaxSize(), color = Color(0xFF07110B)) {
        Column(Modifier.padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("BNET", fontSize = 30.sp, fontWeight = FontWeight.Black, color = Green)
            Text("Mon numéro : $myNumber", color = Color.White)
            Spacer(Modifier.height(10.dp)); Text(status, color = Color.LightGray)

            if (callState != CallState.IDLE) {
                CallPanel(callState, remote, mesh)
            } else {
                OutlinedButton(onClick = { message = ""; launcher.launch(permissions) }, modifier = Modifier.fillMaxWidth()) { Text("Activer / relancer le radar") }
                Text("${peers.size} téléphone(s) détecté(s)", color = Color.Gray)
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 145.dp)) {
                    items(peers.entries.toList(), key = { it.key }) { peer ->
                        Card(Modifier.fillMaxWidth().padding(vertical = 3.dp).clickable {
                            if (!mesh.callEndpoint(peer.key)) message = "Connexion en préparation, réessaie dans 2 secondes."
                        }) { Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) { Text(peer.value); Text("APPELER", color = Green) } }
                    }
                }
                OutlinedTextField(dial, { dial = it.take(16) }, label = { Text("Numéro BNET") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                val keys = listOf("1","2","3","4","5","6","7","8","9","+","0","⌫")
                keys.chunked(3).forEach { row ->
                    Row {
                        row.forEach { key ->
                            TextButton(
                                onClick = { if (key == "⌫") dial = dial.dropLast(1) else if (dial.length < 16) dial += key },
                                modifier = Modifier.size(88.dp, 50.dp)
                            ) { Text(key, fontSize = 22.sp) }
                        }
                    }
                }
                Button(onClick = { if (!mesh.callNumber(dial)) message = "Numéro absent du radar ou téléphone pas encore connecté." }, modifier = Modifier.fillMaxWidth()) { Text("Appeler sur BNET") }
            }
            if (message.isNotBlank()) Text(message, color = Color(0xFFFFB4AB), modifier = Modifier.padding(8.dp))
            Spacer(Modifier.weight(1f)); Text("Wi‑Fi + Bluetooth obligatoires • application ouverte", color = Color.Gray, fontSize = 12.sp)
        }
    }
}

@Composable
private fun CallPanel(state: CallState, remote: String, mesh: MeshManager) {
    Card(Modifier.fillMaxWidth().padding(vertical = 24.dp)) {
        Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(when (state) { CallState.INCOMING -> "APPEL BNET ENTRANT"; CallState.OUTGOING -> "APPEL EN COURS"; CallState.ACTIVE -> "INTERPHONE ACTIF"; else -> "" }, color = Green, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp)); Text(remote, fontSize = 22.sp)
            Spacer(Modifier.height(22.dp))
            if (state == CallState.INCOMING) Row {
                Button(onClick = mesh::acceptCall) { Text("Décrocher") }; Spacer(Modifier.width(12.dp))
                Button(onClick = mesh::declineCall, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB91C1C))) { Text("Refuser") }
            } else Button(onClick = mesh::hangUp, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB91C1C))) { Text("Raccrocher") }
        }
    }
}
