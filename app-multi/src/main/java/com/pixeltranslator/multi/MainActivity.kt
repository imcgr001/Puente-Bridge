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
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
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
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
            isAutoDetect = uiState.isAutoDetect,
            isDirectTranslation = uiState.isDirectTranslation,
            onSetAutoDetect = viewModel::setAutoDetect,
            onSetDirectTranslation = viewModel::setDirectTranslation,
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
        Text(
            text = strings.title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

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

        Spacer(modifier = Modifier.height(8.dp))

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
                Text("Settings", maxLines = 1)
            }
            OutlinedButton(
                onClick = viewModel::clearConversation,
                modifier = Modifier.weight(1f)
            ) {
                Text(strings.clearButton, maxLines = 1)
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

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

        Spacer(modifier = Modifier.height(12.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            val scrollState = rememberScrollState()
            LaunchedEffect(uiState.turns.size) {
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
                            "Auto-detect mode.\nSpeak any supported language — translation will be in English."
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
                        ConversationBubble(turn)
                        if (index < uiState.turns.lastIndex) {
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Language dropdowns on their own row, side by side. Keeping them
        // separate from the mic row lets the mic area breathe and keeps
        // the dual-mic (paired+direct) layout symmetric.
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

        Spacer(modifier = Modifier.height(16.dp))

        // Mic row on its own. Single centered mic in normal mode; in
        // paired+direct mode, two direction-specific mics flank the row
        // edges (left = A→B, right = B→A) so the operator and speaker
        // each have an obvious button on their side of the phone.
        val showDualMic = uiState.isDirectTranslation && !uiState.isAutoDetect
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = if (showDualMic) Arrangement.SpaceBetween else Arrangement.Center
        ) {
            if (showDualMic) {
                PushToTalkButton(
                    isRecording = uiState.isRecording && uiState.pendingDirectTarget == uiState.languageB,
                    enabled = hasPermission && uiState.isModelLoaded && !uiState.isProcessing,
                    label = "${uiState.languageA.code.uppercase()}→${uiState.languageB.code.uppercase()}",
                    onPressStart = { viewModel.onPushToTalkPressed(uiState.languageB) },
                    onPressEnd = viewModel::onPushToTalkReleased
                )
                PushToTalkButton(
                    isRecording = uiState.isRecording && uiState.pendingDirectTarget == uiState.languageA,
                    enabled = hasPermission && uiState.isModelLoaded && !uiState.isProcessing,
                    label = "${uiState.languageB.code.uppercase()}→${uiState.languageA.code.uppercase()}",
                    onPressStart = { viewModel.onPushToTalkPressed(uiState.languageA) },
                    onPressEnd = viewModel::onPushToTalkReleased
                )
            } else {
                PushToTalkButton(
                    isRecording = uiState.isRecording,
                    enabled = hasPermission && uiState.isModelLoaded && !uiState.isProcessing,
                    onPressStart = { viewModel.onPushToTalkPressed() },
                    onPressEnd = viewModel::onPushToTalkReleased
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = when {
                !hasPermission -> strings.micPermissionRequired
                !uiState.isModelLoaded -> strings.loadingModels
                uiState.isRecording -> strings.statusListening
                else -> strings.holdToSpeak
            },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

    }
}

/**
 * Full-screen Settings modal. Houses the per-turn mode toggles that used
 * to live on the main screen's bottom strip. Kept separate so the main
 * screen can give the paired+direct dual-mic layout room to breathe.
 */
@Composable
private fun SettingsScreen(
    isAutoDetect: Boolean,
    isDirectTranslation: Boolean,
    onSetAutoDetect: (Boolean) -> Unit,
    onSetDirectTranslation: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(horizontal = 24.dp, vertical = 24.dp)
    ) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(24.dp))

        SettingRow(
            checked = isAutoDetect,
            onCheckedChange = onSetAutoDetect,
            title = "Auto-detect language",
            subtitle = "Open-set language identification. Translates anything spoken into English — text only, no speech."
        )
        Spacer(modifier = Modifier.height(20.dp))
        SettingRow(
            checked = isDirectTranslation,
            onCheckedChange = onSetDirectTranslation,
            title = "Direct translation",
            subtitle = "Faster audio → translation in one step, no transcription shown. In paired mode, two direction-specific mics replace the single mic."
        )

        Spacer(modifier = Modifier.weight(1f))

        androidx.compose.material3.Button(
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Done")
        }
    }
}

@Composable
private fun SettingRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    title: String,
    subtitle: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        androidx.compose.material3.Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold
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
    disabled: Language,
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
                // Two-line stack avoids squeezing long dual-name strings like
                // "Vietnamese · Tiếng Việt" horizontally into a narrow slot.
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        current.displayName,
                        fontSize = 12.sp,
                        maxLines = 1
                    )
                    if (current.displayName != current.nativeName) {
                        Text(
                            current.nativeName,
                            fontSize = 10.sp,
                            maxLines = 1
                        )
                    }
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
                        enabled = lang != disabled,
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
    onPressStart: () -> Unit,
    onPressEnd: () -> Unit,
    label: String? = null
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
        modifier = Modifier
            .size(96.dp)
            .background(buttonColor, CircleShape)
            .then(
                if (enabled) {
                    Modifier.pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            onPressStart()
                            waitForUpOrCancellation()
                            onPressEnd()
                        }
                    }
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = when {
                isRecording -> "REC"
                label != null -> label
                else -> "MIC"
            },
            color = textColor,
            fontWeight = FontWeight.Bold,
            fontSize = if (label != null) 13.sp else 16.sp
        )
    }
}

@Composable
private fun ConversationBubble(turn: ConversationTurn) {
    SelectionContainer {
        Column(modifier = Modifier.fillMaxWidth()) {
            val sourceLabel = "${turn.sourceDisplayName} → ${turn.targetLanguage.displayName}"
            Text(
                text = sourceLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
                // Output-side ML Kit check disagreed with the target language.
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
            if (!turn.spokenAloud) {
                Text(
                    text = "(text only — no TTS voice installed)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
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
