# RMT scripting (Java port)

The Java port of RASTER Music Tracker can run a script file: open a song,
set what the export dialogs would ask, export in any format, save. This is
meant for batch files, build scripts and CI, where the SAP, XEX, WAV and
ASM files of a song are produced from its `.rmt` source reproducibly, and
for repeated export sets while composing.

The C++ `Rmt.exe` does not run these scripts (its `/SCRIPT` and `/TEST`
switches are developer utilities).

## Running a script

- From the command line, without a window (no display is needed):

  ```
  java -jar rmt.jar /SCRIPT:build.rmtscript
  ```

  `-script:` is accepted too. The program folder (the jar's folder, or
  `-Drmt.config.dir=<folder>`) provides `rmt.ini` and `tuning.ini` as for
  the window, so the tuning shapes the exports exactly as in the tracker.
  Messages that would be boxes in the window are printed to the console:
  errors and warnings to stderr, information to stdout.

- From the window: **Tools > Run Script...** picks a script and runs it on
  the current session. Message boxes stay boxes; the commands' output is
  shown once at the end.

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
  script travels with its songs.
- An output file that exists already is an error, unless the script says
  `set overwrite yes` first.

## Commands

| Command | Meaning |
|---|---|
| `open <file>` | Opens a song. The format follows the extension: `.rmt`, `.txt` or `.rmw` (as File > Open). |
| `save <file>` | Saves the song as `.rmt`, `.txt` or `.rmw` by extension (as File > Save as). The RMT format's integrity check applies. |
| `export <format> <file> [name=value ...]` | Exports the song (as File > Export as). Formats and options below. |
| `set overwrite yes\|no` | Whether later `save`/`export` commands may replace existing files. Default `no`. |
| `set ntsc yes\|no` | The Options dialog's NTSC setting (the video standard: 60 or 50 frames per second, and the POKEY clock). |
| `set driver <version>` | The Options dialog's tracker driver: `unpatched`, `unpatched-with-tuning`, `patch3`, `patch6`, `patch8`, `patch16` (the default), `patch-prince-of-persia`. |
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

## Not available as script commands

Importing (MOD, TMC), the instrument files (`.rti`), the track files and
`New` - the tracker's interactive functions. The command set follows the
export workflow; further commands can be added if a need arises.
