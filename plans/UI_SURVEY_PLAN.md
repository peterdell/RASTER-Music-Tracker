# Survey: RMT's current C++ UI layer

## Context

The characterization-testing effort (`plans/BROADER_SURVEY_PLAN.md` and
everything downstream of it) deliberately treated every UI file as
Category A - "real UI, out of scope for testing" - since UI code can't be
unit-tested the way model code can. That phase is now essentially done.
Before any Java-port planning can start, the port needs to actually
replicate today's UI (the user's explicit requirement: "the UI also needs
to match today's Windows UI"), so this document surveys what that UI
*is* - architecture, rendering, input model, commands, dialogs - as a
read-only inventory. **It does not propose a Java port design or make any
architecture decision.** It exists so that `plans/FILE_TIERING_STRATEGY.md`'s
open composition-vs-mechanical question, and any future UI-framework
choice, can be made from real information instead of guesses from file
names.

## App architecture

Classic MFC SDI (single document interface), but the Document/View split is
almost entirely unused:

- **`CRmtApp`** (`Rmt.h`/`Rmt.cpp`) is the `CWinApp`. `InitInstance()`
  registers one `CSingleDocTemplate` binding `CRmtDoc`/`CMainFrame`/
  `CRmtView`, then does essentially all of the app's real startup work
  itself: loads the 6502 DLL (`g_Atari.Init()`), initializes tuning,
  constructs `g_AtariTrackerDriver` and loads/inits the RMT driver
  routines, calls `g_Song.ClearSong(8)`, and constructs `g_SongUI = new
  CSongUI(g_Song)`. It also parses the command line for the `/TEST`,
  `/SCRIPT`, and standard shell-command switches.
- **`CRmtDoc`** (`RmtDoc.h`/`.cpp`) is a near-empty stub. Its own comment
  says it outright: `// NOTE: Not used in RASTER Music Tracker`.
  `Serialize()` is a no-op, there are no data members. The document/view
  architecture exists only because MFC's `CSingleDocTemplate` requires a
  `CDocument` subclass to exist - it carries no state.
- **The actual model** is a set of global objects, not anything owned by
  the document: `CSong g_Song`, `CTracks g_Tracks`, `CInstruments
  g_Instruments`, `CUndo g_Undo`, `CTrackClipboard g_TrackClipboard`,
  `CAtari g_Atari`, `CAtariTrackerDriver* g_AtariTrackerDriver`, `CXPokey
  g_Pokey`, `CSongUI* g_SongUI`, `CRmtMidi g_Midi` - all declared `extern`
  and used directly from `CRmtView`'s command handlers and from `Global.cpp`.
  This is the same global-state architecture the entire characterization-
  testing effort was built around (see every `test/*Stub.cpp` file's
  `extern CSong g_Song;` etc.) - confirmed here to be the real production
  wiring, not a testing-only convenience.
- **`CMainFrame`** (`MainFrm.h`/`.cpp`) hosts the toolbar (`m_wndToolBar`),
  a play/channels/block toolbar trio wrapped in a `CReBar`
  (`m_wndReBar`/`m_ToolBarPlay`/`m_ToolBarChannels`/`m_ToolBarBlock`), the
  status bar, and one embedded control - a "skip N lines after note
  insert" combo box built directly into the toolbar
  (`m_comboSkipLinesAfterNoteInsert`, values 0-8). It persists window
  position/size across sessions via `WriteProfileInt`/`GetProfileInt`
  (registry-backed, under `HKCU\...\RASTER Music Tracker\Frame`). Its
  `OnGetMinMaxInfo` enforces a minimum window size that depends on mono
  vs. stereo mode (800x600 mono, 1120x600 stereo) - the tracker grid is
  wider in stereo mode (see Rendering, below).
