# Plan 43: Remove the printing commands from both programs

Status: DONE 2026-10-13, P1-P5. The decision was already made and
recorded in `doc/rmt_changes.md` under 1.36: printing is intentionally
not ported and will not be, because the Windows program's print support
was never implemented beyond the generated MFC skeleton, users asked
about it replied they do not need printing, and every platform's own
screenshot function already takes a picture of the window. This plan
carried that decision into the two programs, so the menus no longer
offer something that does not work.

## What was there

Three commands, in both programs, that could not do anything useful.

**C++** - the generated MFC skeleton, untouched since the project was
created:

| Where | What |
| ------------------------- | --------------------------------------------------------- |
| `Rmt.rc:1187-1189`        | `MENUITEM "&Print...\tCtrl+P"`, `Print Pre&view`, `Prin&t Setup...` |
| `Rmt.rc:1525`             | the `Ctrl+P` accelerator for `ID_FILE_PRINT`              |
| `Rmt.rc:1691-1693`        | the three `STRINGTABLE` prompts the status bar shows      |
| `Rmt.cpp:50`              | `ON_COMMAND(ID_FILE_PRINT_SETUP, CWinApp::OnFilePrintSetup)` |
| `RmtView.cpp:81-83`       | `ON_COMMAND` for `ID_FILE_PRINT`, `ID_FILE_PRINT_DIRECT` and `ID_FILE_PRINT_PREVIEW` |
| `RmtView.h:80-82`, `RmtView.cpp:469-483` | `OnPreparePrinting`, `OnBeginPrinting`, `OnEndPrinting` |

What reached the printer: MFC's default `OnPrint` calls
`CRmtView::OnDraw` with a printer DC, and `OnDraw` blits
`m_width x m_height` - the *window's* size in screen pixels - from its
memory DC, inside an `if (g_screenupdate)`. So the page was a small image
in one corner, or blank when that flag happened to be clear, and
`StretchBlt` from a memory DC is something printer drivers may refuse
outright. `ID_FILE_PRINT_DIRECT` had a handler but no menu item at all.

`ID_FILE_PRINT*` are MFC's own IDs from `afxres.h`, not RMT's, so
`resource.h` had nothing to remove.

**Java** - present, greyed, and refusing:

| Where | What |
| ----------------------------------- | ------------------------------------------------ |
| `RmtCommandId.java:19`              | `FILE_PRINT`, `FILE_PRINT_PREVIEW`, `FILE_PRINT_SETUP` |
| `Actions.java:55-57`                | `MainMenu_File_Print` (with `Ctrl+P`), `_PrintPreview`, `_PrintSetup` |
| `Actions.properties:32-37`          | their labels and tooltips                        |
| `RmtMainMenu.java:110-112`          | the three File-menu items                        |
| `RmtCommands.java:294`              | `-> host.notAvailable("Printing")`               |
| `RmtCommands.java:736`              | `isEnabled -> false`                             |

## The one thing that made this a single change, not two

`doc/rmt_action_infos.md` is generated from each program's own resources
by the `dump actions` script command, and the two files are compared byte
for byte - by `build/compare_exports.ps1` in the daily build and by
`CrossProgramExportTest` on every `mvn test` with `Rmt.exe` built. Its
lines 12-14 were the three print commands. So the C++ and Java removals
had to land together: either program alone would have failed the
comparison. The same held for the manual, which listed `CONTROL+P` in its
key table.

## What was done

- **P1. C++ resources.** DONE. The three `MENUITEM` lines, the `Ctrl+P`
  accelerator and the three `STRINGTABLE` prompts are gone from
  `Rmt.rc`. The print block had a separator of its own, so one of the two
  that would have ended up adjacent went with it: the File menu now runs
  Import/Export, separator, Properties, separator, Exit.
- **P2. C++ handlers, commented out rather than deleted.** DONE. The four
  `ON_COMMAND` entries and the three `OnPreparePrinting`/
  `OnBeginPrinting`/`OnEndPrinting` overrides, each kept in place as a
  comment with a line saying why, so the next reader sees the decision
  instead of wondering whether printing was forgotten. Commenting out the
  overrides is safe: they only overrode `CView`'s own virtuals, which
  remain. `RmtView.h`'s declarations went the same way.
- **P3. Java.** DONE. The three `RmtCommandId` constants, the three
  `Actions` fields, their six `Actions.properties` lines and the three
  `RmtMainMenu` items are gone, and the two `RmtCommands` cases now name
  `FILE_PROPERTIES` alone. The `Actions` field and its two
  `Actions.properties` keys had to go together: the texts are bound to
  the fields by `NLS` when `Actions` loads (`Actions.java:13`), so a key
  left behind has no field to attach to and a field left behind has no
  text.
- **P4. The generated table and the documents.** DONE.
  `doc/rmt_action_infos.md` regenerated from `Rmt.exe` exactly three rows
  shorter, with `doc/rmt_note_keys.md` unchanged, and
  `CrossProgramExportTest` confirms the port writes the same file. The
  `CONTROL+P` row is gone from `doc/rmt_en.md`'s key table, and 1.36's
  printing entry in `doc/rmt_changes.md` now says the entries are gone
  from both programs rather than greyed in one.
- **P5. `Ctrl+P` is deliberately unassigned.** DONE, by decision. It is
  free and stays free: no editor function is moved onto it, since the
  three that were moved off Ctrl+N/Ctrl+O/Ctrl+P on 2026-09-28 have been
  Ctrl+T/Ctrl+K/Ctrl+J for a release and should not move twice.
  `Actions.java:106` and the change log record it, so the next person to
  look for a free key finds the reason rather than guessing.

## Found while doing it

- **1.36's own "Windows standard keys" entry had become wrong.** It said
  "Print is Ctrl+P" while listing the three editor functions that moved
  off those keys. Since 1.36 is unreleased, the entry now names Ctrl+N,
  Ctrl+O and Ctrl+P as the keys that were taken, which is both true and
  still explains why three functions moved.
- **`notAvailable("Printing")` was the wrong message for Properties.**
  Removing the three ids left `FILE_PROPERTIES` alone in a case that
  reported the feature as "Printing". It now says "Song properties",
  which is what the command is - the behaviour (disabled, and refusing
  if reached) is unchanged.

## Verification

- 450 C++ tests and 702 Java tests pass; both C++ projects build with no
  errors or warnings.
- `doc/rmt_action_infos.md` is three rows shorter and nothing else in it
  changed; `CrossProgramExportTest` compares both programs' dumps byte
  for byte and passes, so the two menus still agree.
- No test referenced `FILE_PRINT`, as expected, so none needed changing.
- The C++ File menu has no stray or doubled separator, and `Ctrl+P` does
  nothing in either program.

## Out of scope

- Implementing printing in any form. That is the decision this plan
  carried out, not a question it reopened. If it is ever revisited, the
  Java port is where it would be cheap: `CanvasXY` already draws the
  whole window into a `BufferedImage`, so a `Printable` would only have
  to scale that image by an integer factor onto the page.
- `File > Properties` and its `Alt+Enter` accelerator. It shared the two
  `RmtCommands` cases and is equally without function today, but for a
  different reason and with a different future - see
  [plan 44](44_SONG_PROPERTIES_DIALOG_PLAN.md).
