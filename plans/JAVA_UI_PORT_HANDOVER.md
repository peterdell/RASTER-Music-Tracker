# Handover: starting the Java UI port (Phase B)

This is a deliberately short entry point for a session with no history of
this effort (written when the user switched to the Fable model for this
phase) - it orients you and points at the real detail rather than
repeating it. Don't duplicate the docs it links to; read them.

## Current state (2026-09-26)

The Java model-layer port is done. 59 classes under
`src/java/com/wudsn/tools/rmt/model/`, 398 passing tests
(`mvn -o clean test` from the repo root), latest commit `08d9465`. Every
real, meaningful C++ model-layer method has either been ported or is
confirmed permanently out of scope (see "Confirmed out of scope" below).
`com.wudsn.tools.rmt.ui` **does not exist yet - zero UI code has been
written.** That's this phase's job.

## The one hard requirement

The user has explicitly said: **the UI needs to match today's Windows
UI.** This is not a modernization opportunity - `plans/UI_SURVEY_PLAN.md`
exists specifically to inventory the real C++ UI (architecture, rendering,
input model, dialogs) so the Java port replicates it, not reimagines it.
Read that document before making any UI design decision.

## Already-resolved architecture decisions

From `plans/UI_SURVEY_PLAN.md`'s "Open questions" section - don't
re-litigate these:

- **Keep the exact pixelated bitmap-font look**, not a modernized
  rendered-text UI. The glyph sheet is baked into the `IDB_GFX` Win32
  resource and needs exporting to a plain image file for Java to blit
  from.
- **Keep the `Pokey`/`AtariView` debug overlays and the Pokey
  register-poking debug submenu** from the start - developer/diagnostic
  UI, but in scope, not deprioritized.
- **Defer MIDI input entirely.** `Midi_Song.cpp`'s ~600 lines of dispatch
  logic (including an admittedly-unfinished "channel 15 control surface"
  hack) is not part of the initial Java UI scope.

## Still open - resolve these before writing UI code

- **Rendering/repaint model.** Today's app always redraws everything on a
  timer (`CRmtScreenLayout`'s fixed-pixel grid). Keep that model (simplest,
  most faithful) or move to event-driven repaint-on-change (more
  idiomatic, but a real behavior change needing the `RefreshScreen()`
  polling loop rethought)?
- **Java UI toolkit choice** - not decided anywhere. Swing, JavaFX, and a
  lower-level 2D canvas library are all viable for this app's fixed-size
  sprite-sheet blitting. Check `dis6502`
  (`C:\jac\system\Java\Programming\Repositories\dis6502`, this project's
  own prior-port precedent for build/package layout) for whether it
  already made a similar rendering choice worth reusing.
- **Two UX patterns with no default toolkit equivalent**: click-positioned
  popup dialogs (`COctaveSelectDlg`/`CVolumeSelectDlg`/
  `CInstrumentSelectDlg`, positioned at the mouse click, not centered) and
  5 custom per-context cursors. Need deliberate design.
- **A handful of dialogs surveyed but not traced to their consuming model
  method**: `CRenumberTracksDlg`/`CRenumberInstrumentsDlg`,
  `CChangeMaxtracklenDlg`, `CChannelsSelectionDlg`,
  `CExportStrippedRMTDialog`, `CExportAsmDlg`,
  `CExportRelocatableAsmForRmtPlayer`, `CTracksLoadDlg` - trace these when
  their specific flow becomes relevant, not before.

## Recommended starting point

Don't attempt the whole UI in one pass. Start with the smallest possible
vertical slice - a main window, read-only tracker-grid rendering, and
keyboard navigation (no editing, no dialogs, no export flows, no debug
overlays) - as working code that answers the toolkit and repaint-model
questions above before committing to anything bigger. This mirrors how
the model port itself started with `Fraction` (the smallest,
dependency-free class) rather than `Song`. Once that slice works, this
phase deserves its own dedicated plan doc (e.g.
`plans/JAVA_UI_PORT_PLAN.md`), broken into batches the same way the model
port was.

## What the model layer already gives you to build against

The UI will drive these (already-ported, already-tested) classes rather
than reimplementing any of this logic: `Song` (the God class - playback,
editing, import/export, ~3700 lines), `Tracks`/`Instruments`/`Track`/
`Instrument`, `Undo`/`TrackClipboard`, `AtariTrackerDriver` (real for
keyboard-note-preview bookkeeping, permanently no-op for actual audio -
see its own class javadoc), `AsapEmulator` (the real 6502/POKEY emulator,
used for `dumpSongToPokeyStream`/export, not preview), and the exporter
classes (`RmtExporter`, `AsmFileExporter`, `SapFileExporter`,
`SongExporter`, `WaveFileExporter`). Every one of these has its own class
javadoc explaining what it covers and what it deliberately omits - read
the javadoc of whatever you're about to call before assuming its
behavior.

## Working conventions established during the model port (carry these forward)

- **Investigate the C++ source fully before writing Java** - read the real
  method body, not just the header, before porting or designing against
  it.
- **Verify with `mvn -o clean test`** (not `mvn -o test` - stale
  incremental compiles have masked real errors before) after every
  change, before considering it done.
- **Update `plans/NOTES.md`** with a dated entry after each batch -
  what changed, why, what was verified. This is the project's own running
  log; skim its tail for the most recent context before starting.
- **Ask before committing** - every batch in this effort has been
  committed only after explicit user approval, one batch at a time.
- **"C++ global a method needs -> explicit Java parameter"**, not a
  stored/static field - the idiom used throughout the model port (e.g.
  `Undo`, `TrackClipboard`, `AtariTrackerDriver` are always passed in, never
  held as singletons). Expect this to extend into the UI layer's own
  collaborators.
- Prefer **idiomatic substitution over line-for-line translation** when it
  produces identical observable behavior (e.g. `WaveFileExporter` uses
  ASAP's own audio synthesis instead of porting C++'s separate `CXPokey`
  renderer) - but document the deviation clearly when you make one.
- Git commits end with the standard attribution footer (see any recent
  commit via `git log` for the exact format) - the session ID in prior
  commits belongs to the previous session; use your own once you have one.

## Reading order

1. `plans/UI_SURVEY_PLAN.md` - the real UI's architecture, in full.
2. This document's "Still open" section - the decisions to make first.
3. `plans/JAVA_PORT_PLAN.md` - skim for conventions (it's a long batch-by-
   batch history of the model port; you don't need every batch, just the
   idioms).
4. `plans/JAVA_PORT_NEXT_STEPS_PLAN.md` - Phase A's closing state and
   Phase B's own section.
5. `CLAUDE.md` - build/test commands for both languages.
