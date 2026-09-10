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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin
import coil.compose.AsyncImage

private val BrandRed = Color(0xFFE62B3A)
private val Dark = Color(0xFFFFFBFC)
private val Panel = Color(0xFFF4F6F8)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val number = BnetNumber.getOrCreate(this)
        val mesh = MeshManager(this, number)
        val internet = InternetManager(this)
        setContent { MaterialTheme(colorScheme = lightColorScheme(primary = BrandRed, secondary = Color(0xFFFF6B78), background = Dark, surface = Panel, onSurface = Color(0xFF161A1D))) { BnetApp(number, mesh, internet) } }
    }
}

@Composable
private fun BnetApp(number: String, mesh: MeshManager, internet: InternetManager) {
    var splash by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) { delay(3400); splash = false }
    if (splash) BnetSplash() else BnetScreen(number, mesh, internet)
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
                drawArc(BrandRed.copy(alpha = .16f), angle, 305f, false, Offset(pad, pad), Size(diameter, diameter), style = Stroke(8.dp.toPx(), cap = StrokeCap.Round))
                drawArc(BrandRed, angle + 55f, 210f, false, Offset(pad + 15, pad + 15), Size(diameter - 30, diameter - 30), style = Stroke(4.dp.toPx(), cap = StrokeCap.Round))
                val r = diameter / 2; val rad = Math.toRadians(angle.toDouble())
                drawCircle(BrandRed, 8.dp.toPx(), Offset(center.x + r * cos(rad).toFloat(), center.y + r * sin(rad).toFloat()))
            }
            Text(title.take(shown), fontSize = 48.sp, fontWeight = FontWeight.Black, letterSpacing = 9.sp, color = BrandRed)
            Spacer(Modifier.height(14.dp)); Text("AU SERVICE DES HUMAINS", color = BrandRed, letterSpacing = 2.sp)
            Spacer(Modifier.height(8.dp)); Text("BACK NETWORKING TECHNOLOGY", color = Color(0xFF30363B), letterSpacing = 2.sp, fontSize = 12.sp)
            Spacer(Modifier.height(34.dp)); Text("développée par LABED ABDENOUR", color = Color.Gray, fontSize = 13.sp)
        }
    }
}

@Composable
fun BnetScreen(myNumber: String, mesh: MeshManager, internet: InternetManager) {
    var tab by remember { mutableIntStateOf(2) }
    var message by remember { mutableStateOf("") }
    val status by mesh.status.collectAsState(); val peers by mesh.peers.collectAsState(); val online by mesh.onlinePeers.collectAsState(); val callState by mesh.callState.collectAsState(); val remote by mesh.remoteNumber.collectAsState()
    val internetNumber by internet.serverNumber.collectAsState()
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
    LaunchedEffect(Unit) { internet.connect() }
        Surface(Modifier.fillMaxSize(), color = Dark) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column { Text("BNET", fontSize = 27.sp, fontWeight = FontWeight.Black, color = BrandRed); Text(myNumber, color = Color(0xFF444A50), fontSize = 13.sp) }
                AssistChip(onClick = { launcher.launch(permissions) }, label = { Text(if (online.isEmpty()) "RADAR" else "${online.size} EN LIGNE") })
            }
            Text(status, color = Color(0xFF69727A), fontSize = 13.sp, modifier = Modifier.padding(vertical = 8.dp))
            if (callState != CallState.IDLE) CallPanel(callState, remote, mesh)
            else if (tab == 0) UnifiedDialerScreen(online, mesh, internet) { message = it }
            else if (tab == 1) UnifiedMessengerScreen(online, mesh, internet) { message = it }
            else InternetScreen(internet, internetNumber)
            if (message.isNotBlank()) Text(message, color = Color(0xFFFF9B93), fontSize = 13.sp, modifier = Modifier.padding(6.dp))
            Spacer(Modifier.weight(1f))
            NavigationBar(containerColor = Color.White) {
                NavigationBarItem(selected = tab == 0, onClick = { tab = 0 }, icon = { Text("⌁", fontSize = 22.sp) }, label = { Text("Radar local") })
                NavigationBarItem(selected = tab == 1, onClick = { tab = 1 }, icon = { Text("✉", fontSize = 22.sp) }, label = { Text("Messages") })
                NavigationBarItem(selected = tab == 2, onClick = { tab = 2 }, icon = { Text("☎", fontSize = 22.sp) }, label = { Text("Appels réseau") })
            }
        }
    }
}

