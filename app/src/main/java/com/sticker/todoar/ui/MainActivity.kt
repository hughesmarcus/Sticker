package com.sticker.todoar.ui

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Bundle
import android.text.format.DateFormat
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.sticker.todoar.domain.TodoSticker
import com.sticker.todoar.ui.stickers.RoomPlacementRequest
import com.sticker.todoar.ui.stickers.StickerUiState
import com.sticker.todoar.ui.stickers.StickerViewModel
import com.sticker.todoar.xr.XrPlacementResult
import com.sticker.todoar.xr.XrStickerScene
import dagger.hilt.android.AndroidEntryPoint
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.xr.runtime.AnchorPersistenceMode
import androidx.xr.runtime.Config
import androidx.xr.runtime.DeviceTrackingMode
import androidx.xr.runtime.PlaneTrackingMode
import androidx.xr.runtime.Session
import androidx.xr.runtime.SessionConfigureSuccess
import androidx.xr.runtime.SessionCreateApkRequired
import androidx.xr.runtime.SessionCreateSuccess
import androidx.xr.runtime.SessionCreateUnsupportedDevice
import androidx.xr.scenecore.scene
import androidx.xr.runtime.math.FloatSize2d
import androidx.xr.runtime.math.FloatSize3d
import androidx.xr.scenecore.MovableComponent
import com.sticker.todoar.R
import java.util.Date

