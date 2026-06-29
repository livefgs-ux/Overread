package com.aistudio.overread.bzvz.app.ui.screens

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media.projection.MediaProjectionManager
import com.aistudio.overread.bzvz.data.UserPreferencesRepository
import com.aistudio.overread.bzvz.live.LiveModeController
import com.aistudio.overread.bzvz.live.LiveReadingService
import com.aistudio.overread.bzvz.live.LiveSessionState
import com.aistudio.overread.bzvz.live.MediaProjectionSessionManager
import com.aistudio.overread.bzvz.overlay.OverlayPermissionManager
import com.aistudio.overread.bzvz.overlay.OverlayService
import kotlinx.coroutines.launch

/**
 * Home Screen - Main controller for starting live webtoon translation
 * 
 * Simple, uncluttered interface with only essential options:
 * 1. Start Live Translation
 * 2. Change Language
 * 3. Stop if already running
 */
@Composable
fun HomeScreen(
    onNavigateToLanguageSelector: () -> Unit
) {
    val context = LocalContext.current
    val repository = remember { UserPreferencesRepository(context) }
    val scope = rememberCoroutineScope()

    // Preferences
    val targetLanguage by repository.targetLanguageFlow.collectAsState(initial = "en")
    val selectedApp by repository.selectedAppFlow.collectAsState(initial = "entire_screen")

    // Permission states
    var hasOverlayPermission by remember { mutableStateOf(OverlayPermissionManager.hasOverlayPermission(context)) }
    var liveSessionState by remember { mutableStateOf(LiveSessionState.Idle) }

    // Observe live session state
    LaunchedEffect(Unit) {
        LiveModeController.state.collect { state ->
            liveSessionState = state
        }
    }

    // Launcher for screen capture permission
    val liveModeLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK && result.data != null) {
            MediaProjectionSessionManager.prepare(result.resultCode, result.data!!)
            LiveModeController.onPermissionGranted()
            startLiveReading(context)
        } else {
            LiveModeController.onPermissionDenied()
        }
    }

    // Request overlay permission
    val overlayLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        hasOverlayPermission = OverlayPermissionManager.hasOverlayPermission(context)
        if (hasOverlayPermission) {
            requestScreenCapturePermission(context, liveModeLauncher)
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "OverRead",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Real-time Webtoon Translator",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Main Content
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                when (liveSessionState) {
                    LiveSessionState.Idle, LiveSessionState.Paused -> {
                        // Start button
                        Button(
                            onClick = {
                                if (!hasOverlayPermission) {
                                    OverlayPermissionManager.requestOverlayPermission(context, overlayLauncher)
                                } else {
                                    requestScreenCapturePermission(context, liveModeLauncher)
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Start Live Translation",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    LiveSessionState.Running -> {
                        // Stop button
                        Button(
                            onClick = {
                                LiveModeController.stop()
                                LiveReadingService.stop(context)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Text(
                                text = "Stop Translation",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Floating button is active on your screen",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    else -> {
                        CircularProgressIndicator()
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                // Info card
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "Target Language: $targetLanguage",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Translation Mode: ${if (selectedApp == "entire_screen") "Entire Screen" else "Specific App"}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Bottom Controls
            Row(
                modifier = Modifier
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onNavigateToLanguageSelector,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Change Language")
                }

                OutlinedButton(
                    onClick = {
                        // TODO: Open settings for app selection and other preferences
                    },
                    modifier = Modifier
                        .size(48.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings"
                    )
                }
            }
        }
    }
}

private fun requestScreenCapturePermission(
    context: Context,
    launcher: androidx.activity.result.ActivityResultLauncher<Intent>
) {
    val mediaProjectionManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    val captureIntent = mediaProjectionManager.createScreenCaptureIntent()
    launcher.launch(captureIntent)
}

private fun startLiveReading(context: Context) {
    val serviceIntent = Intent(context, LiveReadingService::class.java)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        context.startForegroundService(serviceIntent)
    } else {
        context.startService(serviceIntent)
    }
}
