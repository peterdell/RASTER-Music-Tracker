# Plan: the Java UI port (Phase B)

Written 2026-09-26 on picking up `plans/JAVA_UI_PORT_HANDOVER.md`. This is
the dedicated plan that handover said Phase B deserves. Sections marked
**DECISION** need the user's input before the affected batch starts; the
recommendation given for each is the default if not overridden.

## What was verified before writing this

- `plans/UI_SURVEY_PLAN.md` read in full, plus the C++ code it describes:
  `RmtView.cpp` (paint loop, `Resize()`, `OnInitialUpdate()`,
  `Read/WriteRMTConfig()`), `CanvasXY.cpp`/`.h`, `Canvas.h`,
  `TextColors.h`, `GuiHelpers.cpp`, `Global.h`, `MainFrm.cpp`,
  `SongUI.h`/`.cpp` (method map + `DrawTracks()`), `TracksControl.h`,
  `GUI_Song.cpp`/`GUI_Instruments.cpp` (method maps), `Rmt.rc` (menu/
  accelerator/toolbar counts), `src/cpp/res/`.
- `dis6502` (`C:\jac\system\Java\Programming\Repositories\dis6502`), the
  user's own prior C++-to-Java port, as the precedent the handover said to
  check: it is **pure Swing** (34 files use `javax.swing`, none use
  JavaFX), built on **`com.wudsn.tools.base.gui`** - which is *already a
  Maven dependency of this project* (`pom.xml`: `com.wudsn.tools.base`).
- WUDSN Base's `gui` package contents (from the installed jar): `MainWindow`
  + `MainWindowPreferences` (a `JFrame` wrapper that persists position/
  size/state), `StatusBar`, `ElementFactory` (menus/menu items/buttons/
  toggle buttons/check-box items/icons from declarative `Action`s),
  `ModalDialog`/`StandardDialog`/`SimpleDialog`, `FileChooser`,
  `KeyStroke`, `Desktop`, `HelpDialog`, `UIManager`; and
  `com.wudsn.tools.base.repository.Action`/`NLS` - declarative actions
  (label/tooltip/accelerator) whose text lives in `Actions.properties`,
  exactly how `dis6502`'s `Actions.java`/`MainMenu.java` are built.

## Facts that shape the design

**Rendering model (C++).** One off-screen bitmap sized to the *logical*
resolution (`g_width = windowWidth * 100 / g_scaling_percentage`, same for
height; scaling 100-300%), redrawn in full and `StretchBlt`'d to the
window on every paint. Driven by a repeating timer with a 16/16/15 ms tick
cycle (`m_timerDisplayTick`, ~64 Hz) whose handler calls `RefreshScreen()`
(sets a dirty flag + invalidates); `OnDraw` then runs `Resize()`,
`g_Song.RespectBoundaries()`, `DrawAll()`, blit. `DrawAll()` order:
fill `CRGBColor::BACKGROUND` (RGB 34,50,80), `DrawInfo`, `DrawSong`,
`DrawVolumeAnalyzer`, `DrawPlayTimeCounter`, then `DrawTracks` *or*
`DrawInstrument` depending on `g_active_ti`. Derived layout values:
`g_tracklines = (g_height - (TRACKS_Y + 3*16) - 40) / 16`,
`g_line_y = g_tracklines / 2`; minimum window 800x600 mono / 1120x600
stereo (`CMainFrame::OnGetMinMaxInfo`).

**dis6502 already uses this exact rendering model**: `GraphicPanel`
paints into a fixed-size `BufferedImage` and draws it scaled with
`RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR` in
`paintComponent`; `MemoryInspectorPanel` uses a `javax.swing.Timer`. Its
`ComputerFont` documents a **real HiDPI bug** hit on Windows at 150%
display scaling: drawing pixel art through a fractional device-scale
transform produced irregular dropped/doubled rows; the fix was to
rasterize at native size into a `BufferedImage` and draw 1:1, and to
treat any scaled on-screen `drawImage` as something to verify on a real
HiDPI display. RMT's *whole UI* is one scaled blit, so this must be
tested on the user's actual display scaling early (see B1).