@Composable
private fun UnifiedDialerScreen(peers: Map<String, String>, mesh: MeshManager, internet: InternetManager, report: (String) -> Unit) {
    var networkMode by remember { mutableStateOf(true) }
    var dial by remember { mutableStateOf("") }
    val callStatus by internet.callManager.status.collectAsState()
    val callPeer by internet.callManager.peerNumber.collectAsState()
    Column(Modifier.fillMaxWidth()) {
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(22.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text("CLAVIER BNET", color = BrandRed, fontWeight = FontWeight.Black, fontSize = 18.sp)
                Text(if (networkMode) "APPEL NETWORK • Internet chiffré" else "APPEL LOCAL • Radar de proximité", color = Color(0xFF69727A), fontSize = 12.sp)
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    FilterChip(selected = networkMode, onClick = { networkMode = true }, label = { Text("Network") })
                    Spacer(Modifier.width(8.dp))
                    FilterChip(selected = !networkMode, onClick = { networkMode = false }, label = { Text("Local") })
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(dial, { dial = it.filter { ch -> ch.isDigit() || ch == '+' || ch == '-' }.take(16) }, label = { Text("Numéro BNET") }, modifier = Modifier.fillMaxWidth(), singleLine = true, textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center, fontSize = 20.sp), shape = RoundedCornerShape(18.dp))
                val keys = listOf("1","2","3","4","5","6","7","8","9","+","0","⌫")
                keys.chunked(3).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) { row.forEach { key ->
                        TextButton(onClick = { if (key == "⌫") dial = dial.dropLast(1) else if (dial.length < 16) dial += key }, modifier = Modifier.size(78.dp, 46.dp)) { Text(key, fontSize = 21.sp, color = Color(0xFF252A2F)) }
                    } }
                }
                Button(onClick = {
                    if (networkMode) { if (dial.isBlank()) report("Saisis un numéro BNET.") else internet.callManager.call(dial) }
                    else if (!mesh.callNumber(dial)) report("Numéro absent du radar local.")
                }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = BrandRed), shape = RoundedCornerShape(18.dp)) {
                    Text(if (networkMode) "APPELER SUR NETWORK" else "APPELER EN LOCAL", color = Color.White)
                }
            }
        }
        if (!networkMode) {
            Text("RADAR LOCAL", color = BrandRed, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp))
            if (peers.isEmpty()) Text("Aucun téléphone détecté. Active le radar.", color = Color(0xFF69727A), fontSize = 12.sp)
            peers.entries.forEach { (id, number) -> PeerCard(number, "UTILISER") { dial = number } }
        }
        if (callStatus != InternetCallStatus.IDLE) {
            Card(Modifier.fillMaxWidth().padding(top = 10.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFFFE8EB)), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(when (callStatus) { InternetCallStatus.INCOMING -> "APPEL NETWORK ENTRANT"; InternetCallStatus.RINGING -> "APPEL NETWORK…"; InternetCallStatus.CONNECTING -> "CONNEXION CHIFFRÉE…"; InternetCallStatus.CONNECTED -> "APPEL NETWORK CONNECTÉ"; else -> "APPEL TERMINÉ" }, color = BrandRed, fontWeight = FontWeight.Black)
                    Text(callPeer, color = Color(0xFF454D55)); Spacer(Modifier.height(8.dp))
                    Row {
                        if (callStatus == InternetCallStatus.INCOMING) Button(onClick = internet.callManager::accept) { Text("Décrocher") }
                        Spacer(Modifier.width(8.dp)); Button(onClick = internet.callManager::hangup, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB3261E))) { Text("Couper") }
                    }
                }
            }
        }
    }
}

