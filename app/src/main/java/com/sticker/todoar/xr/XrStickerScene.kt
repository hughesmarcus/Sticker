package com.sticker.todoar.xr

import android.util.Log
import android.text.format.DateFormat
import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import androidx.xr.arcore.Anchor
import androidx.xr.arcore.AnchorCreateResourcesExhausted
import androidx.xr.arcore.AnchorCreateSuccess
import androidx.xr.arcore.AnchorCreateTrackingUnavailable
import androidx.xr.arcore.AnchorResult
import androidx.xr.arcore.ArDevice
import androidx.xr.arcore.Plane
import androidx.xr.arcore.TrackingState
import androidx.xr.arcore.hitTest
import androidx.xr.arcore.runtime.Anchor as RuntimeAnchor
import androidx.xr.runtime.Session
import androidx.xr.runtime.math.FloatSize2d
import androidx.xr.runtime.math.Pose
import androidx.xr.runtime.math.Quaternion
import androidx.xr.runtime.math.Ray
import androidx.xr.runtime.math.Vector3
import androidx.xr.scenecore.AnchorEntity
import androidx.xr.scenecore.Entity
import androidx.xr.scenecore.EntityMoveListener
import androidx.xr.scenecore.MovableComponent
import androidx.xr.scenecore.PanelEntity
import androidx.xr.scenecore.Space
import androidx.xr.scenecore.scene
import androidx.xr.runtime.math.FloatSize3d
import com.sticker.todoar.R
import com.sticker.todoar.domain.StickerPinQuality
import com.sticker.todoar.domain.StickerSpatialPose
import com.sticker.todoar.domain.TodoSticker
import com.sticker.todoar.domain.TodoStickerColor
import com.sticker.todoar.domain.TodoStickerPriority
import java.util.UUID
import java.util.Date
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.coroutineContext
import kotlin.math.max

sealed interface XrPlacementResult {
    data class Success(
        val anchorProvider: String,
        val anchorId: String,
        val activitySpacePose: StickerSpatialPose? = null
    ) : XrPlacementResult

    data class Failure(@StringRes val messageRes: Int) : XrPlacementResult
}

