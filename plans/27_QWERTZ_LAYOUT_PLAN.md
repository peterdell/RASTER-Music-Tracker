# Plan: a QWERTZ keyboard layout in both programs (K1-K5)

Status: proposal (2026-09-30). Nothing implemented yet. Grew out of plan
26's finding 1.5 (the Pokey Explorer's "Z" vs "Y" hint): the user's
observation that the swap is the German versus the English keyboard, and
the decision to solve it the way RMT already solves AZERTY - as a layout.

## 1. The problem

Both programs read keys as Windows virtual key codes (Java translates its
key events to the same codes in `VirtualKey`). For letters and digits the
code is the *character the key prints*, not its position: on a German
keyboard the key under the 6 prints Z and delivers `VK_Z`. RMT's note keys
are a piano laid over *positions* - the bottom row from C-1, the row above
from C-2 - so on a German keyboard with the "QWERTY Layout" option the
lowest C (QWERTY `Z`, bottom left) lands on the key under the 6, and the
A-2 of QWERTY `Y` on the bottom left key; the same for the OEM keys ([ ] ;
' / are ü + ö ä - on a German keyboard, at partly different positions).
The French keyboard has the same problem and got its own layout, AZERTY
(`keynotes_AZERTY`, `KeyboardRows`, `KeyLegend`, the Options combo). German
users have nothing.

The Pokey Explorer (plan 26) shows the same thing in one key: the
controller's decrease row is `Q W E R T Y U I`, the row under the digits
on a QWERTY keyboard; the menu hint says `Z`, written from a German
keyboard. Under the layout idea both are right, for their own keyboard.

## 2. What exists (both programs, verified 2026-09-30)

- `KeyboardLayout { QWERTY = 0, AZERTY = 1 }` (`General.h`;
  `model/KeyboardLayout.java` with int constants), `g_keyboard_layout` /
  `RmtOptions.keyboardLayout`, persisted as `KEYBOARD_LAYOUT = n` in
  `rmt.ini`, default QWERTY (`ResetRMTConfig`); the Options dialog's
  "Keyboard Layout" combo with "QWERTY Layout"/"AZERTY Layout"
  (`OptionsDialog.cpp:109`, `OptionsDialog.KEYBOARD_LAYOUTS`).
- The note tables `keynotes_QWERTY`/`keynotes_AZERTY` (256 entries by
  virtual key; `Keyboard2NoteMapping.cpp` / `.java`), selected in
  `NoteKey(vk)` / `noteKey(vk, layout)`; `keynumbs`/`keynumblock09` are
  layout independent.
- The keyboard picture: `KeyboardRows(layout)` (four rows of virtual keys),
  `KeyLegend(vk, layout)` (what the key prints - the French `& é " ' (` row,
  the OEM keys), `NoteKeysTable()` / `NoteKeys.table()` = "### QWERTY" and
  "### AZERTY" pictures plus tables; `dump notekeys` writes it,
  `doc/rmt_note_keys.md` is that output, included by the manual's "Note
  Keys" section.
- Tests: `Keyboard2NoteMappingTests.cpp`, `Keyboard2NoteMappingTest.java`,
  `ScriptRunnerTest` (the picture's first lines), `OptionsDialogsTest`
  (the combo), `DocGeneratorTest` ("<h3>AZERTY</h3>").
- `Ctrl+Z`/`Ctrl+Y` (undo/redo) and the other accelerators stay character
  based - that is what every Windows program does and what users expect;
  layouts apply to the piano keys (and, K3, the explorer's key row) only.

## 3. The QWERTZ layout

The German keyboard (Windows KBDGR virtual keys; the Java translation is
K2's job):

| Row | Keys (virtual key) |
|---|---|
| number | `1`..`0`, ß (`VK_OEM_4` $DB), ´ (`VK_OEM_6` $DD) |
| upper | `Q W E R T Z U I O P`, ü (`VK_OEM_1` $BA), + (`VK_OEM_PLUS` $BB) |
| home | `A S D F G H J K L`, ö (`VK_OEM_3` $C0), ä (`VK_OEM_7` $DE), # (`VK_OEM_2` $BF) |
| bottom | < (`VK_OEM_102` $E2), `Y X C V B N M`, `,` `.` (`$BC` `$BE`), - (`VK_OEM_MINUS` $BD) |

`keynotes_QWERTZ` = the QWERTY table by *position*: `Y` and `Z` exchanged
(Y = C-1, Z = A-2), the OEM keys moved to the German key at the QWERTY
position - ü F-3 and + G-3 (QWERTY `[` `]`), ö D#2 (QWERTY `;`), ´ F#3
(QWERTY `=`), `-` E-2 (QWERTY `/`); ß, ä, # and < unmapped like their
QWERTY positions (`-`, `'`, none, none). `KeyLegend` for QWERTZ: the
umlauts and ß as UTF-8 (the French legends already are), `+ - # ´ ^ <`.
`KeyboardRows` as the table above. The picture then shows
`Q W E R T Z U I O P ü +` over `C-2 D-2 ...` - what the German user sees
on the keys.

## 4. Batches

### K1 - the layout in both programs

`KeyboardLayout::QWERTZ = 2` / `KeyboardLayout.QWERTZ = 2` (the ini value;
old files keep meaning the same), `keynotes_QWERTZ` and its selection in
`NoteKey`/`noteKey`, `KeyboardRows`/`KeyLegend`/`NoteKeysTable` with the
third picture "### QWERTZ" (`NoteKeys` the same), the Options combo entry
"QWERTZ Layout" (`OptionsDialog.cpp`, `OptionsDialog.KEYBOARD_LAYOUTS`).
Tests: the mapping tests of both languages get the QWERTZ cases (`Y` ->
C-1, `Z` -> A-2, `ü` -> F-3, `-` -> E-2, `ß` unmapped); `ScriptRunnerTest`
and `DocGeneratorTest` the third heading; `OptionsDialogsTest` the third
entry; the cross-program check: `actions.rmtscript` already dumps the
note keys (`dump notekeys rmt_note_keys.md`), so `compare_exports.ps1`
proves both programs' three pictures identical byte for byte.

### K2 - the Java key translation for the German OEM keys

`VirtualKey` turns Swing key events into the Windows codes; letters and
digits are safe, the OEM keys are mapped from the key character today
(verify: `VirtualKey.java` ~95-130). ü ö ä ß ´ # + and < must arrive as
$BA $C0 $DE $DB $DD $BF $BB $E2 on Windows, Linux and macOS with a German
layout - a table of `keyChar -> VK` for the QWERTZ characters next to the
existing AZERTY ones, and a unit test per character. Without this K1
works in `Rmt.exe` and in the Java port only for the letters.

### K3 - the Pokey Explorer's key row follows the layout (both programs)

The controller's decrease keys become the layout's second key row
(`KeyboardRows(layout)[1]`, the first eight keys): QWERTY `Q W E R T Y U I`,
QWERTZ `Q W E R T Z U I`, AZERTY `A Z E R T Y U I` - positional, like the
digits above them. The menu hint (`Rmt.rc`, `RmtMainMenu.decreaseAudc`)
stays the QWERTY letters (a static resource; the action table is layout
independent) and the manual's explorer section says "the key row under
the digits in your layout". Java: after plan 26 P1 (done 2026-09-30, the controller exists); C++: any
time. The hint is `Y` in both programs since plan 26 (the QWERTY letter);
K3 adds the per-layout key row.

### K4 - documentation and bookkeeping

`doc/rmt_en.md` "Note Keys": "(QWERTY, AZERTY or QWERTZ)";
`doc/rmt_note_keys.md` regenerated (three pictures); `doc/rmt_scripting.md`
`dump notekeys` row; the Options section if the manual has one (check);
`doc/rmt_changes.md` 1.36: "Keyboard layout QWERTZ (German) for the note
keys"; `plans/README.md`, `NOTES.md`.

### K5 - the default layout from the operating system (decision 5.2)

`ResetRMTConfig` (the first start, no `rmt.ini`) picks the layout from the
keyboard language: German -> QWERTZ, French -> AZERTY, else QWERTY. C++:
`GetKeyboardLayout(0)`'s language id; Java: `InputContext.getInstance()
.getLocale()` (falls back to the default locale). Only the initial default
- a saved `KEYBOARD_LAYOUT` always wins. Small, and the German and French
users get the right piano without finding the option.

## 5. Decisions for the user

1. **K3**: the explorer's key row per layout (recommended) or the plain
   `Y` hint fix from plan 26.
2. **K5**: the OS-derived default (recommended, small) or QWERTY as today.
3. **The OEM key assignments of 3**: they follow the QWERTY *positions*
   (ü = F-3 because `[` is F-3). The alternative would be to keep the
   *characters* (`-` stays unmapped as on QWERTY) - positional is what the
   piano picture promises, so positional is recommended; a German user
   should check the picture on a real keyboard (`dump notekeys` or the
   manual).

## 6. Verification

`mvn -o clean package`, Release build of `Rmt.sln` with `RmtTests.exe`,
`build/compare_exports.ps1` (the note key pictures of both programs
identical), the regenerated `doc/rmt_note_keys.md` read against a German
keyboard. Manual: Options > QWERTZ Layout, play the two piano rows in both
programs; the Java port also on Linux with a German layout for K2.

## 7. Estimate

K1 small-medium (mechanical in both languages). K2 small, but needs the
three platforms for confidence. K3 small (Java after plan 26 P1). K4 small.
K5 small. Order: K1, K2, K4, then K3 with plan 26, K5 any time.
