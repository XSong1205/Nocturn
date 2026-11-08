# Page navigation

The page host follows the Android [Navigation 3 animations recipe](https://github.com/android/nav3-recipes/tree/main/app/src/main/java/com/example/nav3recipes/animations).

`AppNavigation.kt` adapts the view model's selected tab and page stack into Navigation 3 entries. A tab, stack position and destination identify each entry, so repeated destinations retain independent saveable state. The saveable-state decorator restores the previous entry's state on back and removes state when an entry leaves the stack.

`PageTransitions.kt` contains only native Compose `ContentTransform` definitions:

- Push and pop use opposite full-width horizontal slides, mirrored in RTL.
- Predictive back seeks the same pop transform from either swipe edge. Keeping the transform identical through release prevents a direction change on commit or cancellation.
- Switching top-level tabs uses a short crossfade.
- Compose controls animation duration scaling, including disabled system animations.

`NavDisplay` owns gesture progress, settling and scene lifecycle. A completed back updates the stack immediately; no application coroutine, timer, second animation or fake transition duration delays the pop. The explicit scene/state overload allows page back to be disabled while the player handles it.

The routed backdrop clips to its rectangular bounds because its ambient circles otherwise paint beyond the moving page. There are no extra animated page layers, rounded page masks or scrims.

Navigation buttons use `dropUnlessResumed`, or its argument-taking counterpart `rememberResumedNavigation`, so entering and outgoing destinations cannot trigger another page jump while their scene is only STARTED. System back remains handled by Navigation 3. Playback and ordinary screen actions are independent of these navigation guards.

## Validation

Run the focused host regression suites:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests 'dev.nocturn.app.ui.AppNavigationTest' --tests 'dev.nocturn.app.ui.PredictiveBackPixelsTest' --tests 'dev.nocturn.app.ui.NavigationReducedMotionTest' --tests 'dev.nocturn.app.ui.PlayerSheetTest'
```

These cover rapid navigation and tab replacement, repeated destinations and fresh visits, scroll restoration, predictive cancellation/restart/commit, both gesture edges, RTL, page composition stability, lifecycle guards, zero-duration animations, opaque page pixels and player back priority. Pixel screenshots are host renders under `app/build/screenshots/`, not device screenshots.
