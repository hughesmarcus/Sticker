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
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.xr.runtime.AnchorPersistenceMode
import androidx.xr.runtime.Config
import androidx.xr.runtime.DeviceTrackingMode
import androidx.xr.runtime.PlaneTrackingMode
import androidx.xr.runtime.Session
import androidx.xr.runtime.SessionConfigureSuccess
import androidx.xr.runtime.SessionCreateApkRequired
import androidx.xr.runtime.SessionCreateSuccess
import androidx.xr.runtime.SessionCreateUnsupportedDevice
import androidx.xr.runtime.math.FloatSize2d
import androidx.xr.runtime.math.FloatSize3d
import androidx.xr.scenecore.MovableComponent
import androidx.xr.scenecore.scene
import com.sticker.todoar.R
import com.sticker.todoar.ui.model.RoomPlacementRequest
import com.sticker.todoar.ui.model.StickerUiState
import com.sticker.todoar.ui.model.UiText
import com.sticker.todoar.ui.sticker.DEFAULT_MAIN_PANEL_SCALE
import com.sticker.todoar.ui.sticker.MAX_MAIN_PANEL_SCALE
import com.sticker.todoar.ui.sticker.MIN_MAIN_PANEL_SCALE
import com.sticker.todoar.ui.sticker.StickerScreen
import com.sticker.todoar.ui.sticker.StickerTheme
import com.sticker.todoar.ui.viewmodel.StickerViewModel
import com.sticker.todoar.xr.XrPlacementResult
import com.sticker.todoar.xr.XrStickerScene
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