- **`CRmtView`** (`RmtView.h`/`.cpp`, 2986 lines - by far the largest UI
  file) is the real controller: every menu command, every keyboard/mouse
  event, and the paint loop all live here. It owns the actual rendering
  surface (`m_mem_bitmap`/`m_mem_dc`, an off-screen compatible bitmap) and
  a `CCanvasXY* m_canvasXY` handed out to `g_Instruments`/`g_SongUI` so
  *they* can draw into the same surface (`Resize()`:
  `g_Instruments.SetCanvas(*m_canvasXY); g_SongUI->SetCanvas(*m_canvasXY);`).
  So drawing logic is split between the view (owns the surface, drives the
  timer, does `StretchBlt`) and per-domain UI helper classes that draw
  into it (see Rendering).

**Bottom line for the Java port**: there is no meaningful
document/view/model separation to preserve. The real architecture is one
global mutable model (`CSong` and friends) plus one controller/view class
that both handles all input and drives all drawing. A Java port's own
model-vs-UI boundary would be a *new* design decision, not something
inherited from the current structure.

## Rendering approach

Everything is drawn into a single in-memory GDI bitmap (`CDC
m_mem_dc`/`CBitmap m_mem_bitmap`, sized to the client area) and blitted to
the screen with `StretchBlt` on every paint (`CRmtView::OnDraw`) - this is
what makes the UI's live rescaling (`g_scaling_percentage`, 100-300%) work:
the model always draws at a fixed logical resolution and the final
`StretchBlt` does the scaling. A 16ms repeating timer (`m_timerDisplay`,
`CRmtView::OnTimer`) sets a dirty flag and calls `RefreshScreen()`
continuously - this is a polling/redraw-loop UI, not an event-driven
"paint on state change" one.

**Text/graphics are a hand-rolled bitmap font, not GDI text at all.**
`CCanvasXY` (`CanvasXY.h`/`.cpp`) blits 8x16 ("normal") or 8x8 ("mini")
character cells out of one loaded bitmap resource (`IDB_GFX`, loaded once
in `CRmtView::OnInitialUpdate` into a second compatible DC,
`CCanvasXY::g_gfx_dc`, shared via a `static` pointer). Every ASCII
character (`charcode & 0x7f`) has a fixed x-offset in the sheet
(`charcode << 3`); color is selected by which vertical band of the *same*
pre-rendered glyph sheet gets blitted (`GetColorY(color)`, a plain
`enum(color) << 4` or `<< 3` offset) - there is no runtime color
blending, alpha, or anti-aliasing anywhere. Selection/hover highlighting
works the same way: `TextXYSelN`/`TextXYCol` recolor specific character
ranges within a string by picking a different color band per character,
and per-pixel mouse hover detection (`IsHoveredXY`, in `GuiHelpers.cpp`)
swaps in yet another band. `IconMiniXY` blits small fixed-size icons from
a reserved strip of the same sheet. `CCanvas` (`Canvas.h`/`.cpp`) is a thin
"cursor" wrapper over `CCanvasXY` (`At(col,row)`/`NextRow()`/`PrintMini()`)
used by the per-domain drawing helpers below.

**Layout is fixed-pixel, not flow/responsive.** `CRmtScreenLayout.h`
hardcodes absolute pixel positions in units of the 8x16 character cell
(e.g. `TRACKS_Y = 8*16+8`, `SONG_X = 96*8`, `INFO_Y_LINE_1..6`). The whole
screen is composed like a fixed-size text-mode terminal, then the final
`StretchBlt` scales that fixed canvas to whatever window size the user
picked.

**Drawing is split across several small non-window "view" helper
classes**, all constructed with a `CCanvas&`/`CCanvasXY&` and offering
`Draw()` methods - none of them are `CWnd` subclasses or separate windows,
despite the "View" naming:
- `CSongUI` (`SongUI.h`/`.cpp`) - the main one. `DrawTracks`/`DrawSong`/
  `DrawInstrument`/`DrawInfo`/`DrawVolumeAnalyzer`/`DrawPlayTimeCounter`,
  called every frame from `CRmtView::DrawAll()`. Constructed once
  (`g_SongUI`) around `g_Song`.
- `CTracksControl` (`TracksControl.h`/`.cpp`) - draws individual track
  headers/lines, used by `CSongUI`'s track-grid drawing.
