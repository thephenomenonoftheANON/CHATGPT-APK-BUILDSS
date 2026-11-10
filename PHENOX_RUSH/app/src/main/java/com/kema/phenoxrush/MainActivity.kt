package com.kema.phenoxrush

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.collectLatest
import java.util.Locale

private val Neon = Color(0xFFB7FF32)
private val Dark = Color(0xFF080D0A)
private val Panel = Color(0xFF151E18)

class MainActivity : ComponentActivity() {
    private val permissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result[Manifest.permission.ACCESS_FINE_LOCATION] == true) startTrackingService()
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { RushScreen(
            onStart = { requestAndStart() },
            onStop = { startService(Intent(this, TrackingService::class.java).setAction(TrackingService.ACTION_STOP)) }
        ) }
    }
    private fun requestAndStart() {
        val requested = buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (requested.all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }) startTrackingService()
        else permissions.launch(requested.toTypedArray())
    }
    private fun startTrackingService() {
        ContextCompat.startForegroundService(this, Intent(this, TrackingService::class.java).setAction(TrackingService.ACTION_START))
    }
}

@Composable
private fun RushScreen(onStart: () -> Unit, onStop: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val dao = remember { TrackDatabase.get(context).dao() }
    val sessions by dao.sessions().collectAsState(initial = emptyList())
    var active by remember { mutableStateOf(false) }
    var startMillis by remember { mutableLongStateOf(0L) }
    var elapsed by remember { mutableLongStateOf(0L) }
    LaunchedEffect(active) {
        while (active) {
            elapsed = System.currentTimeMillis() - startMillis
            kotlinx.coroutines.delay(1000)
        }
    }
    MaterialTheme(colorScheme = darkColorScheme(primary=Neon, background=Dark, surface=Panel, onPrimary=Dark)) {
        Column(Modifier.fillMaxSize().background(Dark).padding(20.dp)) {
            Text("PHENOX RUSH", color=Neon, style=MaterialTheme.typography.headlineMedium)
            Text("WALK TRACKER  •  GPS", color=Color.LightGray, style=MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(22.dp))
            Card(colors=CardDefaults.cardColors(containerColor=Panel), shape=RoundedCornerShape(22.dp)) {
                Column(Modifier.fillMaxWidth().padding(20.dp)) {
                    Text("LIVE SESSION", color=Color.LightGray)
                    Text("GPS ROUTE RECORDING", color=Neon, style=MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(20.dp))
                    Text(formatDuration(elapsed), color=Color.White, style=MaterialTheme.typography.displayMedium)
                    Text("ELAPSED TIME", color=Color.Gray)
                    Spacer(Modifier.height(16.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.SpaceBetween) {
                        Metric("DISTANCE", if (active) "Recording" else "0.00 km")
                        Metric("AVG SPEED", "-- km/h")
                        Metric("PACE", "-- /km")
                    }
                    Spacer(Modifier.height(18.dp))
                    Button(onClick = {
                        if (!active) { startMillis=System.currentTimeMillis(); elapsed=0; active=true; onStart() }
                        else { active=false; onStop() }
                    }, modifier=Modifier.fillMaxWidth().height(56.dp), colors=ButtonDefaults.buttonColors(containerColor=Neon, contentColor=Dark)) {
                        Text(if (active) "FINISH WALK" else "START WALK")
                    }
                    Text("Keep PHENOX RUSH visible while granting location permission. Android displays a persistent tracking notification.", color=Color.Gray, style=MaterialTheme.typography.bodySmall)
                }
            }
            Spacer(Modifier.height(20.dp))
            Text("WALK HISTORY", color=Neon, style=MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            LazyColumn(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                items(sessions) { s ->
                    Card(colors=CardDefaults.cardColors(containerColor=Panel)) {
                        Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement=Arrangement.SpaceBetween) {
                            Column {
                                Text(java.text.DateFormat.getDateTimeInstance().format(java.util.Date(s.startedAt)), color=Color.White)
                                Text(formatDuration(s.durationMillis), color=Color.Gray)
                            }
                            Text(String.format(Locale.US, "%.2f km", s.distanceMeters/1000), color=Neon)
                        }
                    }
                }
            }
        }
    }
}
@Composable private fun Metric(label: String, value: String) {
    Column { Text(value, color=Color.White, style=MaterialTheme.typography.titleMedium); Text(label, color=Color.Gray, style=MaterialTheme.typography.labelSmall) }
}
private fun formatDuration(ms: Long): String {
    val sec=ms/1000
    return String.format(Locale.US, "%02d:%02d:%02d", sec/3600, (sec%3600)/60, sec%60)
}
