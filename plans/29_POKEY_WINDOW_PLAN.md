# Plan: the POKEY registers view in its own modeless window (W1-W6)

Status: **currently out of scope** (the user, 2026-10-01). Nothing
implemented, and nothing is to be started from this plan until the user
says otherwise. It is written down so the analysis behind it - what the
view really is, what a second window would cost and which decisions come
first - does not have to be done again.

The user's plan it analyses: "separate the Pokey Registers view and the
related Pokey Explorer Menu to a separate, amodal Window".

## 1. What exists today

Verified on 2026-10-01 in both programs.

### 1.1 The view is text on the tracker's own canvas

`PokeyView` (Java `ui`, C++ `PokeyView.cpp`, ~300 lines each) draws with
the tracker's glyph blitter: `CanvasXY` copies 8x16 cells out of
`gfx-8x16.bmp` into one `BufferedImage`, and `Canvas` is only an origin
plus a text cursor over it. `SongUI.drawVolumeAnalyzer()` creates that
origin and calls the view:

```java
if (DEBUG_POKEY) {
    Canvas pokeyCanvas = new Canvas(canvasXY, POKEY_VIEW_X, POKEY_VIEW_Y);
    new PokeyView(pokeyCanvas).draw(stereo, tuning, tuningSettings,
            notesPerOctave, atari, explorerMode, pokeyController);
}
```

`POKEY_VIEW_X = SONG_OFFSET_X + 6 * 8 - 32`, `POKEY_VIEW_Y =
TRACKS_Y + 50`. The view itself never learns where it sits - **it is
already isolated behind a two-line interface**, which is why this is
feasible at all. Its size is about 21 text rows by 70 columns, i.e.
roughly 560 x 190 logical pixels (the explorer rows are the last three).

### 1.2 What paints it

`TrackerPanel` owns everything around it: the `BufferedImage`, the
resize/scaling arithmetic (`computeLayout`, `resizeCanvas`), the 16/16/15
ms timer, `paintComponent` (which redraws the canvas only when
`uiState.screenUpdate` is set, then blits it with an integer transform),
the key and mouse listeners and the cursor map. A second window needs the
image, the resize, the timer and the paint - not the input parts.

### 1.3 What switches it on

`RmtOptions.view.pokeyRegisters` (`VIEW_POKEYCHIPREGISTERS` in
`rmt.ini`), toggled by `VIEW_POKEYREGS` = Pokey > "Pokey Chip Registers".
Three couplings that a separate window makes wrong:

- `isEnabled(VIEW_POKEYREGS)` returns `view.volumeAnalyzer` - the item is
  greyed while the analyzer is off, for no reason that survives the move.
- `SongUI` clears `DEBUG_POKEY` when the tracker window is under 960
  pixels wide (1220 in the instruments part) - a rule about the tracker's
  own layout.
- The same flag also draws the per-channel AUDF/AUDC numbers and the
  AUDCTL/SKCTL values **next to the analyzer bars**
  (`SongUI:296/301/311`), which stay in the tracker window. One flag, two
  meanings: it has to become two.

### 1.4 The menu and the explorer mode

The Pokey menu is 50 items (registers, AUDCTL bits, SKCTL, debug channel,
divisor) plus the two at its top. Since plan 26 they all work, in the
explorer mode only, through `RmtCommands.pokeyCommand` /
`CRmtView::OnCmdMsg`. The mode itself is entered by Ctrl+Shift+F5 and
left by the Edit/Jam toggle; in it, every key of the tracker goes to
`PokeyController.onKeyDown` through `SongInput.proveKey`.

### 1.5 The constraint that shapes the plan

`doc/rmt_action_infos.md` is generated from the menus of **both**
programs and compared byte for byte by `compare_exports.ps1` and
`CrossProgramExportTest`. Moving the Pokey menu out of the Java menu bar
therefore fails the comparison unless `Rmt.exe` moves it too. In C++ that
is the expensive half: the view draws inside `CRmtView::DrawAll`'s device
context, so a second window means a new `CFrameWnd` with its own message
map, timer and `OnCmdMsg` routing.

### 1.6 Window infrastructure

WUDSN Base has `MainWindow` (a `JFrame` that saves its state) and
`MainWindowPreferences`, both written for exactly one window; the port's
`RmtWindowPreferences` implements it over one `Preferences` node. A
second window that remembers position and size needs either a second node
with its own keys or a generalization of that interface - the latter
touches the shared library and therefore dis6502 too. Every other window
in the port is a modal dialog (`ModalDialog`, `AboutDialog`); **there is
no modeless window yet**, so this is the first.

## 2. Design