- `CPokeyView` (`PokeyView.h`/`.cpp`) - draws the "POKEY Explorer" debug
  overlay (`explorerMode` parameter), showing live POKEY register state.
- `CAtariView` (`AtariView.h`/`.cpp`) - draws a hex dump of Atari memory,
  another debug overlay.

**Custom cursors**: five custom cursor resources are loaded in
`OnInitialUpdate` (`IDC_CURSOR_CHANNEL_ON_OFF`/`_ENVELOPE_VOLUME`/`_GOTO`/
`_DIALOG`/`_SET_POSITION`) and swapped via `OnSetCursor` depending on what
the mouse is hovering over - a real UX detail (distinct cursor per
interactive element) a faithful port would need custom cursor assets for.

## Keyboard-driven editing model

RMT is a fully keyboard-operable grid editor (the genre's classic
"tracker" UX). Input flows: `CRmtView::OnKeyDown` reads the raw virtual-key
code, handles a handful of global keys directly (media play/pause/next/
prev-track, `F11` for "respect volume" toggle, modifier-key tracking for
Shift/Ctrl/Alt), then dispatches based on `g_activepart` (a `Part` enum:
`PART_INFO`/`PART_TRACKS`/`PART_INSTRUMENTS`/`PART_SONG`) to one of four
`CSong` methods - `InfoKey`/`TrackKey`/`InstrKey`/`SongKey` (all
implemented in `GUI_Song.cpp`, already characterized as Category A "real
UI" during testing - confirmed here to be the real command surface, not
just something the survey named that way).

Two cross-cutting behaviors apply uniformly across all four parts:
- **Holding Shift plays a live note preview** instead of editing, by
  routing to `CSong::ProveKey` whenever the pressed key maps to a musical
  note or number key (`NoteKey(vk) >= 0`, `Numblock09Key(vk) >= 0`, or
  Space) - except while actively typing into a text field (song name,
  instrument name), where Shift is needed for normal typing instead and a
  CAPSLOCK-based workaround exists to fake shift-lock behavior for those
  fields.
- **`IsProveMode()`** (`g_prove` global, an `EditMode` enum:
  `EDIT_MODE`/`JAM_MONO_MODE`/`JAM_STEREO_MODE`/`MIDI_CH15_MODE`/
  `POKEY_EXPLORER_MODE`) can redirect *all* keys in `PART_TRACKS`/
  `PART_SONG` to `ProveKey` - a "jam mode" for playing the instrument
  live from the keyboard without editing, plus two special modes (MIDI
  channel 15 passthrough, and the POKEY Explorer debug view).