@Composable
private fun UnifiedMessengerScreen(peers: Map<String, String>, mesh: MeshManager, internet: InternetManager, report: (String) -> Unit) {
    var networkMode by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        Text("MESSAGERIE", color = BrandRed, fontWeight = FontWeight.Black, fontSize = 18.sp)
        Text(if (networkMode) "Texte + vocal • Appel network disponible" else "Texte + fichiers • Radar local", color = Color(0xFF69727A), fontSize = 12.sp)
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.Center) {
            FilterChip(selected = !networkMode, onClick = { networkMode = false }, label = { Text("Local") })
            Spacer(Modifier.width(8.dp))
            FilterChip(selected = networkMode, onClick = { networkMode = true }, label = { Text("Network") })
        }
        Box(Modifier.weight(1f, fill = false)) {
            if (networkMode) InternetScreen(internet, internet.serverNumber.collectAsState().value)
            else MessengerScreen(peers, mesh, report)
        }
    }
}

@Composable
private fun InternetScreenLegacy(internet: InternetManager, number: String) {
    val status by internet.status.collectAsState()
    val connected by internet.connected.collectAsState()
    val contacts by internet.contacts.collectAsState()
    val voices by internet.voices.collectAsState()
    val sharedContacts by internet.sharedContacts.collectAsState()
    val savedName by internet.displayName.collectAsState()
    val avatar by internet.avatarUrl.collectAsState()
    var section by remember { mutableIntStateOf(0) }
    var contactNumber by remember { mutableStateOf("") }
    var nickname by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf("") }
    var profileName by remember { mutableStateOf("") }
    var notice by remember { mutableStateOf("") }
    var recording by remember { mutableStateOf(false) }
    var pickedAvatar by remember { mutableStateOf<android.net.Uri?>(null) }
    val avatarPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> pickedAvatar = uri }
    LaunchedEffect(savedName) { if (profileName.isBlank()) profileName = savedName }
    LaunchedEffect(Unit) { while (true) { delay(5000); if (connected) { internet.loadContacts(); internet.loadVoices(); internet.loadSharedContacts() } } }
    Column(
        Modifier.fillMaxWidth().padding(top = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column { Text(if (connected) "● INTERNET ACTIF" else "○ INTERNET", color = if (connected) BrandRed else Color.Gray, fontWeight = FontWeight.Bold); Text(number.ifBlank { status }, fontSize = 13.sp) }
            TextButton(onClick = internet::connect) { Text("Actualiser") }
        }
        TabRow(selectedTabIndex = section, containerColor = Panel) {
            listOf("Répertoire", "Vocaux", "Profil").forEachIndexed { i, title -> Tab(selected = section == i, onClick = { section = i }, text = { Text(title, fontSize = 12.sp) }) }
        }
        Spacer(Modifier.height(10.dp))
        when (section) {
            0 -> {
                Text("RÉPERTOIRE BNET PRIVÉ", color = BrandRed, fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(contactNumber, { contactNumber = it.take(16) }, label = { Text("Numéro BNET") }, modifier = Modifier.weight(1f), singleLine = true)
                    Spacer(Modifier.width(6.dp)); Button(onClick = { internet.addContact(contactNumber, nickname) { notice = it } }) { Text("+") }
                }
                OutlinedTextField(nickname, { nickname = it.take(40) }, label = { Text("Nom du contact") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 250.dp)) {
                    items(contacts, key = { it.id }) { contact ->
                        Card(Modifier.fillMaxWidth().padding(top = 5.dp).clickable { selected = contact.number }, colors = CardDefaults.cardColors(containerColor = if (selected == contact.number) Color(0xFF16472E) else Panel)) {
                            Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Column { Text(contact.nickname.ifBlank { "Contact BNET" }, fontWeight = FontWeight.Bold); Text(contact.number, color = Color.LightGray, fontSize = 12.sp) }
                                Row {
                                    TextButton(onClick = { internet.shareContact(selected, contact.number) { notice = it } }) { Text("Partager", fontSize = 11.sp) }
                                    TextButton(onClick = { internet.deleteContact(contact.id) }) { Text("Supprimer", color = Color(0xFFFF8A80), fontSize = 11.sp) }
                                }
                            }
                        }
                    }
                }
                Text("Touchez un contact pour le sélectionner avant un message vocal.", color = Color.Gray, fontSize = 11.sp)
                if (sharedContacts.any { !it.mine }) {
                    Text("CONTACTS REÇUS", color = BrandRed, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                    sharedContacts.filter { !it.mine }.take(4).forEach { shared ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(shared.number)
                            TextButton(onClick = { internet.addContact(shared.number, "Contact partagé") { notice = it } }) { Text("Ajouter") }
                        }
                    }
                }
            }
            1 -> {
                Text("MESSAGES VOCAUX", color = BrandRed, fontWeight = FontWeight.Bold)
                Text(if (selected.isBlank()) "Sélectionne d’abord un contact dans Répertoire" else "Destinataire : $selected", color = Color.LightGray, fontSize = 12.sp)
                Spacer(Modifier.height(8.dp))
                Button(onClick = {
                    if (!recording) { notice = internet.startVoice(); recording = notice.startsWith("Enregistrement") }
                    else { internet.stopVoiceAndSend(selected) { notice = it }; recording = false }
                }, enabled = selected.isNotBlank(), colors = ButtonDefaults.buttonColors(containerColor = if (recording) Color(0xFFB91C1C) else BrandRed), modifier = Modifier.fillMaxWidth()) {
                    Text(if (recording) "■ ARRÊTER ET ENVOYER" else "● ENREGISTRER UN VOCAL", color = Color.Black)
                }
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 280.dp)) {
                    items(voices, key = { it.id }) { voice ->
                        Card(Modifier.fillMaxWidth().padding(top = 5.dp), colors = CardDefaults.cardColors(containerColor = if (voice.mine) Color(0xFF126B3D) else Color(0xFF17345A))) {
                            Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Column { Text(if (voice.mine) "Envoyé à ${voice.peer}" else "Message vocal reçu"); Text(voice.createdAt.take(16).replace('T', ' '), fontSize = 10.sp, color = Color.LightGray) }
                                Button(onClick = { internet.playVoice(voice.mediaPath) { notice = it } }) { Text("▶") }
                            }
                        }
                    }
                }
            }
            else -> {
                Text("MON PROFIL BNET", color = BrandRed, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                AsyncImage(model = pickedAvatar ?: avatar, contentDescription = "Photo de profil", modifier = Modifier.size(120.dp), contentScale = ContentScale.Crop)
                TextButton(onClick = { avatarPicker.launch("image/*") }) { Text("Changer la photo") }
                OutlinedTextField(profileName, { profileName = it.take(60) }, label = { Text("Nom affiché") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Spacer(Modifier.height(8.dp))
                Button(onClick = { internet.updateProfile(profileName, pickedAvatar) { notice = it } }, modifier = Modifier.fillMaxWidth()) { Text("ENREGISTRER LE PROFIL") }
                Text(number, color = Color.LightGray, modifier = Modifier.padding(top = 10.dp))
            }
        }
        if (notice.isNotBlank()) Text(notice, color = if (notice.contains("refus") || notice.contains("Échec") || notice.contains("invalide")) Color(0xFFFF5252) else BrandRed, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp), textAlign = TextAlign.Center)
    }
}