**The glyph sheet is already a plain file.** `src/cpp/res/gfx-8x16.bmp`
(1024x240, 8-bit indexed - `ImageIO` reads BMP natively) *is* `IDB_GFX`;
the handover's "needs exporting" item is already satisfied. Layout,
decoded from `CanvasXY.cpp`: 128 glyphs of 8x16 at `x = (char & 0x7f) << 3`;
color = vertical band `y = TextColor << 4` (`TextColor` values 0-14,
deliberately skipping 7 and 8); the 8x8 "mini" font lives in exactly
those skipped bands, `y = 112 + (TextMiniColor << 3)` (4 mini colors);
4 small 32x6 icons at `y = 122, x = (icon-1)*32`. Spaces are not drawn
(transparent). `gfx-12x24.bmp` is referenced nowhere - unused. Other
resources: 5 custom cursors (`res/*.cur`, 326 bytes each, standard 32x32
1-bit ICO-format cursors - *not* loadable by `ImageIO`; a one-time
conversion to PNG + hotspot, or a ~40-line parser, is needed), toolbar
strips (`toolbar_main_window*.bmp`, `toolbar_block*.bmp`, BMP - readable,
need slicing per button), app/file-type `.ico`s (convert to PNG, as
dis6502 did for its own icons).

**Input model.** `OnKeyDown` tracks Shift/Ctrl/Alt into globals, handles a
few global keys, then dispatches on `g_activepart` (`Part`: INFO/TRACKS/
INSTRUMENTS/SONG) to `CSong::InfoKey`/`TrackKey`/`InstrKey`/`SongKey`
(all in `GUI_Song.cpp`, 2220 lines together with `ProveKey` and the
`*CursorGoto` mouse-hit helpers), with Shift-held/`IsProveMode()`
redirecting note keys to `ProveKey` (live preview). `GUI_Instruments.cpp`
(587 lines) is the instrument editor's drawing + hit-testing, implemented
as `CInstruments` methods. Mouse: `CRmtView::MouseAction` hit-tests
against `CRmtScreenLayout` coordinates; `IsHoveredXY()` (reads
`g_mouse.pointX/Y`) drives per-character hover recoloring inside
`CanvasXY`. `EditText()` (`GuiHelpers.cpp`) is the shared text-field
editor for the song/instrument name fields (VK-code-to-char mapping,
insert/delete/home/end).

**Command surface.** 12 top-level menus (`Pokey` alone has ~12 nested
submenus), 192 `MENUITEM`s, 76 accelerators, 40 toolbar buttons across 2
toolbars (main + block; `m_ToolBarPlay`/`m_ToolBarChannels` are confirmed
dead), 203 message-map handlers in `CRmtView`, the large majority being
one-line delegations to already-ported model methods, plus
`ON_UPDATE_COMMAND_UI` enable/check logic. `CMainFrame` also embeds a
"skip N lines after note insert" combo box (0-8) in the main toolbar.

**UI state today lives in ~45 globals** (`Global.h`): transient editor
state (`g_activepart`/`g_active_ti`, modifier-key flags, `g_mouse`,
`g_cursoractview`/`g_line_y`/`g_tracklines`, `g_screenupdate`,
`g_timerGlobalCount`, `g_changes`, `g_isEditingInstrumentName`/
`is_editing_infos`, FPS debug counters) and persisted options
(`g_scaling_percentage`, the two track-line highlight intervals,
`g_tracklinealtnumbering`, `g_displayflatnotes`/`g_usegermannotation`,
`g_notesperoctave`, `TViewState g_view` - 9 view toggles, 7
`g_keyboard_*` flags + `g_keyboard_layout`, `g_SkipLinesAfterNoteInsert`,
default/last-used folder paths, `g_trackerDriverVersion`, NTSC).
Persistence is a **plain `rmt.ini` text file** (`# comment` / `NAME = value`
lines, written by `WriteRMTConfig()` next to the program), *not* the
registry - only the frame position/size uses `GetProfileInt` (registry).

