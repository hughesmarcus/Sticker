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
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
import com.sticker.todoar.domain.StickerPinQuality
import com.sticker.todoar.domain.TodoSticker
import com.sticker.todoar.domain.TodoStickerColor
import com.sticker.todoar.domain.TodoStickerPriority
import com.sticker.todoar.ui.stickers.RoomPlacementRequest
import com.sticker.todoar.ui.stickers.StickerItemUiState
import com.sticker.todoar.ui.stickers.StickerItemViewModel
import com.sticker.todoar.ui.stickers.StickerUiState
import com.sticker.todoar.ui.stickers.StickerViewModel
import com.sticker.todoar.xr.XrPlacementResult
import com.sticker.todoar.xr.XrStickerScene
import dagger.hilt.android.AndroidEntryPoint
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
private const val SNOOZE_SHORT_MINUTES = 5
private const val SNOOZE_LONG_MINUTES = 15

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
    private var alarmToneJob: Job? = null
    private var isStartingXrSession = false
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
                    onDraftColorChanged = viewModel::onDraftColorChanged,
                    onDraftPriorityChanged = viewModel::onDraftPriorityChanged,
                    onSaveClicked = viewModel::onSaveRequested,
                    onPlaceClicked = ::spawnDraftInRoom,
                    onBringNotesHere = ::bringNotesHere,
                    onEditSticker = viewModel::onStickerEdited,
                    onSnoozeSticker = viewModel::onStickerSnoozed,
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
        if (isStartingXrSession || xrStickerScene != null) return
        isStartingXrSession = true
        lifecycleScope.launch {
            try {
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
            } finally {
                isStartingXrSession = false
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
            clearXrReferences()
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
                onUpdateStickerStyle = viewModel::onStickerStyleChanged,
                onSnoozeSticker = viewModel::onStickerSnoozed,
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
                    color = request.color,
                    priority = request.priority,
                    activitySpacePose = result.activitySpacePose
                )
            }
            is XrPlacementResult.Failure -> {
                viewModel.onRoomPlacementFailed(UiText.resource(result.messageRes))
            }
        }
    }

    private fun makeMainPanelMovable(session: Session) {
        mainPanelMovableComponent?.let { previousComponent ->
            runCatching { session.scene.mainPanelEntity.removeComponent(previousComponent) }
        }
        mainPanelMovableComponent = null
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
                    if (state.isLoading) return@collect

                    val expiredAlarmIds = state.stickers
                        .filter { sticker -> sticker.isTimerExpired(state.nowMillis) }
                        .map { sticker -> sticker.id }
                        .toSet()
                    alarmedStickerIds.retainAll(expiredAlarmIds)
                    if (!alarmCollectionInitialized && !pendingAlarmLaunch) {
                        alarmedStickerIds += expiredAlarmIds
                    }

                    val dueSticker = state.stickers.firstOrNull { sticker ->
                        sticker.shouldTriggerAlarm(state.nowMillis) && sticker.id !in alarmedStickerIds
                    }
                    if (dueSticker != null) {
                        alarmedStickerIds += expiredAlarmIds
                        playAlarmTone()
                        viewModel.onAlarmTriggered(dueSticker.id, dueSticker.text)
                    }
                    alarmCollectionInitialized = true
                    pendingAlarmLaunch = false
                    scheduleNextSystemAlarm(state)
                }
            }
        }
    }

    private fun playAlarmTone() {
        alarmToneJob?.cancel()
        val generator = toneGenerator ?: ToneGenerator(AudioManager.STREAM_ALARM, ALARM_VOLUME_PERCENT)
            .also { toneGenerator = it }
        val job = lifecycleScope.launch {
            repeat(ALARM_TONE_REPEAT_COUNT) {
                generator.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, ALARM_TONE_MILLIS)
                delay(ALARM_TONE_GAP_MILLIS)
            }
        }
        alarmToneJob = job
        job.invokeOnCompletion {
            if (alarmToneJob == job) {
                alarmToneJob = null
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
                    if (state.isLoading) return@collect

                    xrStickerScene?.syncStickers(
                        stickers = state.stickers,
                        nowMillis = state.nowMillis
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        alarmToneJob?.cancel()
        alarmToneJob = null
        clearXrReferences()
        toneGenerator?.release()
        toneGenerator = null
        super.onDestroy()
    }

    private fun clearXrReferences() {
        xrStickerScene?.clear()
        xrStickerScene = null
        mainPanelMovableComponent?.let { component ->
            runCatching { xrSession?.scene?.mainPanelEntity?.removeComponent(component) }
        }
        mainPanelMovableComponent = null
        xrSession = null
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
    onDraftColorChanged: (TodoStickerColor) -> Unit,
    onDraftPriorityChanged: (TodoStickerPriority) -> Unit,
    onSaveClicked: (String) -> Unit,
    onPlaceClicked: (String, String) -> Unit,
    onBringNotesHere: () -> Unit,
    onEditSticker: (Long, String, String, TodoStickerColor, TodoStickerPriority) -> Unit,
    onSnoozeSticker: (Long, Int) -> Unit,
    onToggleSticker: (Long) -> Unit,
    onDeleteSticker: (Long) -> Unit
) {
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
                selectedColor = state.selectedColor,
                selectedPriority = state.selectedPriority,
                onDraftChanged = onDraftChanged,
                onAlarmTimeChanged = onAlarmTimeChanged,
                onAlarmCleared = onAlarmCleared,
                onColorChanged = onDraftColorChanged,
                onPriorityChanged = onDraftPriorityChanged,
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
                isLoading = state.isLoading,
                isError = state.isError,
                errorText = state.status,
                stickers = state.stickers,
                nowMillis = state.nowMillis,
                onEditSticker = onEditSticker,
                onSnoozeSticker = onSnoozeSticker,
                onToggleSticker = onToggleSticker,
                onDeleteSticker = onDeleteSticker
            )
        }
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
    selectedColor: TodoStickerColor,
    selectedPriority: TodoStickerPriority,
    onDraftChanged: (String) -> Unit,
    onAlarmTimeChanged: (String) -> Unit,
    onAlarmCleared: () -> Unit,
    onColorChanged: (TodoStickerColor) -> Unit,
    onPriorityChanged: (TodoStickerPriority) -> Unit,
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
            StickerStyleControls(
                selectedColor = selectedColor,
                selectedPriority = selectedPriority,
                onColorSelected = onColorChanged,
                onPrioritySelected = onPriorityChanged
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
private fun StickerStyleControls(
    selectedColor: TodoStickerColor,
    selectedPriority: TodoStickerPriority,
    onColorSelected: (TodoStickerColor) -> Unit,
    onPrioritySelected: (TodoStickerPriority) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.label_note_color),
            color = Color(0xFF3A403A),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TodoStickerColor.entries.forEach { color ->
                val label = color.labelText()
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .background(color.backgroundColor(), RoundedCornerShape(8.dp))
                        .border(
                            width = if (color == selectedColor) 3.dp else 1.dp,
                            color = if (color == selectedColor) Color(0xFF1F2320) else Color(0xFF7B8177),
                            shape = RoundedCornerShape(8.dp)
                        )
                        .semantics { contentDescription = label }
                        .clickable { onColorSelected(color) }
                )
            }
        }
        Text(
            text = stringResource(R.string.label_priority),
            color = Color(0xFF3A403A),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TodoStickerPriority.entries.forEach { priority ->
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    onClick = { onPrioritySelected(priority) },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = priority.labelText(),
                        color = if (priority == selectedPriority) Color(0xFF1B4F45) else Color(0xFF3A403A),
                        fontWeight = if (priority == selectedPriority) FontWeight.Bold else FontWeight.Normal
                    )
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
    isLoading: Boolean,
    isError: Boolean,
    errorText: UiText,
    stickers: List<TodoSticker>,
    nowMillis: Long,
    onEditSticker: (Long, String, String, TodoStickerColor, TodoStickerPriority) -> Unit,
    onSnoozeSticker: (Long, Int) -> Unit,
    onToggleSticker: (Long) -> Unit,
    onDeleteSticker: (Long) -> Unit
) {
    if (isLoading) {
        LoadingStickerState()
        return
    }

    if (isError) {
        ErrorStickerState(errorText)
        return
    }

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
                onEditSticker = onEditSticker,
                onSnoozeSticker = onSnoozeSticker,
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
    onEditSticker: (Long, String, String, TodoStickerColor, TodoStickerPriority) -> Unit,
    onSnoozeSticker: (Long, Int) -> Unit,
    onToggleSticker: (Long) -> Unit,
    onDeleteSticker: (Long) -> Unit
) {
    val existingAlarmTimeText = sticker.dueAtMillis?.toClockTimeText().orEmpty()
    val itemViewModel = remember(sticker.id) {
        StickerItemViewModel(
            sticker = sticker,
            nowMillis = nowMillis,
            alarmTimeText = existingAlarmTimeText
        )
    }
    LaunchedEffect(itemViewModel, sticker, nowMillis, existingAlarmTimeText) {
        itemViewModel.updateSticker(
            sticker = sticker,
            nowMillis = nowMillis,
            alarmTimeText = existingAlarmTimeText
        )
    }
    val itemState by itemViewModel.uiState.collectAsStateWithLifecycle()
    val currentSticker = itemState.sticker
    val timerText = currentSticker.timerText(itemState.nowMillis)
    val placementText = currentSticker.placementText()
    val priorityText = currentSticker.priority.labelText()
    val metadataText = listOfNotNull(timerText, priorityText, placementText)
        .joinToString(stringResource(R.string.metadata_separator))
    val expired = itemState.isExpired
    val currentAlarmTimeText = currentSticker.dueAtMillis?.toClockTimeText().orEmpty()

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = when {
                currentSticker.done -> Color(0xFFDDE1DA)
                expired -> Color(0xFFFFC7B8)
                else -> currentSticker.color.backgroundColor()
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
                                textDecoration = if (currentSticker.done) {
                                    TextDecoration.LineThrough
                                } else {
                                    TextDecoration.None
                                }
                            )
                        ) {
                            append(currentSticker.text)
                        }
                    },
                    color = Color(0xFF2C3028),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
                if (metadataText.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = metadataText,
                        color = if (expired) Color(0xFF8B2F1D) else Color(0xFF586053),
                        fontSize = 13.sp,
                        fontWeight = if (expired) FontWeight.Bold else FontWeight.Normal
                    )
                }
                if (expired) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = { onSnoozeSticker(currentSticker.id, SNOOZE_SHORT_MINUTES) },
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(stringResource(R.string.action_snooze_5))
                        }
                        OutlinedButton(
                            onClick = { onSnoozeSticker(currentSticker.id, SNOOZE_LONG_MINUTES) },
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(stringResource(R.string.action_snooze_15))
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.width(10.dp))
            OutlinedButton(
                onClick = { itemViewModel.startEditing(currentAlarmTimeText) },
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(stringResource(R.string.action_edit))
            }
            Spacer(modifier = Modifier.width(8.dp))
            OutlinedButton(
                onClick = { onToggleSticker(currentSticker.id) },
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    stringResource(
                        if (currentSticker.done) R.string.action_undo else R.string.action_done
                    )
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            OutlinedButton(
                onClick = { onDeleteSticker(currentSticker.id) },
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(stringResource(R.string.action_delete))
            }
        }
    }

    if (itemState.isEditing) {
        val defaultNoteText = stringResource(R.string.default_note_text)
        EditStickerDialog(
            state = itemState,
            onTextChanged = itemViewModel::onEditingTextChanged,
            onAlarmTimeChanged = itemViewModel::onEditingAlarmTimeChanged,
            onColorChanged = itemViewModel::onEditingColorChanged,
            onPriorityChanged = itemViewModel::onEditingPriorityChanged,
            onDismiss = { itemViewModel.cancelEditing(currentAlarmTimeText) },
            onSave = {
                val request = itemViewModel.saveEditing(defaultNoteText)
                onEditSticker(
                    request.id,
                    request.text,
                    request.alarmTimeText,
                    request.color,
                    request.priority
                )
            }
        )
    }
}

