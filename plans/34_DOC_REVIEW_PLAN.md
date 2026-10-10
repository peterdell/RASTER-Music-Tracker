# Plan 34: Review of the doc/ pages - typos, grammar, wording, consistency

Status: ANALYZED 2026-10-10; D1 done 2026-10-10 (40 fixes, plus one
found while fixing: rmt_testing.md 25 "you are not always required" ->
they), D2 done 2026-10-10 (the binaries bullet was rewritten with the
current facts: resources extraction and the driver byte-check exist,
the remaining sources stay open; rmt_tracker_usage.md stays a
decision), D3 open except the first item.

All eleven `doc/*.md` files were read in full (2822 lines). Scope rules
applied:

- **Historical changelog text stays verbatim.** `rmt_changes.md` from
  "Changes in RMT 1.34.00" downward is VinsCool's and Raster's original
  release notes, preserved as written; only the maintained era (planned
  sections, 1.36, 1.35) is reviewed. Likewise the quoted AtariAge posts
  in `rmt_tracker_drivers.md` stay as quoted.
- **Generated files are reviewed, fixed only through their generator.**
  `rmt_action_infos.md` and `rmt_note_keys.md` come from `dump actions`
  / `dump notekeys`; both read cleanly, nothing to do.

## D1. Typos and grammar (mechanical, no meaning changes)

- `rmt_tracker.md` 26 and `rmt_tracker_drivers.md` 3: "the tracker
  **diver** is" -> driver (the same copied sentence in both files).
- `rmt_tracker.md` 104: "either unclear, or **nor**
  guaranteed" -> not; "There is **not** reason" -> no.
- `rmt_tracker.md` 108: "loaded in **DIS602**" -> DIS6502.
- `rmt_tracker_drivers.md` 28: "This version was mainly **with** RMT
  1.28" -> mainly used with.
- `rmt_testing.md` 3: "**a** Atari 8-bit tracker" -> an; 5: "absolutely
  **not** clue" -> no clue, "in future" -> in the future; 15: "to not
  have **public discussion thread**" -> a public discussion thread,
  "**be** have direct communication" -> but; 17: "**mailto::**" -> one
  colon; 23: "And from **I** saw" -> from what I saw; 24: "the
  "**rmt.int**"" -> rmt.ini; 26: "Have you **every** used the
  **Midi_input**? **I yes/now**, why?" -> ever / MIDI input / If
  yes/no.
- `rmt_en.md` 9: "Most **the** shortcuts" -> Most of the; 101:
  "**highligthed**" -> highlighted; 102: "from $0 to $F **Could**" ->
  missing period; 112: "current **possition**" -> position; 118:
  "Every new ENTER **hotkeys**" -> hotkey; 207: "Speed for **the**
  each table step" -> for each; 236: "as **a** two separate
  parameters" -> as two; 334: "Move cursor **song name line**" -> to
  the song name line; 344: "for each **elements**" -> element.
- `rmt_format.md` 201: "**- -** The file format" -> one bullet; 208:
  "one **of** more **segements**" -> or / segments; 222: "instead
  CR/LF" -> instead of; 256: "End marker **fo** the text" -> of; 268:
  "must **$02e1**" -> must be $02e1; 269: "Address nnnn+2 **the**
  display routine" -> of the; 271: "**Atari-8 bit**" -> Atari 8-bit,
  "a three-letter ASCII uppercase letters type code" -> reword; 281-283:
  "First letter of **type segment type** code" (3x) -> of the segment
  type code; 123: "ptr_instr0, ptr_instr1, **ptr_instr3**" -> ptr_instr2.
- `rmt_changes.md` (maintained era) 31: "sound **genration**" ->
  generation; 102: "doc/**rmt_version.md**" -> rmt_versions.md; 103:
  "(2026-01-08)**)**" -> one closing parenthesis; 27: heading "Changes
  in RMT 1.36 " carries a trailing blank.

## D2. Wording and structure (small rewrites, same meaning)

- `rmt_tracker.md` 105-108: the Future Plans list is garbled
  ("compatible as part of the build", "included **are** binaries in
  the distribution, similar to **for the** '.prf' files **are**
  distributed and loaded in DIS6502") - rewrite the three bullets.
- `rmt_tracker.md` 33: "Stereo indicates that all channels are
  **combined into** two different outputs" - they are split between
  the outputs; reword.
- `rmt_tracker.md` 47: "consists of two parts" - add the colon.
- `rmt_en.md` 103: "Fxx is intended to be a speed command, this is
  currently placeholder strings." - reword (singular/plural, comma
  splice).
- `rmt_en.md` 178: "All changes are **provided** either on each
  line" -> applied.
- `rmt_en.md` 226: "Values can range from positive ($00 to $7F) or
  negative ($FF to $80) numbers" -> "Values can be positive ... or
  negative ...".