**Model composition root** (the wiring every UI needs, taken verbatim from
`SongEditingTest.setUp()`): `Tracks` (+`setMaxTrackLength(64)`,
`initTracks()`) -> `Instruments` (+`initInstruments()`) ->
`new Song(instruments, tracks)` -> `new TrackClipboard()` ->
`new Undo(tracks, instruments, song, clipboard)` -> `new Messages()` ->
`new AtariTrackerDriver(new Atari())` -> `new ChannelControl(8)`;
`tracks4_8` is always an explicit parameter, never stored.

**Audio.** The C++ UI plays sound live (`CXPokey`/DirectSound, driven by
`CSong::TimerRoutine()`); keyboard note preview goes through the real
6502 driver. **The Java model has no real-time audio path at all** -
`AtariTrackerDriver.play()`/`setPokey()`/`silence()` are permanently
no-op (no 6502 without ASAP), and `AsapEmulator` is only used offline
for export (`dumpSongToPokeyStream`), playing a whole exported module.
This is the single biggest architecture question the handover did not
list - see DECISION 4.

## Design (Java)

Package `com.wudsn.tools.rmt.ui` (decided in `plans/JAVA_PORT_PLAN.md`
decision 6). Mapping from the C++ classes:

| C++ | Java | Notes |
|---|---|---|
| `CRmtApp` (`Rmt.cpp`) | `RmtApplication` (`main`) | Builds the composition root above, loads options, creates the window, starts the display timer. |
| `CMainFrame` | `RmtMainWindow` | Wraps WUDSN Base `MainWindow` (frame + `MainWindowPreferences` for position/size) + `StatusBar`; two `JToolBar`s (main, block) + the skip-lines `JComboBox`; enforces the 800/1120 x 600 minimum. |
| `CRmtView` | split three ways | (1) `TrackerPanel` (`JPanel`: owns the off-screen `BufferedImage` canvas, `paintComponent` = nearest-neighbor scaled `drawImage`, `javax.swing.Timer` ~16 ms, key/mouse listeners that dispatch exactly as `OnKeyDown`/`MouseAction`); (2) `RmtActions` + `RmtMainMenu` + command handlers (the 203 handlers as `Action`s via `ElementFactory`, thin delegations to model methods); (3) `RmtConfig` (`rmt.ini` read/write/reset, tuning config). |
| `CCanvasXY` / `CCanvas` | `CanvasXY` / `Canvas` | `Graphics2D` over the canvas `BufferedImage`; glyph sheet loaded once from the copied `gfx-8x16.bmp` classpath resource; identical blit arithmetic. Hover recoloring needs the current mouse position -> takes a `MouseState`. |
| `CSongUI`, `CTracksControl`, `CPokeyView`, `CAtariView` | same names | Drawing helpers taking `Song`/`Tracks`/... plus `UiState`/`Options` explicitly. |
| `GUI_Instruments.cpp` (`CInstruments::Draw*`/`CursorGoto`/`GetGUIArea`) | `InstrumentsUI` | **Deviation**: C++ puts instrument-editor drawing/hit-testing on the model class; Java keeps `Instruments` UI-free and puts this in a `ui` class that takes `Instruments`. Matches the model/`ui` split already decided. |
| `GUI_Song.cpp` (`InfoKey`/`TrackKey`/`InstrKey`/`SongKey`/`ProveKey`/`*CursorGoto`) | `SongInput` (or per-part handlers) | Same deviation: pure input dispatch over already-ported `Song` operations, so it lives in `ui`, taking `Song` + collaborators. Any `Song` internals it needs that aren't exposed get a getter/setter, not a `ui` class reaching into fields. |
| `GuiHelpers.cpp` | `TextFieldEditor` (`EditText`), `MouseState.isHovered`, `TrackerPanel.refreshScreen`, `StatusBar` text | `DisableEventSection` -> wait-cursor + input-disable helper, only where a real long operation exists (`dumpSongToPokeyStream`). |
| `Global.h` UI globals | `UiState` (transient) + `RmtOptions` (persisted) | Passed explicitly, per the port's "C++ global -> explicit parameter" idiom; no static singletons. |
| Dialogs | `ui` dialog classes | Standard ones on WUDSN Base `ModalDialog`/`StandardDialog`; each hands a parameter object to the already-ported `*Apply()`/exporter method. The three click-positioned popups (octave/volume/instrument select) become an undecorated `JDialog`/`JWindow` shown at the click point. |