@Composable
private fun DialerScreen(peers: Map<String, String>, mesh: MeshManager, report: (String) -> Unit) {
    var dial by remember { mutableStateOf("") }
    var showRecipients by remember { mutableStateOf(false) }
    var transferTarget by remember { mutableStateOf("") }
    val transfers by mesh.transfers.collectAsState()
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) {
            val count = mesh.sendAttachments(transferTarget, uris)
            report(if (count > 0) "$count fichier(s) ajouté(s) à la file d’envoi." else "Aucun fichier envoyé.")
        }
    }
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
    FilledTonalButton(onClick = { if (peers.isEmpty()) report("Aucun destinataire connecté.") else showRecipients = true }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) { Text("ENVOYER PHOTOS • VIDÉOS • AUDIO • FICHIERS") }
    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 105.dp)) {
        items(transfers.takeLast(4).reversed(), key = { "${it.id}-${it.mine}" }) { transfer ->
            Column(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(transfer.name, maxLines = 1, fontSize = 11.sp); Text("${transfer.progress}%", color = BrandRed, fontSize = 11.sp) }
                LinearProgressIndicator(progress = { transfer.progress / 100f }, modifier = Modifier.fillMaxWidth())
            }
        }
    }
    if (showRecipients) AlertDialog(
        onDismissRequest = { showRecipients = false },
        title = { Text("Choisir le destinataire") },
        text = { Column { peers.forEach { (id, number) -> TextButton(onClick = { transferTarget = id; showRecipients = false; filePicker.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxWidth()) { Text(number) } } } },
        confirmButton = { TextButton(onClick = { showRecipients = false }) { Text("Annuler") } }
    )
}