@Composable
private fun EditStickerDialog(
    state: StickerItemUiState,
    onTextChanged: (String) -> Unit,
    onAlarmTimeChanged: (String) -> Unit,
    onColorChanged: (TodoStickerColor) -> Unit,
    onPriorityChanged: (TodoStickerPriority) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.title_edit_note)) },
        text = {
            Column {
                TextField(
                    value = state.editingText,
                    onValueChange = onTextChanged,
                    label = { Text(stringResource(R.string.label_todo)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = { onSave() }
                    )
                )
                Spacer(modifier = Modifier.height(10.dp))
                TextField(
                    value = state.editingAlarmTimeText,
                    onValueChange = onAlarmTimeChanged,
                    label = { Text(stringResource(R.string.label_alarm_time)) },
                    placeholder = { Text(stringResource(R.string.placeholder_alarm_time)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = { onSave() }
                    )
                )
                Spacer(modifier = Modifier.height(10.dp))
                StickerStyleControls(
                    selectedColor = state.selectedColor,
                    selectedPriority = state.selectedPriority,
                    onColorSelected = onColorChanged,
                    onPrioritySelected = onPriorityChanged
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedButton(onClick = { onAlarmTimeChanged("") }) {
                    Text(stringResource(R.string.action_no_alarm))
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onSave
            ) {
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
private fun ErrorStickerState(message: UiText) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp)
            .background(Color.White, RoundedCornerShape(8.dp)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = message.asString(),
            color = Color(0xFF8B2F1D),
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun LoadingStickerState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp)
            .background(Color.White, RoundedCornerShape(8.dp)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(32.dp),
            color = Color(0xFF2F6B5F)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.loading_stickers),
            color = Color(0xFF5A6259),
            fontSize = 17.sp
        )
    }
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
    when (pinQuality) {
        StickerPinQuality.ROOM -> stringResource(R.string.pin_quality_room)
        StickerPinQuality.SESSION -> stringResource(R.string.pin_quality_session)
        StickerPinQuality.FALLBACK -> stringResource(R.string.pin_quality_fallback)
        StickerPinQuality.UNPLACED -> null
    }

@Composable
private fun TodoStickerPriority.labelText(): String =
    when (this) {
        TodoStickerPriority.LOW -> stringResource(R.string.priority_low)
        TodoStickerPriority.NORMAL -> stringResource(R.string.priority_normal)
        TodoStickerPriority.HIGH -> stringResource(R.string.priority_high)
    }

@Composable
private fun TodoStickerColor.labelText(): String =
    when (this) {
        TodoStickerColor.YELLOW -> stringResource(R.string.note_color_yellow)
        TodoStickerColor.BLUE -> stringResource(R.string.note_color_blue)
        TodoStickerColor.GREEN -> stringResource(R.string.note_color_green)
        TodoStickerColor.PINK -> stringResource(R.string.note_color_pink)
    }

private fun TodoStickerColor.backgroundColor(): Color =
    when (this) {
        TodoStickerColor.YELLOW -> Color(0xFFFFE067)
        TodoStickerColor.BLUE -> Color(0xFFBFDDF8)
        TodoStickerColor.GREEN -> Color(0xFFCDECCF)
        TodoStickerColor.PINK -> Color(0xFFFFC9DE)
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
