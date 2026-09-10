package com.bnet.app

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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import coil.compose.AsyncImage
import kotlinx.coroutines.delay

private val Neon = Color(0xFFE62B3A)
private val Night = Color(0xFFFFFBFC)
private val Glass = Color(0xFFF4F6F8)

@Composable
fun InternetScreen(internet: InternetManager, number: String) {
    val contacts by internet.contacts.collectAsState()
    val texts by internet.texts.collectAsState()
    val connected by internet.connected.collectAsState()
    var selected by remember { mutableStateOf("") }
    var notice by remember { mutableStateOf("") }
    var addNumber by remember { mutableStateOf("") }
    var addName by remember { mutableStateOf("") }
    var showAdd by remember { mutableStateOf(false) }
    var showProfile by remember { mutableStateOf(false) }
    var profileName by remember { mutableStateOf("") }
    var profilePhoto by remember { mutableStateOf<android.net.Uri?>(null) }
    val savedName by internet.displayName.collectAsState()
    val avatar by internet.avatarUrl.collectAsState()
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { profilePhoto = it }
    LaunchedEffect(savedName) { if (profileName.isBlank()) profileName = savedName }
    LaunchedEffect(Unit) { while (true) { delay(3500); if (connected) { internet.loadContacts(); internet.loadTexts(); internet.loadVoices() } } }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 650.dp
        if (wide) {
            Row(Modifier.fillMaxSize()) {
                ConversationPanel(internet, selected, texts.filter { it.peer == selected }, notice, { notice = it }, Modifier.weight(.7f))
                Spacer(Modifier.width(10.dp))
                DirectoryPanel(contacts, selected, { selected = it }, { showAdd = true }, { showProfile = true }, Modifier.weight(.3f))
            }
        } else if (selected.isBlank()) {
            DirectoryPanel(contacts, selected, { selected = it }, { showAdd = true }, { showProfile = true }, Modifier.fillMaxSize())
        } else {
            Column(Modifier.fillMaxSize()) {
                TextButton(onClick = { selected = "" }, modifier = Modifier.align(Alignment.Start)) { Text("‹ Répertoire") }
                ConversationPanel(internet, selected, texts.filter { it.peer == selected }, notice, { notice = it }, Modifier.fillMaxSize())
            }
        }
        if (showAdd) AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text("Ajouter un numéro BNET") },
            text = { Column { OutlinedTextField(addNumber, { addNumber = it.take(16) }, label = { Text("+AAMMJJ-XXXXXXXX") }); OutlinedTextField(addName, { addName = it.take(40) }, label = { Text("Nom") }) } },
            confirmButton = { Button(onClick = { internet.addContact(addNumber, addName) { notice = it }; showAdd = false }) { Text("Ajouter") } },
            dismissButton = { TextButton(onClick = { showAdd = false }) { Text("Annuler") } }
        )
        if (showProfile) AlertDialog(
            onDismissRequest = { showProfile = false },
            title = { Text("Mon profil BNET") },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    AsyncImage(model = profilePhoto ?: avatar, contentDescription = "Photo de profil", modifier = Modifier.size(92.dp))
                    TextButton(onClick = { photoPicker.launch("image/*") }) { Text("Choisir une photo") }
                    OutlinedTextField(profileName, { profileName = it.take(60) }, label = { Text("Nom affiché") }, singleLine = true)
                    Text(number, color = Color(0xFF69727A), fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                }
            },
            confirmButton = { Button(onClick = { internet.updateProfile(profileName, profilePhoto) { notice = it }; showProfile = false }) { Text("Enregistrer") } },
            dismissButton = { TextButton(onClick = { showProfile = false }) { Text("Annuler") } }
        )
    }
}

