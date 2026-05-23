# MVVM Guide

Sticker should grow as an MVVM Android app.

## Roles

## Compose UI

The UI layer is Android UI code: activities, composables, dialogs, controls, and future XR rendering surfaces. It can request permissions, forward taps, and render state, but it should not own app decisions.

Examples:

- `MainActivity`
- Composable screen functions
- Sticky note UI
- Permission and install prompts

## ViewModel

The ViewModel owns screen state and user actions. It exposes a single state object for the UI to render and receives actions from the UI through method calls.

Example state:

```kotlin
data class StickerUiState(
    val draftText: String = "",
    val selectedTimerMinutes: Int? = 15,
    val stickers: List<TodoStickerUiModel> = emptyList(),
    val status: UiText = UiText.resource(R.string.status_ready),
    val nowMillis: Long = System.currentTimeMillis()
)
```

Example actions:

```kotlin
fun onDraftChanged(text: String)
fun onTimerSelected(minutes: Int?)
fun onPlaceRequested(text: String, fallbackText: String): RoomPlacementRequest
fun onRoomPlacementSucceeded(text: String, timerDurationMillis: Long?, anchorProvider: String, anchorId: String)
fun onStickerTextUpdated(id: Long, text: String)
fun onStickerToggled(id: Long)
fun onStickerRemoved(id: Long)
```

The ViewModel should not stream one-shot commands back to the UI. For UI-owned platform work such as spawning a live Jetpack XR note, the UI asks the ViewModel for a `RoomPlacementRequest`, performs the XR side effect, then reports success or failure back through ViewModel methods.

## Model

The Model layer is domain and data code. It should not depend on Android UI classes.

Examples:

- Todo sticker domain models
- Repositories
- Local persistence
- Cloud or spatial anchor metadata

Sticker stores todo text, completion state, timer data, and persisted room anchor UUIDs through Room in the `data` package. Jetpack XR sessions, ARCore anchors, and SceneCore entities stay in the `xr` layer because they are live runtime resources, not persistent domain data. Persist only stable anchor IDs, provider names, timestamps, and note state.

## Dependency Injection

Sticker uses Hilt for dependency injection. Activities should not manually create databases, repositories, or ViewModel factories. Prefer constructor injection for owned classes, Hilt modules for framework or third-party classes, `@HiltViewModel` for ViewModels, and `@AndroidEntryPoint` for Android entry points.

## Compose Rule

Sticker uses Jetpack Compose for UI. Do not introduce new View-based screens or manual `LinearLayout`/`TextView`/`Button` trees unless there is a deliberate Compose interop reason.

## XR Direction

Sticker targets Galaxy XR. Do not use the mobile ARCore dependency (`com.google.ar:core`) or Google Play Services for AR. Use Jetpack XR, SceneCore, and ARCore for Jetpack XR (`androidx.xr.*`) for spatial panels, world tracking, hit testing, and persistent anchors.

The walk-and-place flow asks the `xr` layer to create and persist a room anchor, then the ViewModel saves the todo with that anchor UUID. If the draft is blank, Compose passes a string-resource-backed fallback note label into the ViewModel so spawning still works without giving the ViewModel a `Context`. The `xr` layer observes Room-backed stickers and renders each placed note as its own SceneCore panel. Edits from the main panel or spatial note panel flow back through `StickerViewModel.onStickerTextUpdated`, then into the repository and Room.

This keeps the Android/XR runtime objects in the UI/runtime layer while application state remains in Room and ViewModel state flows.

## Rule Of Thumb

If the code answers "what should happen?", put it in a ViewModel, domain class, or repository.

If the code answers "how should this be drawn or forwarded to Android?", put it in Compose UI.