class XrStickerScene(
    private val activity: ComponentActivity,
    private val session: Session,
    scope: CoroutineScope,
    private val onToggleSticker: (Long) -> Unit,
    private val onDeleteSticker: (Long) -> Unit,
    private val onUpdateStickerText: (Long, String) -> Unit,
    private val onUpdateStickerAlarm: (Long, String) -> Unit,
    private val onUpdateStickerSize: (Long, Float) -> Unit,
    private val onUpdateStickerStyle: (Long, TodoStickerColor, TodoStickerPriority) -> Unit,
    private val onSnoozeSticker: (Long, Int) -> Unit,
    private val onActivitySpacePoseUpdated: (Long, StickerSpatialPose) -> Unit,
    private val onAnchorUpdated: (Long, String) -> Unit,
    private val onStatus: (Int) -> Unit
) {
    private val nodes = mutableMapOf<Long, SpatialStickerNode>()
    private val sessionAnchors = mutableMapOf<UUID, Anchor>()
    private val activitySpacePoses = mutableMapOf<UUID, Pose>()
    private val failedStickerLoads = mutableSetOf<Long>()
    private val promotingStickerIds = mutableSetOf<Long>()
    private val promotionJobs = mutableMapOf<Long, Job>()
    private val moveAnchorJobs = mutableMapOf<Long, Job>()
    private val sceneJob = SupervisorJob(scope.coroutineContext[Job])
    private val sceneScope = CoroutineScope(scope.coroutineContext + sceneJob)
    private val anchorPersistenceMutex = Mutex()

    suspend fun createPersistentAnchorAtCurrentSpot(): XrPlacementResult {
        session.scene.requestFullSpaceMode()

        val deviceState = try {
            ArDevice.getInstance(session).state.value
        } catch (_: IllegalStateException) {
            return createActivitySpacePlacement()
        }

        if (deviceState.trackingState != TrackingState.TRACKING) {
            return createActivitySpacePlacement()
        }

        val createdAnchor = createAnchorFromLookDirection(deviceState.devicePose)
        val anchor = when (val result = createdAnchor.result) {
            is AnchorCreateSuccess -> result.anchor
            is AnchorCreateResourcesExhausted -> {
                return createActivitySpacePlacement()
            }
            is AnchorCreateTrackingUnavailable -> {
                return createActivitySpacePlacement()
            }
            else -> {
                return createActivitySpacePlacement()
            }
        }

        val uuid = UUID.randomUUID()
        sessionAnchors[uuid] = anchor
        return XrPlacementResult.Success(
            anchorProvider = ANCHOR_PROVIDER_SESSION_XR,
            anchorId = uuid.toString(),
            activitySpacePose = createdAnchor.activitySpacePose.toStickerSpatialPose()
        )
    }

    fun syncStickers(stickers: List<TodoSticker>, nowMillis: Long) {
        val spatialStickers = stickers.filter { it.isSpatiallyAnchored }
        val activeIds = spatialStickers.map { it.id }.toSet()

        nodes.keys.minus(activeIds).forEach { id ->
            removeNode(id, unpersistAnchor = true)
        }
        failedStickerLoads.retainAll(activeIds)

        spatialStickers.forEach { sticker ->
            val existingNode = nodes[sticker.id]
            if (existingNode != null && existingNode.anchorUuid.toString() == sticker.anchorId) {
                existingNode.sticker = sticker
                existingNode.nowMillis = nowMillis
                existingNode.applySize(sticker)
                maybePromoteTemporaryNode(sticker.id, existingNode)
            } else {
                if (existingNode != null) {
                    removeNode(sticker.id, unpersistAnchor = false)
                }
                runCatching {
                    createNode(sticker, nowMillis)
                }.onFailure { error ->
                    Log.e(TAG, "Could not create spatial note ${sticker.id}", error)
                    if (failedStickerLoads.add(sticker.id)) {
                        onStatus(R.string.status_could_not_create_room_note)
                    }
                }
                nodes[sticker.id]?.let { node -> maybePromoteTemporaryNode(sticker.id, node) }
            }
        }
    }

    fun clear() {
        nodes.keys.toList().forEach { id ->
            removeNode(id, unpersistAnchor = false)
        }
        promotionJobs.values.forEach { job -> job.cancel() }
        promotionJobs.clear()
        moveAnchorJobs.values.forEach { job -> job.cancel() }
        moveAnchorJobs.clear()
        sessionAnchors.values.forEach { anchor -> runCatching { anchor.detach() } }
        sessionAnchors.clear()
        activitySpacePoses.clear()
        failedStickerLoads.clear()
        promotingStickerIds.clear()
        sceneJob.cancel()
    }

    fun bringTemporaryNotesIntoView(): Boolean {
        val temporaryNodes = nodes.values.filter { node ->
            node.anchorProvider in TEMPORARY_ANCHOR_PROVIDERS
        }
        temporaryNodes.forEachIndexed { index, node ->
            val pose = defaultActivitySpacePose(index)
            node.panel.parent = session.scene.activitySpace
            node.panel.setPose(pose, Space.PARENT)
            node.panel.setEnabled(true)
            onActivitySpacePoseUpdated(node.sticker.id, pose.toStickerSpatialPose())
            if (node.anchorProvider == ANCHOR_PROVIDER_ACTIVITY_SPACE) {
                activitySpacePoses[node.anchorUuid] = pose
            }
        }
        return temporaryNodes.isNotEmpty()
    }

    private fun createActivitySpacePlacement(): XrPlacementResult.Success {
        val uuid = UUID.randomUUID()
        val pose = defaultActivitySpacePose(activitySpacePoses.size)
        activitySpacePoses[uuid] = pose
        Log.i(TAG, "Spawning a movable activity-space note in front of the user")
        return XrPlacementResult.Success(
            anchorProvider = ANCHOR_PROVIDER_ACTIVITY_SPACE,
            anchorId = uuid.toString(),
            activitySpacePose = pose.toStickerSpatialPose()
        )
    }

    private fun createAnchorFromLookDirection(devicePose: Pose): CreatedAnchor {
        val forward = devicePose.forward.toNormalized()
        val hitAnchor = createAnchorFromHit(devicePose.translation, forward)
        if (hitAnchor != null) return hitAnchor

        val position = devicePose.translation + forward * DEFAULT_NOTE_DISTANCE_METERS
        val pose = Pose(position)
        return CreatedAnchor(
            result = Anchor.create(session, pose),
            activitySpacePose = pose
        )
    }

    private fun createAnchorFromHit(origin: Vector3, direction: Vector3): CreatedAnchor? =
        runCatching {
            hitTest(session, Ray(origin = origin, direction = direction))
                .firstOrNull { hit ->
                    hit.trackable is Plane
                }
                ?.let { hit ->
                    CreatedAnchor(
                        result = hit.createAnchor(),
                        activitySpacePose = hit.hitPose
                    )
                }
        }.getOrNull()

    private fun maybePromoteTemporaryNode(stickerId: Long, node: SpatialStickerNode) {
        if (node.anchorProvider !in TEMPORARY_ANCHOR_PROVIDERS) return
        if (!promotingStickerIds.add(stickerId)) return

        val job = sceneScope.launch(start = CoroutineStart.LAZY) {
            try {
                val trackingState = ArDevice.getInstance(session).state.value.trackingState
                if (trackingState != TrackingState.TRACKING) return@launch

                val anchor = when (node.anchorProvider) {
                    ANCHOR_PROVIDER_SESSION_XR -> node.anchor ?: return@launch
                    ANCHOR_PROVIDER_ACTIVITY_SPACE -> {
                        val panelPose = node.panel.getPose(Space.ACTIVITY)
                        val anchorPose = Pose(panelPose.translation)
                        when (val result = Anchor.create(session, anchorPose)) {
                            is AnchorCreateSuccess -> result.anchor
                            else -> return@launch
                        }
                    }
                    else -> return@launch
                }
                val uuid = anchor.persistUntilUuid()
                if (nodes[stickerId] !== node) return@launch
                Log.i(TAG, "Promoted temporary note $stickerId to persisted anchor $uuid")
                onAnchorUpdated(stickerId, uuid.toString())
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                Log.w(TAG, "Could not promote temporary note $stickerId to a persisted anchor", error)
            } finally {
                if (promotionJobs[stickerId] == coroutineContext[Job]) {
                    promotionJobs.remove(stickerId)
                }
                promotingStickerIds.remove(stickerId)
            }
        }
        promotionJobs[stickerId] = job
        job.start()
    }

    private fun createNode(sticker: TodoSticker, nowMillis: Long) {
        val anchorUuid = sticker.anchorId?.let { id ->
            runCatching { UUID.fromString(id) }.getOrNull()
        }
        if (anchorUuid == null) {
            failedStickerLoads.add(sticker.id)
            return
        }

        var anchor: Anchor? = null
        var anchorEntity: AnchorEntity? = null
        val panelParent: Entity
        val panelPose: Pose
        var nodeAnchorProvider = sticker.anchorProvider

        when (sticker.anchorProvider) {
            ANCHOR_PROVIDER_JETPACK_XR -> {
                val loadedAnchor = when (val result = runCatching { Anchor.load(session, anchorUuid) }.getOrNull()) {
                    is AnchorCreateSuccess -> result.anchor
                    else -> null
                }
                if (loadedAnchor != null) {
                    val createdAnchorEntity = AnchorEntity.create(session, loadedAnchor)
                    anchor = loadedAnchor
                    anchorEntity = createdAnchorEntity
                    panelParent = createdAnchorEntity
                    panelPose = Pose.Identity
                } else {
                    val fallbackPose = sticker.activitySpacePose?.toPose()
                    if (fallbackPose == null) {
                        if (failedStickerLoads.add(sticker.id)) {
                            onStatus(R.string.status_could_not_load_room_note)
                        }
                        return
                    }
                    activitySpacePoses[anchorUuid] = fallbackPose
                    nodeAnchorProvider = ANCHOR_PROVIDER_ACTIVITY_SPACE
                    panelParent = session.scene.activitySpace
                    panelPose = fallbackPose
                    Log.w(TAG, "Loaded spatial note ${sticker.id} from saved fallback pose")
                }
            }
            ANCHOR_PROVIDER_SESSION_XR -> {
                val sessionAnchor = sessionAnchors[anchorUuid]
                if (sessionAnchor == null) {
                    val fallbackPose = sticker.activitySpacePose?.toPose() ?: defaultActivitySpacePose(sticker.id.hashCode())
                    activitySpacePoses[anchorUuid] = fallbackPose
                    nodeAnchorProvider = ANCHOR_PROVIDER_ACTIVITY_SPACE
                    panelParent = session.scene.activitySpace
                    panelPose = fallbackPose
                } else {
                    val createdAnchorEntity = AnchorEntity.create(session, sessionAnchor)
                    anchor = sessionAnchor
                    anchorEntity = createdAnchorEntity
                    panelParent = createdAnchorEntity
                    panelPose = Pose.Identity
                }
            }
            ANCHOR_PROVIDER_ACTIVITY_SPACE -> {
                panelParent = session.scene.activitySpace
                panelPose = activitySpacePoses[anchorUuid]
                    ?: sticker.activitySpacePose?.toPose()
                    ?: defaultActivitySpacePose(sticker.id.hashCode())
                activitySpacePoses[anchorUuid] = panelPose
            }
            else -> return
        }

        failedStickerLoads.remove(sticker.id)
        val stickerState = mutableStateOf(sticker)
        val nowState = mutableStateOf(nowMillis)
        val composeView = createStickerComposeView(stickerState, nowState)
        val noteSize = sticker.noteSize()
        val panel = PanelEntity.create(
            session = session,
            view = composeView,
            dimensions = noteSize,
            name = "todo-sticker-${sticker.id}",
            pose = panelPose,
            parent = panelParent
        ).apply {
            cornerRadius = NOTE_CORNER_RADIUS_METERS
            contentDescription = activity.getString(R.string.content_description_todo_note, sticker.text)
        }
        Log.i(TAG, "Created spatial note ${sticker.id} with provider ${sticker.anchorProvider}")

        val movableComponent = MovableComponent.createAnchorable(session).apply {
            size = sticker.noteMoveSize()
        }
        movableComponent.addMoveListener(
            object : EntityMoveListener {
                override fun onMoveEnd(
                    entity: Entity,
                    finalInputRay: Ray,
                    finalPose: Pose,
                    finalScale: Float,
                    updatedParent: Entity?
                ) {
                    val stickerId = stickerState.value.id
                    runCatching { entity.getPose(Space.ACTIVITY) }
                        .getOrNull()
                        ?.let { activityPose ->
                            activitySpacePoses[anchorUuid] = activityPose
                            onActivitySpacePoseUpdated(stickerId, activityPose.toStickerSpatialPose())
                        }
                    val movedAnchor = (updatedParent as? AnchorEntity)?.anchor
                    if (movedAnchor == null) {
                        onStatus(R.string.status_moved_note)
                        return
                    }
                    moveAnchorJobs.remove(stickerId)?.cancel()
                    val job = sceneScope.launch(start = CoroutineStart.LAZY) {
                        try {
                            persistMovedAnchor(stickerId, movedAnchor)
                        } finally {
                            if (moveAnchorJobs[stickerId] == coroutineContext[Job]) {
                                moveAnchorJobs.remove(stickerId)
                            }
                        }
                    }
                    moveAnchorJobs[stickerId] = job
                    job.start()
                }
            }
        )
        panel.addComponent(movableComponent)
        saveCurrentActivitySpacePose(sticker.id, panel)

        nodes[sticker.id] = SpatialStickerNode(
            anchorProvider = nodeAnchorProvider,
            anchorUuid = anchorUuid,
            anchor = anchor,
            anchorEntity = anchorEntity,
            panel = panel,
            view = composeView,
            movableComponent = movableComponent,
            stickerState = stickerState,
            nowState = nowState
        )
    }

    private fun saveCurrentActivitySpacePose(stickerId: Long, entity: Entity) {
        runCatching { entity.getPose(Space.ACTIVITY) }
            .getOrNull()
            ?.let { pose -> onActivitySpacePoseUpdated(stickerId, pose.toStickerSpatialPose()) }
    }

    private suspend fun persistMovedAnchor(stickerId: Long, anchor: Anchor) {
        val previousNode = nodes[stickerId]
        val previousAnchorUuid = previousNode?.anchorUuid
        try {
            val uuid = anchor.persistUntilUuid()
            val currentNode = nodes[stickerId] ?: return
            Log.i(TAG, "Saved moved note $stickerId to persisted anchor $uuid")
            if (
                currentNode.anchorProvider == ANCHOR_PROVIDER_JETPACK_XR &&
                previousAnchorUuid != null &&
                previousAnchorUuid != uuid
            ) {
                runCatching { Anchor.unpersist(session, previousAnchorUuid) }
            }
            onAnchorUpdated(stickerId, uuid.toString())
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            if (previousNode?.anchorProvider == ANCHOR_PROVIDER_JETPACK_XR) {
                onStatus(R.string.status_could_not_save_moved_note)
            } else {
                onStatus(R.string.status_moved_note)
            }
        }
    }

    private fun createStickerComposeView(
        stickerState: MutableState<TodoSticker>,
        nowState: MutableState<Long>
    ): ComposeView =
        ComposeView(activity).apply {
            setViewTreeLifecycleOwner(activity)
            setViewTreeViewModelStoreOwner(activity)
            setViewTreeSavedStateRegistryOwner(activity)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                SpatialStickerTheme {
                    SpatialStickerCard(
                        sticker = stickerState.value,
                        nowMillis = nowState.value,
                        onUpdateText = { text -> onUpdateStickerText(stickerState.value.id, text) },
                        onUpdateAlarm = { alarmTimeText ->
                            onUpdateStickerAlarm(stickerState.value.id, alarmTimeText)
                        },
                        onResize = { sizeScale -> onUpdateStickerSize(stickerState.value.id, sizeScale) },
                        onUpdateStyle = { color, priority ->
                            onUpdateStickerStyle(stickerState.value.id, color, priority)
                        },
                        onSnooze = { minutes -> onSnoozeSticker(stickerState.value.id, minutes) },
                        onDone = { onToggleSticker(stickerState.value.id) },
                        onDelete = { onDeleteSticker(stickerState.value.id) }
                    )
                }
            }
        }

    @Suppress("RestrictedApi")
    private suspend fun Anchor.persistUntilUuid(): UUID =
        anchorPersistenceMutex.withLock {
            val knownPersistedUuids = Anchor.getPersistedAnchorUuids(session).toSet()
            withTimeout(ANCHOR_PERSIST_TIMEOUT_MILLIS) {
                var sawPendingState = false
                runtimeAnchor.persist()
                while (true) {
                    when (runtimeAnchor.persistenceState) {
                        RuntimeAnchor.PersistenceState.PERSISTED -> {
                            runtimeAnchor.uuid?.let { uuid -> return@withTimeout uuid }
                        }
                        RuntimeAnchor.PersistenceState.PENDING -> {
                            sawPendingState = true
                        }
                        RuntimeAnchor.PersistenceState.NOT_PERSISTED -> {
                            if (sawPendingState) {
                                throw IllegalStateException("Anchor was not persisted.")
                            }
                        }
                    }
                    Anchor.getPersistedAnchorUuids(session)
                        .firstOrNull { uuid -> uuid !in knownPersistedUuids }
                        ?.let { uuid -> return@withTimeout uuid }
                    delay(250L)
                }
                error("Unreachable")
            }
        }

    private fun defaultActivitySpacePose(slot: Int): Pose {
        val normalizedSlot = (slot % ACTIVITY_SPACE_SPAWN_SLOTS + ACTIVITY_SPACE_SPAWN_SLOTS) %
            ACTIVITY_SPACE_SPAWN_SLOTS
        val xOffset = when (normalizedSlot) {
            0 -> 0.0f
            1 -> 0.42f
            2 -> -0.42f
            else -> 0.0f
        }
        val yOffset = when (normalizedSlot) {
            0 -> 0.42f
            1 -> 0.24f
            2 -> 0.24f
            else -> -0.02f
        }
        return Pose(Vector3(xOffset, yOffset, -VISIBLE_NOTE_DISTANCE_METERS))
    }

    private fun removeNode(id: Long, unpersistAnchor: Boolean) {
        val node = nodes.remove(id) ?: return
        cancelStickerJobs(id)
        runCatching { node.panel.parent = null }
        runCatching { node.panel.removeAllComponents() }
        runCatching { node.view.disposeComposition() }
        if (unpersistAnchor) {
            when (node.anchorProvider) {
                ANCHOR_PROVIDER_JETPACK_XR -> runCatching { Anchor.unpersist(session, node.anchorUuid) }
                ANCHOR_PROVIDER_SESSION_XR -> sessionAnchors.remove(node.anchorUuid)
                ANCHOR_PROVIDER_ACTIVITY_SPACE -> activitySpacePoses.remove(node.anchorUuid)
            }
        } else if (node.anchorProvider == ANCHOR_PROVIDER_SESSION_XR) {
            sessionAnchors.remove(node.anchorUuid)
        } else if (node.anchorProvider == ANCHOR_PROVIDER_ACTIVITY_SPACE) {
            activitySpacePoses.remove(node.anchorUuid)
        }
        runCatching { node.anchor?.detach() }
    }

    private fun cancelStickerJobs(id: Long) {
        promotionJobs.remove(id)?.cancel()
        moveAnchorJobs.remove(id)?.cancel()
        promotingStickerIds.remove(id)
    }

    private var SpatialStickerNode.sticker: TodoSticker
        get() = stickerState.value
        set(value) {
            stickerState.value = value
            panel.contentDescription = activity.getString(R.string.content_description_todo_note, value.text)
        }

    private var SpatialStickerNode.nowMillis: Long
        get() = nowState.value
        set(value) {
            nowState.value = value
        }

    private fun SpatialStickerNode.applySize(sticker: TodoSticker) {
        panel.size = sticker.noteSize()
        movableComponent.size = sticker.noteMoveSize()
    }

    private fun TodoSticker.noteSize(): FloatSize2d {
        val scale = normalizedSizeScale()
        return FloatSize2d(NOTE_WIDTH_METERS * scale, NOTE_HEIGHT_METERS * scale)
    }

    private fun TodoSticker.noteMoveSize(): FloatSize3d {
        val scale = normalizedSizeScale()
        return FloatSize3d(
            NOTE_WIDTH_METERS * scale,
            NOTE_HEIGHT_METERS * scale,
            NOTE_DRAG_DEPTH_METERS
        )
    }

    private data class CreatedAnchor(
        val result: AnchorResult,
        val activitySpacePose: Pose
    )

    private data class SpatialStickerNode(
        val anchorProvider: String?,
        val anchorUuid: UUID,
        val anchor: Anchor?,
        val anchorEntity: AnchorEntity?,
        val panel: PanelEntity,
        val view: ComposeView,
        val movableComponent: MovableComponent,
        val stickerState: MutableState<TodoSticker>,
        val nowState: MutableState<Long>
    )

    private companion object {
        const val TAG = "XrStickerScene"
        const val ANCHOR_PROVIDER_JETPACK_XR = "jetpack_xr_anchor"
        const val ANCHOR_PROVIDER_SESSION_XR = "jetpack_xr_session_anchor"
        const val ANCHOR_PROVIDER_ACTIVITY_SPACE = "jetpack_xr_activity_space"
        const val ANCHOR_PERSIST_TIMEOUT_MILLIS = 60_000L
        const val DEFAULT_NOTE_DISTANCE_METERS = 1.0f
        const val VISIBLE_NOTE_DISTANCE_METERS = 0.65f
        const val NOTE_WIDTH_METERS = 0.68f
        const val NOTE_HEIGHT_METERS = 0.54f
        const val NOTE_DRAG_DEPTH_METERS = 0.1f
        const val NOTE_CORNER_RADIUS_METERS = 0.025f
        const val ACTIVITY_SPACE_SPAWN_SLOTS = 4
        val TEMPORARY_ANCHOR_PROVIDERS = setOf(
            ANCHOR_PROVIDER_SESSION_XR,
            ANCHOR_PROVIDER_ACTIVITY_SPACE
        )

        val TodoSticker.isSpatiallyAnchored: Boolean
            get() = anchorProvider in setOf(
                ANCHOR_PROVIDER_JETPACK_XR,
                ANCHOR_PROVIDER_SESSION_XR,
                ANCHOR_PROVIDER_ACTIVITY_SPACE
            ) && anchorId != null
    }
}

