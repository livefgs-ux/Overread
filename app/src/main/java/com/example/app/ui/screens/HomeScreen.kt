package com.example.app.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import android.media.projection.MediaProjectionManager
import com.example.capture.CaptureState
import com.example.capture.MediaProjectionCaptureService
import com.example.capture.ScreenCaptureManager
import com.example.data.UserPreferencesRepository
import com.example.overlay.OverlayPermissionManager
import com.example.overlay.OverlayService
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToLanguageSelector: () -> Unit
) {
    val context = LocalContext.current
    val repository = remember { UserPreferencesRepository(context) }
    val scope = rememberCoroutineScope()
    
    val targetLanguage by repository.targetLanguageFlow.collectAsState(initial = "en")
    val isReadingMode by repository.readingModeFlow.collectAsState(initial = false)
    val floatingButtonSize by repository.floatingButtonSizeFlow.collectAsState(initial = "Medium")
    val overlayOpacity by repository.overlayOpacityFlow.collectAsState(initial = 0.8f)
    val tutorialSeen by repository.tutorialSeenFlow.collectAsState(initial = false)

    var hasOverlayPermission by remember { mutableStateOf(OverlayPermissionManager.hasOverlayPermission(context)) }
    var showTutorial by remember { mutableStateOf(false) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasOverlayPermission = OverlayPermissionManager.hasOverlayPermission(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val overlayLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        hasOverlayPermission = OverlayPermissionManager.hasOverlayPermission(context)
    }

    val captureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK && result.data != null) {
            ScreenCaptureManager.prepare(result.resultCode, result.data!!)
            MediaProjectionCaptureService.start(context)
        }
    }

    var hasNotificationPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        )
    }

    val notificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasNotificationPermission = isGranted
        if (isGranted) {
            OverlayService.start(context)
            if (!tutorialSeen) {
                showTutorial = true
                scope.launch { repository.setTutorialSeen(true) }
            }
        }
    }

    if (showTutorial) {
        TutorialDialog(onDismiss = { showTutorial = false })
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("OverRead Setup") }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            
            // 1. Status Card
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Status", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Overlay Permission: ${if (hasOverlayPermission) "Granted ✓" else "Not Granted ✕"}",
                        color = if (hasOverlayPermission) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                }
            }

            // 2. Screen Capture Card
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Screen Capture", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "OverRead uses internet only to download official ML Kit language models when needed. Captured frames, OCR text and translations are processed on-device and are not uploaded by OverRead. Translation runs offline after the required language model has been downloaded.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    val captureState by ScreenCaptureManager.captureState.collectAsState()
                    val lastCaptureResult by ScreenCaptureManager.lastCaptureResult.collectAsState()

                    Text("Status: ${captureState.name}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (lastCaptureResult != null) {
                        if (lastCaptureResult!!.success) {
                            Text("Last capture: ${lastCaptureResult!!.width}x${lastCaptureResult!!.height} ✓", color = MaterialTheme.colorScheme.primary)
                        } else {
                            Text("Last capture failed: ${lastCaptureResult!!.errorMessage}", color = MaterialTheme.colorScheme.error)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    
                    if (captureState == CaptureState.Idle || captureState == CaptureState.PermissionRequired || captureState == CaptureState.Failed) {
                        Button(onClick = {
                            val mpm = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                            captureLauncher.launch(mpm.createScreenCaptureIntent())
                        }) {
                            Text("Prepare Screen Capture")
                        }
                    } else {
                        OutlinedButton(onClick = {
                            MediaProjectionCaptureService.stop(context)
                        }) {
                            Text("Stop Capture Service")
                        }
                    }
                }
            }
            
            // 2.3 Translation Model Setup
            val modelStatus by com.example.translation.model.TranslationModelManager.modelStatus.collectAsState()
            val coroutineScope = rememberCoroutineScope()
            
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
                    Text("Translation Model Setup", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "This downloads the local language model used for on-device translation. Your screenshots and detected text are not uploaded.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    if (modelStatus.sourceLanguage == null || modelStatus.sourceLanguage == "und") {
                        Text("Detect text first to know which model is needed.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else if (modelStatus.sourceLanguage == modelStatus.targetLanguage) {
                        Text("Source: ${modelStatus.sourceLanguage} / Target: ${modelStatus.targetLanguage}", style = MaterialTheme.typography.bodyMedium)
                        Text("No translation model needed because source and target are the same.", color = MaterialTheme.colorScheme.secondary)
                    } else {
                        Text("Source: ${modelStatus.sourceLanguage} / Target: ${modelStatus.targetLanguage}", style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.height(4.dp))
                        
                        when {
                            modelStatus.downloadFailed -> {
                                Text("Model download failed. Check connection and try again.", color = MaterialTheme.colorScheme.error)
                                if (modelStatus.errorMessage != null) {
                                    Text(modelStatus.errorMessage!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                                }
                            }
                            modelStatus.isReady -> {
                                Text("Status: Ready ✓", color = MaterialTheme.colorScheme.primary)
                            }
                            modelStatus.isDownloading -> {
                                Text("Status: Downloading...", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            else -> {
                                Text("Status: Required", color = MaterialTheme.colorScheme.secondary)
                            }
                        }
                        
                        if (!modelStatus.isReady && !modelStatus.isDownloading) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(onClick = {
                                coroutineScope.launch {
                                    com.example.translation.model.TranslationModelManager.downloadModel(
                                        modelStatus.sourceLanguage!!, 
                                        modelStatus.targetLanguage
                                    )
                                }
                            }) {
                                Text("Prepare translation model")
                            }
                        }
                    }
                }
            }
            
            // 2.5 Processed Text Diagnostics Card
            val ocrState by com.example.vision.VisionManager.ocrState.collectAsState()
            val ocrResult by com.example.vision.VisionManager.lastOcrResult.collectAsState()
            val processedResult by com.example.vision.VisionManager.lastProcessedResult.collectAsState()
            val langIdState by com.example.vision.VisionManager.languageIdState.collectAsState()
            val langIdResult by com.example.vision.VisionManager.lastLanguageIdResult.collectAsState()
            
            val translationState by com.example.translation.TranslationManager.translationState.collectAsState()
            val translationResult by com.example.translation.TranslationManager.lastTranslationResult.collectAsState()
            
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
                    Text("Diagnostics", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text("OCR Status: ${ocrState.name}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (langIdState != com.example.vision.language.LanguageIdState.Idle) {
                        Text("Language ID Status: ${langIdState.name}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (translationState != com.example.translation.TranslationState.Idle) {
                        Text("Translation Status: ${translationState.name}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    
                    if (ocrResult != null || processedResult != null || langIdResult != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        if (ocrResult?.success == false) {
                            Text("OCR Failed: ${ocrResult?.errorMessage}", color = MaterialTheme.colorScheme.error)
                        } else if (processedResult?.success == false) {
                            Text("Processing Failed: ${processedResult?.errorMessage}", color = MaterialTheme.colorScheme.error)
                        } else {
                            if (ocrResult != null) {
                                Text("Raw OCR Blocks: ${ocrResult?.blockCount ?: 0}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            if (processedResult != null) {
                                Text("Processed Blocks: ${processedResult?.processedBlockCount ?: 0}", color = MaterialTheme.colorScheme.primary)
                            }

                            if (langIdResult != null) {
                                Spacer(modifier = Modifier.height(8.dp))
                                if (langIdResult?.success == true) {
                                    if (langIdResult?.isUnknown == true) {
                                        Text("Detected language: Unknown / Low Confidence", color = MaterialTheme.colorScheme.error)
                                    } else {
                                        Text("Detected language: ${langIdResult?.detectedLanguage}", color = MaterialTheme.colorScheme.primary)
                                        Text("Confidence: ${langIdResult?.confidence}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text("Target language: ${langIdResult?.targetLanguage}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        if (langIdResult?.isSameAsTarget == true) {
                                            Text("Detected language already matches target language.", color = MaterialTheme.colorScheme.secondary)
                                        }
                                    }
                                } else {
                                    Text("Language ID Failed: ${langIdResult?.errorMessage}", color = MaterialTheme.colorScheme.error)
                                }
                            }
                            
                            if (translationResult != null) {
                                Spacer(modifier = Modifier.height(8.dp))
                                if (translationResult?.success == false) {
                                    Text("Translation Failed: ${translationResult?.errorMessage}", color = MaterialTheme.colorScheme.error)
                                } else if (translationState == com.example.translation.TranslationState.Skipped) {
                                    val reason = translationResult?.skippedReason?.name ?: "Unknown"
                                    Text("Translation Skipped: $reason", color = MaterialTheme.colorScheme.secondary)
                                } else if (translationState == com.example.translation.TranslationState.Success) {
                                    Text("Source -> Target: ${translationResult?.sourceLanguage} -> ${translationResult?.targetLanguage}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("Full Translation length: ${translationResult?.fullTranslatedText?.length ?: 0} chars", color = MaterialTheme.colorScheme.primary)
                                }
                            }
                            
                            val renderState by com.example.render.RenderManager.renderState.collectAsState()
                            val renderResult by com.example.render.RenderManager.lastRenderResult.collectAsState()
                            
                            if (renderState != com.example.render.RenderState.Idle) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Overlay Status: ${renderState.name}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (renderResult != null) {
                                    if (renderResult?.success == false) {
                                        Text("Render Failed: ${renderResult?.errorMessage}", color = MaterialTheme.colorScheme.error)
                                    } else if (renderState == com.example.render.RenderState.Success) {
                                        Text("Rendered Boxes: ${renderResult?.boxes?.size ?: 0}", color = MaterialTheme.colorScheme.primary)
                                    } else if (renderState == com.example.render.RenderState.Skipped) {
                                        Text("Render Skipped: No blocks to render.", color = MaterialTheme.colorScheme.secondary)
                                    }
                                }
                                
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedButton(onClick = { com.example.render.RenderManager.reset() }) {
                                    Text("Clear translation boxes")
                                }
                            }
                        }
                    }
                }
            }

            // 3. Action Card
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            ) {
                Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
                    Text("Actions", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    if (!hasOverlayPermission) {
                        Button(
                            onClick = { overlayLauncher.launch(OverlayPermissionManager.getOverlayPermissionIntent(context)) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Allow Floating Button")
                        }
                    } else {
                        Button(
                            onClick = {
                                if (!hasNotificationPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    OverlayService.start(context)
                                    if (!tutorialSeen) {
                                        showTutorial = true
                                        scope.launch { repository.setTutorialSeen(true) }
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Start Floating Button")
                        }

                        OutlinedButton(
                            onClick = { OverlayService.stop(context) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Stop Floating Button")
                        }
                    }

                    TextButton(
                        onClick = { showTutorial = true },
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    ) {
                        Text("Open Tutorial")
                    }
                }
            }

            // 3. Quick Setup Card
            Card {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Quick Setup", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))

                    ListItem(
                        headlineContent = { Text("Target Language") },
                        supportingContent = { Text(targetLanguage.uppercase()) },
                        trailingContent = { Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Select Language") },
                        modifier = Modifier.clickable { onNavigateToLanguageSelector() }
                    )
                    
                    ListItem(
                        headlineContent = { Text("Reading Mode") },
                        supportingContent = { Text("Optimized for Webtoons and Comics") },
                        trailingContent = { 
                            Switch(
                                checked = isReadingMode, 
                                onCheckedChange = { checked ->
                                    scope.launch { repository.setReadingMode(checked) }
                                }
                            ) 
                        }
                    )

                    ListItem(
                        headlineContent = { Text("Button Size") },
                        supportingContent = { Text(floatingButtonSize) },
                        modifier = Modifier.clickable {
                            val nextSize = when(floatingButtonSize) {
                                "Small" -> "Medium"
                                "Medium" -> "Large"
                                else -> "Small"
                            }
                            scope.launch { repository.setFloatingButtonSize(nextSize) }
                        }
                    )

                    ListItem(
                        headlineContent = { Text("Overlay Opacity") },
                        supportingContent = { Text("${(overlayOpacity * 100).toInt()}%") },
                        modifier = Modifier.clickable {
                            val next = if (overlayOpacity >= 1.0f) 0.2f else overlayOpacity + 0.2f
                            scope.launch { repository.setOverlayOpacity(next) }
                        }
                    )
                }
            }

            // 4. Privacy & Permissions Card
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Info, contentDescription = "Privacy Info", tint = MaterialTheme.colorScheme.onSecondaryContainer)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Privacy & Permissions",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "• Overlay Permission: Required to show the floating button and translation boxes.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "• Screen Capture: Requested ONLY when you tap the button to capture a single frame. We do not record continuously.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "• Processing: Screenshots are never saved. Text is processed securely on-device.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "• Internet: Used strictly to download official ML Kit language models without uploading any contents.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }
    }
}

@Composable
fun TutorialDialog(onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(16.dp))
                Text("Welcome to OverRead", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    "OverRead translates text right on your screen without leaving the app you are using.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(16.dp))
                
                Text("• Single Capture: When you tap the floating button, Android will prompt you for screen capture permission to capture a single frame. We do NOT record continuously.", style = MaterialTheme.typography.bodySmall)
                Spacer(modifier = Modifier.height(8.dp))
                
                Text("• Privacy First: The captured frame is processed locally on your device and instantly deleted. No screenshots are saved or uploaded.", style = MaterialTheme.typography.bodySmall)
                Spacer(modifier = Modifier.height(8.dp))
                
                Text("• Offline Translation: Internet is used strictly to download official Google ML Kit language models. Translation runs entirely on your device.", style = MaterialTheme.typography.bodySmall)
                
                Spacer(modifier = Modifier.height(24.dp))
                Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text("Got it")
                }
            }
        }
    }
}