private const val DEFAULT_MAIN_PANEL_SCALE = 1f
private const val MIN_MAIN_PANEL_SCALE = 0.75f
private const val MAX_MAIN_PANEL_SCALE = 1.5f
private const val MAIN_PANEL_SCALE_STEP = 0.1f

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: StickerViewModel by viewModels()
    private var xrStickerScene: XrStickerScene? = null
    private var xrSession: Session? = null
    private var mainPanelMovableComponent: MovableComponent? = null
    private var isMainPanelMinimized = false
    private var mainPanelScale = DEFAULT_MAIN_PANEL_SCALE
    private var toneGenerator: ToneGenerator? = null
    private var scheduledSystemAlarmAtMillis: Long? = null
    private var alarmCollectionInitialized = false
    private var pendingAlarmLaunch = false
    private val alarmedStickerIds = mutableSetOf<Long>()

    private val scenePermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                startXrSession()
            } else {
                viewModel.onXrStatusChanged(UiText.resource(R.string.status_scene_permission_needed))
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingAlarmLaunch = isAlarmLaunchIntent(intent)
        isMainPanelMinimized = savedInstanceState?.getBoolean(STATE_MAIN_PANEL_MINIMIZED) ?: false
        mainPanelScale = savedInstanceState?.getFloat(STATE_MAIN_PANEL_SCALE) ?: loadMainPanelScale()
        setContent {
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            var mainPanelMinimized by rememberSaveable { mutableStateOf(isMainPanelMinimized) }
            var mainPanelScaleState by rememberSaveable { mutableStateOf(mainPanelScale) }
            LaunchedEffect(mainPanelMinimized, mainPanelScaleState) {
                setMainPanelState(mainPanelMinimized, mainPanelScaleState)
            }
            StickerTheme {
                StickerScreen(
                    state = uiState,
                    mainPanelMinimized = mainPanelMinimized,
                    mainPanelScale = mainPanelScaleState,
                    onMainPanelMinimizedChange = { minimized ->
                        mainPanelMinimized = minimized
                    },
                    onMainPanelScaleChange = { scale ->
                        mainPanelScaleState = scale
                    },
                    onDraftChanged = viewModel::onDraftChanged,
                    onAlarmTimeChanged = viewModel::onAlarmTimeChanged,
                    onAlarmCleared = viewModel::onAlarmCleared,
                    onSaveClicked = viewModel::onSaveRequested,
                    onPlaceClicked = ::spawnDraftInRoom,
                    onBringNotesHere = ::bringNotesHere,
                    onEditSticker = viewModel::onStickerEdited,
                    onToggleSticker = viewModel::onStickerToggled,
                    onDeleteSticker = viewModel::onStickerRemoved
                )
            }
        }
        collectSpatialStickers()
        collectAlarmTriggers()
        ensureScenePermissionThenStart()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (isAlarmLaunchIntent(intent)) {
            pendingAlarmLaunch = true
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(STATE_MAIN_PANEL_MINIMIZED, isMainPanelMinimized)
        outState.putFloat(STATE_MAIN_PANEL_SCALE, mainPanelScale)
        super.onSaveInstanceState(outState)
    }

    private fun ensureScenePermissionThenStart() {
        if (checkSelfPermission(SCENE_UNDERSTANDING_PERMISSION) == PackageManager.PERMISSION_GRANTED) {
            startXrSession()
        } else {
            scenePermissionLauncher.launch(SCENE_UNDERSTANDING_PERMISSION)
        }
    }

    private fun startXrSession() {
        lifecycleScope.launch {
            val createResult = withContext(Dispatchers.IO) { Session.create(this@MainActivity) }
            when (createResult) {
                is SessionCreateSuccess -> configureXrSession(createResult.session)
                is SessionCreateApkRequired -> {
                    viewModel.onXrStatusChanged(UiText.resource(R.string.status_android_xr_runtime_required))
                }
                is SessionCreateUnsupportedDevice -> {
                    viewModel.onXrStatusChanged(UiText.resource(R.string.status_spatial_needs_android_xr))
                }
                else -> {
                    viewModel.onXrStatusChanged(UiText.resource(R.string.status_could_not_start_xr))
                }
            }
        }
    }

    private suspend fun configureXrSession(session: Session) {
        val config = Config(
            planeTracking = PlaneTrackingMode.HORIZONTAL_AND_VERTICAL,
            deviceTracking = DeviceTrackingMode.SPATIAL_LAST_KNOWN,
            anchorPersistence = AnchorPersistenceMode.LOCAL
        )
        val configureResult = withContext(Dispatchers.IO) { session.configure(config) }
        if (configureResult is SessionConfigureSuccess) {
            xrSession = session
            session.scene.requestFullSpaceMode()
            makeMainPanelMovable(session)
            applyMainPanelSize(session, isMainPanelMinimized, mainPanelScale)
            xrStickerScene = XrStickerScene(
                activity = this,
                session = session,
                scope = lifecycleScope,
                onToggleSticker = viewModel::onStickerToggled,
                onDeleteSticker = viewModel::onStickerRemoved,
                onUpdateStickerText = viewModel::onStickerTextUpdated,
                onUpdateStickerAlarm = viewModel::onStickerAlarmUpdated,
                onUpdateStickerSize = viewModel::onStickerSizeChanged,
                onActivitySpacePoseUpdated = viewModel::onStickerActivitySpacePoseUpdated,
                onAnchorUpdated = viewModel::onStickerAnchorUpdated,
                onStatus = { resId -> viewModel.onXrStatusChanged(UiText.resource(resId)) }
            )
            viewModel.onXrStatusChanged(UiText.resource(R.string.status_xr_room_ready))
        } else {
            viewModel.onXrStatusChanged(UiText.resource(R.string.status_could_not_configure_xr_anchors))
        }
    }

    private fun spawnDraftInRoom(text: String, fallbackText: String) {
        val request = viewModel.onPlaceRequested(text, fallbackText) ?: return
        lifecycleScope.launch {
            placeStickerInRoom(request)
        }
    }

    private suspend fun placeStickerInRoom(request: RoomPlacementRequest) {
        val scene = xrStickerScene
        if (scene == null) {
            viewModel.onRoomPlacementFailed(UiText.resource(R.string.status_xr_session_not_ready))
            return
        }

        when (val result = scene.createPersistentAnchorAtCurrentSpot()) {
            is XrPlacementResult.Success -> {
                viewModel.onRoomPlacementSucceeded(
                    text = request.text,
                    dueAtMillis = request.dueAtMillis,
                    anchorProvider = result.anchorProvider,
                    anchorId = result.anchorId,
                    activitySpacePose = result.activitySpacePose
                )
            }
            is XrPlacementResult.Failure -> {
                viewModel.onRoomPlacementFailed(UiText.resource(result.messageRes))
            }
        }
    }

    private fun makeMainPanelMovable(session: Session) {
        val movableComponent = MovableComponent.createSystemMovable(session).apply {
            size = FloatSize3d(MAIN_PANEL_MOVE_WIDTH_METERS, MAIN_PANEL_MOVE_HEIGHT_METERS, MAIN_PANEL_MOVE_DEPTH_METERS)
        }
        if (session.scene.mainPanelEntity.addComponent(movableComponent)) {
            mainPanelMovableComponent = movableComponent
        }
    }

    private fun setMainPanelState(minimized: Boolean, scale: Float) {
        val sanitizedScale = sanitizeMainPanelScale(scale)
        isMainPanelMinimized = minimized
        mainPanelScale = sanitizedScale
        saveMainPanelScale(sanitizedScale)
        xrSession?.let { session -> applyMainPanelSize(session, minimized, sanitizedScale) }
    }

    private fun applyMainPanelSize(session: Session, minimized: Boolean, scale: Float) {
        val sanitizedScale = sanitizeMainPanelScale(scale)
        val width = if (minimized) {
            MINIMIZED_PANEL_WIDTH_METERS
        } else {
            MAIN_PANEL_WIDTH_METERS * sanitizedScale
        }
        val height = if (minimized) {
            MINIMIZED_PANEL_HEIGHT_METERS
        } else {
            MAIN_PANEL_HEIGHT_METERS * sanitizedScale
        }
        session.scene.mainPanelEntity.size = FloatSize2d(width, height)
        mainPanelMovableComponent?.size = FloatSize3d(width, height, MAIN_PANEL_MOVE_DEPTH_METERS)
    }

    private fun loadMainPanelScale(): Float {
        val storedScale = getPreferences(Context.MODE_PRIVATE)
            .getFloat(PREF_MAIN_PANEL_SCALE, DEFAULT_MAIN_PANEL_SCALE)
        return sanitizeMainPanelScale(storedScale)
    }

    private fun saveMainPanelScale(scale: Float) {
        getPreferences(Context.MODE_PRIVATE)
            .edit()
            .putFloat(PREF_MAIN_PANEL_SCALE, scale)
            .apply()
    }

    private fun sanitizeMainPanelScale(scale: Float): Float =
        if (scale.isFinite()) {
            scale.coerceIn(MIN_MAIN_PANEL_SCALE, MAX_MAIN_PANEL_SCALE)
        } else {
            DEFAULT_MAIN_PANEL_SCALE
        }

    private fun bringNotesHere() {
        val scene = xrStickerScene
        if (scene == null) {
            viewModel.onXrStatusChanged(UiText.resource(R.string.status_xr_session_not_ready))
            return
        }
        val movedNotes = scene.bringTemporaryNotesIntoView()
        viewModel.onXrStatusChanged(
            UiText.resource(
                if (movedNotes) {
                    R.string.status_notes_brought_here
                } else {
                    R.string.status_no_session_notes_to_move
                }
            )
        )
    }

    private fun collectAlarmTriggers() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    val expiredAlarmIds = state.stickers
                        .filter { sticker -> sticker.isTimerExpired(state.nowMillis) }
                        .map { sticker -> sticker.id }
                        .toSet()
                    alarmedStickerIds.retainAll(expiredAlarmIds)
                    if (!alarmCollectionInitialized && !pendingAlarmLaunch) {
                        alarmedStickerIds += expiredAlarmIds
                    }

                    val dueSticker = state.stickers.firstOrNull { sticker ->
                        sticker.isTimerExpired(state.nowMillis) && sticker.id !in alarmedStickerIds
                    }
                    if (dueSticker != null) {
                        alarmedStickerIds += expiredAlarmIds
                        playAlarmTone()
                        viewModel.onAlarmTriggered(dueSticker.text)
                    }
                    alarmCollectionInitialized = true
                    pendingAlarmLaunch = false
                    scheduleNextSystemAlarm(state)
                }
            }
        }
    }

    private fun playAlarmTone() {
        val generator = toneGenerator ?: ToneGenerator(AudioManager.STREAM_ALARM, ALARM_VOLUME_PERCENT)
            .also { toneGenerator = it }
        lifecycleScope.launch {
            repeat(ALARM_TONE_REPEAT_COUNT) {
                generator.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, ALARM_TONE_MILLIS)
                kotlinx.coroutines.delay(ALARM_TONE_GAP_MILLIS)
            }
        }
    }

    private fun scheduleNextSystemAlarm(state: StickerUiState) {
        val nextAlarmAtMillis = state.stickers
            .asSequence()
            .filter { sticker -> !sticker.done }
            .mapNotNull { sticker -> sticker.dueAtMillis }
            .filter { dueAtMillis -> dueAtMillis > state.nowMillis }
            .minOrNull()

        if (scheduledSystemAlarmAtMillis == nextAlarmAtMillis) return

        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pendingIntent = alarmPendingIntent()
        alarmManager.cancel(pendingIntent)
        scheduledSystemAlarmAtMillis = nextAlarmAtMillis

        if (nextAlarmAtMillis != null) {
            runCatching {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()) {
                    val alarmClockInfo = AlarmManager.AlarmClockInfo(nextAlarmAtMillis, pendingIntent)
                    alarmManager.setAlarmClock(alarmClockInfo, pendingIntent)
                } else {
                    alarmManager.set(AlarmManager.RTC_WAKEUP, nextAlarmAtMillis, pendingIntent)
                }
            }.onFailure { error ->
                if (error is SecurityException) {
                    alarmManager.set(AlarmManager.RTC_WAKEUP, nextAlarmAtMillis, pendingIntent)
                } else {
                    throw error
                }
            }
        }
    }

    private fun alarmPendingIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            action = ACTION_ALARM_TRIGGERED
            putExtra(EXTRA_ALARM_LAUNCH, true)
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            this,
            ALARM_PENDING_INTENT_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun isAlarmLaunchIntent(intent: Intent?): Boolean =
        intent?.action == ACTION_ALARM_TRIGGERED || intent?.getBooleanExtra(EXTRA_ALARM_LAUNCH, false) == true

    private fun collectSpatialStickers() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    xrStickerScene?.syncStickers(
                        stickers = state.stickers,
                        nowMillis = state.nowMillis
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        xrStickerScene?.clear()
        xrStickerScene = null
        xrSession = null
        toneGenerator?.release()
        toneGenerator = null
        super.onDestroy()
    }

    private companion object {
        const val SCENE_UNDERSTANDING_PERMISSION = "android.permission.SCENE_UNDERSTANDING_COARSE"
        const val MAIN_PANEL_WIDTH_METERS = 1.4f
        const val MAIN_PANEL_HEIGHT_METERS = 0.9f
        const val MINIMIZED_PANEL_WIDTH_METERS = 0.34f
        const val MINIMIZED_PANEL_HEIGHT_METERS = 0.16f
        const val MAIN_PANEL_MOVE_WIDTH_METERS = 1.4f
        const val MAIN_PANEL_MOVE_HEIGHT_METERS = 0.9f
        const val MAIN_PANEL_MOVE_DEPTH_METERS = 0.1f
        const val ALARM_VOLUME_PERCENT = 100
        const val ALARM_TONE_REPEAT_COUNT = 3
        const val ALARM_TONE_MILLIS = 700
        const val ALARM_TONE_GAP_MILLIS = 900L
        const val ALARM_PENDING_INTENT_REQUEST_CODE = 4001
        const val ACTION_ALARM_TRIGGERED = "com.sticker.todoar.action.ALARM_TRIGGERED"
        const val EXTRA_ALARM_LAUNCH = "com.sticker.todoar.extra.ALARM_LAUNCH"
        const val STATE_MAIN_PANEL_MINIMIZED = "main_panel_minimized"
        const val STATE_MAIN_PANEL_SCALE = "main_panel_scale"
        const val PREF_MAIN_PANEL_SCALE = "main_panel_scale"
    }
}

