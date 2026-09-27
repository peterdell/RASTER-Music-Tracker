# Documentation generation: Markdown source, HTML in the distributions, menus extracted by the build (proposal)

Status: **approved 2026-09-27** with every recommendation (decisions 1-6;
decision 4: the tables compared, normalized). **D1 DONE 2026-09-27**: `DocGenerator`
(commonmark 0.29.0 + GFM tables as Maven dependencies, shaded into the jar),
`DocGeneratorTest`, `stage_java_release.sh` and `build_rmt_pre.bat` generate
`docs/` (12 files: 8 pages from `.md`, the two manuals, `rmt.gif`, `img/`);
`build_rmt-daily.bat` requires the jar. **D2 DONE 2026-09-27**: `dump
actions <file>` in both programs, `actions.rmtscript` in the comparison
(the two tables byte-identical, 176 rows), the daily build regenerates
`doc/rmt_action_infos.md` and fails on ERROR markers; found and fixed on the
way (section 2.2's findings below): the accelerator table bug, stale menu
labels, a dead About button, two typos, the MFC key names; Tools > Run
Script added to the C++ program, Ctrl+F12 (NTSC) to the Java port. **D3 DONE 2026-09-27**: `doc/rmt_en.md`
(the manual converted 1:1 by a one-time script, with a "Menus, Toolbars and
Keys" chapter that includes the generated table; the three F-key rows the
table proves stale corrected), `doc/rmt_en.html` deleted (generated from now
on), the online help of both programs and README point at the Markdown page
on GitHub. **D4 in progress 2026-09-27**: the manual's hotkey tables
checked row by row against `CRmtView::OnKeyDown`, the accelerator table
and the five key handlers of `GUI_Song.cpp` (`InfoKey`, `InstrKey`,
`ProveKey`, `TrackKey`, `SongKey`); the rows the code contradicts corrected
(see NOTES); the note keys of both layouts as a generated table
(`dump notekeys`, `doc/rmt_note_keys.md`, included in the manual - the
user's decision: one command, a section per layout); Ctrl+Shift+S and
Alt+Enter as real accelerators in both programs (the user's decision: add
them, the menu had promised them without a binding).
Origin: the user's two
points after the C++ scripting batch C3 - (1) the documentation is
maintained as Markdown on GitHub, because that is easier to maintain, and
must stay so; (2) normal users have no Markdown reader, so what ships in the
Windows zip and the Java distribution must be HTML - and the existing
`doc/rmt_en.html` is outdated and out of sync with the code, which is why
the menu traversal tooling exists in the C++ program: to extract the actual
menus for the documentation, ideally as part of the build.

## 1. What exists

### 1.1 Documents (`doc/`)

| File | Lines | Markdown features | Role |
|---|---|---|---|
| `rmt_en.html` | 46 KB | HTML (one `<style>`, `<font class="k">` for keys, `rmt.gif`) | the user manual for 1.31+; outdated |
| `rmt_en_128.html` | | HTML | Raster's original 1.28 manual; historical, stays as is |
| `rmt_changes.md` | 1246 | plain lists | the change history |
| `rmt_versions.md` | 64 | tables | the version history |
| `rmt_scripting.md` | 151 | tables, code fences, inline HTML (`\|` in tables) | the scripting reference |
| `rmt_format.md` | 288 | plain | module file format (technical) |
| `rmt_tracker.md` | 98 | plain | tracker internals (technical) |
| `rmt_tracker_drivers.md` | 74 | tables | the driver variants |
| `rmt_tracker_usage.md` | 22 | one image (`img/`) | GO TO LINE and subsongs |
| `rmt_action_infos.md` | 172 | one table, `<br>`/`<span>` markers | **generated** by `CCommands::Analyze()` |

The distributions' `docs/` folder receives `*.html` and `*.gif` only
(`build/build_rmt_pre.bat` into the gitignored `rmt/docs`, then the
PostBuildEvent's `xcopy` into `out/<Config>/output`), and the Java staging
(`build/stage_java_release.sh`) the same plus `img/` and - since S3 -
`rmt_scripting.md`, which a user cannot read there. The Java port's Help
menu opens `docs/rmt_en.html` locally (`RmtCommands`), the C++ program too.
README links the `.md` files directly and the `.html` files through
`html-preview.github.io`.

### 1.2 The menu analyzer (C++)

`CCommands` (`Commands.h/.cpp`): `Analyze()` loads `IDR_MAIN_WINDOW`,
walks every menu and submenu (`AnalyzeMenu`), the block toolbar
(`AnalyzeToolBar`) and the accelerator tables (`CAcceleratorTable`, with a
forced US keyboard layout for the key names), joins them per command ID
with the STRINGTABLE prompt (`CActionInfo`: text before `\n`, description
after), and `PrintActionInfos()` writes `../doc/rmt_action_infos.md` - a
table of access path (`Menu File / Export`, `Tool Bar Block`), entry,
accelerator (menu label's `\t` part checked against the real table), and
description, with red `ERROR:` markers where the label and the prompt or
the two accelerator sources disagree. It runs at start-up in **Debug
builds only** (`CRmtApp::InitInstance`, `#ifdef DEBUG`), relative to the
working directory. The checked-in file is from 2026-02-14 and carries three
`ERROR` markers. (The C1 batch removed an older duplicate in `RmtTest.cpp`
that only printed to message boxes; `CCommands` was never touched.)

### 1.3 The Java side

`Actions` (from `Actions.properties` via WUDSN Base's `NLS`): one `Action`
per menu, item and command with label, mnemonic, accelerator and
description; `RmtCommandId` maps each command to its action plus the
"checkable" and "accelerator is only a hint" flags (the C++ labels that show
a key the tracker handles itself, and a few stale ones - documented in
`Actions`' javadoc); `RmtMainMenu` builds the `JMenuBar` tree in C++'s
order; `RmtToolBars` the toolbars. Everything needed for the same table
exists as data, and Swing menus can be built without a display.

### 1.4 Tooling available offline

`org.commonmark:commonmark:0.29.0` and `commonmark-ext-gfm-tables:0.29.0`
(BSD-2-Clause, the reference CommonMark implementation for Java) are in the
local Maven repository, so `mvn -o` can depend on them and the shade plugin
puts them into `rmt.jar`. The `exec-maven-plugin` is not available offline;
`maven-antrun-plugin` is. The C++ daily build machine has a Java runtime
(the Java build runs there).

## 2. Design

### 2.1 Markdown -> HTML generator (Java, in `rmt.jar`)

`org.atari.raster.rmt.doc.DocGenerator` with a `main(sourceFolder,
targetFolder)`: converts every `doc/*.md` to `<name>.html` (CommonMark +
GFM tables), wraps it in one HTML template (title = the first heading, a
small embedded stylesheet in `rmt_en.html`'s spirit, `<meta charset>`),
rewrites links to `*.md` into `*.html` so the pages link each other in the
distribution while GitHub shows the `.md`, copies `img/`, `*.gif` and the
`.html` files that have no `.md` source (`rmt_en_128.html`; `rmt_en.html`
until 2.4). An include marker for generated tables:
`<!-- include: rmt_action_infos.md -->` inlines that file, so the manual can
carry the command reference without duplicating it. Tests:
`DocGeneratorTest` (each shipped `.md` converts; tables, fences, link
rewriting, include; a broken include fails).

Invocation: from the build scripts, not from a Maven phase - the C++ build
needs it too and the scripts already own the `docs/` step:

- `build/stage_java_release.sh`: `java -cp target/rmt.jar
  org.atari.raster.rmt.doc.DocGenerator doc "$STAGE/docs"` in place of the
  `cp` lines.
- `build/build_rmt_pre.bat`: the same into `rmt\docs` when
  `target\rmt.jar` exists; otherwise the `xcopy` of `*.html`/`*.gif` as
  today, so the C++ dev loop works without a Java build. `build_rmt-daily.bat`
  requires the jar (it already runs the export comparison with it) so the
  release zip always has the generated pages.

### 2.2 The command table as a script command in both programs

`dump actions <file>` (a new script command, `doc/rmt_scripting.md`): writes
the table `PrintActionInfos()` writes today - the same columns, the same
sort order - to the given file, and **fails the command** when it contains
an `ERROR` marker, so the mismatches become build failures instead of red
text nobody reopens.

- C++: `CCommands::Analyze()` refactored into `Analyze()` (collect) and
  `WriteActionInfos(path)` (returns the error count); the `#ifdef DEBUG`
  start-up call removed; `CScriptRunner::Dump` calls both. The forced US
  keyboard layout stays (key names).
- Java: `ActionInfos` built from `RmtCommandId`/`Actions`/`RmtMainMenu`
  (menu path from the built `JMenuBar`, accelerator from the action unless
  hint-only, description from the action, toolbar from `RmtToolBars`),
  written by the same `ScriptRunner` command.
- `test-resources/scripts/actions.rmtscript`: `dump actions actions.md`.
  The cross-program comparison then compares the two programs' tables -
  every menu path, key and description in one check, as the exports are
  checked today. Known divergences to settle first: `Tools > Run Script`
  (Java only), the Pokey Explorer register commands (48 IDs in C++, 4 kinds
  x register in Java), hint-only accelerators, mnemonics Java added. Either
  the dump normalizes them (plain text without `&`, accelerator from the
  real table only, hint keys in a separate column) or the comparison takes
  an allow-list; **decision 4**.
- `build_rmt-daily.bat` runs `Rmt.exe /SCRIPT:...actions.rmtscript` into
  `doc/rmt_action_infos.md` after the Release build, so the checked-in file
  is always the build's (a `git diff` shows what a menu change did), and
  the generator (2.1) includes it into the manual.

**D2 findings** (each fixed): (1) `CAcceleratorTable::Add()` cleared the
previous table, so the main window's accelerators were never in the table
and the key check could not fire. (2) MFC's `CMFCAcceleratorKey::Format`
names keys in the system language ("Strg", "Umschalt"); the table now
formats them itself, English, and the Java port formats the same way.
(3) Popups report `(UINT)-1` as their command ID and produced a bogus
"Help" row. (4) `GetPlainText` never reset its mnemonic state ("Clear Undo
&& Redo History" kept both ampersands). (5) Stale labels in `Rmt.rc`: Edit
Tracks/Instruments/Info showed F1/F2/F3 (the keys are F2/F3/Shift+F4),
Increase/Decrease Step Size showed Ctrl++/Ctrl+- (they are the keypad's
Num +/Num -), "Ctrl+SPACE"/"ESC"/"Shift+Ctrl+S"/"Shift-F6" spellings, the
Print prompt claimed Ctrl-P, "Decrease By 0x11", "Umnute". (6) The toolbar's
About button used `ID_HELP_ABOUT`, which has no handler - dead; now
`ID_HELP_ABOUT_APP`. (7) The Help menu used MFC's `ID_HELP`/
`ID_CONTEXT_HELP` and so MFC's prompts; it uses the program's own IDs and
prompts now. (8) Five prompts carried the key in the status text instead
of the "status
Label (Key)" convention. (9) Normalizations for the
comparison: the Java port now displays the Pokey Explorer's keys and the
block toolbar's keys as the C++ menus do (hint-only), its toolbar-only
labels follow the C++ tooltips, C++ splits a button's tooltip "Label (Key)"
into entry and key. (10) Real parity gaps closed instead of listed: Tools >
Run Script in the C++ program (`CScriptRunner::RunInteractive`, script
message mode with boxes), Ctrl+F12 toggle NTSC in the Java port
(`SONG_TOGGLE_NTSC`, accelerator-only as in `Rmt.rc`).

### 2.3 What ships where

Both distributions' `docs/`: `rmt_en.html`, `rmt_en_128.html`, `rmt.gif`,
`img/`, and the generated `rmt_changes.html`, `rmt_versions.html`,
`rmt_scripting.html`, `rmt_tracker*.html`, `rmt_format.html`,
`rmt_action_infos.html`. `doc/rmt_changes.md`'s reference to the scripting
document becomes a relative link (rewritten to `.html` in the
distribution). No `.md` is shipped.

### 2.4 The manual

One-time conversion of `rmt_en.html` to `doc/rmt_en.md` (pandoc or by hand:
`<h3>` chapters, `<font class="k">` keys to inline code, the image kept),
with `<!-- include: rmt_action_infos.md -->` replacing the hand-written
command lists, so the menus and keys in the manual are the build's from
then on. `rmt_en.html` is then generated; README's html-preview link becomes
a `.md` link, the Java `ONLINE_HELP_URL` follows. Revising the outdated
chapters is a separate, user-driven effort after that (chapter by chapter;
the generated table already replaces the part that goes stale fastest).

## 3. Batches

- **D1**: `DocGenerator` + tests, commonmark dependency, the two staging
  scripts, `rmt_scripting.md` no longer shipped as `.md`; verify both
  `docs/` folders in a local staging run and the Windows pre-build.
- **D2**: `dump actions` in C++ (the refactor of `CCommands`) and Java,
  `actions.rmtscript`, the daily build regenerating `rmt_action_infos.md`,
  the three current `ERROR` markers fixed (they are real label/prompt
  mismatches in `Rmt.rc`), the cross-program comparison of the tables per
  decision 4.
- **D3**: the manual to Markdown with the included table (2.4).
- **D4** (later, user-driven): revise the manual's chapters.

## 4. Decisions requested

1. **Converter**: the `commonmark` library as a normal Maven dependency
   (BSD-2, 0.29.0 available offline, exact CommonMark + GFM tables) vs. an
   own converter for the subset the docs use. *Recommended: the library.*
2. **Invocation** from the build scripts (2.1) vs. a Maven phase
   (`maven-antrun-plugin`'s `<java>` task). *Recommended: the scripts* -
   the C++ build needs the step too and `mvn package` stays a pure build.
3. **`dump actions` as a script command** (in line with "proper scripts
   instead of /TEST") vs. a command-line switch; and the `ERROR` markers
   failing the command. *Recommended: script command, failing.*
4. **Cross-program comparison of the action tables**: yes, with the dump
   normalized so only real divergences remain (recommended), or an
   allow-list, or no comparison (each program's table generated for its
   own documentation only).
5. **Checked-in `rmt_action_infos.md`** regenerated by the daily build
   (recommended - GitHub shows it, a menu change shows in the diff) vs. a
   build-only product.
6. **Order**: D1 and D2 first, the manual conversion (D3) after the
   pipeline works. *Recommended.*
