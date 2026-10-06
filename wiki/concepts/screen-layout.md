---
type: concept
sources: [NOTES.md#round-8, NOTES.md#round-10, NOTES.md#round-11, NOTES.md#review, PLAN.md#next]
updated: 2026-10-06
tags: [layout, ui, phones]
---

# Screen layout

Adventure was designed for a fixed 480x270 screen (270x480 in portrait), and its viewports
stretched that onto any shape. Browsers and phones come in every shape, and they resize and
rotate.

## Canvas sizing (Round 8)
- **Black screen on a real GPU.** Forge fixes its screen size in `create()`. A page started
  hidden had a size of 0x0, and scissoring removed everything. Now the canvas is drawn at the window's size
  at startup, and `create()` waits for a real size.
- Portrait windows get Forge's portrait layout.
- A full-screen button, which also locks the orientation on Android.

> [!note] Superseded in part
> The canvas sizing here (`100vh`, drawn at CSS pixels) was replaced on 2026-10-06: the canvas follows the visible viewport and is drawn at the device pixel ratio, capped at 2. See [[display-and-viewport]].

## Extending the design size (Round 10, Forge patch)
- `Scene.getViewWidth/Height` extend the design size to the screen's aspect. Map and world
  stages **show more map**. HUD and menus keep their layouts.
- **`ViewLayout`** (a new Forge class) moves each element, or group of touching elements, with
  its nearer edge, and stretches full-size ones. It keeps sizes set later, and looks through
  zero-size container groups (GameHUD's map, hud, menu and avatar groups).
- The canvas fills the window and follows resizes. gdx-teavm's automatic sizing calls
  `Forge.resize`, which now updates the screen size and lays the scenes out again.
- Scenes are laid out again on *any* size change (not only shape changes), and on first use for scenes
  created after a resize (review fix).
- Orientation stays as it was at startup.

## Seams at fractional scales (Round 11)
With the view extended, one map unit is rarely a whole number of pixels (at 1155 px wide it's 77/32 px,
so a 16-unit tile is 38.5 px). With the camera on whole units, every other tile edge lands
exactly on a pixel centre. The GPU's sub-pixel vertex snapping decides which tile gets that
pixel, and nearest sampling then reads the neighbouring tile in the tileset image. The result is
white or orange lines, one pixel wide, while walking. Upstream rarely hits this, because 1080p is
exactly 4 px per unit. A Forge patch in `TemplateTmxMapLoader` insets every tile region, and the
copies that tile objects carry, by 0.1 texel. An inset of 0.01 was not enough, because
SwiftShader snaps to 1/16 px. `find-seams.py` ([[webtest-harness]]) found no lines in 56 cave
frames at 1155x898 and 1710x898.
The same happened in the UI. TenPatch and libGDX `NinePatch` guard patch edges only for linear
filtering, and the skin uses nearest filtering, so dark lines crossed buttons at 1155 px. Both are
shadowed with the same 0.1-texel inset (web layer, `com/ray3k/tenpatch`, `com/badlogic/gdx/graphics/g2d`).
`find-seams.py` only finds bright lines, so check dark seams by eye. At a lower threshold it
also matches UI borders.
The line across the duel background is Forge's own field separator, drawn in every layout.

## VS screen and full-screen button (Round 11)
The VS screen's name font scales only with the screen's aspect ratio. A Forge patch shrinks it
just enough for both names to fit. The page's full-screen button is at the top centre and hides
itself, because every corner has game buttons. It shows after loading and when the pointer rests
near it.
Seen while checking: where the view is wider than a small map, the area past the map is the
clear colour (0,0,0) next to the map's own (6,6,8), which makes a faint band edge.

## Open ([[open-issues]])
- Full screen should fill the screen, **including rotation on phones** (user-reported, PLAN
  "Next"). This was done for the page and live resizes in Round 10. Phone rotation is untested.
- Extended views much taller or wider than 960 units can show unloaded world chunks.
- Check the landscape hand layout (hand drawn as a column on the right) against Forge.

## Files
`Scene`, `UIScene`, `HudScene`, `TileMapScene`, `RewardScene`, `GameHUD`, `GameStage`,
`RewardActor`, `ViewLayout` in `patches/forge-web.patch` ([[src-forge-web-patch]]).
`index.html` handles canvas sizing (`--game-aspect`, earlier CSS scaling).
