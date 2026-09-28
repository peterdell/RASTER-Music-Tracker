# RMT Scripting

Both RASTER Music Tracker programs - the Windows `Rmt.exe` and the Java
port - can run a script file: open a song, set what the export dialogs
would ask, export in any format, save. This is meant for batch files, build
scripts and CI, where the SAP, XEX, WAV and ASM files of a song are produced
from its `.rmt` source reproducibly, for repeated export sets while
composing, and for comparing the two programs' exports. The same script runs
in both.

## Running a script

- Java port, from the command line, without a window (no display is
  needed):

  ```
  java -jar rmt.jar /SCRIPT:build.rmtscript
  ```

  `-script:` is accepted too. The program folder (the jar's folder, or
  `-Drmt.config.dir=<folder>`) provides `rmt.ini` and `tuning.ini` as for
  the window, so the tuning shapes the exports exactly as in the tracker.
  Messages that would be boxes in the window are printed to the console:
  errors and warnings to stderr, information to stdout.

- From the window, in both programs: **Tools > Run Script...** picks a
  script and runs it on the current session. Message boxes stay boxes; the
  commands' output is shown once at the end.

- Windows program:

  ```
  start /wait Rmt.exe /SCRIPT:build.rmtscript
  ```

  `Rmt.exe` is a windowed program, so `cmd.exe` returns to the prompt at
  once unless it is started with `start /wait` (in PowerShell:
  `Start-Process Rmt.exe "/SCRIPT:build.rmtscript" -Wait`). The window is
  created but stays hidden. The messages go to the console the program was
  started from; when there is none (started from Explorer or a scheduler),
  they go to `<script>.log` next to the script. The exit code is the same
  as the Java port's (`%ERRORLEVEL%` after `start /wait`).

Exit codes of the command line:

| Code | Meaning |
|---|---|
| 0 | every command succeeded |
| 1 | a command failed; the script stopped there and the message names the line |
| 2 | the script file could not be read or has a syntax error (the message names the line) |

## Syntax

- One command per line. Blank lines are ignored, `#` starts a comment (at
  the start of a line or before a token).
- Tokens are separated by blanks. A value with blanks is quoted:
  `"My Song.sap"`. Inside quotes, `\"` is a quote and `\\` a backslash.
- Options are written `name=value` after the command's arguments, in any
  order: `export sap out.sap author="Raster" subsongs=0`. A quoted value
  works after the `=` as well.
- Command names, option names and option values that are choices
  (`yes`/`no`, format names, driver names) are case-insensitive; other
  values (texts, labels, file names) keep their case.
- Yes/no options accept `yes`, `no`, `true`, `false`, `on`, `off`, `1`, `0`.
- Relative file names are relative to the script file's folder, so a
  script travels with its songs. Output files (`save`, `export`) go to the
  folder of `set output` instead when the script gave one; the folder is
  created when it is missing.
- An output file that exists already is an error, unless the script says
  `set overwrite yes` first.

## Commands

