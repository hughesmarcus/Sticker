# Sticker Architecture

## MVP Shape

The app keeps the core loop intentionally small:

1. `StickerViewModel` owns screen state and todo actions.
2. Room stores todo text, completion state, timers, and placement metadata.
3. Hilt wires the database, DAO, repository, and ViewModel.
4. `MainActivity` renders a Galaxy XR-compatible Compose panel.
5. Spatial anchors and spatial panels live in the Jetpack XR `xr` package.

This avoids the mobile ARCore runtime and Google Play Services for AR while keeping app state in MVVM layers.

## Android Architecture Recommendation Check

The project follows the Android architecture recommendations at https://developer.android.com/topic/architecture/recommendations:

- Clear layers: Compose and Activity code in `ui`, runtime spatial rendering in `xr`, repository and Room code in `data`, stable app models in `domain`.
- Unidirectional data flow: `StickerViewModel` exposes one `uiState` `StateFlow`; UI calls ViewModel methods for user actions.
- Lifecycle-aware collection: Compose collects state with `collectAsStateWithLifecycle`, and Activity-side XR sync uses `repeatOnLifecycle`.
- Data access through repository: UI and ViewModel do not call Room DAO methods directly.
- ViewModel independence: the ViewModel has no `Activity`, `Context`, `Resources`, XR `Session`, or SceneCore dependencies.
- No ViewModel-to-UI event stream: UI-owned XR spawning is requested with a return value, then success or failure is reported back to the ViewModel.
- Dependency injection: Hilt owns database, DAO, repository, and ViewModel construction.
- Testability: the repository is injected behind an interface, with JVM tests covering ViewModel state flow behavior and repository mapping/mutation behavior.

## Current Package Shape

```text
com.sticker.todoar
  data/        Room database, DAO, repository
  domain/      Todo sticker model
  ui/          MainActivity and Compose UI
  ui/stickers/ ViewModel, UiState, and placement request types
  xr/          Jetpack XR SceneCore panels and ARCore for Jetpack XR anchors
```

## Current Placement State

Todo data, note edits, timer due times, and persisted anchor UUIDs survive app restarts in Room. The main Compose panel is now the control surface. Placed notes render as independent SceneCore `PanelEntity` instances attached to ARCore for Jetpack XR anchors.

For real house-scale persistence on Galaxy XR, the app uses:

- ARCore for Jetpack XR planes, hit tests, and persistent anchors.
- SceneCore `AnchorEntity` and `PanelEntity` to show note panels in space.
- Room fields for persisted anchor UUIDs and placement metadata.

The primary XR action is direct: tapping `Spawn Note` creates a note, creates a persisted anchor when possible, and renders a draggable spatial panel. If the composer is empty, the UI supplies a string-resource-backed default note label before asking the ViewModel for a placement request. Moving the panel through SceneCore updates the anchor UUID in Room, and editing a note updates the same Room row so both the list and spatial panel stay in sync.

Spatial panels require Android XR Full Space. `MainActivity` declares `XR_ACTIVITY_START_MODE_FULL_SPACE_MANAGED` and the XR scene requests Full Space before creating anchors or panels.

If local anchor persistence is unavailable on the current device/session, Sticker still spawns a live draggable note using a session-only anchor. If room tracking is not ready yet, Sticker creates a larger ActivitySpace note offset to the user's side so tapping `Spawn Note` still produces a visible spatial panel. The XR layer retries those temporary notes and promotes them to persisted `jetpack_xr_anchor` rows as soon as tracking can create and persist an anchor. Dragging a temporary note onto a tracked surface can also produce a persistent anchor. Only `jetpack_xr_anchor` placements are expected to survive taking the headset off or restarting the app.

## Current Data Model Shape

```kotlin
data class TodoSticker(
    val id: Long,
    val text: String,
    val done: Boolean,
    val timerDurationMillis: Long?,
    val dueAtMillis: Long?,
    val placedAtMillis: Long?,
    val anchorProvider: String?,
    val anchorId: String?,
    val createdAtMillis: Long,
    val updatedAtMillis: Long
)
```