@Composable
private fun MessengerScreen(peers: Map<String, String>, mesh: MeshManager, report: (String) -> Unit) {
    val messages by mesh.messages.collectAsState(); var selected by remember { mutableStateOf("") }; var draft by remember { mutableStateOf("") }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isNotEmpty()) {
            if (selected.isBlank()) report("Choisis d’abord un numéro.")
            else {
                val count = mesh.sendAttachments(selected, uris, true)
                if (count == 0) report("Photos non envoyées.") else report("$count photo(s) en cours d’envoi.")
            }
        }
    }
    val listState = rememberLazyListState()
    LaunchedEffect(messages.size) { if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex) }
    Text("Messagerie locale", fontWeight = FontWeight.Bold)
    if (peers.isEmpty()) Text("Active le radar pour trouver un contact.", color = Color.Gray, modifier = Modifier.padding(16.dp))
    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 105.dp)) { items(peers.entries.toList(), key = { it.key }) { peer -> PeerCard(peer.value, if (selected == peer.key) "CHOISI" else "ÉCRIRE") { selected = peer.key } } }
    HorizontalDivider(Modifier.padding(vertical = 8.dp), color = Color.DarkGray)
    LazyColumn(Modifier.fillMaxWidth().height(190.dp), state = listState) {
        items(messages) { item ->
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = if (item.mine) Arrangement.End else Arrangement.Start) {
                Card(colors = CardDefaults.cardColors(containerColor = if (item.mine) Color(0xFF126B3D) else Color(0xFF17345A))) {
                    Column(Modifier.padding(10.dp).widthIn(max = 245.dp)) {
                        Text(if (item.mine) "Envoyé à ${item.peer}" else "Reçu de ${item.peer}", color = Color.LightGray, fontSize = 10.sp)
                        if (item.imageSource != null) AsyncImage(model = item.imageSource, contentDescription = "Photo BNET", modifier = Modifier.fillMaxWidth().heightIn(min = 90.dp, max = 230.dp).padding(top = 5.dp), contentScale = ContentScale.Crop)
                        if (item.text.isNotBlank()) Text(item.text, modifier = Modifier.padding(top = 3.dp))
                    }
                }
            }
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(draft, { draft = it.take(500) }, label = { Text("Message") }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(18.dp))
        Spacer(Modifier.width(6.dp)); FilledTonalButton(onClick = { if (selected.isBlank()) report("Choisis d’abord un numéro.") else photoPicker.launch("image/*") }) { Text("📷") }
        Spacer(Modifier.width(6.dp)); Button(onClick = { if (selected.isBlank()) report("Choisis d’abord un numéro.") else if (mesh.sendMessage(selected, draft)) draft = "" else report("Message non envoyé.") }) { Text("➤") }
    }
}

@Composable
private fun PeerCard(number: String, action: String, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(vertical = 3.dp).clickable(onClick = onClick), colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(15.dp)) {
        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) { Text(number); Text(action, color = BrandRed, fontWeight = FontWeight.Bold, fontSize = 12.sp) }
    }
}

@Composable
private fun CallPanel(state: CallState, remote: String, mesh: MeshManager) {
    var seconds by remember { mutableIntStateOf(0) }
    LaunchedEffect(state) { seconds = 0; if (state == CallState.ACTIVE) while (true) { delay(1000); seconds++ } }
    val time = "%02d:%02d".format(seconds / 60, seconds % 60)
    Card(Modifier.fillMaxWidth().padding(vertical = 28.dp), colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(28.dp)) {
        Column(Modifier.fillMaxWidth().padding(30.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(when (state) { CallState.INCOMING -> "APPEL BNET ENTRANT"; CallState.OUTGOING -> "SONNERIE…"; CallState.ACTIVE -> "INTERPHONE ACTIF"; else -> "" }, color = BrandRed, fontWeight = FontWeight.Bold)
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
