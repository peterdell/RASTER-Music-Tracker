# Plan 44: A module properties dialog for the song's own settings

Status: DONE 2026-10-14, S1-S5. `File > Properties` and its `Alt+Enter`
accelerator existed in both programs' menus and did nothing: a
`MENUITEM` and an accelerator in `Rmt.rc` with no handler behind them at
all, so the entry was grayed in C++, and the Java port routed it to
`host.notAvailable` and reported it disabled. The command slot was
already reserved in both programs - this plan gave it its dialog.

## What belongs in it, and where those settings lived

Three things that are properties of the *song*, not of the program, and
that sat in the Options dialog:

| Setting | Before | Scope of the value |
| ---------------------------- | ------------------------------------------------ | ---------------- |
| NTSC / PAL                   | `OptionsDialog`'s Module group, a checkbox       | the song's: `CSong::m_ntsc`, `IsNTSC()`/`SetNTSC()` |
| Tracker driver version       | `OptionsDialog`'s Module group, a combo box      | the song's, since [plan 41](41_DRIVER_VERSION_IS_A_SONG_PROPERTY_PLAN.md): `CSong::m_trackerDriverVersion` |
| Tuning settings              | a `Tuning...` button in `OptionsDialog` opening `TuningDialog` | not the song's yet: `g_tuning`/`g_tuningRatios` in C++, `RmtSession` in Java - S1 moved them |

The Options dialog called the first two a "Module" group, and
`OptionsValues.java:27` said of the driver version "The song's own
setting, like `ntsc`". So the anomaly was written down in the code
already: a program-settings dialog was carrying song properties. Plan 41
made the driver version a song property in the model; this plan did the
same for the tuning settings and then finished all three in the user
interface.

## This is a move, not an addition

A setting that persists and is changed rarely belongs in exactly one
place. The point was therefore **not** a second way to reach these
values: each one *left* the Options dialog as it arrived in the module
properties, so there is still exactly one control per value.

## Persistence did not change

Where the values are stored is exactly as it was:

- NTSC in `rmt.ini` as `NTSC_SYSTEM` (`RmtView.cpp:606`,
  `RmtConfig.java:206`).
- The driver version in `rmt.ini` as well.
- The tuning settings in `tuning.ini`.

So a song still travels without its region and its tuning: open someone
else's module and it plays with *your* settings, before this plan and
after it. That is a real limitation, and it was not this plan's to fix -
`doc/rmt_changes.md` lists the per-module `.ini` under 1.37, with the RMT
2.0 format behind it, and both stayed out of scope.

Keeping `rmt.ini` had a useful consequence: because nothing is read from
the module, opening a file cannot change these values, so there was no
question of what an existing module defaults to. Every module behaves as
it did.

## What was done

- **S1. The tuning settings are the song's, in both programs.** DONE.
  `CSong` has `m_tuning`/`m_tuningRatios` with `GetTuning()`/
  `GetTuningRatios()` beside `m_ntsc`, and `Song` has the same in the
  port; `g_tuning`/`g_tuningRatios` are gone from `Global.h`/`Global.cpp`
  and the two fields are gone from `RmtSession`. Everything that turns a
  note into a POKEY frequency reads them off the song now
  (`TuningTables.cpp`, `PokeyView`, the `.ini` reader and writer, the
  tuning dialog), and `Song.resetTuningVariables` lost the two parameters
  the port used to hand in from the `ui` package. Plan 41's pattern,
  globals deleted rather than left beside the member.
- **S2. The dialog.** DONE. In C++ `CModulePropertiesDialog`
  (`ModulePropertiesDialog.h`/`.cpp`) on the `IDD_FILE_PROPERTIES`
  template that was already in `Rmt.rc`; in the port
  `ModulePropertiesDialog` (`ModalDialog`, OK/Cancel per the WUDSN Base
  rules) with `ModulePropertiesValues` as its data holder. The NTSC
  checkbox and the driver-version combo moved over, the `Tuning...`
  button with them.
- **S3. The Options dialog lost all three.** DONE. The "Module Defaults"
  group and the `Tuning...` button are gone from `IDD_OPTIONS` (the
  dialog is 43 units shorter and everything below the group moved up),
  from `COptionsDialog` (members, DDX entries, the combo population,
  `OnClickedOptionsTuning`), and from the port's `OptionsDialog`,
  `OptionsValues` and `RmtCommands.applyOptions`. The `rmt.ini` and
  `tuning.ini` reading and writing was not touched.
