package com.pixeltranslator.multi

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.ui.draw.scale
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pixeltranslator.multi.ml.GemmaTranslatorManager.ModelSize
import com.pixeltranslator.multi.ui.AboutText
import com.pixeltranslator.multi.ui.ConversationTurn
import com.pixeltranslator.multi.ui.Disclaimer
import com.pixeltranslator.multi.ui.Language
import com.pixeltranslator.multi.ui.Strings
import com.pixeltranslator.multi.ui.TranslatorViewModel
import com.pixeltranslator.multi.ui.UiStrings

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    TranslatorScreen()
                }
            }
        }
    }
}

@Composable
fun TranslatorScreen(viewModel: TranslatorViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var previewPhotoJpeg by remember { mutableStateOf<ByteArray?>(null) }

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
    }

    // Android PhotoPicker — no storage permission required on API 33+.
    // Returns a content URI we hand to the ViewModel to stream into Gemma's
    // vision encoder.
    val photoPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) viewModel.processImage(uri)
    }

    // System-camera capture via TakePicturePreview. Runs the platform camera
    // app in its own process, so we don't need the CAMERA runtime permission.
    // Returns a thumbnail-resolution Bitmap — fine for sign/menu OCR since
    // Gemma's vision encoder downsamples anyway. Upgrade to TakePicture +
    // FileProvider if full-res becomes necessary.
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicturePreview()
    ) { bmp: android.graphics.Bitmap? ->
        if (bmp != null) {
            val stream = java.io.ByteArrayOutputStream()
            bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, stream)
            viewModel.processImageBytes(stream.toByteArray())
        }
    }

    LaunchedEffect(Unit) {
        if (!hasPermission) {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    // Re-check all-files-access each time the app resumes so returning from
    // the Settings page kicks the model load without a manual button tap.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.tryLoadModels()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (uiState.needsStoragePermission) {
        StorageAccessScreen(
            onGrant = {
                val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                    }
                } else {
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                    }
                }
                context.startActivity(intent)
            }
        )
        return
    }

    if (uiState.showDisclaimer) {
        DisclaimerScreen(
            strings = Strings.forLanguage(uiState.languageA),
            languageA = uiState.languageA,
            languageB = uiState.languageB,
            ttsAvailableForA = uiState.ttsAvailableForA,
            ttsAvailableForB = uiState.ttsAvailableForB,
            onPlayAloud = viewModel::playDisclaimerAloud,
            onDismiss = viewModel::dismissDisclaimer
        )
        return
    }

    if (uiState.showSettings) {
        SettingsScreen(
            strings = Strings.forLanguage(uiState.languageA),
            currentLanguage = uiState.languageA,
            onLanguageSelect = viewModel::setLanguageA,
            isAutoDetect = uiState.isAutoDetect,
            isDirectTranslation = uiState.isDirectTranslation,
            isExplicitDirection = uiState.isExplicitDirection,
            isAutoStopMic = uiState.isAutoStopMic,
            onSetAutoDetect = viewModel::setAutoDetect,
            onSetDirectTranslation = viewModel::setDirectTranslation,
            onSetExplicitDirection = viewModel::setExplicitDirection,
            onSetAutoStopMic = viewModel::setAutoStopMic,
            onDismiss = viewModel::closeSettings
        )
        return
    }

    if (uiState.showAbout) {
        AboutScreen(
            strings = Strings.forLanguage(uiState.languageA),
            language = uiState.languageA,
            onDismiss = viewModel::dismissAbout
        )
        return
    }

    val strings = Strings.forLanguage(uiState.languageA)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(horizontal = 16.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top action row — utility navigation. Sits above the model
        // selector because users tap About / Settings / Clear less often
        // than they switch model variants, but those actions live in the
        // same "chrome" zone above the conversation surface.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = viewModel::showAbout,
                modifier = Modifier.weight(1f)
            ) {
                Text(strings.aboutButton, maxLines = 1)
            }
            OutlinedButton(
                onClick = viewModel::showDisclaimer,
                modifier = Modifier.weight(1f)
            ) {
                Text(strings.disclaimerButton, maxLines = 1)
            }
            OutlinedButton(
                onClick = viewModel::openSettings,
                modifier = Modifier.weight(1f)
            ) {
                Text(strings.settingsButton, maxLines = 1)
            }
            OutlinedButton(
                onClick = viewModel::clearConversation,
                modifier = Modifier.weight(1f)
            ) {
                Text(strings.clearButton, maxLines = 1)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Model selector — equal-width chips, centered text.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ModelSize.entries.forEach { model ->
                FilterChip(
                    selected = uiState.currentModel == model,
                    onClick = { viewModel.switchModel(model) },
                    label = {
                        val label = if (model == ModelSize.E2B) strings.modelQuick else strings.modelHighQuality
                        Text(
                            label,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Status row only renders when something is actually happening or
        // there's an error to surface. The idle "Ready" message was visual
        // noise wedged between the action rows; the hold-to-speak label
        // below the mic already conveys idle state contextually.
        val showStatus = uiState.isProcessing ||
            uiState.status.startsWith("Error", ignoreCase = true)
        Spacer(modifier = Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(20.dp),
            contentAlignment = Alignment.Center
        ) {
            if (showStatus) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (uiState.isProcessing) {
                        androidx.compose.material3.CircularProgressIndicator(
                            modifier = Modifier.size(12.dp),
                            strokeWidth = 1.5.dp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text(
                        text = localizeStatus(uiState.status, strings),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Language dropdowns sit directly above the conversation card —
        // selection context is most relevant when reading the turns they
        // govern, so co-locating them removes a glance up to a header.
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LanguageDropdown(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
                current = uiState.languageA,
                disabled = uiState.languageB,
                ttsAvailable = uiState.ttsAvailableForA,
                onSelect = viewModel::setLanguageA,
                enabled = !uiState.isAutoDetect
            )
            LanguageDropdown(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
                current = uiState.languageB,
                disabled = uiState.languageA,
                ttsAvailable = uiState.ttsAvailableForB,
                onSelect = viewModel::setLanguageB,
                enabled = !uiState.isAutoDetect
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            val scrollState = rememberScrollState()
            // Key on the full turns list (not just its size) so content
            // mutations trigger a scroll too. The photo pipeline seeds a
            // placeholder turn with the thumbnail, then swaps in the
            // transcription + translation when inference returns; the list
            // size stays the same but the rendered content grows, so we
            // need the re-scroll on that mutation to keep the user in view.
            LaunchedEffect(uiState.turns) {
                scrollState.animateScrollTo(scrollState.maxValue)
            }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .verticalScroll(scrollState)
            ) {
                if (uiState.turns.isEmpty()) {
                    Text(
                        text = if (uiState.isAutoDetect) {
                            "Auto-detect mode.\nSpeak any supported language — translation will be in ${uiState.languageA.displayName}."
                        } else {
                            strings.emptyPlaceholder(
                                uiState.languageA.nativeName,
                                uiState.languageB.nativeName
                            )
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    uiState.turns.forEachIndexed { index, turn ->
                        val canReplayTurn = canReplayTurn(
                            turn = turn,
                            ttsAvailableForA = uiState.ttsAvailableForA,
                            ttsAvailableForB = uiState.ttsAvailableForB,
                            languageA = uiState.languageA,
                            languageB = uiState.languageB
                        )
                        ConversationBubble(
                            turn = turn,
                            strings = strings,
                            canReplay = canReplayTurn,
                            isReplaying = uiState.activeReplayTurnIndex == index,
                            onReplayClick = { viewModel.replayTurn(index, turn) },
                            onPhotoClick = { previewPhotoJpeg = it }
                        )
                        if (index < uiState.turns.lastIndex) {
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Mic row on its own. Paired mode defaults to one mic. Explicit
        // direction restores two target-specific mics for higher-control
        // workflows, with or without direct translation.
        val showDualMic = uiState.isExplicitDirection && !uiState.isAutoDetect
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = if (showDualMic) Arrangement.spacedBy(8.dp) else Arrangement.Center
        ) {
            if (showDualMic) {
	                PushToTalkButton(
	                    isRecording = uiState.isRecording && uiState.pendingDirectTarget == uiState.languageB,
	                    enabled = hasPermission && uiState.isModelLoaded && !uiState.isProcessing &&
	                        (!uiState.isRecording || uiState.pendingDirectTarget == uiState.languageB),
	                    autoStop = uiState.isAutoStopMic,
	                    label = "${uiState.languageA.code.uppercase()}→${uiState.languageB.code.uppercase()}",
	                    wide = true,
	                    modifier = Modifier.weight(1f),
                    onPressStart = { viewModel.onPushToTalkPressed(uiState.languageB) },
                    onPressEnd = viewModel::onPushToTalkReleased
                )
	                PushToTalkButton(
	                    isRecording = uiState.isRecording && uiState.pendingDirectTarget == uiState.languageA,
	                    enabled = hasPermission && uiState.isModelLoaded && !uiState.isProcessing &&
	                        (!uiState.isRecording || uiState.pendingDirectTarget == uiState.languageA),
	                    autoStop = uiState.isAutoStopMic,
	                    label = "${uiState.languageB.code.uppercase()}→${uiState.languageA.code.uppercase()}",
	                    wide = true,
	                    modifier = Modifier.weight(1f),
                    onPressStart = { viewModel.onPushToTalkPressed(uiState.languageA) },
                    onPressEnd = viewModel::onPushToTalkReleased
                )
            } else {
	                PushToTalkButton(
	                    isRecording = uiState.isRecording,
	                    enabled = hasPermission && uiState.isModelLoaded && !uiState.isProcessing,
	                    autoStop = uiState.isAutoStopMic,
	                    onPressStart = { viewModel.onPushToTalkPressed() },
	                    onPressEnd = viewModel::onPushToTalkReleased
	                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Image-translation entry points. Left: capture a fresh photo via
        // the system camera. Right: pick an existing image from the gallery.
        // Both flow into the same ViewModel → Gemma OCR+translate pipeline.
        // Disabled while a turn is in flight so we don't race the Engine.
        val imageButtonsEnabled =
            uiState.isModelLoaded && !uiState.isProcessing && !uiState.isRecording
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = { cameraLauncher.launch(null) },
                enabled = imageButtonsEnabled,
                modifier = Modifier.weight(1f)
            ) {
                Text(strings.takePhotoButton, maxLines = 1)
            }
            OutlinedButton(
                onClick = {
                    photoPickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                enabled = imageButtonsEnabled,
                modifier = Modifier.weight(1f)
            ) {
                Text(strings.uploadPhotoButton, maxLines = 1)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = when {
                !hasPermission -> strings.micPermissionRequired
                !uiState.isModelLoaded -> strings.loadingModels
                uiState.isRecording -> strings.statusListening
                uiState.isAutoStopMic -> strings.tapToSpeak
                else -> strings.holdToSpeak
            },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

    }

    previewPhotoJpeg?.let { jpeg ->
        PhotoPreviewDialog(
            jpeg = jpeg,
            onDismiss = { previewPhotoJpeg = null }
        )
    }
}

@Composable
private fun PhotoPreviewDialog(
    jpeg: ByteArray,
    onDismiss: () -> Unit
) {
    val bmp = remember(jpeg) {
        android.graphics.BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.92f)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
                    .padding(16.dp)
            ) {
                if (bmp != null) {
                    androidx.compose.foundation.Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "Photo preview",
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = 64.dp),
                        contentScale = ContentScale.Fit
                    )
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(48.dp)
                        .background(
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                            shape = CircleShape
                        )
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "×",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) {
                    Text("Close")
                }
            }
        }
    }
}

/**
 * Full-screen Settings modal. Houses the per-turn mode toggles that used
 * to live on the main screen's bottom strip. Kept separate so the main
 * screen can give the paired+direct dual-mic layout room to breathe.
 */
@Composable
private fun SettingsScreen(
    strings: UiStrings,
    currentLanguage: Language,
    onLanguageSelect: (Language) -> Unit,
    isAutoDetect: Boolean,
    isDirectTranslation: Boolean,
    isExplicitDirection: Boolean,
    isAutoStopMic: Boolean,
    onSetAutoDetect: (Boolean) -> Unit,
    onSetDirectTranslation: (Boolean) -> Unit,
    onSetExplicitDirection: (Boolean) -> Unit,
    onSetAutoStopMic: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    // Outer column: header + scrollable middle + Done button. The Done
    // button stays anchored at the bottom regardless of how much
    // instructions content the user is reading. Middle scrolls so the
    // localized instructions don't push the toggles or the button off
    // screen on shorter devices or with longer translations.
    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(horizontal = 24.dp, vertical = 24.dp)
    ) {
        Text(
            text = strings.settingsButton,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(24.dp))

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            // App language picker — drives the UI chrome AND the target
            // language for auto-detect mode. Same value as the A dropdown
            // on the main screen; placing a copy here makes it more
            // discoverable as the "app language" concept rather than
            // just the A side of a paired conversation.
            Text(
                text = strings.appLanguageTitle,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = strings.appLanguageSubtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            LanguageDropdown(
                current = currentLanguage,
                disabled = null,
                ttsAvailable = true,
                onSelect = onLanguageSelect,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(28.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(20.dp))

            SettingRow(
                checked = isAutoDetect,
                onCheckedChange = onSetAutoDetect,
                title = strings.autoDetectTitle,
                subtitle = strings.autoDetectSubtitle
            )
            Spacer(modifier = Modifier.height(20.dp))
            SettingRow(
                checked = isDirectTranslation,
                onCheckedChange = onSetDirectTranslation,
                title = strings.directTranslationTitle,
                subtitle = strings.directTranslationSubtitle
            )
            Spacer(modifier = Modifier.height(20.dp))
            SettingRow(
                checked = isAutoStopMic,
                onCheckedChange = onSetAutoStopMic,
                title = strings.autoStopMicTitle,
                subtitle = strings.autoStopMicSubtitle
            )
            Spacer(modifier = Modifier.height(20.dp))
            SettingRow(
                checked = isExplicitDirection,
                onCheckedChange = onSetExplicitDirection,
                title = strings.explicitDirectionTitle,
                subtitle = if (isAutoDetect) {
                    strings.explicitDirectionDisabledSubtitle
                } else {
                    strings.explicitDirectionSubtitle
                },
                enabled = !isAutoDetect
            )

            Spacer(modifier = Modifier.height(28.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = strings.instructionsTitle,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = strings.instructionsBody,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 20.sp
            )

            Spacer(modifier = Modifier.height(20.dp))
        }

        androidx.compose.material3.Button(
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(strings.closeButton)
        }
    }
}

@Composable
private fun SettingRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    title: String,
    subtitle: String,
    enabled: Boolean = true
) {
    val contentColor = if (enabled) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        androidx.compose.material3.Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = contentColor
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Maps the ViewModel's English status strings to their localized equivalent
 * based on Language A. Unknown strings (dynamic errors, adb-push messages)
 * pass through unchanged since they're diagnostic, not user-facing copy.
 */
private fun localizeStatus(raw: String, s: UiStrings): String = when {
    raw == "Ready" -> s.statusReady
    raw == "Initializing..." -> s.statusLoading
    raw == "Loading models..." -> s.loadingModels
    raw == "Listening..." -> s.statusListening
    raw == "Translating..." -> s.statusTranslating
    raw == "Speaking..." -> s.statusSpeaking
    raw == "Ready (no voice installed)" -> s.statusNoVoice
    raw == "Grant all-files access to load models" -> s.loadingModels
    raw.startsWith("Loading ") -> s.loadingModels
    else -> raw  // errors, model-not-found, etc. — pass through verbatim
}

@Composable
private fun LanguageDropdown(
    current: Language,
    disabled: Language?,
    ttsAvailable: Boolean,
    onSelect: (Language) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = { expanded = true },
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = 8.dp, vertical = 4.dp
                )
            ) {
                // English name on top, native name below (when different).
                // Always render the second line — when the native name matches
                // the English one (e.g. "English" / "English"), use a blank
                // placeholder so the button height stays consistent across
                // languages. Otherwise English's button collapses to half
                // the height of every other language's, which reads as a
                // glitch when scanning the row.
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        current.displayName,
                        fontSize = 12.sp,
                        maxLines = 1
                    )
                    Text(
                        text = if (current.displayName != current.nativeName) current.nativeName else " ",
                        fontSize = 10.sp,
                        maxLines = 1
                    )
                }
                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                Language.entries.forEach { lang ->
                    DropdownMenuItem(
                        text = {
                            Text("${lang.displayName}  —  ${lang.nativeName}")
                        },
                        enabled = disabled == null || lang != disabled,
                        onClick = {
                            onSelect(lang)
                            expanded = false
                        }
                    )
                }
            }
        }
        if (!ttsAvailable) {
            Text(
                text = "no voice",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
private fun PushToTalkButton(
    isRecording: Boolean,
    enabled: Boolean,
    autoStop: Boolean,
    onPressStart: () -> Unit,
    onPressEnd: () -> Unit,
    label: String? = null,
    wide: Boolean = false,
    modifier: Modifier = Modifier
) {
    val buttonColor = when {
        !enabled -> MaterialTheme.colorScheme.surfaceVariant
        isRecording -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.primary
    }
    val textColor = when {
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant
        isRecording -> MaterialTheme.colorScheme.onError
        else -> MaterialTheme.colorScheme.onPrimary
    }

    Box(
        modifier = modifier
            .then(
                if (wide) Modifier.height(76.dp)
                else Modifier.size(96.dp)
            )
            .background(
                color = buttonColor,
                shape = if (wide) RoundedCornerShape(36.dp) else CircleShape
            )
            .then(
                if (enabled) {
                    if (autoStop) {
                        Modifier.clickable {
                            if (isRecording) onPressEnd() else onPressStart()
                        }
                    } else {
                        Modifier.pointerInput(Unit) {
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false)
                                onPressStart()
                                waitForUpOrCancellation()
                                onPressEnd()
                            }
                        }
                    }
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        if (autoStop && isRecording) {
            if (wide) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Stop,
                        contentDescription = "Stop recording",
                        tint = textColor,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "STOP",
                        color = textColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            } else {
                Icon(
                    imageVector = Icons.Default.Stop,
                    contentDescription = "Stop recording",
                    tint = textColor,
                    modifier = Modifier.size(34.dp)
                )
            }
        } else {
            Text(
                text = when {
                    isRecording -> "REC"
                    label != null -> label
                    else -> "MIC"
                },
                color = textColor,
                fontWeight = FontWeight.Bold,
                fontSize = if (label != null) 14.sp else 16.sp
            )
        }
    }
}

@Composable
private fun ConversationBubble(
    turn: ConversationTurn,
    strings: UiStrings,
    canReplay: Boolean,
    isReplaying: Boolean,
    onReplayClick: () -> Unit,
    onPhotoClick: (ByteArray) -> Unit
) {
    SelectionContainer {
        Column(modifier = Modifier.fillMaxWidth()) {
            val sourceLabel = "${turn.sourceDisplayName} → ${turn.targetLanguage.displayName}"
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = sourceLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                if (canReplay) {
                    IconButton(
                        onClick = onReplayClick,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = if (isReplaying) {
                                Icons.Default.Stop
                            } else {
                                Icons.AutoMirrored.Filled.VolumeUp
                            },
                            contentDescription = if (isReplaying) {
                                "Stop playback"
                            } else {
                                "Replay translation"
                            },
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
            // Turn whose detected language wasn't in the configured pair.
            // Translation may still work through Gemma, but flag it so the
            // user knows the source isn't what they configured.
            if (turn.outOfPairLanguageName != null) {
                Text(
                    text = strings.outOfPairWarning(turn.outOfPairLanguageName),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            // Direct-translation turns have no transcription step and thus
            // no source-language detection — the "not a conversation pair
            // language" warning would be misleading there. Suppress it on
            // empty-transcription turns.
            if (turn.sourceLanguage == null && turn.transcription.isNotEmpty()) {
                // Out-of-set language: recognized for translation but not a
                // paired-mode conversation language in this build. Note: does
                // NOT repeat the quality-unverified warning — that's its own
                // label below and carries the stronger claim.
                Text(
                    text = "(recognized, not a conversation pair language)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            // Image-translation thumbnail (set only for photo turns). Decode
            // from the compressed JPEG bytes stored on the turn. Small
            // fixed-width cap so long signs don't blow out row height.
            val photoJpeg = turn.thumbnailJpeg
            if (photoJpeg != null) {
                val bmp = remember(photoJpeg) {
                    android.graphics.BitmapFactory.decodeByteArray(
                        photoJpeg, 0, photoJpeg.size
                    )
                }
                if (bmp != null) {
                    androidx.compose.foundation.Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "Photo source",
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 160.dp)
                            .clickable { onPhotoClick(photoJpeg) },
                        contentScale = ContentScale.Fit
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                }
            }
            if (turn.transcription.isNotEmpty()) {
                Text(
                    text = turn.transcription,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
            }
            Text(
                text = turn.translation,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                fontSize = 18.sp
            )
            // Four quality-warning signals. Each fires independently and
            // carries a different message. Most serious / specific first.
            if (turn.unexpectedEnglish) {
                // Auto-detect found English when it should have found another
                // language. Most likely Gemma's audio encoder hallucinated.
                Text(
                    text = "(unexpected English — audio may not have been understood)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            if (turn.translationSuspect) {
                // Output-side language check disagreed with the target language.
                Text(
                    text = "(output sanity check failed — translation may not be correct)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            if (turn.qualityUnverified) {
                // Translating through a language outside our verified 13.
                // Gemma handled it, but we haven't benchmarked quality.
                Text(
                    text = "(translation quality unverified for ${turn.sourceDisplayName})",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            if (turn.lowConfidence) {
                Text(
                    text = "(low detection confidence — language identification uncertain)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (turn.confusableSink && turn.sourceLanguage != null) {
                // Soft warning: Gemma's audio encoder tends to absorb sibling
                // languages into this one. Detection MAY still be correct;
                // just flag for operator sanity-check if they expected a
                // close-cousin language.
                val neighbors = Language.confusableNeighbors(turn.sourceLanguage).joinToString(", ")
                Text(
                    text = "(auto-detect may confuse ${turn.sourceDisplayName} with related languages such as $neighbors — use paired mode if you know the speaker's language)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            // "Text only" hint only makes sense for voice turns where TTS
            // was expected but unavailable. Image turns are always
            // text-only by design, so showing it there is misleading noise.
            if (!turn.spokenAloud && !turn.isImageTurn) {
                Text(
                    text = "(text only — no TTS voice installed)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun canReplayTurn(
    turn: ConversationTurn,
    ttsAvailableForA: Boolean,
    ttsAvailableForB: Boolean,
    languageA: Language,
    languageB: Language
): Boolean {
    val translation = turn.translation.trim()
    if (turn.isImageTurn) return false
    if (translation.isBlank()) return false
    if (translation.equals("(translating...)", ignoreCase = true)) return false
    if (translation.equals("(translation unavailable)", ignoreCase = true)) return false
    if (translation.startsWith("Error:", ignoreCase = true)) return false

    return when (turn.targetLanguage) {
        languageA -> ttsAvailableForA
        languageB -> ttsAvailableForB
        else -> true
    }
}

@Composable
private fun DisclaimerScreen(
    strings: UiStrings,
    languageA: Language,
    languageB: Language,
    ttsAvailableForA: Boolean,
    ttsAvailableForB: Boolean,
    onPlayAloud: (Language) -> Unit,
    onDismiss: () -> Unit
) {
    // Which language's disclaimer text is shown on screen. Defaults to A.
    var shownIn by remember { mutableStateOf(languageA) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = strings.disclaimerScreenTitle,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            listOf(languageA, languageB).distinct().forEach { lang ->
                FilterChip(
                    selected = shownIn == lang,
                    onClick = { shownIn = lang },
                    label = { Text(lang.nativeName, fontSize = 13.sp) },
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            SelectionContainer {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = Disclaimer.textFor(shownIn),
                        style = MaterialTheme.typography.bodyLarge,
                        fontSize = 17.sp,
                        lineHeight = 26.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = strings.playAloudLabel,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(4.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            OutlinedButton(
                onClick = { onPlayAloud(languageA) },
                enabled = ttsAvailableForA
            ) {
                Text(languageA.nativeName)
            }
            OutlinedButton(
                onClick = { onPlayAloud(languageB) },
                enabled = ttsAvailableForB
            ) {
                Text(languageB.nativeName)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        androidx.compose.material3.Button(onClick = onDismiss) {
            Text(strings.beginButton)
        }

        Spacer(modifier = Modifier.height(12.dp))
    }
}

@Composable
private fun AboutScreen(
    strings: UiStrings,
    language: Language,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = strings.aboutScreenTitle,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = strings.aboutScreenSubtitle,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(12.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            SelectionContainer {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = AboutText.textFor(language),
                        style = MaterialTheme.typography.bodyLarge,
                        fontSize = 16.sp,
                        lineHeight = 24.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        androidx.compose.material3.Button(onClick = onDismiss) {
            Text(strings.closeButton)
        }

        Spacer(modifier = Modifier.height(12.dp))
    }
}

@Composable
private fun StorageAccessScreen(onGrant: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "All-files access required",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "This app reads Gemma model weights from /sdcard/Download/litertlm-models/ so they can be shared with the bilingual translator app instead of duplicating multi-GB files. Android requires a one-time toggle for that access.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))
        androidx.compose.material3.Button(onClick = onGrant) {
            Text("Open Settings")
        }
    }
}
