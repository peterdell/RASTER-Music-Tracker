# Plan: the Pokey Explorer in the Java port (P1-P4)

Status: proposal (2026-09-30). Nothing implemented yet.

## 1. What the C++ program does

The Pokey Explorer is a debugging mode for pitch calculations: the RMT
routines are switched off, the keyboard writes the driver's POKEY shadow
registers directly, and the "POKEY REGISTERS" view shows, for one chosen
channel, the numbers behind the pitch formula. Four pieces:

### 1.1 The mode

`EditMode::POKEY_EXPLORER_MODE`, a "special prove mode" like the MIDI CH15
mode. Entered by Pokey > Activate Pokey Explorer Mode (Ctrl+Shift+F5,
`CRmtView::OnEditActivatePokeyExplorerMode` -> `SetEditMode`, the item is
enabled while not in the mode). Left through the Edit/Jam toggle
(`SwitchEditMode`: a special mode always drops back to edit mode). While
in it, the timer's `CAtariTrackerDriver::Play(specialProveMode)` skips the
RMT routine and only runs `SetPokey` (the shadow bytes go to `$D200`), the
info line shows "EXPLORER MODE (PITCH CALCULATIONS)", and every key of the
tracks/instruments parts goes to the controller (`CSong::ProveKey`, first
lines) - the note keys are not notes here.

### 1.2 `CPokeyController` (`PokeyController.h/.cpp`, 500 lines, owned by `CSong`)

State: `m_channel_index` (0-3, the channel the view details) and
`m_divisor` (1.0-10000.0, a free factor in the pitch formula). Memory:
the driver's shadow registers `AUDF` `$3178` (8 bytes), `AUDC` `$3180` (8
bytes), `AUDCTL` `$3C69`, `SKCTL` `$3CD3` (the same addresses the MIDI CH15
knobs write). `OnKeyDown(vk, shift, control)` returns TRUE for a handled
key:

| Key | Effect |
|---|---|
| Enter / Backspace | next / previous channel (0-3, wrapping) |
| + / - (`VK_OEM_PLUS`/`MINUS`) | divisor +0.1 / -0.1; with Shift +1.0 / -1.0; clamped to 1..10000 |
| 1 3 5 7 / Q E T U | AUDF0..3 + 1 / - 1; with Shift +/- $10 (byte arithmetic, wraps) |
| 2 4 6 8 / W R Y I | AUDC0..3 the same |
| C G F K J D A P | AUDCTL bits 0..7 toggled (EOR) |
| M | SKCTL EOR $88 (two-tone) |

Control is never looked at. The digits and letters are virtual key codes,
i.e. the physical QWERTY positions on any layout.

### 1.3 The view (`CPokeyView::Draw`, `PokeyView.cpp:241-295`)

With `explorerMode`, for the channel whose index (`pokey * 4 + channel`)
equals the controller's, three extra rows at `explorerRow`:

```
CH_IDX: n , AUDF: $xx , AUDC: $xx , MODULO: nnn
COARSE_DIVISOR: nnn , DIVISOR: nnnnn.n , MODOFFSET: n
 pppp.pp HZ = ((FREQ17 / (COARSE_DIVISOR * DIVISOR)) / (AUDF + MODOFFSET)) / 2
```

`modoffset` 7 for 16-bit joined, 4 for 1.79 MHz, else 1 with a coarse
divisor of 28 (64 kHz) or 114 (15 kHz); `modulo` = the first i in 3..255
dividing `audf + modoffset`; the low AUDF byte is printed at column 61 for
joined channels; the pitch is `CTuning::GetPitch(i_audf, coarseDivisor,
divisor, modoffset)` (already ported as `Tuning.getPitch`). The view runs
only with the "Pokey Chip Registers" display on.

### 1.4 The Pokey menu

Pokey > Channel 1-4 > AUDFn/AUDCn > Increase/Decrease By 0x01/0x10,
AUDCTL > Bit 0-7, SKCTL > Two Tone Mode, Debug Channel > Next/Previous,
Divisor > +-0.1/1.0 - 50 items with the keys as hints, and the accelerator
table `IDR_POKEY_EXPLORER` (appended to the accelerators for the command
table dump). **None of the 50 items has an `ON_COMMAND` handler**, so MFC
shows them all greyed and clicking is impossible; only the keys of 1.2
work, and only in the explorer mode. (The Java menu reproduces that: the
items exist and are disabled.)