@Composable
private fun StickerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MaterialTheme.colorScheme.copy(
            primary = Color(0xFF2F6B5F),
            onPrimary = Color.White,
            surface = Color(0xFFF6F7F2),
            onSurface = Color(0xFF1F2320)
        ),
        content = content
    )
}

@Composable
private fun StickerScreen(
    state: StickerUiState,
    mainPanelMinimized: Boolean,
    mainPanelScale: Float,
    onMainPanelMinimizedChange: (Boolean) -> Unit,
    onMainPanelScaleChange: (Float) -> Unit,
    onDraftChanged: (String) -> Unit,
    onAlarmTimeChanged: (String) -> Unit,
    onAlarmCleared: () -> Unit,
    onSaveClicked: (String) -> Unit,
    onPlaceClicked: (String, String) -> Unit,
    onBringNotesHere: () -> Unit,
    onEditSticker: (Long, String, String) -> Unit,
    onToggleSticker: (Long) -> Unit,
    onDeleteSticker: (Long) -> Unit
) {
    var editingSticker by remember { mutableStateOf<TodoSticker?>(null) }

    if (mainPanelMinimized) {
        MinimizedStickerPanel(
            onRestore = { onMainPanelMinimizedChange(false) }
        )
        return
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFFF6F7F2)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    enabled = mainPanelScale > MIN_MAIN_PANEL_SCALE,
                    onClick = {
                        onMainPanelScaleChange(
                            (mainPanelScale - MAIN_PANEL_SCALE_STEP)
                                .coerceIn(MIN_MAIN_PANEL_SCALE, MAX_MAIN_PANEL_SCALE)
                        )
                    },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(stringResource(R.string.action_size_smaller))
                }
                OutlinedButton(
                    enabled = mainPanelScale < MAX_MAIN_PANEL_SCALE,
                    onClick = {
                        onMainPanelScaleChange(
                            (mainPanelScale + MAIN_PANEL_SCALE_STEP)
                                .coerceIn(MIN_MAIN_PANEL_SCALE, MAX_MAIN_PANEL_SCALE)
                        )
                    },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(stringResource(R.string.action_size_larger))
                }
                OutlinedButton(
                    onClick = { onMainPanelMinimizedChange(true) },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(stringResource(R.string.action_minimize_panel))
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            StickerComposer(
                draftText = state.draftText,
                alarmTimeText = state.selectedAlarmTimeText,
                onDraftChanged = onDraftChanged,
                onAlarmTimeChanged = onAlarmTimeChanged,
                onAlarmCleared = onAlarmCleared,
                onSaveClicked = onSaveClicked,
                onPlaceClicked = onPlaceClicked,
                onBringNotesHere = onBringNotesHere
            )
            Text(
                text = state.status.asString(),
                modifier = Modifier.padding(top = 14.dp, bottom = 10.dp),
                color = Color(0xFF3A403A),
                fontSize = 15.sp
            )
            StickerList(
                stickers = state.stickers,
                nowMillis = state.nowMillis,
                onEditSticker = { sticker -> editingSticker = sticker },
                onToggleSticker = onToggleSticker,
                onDeleteSticker = onDeleteSticker
            )
        }
    }

    editingSticker?.let { sticker ->
        EditStickerDialog(
            sticker = sticker,
            onDismiss = { editingSticker = null },
            onSave = { text, alarmTimeText ->
                onEditSticker(sticker.id, text, alarmTimeText)
                editingSticker = null
            }
        )
    }
}