private fun Pose.toStickerSpatialPose(): StickerSpatialPose =
    StickerSpatialPose(
        translationX = translation.x,
        translationY = translation.y,
        translationZ = translation.z,
        rotationX = rotation.x,
        rotationY = rotation.y,
        rotationZ = rotation.z,
        rotationW = rotation.w
    )

private fun StickerSpatialPose.toPose(): Pose =
    Pose(
        translation = Vector3(translationX, translationY, translationZ),
        rotation = Quaternion(rotationX, rotationY, rotationZ, rotationW)
    )

private const val DEFAULT_NOTE_SIZE_SCALE = 1f
private const val MIN_NOTE_SIZE_SCALE = 0.7f
private const val MAX_NOTE_SIZE_SCALE = 1.8f
private const val NOTE_SIZE_STEP = 0.15f
private const val SNOOZE_SHORT_MINUTES = 5
private const val SNOOZE_LONG_MINUTES = 15

private fun TodoSticker.normalizedSizeScale(): Float =
    if (sizeScale.isFinite()) {
        sizeScale.coerceIn(MIN_NOTE_SIZE_SCALE, MAX_NOTE_SIZE_SCALE)
    } else {
        DEFAULT_NOTE_SIZE_SCALE
    }

@Composable
private fun SpatialStickerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MaterialTheme.colorScheme.copy(
            primary = Color(0xFF2F6B5F),
            onPrimary = Color.White,
            surface = Color(0xFFFFF4A8),
            onSurface = Color(0xFF25251D)
        ),
        content = content
    )
}

