package com.bnet.app

import android.Manifest
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.TrafficStats
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import java.io.File
import java.text.DecimalFormat

private val Green = Color(0xFF32FF88)
private val Dark = Color(0xFF020805)
private val Panel = Color(0xFF0B1911)

data class Finding(val title: String, val detail: String, val severity: Int, val action: (() -> Unit)? = null)
data class ScanResult(val score: Int, val findings: List<Finding>, val inspectedApps: Int)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme(colorScheme = darkColorScheme(primary = Green, surface = Panel)) { SentinelApp(this) } }
    }
}

@Composable
private fun SentinelApp(context: Context) {
    var result by remember { mutableStateOf<ScanResult?>(null) }
    var scanning by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<Finding?>(null) }
    LaunchedEffect(Unit) { scanning = true; result = scanDevice(context); scanning = false }
    Surface(Modifier.fillMaxSize(), color = Dark) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column { Text("BNET SENTINEL", color = Green, fontSize = 25.sp, fontWeight = FontWeight.Black); Text("AUDIT LOCAL DE SÉCURITÉ", color = Color.Gray, fontSize = 11.sp, letterSpacing = 2.sp) }
                Button(onClick = { scanning = true; result = scanDevice(context); scanning = false }) { Text("ANALYSER") }
            }
            Spacer(Modifier.height(14.dp))
            if (scanning || result == null) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(); Text("Analyse…", modifier = Modifier.padding(top = 90.dp)) }
            else {
                val r = result!!
                RiskGauge(r.score)
                Text("${r.inspectedApps} applications contrôlées • analyse exécutée uniquement sur ce téléphone", color = Color.Gray, fontSize = 11.sp, modifier = Modifier.padding(vertical = 8.dp))
                LazyColumn(Modifier.weight(1f)) {
                    items(r.findings) { finding ->
                        Card(Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { selected = finding }, colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(17.dp)) {
                            Row(Modifier.fillMaxWidth().padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                                Surface(shape = RoundedCornerShape(50), color = severityColor(finding.severity).copy(alpha = .18f), modifier = Modifier.size(38.dp)) { Box(contentAlignment = Alignment.Center) { Text(if (finding.severity >= 3) "!" else if (finding.severity == 2) "?" else "✓", color = severityColor(finding.severity), fontWeight = FontWeight.Black) } }
                                Spacer(Modifier.width(11.dp)); Column(Modifier.weight(1f)) { Text(finding.title, fontWeight = FontWeight.Bold); Text(finding.detail, color = Color.LightGray, fontSize = 11.sp, maxLines = 2) }
                            }
                        }
                    }
                }
                Text("Limite : une interception opérateur/SS7 ou un implant très avancé ne peut pas être confirmé par une application ordinaire.", color = Color(0xFFFFC86A), fontSize = 11.sp, textAlign = TextAlign.Center)
            }
        }
    }
    selected?.let { finding -> AlertDialog(onDismissRequest = { selected = null }, title = { Text(finding.title) }, text = { Text(finding.detail) }, confirmButton = { if (finding.action != null) Button(onClick = { finding.action.invoke(); selected = null }) { Text("OUVRIR LE RÉGLAGE") } else TextButton(onClick = { selected = null }) { Text("Fermer") } }) }
}

@Composable
private fun RiskGauge(score: Int) {
    val label = when { score >= 65 -> "RISQUE ÉLEVÉ"; score >= 30 -> "À VÉRIFIER"; else -> "RISQUE FAIBLE" }
    val color = when { score >= 65 -> Color(0xFFFF5A64); score >= 30 -> Color(0xFFFFC44D); else -> Green }
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(25.dp)) {
        Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(112.dp), contentAlignment = Alignment.Center) { Canvas(Modifier.fillMaxSize()) { drawCircle(Color.DarkGray, style = Stroke(10.dp.toPx())); drawArc(color, -90f, 360f * score / 100f, false, style = Stroke(10.dp.toPx())) }; Text("$score", fontSize = 31.sp, fontWeight = FontWeight.Black, color = color) }
            Spacer(Modifier.width(20.dp)); Column { Text(label, color = color, fontWeight = FontWeight.Black, fontSize = 18.sp); Text("Indice d’exposition, pas preuve d’espionnage", color = Color.LightGray, fontSize = 12.sp) }
        }
    }
}