@Composable
private fun DirectoryPanel(contacts: List<InternetContact>, selected: String, choose: (String) -> Unit, add: () -> Unit, profile: () -> Unit, modifier: Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = Glass), shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.fillMaxSize().padding(14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column { Text("RÉPERTOIRE", color = Neon, fontWeight = FontWeight.Black); Text("${contacts.size} contact(s)", color = Color.Gray, fontSize = 11.sp) }
                Row {
                    FilledTonalIconButton(onClick = profile) { Text("●") }
                    Spacer(Modifier.width(6.dp))
                    FilledIconButton(onClick = add) { Text("+") }
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 10.dp), color = Neon.copy(alpha = .25f))
            LazyColumn {
                items(contacts, key = { it.id }) { contact ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { choose(contact.number) }, verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = RoundedCornerShape(50), color = if (selected == contact.number) Neon else Color(0xFFFFE8EB), modifier = Modifier.size(44.dp)) {
                            Box(contentAlignment = Alignment.Center) { Text(contact.nickname.take(1).ifBlank { "B" }, fontWeight = FontWeight.Black, color = if (selected == contact.number) Color.White else Neon) }
                        }
                        Spacer(Modifier.width(10.dp)); Column { Text(contact.nickname.ifBlank { "Utilisateur BNET" }, fontWeight = FontWeight.Bold); Text(contact.number, color = Color.Gray, fontSize = 11.sp) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConversationPanel(internet: InternetManager, selected: String, messages: List<InternetText>, notice: String, report: (String) -> Unit, modifier: Modifier) {
    val callStatus by internet.callManager.status.collectAsState()
    val callPeer by internet.callManager.peerNumber.collectAsState()
    val transition = rememberInfiniteTransition(label = "neon-border")
    val phase by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(6500, easing = LinearEasing)), label = "phase")
    var draft by remember { mutableStateOf("") }
    var recording by remember { mutableStateOf(false) }
    val list = rememberLazyListState()
    LaunchedEffect(messages.size) { if (messages.isNotEmpty()) list.animateScrollToItem(messages.lastIndex) }
    NeonFrame(modifier, phase) {
        if (selected.isBlank()) {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Canvas(Modifier.size(150.dp)) { drawCircle(Neon.copy(alpha = .12f)); drawCircle(Neon, radius = 52.dp.toPx(), style = Stroke(3.dp.toPx())); drawCircle(Neon, radius = 8.dp.toPx()) }
                Text("BNET", color = Neon, fontSize = 46.sp, fontWeight = FontWeight.Black, letterSpacing = 8.sp)
                Text("BACK NETWORKING TECHNOLOGY", color = Color(0xFF30363B), letterSpacing = 2.sp, fontSize = 12.sp)
                Text("Choisissez un numéro dans le répertoire", color = Color(0xFF69727A), modifier = Modifier.padding(top = 20.dp))
            }
        } else {
            Column(Modifier.fillMaxSize().padding(14.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column { Text(selected, fontWeight = FontWeight.Black, fontSize = 18.sp); Text("Discussion BNET sécurisée", color = Neon, fontSize = 11.sp) }
                    FilledIconButton(onClick = { internet.callManager.call(selected) }) { Text("☎") }
                }
                HorizontalDivider(Modifier.padding(vertical = 9.dp), color = Neon.copy(alpha = .25f))
                if (callStatus != InternetCallStatus.IDLE) {
                    InternetCallPanel(callStatus, callPeer, internet.callManager)
                    HorizontalDivider(Modifier.padding(vertical = 7.dp), color = Neon.copy(alpha = .25f))
                }
                LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = list) {
                    items(messages, key = { it.id }) { message -> NeonMessage(message, phase) }
                }
                if (notice.isNotBlank()) Text(notice, color = Color(0xFFD22A3A), fontSize = 11.sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(draft, { draft = it.take(4000) }, placeholder = { Text("Écrire un message…") }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(22.dp), maxLines = 3)
                    Spacer(Modifier.width(5.dp))
                    FilledTonalButton(onClick = {
                        if (!recording) { val result = internet.startVoice(); report(result); recording = result.startsWith("Enregistrement") }
                        else { internet.stopVoiceAndSend(selected, report); recording = false }
                    }) { Text(if (recording) "■" else "🎙") }
                    Spacer(Modifier.width(5.dp)); Button(onClick = { val sent = draft; internet.sendText(selected, sent) { result -> report(result); if (result.isBlank()) draft = "" } }) { Text("➤") }
                }
            }
        }
    }
}

@Composable
private fun InternetCallPanel(status: InternetCallStatus, peer: String, manager: InternetCallManager) {
    var seconds by remember(status) { mutableIntStateOf(0) }
    LaunchedEffect(status) { if (status == InternetCallStatus.CONNECTED) while (true) { delay(1000); seconds++ } }
    val label = when (status) {
        InternetCallStatus.INCOMING -> "APPEL ENTRANT"
        InternetCallStatus.RINGING -> "APPEL EN COURS…"
        InternetCallStatus.CONNECTING -> "CONNEXION CHIFFRÉE…"
        InternetCallStatus.CONNECTED -> "CONNECTÉ • %02d:%02d".format(seconds / 60, seconds % 60)
        else -> "APPEL TERMINÉ"
    }
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFFFFE8EB))) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Column { Text(label, color = Neon, fontWeight = FontWeight.Black); Text(peer, fontSize = 12.sp, color = Color(0xFF4F5961)) }
            Row {
                if (status == InternetCallStatus.INCOMING) Button(onClick = manager::accept) { Text("Décrocher") }
                Spacer(Modifier.width(6.dp)); Button(onClick = manager::hangup, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB3261E))) { Text("Couper") }
            }
        }
    }
}

@Composable
private fun NeonFrame(modifier: Modifier, phase: Float, content: @Composable () -> Unit) {
    Box(modifier.padding(2.dp)) {
        Canvas(Modifier.matchParentSize()) {
            val perimeter = 2f * (size.width + size.height)
            val head = phase * perimeter
            val colors = listOf(Color.Transparent, Neon.copy(alpha = .2f), Neon, Color.White, Neon, Color.Transparent)
            drawRoundRect(brush = Brush.linearGradient(colors, start = Offset((head % size.width), 0f), end = Offset(size.width - (head % size.width), size.height)), cornerRadius = androidx.compose.ui.geometry.CornerRadius(24.dp.toPx()), style = Stroke(3.dp.toPx()))
        }
        Surface(Modifier.fillMaxSize().padding(3.dp), color = Night, shape = RoundedCornerShape(22.dp)) { content() }
    }
}

@Composable
private fun NeonMessage(message: InternetText, phase: Float) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = if (message.mine) Arrangement.End else Arrangement.Start) {
        Surface(color = if (message.mine) Color(0xFFFFE8EB) else Color(0xFFF0F2F4), shape = RoundedCornerShape(16.dp), tonalElevation = 3.dp) {
            Box {
                Canvas(Modifier.matchParentSize()) { drawRoundRect(Neon.copy(alpha = .38f + .22f * phase), cornerRadius = androidx.compose.ui.geometry.CornerRadius(16.dp.toPx()), style = Stroke(1.3.dp.toPx())) }
                Column(Modifier.padding(11.dp).widthIn(max = 280.dp)) { Text(message.body, color = Color(0xFF1C2328)); Text(message.createdAt.take(16).replace('T', ' '), color = Color(0xFF69727A), fontSize = 9.sp, modifier = Modifier.align(Alignment.End)) }
            }
        }
    }
}
