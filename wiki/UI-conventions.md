<!-- Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com> -->

# UI conventions

## Shared components

Use `AppTopBar` and `ScreenBottomBar` from `resources/ScreenBars.kt` rather
than creating screen-specific navigation bars. Primary signed-in screens show
the shared bottom bar; secondary screens use the shared top bar when they need
a title or back action.

## Theme colors

Use `MaterialTheme.colorScheme` or `LocalAppUiColors.current` from `ui/theme`
for screen colors. Keep `Color(0x...)` values in `ui/theme` only. Put new
reusable colors in the theme tokens so light and dark mode stay coordinated.

## Back and bottom bar placement

Put a screen's back action in the leading slot of `AppTopBar`. Do not add a
second back button in screen content. Keep `ScreenBottomBar` at the bottom of
the scaffold, with the current destination selected and navigation/placeholder
actions wired to callbacks.

## Test tags

Declare stable Compose test tags in `resources/C.kt`, under `C.Tag`, and use
those constants with `Modifier.testTag`. Avoid duplicating literal tag strings
in screens and tests.

## Screen states

Render loading, empty, and error states explicitly rather than showing a blank
screen. Loading uses a progress indicator and status text; empty states explain
that there is no content and provide a relevant next action when possible;
errors show the message and a retry action when retrying is supported.