### 1.5 Provable bugs met on the way

- `OnPreviousChannel()` never decrements: `if (m_channel_index < 0) = 3`
  without the `m_channel_index--` before it. Backspace does nothing. Fix in
  both (the standing rule).
- The menu hint for "Channel 3 > AUDC2 > Decrease" says `Z` (`Rmt.rc`
  menu label and `IDR_POKEY_EXPLORER`), the controller uses `VK_Y` - the
  QWERTY row Q W E R T **Y** U I. The Java menu copied the `Z`. The user:
  the swap is the German (QWERTZ) versus the English keyboard - both are
  right for their own keyboard. Resolved by plan 27 (K3: the explorer's
  decrease row follows the keyboard layout, the hint shows the QWERTY
  letters, so `Y`); not a fix of this plan any more.

## 2. What the Java port already has

- `EditMode.POKEY_EXPLORER_MODE` with `isSpecialProveMode()`; the info line
  text; `AudioEngine` calls `driver.play(specialProveMode)` (RMT routine
  skipped, `setPokey` kept) - the sound side is done.
- `RmtCommandId.EDIT_ACTIVATE_POKEY_EXPLORER_MODE` sets the mode (enabled
  while not in it); the toolbar/Edit toggle leaves it
  (`UiState.switchEditMode`).
- The whole Pokey menu (`RmtMainMenu.createPokeyMenu`) with the C++ key
  hints: four *shared* register command ids (`POKEY_REGISTER_INCREASE_BY_01`
  etc. used by all 32 register items), the eight AUDCTL bit ids, two-tone,
  next/previous channel, four divisor ids - all "never enabled".
- `SongInput.proveKey` has the branch `if (editMode == POKEY_EXPLORER_MODE)
  return false; // CPokeyController unported`.
- `PokeyView.draw` has the placeholder comment at the explorer rows and no
  `explorerMode`/controller parameters; the row constant `explorerRow`
  exists in C++ (`pokey2Row + pokeyRows`), check the Java layout constants.
- `Tuning.getPitch(audf, coarseDivisor, divisor, cycle)`.
- The driver memory is `session.atari.getMemory()` (the MIDI CH15 code
  writes the same shadow addresses through it).

## 3. Design

- **`PokeyController`** in `org.atari.raster.rmt.ui` (key codes are
  `VirtualKey`'s, a UI concern; the class is otherwise pure and testable
  headless): `channelIndex`, `divisor`, `onKeyDown(vk, shift, control)`,
  the `increase/decrease/eor` helpers over the `byte[]` memory, the
  constants `AUDF/AUDC/AUDCTL/SKCTL` (shared with `MidiInput` - move them
  to one place, e.g. `PokeyController` public constants that `MidiInput`
  uses). Owned by `RmtSession` (`session.pokeyController`), not by `Song`:
  C++'s `CSong::m_PokeyController` is a UI object hung on the song for
  lack of a session; the model stays free of key codes.
- **Keys**: `SongInput.proveKey`'s branch calls
  `session.pokeyController.onKeyDown(vk, shift, control)` and returns its
  result (redraw), as C++ does.
- **View**: `PokeyView.draw(..., boolean explorerMode, PokeyController
  controller)`; `SongUI` passes `ui.editMode == POKEY_EXPLORER_MODE` and
  `session.pokeyController`; the three rows ported line for line
  (`printfMini(1, "%d", ...)`, `printByte`, `atColumn`), the `%6.1f`/`%9.2f`
  formats with `Locale.ROOT`.
- **Menu**: see the decision in 5. Faithful = the 50 items stay disabled
  (C++ has no handlers). Improved = the items work in the explorer mode;
  the four shared register ids become per-register ids (32, as C++'s
  resource ids `ID_POKEY_AUDF0_INCREASE_BY_01` ...), each command calling
  the controller's method; `isEnabled` = in the explorer mode. The action
  table (`dump actions`) does not change either way: it lists menu paths,
  labels and keys, not ids.

