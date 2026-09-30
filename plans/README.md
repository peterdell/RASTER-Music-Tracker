# Plans

The plan files of this repository, one per batch of work, numbered in the
order they were created - which is also the logical order of the effort:
`01` to `12` are the C++ characterization-testing phase (2026-09-21 to
09-24), `13` onwards the Java port and what grew out of it. A new plan gets
the next free number. Each plan carries its status in its first lines; this
table is the overview.

The three standing documents are not numbered: `CPP_RULES.md` (the C++ coding
style rules), `OVERALL_PLAN.md` (the original brief for the whole effort)
and `NOTES.md` (the running log, one dated entry per batch - the place to
look for what was done, why, and what was found).

| No. | Plan | Purpose | Status |
|---|---|---|---|
| 01 | [SONG_IO_SONG_REMAINING_PLAN](01_SONG_IO_SONG_REMAINING_PLAN.md) | The remaining methods of `Song.cpp`/`IO_Song.cpp`: safe cluster vs. real hazards, characterization tests | Done 2026-09-23 |
| 02 | [EXPORTV2_PLAN](02_EXPORTV2_PLAN.md) | `ExportV2` split into dialog-free `Apply` entry points | Done 2026-09-22 |
| 03 | [IO_IMPORTER_PLAN](03_IO_IMPORTER_PLAN.md) | The MOD/TMC importers under test | Done 2026-09-23 |
| 04 | [BROADER_SURVEY_PLAN](04_BROADER_SURVEY_PLAN.md) | Survey of the untested files beyond `Song.cpp` | Survey, no open work |
| 05 | [SAP_LZSS_WAV_XEX_PLAN](05_SAP_LZSS_WAV_XEX_PLAN.md) | The export chain (SAP-R, LZSS, WAV, XEX) under test | Done 2026-09-24 |
| 06 | [DUAL_MODE_PATTERN_PLAN](06_DUAL_MODE_PATTERN_PLAN.md) | The output-parameter pattern for methods that end in a message box (`InstrInfo`/`TrackInfo`) | Done 2026-09-23 |
| 07 | [FILE_TIERING_STRATEGY](07_FILE_TIERING_STRATEGY.md) | Why and how the `.cpp` files are split into testable tiers | Reference |
| 08 | [MESSAGEBOX_REFACTOR_PLAN](08_MESSAGEBOX_REFACTOR_PLAN.md) | `MessageBox` calls replaced by the `Send*Message` helpers (`Messages.cpp`) | Done 2026-09-23 |
| 09 | [UNDO_PLAN](09_UNDO_PLAN.md) | `Undo.cpp` under test | Done 2026-09-23 |
| 10 | [EXPORTLZSS_PLAN](10_EXPORTLZSS_PLAN.md) | `ExportLZSS` under test | Done 2026-09-24 |
| 11 | [EXPORTWAV_PLAN](11_EXPORTWAV_PLAN.md) | `ExportWAV` under test | Done 2026-09-24 |
| 12 | [UI_SURVEY_PLAN](12_UI_SURVEY_PLAN.md) | Survey of the C++ UI layer, the input for the Java UI port | Survey, no open work |
| 13 | [JAVA_PORT_PLAN](13_JAVA_PORT_PLAN.md) | The Java port's master plan: decisions, phase A (the model), the batch log, the "next" pointer | Phases A and B done 2026-09-27 |
| 14 | [JAVA_IMPORTER_PLAN](14_JAVA_IMPORTER_PLAN.md) | The importers (`IO_ImporterCore.cpp`) in Java | Done 2026-09-26 |
| 15 | [JAVA_SONGEDITING_PLAN](15_JAVA_SONGEDITING_PLAN.md) | `SongEditing.cpp` in Java, sub-batch by sub-batch | Done 2026-09-26 |
| 16 | [JAVA_PORT_NEXT_STEPS_PLAN](16_JAVA_PORT_NEXT_STEPS_PLAN.md) | What remained of phase A after the model: emulation, exports, the closing sweep | Done 2026-09-27 |
| 17 | [JAVA_UI_PORT_HANDOVER](17_JAVA_UI_PORT_HANDOVER.md) | The short entry point for starting phase B in a fresh session | Reference |
| 18 | [JAVA_UI_PORT_PLAN](18_JAVA_UI_PORT_PLAN.md) | Phase B, the Swing UI: batches B1-B9 | Done 2026-09-27 |
| 19 | [JAVA_AUDIO_PLAN](19_JAVA_AUDIO_PLAN.md) | Real-time sound on ASAP's emulation (B8) | Done 2026-09-27 |
| 20 | [JAVA_B9_PLAN](20_JAVA_B9_PLAN.md) | Packaging: the jar, the program folder, the release workflow (B9) | Done 2026-09-27 |
| 21 | [JAVA_SCRIPTING_PLAN](21_JAVA_SCRIPTING_PLAN.md) | The script feature of the Java port (S1-S3) | Done 2026-09-27 |
| 22 | [CPP_SCRIPTING_PLAN](22_CPP_SCRIPTING_PLAN.md) | The same scripts in `Rmt.exe` and the cross-program export comparison (C1-C3) | Done 2026-09-27 |
| 23 | [DOC_GENERATION_PLAN](23_DOC_GENERATION_PLAN.md) | Markdown as the documentation source, generated HTML, the command and note key tables from the programs (D1-D4) | Done 2026-09-27 |
| 24 | [EXPORT_SCREEN_UPDATES_PLAN](24_EXPORT_SCREEN_UPDATES_PLAN.md) | The C++ exports redraw the whole screen up to 60 times a second: proposals for a quiet, faster export (E1-E3) | E1 done 2026-09-28; E2/E3 later |
| 25 | [JAVA_MIDI_PLAN](25_JAVA_MIDI_PLAN.md) | MIDI input for the Java port: device lifecycle, the `CSong::MidiEvent` handler, the CH16/CH10 controller mode, a `midi` script command for the cross-program check (M0-M5) | Done 2026-09-30 |
| 26 | [JAVA_POKEY_EXPLORER_PLAN](26_JAVA_POKEY_EXPLORER_PLAN.md) | The Pokey Explorer in the Java port: `CPokeyController`, the explorer rows of the POKEY view, the Pokey menu (P1-P4) | Done 2026-09-30 |
| 27 | [QWERTZ_LAYOUT_PLAN](27_QWERTZ_LAYOUT_PLAN.md) | A QWERTZ (German) keyboard layout for the note keys in both programs, the explorer key row per layout, the OS-derived default (K1-K5) | Done 2026-09-30 |
| 28 | [VALUE_SETS_PLAN](28_VALUE_SETS_PLAN.md) | WUDSN Base value sets for `KeyboardLayout`, `TrackerDriverVersion` and `AssemblerFormat`: texts in `ValueSets.properties`, `ValueSetField` in the dialogs (V1-V5) | Done 2026-10-01 |
