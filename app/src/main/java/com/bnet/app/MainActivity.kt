package com.bnet.app

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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
import java.util.Calendar
import java.util.Locale
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

private val Space = Color(0xFF050914)
private val Card = Color(0xFF101A2D)
private val Gold = Color(0xFFFFC857)
private val Blue = Color(0xFF70B7FF)
private val Violet = Color(0xFFB58CFF)

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
    var modelIndex by rememberSaveable { mutableIntStateOf(0) }
    var showRealTime by rememberSaveable { mutableStateOf(false) }
    val models = ClockModel.values()

    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }

    val calm = CalmClock.display(now)
    val real = CalmClock.realTime(now)

    Surface(Modifier.fillMaxSize(), color = Space) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "INTERSTELLAR TIME",
                color = Gold,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 3.sp
            )
            Text(
                "BNET • TEMPS CALME",
                color = Blue,
                fontSize = 12.sp,
                letterSpacing = 2.sp,
                modifier = Modifier.padding(top = 6.dp)
            )

            Spacer(Modifier.height(18.dp))
            CalmDial(calm.progress, models[modelIndex])
            Spacer(Modifier.height(12.dp))

            Text(
                calm.label,
                color = Color.White,
                fontSize = 44.sp,
                fontWeight = FontWeight.Light,
                letterSpacing = 4.sp
            )
            Text(
                "BNET • " + calm.period,
                color = Gold,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 2.sp
            )

            Spacer(Modifier.height(16.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = Card),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "00:00 BNET = 13:00 réel",
                        color = Violet,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "1 heure BNET = 12 heures réelles",
                        color = Color.LightGray,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 5.dp)
                    )
                    if (showRealTime) {
                        Text(
                            "Heure réelle : " + real,
                            color = Blue,
                            fontSize = 15.sp,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Afficher le temps réel", color = Color.LightGray, fontSize = 13.sp)
                Switch(
                    checked = showRealTime,
                    onCheckedChange = { showRealTime = it },
                    colors = SwitchDefaults.colors(checkedThumbColor = Space, checkedTrackColor = Gold)
                )
            }

            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Modèle : " + models[modelIndex].title, color = Color.LightGray, fontSize = 13.sp)
                Button(
                    onClick = { modelIndex = (modelIndex + 1) % models.size },
                    colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Space),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text("Changer")
                }
            }

            Spacer(Modifier.weight(1f))
            Text(
                "Notification persistante : écran verrouillé",
                color = Color.Gray,
                fontSize = 12.sp
            )
            Text(
                "INTERSTELLAR TIME • LABED ABDNOUR",
                color = Gold.copy(alpha = .75f),
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

private enum class ClockModel(val title: String) {
    CLASSIC("Classique"),
    NEBULA("Nébuleuse"),
    ORBIT("Orbite"),
    PULSE("Pulse"),
    BLACK_HOLE("Trou noir")
}

@Composable
private fun CalmDial(progress: Float, model: ClockModel) {
    Canvas(Modifier.size(220.dp)) {
        val stroke = 11.dp.toPx()
        val radius = size.minDimension / 2f - stroke
        val sweep = progress * 360f
        when (model) {
            ClockModel.CLASSIC -> {
                drawCircle(Color(0xFF1C2A42), radius, style = Stroke(stroke))
                drawArc(Gold, -90f, sweep, false, style = Stroke(stroke, cap = StrokeCap.Round))
                drawCircle(Blue.copy(alpha = .22f), radius * .55f, style = Stroke(2.dp.toPx()))
            }
            ClockModel.NEBULA -> {
                drawCircle(Color(0xFF142443), radius, style = Stroke(stroke))
                drawCircle(Violet.copy(alpha = .28f), radius * .78f, style = Stroke(3.dp.toPx()))
                drawCircle(Blue.copy(alpha = .22f), radius * .52f, style = Stroke(2.dp.toPx()))
                drawArc(Gold, -90f, sweep, false, style = Stroke(stroke, cap = StrokeCap.Round))
                for (i in 0 until 28) {
                    val a = Math.toRadians((i * 13.7).toDouble())
                    val r = radius * (0.35f + (i % 5) * 0.1f)
                    drawCircle(
                        Gold.copy(alpha = 0.35f + (i % 3) * 0.15f),
                        (1.5f + i % 3).dp.toPx(),
                        center.copy(
                            x = center.x + cos(a).toFloat() * r,
                            y = center.y + sin(a).toFloat() * r
                        )
                    )
                }
            }
            ClockModel.ORBIT -> {
                drawOval(Color(0xFF182B46), style = Stroke(stroke))
                drawArc(Gold, -90f, sweep, false, style = Stroke(stroke, cap = StrokeCap.Round))
                drawOval(Blue.copy(alpha = .28f), style = Stroke(2.dp.toPx()))
                val a = Math.toRadians((-90f + sweep).toDouble())
                drawCircle(
                    Gold,
                    8.dp.toPx(),
                    center.copy(
                        x = center.x + cos(a).toFloat() * radius,
                        y = center.y + sin(a).toFloat() * radius * .55f
                    )
                )
            }
            ClockModel.PULSE -> {
                drawCircle(Color(0xFF15243D), radius, style = Stroke(3.dp.toPx()))
                val bars = 32
                for (i in 0 until bars) {
                    val a = Math.toRadians((i * 360.0 / bars - 90.0))
                    val inner = radius * .62f
                    val outer = radius * (if (i / 4f < progress * bars) .92f else .76f)
                    drawLine(
                        if (i / 4f < progress * bars) Gold else Blue.copy(alpha = .22f),
                        center.copy(
                            x = center.x + cos(a).toFloat() * inner,
                            y = center.y + sin(a).toFloat() * inner
                        ),
                        center.copy(
                            x = center.x + cos(a).toFloat() * outer,
                            y = center.y + sin(a).toFloat() * outer
                        ),
                        5.dp.toPx(),
                        StrokeCap.Round
                    )
                }
            }
            ClockModel.BLACK_HOLE -> {
                drawCircle(Color(0xFF1C1420), radius, style = Stroke(3.dp.toPx()))
                drawArc(Color(0xFFFF8C42).copy(alpha = .65f), -35f, 245f, false, style = Stroke(18.dp.toPx()))
                drawArc(Gold, 150f, 160f, false, style = Stroke(7.dp.toPx(), cap = StrokeCap.Round))
                drawCircle(Color(0xFF010205), radius * .48f)
                drawCircle(Color(0xFF4D3151).copy(alpha = .5f), radius * .53f, style = Stroke(2.dp.toPx()))
            }
        }
    }
}

internal data class CalmDisplay(
    val label: String,
    val progress: Float,
    val period: String
)

internal object CalmClock {
    private const val MINUTES_PER_REAL_DAY = 1_440f
    private const val BNET_MINUTES_PER_REAL_MINUTE = 1f / 12f
    private const val ANCHOR_REAL_MINUTES = 13f * 60f

    fun display(nowMillis: Long): CalmDisplay {
        val cal = Calendar.getInstance()
        cal.timeInMillis = nowMillis
        val realMinuteOfDay =
            cal.get(Calendar.HOUR_OF_DAY) * 60f +
                cal.get(Calendar.MINUTE) +
                cal.get(Calendar.SECOND) / 60f +
                cal.get(Calendar.MILLISECOND) / 60_000f
        val elapsed = (realMinuteOfDay - ANCHOR_REAL_MINUTES + MINUTES_PER_REAL_DAY) % MINUTES_PER_REAL_DAY
        val bnetMinutes = elapsed * BNET_MINUTES_PER_REAL_MINUTE
        val totalSeconds = floor(bnetMinutes * 60f).toInt()
        val hour = (totalSeconds / 3_600) % 24
        val minute = (totalSeconds / 60) % 60
        val second = totalSeconds % 60
        return CalmDisplay(
            String.format(Locale.US, "%02d:%02d:%02d", hour, minute, second),
            bnetMinutes / 120f,
            if (hour < 1) "MATIN" else "APRÈS-MIDI"
        )
    }

    fun realTime(nowMillis: Long): String {
        val cal = Calendar.getInstance()
        cal.timeInMillis = nowMillis
        return String.format(
            Locale.US,
            "%02d:%02d:%02d",
            cal.get(Calendar.HOUR_OF_DAY),
            cal.get(Calendar.MINUTE),
            cal.get(Calendar.SECOND)
        )
    }
}