- **`PokeyPanel`** (`ui`): a `JPanel` with its own `CanvasXY`,
  `BufferedImage`, timer and `paintComponent`, drawing only
  `PokeyView`. The canvas is a fixed logical size (the view's own),
  scaled by `options.scalingPercentage` like the tracker's, so the two
  windows look alike.
- **`PokeyWindow`**: a `JFrame` holding the panel, `setDefaultCloseOperation
  (HIDE_ON_CLOSE)`, non-modal, owner = the main window so it stays above
  it and does not get its own taskbar entry. Shown and hidden by
  `VIEW_POKEYREGS`, which becomes "show the POKEY window".
- **The shared paint loop**: `TrackerPanel`'s timer and the
  canvas/resize/blit code are extracted into a small base class or helper
  (`GlyphCanvasPanel`) that both panels use. The extraction is the
  honest move; copying 80 lines is the alternative and I would not.
- **Repaint**: the POKEY panel repaints on its own tick, reading the
  emulated memory under `session.lock` exactly as `TrackerPanel` does,
  and its timer stops while the window is hidden.
- **The two flags**: `view.pokeyRegisters` keeps its `rmt.ini` name and
  now means "the POKEY window is open"; a second, new flag (or simply the
  analyzer's own setting) keeps the numbers next to the analyzer bars.
  Decision 5.1.
- **`isEnabled(VIEW_POKEYREGS)`** becomes unconditional, and `SongUI`'s
  width rule applies only to what stays in the tracker window.

## 3. Batches

### W1 - the shared paint loop

Extract from `TrackerPanel` what a second panel needs: the canvas image,
`resizeCanvas`, the device-scale blit and the timer. No behaviour change;
the reference screenshot tests (`SongUITest`) are the net that proves it.

### W2 - `PokeyPanel` and `PokeyWindow`

The panel, the frame, `VIEW_POKEYREGS` opening and closing it, the timer
running only while visible. The view still draws in the tracker window
too, so the two can be compared side by side during the batch.

### W3 - out of the tracker window

Remove the `DEBUG_POKEY` block from `SongUI`, split the option (5.1),
drop the width suppression and the `volumeAnalyzer` dependency of
`isEnabled`. **The reference screenshots change here** - the captures
were taken from `Rmt.exe` with the view inside the window, so either the
excluded regions grow or new captures are needed (5.2). This is the batch
that changes what the user sees.

### W4 - the window's position and size

`RmtWindowPreferences` for a second window (5.3), and the window's
visibility persisted like the other view flags.

### W5 - the menu (decision 5.4, both programs or neither)

The Pokey menu moves into the new window's own menu bar. Requires the
same move in `Rmt.exe` (1.5), i.e. a `CFrameWnd` with its own message map
and the `OnCmdMsg` routing that plan 26 added; then
`doc/rmt_action_infos.md` is regenerated and both programs must produce
it identically again. The explorer keys need a decision too: the window
either handles them itself when focused, or they keep working only in the
tracker window (5.5).

### W6 - documentation

`doc/rmt_en.md`'s "Pokey Explorer" section (the window, how to open it),
`doc/rmt_changes.md`, `plans/README.md`, `NOTES.md`, and the javadoc of
the classes that lose the block.

## 4. What this is not

The C++ program keeps its view inside the tracker window unless W5 is
taken. The two programs' screens then differ deliberately for the first
time - worth saying out loud, because every UI decision so far has been
"the port looks like `Rmt.exe`".

## 5. Decisions for the user

1. **The second flag**: does the analyzer keep its AUDF/AUDC/AUDCTL
   numbers when the POKEY window is closed (recommended: yes, tie them to
   the analyzer's own setting - they are part of the analyzer), or do they
   disappear with the window?
2. **The reference screenshots**: extend the excluded region (cheap, the
   capture keeps showing a view the Java port no longer draws there) or
   take new captures from `Rmt.exe` with the registers switched off
   (cleaner, needs the user).
3. **Window preferences**: a second `Preferences` node in the port
   (self-contained) or generalize `MainWindowPreferences` in WUDSN Base
   (better, affects dis6502).
4. **The menu (W5)**: move it in both programs, or leave it in the main
   menu bar for now (recommended: leave it - the window is useful without
   it, and the action table stays identical, see 1.5).
5. **The explorer keys**, only if W5 is taken: handled by the new window
   when it has the focus, or left to the tracker window.

## 6. Verification

`mvn -o clean package` per batch; `SongUITest`'s reference comparison is
the net for W1 and W3; `compare_exports.ps1` and the action table must
stay identical for W1-W4 (they only change in W5, and then in both
programs). Manual: the window open while the song plays, the registers
updating at the frame rate, the explorer mode's keys, the window
reopening where it was left.

## 7. Estimate

W1 small-medium, W2 medium, W3 small, W4 small, W6 small - about a day
for the Java port. W5 alone is two to three days because of the C++
`CFrameWnd`, and carries the most risk, since the window code is the
least tested part of `Rmt.exe`.
