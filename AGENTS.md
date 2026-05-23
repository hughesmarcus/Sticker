# Project Instructions

This Android project must use MVVM for app features.
It must use Hilt for dependency injection.
It must target Galaxy XR with Jetpack XR APIs, not mobile ARCore / Google Play Services for AR.
It must use Jetpack Compose for UI.

## Architecture Rules

- Keep activities and composables thin. They should handle Android lifecycle, permissions, navigation, rendering UI state, and binding UI events.
- Put screen state and user actions in `ViewModel` classes.
- Represent screen state with immutable Kotlin data classes, commonly named `SomethingUiState`.
- Use repositories for data access, persistence, AR anchor persistence, and service boundaries.
- Use Hilt modules, constructor injection, `@HiltAndroidApp`, `@AndroidEntryPoint`, and `@HiltViewModel` for dependency wiring.
- Avoid manually constructing databases, repositories, or ViewModel factories inside activities.
- Avoid putting business rules, todo state management, or persistence decisions directly in `Activity`, `Fragment`, or composable functions.
- Prefer unidirectional flow: UI sends events to the ViewModel, the ViewModel updates state, and the UI renders that state.
- Keep ARCore for Jetpack XR session/rendering glue isolated from todo domain state so persistent anchors can evolve without rewriting the todo model.
- Do not add `com.google.ar:core` or `com.google.ar.core` manifest metadata. Galaxy XR work should use Jetpack XR, SceneCore, and ARCore for Jetpack XR (`androidx.xr.*`) instead.
- Do not add new View-based UI unless it is a deliberate Compose interop boundary.

## Expected Package Shape

Use this package layout as the project grows:

```text
com.sticker.todoar
  xr/          Jetpack XR, SceneCore, ARCore for Jetpack XR, spatial anchors
  data/        repositories, persistence, DTOs
  domain/      todo models and app rules
  ui/          activities, composables, UI components
  ui/<screen>/ ViewModels and UiState for each screen
```

## Before Adding Features

When adding or changing app behavior, first decide where it belongs:

- UI-only gesture or layout behavior: Compose code in `ui`
- XR tracking/rendering behavior: `xr`
- Todo state, completion, deletion, filtering: `domain` and ViewModel
- Saved notes, timer due times, anchor IDs, sync, or storage: `data`

If a change would make `MainActivity` own more todo behavior, introduce or extend a ViewModel instead.
If a change needs world tracking, use ARCore for Jetpack XR and keep the API behind an `xr` package boundary.
