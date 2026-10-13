# Plan 44: A song properties dialog for the song's own settings

Status: PROPOSED 2026-10-12. `File > Properties` and its `Alt+Enter`
accelerator exist in both programs' menus and do nothing: there is a
`MENUITEM` and an accelerator in `Rmt.rc` with no handler behind them at
all, so the entry is greyed in C++, and the Java port routes it to
`host.notAvailable` and reports it disabled. The command slot is
therefore already reserved in both programs - this plan gives it its
dialog.

## What belongs in it, and where those settings live today

Three things that are properties of the *song*, not of the program, and
that sit in the Options dialog today:

| Setting | Today | Scope of the value |
| ---------------------------- | ------------------------------------------------ | ---------------- |
| NTSC / PAL                   | `OptionsDialog`'s Module group, a checkbox       | the song's: `CSong::m_ntsc`, `IsNTSC()`/`SetNTSC()` |
| Tracker driver version       | `OptionsDialog`'s Module group, a combo box      | the song's, since [plan 41](41_DRIVER_VERSION_IS_A_SONG_PROPERTY_PLAN.md): `CSong::m_trackerDriverVersion` |
| Tuning settings              | a `Tuning...` button in `OptionsDialog` opening `TuningDialog` | not the song's yet: `g_tuning`/`g_tuningRatios` in C++, `RmtSession` in Java - S1 moves them |

The Options dialog already calls the first two a "Module" group, and
`OptionsValues.java:27` already says of the driver version "The song's
own setting, like `ntsc`". So the anomaly is written down in the code
already: a program-settings dialog is carrying song properties. Plan 41
made the driver version a song property in the model; this plan does the
same for the tuning settings and then finishes all three in the user
interface.

## This is a move, not an addition

A setting that persists and is changed rarely belongs in exactly one
place. The point of this plan is therefore **not** a second way to reach
these values: each one has to *leave* the Options dialog as it arrives in
the song properties, so that after the change there is still exactly one
control per value. If any of the three turns out to be wanted in both
places, that is a reason to reconsider the move for that setting, not a
reason to duplicate it.

## Persistence does not change here

Where the values are stored stays exactly as it is:

- NTSC in `rmt.ini` as `NTSC_SYSTEM` (`RmtView.cpp:606`,
  `RmtConfig.java:206`).
- The driver version in `rmt.ini` as well.
- The tuning settings in `tuning.ini`.

So a song still travels without its region and its tuning: open someone
else's module and it plays with *your* settings, before this plan and
after it. That is a real limitation, and it is not this plan's to fix -
`doc/rmt_changes.md` lists the per-module `.ini` under 1.37, with the RMT
2.0 format behind it, and both stay out of scope here.

What this plan does is smaller and worth doing on its own: the values
become the song's in the model, and they are edited where they belong,
in a dialog of their own rather than in the dialog for program settings.
Keeping `rmt.ini` has a useful side effect - because nothing is read from
the module, opening a file cannot change these values, so there is no
question of what an existing module defaults to. Every module behaves as
it does today.

## Proposed steps

- **S1. Make the tuning settings the song's, in both programs.** The
  region and the driver version are already `CSong`/`Song` members
  (`m_ntsc`, and `m_trackerDriverVersion` since
  [plan 41](41_DRIVER_VERSION_IS_A_SONG_PROPERTY_PLAN.md)); the tuning is
  still `g_tuning`/`g_tuningRatios` in C++ (`Global.h:32-33`) and lives
  on `RmtSession` in the Java port (`RmtSession.java:45`), so the port
  hands a `ui` value into `model` functions - `Song.resetTuningVariables`
  takes both as parameters, and those parameters go away once the song
  owns them. Plan 41's S1-S2 are the pattern to follow, including
  deleting the globals rather than leaving one beside the member.
- **S2. Build the dialog.** `ModalDialog` with OK/Cancel per the WUDSN
  Base rules, the NTSC checkbox and the driver-version `ValueSetField`
  moved over from `OptionsDialog`, and the tuning settings reachable from
  it as they are from Options today. Labels carry no trailing colon, and
  the entry keeps its `...` since it opens a dialog in which something
  can be changed.
- **S3. Remove the three from the Options dialog,** leaving no second
  control for any of them, and check what remains of the Module group.
  The `rmt.ini` and `tuning.ini` reading and writing stays untouched -
  only the controls move.
- **S4. Wire `File > Properties` and `Alt+Enter`** in both programs: the
  C++ menu entry gets its first handler, and the Java `FILE_PROPERTIES`
  command leaves the `notAvailable`/`isEnabled -> false` cases it
  currently shares with the print commands (which [plan
  43](43_REMOVE_PRINTING_PLAN.md) removes).
- **S5. Scripting and the generated table.** `set ntsc` and
  `set driver` already exist as script commands and must keep working
  against the same song values. `doc/rmt_action_infos.md` gains the
  Properties entry's real description in both programs, and the manual's
  `ALT+ENTER` row - which today claims it opens the song properties -
  becomes true.

## Open questions

- **Do the two track-line highlights move too?** They are Options
  settings today, and they describe how a song is written - a waltz wants
  3 and 6 where a beat wants 4 and 8 - so a case can be made that they
  belong with the song. Against it: they change nothing about how the
  song sounds, only about how it is displayed, which makes them view
  preferences like the keyboard layout. They are not in the list above,
  so the answer is "no" unless this plan is told otherwise.
- **What is the dialog called?** The menu says "Properties", the Options
  group says "Module", the file format says "module" and this plan's
  title says "song". The program uses "song" and "module" for the same
  thing in different places; this is a chance to pick one for the user
  interface.
- **Does the driver version belong beside the other two?** It decides
  which player a song is written for, so it is a property of the song in
  the same sense - but unlike region and tuning it also decides what the
  tracker loads into Atari memory, and changing it has an immediate
  effect that region and tuning do not.

## Verification

- Both suites green, and `rmt.ini`/`tuning.ini` round-trip unchanged:
  the configuration tests assert the written files, so S1-S3 must not
  move a single key.
- `compare_exports` identical. Nothing here changes what either program
  produces; if it does, the move changed a value's lifetime somewhere.
- `doc/rmt_action_infos.md` regenerated from both programs and still
  byte-identical, now carrying the Properties entry's description.
- The three settings are reachable in exactly one place each, and a
  script's `set ntsc` / `set driver` still changes what the dialog shows.

## Out of scope

- Storing the values with the module. The per-module `.ini` of
  `doc/rmt_changes.md`'s 1.37 plan and the RMT 2.0 format behind it both
  stay out of scope: this plan changes where the values are edited and
  which object owns them, not where they are written. Nothing it does
  should prejudge either, and nothing it does has to be undone by them.
- Printing. `File > Properties` only shares two `switch` cases with the
  print commands today, nothing more - see [plan
  43](43_REMOVE_PRINTING_PLAN.md).
