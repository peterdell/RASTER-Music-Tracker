# Plan 43: Remove the printing commands from both programs

Status: PROPOSED 2026-10-12. The decision is already made and recorded in
`doc/rmt_changes.md` under 1.36: printing is intentionally not ported and
will not be, because the Windows program's print support was never
implemented beyond the generated MFC skeleton, users asked about it
replied they do not need printing, and every platform's own screenshot
function already takes a picture of the window. This plan carries that
decision into the two programs, so the menus stop offering something
that does not work.

## What is there today

Three commands, in both programs, that cannot do anything useful.

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

What reaches the printer: MFC's default `OnPrint` calls
`CRmtView::OnDraw` with a printer DC, and `OnDraw` blits
`m_width x m_height` - the *window's* size in screen pixels - from its
memory DC, inside an `if (g_screenupdate)`. So the page is a small image
in one corner, or blank when that flag happens to be clear, and
`StretchBlt` from a memory DC is something printer drivers may refuse
outright. `ID_FILE_PRINT_DIRECT` has a handler but no menu item at all.

Note that `ID_FILE_PRINT*` are MFC's own IDs from `afxres.h`, not RMT's -
`resource.h` has no entries to remove.

**Java** - present, greyed, and refusing:

| Where | What |
| ----------------------------------- | ------------------------------------------------ |
| `RmtCommandId.java:19`              | `FILE_PRINT`, `FILE_PRINT_PREVIEW`, `FILE_PRINT_SETUP` |
| `Actions.java:55-57`                | `MainMenu_File_Print` (with `Ctrl+P`), `_PrintPreview`, `_PrintSetup` |
| `Actions.properties:32-37`          | their labels and tooltips                        |
| `RmtMainMenu.java:110-112`          | the three File-menu items                        |
| `RmtCommands.java:294`              | `-> host.notAvailable("Printing")`               |
| `RmtCommands.java:736`              | `isEnabled -> false`                             |

## The one thing that makes this a single change, not two

`doc/rmt_action_infos.md` is generated from each program's own resources
by the `dump actions` script command, and the build compares the two
files byte for byte. Its lines 12-14 are the three print commands. So
the C++ and Java removals have to land together: either program alone
would make the comparison fail. The same holds for the manual, which
lists `CONTROL+P` in its key table.

## Proposed steps

- **P1. C++ resources.** Remove the three `MENUITEM` lines, the `Ctrl+P`
  accelerator and the three `STRINGTABLE` prompts from `Rmt.rc`. The File
  menu's separators need a look afterwards, so the menu does not end up
  with two in a row or a trailing one.
- **P2. C++ handlers, commented out rather than deleted.** The four
  `ON_COMMAND` entries and the three `OnPreparePrinting`/
  `OnBeginPrinting`/`OnEndPrinting` overrides, each kept in place as a
  comment with one line saying why, so the next reader sees the decision
  instead of wondering whether printing was forgotten. Commenting out the
  overrides is safe: they only override `CView`'s own virtuals, which
  remain. `RmtView.h`'s declarations go the same way.
- **P3. Java.** Remove the three `RmtCommandId` constants, the three
  `Actions` fields, their six `Actions.properties` lines, the three
  `RmtMainMenu` items, and drop the three ids from the two
  `RmtCommands` cases - leaving `FILE_PROPERTIES`, which shares those
  cases for an unrelated reason (see plan 44) and must keep behaving
  exactly as it does now. The `Actions` field and its two
  `Actions.properties` keys go together: the texts are bound to the
  fields by `NLS` when `Actions` loads (`Actions.java:13`), so a key left
  behind has no field to attach to and a field left behind has no text.
- **P4. The generated table and the documents.** Regenerate
  `doc/rmt_action_infos.md` from both programs and confirm they are
  byte-identical and three rows shorter. Remove the `CONTROL+P` row from
  `doc/rmt_en.md`'s key table - it currently says the key is without
  function, which P1-P3 make untrue in the other direction. Add one line
  to `doc/rmt_changes.md` under 1.36 recording that the entries are now
  gone, beside the existing declaration of why.
- **P5. Decide what `Ctrl+P` becomes.** Removing the accelerator frees
  it, and `Actions.java:108` records that `Ctrl+J`/`Ctrl+K`/`Ctrl+T` were
  moved on 2026-09-28 precisely because their old keys went to Print,
  Open and New. Leaving it unassigned is a perfectly good answer; this
  step only requires that the choice be made deliberately rather than by
  omission.

## Verification

- Both suites green. No test references `FILE_PRINT` today, so none
  should need changing; if one does, that is a finding worth recording
  rather than a line to edit.
- `doc/rmt_action_infos.md` regenerated from both programs, byte-
  identical, three rows shorter, and the `dump actions` comparison in the
  build still passes.
- The C++ File menu opens with no stray or doubled separator, and
  `Ctrl+P` does nothing.
- `compare_exports` unchanged: this touches menus and resources only.

## Out of scope

- Implementing printing in any form. That is the decision this plan
  carries out, not a question it reopens. If it is ever revisited, the
  Java port is where it would be cheap: `CanvasXY` already draws the
  whole window into a `BufferedImage`, so a `Printable` would only have
  to scale that image by an integer factor onto the page.
- `File > Properties` and its `Alt+Enter` accelerator. It sits in the
  same two `RmtCommands` cases and is equally without function today,
  but for a different reason and with a different future - see
  [plan 44](44_SONG_PROPERTIES_DIALOG_PLAN.md).
