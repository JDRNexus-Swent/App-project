<!-- Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com> -->

# UI conventions

The app is built with Jetpack Compose and Material 3. Prefer its components and
the shared app theme over screen-specific styling so screens feel like one
product and remain consistent in light and dark mode.

## Screen structure

Use `Scaffold` for screens with app bars or other persistent chrome. Put the
shared top and bottom bars in its `topBar` and `bottomBar` slots, and apply the
`innerPadding` passed to the content lambda so content is not obscured. Keep
screen content scrollable when it may exceed the available height, including
when the keyboard is open.

Keep composables focused on rendering UI and forwarding user actions through
callbacks. Collect screen state from the ViewModel and render from that state;
keep business logic out of composables. ViewModels depend on repository
interfaces and must not import Firebase or repository implementations.

## Shared components

Use `AppTopBar` and `ScreenBottomBar` from `resources/ScreenBars.kt` rather
than creating screen-specific navigation bars. Primary signed-in screens show
the shared bottom bar; secondary screens use the shared top bar when they need
a title or back action.

Use a top-bar title that names the current destination. Put a back action in its
leading slot on secondary screens, and use trailing actions only for actions
relevant to that screen. The bottom bar is for primary destinations; reflect the
current destination in its selected tab and route tab presses through callbacks.
Keep placeholder destinations on the shared navigation rather than adding
screen-specific navigation controls.

## Layout and components

Use Material 3 components (`Button`, `TextButton`, `OutlinedTextField`, and
similar) for standard controls. Prefer a clear hierarchy: one primary action,
secondary actions styled less prominently, and related content grouped
together. Use consistent spacing rather than one-off values; the existing
screens commonly use 8–16 dp between related elements and 16–24 dp for content
insets. Let content use available width where possible, and make long forms
scrollable instead of shrinking controls to fit.

Give interactive controls a comfortable touch target (about 48 dp or larger)
and a visible enabled, disabled, loading, or error state where applicable.
Provide meaningful accessibility labels for icon-only actions; decorative
icons should not announce redundant descriptions.

## Theme colors

Use `MaterialTheme.colorScheme` or `LocalAppUiColors.current` from `ui/theme`
for screen colors. Keep `Color(0x...)` values in `ui/theme` only. Put new
reusable colors in the theme tokens so light and dark mode stay coordinated.
The app palette uses warm browns and neutrals with gold accents. Use semantic
roles such as primary, surface, background, and on-surface/text colors rather
than choosing a color by its raw value. Avoid hard-coded colors in screens.

Use `MaterialTheme.typography` for text hierarchy instead of assigning font
sizes ad hoc. Reserve custom type styling for intentional branding or emphasis,
and keep body text readable and adaptable to system font scaling.

## Back and bottom bar placement

Put a screen's back action in the leading slot of `AppTopBar`. Do not add a
second back button in screen content. Keep `ScreenBottomBar` at the bottom of
the scaffold, with the current destination selected and navigation/placeholder
actions wired to callbacks.

## Interaction and state

Represent loading, empty, error, and success/content states explicitly. Do not
leave a blank screen while data is loading or has failed. Keep actions disabled
when they cannot be used (for example, while a request is in progress), and
show progress feedback. When retrying is supported, make retry available from
the error state. Use inline validation or supporting text near the relevant
field when possible.

## Test tags

Declare stable Compose test tags in `resources/C.kt`, under `C.Tag`, and use
those constants with `Modifier.testTag`. Avoid duplicating literal tag strings
in screens and tests. Tags should identify controls by purpose, not by their
position or displayed copy, so tests remain stable when layout or wording
changes. Tests should prefer tags for interaction targets and assert the
user-visible state as well.
