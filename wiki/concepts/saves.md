---
type: concept
sources: [NOTES.md#round-8, NOTES.md#round-11, web/src/main/java/org/teavm/classlib/java/io/TObjectOutputStream.java, web/src/main/java/forgeweb/fs/UserDataStore.java]
updated: 2026-10-01
tags: [saves, serialization, indexeddb]
---

# Saves

Adventure saves (`WorldSave`, `SaveLoadScene`) and some deck data use Java serialization
(`ObjectOutputStream`/`ObjectInputStream`), which [[teavm|TeaVM]] doesn't have. The port
supplies its own object streams with **its own tagged format** and persists files in
IndexedDB.

## Format and implementation
The classlib shadows `TObjectOutputStream` and `TObjectInputStream` (with `TObjectStreamClass`
and others) handle:
- references and cycles, arrays, collections by kind, `EnumMap`
- plain fields, by reflection (fields exposed by `WebReflection`, see [[reflection-on-teavm]])
- private `writeObject`/`readObject` and `readResolve`, only for classes in `SerialHooks`
- allocation without constructors, via the class's JS constructor (`@JSBody` in
  `allocateImpl`, which is JS-only and a blocker for wasm, see [[stay-on-js-backend]])
- `java.util.UUID`, which isn't `Serializable` in TeaVM, via its own stream tag

JDK-format streams are **rejected with an `IOException`** (so desktop saves don't load, and
vice versa). A [[selftest]] check covers this.

## Persistence
Files under `/forge/data/` are mirrored to IndexedDB by `UserDataStore` (see
[[virtual-file-system]]). Saving, reloading the page and loading works (Round 8).

## Hooks silently skipped (Round 11)
The Load screen froze as soon as any save existed. `SaveLoadScene.getSplitHeaderName` called
`.contains` on a null `WorldSaveHeader.name`. The narrowed reflection list never made the
private hooks callable, so `SerialHooks` found none, and the streams fell back to plain fields.
`WorldSaveHeader` exposes no plain fields, so the header was written and read back **empty**,
with no error. The same happened to `PaperCard.readObject`, which restores the transient `rules`
field (cards in saved booster decks had no rules), and to `Deck.readResolve`. The fix is in
[[reflection-on-teavm|WebReflection]] (`SERIAL_CLASSES`). `SerialHooks` now warns at startup when
a listed class has no hooks, and three [[selftest]] checks cover it. A Forge patch makes
`SaveLoadScene.updateFiles` skip an unreadable save, and log it, instead of breaking the whole
list. Verified: quicksave, reload, the Load screen lists the save with its date and location, and
loading restores the map and the player.
Save preview thumbnails were black. Forge reads the framebuffer on a key press or autosave, and
WebGL had cleared it. The fix is `preserveDrawingBuffer`, together with `ScreenUtil` following
the screen size.

## Forge bug found along the way
`AdventurePlayer` saved `colorIdentity` as a byte and read it as a string, so every loaded
character became colorless, **on desktop too**. It is fixed in the patch and is an upstream
candidate ([[bug-catalog]]).

## Autosave cost
An autosave (before every duel, town or dungeon) re-encoded the world map PNG and two 700x700
arrays each time. `World` now encodes them once (Forge patch). Compression uses the browser's
`CompressionStream` through the shadowed `DeflaterOutputStream` (3 MB in 57 ms). See
[[memory-budget]] for the transient spike this leaves.

## Planned (PLAN Phase 6)
A save header with a version and the Forge commit, tolerating added and removed fields. Today a Forge
update that changes a saved class can break old saves. See [[plan-phases]].

## Verified by
[[selftest]]: save values round-trip, header round-trip (Load list), hooks callable, EnumMap
keyed by a Forge enum, deck round-trip with boosters (`PaperCard.readObject`).