Mouse input (`CRmtView::MouseAction`, `OnLButtonDown`/`OnRButtonDown`/etc.)
is a large hit-testing switch against the same fixed pixel layout
(`CRmtScreenLayout`) - clicking a field in the Info panel opens one of
three small popup-style dialogs positioned *at the click point*
(`COctaveSelectDlg`/`CVolumeSelectDlg`/`CInstrumentSelectDlg`, all
positioned via `CPoint(x - offset, y - offset)` relative to the click and
the main window's screen position - not centered modal dialogs). This is a
distinct UX pattern from RMT's other (centered, standard-sized) dialogs and
would need a specific Java equivalent (e.g. a borderless popup positioned
at the cursor) to replicate faithfully.

## Menu/toolbar/command structure

Top-level menus (`Rmt.rc`): **File, Edit, View, Play, Channels, Song,
Instrument, Track, Block, Pokey, Tools, Help**. `Pokey` is a deep debug
submenu for directly poking POKEY registers (per-channel AUDF/AUDC,
AUDCTL, SKCTL, "Divisor") - a developer/debug menu, not typical end-user
functionality. Every menu command maps to one `ON_COMMAND`/
`ON_UPDATE_COMMAND_UI` pair in `CRmtView`'s message map (`RmtView.h`/`.cpp`
- see the huge `afx_msg` list in the header), the large majority of which
are thin one-line delegations straight to a `CSong`/`CTracks`/
`CInstruments`/`CUndo` method (e.g. `OnSongCopyline() { g_Song.SongCopyLine();
}`). `OnUpdate*` handlers implement standard MFC command-UI graying/
checking (e.g. graying "Redo" when there's nothing to redo).

Three toolbars exist (`IDR_MAIN_WINDOW` main toolbar, `IDR_TOOLBAR_BLOCK`
block-edit toolbar, referenced `IDR_TOOLBAR_CHANNELS`/`IDR_TOOLBAR_PLAY`
resource IDs for the other two `CMainFrame` toolbar members, though only
`m_wndToolBar` and `m_ToolBarBlock` are explicitly created/added to the
rebar in `OnCreate` - `m_ToolBarPlay`/`m_ToolBarChannels` are declared but
not obviously wired up in the code read; flagged as an open question
below). Accelerators are defined in `Rmt.rc`; `Commands.cpp`'s
`CCommands`/`CAcceleratorTable`/`CMenuEntry` classes are a debug-only
consistency-checker (`CCommands::Analyze()`, called only under `#ifdef
DEBUG` in `Rmt.cpp`) that cross-references the menu resource, accelerator
table, and toolbars to flag commands missing an accelerator/description -
not part of the runtime UI itself.

## Dialog inventory

Every real (non-testable) dialog class found, its purpose, and (where
already established by characterization-testing work) which model method
consumes its input:

| Dialog | File | Purpose | Feeds |
|---|---|---|---|
| `CAboutDialog` | AboutDialog.h/.cpp | App/credits/6502+POKEY driver info | display only |
| `COptionsDialog` + `COptionsPathsDialog` | OptionsDialog.h/.cpp | App-wide preferences: keyboard layout, MIDI device/touch-response/volume-offset, track-line highlight colors, window scaling %, tracker driver version, NTSC/PAL, smooth scrolling, flat-note display, German note names, several keyboard-behavior toggles, HW sound buffer toggle; nested paths sub-dialog for songs/instruments/tracks default folders | writes many `g_*` globals directly, no `CSong` involvement |
| `TuningDlg` | TuningDialog.h/.cpp | Edit `TTuningSettings`/`TTuningRatios` (custom tuning), with a "Test" playback button | `CTuning`, not `CSong` |
| `CFileNewDlg` | filenewdlg.h/.cpp | New song: max track length, mono/stereo | `CSong::FileNew` |
| `CChangeMaxtracklenDlg` | filenewdlg.h/.cpp | Change max track length on an existing song | (not traced further this pass) |
| `CEffectsDlg` | effectsdlg.h, impl in Clipboard.cpp | Block effects (volume/pitch slides etc.) applied to a track selection | confirmed in characterization testing to have **no extractable logic** - `BlockEffect` stays deferred |
| `CSongTracksOrderDlg` | effectsdlg.h, impl in Song.cpp | Reassign/reorder song columns (mono<->stereo remap, swap, clear) | `CSong::TracksOrderChange` → `TracksOrderChangeApply()` (already split, tested) |
| `CInstrumentChangeDlg` | effectsdlg.h, impl in Song.cpp | Bulk instrument remap (note/volume/instrument range remapping) | `CSong::InstrChange` → `TInstrChangeParams` → `InstrChangeApply()` (already split, tested) |
| `CRenumberTracksDlg` / `CRenumberInstrumentsDlg` | effectsdlg.h | Choose renumbering strategy (by usage order etc.) | `CSong::RenumberAllTracks`/`RenumberAllInstruments` (not traced further this pass) |
| `CInsertCopyOrCloneOfSongLinesDlg` | effectsdlg.h, impl in Song.cpp | Insert a copy/clone of a songline range, with optional tuning/volume adjustment | `CSong::SongInsertCopyOrCloneOfSongLines` → `SongInsertCopyOrCloneOfSongLinesApply()` (already split, tested) |
| `COctaveSelectDlg` / `CVolumeSelectDlg` / `CInstrumentSelectDlg` | effectsdlg.h, impl in GUI_Song.cpp | Click-positioned popups for picking octave/volume/active-instrument from the Info panel | `CSong::InfoCursorGotoOctaveSelect`/`...VolumeSelect`/`...InstrumentSelect` |
| `CChannelsSelectionDlg` | effectsdlg.h | Channel selection (not traced further this pass) | - |
| `CImportModDlg` + `CImportModFinishedDlg` | importdlgs.h/.cpp | MOD import options (8 checkboxes: octave shift, portamento, full volume range, etc.) and post-import summary | `CSong::ImportMOD` → `ImportModParseHeader`/`ImportModApply` (already split, tested - see `plans/IO_IMPORTER_PLAN.md`) |
| `CImportTmcDlg` + `CImportTmcFinishedDlg` | importdlgs.h/.cpp | TMC import options and post-import summary | `CSong::ImportTMC` → `ImportTMCParseHeader`/`ImportTMCApply` (already split, tested) |
| `CTracksLoadDlg` | importdlgs.h | Track load range options | `CTracks::LoadTrack`-adjacent (not traced further) |
| `CExportStrippedRMTDialog` | exportdlgs.h | Export a size-optimized/stripped RMT module, with SFX-support and global-volume-fade options | RMT export path (not traced further this pass) |
| `CExpMSXDlg` | exportdlgs.h, `ShowXEXExportDialog` in SongExporter.cpp | XEX/MSX export options: on-screen text (5 lines), meter color, raster-bar display, region auto-detect | `CSongExporter::ExportXEX_LZSS` (already split - dialog stays in `SongExporter.cpp`, real work in `SongExporterCore.cpp`, tested - see `plans/EXPORTLZSS_PLAN.md`/`plans/SAP_LZSS_WAV_XEX_PLAN.md`) |
| `CExportAsmDlg` | exportdlgs.h | ASM export options (tracks vs. whole song, note format, duration format) | ASM export path (not traced further) |
| `CExportRelocatableAsmForRmtPlayer` | exportdlgs.h | Relocatable-ASM export for the RMT player runtime (per-section relocation labels, SFX support) | ASM export path (not traced further) |
| `CSAPFileExportDialog` | SAPFileExportDialog.h/.cpp | SAP file metadata (author/date/name/subsongs) | `CSAPFileExporter::ExportSAP_R`/`ExportSAP_B_LZSS` (already tested, see `plans/SAP_LZSS_WAV_XEX_PLAN.md`) |
| 8x `CFileDialog` (standard Windows Open/Save) | IO_Song.cpp | File open/save/import/export/instrument-save/instrument-load/track-save/track-load | the `FileXxx` family - confirmed in `plans/SONG_IO_SONG_REMAINING_PLAN.md` Batch 7 to have no extractable split (the chosen path *is* the method) |

`CTypedComboBox<T>` (`TypedComboBox.h`) is a trivial generic helper (an
enum-backed `CComboBox`), used by `COptionsDialog` for the keyboard-layout
and tracker-driver-version pickers - not a dialog itself.

## Observations relevant to the Java port decision

- **The global-state architecture is real, not a testing artifact.**
  Every seam identified during characterization testing
  (`SendQuestionMessage`'s test-injectable answer, `InstrInfo`/`TrackInfo`'s
  optional-output-parameter split, the `*Apply()` cores already extracted
  from `InstrChange`/`TracksOrderChange`/`SongInsertCopyOrCloneOfSongLines`/
  `ImportTMC`/`ImportMOD`) lines up exactly with real dialog boundaries
  found here - confirming `plans/FILE_TIERING_STRATEGY.md`'s option 2
  (composition via injected interfaces) has genuine seams to use, not just
  theoretical ones. The dialogs themselves are naturally "thin" (gather a
  fixed parameter struct, hand it to an already-separable `*Apply()`
  method) in every case that's been traced end-to-end.
- **Rendering is simple to replicate in *some* 2D toolkit, but the bitmap-
  font approach is a specific stylistic choice, not an incidental
  limitation.** Any Java 2D API (Swing `Graphics2D`/`BufferedImage`,
  JavaFX `Canvas`/`GraphicsContext`) can trivially do fixed-size
  sprite-sheet blits at a fixed frame rate. The open decision is whether
  the Java port should **keep** the exact pixelated bitmap-font look (by
  extracting/reusing the same glyph sheet, currently baked into the
  `IDB_GFX` Win32 resource - it would need to be exported to a plain image
  file first) or move to a more conventional rendered-text UI. This is a
  visual-fidelity decision, not a technical constraint - flagged for a
  decision, not resolved here.
- **The fixed-pixel, fixed-character-grid layout (`CRmtScreenLayout`) is
  itself simple to port 1:1** (it's just a set of integer constants) but
  assumes the "always redraw everything, every frame" model - a Java port
  could keep that model (simplest, most faithful) or move to an
  event-driven repaint-on-change model (more idiomatic for a GUI toolkit,
  but a real behavior change from today's app, and would need the timer-
  driven `RefreshScreen()` polling loop rethought).
- **The keyboard dispatch model (`Part` + `EditMode` + `ProveKey`
  override) is a clean, already-well-defined state machine** - confirmed
  here as a real, single, consistent input model across the whole app (not
  ad hoc per-screen key handling), which should translate cleanly to
  whatever Java input-handling approach is chosen (a single key-event
  listener on the main canvas, dispatching the same way).
- **Two UX patterns need explicit design attention** in a Java port,
  since they don't map onto default toolkit widgets: the click-positioned
  popup dialogs (`COctaveSelectDlg`/`CVolumeSelectDlg`/
  `CInstrumentSelectDlg`, positioned at the mouse click rather than
  centered) and the custom per-context cursors (5 of them). Neither is
  hard, but both need a deliberate equivalent rather than falling out of
  a standard dialog/cursor API by default.
- **The `Pokey` debug submenu and `CPokeyView`/`CAtariView` debug overlays**
  are developer/diagnostic UI, not core end-user functionality - worth
  flagging as a candidate to deprioritize or drop in a first Java port
  pass, a product decision for the user rather than something this survey
  resolves.

## Open questions

1. ~~`m_ToolBarPlay`/`m_ToolBarChannels`~~ - **resolved while writing this
   survey**: `grep`-confirmed neither is referenced anywhere outside their
   own declaration in `MainFrm.h` - genuinely dead code (declared, never
   created, never added to the rebar, never shown). Not a question, just
   noting it here in case it matters for the port (nothing to carry over).
2. **Several dialogs were inventoried but not traced to their consuming
   model method this pass** (`CRenumberTracksDlg`/`CRenumberInstrumentsDlg`,
   `CChangeMaxtracklenDlg`, `CChannelsSelectionDlg`,
   `CExportStrippedRMTDialog`, `CExportAsmDlg`,
   `CExportRelocatableAsmForRmtPlayer`, `CTracksLoadDlg`) - worth a
   follow-up pass if/when those specific export/renumber flows become
   relevant to porting order.
3. ~~Should the Java port preserve the bitmap-font pixelated look, or is
   this an opportunity to modernize the visual style?~~ **Resolved**: keep
   the exact pixelated bitmap-font look. The `IDB_GFX` glyph sheet will
   need extracting to a plain image file for the Java port to blit from.
4. ~~Is the `Pokey` register-poking debug submenu and the
   `CPokeyView`/`CAtariView` debug overlays meant to survive into the Java
   port?~~ **Resolved**: keep them from the start, alongside everything
   else - no reduced initial scope here.
5. ~~Is MIDI input in scope for an initial Java port?~~ **Resolved**:
   defer MIDI entirely for now. `javax.sound.midi` would make the I/O
   plumbing itself simple (arguably simpler than today's raw
   `mmsystem.h` calls - see the `MidiSystem`/`Receiver`/`ShortMessage`
   mapping discussed in this session), but `Midi_Song.cpp`'s ~600 lines of
   dispatch logic (legacy channel-0 note input, multitimbral channels
   1-8, and an admittedly unfinished "channel 15 control surface"/drumpad-
   to-POKEY-register hack the code's own comments call "very terrible...
   will eventually be replaced") isn't a good first-pass investment. Not
   part of the initial Java port's scope; revisit later.