## 4. Batches

### P1 - `PokeyController` and the keys

The class of 3 with the previous-channel fix; the `proveKey` branch; the
constants shared with `MidiInput`. Tests (`PokeyControllerTest`): every key
of the table in 1.2 against the memory bytes (wrap of `$FF + 1`, `$00 - 1`,
the `$10` steps), AUDCTL bits and SKCTL `$88`, divisor steps and clamps,
channel cycling both ways, unknown key -> false and memory untouched,
Control ignored. C++: `PokeyControllerTests.cpp` in `RmtTests` (the file is
already linked into the test project) with the same table, so both
controllers are checked by the same cases; the C++ fix of
`OnPreviousChannel`.

### P2 - the view

`PokeyView.draw` with the explorer block and the two new parameters;
`SongUI`'s call. Tests: the existing `PokeyView` test style (a canvas
rendered headless, text read back): with the mode on and channel 1
selected, the rows show `CH_IDX: 1`, the AUDF/AUDC bytes, `COARSE_DIVISOR:
28`, `MODOFFSET: 1`, the low byte for a joined channel, and the pitch
equals `Tuning.getPitch` for those inputs; with the mode off the rows are
empty. Optional: a reference screenshot from `Rmt.exe` in the explorer mode
(Ctrl+Shift+F5 with the registers view on) for the pixel comparison the
other screens have - needs the user to take it.

### P3 - the Pokey menu (decision 5.1)

Faithful: nothing to do; the hint fix of 1.5 only (`RmtMainMenu`'s
`decreaseAudc` array, `Rmt.rc` label and accelerator).
Improved: 32 per-register ids + the existing 15 wired to the controller,
enabled in the explorer mode; in C++ the same 50 `ON_COMMAND`/
`ON_UPDATE_COMMAND_UI` pairs (one macro line each) so the menu works there
too - otherwise the Java menu would do what the C++ menu cannot.

### P4 - documentation and bookkeeping

- `doc/rmt_en.md`: a section "Pokey Explorer" after "MIDI Input": how to
  enter and leave the mode, what it is for, the key table of 1.2, the
  three rows and the formula. The manual has no explorer text today.
- `doc/rmt_changes.md`: the 1.37 planned line "The Pokey Explorer in the
  Java port" goes; 1.36 entries for the two fixes (Backspace, the `Y`
  hint) and, with the improved P3, the working menu.
- `README.md` "Not ported: printing and the Pokey Explorer" -> printing.
- `doc/rmt_action_infos.md` regenerated (the `Y`), `plans/README.md`,
  `NOTES.md`, the javadoc placeholders in `PokeyView`, `RmtCommandId`,
  `RmtCommands`, `SongInput` removed.

## 5. Decisions for the user

1. **The Pokey menu**: faithful (50 disabled items, keys only - what
   `Rmt.exe` does) or improved in both programs (the items work in the
   explorer mode; ~100 mechanical lines in C++, ~60 in Java plus 32 ids).
   Recommended: improved in both - a menu of 50 permanently dead items is
   a defect, and the fix is mechanical.
2. **The two fixes of 1.5** in both programs (recommended, the standing
   rule).
3. **The reference screenshot** for P2 (optional, needs `Rmt.exe` by hand).

## 6. Verification

Per batch `mvn -o clean package`; P1 and P3 also the Release build of
`Rmt.sln` with `RmtTests.exe`; `dump actions` through the daily build or
`build/compare_exports.ps1` (`actions.rmtscript`) for the hint fix - both
tables identical. Manual: Ctrl+Shift+F5 with Pokey Chip Registers on, the
keys of 1.2 change the shown bytes and the pitch line in both programs the
same way; the Edit/Jam toggle leaves the mode.

## 7. Estimate

P1 small-medium (the class is long but trivial; the tests carry the
table). P2 small. P3 faithful: trivial; improved: medium, mechanical. P4
small. Order: P1, P2, P3, P4.
