package dev.abhinav.screenrecorder

import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.abhinav.screenrecorder.ui.theme.ScreenRecorderTheme

class MainActivity : ComponentActivity() {

    private val mediaProjectionManager by lazy {
        getSystemService<MediaProjectionManager>()!!
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ScreenRecorderTheme {
                val isServiceRunning by RecordService
                    .isRunning
                    .collectAsStateWithLifecycle()

                var hasNotificationPermission by remember {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        mutableStateOf(ContextCompat.checkSelfPermission(applicationContext, android.Manifest.permission.ACCESS_MEDIA_LOCATION) == PackageManager.PERMISSION_GRANTED)
                    } else {
                        mutableStateOf(true)
                    }
                }

                val screenRecordLaunch = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.StartActivityForResult()
                ) { result ->
                    val intent = result.data ?: return@rememberLauncherForActivityResult
                    val config = RecordConfig(resultCode = result.resultCode, data = intent)

                    val serviceIntent = Intent(applicationContext, RecordService::class.java).apply {
                        action = RecordService.START_RECORDING
                        putExtra(RecordService.KEY_RECORDING_CONFIG, config)
                    }
                    startForegroundService(serviceIntent)
                }

                val permission = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission()
                ) { isGranted ->
                    hasNotificationPermission = isGranted
                    if (hasNotificationPermission && !isServiceRunning) {
                        // Start the service
                        screenRecordLaunch.launch(
                            mediaProjectionManager.createScreenCaptureIntent()
                        )
                    }
                }

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Box(
                        modifier = Modifier.fillMaxSize().padding(innerPadding),
                        contentAlignment = Alignment.Center
                    ) {
                        Button(
                            onClick = {
                                if (!hasNotificationPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    permission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    if (isServiceRunning) {
                                        // Stop the service
                                        Intent(applicationContext, RecordService::class.java).also{
                                            it.action = RecordService.STOP_RECORDING
                                            startForegroundService(it)
                                        }

                                    } else {
                                        // Start the service
                                        screenRecordLaunch.launch(
                                            mediaProjectionManager.createScreenCaptureIntent()
                                        )
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                contentColor = if (isServiceRunning) Color.Red else Color.Green
                            )
                        ) {
                            Text(
                                text = if (isServiceRunning) "Stop Recording" else "Start Recording",
                                color = Color.Black
                            )
                        }
                    }
                }
            }
        }
    }
}