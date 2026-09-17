package com.bnet.app

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

private val Space = Color(0xFF050914)
private val Card = Color(0xFF101A2D)
private val Gold = Color(0xFFFFC857)
private val Blue = Color(0xFF70B7FF)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 10)
        }
        ContextCompat.startForegroundService(
            this,
            Intent(this, InterstellarTimeService::class.java)
        )
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Gold, background = Space, surface = Card)) {
                InterstellarScreen()
            }
        }
    }
}

@Composable
private fun InterstellarScreen() {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(60_000)
        }
    }

    val calm = CalmClock.display(now)
    val real = CalmClock.realTime(now)

    Surface(Modifier.fillMaxSize(), color = Space) {
        Column(
            Modifier.fillMaxSize().padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(28.dp))
            Text(
                "INTERSTELLAR TIME",
                color = Gold,
                fontSize = 25.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 3.sp
            )
            Text(
                "TEMPS CALME • 2H / 24H",
                color = Blue,
                fontSize = 12.sp,
                letterSpacing = 2.sp,
                modifier = Modifier.padding(top = 8.dp)
            )
            Spacer(Modifier.height(34.dp))
            CalmDial(calm.progress)
            Spacer(Modifier.height(26.dp))
            Text(
                calm.label,
                color = Color.White,
                fontSize = 54.sp,
                fontWeight = FontWeight.Light,
                letterSpacing = 5.sp
            )
            Text(
                "heure affichée",
                color = Color.LightGray,
                fontSize = 14.sp
            )
            Spacer(Modifier.height(30.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = Card),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Heure réelle", color = Color.LightGray, fontSize = 13.sp)
                    Text(real, color = Blue, fontSize = 26.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Chaque minute affichée représente 12 minutes réelles.",
                        color = Color.Gray,
                        fontSize = 12.sp
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            Text(
                "La notification reste visible sur l’écran verrouillé.",
                color = Color.Gray,
                fontSize = 12.sp
            )
            Text(
                "INTERSTELLAR TIME • LABED ABDNOUR",
                color = Gold.copy(alpha = .75f),
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 10.dp, bottom = 12.dp)
            )
        }
    }
}

@Composable
private fun CalmDial(progress: Float) {
    Canvas(Modifier.size(230.dp)) {
        val stroke = 12.dp.toPx()
        val radius = size.minDimension / 2f - stroke
        drawCircle(Color(0xFF1C2A42), radius, style = Stroke(stroke))
        drawArc(
            color = Gold,
            startAngle = -90f,
            sweepAngle = progress * 360f,
            useCenter = false,
            style = Stroke(stroke, cap = StrokeCap.Round)
        )
        val angle = Math.toRadians((-90f + progress * 360f).toDouble())
        val point = center.copy(
            x = center.x + radius * cos(angle).toFloat(),
            y = center.y + radius * sin(angle).toFloat()
        )
        drawCircle(Gold, 8.dp.toPx(), point)
        drawCircle(Blue.copy(alpha = .25f), radius * .55f, style = Stroke(2.dp.toPx()))
    }
}

private data class CalmDisplay(val label: String, val progress: Float)

private object CalmClock {
    fun display(nowMillis: Long): CalmDisplay {
        val cal = java.util.Calendar.getInstance()
        cal.timeInMillis = nowMillis
        val minutes = cal.get(java.util.Calendar.HOUR_OF_DAY) * 60 +
            cal.get(java.util.Calendar.MINUTE) +
            cal.get(java.util.Calendar.SECOND) / 60f
        val calmMinutes = minutes / 12f
        val hour = calmMinutes.toInt() / 60
        val minute = calmMinutes.toInt() % 60
        return CalmDisplay(
            String.format(Locale.US, "%02d:%02d", hour, minute),
            calmMinutes / 120f
        )
    }

    fun realTime(nowMillis: Long): String {
        val cal = java.util.Calendar.getInstance()
        cal.timeInMillis = nowMillis
        return String.format(
            Locale.US,
            "%02d:%02d",
            cal.get(java.util.Calendar.HOUR_OF_DAY),
            cal.get(java.util.Calendar.MINUTE)
        )
    }
}