**Testing strategy.** The renderer is fully deterministic (bitmap blits,
no fonts, no anti-aliasing), so `CanvasXY`/`SongUI` can be tested
headless: render a known model state into a `BufferedImage` and compare
pixels against golden PNGs checked into `src/java/test/...` (regenerate
deliberately when a change is intended). Key dispatch is testable at the
logic level (feed key codes, assert `Song` state) because the `Song`
operations are pure. Real windows/dialogs/mouse remain manual checks - the
handover's "state explicitly if you can't test the UI" rule applies.

## Batches

Each batch: read the C++ in full first, implement, `mvn -o clean test`,
golden-image tests where the batch draws anything, manual run where it
shows anything, `plans/NOTES.md` entry, ask before committing.

- **B0 - foundation (no window yet) - DONE (2026-09-26).** New
  `com.wudsn.tools.rmt.ui` package: `RmtScreenLayout`, `TextColor`
  (explicit band values, since 7/8 are the mini-font bands),
  `TextMiniColor`, `RgbColor`, `UiState` (the transient `Global.h` state -
  see its javadoc for the exact `g_*` mapping), `CanvasXY` + `Canvas`,
  `CursorLoader`; `EditMode` went into `model` beside its `General.h`
  siblings `Part`/`PlayMode`/`EditArea`/`KeyboardLayout`, with
  `isProveMode()`/`isSpecialProveMode()` as enum methods replacing C++'s
  free functions. Resources copied unchanged next to the classes:
  `gfx-8x16.bmp` (`ImageIO` reads the 8-bit BMP fine; converted to RGB
  once at load), the five `.cur` files, both `*-32x30.bmp` toolbar strips;
  `application.ico` converted once to `application.png` (System.Drawing).
  **`.cur` route decided: parse at runtime** (`CursorLoader`, ~60 lines:
  ICO container + 1-bpp XOR/AND masks + the hotspot the file itself
  declares) rather than a lossy PNG conversion - keeps the original assets
  authoritative. **Tests are analytic, not golden images**: each drawn
  canvas cell is compared against the exact glyph-sheet cell C++'s
  `BitBlt` would copy (per color band, mini bands, icon strip, the
  per-method space-skipping rules, prove-mode/hover recoloring), which
  tests the *mapping* rather than a snapshot - golden PNGs (and the
  user's `Rmt.exe` screenshots) start with B1's full-frame renders. Two
  listed items deliberately moved: the `RmtApplication` stub to B1 (a
  `main` with nothing to show would be a half-finished placeholder) and
  `RmtOptions` to B6 (nothing in B0 reads a persisted option). 425 tests
  (+27), no regressions.
- **B1 - the vertical slice (DECISIONS 1-2 must be settled first).**
  `TrackerPanel` (offscreen canvas, scaled paint, timer, dirty flag,
  `Resize()` math incl. `g_tracklines`/`g_line_y`), minimal
  `RmtMainWindow` (frame + status bar, no menus), `SongUI.drawInfo`/
  `drawSong`/`drawTracks` read-only + `TracksControl`, keyboard *navigation
  only* (arrows/page/home/end via the already-ported `trackUp`/`trackDown`/
  `trackLeft`/`trackRight`/`songUp`/`songDown`), loading a real `.rmt` from
  the command line so there is something to look at. **Verify on the
  user's real display scaling** (the dis6502 HiDPI lesson) and at 100%/
  200% RMT scaling. Golden-image test of the full first frame.
- **B2 - complete drawing.** `drawVolumeAnalyzer` (283 lines),
  `drawPlayTimeCounter`, `drawInstrument` + `InstrumentsUI` drawing,
  `PokeyView`, `AtariView`, smooth scrolling, hover recoloring, GOTO-line
  rendering, debug FPS overlay, title bar (`SetRMTTitle`).
- **B3 - full keyboard input.** `SongInput`: `InfoKey`/`TrackKey`/
  `InstrKey`/`SongKey`/`ProveKey`, `TextFieldEditor`, modifier tracking,
  `Part` switching, `EditMode` (jam/prove) handling, `g_SkipLinesAfterNoteInsert`,
  respect-volume toggle. Logic-level tests per key group.
- **B4 - mouse.** `MouseAction` hit-testing, `TrackCursorGoto`/
  `SongCursorGoto`/`InfoCursorGoto*`/`InstrumentsUI.cursorGoto`, wheel,
  the 5 custom cursors, the three click-positioned popup selectors.
- **B5 - menus, toolbars, accelerators, status bar.** `RmtActions`
  (+`Actions.properties`), `RmtMainMenu` (12 menus incl. the `Pokey`
  tree), 2 toolbars + skip-lines combo, 76 accelerators, the 203 handlers
  as thin delegations, enable/check-state updates.
- **B6 - options & persistence (DECISION 3).** `rmt.ini` read/write/reset,
  tuning config, frame position via `MainWindowPreferences`, Options +
  Paths dialogs, Tuning dialog.
- **B7 - dialogs.** File new/open/save/import/export via WUDSN
  `FileChooser` -> the already-ported loaders/`*Apply()`/exporters; SAP
  and XEX export dialogs; instrument change, tracks order, insert/clone,
  renumber, channels selection, change max track length, tracks load;
  `BlockEffect` (dialog + its logic together, since C++ has no separable
  core); About (with the 6502/POKEY driver info replaced by ASAP's).
- **B8 - audio (DECISION 4).** Scope depends on the decision; see below.
- **B9 - packaging & polish.** `maven-shade-plugin` fat jar + launcher
  (as dis6502), `/SCRIPT`-style command line, unsaved-changes prompts on
  close (`WarnUnsavedChanges`), min-size rules, final HiDPI pass.

## DECISIONS

**Resolved by the user on 2026-09-26: decisions 1-4 below all go with the
recommendation** - Swing + WUDSN Base gui; timer-driven full redraw;
`rmt.ini` kept as the options format (Preferences only for window
geometry); UI first, real-time audio deferred to B8. Decision 5 stands as
recommended. Decision 6: **the user captured reference `Rmt.exe`
screenshots**, now kept in `test-resources/ui-reference/` (a repo-root
`test-resources/` folder, as dis6502 has; one self-contained sub-folder
per scenario with its `.rmt` - `song0-empty/`, `song1-mono/`, ...; that
folder's own `README.md` lists what is present, the capture conditions,
and the proposed names of the still-missing captures, most valuable
first: the same views at RMT scaling 200%, a stereo module, a playback
state, a GOTO line, jam mode) - B1/B2's golden-image tests
should compare against those, not only against Java-generated goldens.
Because they are whole-desktop captures at 150% device scaling, B1 must
first locate the RMT client area within them and account for the
150% scale (and RMT's own `g_scaling_percentage`) before any pixel
comparison. The reasoning is kept below for the record.

**The user's display runs at 150% Windows scaling** - the exact setting
where dis6502's `ComputerFont` HiDPI bug reproduced. B1's on-screen
verification at 150% is therefore a hard gate, not a nice-to-have: the
scaled `drawImage` of the off-screen canvas must be checked for dropped/
doubled rows at RMT scaling 100% *and* 200%, and if it misbehaves, the
fallback is dis6502's own fix (render at native size, draw 1:1 - i.e.
apply RMT's `g_scaling_percentage` while drawing into the off-screen
image rather than in the final blit).

1. **UI toolkit.** *Recommendation: Swing + `com.wudsn.tools.base.gui`.*
   It is what dis6502 uses, it is already a dependency, and it directly
   supplies the frame/preferences/status-bar/menu/dialog/file-chooser
   scaffolding B1/B5/B6/B7 need, so those batches become "fill in RMT's
   specifics" rather than "build infrastructure". JavaFX would add a new
   dependency and a second UI idiom in the same author's tool family for
   no gain - the hard part (a hand-blitted bitmap canvas) is equally
   trivial in either.
2. **Repaint model.** *Recommendation: keep the timer-driven full redraw
   (a `javax.swing.Timer` at ~16 ms firing `repaint()` when the dirty flag
   is set).* It is the faithful choice, the drawing code is written for
   it (smooth scrolling, play-position tracking, analyzer, blink), and a
   tracker genuinely needs a periodic tick during playback anyway. The
   cost - ~60 full redraws/s of a small bitmap - is negligible on any
   machine that runs a JVM. Event-driven repaint would mean re-deriving
   every state change that affects the screen, for no user-visible gain.
3. **Options persistence.** *Recommendation: keep the `rmt.ini` text
   format, same keys, same location semantics* (a file next to the app /
   in the user's config dir), so the Java and C++ apps stay mutually
   readable and users can carry settings across; use
   `MainWindowPreferences` (Java Preferences) *only* for window position/
   size, exactly mirroring what C++ keeps in the registry today.
4. **Real-time audio - the big one.** Options: **(a)** ship the editor UI
   first with no live sound (all editing, drawing, file I/O and export
   work; play commands advance the visual play position via the existing
   `playVBI` loop without audio) and add audio as B8; **(b)** design the
   audio path now, before B1. The only viable audio engine is ASAP: for
   "play song/from position", export the current module bytes ->
   `ASAP.load` -> `playSong(0, ...)` positioned at the play line -> stream
   `generate()` output to a `javax.sound.sampled.SourceDataLine` on a
   background thread, re-exporting on each play start (the same thing
   `dumpSongToPokeyStream` already does, minus the recording). Keyboard
   *note preview* ("prove"/jam mode - one instrument, live, no song) has
   **no** ASAP equivalent and would need a real RMT driver running inside
   the emulator with the Java side poking notes into its memory - a
   genuinely new mechanism. *Recommendation: (a)* - it unblocks everything
   else, and B8 can then be designed against a working UI with the two
   sub-problems (song playback vs. note preview) tackled separately.
5. **First slice content.** *Recommendation: B1 exactly as above* (read-
   only grid + navigation + load-from-command-line), because it proves the
   canvas, the scaling, the timer, the key path and HiDPI behavior with
   the least code.
6. **Reference screenshots for golden-image tests.** Golden PNGs
   generated by the Java renderer itself catch regressions but not
   "differs from the real RMT". If the user can capture a few `Rmt.exe`
   screenshots of a known `.rmt` at 100% scaling (e.g. tracks view,
   instrument view, a GOTO line), B1/B2 can compare against them pixel
   for pixel, which is the strongest possible "matches today's Windows
   UI" check. Optional but valuable.

## Confirmed out of scope for Phase B (unchanged from the handover)

MIDI input (`Midi_Song.cpp`), `TimerRoutine`'s DirectSound rendering
(replaced by DECISION 4's ASAP path if/when audio is done), the `FileXxx`
`CFileDialog` methods as such (their *purpose* returns via WUDSN
`FileChooser` + the ported loaders), `Commands.cpp`'s debug-only
menu/accelerator consistency checker.