private fun scanDevice(context: Context): ScanResult {
    val findings = mutableListOf<Finding>()
    fun settings(action: String): () -> Unit = { runCatching { context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }; Unit }
    val cm = context.getSystemService(ConnectivityManager::class.java)
    val caps = cm.getNetworkCapabilities(cm.activeNetwork)
    val vpn = caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
    findings += Finding("VPN", if (vpn) "Un tunnel VPN est actif. Vérifiez que vous reconnaissez son application." else "Aucun tunnel VPN actif détecté.", if (vpn) 2 else 0, settings(Settings.ACTION_VPN_SETTINGS))
    val proxy = System.getProperty("http.proxyHost").orEmpty()
    findings += Finding("Proxy réseau", if (proxy.isBlank()) "Aucun proxy système déclaré." else "Proxy actif : $proxy", if (proxy.isBlank()) 0 else 3, settings(Settings.ACTION_WIFI_SETTINGS))
    val adb = Settings.Global.getInt(context.contentResolver, Settings.Global.ADB_ENABLED, 0) == 1
    findings += Finding("Débogage USB", if (adb) "ADB est activé : désactivez-le hors utilisation." else "Débogage USB désactivé.", if (adb) 2 else 0, settings(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
    val accessibility = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
    findings += Finding("Services d’accessibilité", if (accessibility.isBlank()) "Aucun service tiers activé." else "Services activés : ${accessibility.replace(':', '\n')}", if (accessibility.isBlank()) 0 else 3, settings(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    val listeners = NotificationManagerCompat.getEnabledListenerPackages(context) - context.packageName
    findings += Finding("Accès aux notifications", if (listeners.isEmpty()) "Aucune application tierce ne lit les notifications." else listeners.joinToString(prefix = "Applications autorisées : "), if (listeners.isEmpty()) 0 else 2, settings("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
    val dpm = context.getSystemService(DevicePolicyManager::class.java)
    val admins = dpm.activeAdmins.orEmpty().map { it.packageName }.filter { it != context.packageName }
    findings += Finding("Administrateurs de l’appareil", if (admins.isEmpty()) "Aucun administrateur tiers actif." else admins.joinToString(prefix = "Administrateurs : "), if (admins.isEmpty()) 0 else 3, settings(Settings.ACTION_SECURITY_SETTINGS))
    val root = listOf("/system/xbin/su", "/system/bin/su", "/sbin/su", "/data/adb/magisk").any { File(it).exists() } || android.os.Build.TAGS?.contains("test-keys") == true
    findings += Finding("Intégrité système", if (root) "Indices de root ou système modifié détectés." else "Aucun indice simple de root détecté.", if (root) 4 else 0)
    val pm = context.packageManager
    val apps = if (android.os.Build.VERSION.SDK_INT >= 33) pm.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(PackageManager.GET_META_DATA.toLong())) else @Suppress("DEPRECATION") pm.getInstalledApplications(PackageManager.GET_META_DATA)
    val risky = mutableListOf<String>(); val sideloaded = mutableListOf<String>()
    val sensitive = listOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA, Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_CALL_LOG, Manifest.permission.SYSTEM_ALERT_WINDOW)
    apps.filter { it.packageName != context.packageName && it.flags and ApplicationInfo.FLAG_SYSTEM == 0 }.forEach { app ->
        val label = pm.getApplicationLabel(app).toString()
        val granted = sensitive.count { pm.checkPermission(it, app.packageName) == PackageManager.PERMISSION_GRANTED }
        if (granted >= 2) risky += "$label ($granted accès sensibles)"
        val source = runCatching { if (android.os.Build.VERSION.SDK_INT >= 30) pm.getInstallSourceInfo(app.packageName).installingPackageName else @Suppress("DEPRECATION") pm.getInstallerPackageName(app.packageName) }.getOrNull()
        if (source.isNullOrBlank()) sideloaded += label
    }
    findings += Finding("Applications très autorisées", if (risky.isEmpty()) "Aucune combinaison inhabituelle détectée." else risky.take(12).joinToString(), if (risky.isEmpty()) 0 else 3, settings(Settings.ACTION_PRIVACY_SETTINGS))
    findings += Finding("Installations hors boutique", if (sideloaded.isEmpty()) "Aucune installation sans source reconnue." else sideloaded.take(15).joinToString(), if (sideloaded.isEmpty()) 0 else 2, settings(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES))
    val rx = formatBytes(TrafficStats.getTotalRxBytes()); val tx = formatBytes(TrafficStats.getTotalTxBytes())
    findings += Finding("Transferts réseau depuis le démarrage", "Reçu : $rx • Envoyé : $tx. Un volume élevé seul ne prouve pas une fuite.", 1, settings(Settings.ACTION_DATA_USAGE_SETTINGS))
    val playProtect: () -> Unit = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://play.google.com/store/apps/details?id=com.google.android.gms")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }; Unit }
    findings += Finding("Contrôle Play Protect", "Lancez aussi une analyse Play Protect : Sentinel ne remplace pas l’antivirus système.", 1, playProtect)
    val score: Int = findings.fold(0) { total, item -> total + when (item.severity) { 4 -> 28; 3 -> 16; 2 -> 8; else -> 0 } }.coerceAtMost(100)
    return ScanResult(score, findings.sortedByDescending { it.severity }, apps.size)
}

private fun severityColor(level: Int) = when { level >= 3 -> Color(0xFFFF626C); level == 2 -> Color(0xFFFFC44D); else -> Green }
private fun formatBytes(value: Long): String { if (value < 0) return "indisponible"; val units = arrayOf("o", "Ko", "Mo", "Go"); var v = value.toDouble(); var i = 0; while (v >= 1024 && i < units.lastIndex) { v /= 1024; i++ }; return "${DecimalFormat("0.0").format(v)} ${units[i]}" }