@Composable
private fun MinimizedStickerPanel(
    onRestore: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFFF6F7F2)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Button(
                onClick = onRestore,
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(
                    text = stringResource(R.string.action_restore_panel),
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun StickerComposer(
    draftText: String,
    alarmTimeText: String,
    onDraftChanged: (String) -> Unit,
    onAlarmTimeChanged: (String) -> Unit,
    onAlarmCleared: () -> Unit,
    onSaveClicked: (String) -> Unit,
    onPlaceClicked: (String, String) -> Unit,
    onBringNotesHere: () -> Unit
) {
    val defaultNoteText = stringResource(R.string.default_note_text)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFBE0)),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextField(
                    value = draftText,
                    onValueChange = onDraftChanged,
                    modifier = Modifier.weight(1f),
                    textStyle = TextStyle(fontSize = 18.sp),
                    placeholder = { Text(stringResource(R.string.label_todo)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = { onSaveClicked(draftText) }
                    ),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent
                    )
                )
                Spacer(modifier = Modifier.width(12.dp))
                Button(
                    onClick = { onSaveClicked(draftText) },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(stringResource(R.string.action_save), fontWeight = FontWeight.Bold)
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            AlarmTimeEditor(
                alarmTimeText = alarmTimeText,
                onAlarmTimeChanged = onAlarmTimeChanged,
                onAlarmCleared = onAlarmCleared
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onBringNotesHere,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(stringResource(R.string.action_bring_notes_here))
                }
                Spacer(modifier = Modifier.width(10.dp))
                Button(
                    onClick = { onPlaceClicked(draftText, defaultNoteText) },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(stringResource(R.string.action_spawn_note), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun AlarmTimeEditor(
    alarmTimeText: String,
    onAlarmTimeChanged: (String) -> Unit,
    onAlarmCleared: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextField(
            value = alarmTimeText,
            onValueChange = onAlarmTimeChanged,
            modifier = Modifier.weight(1f),
            label = { Text(stringResource(R.string.label_alarm_time)) },
            placeholder = { Text(stringResource(R.string.placeholder_alarm_time)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                disabledContainerColor = Color.Transparent
            )
        )
        OutlinedButton(
            onClick = onAlarmCleared,
            shape = RoundedCornerShape(10.dp)
        ) {
            Text(stringResource(R.string.action_no_alarm))
        }
    }
}

@Composable
private fun StickerList(
    stickers: List<TodoSticker>,
    nowMillis: Long,
    onEditSticker: (TodoSticker) -> Unit,
    onToggleSticker: (Long) -> Unit,
    onDeleteSticker: (Long) -> Unit
) {
    if (stickers.isEmpty()) {
        EmptyStickerState()
        return
    }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(
            items = stickers,
            key = { sticker -> sticker.id }
        ) { sticker ->
            StickerItem(
                sticker = sticker,
                nowMillis = nowMillis,
                onEditSticker = { onEditSticker(sticker) },
                onToggleSticker = onToggleSticker,
                onDeleteSticker = onDeleteSticker
            )
        }
    }
}

@Composable
private fun StickerItem(
    sticker: TodoSticker,
    nowMillis: Long,
    onEditSticker: () -> Unit,
    onToggleSticker: (Long) -> Unit,
    onDeleteSticker: (Long) -> Unit
) {
    val timerText = sticker.timerText(nowMillis)
    val placementText = sticker.placementText()
    val expired = sticker.isTimerExpired(nowMillis)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = when {
                sticker.done -> Color(0xFFDDE1DA)
                expired -> Color(0xFFFFC7B8)
                else -> Color(0xFFFFE067)
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = buildAnnotatedString {
                        withStyle(
                            SpanStyle(
                                textDecoration = if (sticker.done) {
                                    TextDecoration.LineThrough
                                } else {
                                    TextDecoration.None
                                }
                            )
                        ) {
                            append(sticker.text)
                        }
                    },
                    color = Color(0xFF2C3028),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
                if (timerText != null || placementText != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = listOfNotNull(timerText, placementText)
                            .joinToString(stringResource(R.string.metadata_separator)),
                        color = if (expired) Color(0xFF8B2F1D) else Color(0xFF586053),
                        fontSize = 13.sp,
                        fontWeight = if (expired) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
            Spacer(modifier = Modifier.width(10.dp))
            OutlinedButton(
                onClick = onEditSticker,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(stringResource(R.string.action_edit))
            }
            Spacer(modifier = Modifier.width(8.dp))
            OutlinedButton(
                onClick = { onToggleSticker(sticker.id) },
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    stringResource(
                        if (sticker.done) R.string.action_undo else R.string.action_done
                    )
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            OutlinedButton(
                onClick = { onDeleteSticker(sticker.id) },
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(stringResource(R.string.action_delete))
            }
        }
    }
}

@Composable
private fun EditStickerDialog(
    sticker: TodoSticker,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    var text by remember(sticker.id) { mutableStateOf(sticker.text) }
    val existingAlarmTimeText = sticker.dueAtMillis?.toClockTimeText().orEmpty()
    var alarmTimeText by remember(sticker.id, existingAlarmTimeText) {
        mutableStateOf(existingAlarmTimeText)
    }
    val defaultNoteText = stringResource(R.string.default_note_text)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.title_edit_note)) },
        text = {
            Column {
                TextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(stringResource(R.string.label_todo)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = { onSave(text.ifBlank { defaultNoteText }, alarmTimeText) }
                    )
                )
                Spacer(modifier = Modifier.height(10.dp))
                TextField(
                    value = alarmTimeText,
                    onValueChange = { alarmTimeText = it },
                    label = { Text(stringResource(R.string.label_alarm_time)) },
                    placeholder = { Text(stringResource(R.string.placeholder_alarm_time)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = { onSave(text.ifBlank { defaultNoteText }, alarmTimeText) }
                    )
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedButton(onClick = { alarmTimeText = "" }) {
                    Text(stringResource(R.string.action_no_alarm))
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(text.ifBlank { defaultNoteText }, alarmTimeText) }) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

@Composable
private fun EmptyStickerState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp)
            .background(Color.White, RoundedCornerShape(8.dp)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(R.string.empty_stickers),
            color = Color(0xFF5A6259),
            fontSize = 17.sp
        )
    }
}

@Composable
private fun TodoSticker.timerText(nowMillis: Long): String? {
    val dueAt = dueAtMillis ?: return null
    if (done) return stringResource(R.string.alarm_complete)

    val remainingMillis = dueAt - nowMillis
    if (remainingMillis <= 0) return stringResource(R.string.alarm_due)

    return stringResource(
        R.string.alarm_due_at_in,
        dueAt.toClockTimeText(),
        remainingMillis.formatDuration()
    )
}

@Composable
private fun Long.toClockTimeText(): String =
    DateFormat.getTimeFormat(LocalContext.current).format(Date(this))

@Composable
private fun TodoSticker.placementText(): String? =
    when (anchorProvider) {
        "jetpack_xr_anchor" -> stringResource(R.string.placement_in_room)
        "jetpack_xr_session_anchor" -> stringResource(R.string.placement_session_room)
        "jetpack_xr_activity_space" -> stringResource(R.string.placement_session_room)
        "jetpack_xr" -> stringResource(R.string.placement_anchored_in_xr)
        else -> null
    }

@Composable
private fun Long.formatDuration(): String {
    val totalSeconds = max(0L, this / 1_000L)
    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0) {
        stringResource(R.string.duration_hours_minutes, hours, minutes)
    } else {
        stringResource(R.string.duration_minutes_seconds, minutes, seconds)
    }
}