- **S4. `File > Properties...` and `Alt+Enter` work in both programs.**
  DONE. `CRmtView::OnFileProperties()` fills the dialog in and applies
  what it returns - the two `if`s that were `OnToolsOptions()`'s - and
  the port's `FILE_PROPERTIES` command does the same through
  `Host.editModuleProperties` and `RmtCommands.applyModuleProperties`.
  The entry ends in `...` now, since it opens a dialog in which
  something can be changed.
- **S5. Scripting, the generated table and the documents.** DONE.
  `set ntsc` and `set driver` work against the same song values - and
  C++'s `set ntsc` is now right about which tuning it rescales (see
  below). `doc/rmt_action_infos.md` carries the Properties entry's real
  description in both programs, the manual's `ALT+ENTER` row says what
  the dialog contains, and `doc/rmt_changes.md` records the move under
  1.36.

## Found while doing it

- **The dialog was already designed.** `Rmt.rc` held an
  `IDD_FILE_PROPRTIES` template captioned "Module Properties", with the
  NTSC checkbox, the driver-version combo and a `Tuning...` button -
  plus Title/Author/Date/Subsongs fields that an RMT module does not
  carry (they belong to the SAP export). No class ever used it. The
  three settings this plan moves are exactly the three its author
  reserved, so the template became this dialog, reduced to them, and the
  symbol lost its typo.
- **C++'s `set ntsc` script command rescaled the wrong tuning.** It
  changed the global tuning while setting the region of its own
  `m_song` - invisible while the tuning was global, a real mix-up once
  the song owns it. It now rescales the tuning of the song it is
  editing.
- **A test asserted the global instead of the song.**
  `SongEditingTest.ResetTuningVariablesUsesTheNtscOrPalBaseTuning` reset
  the fixture's own song and then read the values off `g_Song`; both were
  the same global before. It reads the song it resets now.
- **`DataTypes.properties` had nine labels ending in a colon**, against
  the rule that labels carry none (the C++ dialogs' own labels keep
  theirs - they are MFC resources, not this family's). Fixed for all
  nine, not only for the two that moved.
- **The name.** The open question of what to call the dialog is answered
  "module": the rest of the user interface says module (Create a new
  module, Open a module file, the Module group), and the reserved
  template said "Module Properties" too. The code mirrors it -
  `CModulePropertiesDialog`, `ModulePropertiesDialog`,
  `ModulePropertiesValues` - while the command keeps C++'s
  `ID_FILE_PROPERTIES` name.
- **The driver version belongs in the dialog**, confirmed by the user on
  2026-10-14. It decides which player a song is written for, which makes
  it a property of the song in the same sense as the region and the
  tuning - even though, unlike those two, changing it has an immediate
  effect, because the tracker reloads the player's routines into Atari
  memory on the spot.
- **The track-line highlights did not move**, per this plan's own
  answer: they decide how a song is displayed, not how it sounds, so they
  stay in Options with the other view preferences.

## Verification

- 450 C++ tests and 705 Java tests pass (three new ones: the dialog's
  round trip, the command's OK/Cancel behavior and
  `applyModuleProperties`); both C++ projects build with no errors and no
  warnings.
- `doc/rmt_action_infos.md` regenerated from `Rmt.exe` changed in exactly
  one row - `| Menu File | Properties... | Alt+Enter | Open the module
  properties dialog |` - and `CrossProgramExportTest` compares it with
  the port's own dump byte for byte, so the two menus still agree.
- `rmt.ini` and `tuning.ini` round-trip unchanged (`RmtConfigTest`
  asserts the written files), so the move did not touch a single key.
- Every export is identical between the two programs (the cross-program
  comparison over all scripts), so nothing about the sound changed.

## Out of scope

- Storing the values with the module. The per-module `.ini` of
  `doc/rmt_changes.md`'s 1.37 plan and the RMT 2.0 format behind it both
  stayed out of scope: this plan changed where the values are edited and
  which object owns them, not where they are written.
- Printing. `File > Properties` only shared two `switch` cases with the
  print commands - see [plan 43](43_REMOVE_PRINTING_PLAN.md).

## Open afterwards

- **The Tuning... button acts outside the OK/Cancel round trip.** The
  tuning dialog writes the song's tuning the moment it is confirmed, as
  it did from the Options dialog, while the video standard and the
  driver version are applied only when the module properties are
  confirmed. Two consequences: Cancel does not take back a tuning edit,
  and a tuning edited while Patch 16 was selected stays behind if the
  version is then cancelled back to a driver that ignores it
  (`IsTuningSupported()`). Making the tuning part of the round trip
  would mean giving the tuning dialog a working copy of the song's
  tuning and applying it with the rest - worth doing when the tuning
  itself is next touched, not on its own.
- The Title/Author/Date/Subsongs fields the reserved template had. An
  RMT module carries none of them today; if the RMT 2.0 format does,
  this dialog is where they belong.