| Command | Meaning |
|---|---|
| `open <file>` | Opens a song. The format follows the extension: `.rmt`, `.txt` or `.rmw` (as File > Open). |
| `save <file>` | Saves the song as `.rmt`, `.txt` or `.rmw` by extension (as File > Save as). The RMT format's integrity check applies. |
| `export <format> <file> [name=value ...]` | Exports the song (as File > Export as). Formats and options below. |
| `set overwrite yes\|no` | Whether later `save`/`export` commands may replace existing files. Default `no`. |
| `set output <folder>` | Where later `save`/`export` commands write (relative to the script's folder; created when missing). Default: the script's folder. |
| `set ntsc yes\|no` | The Options dialog's NTSC setting (the video standard: 60 or 50 frames per second, and the POKEY clock). |
| `set driver <version>` | The Options dialog's tracker driver: `unpatched`, `unpatched-with-tuning`, `patch3`, `patch6`, `patch8`, `patch16` (the default), `patch-prince-of-persia`. |
| `dump actions <file>` | Writes the program's command table - every menu item, toolbar button and key with its description - as a Markdown table (`doc/rmt_action_infos.md` is made this way by the build). A row marked ERROR is an inconsistency in the program's resources and fails the command. |
| `dump notekeys <file>` | Writes the note keys of the QWERTY and AZERTY keyboard layouts (which key plays which note) as Markdown tables (`doc/rmt_note_keys.md`). |
| `echo <text ...>` | Prints the text. |
| `quit` | Ends the script (implicit at its end). |

## Export formats and options

The format names are those of the Export dialog's file-type list. Every
option has the value the dialog would show when it opens, so a bare
`export sap out.sap` produces exactly what the dialog's OK produces with
untouched fields. The extension of the format is added to the file name
when it is missing.

| Format | File | Options |
|---|---|---|
| `stripped-rmt` | `.rmt` | `address` (the module's address, `$4000` or decimal; default: the loaded module's), `sfx` (SFX support), `gvf` (global volume fade), `nos` (no starting songline), `asmformat` = `xasm` or `atasm` |
| `asm` | `.asm` | `type` = `tracks` or `song` (tracks, or the whole song by song columns), `notes` = `index` or `freq`, `durations` = `notes`, `note-duration` or `duration-note`, `prefix` (the label prefix, empty = no labels; default `MUSIC`) |
| `sapr` | `.sapr` | `author`, `name`, `date` (default: the song's name, the song's author, today), `subsongs` (default: the subsongs found in the song) |
| `lzss` | `.lzss` | none. Writes the full tune and, when they are longer than 16 bytes, the `_INTRO.lzss` and `_LOOP.lzss` siblings. |
| `sap` | `.sap` | as `sapr` (a SAP of type B with the LZSS player) |
| `xex` | `.xex` | `text` (the screen text, up to 5 lines of 40 characters, `\n` between lines; default: the song's name, date and author lines the dialog proposes), `rasterbar` (yes/no), `shuffle` (yes/no), `region-auto` (yes/no), `color` (the rasterbar color, 0-255) |
| `rmtplayer-asm` | `.asm` | `startlabel` (default `RMT_SONG_DATA`), `relocate` (a comma list of `instruments`, `tracks`, `songlines`, or `none`), `instruments-label`, `tracks-label`, `songlines-label`, `asmformat`, `sfx`, `gvf`, `nos` |
| `wav` | `.wav` | none (16-bit, 44.1 kHz, 2 channels, the song once up to its loop point) |

Options that a format does not have, and values that are not valid, stop
the script with a message naming the line.

## Example

```
# Build the release assets of Delta
set overwrite yes
open Delta.rmt
export sap Delta.sap author="Raster" name="Delta" subsongs="0"
export xex Delta.xex text="DELTA BY RASTER\nJAVA PORT" rasterbar=no
export stripped-rmt Delta_stripped.rmt address=$5000
export wav Delta.wav
echo finished
```

```
java -jar rmt.jar /SCRIPT:Delta\build.rmtscript
```

## Environment variables

Two variables let a build script run one and the same script file with
different outputs, without editing it:

| Variable | Programs | Meaning |
|---|---|---|
| `RMT_SCRIPT_OUTPUT=<folder>` | both | The output folder for every `save`/`export`; overrides the script's `set output`. |
| `RMT_SCRIPT_LOG=<file>` | Windows | Writes the messages to this file instead of the console or `<script>.log`. |
| `RMT_SCRIPT_SHOW_WINDOW=1` | Windows | Shows the window during the script run (normally it stays hidden), to watch what a script does. |

## Comparing the two programs

The scripts in `test-resources/scripts` export the reference songs in every
format, once with the defaults and once with every option set.
`build/compare_exports.ps1` runs each of them through `Rmt.exe` and through
`rmt.jar` with the same `rmt.ini`/`tuning.ini` and compares the output
folders byte for byte (WAV files excepted: 8-bit in `Rmt.exe`, 16-bit in
the Java port). `build/build_rmt-cpp-daily.bat` runs it after the release build
when `target/rmt.jar` exists; the JUnit test `CrossProgramExportTest` runs
the same comparison with every `mvn test` when `Rmt.exe` is built. A
difference is a port bug in one of the two programs.

## Not available as script commands

Importing (MOD, TMC), the instrument files (`.rti`), the track files and
`New` - the tracker's interactive functions. The command set follows the
export workflow; further commands can be added if a need arises.
