# Meteor GUI Position Fix

Client-side Meteor addon for Minecraft 1.21.11 / Meteor 1.21.11-82.

## What it fixes

Meteor's ClickGUI rebuilds every category window from scratch each time the
menu is opened, and re-runs a fresh grid/cascade layout for them. Vanilla
Meteor does have its own save/restore for a dragged window's position, but
it's keyed by the window's `id` string, stored inside the current
`GuiTheme`, and it only kicks in for windows that have actually been
dragged. In a large modpack with many addon-created "category manager"
style groupings, most windows are never individually dragged, and some
don't keep a stable `id` across a menu rebuild at all — so they keep
reflowing to a fresh layout on every reopen with no way to "opt in" short
of manually dragging every single one.

## How the fix works

This addon hooks Meteor's generic `WWindow` / `WContainer` API directly —
what every category window is built from, regardless of which addon
created it — rather than any one specific container class:

- **Auto-pin on first layout.** `WWindowLayoutMixin` hooks the tail of
  `WWindow.onCalculateWidgetPositions()`. The *first* time any window
  completes a layout pass, its resulting position is snapshotted and
  treated as permanent. Every later reopen restores exactly that spot —
  no drag required. This runs after vanilla's own restore, after child
  layout, and after Meteor's own "moved" catch-up logic, so it has the
  final say for that layout pass regardless of what any container
  (vanilla or a custom "Category Manager") did earlier in the same pass.
- **Drag still wins.** `WContainerMoveMixin` hooks `WContainer.move()` —
  what runs whenever the user drags any window's header — and, when the
  thing being moved is a `WWindow`, immediately updates its pinned
  position to match. A manual drag always overrides the auto-pinned spot.
- **Screen-edge clamping**, in both of the above: a window can't be
  dragged past the edge of the screen (clamped live, every frame, while
  dragging), and is also re-clamped at layout time, which additionally
  covers the game window/GUI scale changing after the fact — e.g. an
  on-screen control layer toggling on a touch-based launcher.
- **No-overlap snap on release (v3.4 / v3.4.1).** While you hold the mouse button
  you can freely drag a category *through* other categories. As soon as
  you release, `WHeaderReleaseMixin` + `WindowPositionMemory.resolveOverlaps`
  move *only* the released window to the nearest free on-screen spot (side-flush candidates + spiral search) so its
  visible rectangle (including a small gap) no longer intersects any
  sibling. Only the released window is moved; others stay where they are.
  The final non-overlapping position is then saved.
- **`WindowPositionMemory`** is the persistent store behind all of this,
  independent of Meteor's own theme-bound config. It keys primarily by
  the window's `id`, falling back to its title when the id is missing or
  unstable. Positions are kept in memory for the session and written to
  `config/meteor-gui-position-fix.json`, so placement survives a full
  game restart too, not just a menu reopen.
- **`WWindowTitleAccessor`** is a small `@Accessor` mixin exposing
  `WWindow`'s otherwise-protected `title` field, used only as that
  fallback key.

All injectors are marked `require = 0` and wrapped defensively, so if a
future Meteor update changes one of these methods, the fix simply stops
applying instead of ever blocking the ClickGUI from opening.

### The collapsed-window height bug (fixed in v3.2 / v3.3)

`WWindow` never overrides `onCalculateSize()`, so its `height` field
always reflects the window's **fully expanded** content size (header +
every module inside it) — even while the category is collapsed to just
its header bar. Only rendering knows the real, currently-visible height,
via `(height - header.height) * animProgress + header.height`.

Both vanilla Meteor's own position restore *and* earlier versions of
this fix used the raw `height` field for on-screen clamping. That means a
collapsed window dragged to, say, the bottom of the screen would get
clamped back up near the top on the next layout pass — the clamp check
`y + height > screenHeight` fails using the expanded height, even though
the collapsed header alone would fit fine. This is what caused categories
to keep "snapping back" after being moved and reopened, and is exactly
why closed categories could only be moved to the screen edge that an
*open* category would fit.

v3.3 fixes this in four places:

1. `WindowPositionMemory.effectiveHeight()` reproduces the render-time
   math using reflection. (The `header` field is the protected nested type
   `WWindow.WHeader`; Mixin @Accessor cannot target it with a `WWidget`
   return type, which previously caused a crash on load.) Collapsed
   windows are clamped against the height they actually occupy.
2. `WWindowClampMixin` redirects the two `height` field reads inside
   Meteor's own `onCalculateWidgetPositions` clamp so vanilla's restore
   also uses the visible height. Closed categories therefore stay where
   you put them across menu close/reopen.
3. Clamping and position restore are applied as real `move()` calls (with
   a re-entrancy guard) so the header, background and all children stay
   glued to the window. Directly writing `x`/`y` previously left the
   background stuck on-screen while the category was dragged outside.
4. `WContainerHitOrderMixin` makes mouse clicks/releases walk the child
   list back-to-front (matching paint order). Overlapping categories no
   longer steal clicks from the window drawn on top, so you can always
   drag or collapse the one you can see. Categories are also kept below
   the top tab bar so they don't park under the Modules/Config strip.

### Confirming the mixins are actually applying

The addon and its hooks log a line the first time they run, via the
standard Minecraft/Fabric logger. After installing the mod and opening
the ClickGUI at least once (and dragging + releasing a category), check
`logs/latest.log` for:

```
[MeteorGuiPositionFix] Addon loaded.
[MeteorGuiPositionFix] Window layout hook is active.
[MeteorGuiPositionFix] Window move hook is active.
[MeteorGuiPositionFix] Overlap-resolve-on-release is active.
```

If the first line is missing, the addon jar itself isn't being picked up
(wrong `mods` folder, or Meteor isn't finding it as an addon). If the
first line appears but the other lines never do even after opening the
ClickGUI and dragging a window, the mixins aren't applying on that
particular setup — this can happen if the launcher uses a Fabric Loader
version or mixin service Meteor doesn't expect. That's worth reporting
back with the relevant `latest.log` lines.

## Project layout

The project follows the supplied Fabric 1.21.11 example structure:

- `src/main` — mod metadata.
- `src/client` — client-only Meteor addon and mixins.
- `fabric-loom-remap` with split environment source sets.
- Official Mojang mappings.
- Java 21.

## Build

Requirements:

- Java 21
- Internet access for Gradle, Minecraft, Fabric API and Maven dependencies.
- A Meteor Client JAR for the matching version placed in `libs/` as
  `meteor-client-1.21.11-local.jar` (compile-only; not bundled at runtime).

Run:

```bash
chmod +x gradlew
./gradlew clean build
```

The addon JAR is created in `build/libs/`.

Put the resulting JAR in the same `mods` directory as Meteor.

## Local Meteor dependency

The `libs/` directory is expected to contain the local Meteor Client JAR
used only to compile this addon (`modCompileOnly`) — it is never bundled
or loaded at runtime, since the player is expected to already have their
own real Meteor Client installed.