- `rmt_en.md` 187/304/338: three bare "### Mouse control" headings are
  immediately followed by another same-level heading; demote the
  follow-up headings or merge.
- `rmt_format.md` 212: "except that the format version at offset $07
  ..., which will contain" - drop the "which".
- `rmt_tracker_usage.md`: already marked "// TODO This needs to be
  reworked" at the top; it has no title heading, starts with blank
  lines, and several sentences are garbled ("This is used mainly
  defined at the Export time", "where all Subsongs are located to").
  Rework wholesale or fold into the manual - a decision, not a patch.

## D3. Consistency and factual issues (need a decision or fact-check)

- **DONE 2026-10-10 - the two 1.36 ASAP bullets contradicted the
  fact-checked tracker page.** Decided by the user: the Java port has
  switched to ASAP; the C++ program keeps the DLLs until the C++ parts
  are deleted. Both bullets are now marked "Java port:", and
  rmt_tracker.md's Future Plans bullet states the same instead of the
  obsolete replace-the-DLLs plan. `rmt_changes.md` 31 ("All CPU emulation and sound generation
  is now independent of the old libraries and based on ASAP") and 34
  ("The full ASAP is used ... instead of the sa_c6502.dll/sa_pokey.dll
  ... replay and WAV export are now 16-bit") describe the JAVA port,
  but stand under the preamble "the changes below are changes of the
  Windows program Rmt.exe". The Windows program still loads the DLLs
  (verified in code, and `rmt_tracker.md` says so since 2026-10-09).
  Mark both bullets as Java-port statements or reword them.
- **"Last official version by Radek Sterba" appears twice.**
  `rmt_versions.md` says it for RMT 1.28 (2009, official table) AND
  for RMT 1.29 (2010, unofficial table). One of the two comments is
  wrong - presumably 1.29 should read "last version with source code
  by Radek Sterba" or similar.
- **"RMX" versus 'R','M','T'.** `rmt_format.md`'s version-2 draft says
  the metadata segment starts with the marker "RMX1" (prose, 224, and
  example, 229), but the struct at 248-251 spells the bytes
  'R','M','T','1' - and 271 reserves 'RMT' for the first segment. The
  struct bytes should read 'R','M','X'.
- **The SAP format link is a placeholder.** `rmt_format.md` 220:
  "[SAP format]((https://www.example.com))" - double parentheses and
  example.com; the real target is
  https://asap.sourceforge.net/sap-format.html.
- **"Vin Samuel" versus "VinsCool".** `rmt_tracker.md` 10, `rmt_en.md`
  5 and `rmt_versions.md` use the real name; `rmt_tracker_drivers.md`
  uses the handle; `rmt_changes.md`'s header uses both ("Vin Samuel,
  VinsCool"). Decide one convention (the changes header's "real name,
  handle" reads best) and apply it on first mention per page.
- **"Radek Sterba" without diacritics** in `rmt_changes.md`'s header
  versus "Radek Štěrba" everywhere else.
- **The scripting page miscounts its own table.** `rmt_scripting.md`
  140: "Two variables let a build script ..." - the table below lists
  three (`RMT_SCRIPT_OUTPUT`, `RMT_SCRIPT_LOG`,
  `RMT_SCRIPT_SHOW_WINDOW`).
- **A mangled file-name example.** `rmt_changes.md` 17 (2.00 planned):
  'named in the format "-VU-Player_V1.xex"' - the name before the dash
  is missing (likely "<song>-VU-Player_V1.xex").
- **Module header types.** `rmt_format.md` 39: offset $00 "WORD Header
  string \"RMT4\"" is four bytes, not a WORD (the next field is at
  $04); 42: "$06 BYTE Replay frequency" names what the manual calls
  the engine/instrument speed - align the terms.
- **`rmt_en.md` 327**: "S | Engine speed ... (X player calls per
  frame.)" - the X does not match the field name S.
- **`rmt_en.md` 113/115**: the term '"wise loop"' (twice) looks like a
  leftover; check what the feature is really called (track loop?).
- **`rmt_tracker.md` 12**: "known issues in RMT version 1.34" - the
  page describes the 1.36 fork; check whether 1.34 is still the
  intended baseline of the issue lists.
- **Distortion table readability.** `rmt_en.md` 247/251: values 6 and
  E are both explained as "Distortion C", which is correct POKEY-wise
  but reads like a copy error - say "maps to AUDC distortion C".
- **`rmt_versions.md` 39**: "RMT 1.28, which was the de facto standard
  from 2008 to 2021" - 1.28 was released 2009-05-19 per the table
  above; check the year.

## Execution

1. D1 in one commit (mechanical).
2. D2 in one commit (each rewrite reviewed individually).
3. D3 one by one - each needs the decision noted above; the 1.36 ASAP
   bullets first, since they misstate what the shipping Windows
   program does.