@Composable
private fun SpatialStickerCard(
    sticker: TodoSticker,
    nowMillis: Long,
    onUpdateText: (String) -> Unit,
    onUpdateAlarm: (String) -> Unit,
    onResize: (Float) -> Unit,
    onUpdateStyle: (TodoStickerColor, TodoStickerPriority) -> Unit,
    onSnooze: (Int) -> Unit,
    onDone: () -> Unit,
    onDelete: () -> Unit
) {
    val expired = sticker.isTimerExpired(nowMillis)
    val timerText = sticker.timerText(nowMillis)
    val metadataText = listOfNotNull(
        timerText,
        sticker.priority.labelText(),
        sticker.pinQuality.labelText()
    ).joinToString(stringResource(R.string.metadata_separator))
    val sizeScale = sticker.normalizedSizeScale()
    var editing by remember(sticker.id) { mutableStateOf(false) }
    var editingText by remember(sticker.id) { mutableStateOf(sticker.text) }
    val existingAlarmTimeText = sticker.dueAtMillis?.toClockTimeText().orEmpty()
    var editingAlarmTimeText by remember(sticker.id, existingAlarmTimeText) {
        mutableStateOf(existingAlarmTimeText)
    }
    var selectedColor by remember(sticker.id) { mutableStateOf(sticker.color) }
    var selectedPriority by remember(sticker.id) { mutableStateOf(sticker.priority) }
    val defaultNoteText = stringResource(R.string.default_note_text)

    LaunchedEffect(sticker.text, sticker.dueAtMillis, sticker.color, sticker.priority, editing) {
        if (!editing) {
            editingText = sticker.text
            editingAlarmTimeText = existingAlarmTimeText
            selectedColor = sticker.color
            selectedPriority = sticker.priority
        }
    }

    Card(
        modifier = Modifier.fillMaxSize(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = when {
                sticker.done -> Color(0xFFDDE1DA)
                expired -> Color(0xFFFFC7B8)
                else -> sticker.color.backgroundColor()
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (editing) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    TextField(
                        value = editingText,
                        onValueChange = { editingText = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text(stringResource(R.string.label_todo)) }
                    )
                    TextField(
                        value = editingAlarmTimeText,
                        onValueChange = { editingAlarmTimeText = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text(stringResource(R.string.label_alarm_time)) },
                        placeholder = { Text(stringResource(R.string.placeholder_alarm_time)) }
                    )
                    SpatialStickerStyleControls(
                        selectedColor = selectedColor,
                        selectedPriority = selectedPriority,
                        onColorSelected = { selectedColor = it },
                        onPrioritySelected = { selectedPriority = it }
                    )
                }
            } else {
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
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 22.sp
                    )
                    if (metadataText.isNotBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = metadataText,
                            color = if (expired) Color(0xFF8B2F1D) else Color(0xFF586053),
                            fontSize = 13.sp,
                            fontWeight = if (expired) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                    if (expired) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                modifier = Modifier.weight(1f),
                                onClick = { onSnooze(SNOOZE_SHORT_MINUTES) }
                            ) {
                                Text(stringResource(R.string.action_snooze_5), fontSize = 11.sp)
                            }
                            OutlinedButton(
                                modifier = Modifier.weight(1f),
                                onClick = { onSnooze(SNOOZE_LONG_MINUTES) }
                            ) {
                                Text(stringResource(R.string.action_snooze_15), fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
            if (!editing) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        modifier = Modifier.weight(1f),
                        enabled = sizeScale > MIN_NOTE_SIZE_SCALE,
                        onClick = {
                            onResize((sizeScale - NOTE_SIZE_STEP).coerceIn(MIN_NOTE_SIZE_SCALE, MAX_NOTE_SIZE_SCALE))
                        }
                    ) {
                        Text(stringResource(R.string.action_size_smaller), fontSize = 11.sp)
                    }
                    OutlinedButton(
                        modifier = Modifier.weight(1f),
                        enabled = sizeScale < MAX_NOTE_SIZE_SCALE,
                        onClick = {
                            onResize((sizeScale + NOTE_SIZE_STEP).coerceIn(MIN_NOTE_SIZE_SCALE, MAX_NOTE_SIZE_SCALE))
                        }
                    ) {
                        Text(stringResource(R.string.action_size_larger), fontSize = 11.sp)
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (editing) {
                    OutlinedButton(
                        modifier = Modifier.weight(1f),
                        onClick = {
                            editingText = sticker.text
                            editingAlarmTimeText = existingAlarmTimeText
                            selectedColor = sticker.color
                            selectedPriority = sticker.priority
                            editing = false
                        }
                    ) {
                        Text(stringResource(R.string.action_cancel), fontSize = 12.sp)
                    }
                    Button(
                        modifier = Modifier.weight(1f),
                        onClick = {
                            onUpdateText(editingText.ifBlank { defaultNoteText })
                            onUpdateAlarm(editingAlarmTimeText)
                            onUpdateStyle(selectedColor, selectedPriority)
                            editing = false
                        }
                    ) {
                        Text(stringResource(R.string.action_save), fontSize = 12.sp)
                    }
                } else {
                    OutlinedButton(
                        modifier = Modifier.weight(1f),
                        onClick = {
                            editingText = sticker.text
                            editing = true
                        }
                    ) {
                        Text(stringResource(R.string.action_edit), fontSize = 12.sp)
                    }
                    OutlinedButton(
                        modifier = Modifier.weight(1f),
                        onClick = onDone
                    ) {
                        Text(
                            stringResource(
                                if (sticker.done) R.string.action_undo else R.string.action_done
                            ),
                            fontSize = 12.sp
                        )
                    }
                    Button(
                        modifier = Modifier.weight(1f),
                        onClick = onDelete
                    ) {
                        Text(stringResource(R.string.action_delete), fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun SpatialStickerStyleControls(
    selectedColor: TodoStickerColor,
    selectedPriority: TodoStickerPriority,
    onColorSelected: (TodoStickerColor) -> Unit,
    onPrioritySelected: (TodoStickerPriority) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TodoStickerColor.entries.forEach { color ->
                val label = color.labelText()
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .background(color.backgroundColor(), RoundedCornerShape(7.dp))
                        .border(
                            width = if (color == selectedColor) 3.dp else 1.dp,
                            color = if (color == selectedColor) Color(0xFF1F2320) else Color(0xFF7B8177),
                            shape = RoundedCornerShape(7.dp)
                        )
                        .semantics { contentDescription = label }
                        .clickable { onColorSelected(color) }
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TodoStickerPriority.entries.forEach { priority ->
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    onClick = { onPrioritySelected(priority) },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = priority.shortLabelText(),
                        fontSize = 10.sp,
                        fontWeight = if (priority == selectedPriority) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }
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
private fun StickerPinQuality.labelText(): String? =
    when (this) {
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
private fun TodoStickerPriority.shortLabelText(): String =
    when (this) {
        TodoStickerPriority.LOW -> stringResource(R.string.priority_low_short)
        TodoStickerPriority.NORMAL -> stringResource(R.string.priority_normal_short)
        TodoStickerPriority.HIGH -> stringResource(R.string.priority_high_short)
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
