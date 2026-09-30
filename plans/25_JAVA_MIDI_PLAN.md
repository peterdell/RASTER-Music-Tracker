# Plan: MIDI input for the Java port (M0-M5)

Status: DONE 2026-09-30 (M0 and the C++ fix on 2026-09-29, M1-M5 on 2026-09-30 - "implement the midi plan"). The CH16/CH10 controller mode (M3) ported faithfully, with one more C++ fix: the knobs 71-78 worked in every mode (their `case`s sat inside an `if` a switch jump never evaluates); now `MIDI_CH15_MODE` only, in both programs.

## 1. What the C++ program does

MIDI in RMT is **input only** (a MIDI OUT was planned and never built,
`RmtMidi.h` keeps the commented-out members). Three pieces:

### 1.1 `CRmtMidi` (`RmtMidi.h/.cpp`, the global `g_Midi`) - the device

- Settings persisted in `rmt.ini`: `MIDI_IN` (device *name*), `MIDI_TR`
  (touch response), `MIDI_VOLUMEOFFSET` (0-15), `MIDI_NOTEOFF` (record note
  off). Read in `ReadRMTConfig`, written in `WriteRMTConfig`, defaults in
  `ResetRMTConfig` (`""`, 0, 0, 0 - note the constructor default of
  `m_VolumeOffset = 1` is overwritten by every config path).
- `MidiInit()`: turns MIDI off, looks the configured name up among
  `midiInGetDevCaps()` names, stores the index; **not found -> warning box
  "MIDI IN error / Can't init the MIDI IN device <name>" and the name is
  cleared**; if it was on before, it is turned on again. Called at start-up
  (`OnInitialUpdate`: `MidiInit(); MidiOn();`), after the Options dialog,
  in `ResetRMTConfig`.
- `MidiOn()`: resets the 16 per-channel arrays (`m_LastNoteOnChannel = -1`,
  `m_NoteVolumeOnChannel = 0`, `m_InstrumentOnChannel = 0`), opens the
  device with the callback `MidiInProc`, starts it. Failure: error box
  "MidiInOpen error / Can't open selected MIDI IN device.".
- `MidiOff()`: stop/reset/close. `MidiRestart()` = off + on (unused).
- `MidiInProc` forwards `MIM_DATA` and `MIM_ERROR` messages to
  `g_Song.MidiEvent(dwParam1)` - status byte, data1, data2 in the low three
  bytes. Runs on the multimedia callback thread, no locking (C++ relies on
  the message being cheap; the Java port has a lock, see 3.3).
- Toolbar button `ID_MIDIONOFF` ("Toggle MIDI on/off"): enabled when a
  device is configured (`GetMidiDevId() >= 0`), checked when on, toggles
  `MidiOn()`/`MidiOff()`. No menu item, no key.
- Options dialog: combo "MIDI IN Device" = "None" + all device names,
  "Touch response" (enables "Atari Volume Offset" 0-15), "Record note off".
  On OK the chosen index is turned back into a name and `MidiInit()` runs.

### 1.2 `CSong::MidiEvent(DWORD)` (`Midi_Song.cpp`, 637 lines) - the handler

Decodes `cmd = status & 0xF0`, `chn = status & 0x0F`, `pr1`, `pr2`. Then,
in this order:

1. **Note off (0x80) on any channel but 15 and 9** becomes note on with
   velocity 0. **System messages (0xF0)**: only 0xFF (system reset)
   does anything - `g_AtariTrackerDriver->Init()` and the per-channel
   arrays of channels 1-15 reset; everything else in 0xF0 returns.
2. **Channels 2-9 (`chn` 1-8) - multitimbral live play**, no recording,
   works without window focus: Atari track `atc = (chn-1) % g_tracks4_8`;
   note on -> `note = pr1 - 36`, `vol = pr2 / 8`, instrument
   `m_InstrumentOnChannel[chn]`, `SetPlayPressedTonesTNIV(atc, note, ins,
   vol)`; a velocity-0 note on only counts when it is the last note of that
   track. Program change (0xC0) sets the channel's instrument. Control
   change 123 (all notes off) plays note -1 volume 0; 121 (reset all
   controls) is the system reset. (The `chn == 9` "drums" branch inside
   this block is unreachable - `chn < 9` - dead code, leave it.)
3. **Focus gate**: `if (!g_RmtHasFocus && !IsProveMode()) return;` -
   everything below needs the main view focused (`CRmtView::OnSetFocus`/
   `OnKillFocus`) or a jam mode, "to avoid overwriting patterns
   accidentally".
4. **Channels 16 and 10 (`chn` 15 and 9) - VinsCool's controller**
   (comments call it a temporary workaround / test code):
   - CH16 control changes: 1 mod wheel -> `m_mod_wheel = (pr2-64)/8`
     (a note offset), 7 volume slider -> `m_vol_slider`/`m_volume` (1-15),
     115 loop -> `Play(PLAY_TRACK)`, 116 stop, 117 play song, 118 rec ->
     `SwitchEditMode(MIDI_CH15_MODE)`, 123 -> stop + system reset; in
     `MIDI_CH15_MODE` knobs 71-78 write the driver's AUDF/AUDC shadow
     registers directly (`memory[0x3178+o]`, `memory[0x3180+o]`,
     `o = m_ch_offset ? 2 : 0`).
   - CH10 note on in `MIDI_CH15_MODE`: drumpads 60/62/66/70/74/69/75/73
     toggle AUDCTL/SKCTL bits (`memory[0x3C69]`, `memory[0x3CD3]`), pad 74
     flips `m_ch_offset`. Other CH10 events are dropped.
   - CH16 notes outside `MIDI_CH15_MODE`: the recording code of channel 1
     (below) copied with `atc = m_heldkeys % g_tracks4_8` and the mod-wheel
     offset, tracking `m_heldkeys`.
   - CH16 notes in `MIDI_CH15_MODE`: the "TESTING HARDCODED DATA" block -
     direct AUDF/AUDC writes from the note tables in driver memory
     (`0xB000`-`0xB4C0` pages) by distortion (`m_midi_distortion` from
     program change) and AUDCTL clock bits, polyphonic over 4 tracks via
     `m_LastNoteOnChannel[0..3]`.
5. **Channel 1 (`chn` 0) - the de facto note recording** ("legacy for
   compatibility"): note on, `note = pr1 - 36` (from the 3rd octave);
   velocity 0 is a note off only with `m_NoteOff`; volume = `m_VolumeOffset
   + pr2/8` clamped 1-15 with touch response (and it sets `m_volume`), else
   the current `m_volume`. Not in the TRACKS part, or in a prove mode, or
   with Shift/Ctrl held: play only (`Prove_midi`). Otherwise: during
   follow-play in the first half of a line the note is *quantized*
   (`m_quantization_note/instr/vol`, entered by `PlayVBI` on the next
   line); else `TrackSetNoteInstrVol` + `BLOCKDESELECT` + (with
   `g_respectvolume`) the line's volume, then `TrackDown(
   g_SkipLinesAfterNoteInsert)` unless following, and the note is played
   (`SetPlayPressedTonesTNIV`, stereo twin in `JAM_STEREO_MODE`/Ctrl).
   Note off of the last note on the channel deletes the note and writes
   volume 0 (`TrackSetNoteActualInstrVol(-1)`, `TrackSetVol(0)`) or
   quantizes as `-2`. **Program change on any channel other than 1-8 ->
   `ActiveInstrSet(pr1)`** (the `else if (cmd == 0xc0)` after the `chn ==
   0` block; for `chn` 0 itself program change never arrives there - the
   `if (chn == 0)` swallows it. Faithful port keeps that).

`PlayVBI`'s quantization (`SongEditing.cpp:3270-3295`): with
`m_speeda == m_speed && m_followplay`, a pending note is entered via
`TrackSetNoteInstrVol` (respecting `g_respectvolume`) and played; `-2`
deletes the note and writes volume 0; then reset to -1.

### 1.3 State on `CSong` (Song.h:394-399)

`m_mod_wheel`, `m_vol_slider`, `m_heldkeys`, `m_midi_distortion`,
`m_ch_offset` - "MIDI input variables, used for tests through MIDI CH15".
Plus `EditMode::MIDI_CH15_MODE`, shown as "EXPLORER MODE (MIDI CH15)".

### 1.4 Provable C++ bugs met on the way

- `Midi_Song.cpp:373` and `:609`: `if (m_play && m_followplay && (m_speed <
  (m_speed / 2)))` compares `m_speed` with itself - always false, so a MIDI
  note off during follow-play is never quantized and is entered
  immediately instead. Obviously meant `m_speeda` (as the note-on branch
  five lines above). Fix in both languages (the standing rule), noted in
  the change history.
- `Midi_Song.cpp:47-52`: the drums branch is unreachable (see above). Dead
  code, not a behaviour bug - port as a comment, not as code.

## 2. What the Java port already has

- `RmtOptions.midiDevice/midiTouchResponse/midiVolumeOffset/midiNoteOff`,
  read/written by `RmtConfig` (`MIDI_IN` etc., same ini text, verified by
  `RmtConfigTest`), reset by `RmtOptions.reset()`.
- `OptionsDialog`: the MIDI group with the device combo filled from
  `javax.sound.midi.MidiSystem.getMidiDeviceInfo()` (transmitting devices
  that are neither a `Sequencer` nor a `Synthesizer`), touch response
  enabling the offset field, validation 0-15. `RmtCommands.applyOptions`
  copies the four values; `MidiInit()` has no counterpart yet.
- Toolbar button `RmtCommandId.MIDIONOFF` exists (in `doc/rmt_action_infos.md`
  already), `isEnabled` returns false, `execute` does nothing.
- `EditMode.MIDI_CH15_MODE` with `isSpecialProveMode()`, drawn by `SongUI`;
  `UiState.switchEditMode` drops a special mode back to edit mode.
- `Song.setQuantization(note, instr, vol)` is set by the keyboard's
  `SongInput.insertNote`, **but `Song.playVBI` omits the consuming branch**
  (its javadoc says no caller sets it - outdated since the UI port). That
  is a live bug today: a note typed during follow-play in the first half
  of a line is dropped in Java. MIDI recording depends on the same branch
  (and on `-2` for note off), so it is the first step (M0).
- The threading model: `RmtSession.lock` (reentrant) held by the audio
  thread per frame and by the EDT per event/command/paint; released around
  modal dialogs. `AudioEngine` never touches Swing. `SwingMessages` shows
  boxes through the session.
- The driver runs in the emulated 6502 with the same memory image, so the
  C++ direct writes to `0x3178`/`0x3180`/`0x3C69`/`0x3CD3` mean the same in
  `session.atari.getMemory()` (the audio engine copies `$D200`-`$D208` from
  memory each frame, and the driver's `SetPokey` fills those from the
  shadow registers exactly as on the C++ side).

## 3. Design

### 3.1 Java Sound, no native code

`javax.sound.midi` is part of `java.desktop`, already in the jpackage
images (`--add-modules ALL-MODULE-PATH`); Windows (MME), Linux (ALSA) and
macOS (CoreMIDI) are covered by the JDK. Device = `MidiDevice` from
`MidiSystem.getMidiDevice(info)`; `device.open()`, `device.getTransmitter()
.setReceiver(receiver)`; `receiver.send(MidiMessage, timeStamp)` is called
on a Java Sound thread. `ShortMessage.getStatus()` gives the status byte;
`getCommand()`/`getChannel()`/`getData1()`/`getData2()` the rest; the
system reset is `ShortMessage.SYSTEM_RESET` (0xFF), sysex a
`SysexMessage` (ignored, as in C++).

Device names: on Windows Java Sound reports the MME product names, so the
`MIDI_IN` value stays interchangeable with `Rmt.exe`'s `rmt.ini` in the
common case; a mismatch only produces the same "Can't init the MIDI IN
device" warning C++ shows for an unplugged device. M1 verifies with the
user's device.

### 3.2 Classes (all in `org.atari.raster.rmt.ui`, next to `AudioEngine`)

- **`RmtMidi`** - the port of `CRmtMidi`: `midiInit()`, `midiOn()`,
  `midiOff()`, `isOn()`, `getDeviceName()`, `hasDevice()` (= `GetMidiDevId()
  >= 0`), the three `int[16]` channel arrays (package-visible, used by the
  handler as in C++). The settings themselves stay in `RmtOptions` (the
  Java home of the persisted `CRmtMidi` members) - `RmtMidi` reads them
  from the session. Device enumeration behind a small interface
  (`MidiDevices`: `names()`, `open(name, listener)`) with the Java Sound
  implementation and a test fake, so the lifecycle and the handler are
  testable headless (the pattern `AudioEngine` uses for the missing
  mixer). Messages through `session.messages` (warning/error boxes as in
  C++). `OptionsDialog.listMidiInputDevices()` moves onto the same
  interface so the dialog and `midiInit()` see one list.
- **`MidiInput`** - the port of `CSong::MidiEvent`, as a class beside
  `SongInput` (the handler is UI-level code: it needs `UiState` - edit mode,
  active part, respectVolume, Shift/Ctrl state -, `session.undo`,
  `skipLinesAfterNoteInsert`, the toolbar/skip options, and reuses
  `SongInput`'s helpers `blockDeselect`, `trackDown`, `isPlayingAndFollowing`).
  One entry point `midiEvent(int status, int data1, int data2, boolean
  hasFocus)` - the C++ globals `g_RmtHasFocus`, `g_shiftkey`,
  `g_controlkey`, `g_tracks4_8`, `g_respectvolume`, `g_SkipLinesAfterNoteInsert`
  become the session/UI state the class already has or explicit
  parameters ("C++ global -> explicit Java parameter"). The `CSong`
  members `m_mod_wheel`, `m_vol_slider`, `m_heldkeys`, `m_midi_distortion`,
  `m_ch_offset` live in `MidiInput` (only MIDI uses them; the Pokey
  Explorer in C++ has its own set).
- **Wiring**: `RmtSession` owns the `RmtMidi` (as it owns the audio engine
  and messages); `RmtMainWindow.show()` calls `midiInit(); midiOn();` after
  the display timer starts (C++ `OnInitialUpdate`), the window close calls
  `midiOff()` (C++ destructor). `RmtConfig.resetRMTConfig` and
  `RmtCommands.applyOptions` call `midiInit()` where C++ does (the
  "no counterpart" javadoc comments in `RmtCommands`, `RmtConfig`,
  `RmtOptions`, `OptionsDialog` go). `RmtCommands`: `MIDIONOFF` executes
  the toggle, `isEnabled` = `hasDevice()`, `isChecked` = `isOn()`.
- **Focus**: `g_RmtHasFocus` becomes a `volatile boolean` on
  `RmtMainWindow`/`TrackerPanel` set by a `FocusListener` on the tracker
  panel (the C++ view's `OnSetFocus`/`OnKillFocus`; the panel is the
  keyboard focus owner already), read by the MIDI thread.

### 3.3 Threading

The receiver runs `session.locked(() -> midiInput.midiEvent(...))` - the
lock the EDT and the audio thread already share, so a MIDI note is entered
atomically between frames, exactly like a key press. No Swing calls from
the MIDI thread: the display timer repaints the canvas (if it repaints
only on a dirty flag, the handler sets it through the same path key input
uses). Message boxes from the MIDI thread (`midiOn()` failure is reached
from the EDT anyway; `midiInit()` too) - only the handler itself runs on
the MIDI thread and it shows none. `midiOff()` from the EDT while the
receiver holds the lock: `device.close()` after `lock`-guarded shutdown,
no deadlock because the receiver never blocks on Swing.

### 3.4 Script command for tests and the cross-program check (both programs)

A script command `midi <status> <data1> <data2>` (hex bytes, e.g. `midi 90
3C 64`) feeding the handler with `hasFocus = true` (a hidden script window
never has focus; the C++ `g_RmtHasFocus` gate is bypassed by the explicit
parameter - `CSong::MidiEvent(DWORD dwParam, bool hasFocus)` with the
callback passing `g_RmtHasFocus`). Then `test-resources/scripts/
midi.rmtscript` records a few notes on channel 1 (with and without touch
response, note off on/off, a program change, all-notes-off, a channel-2
live note that must *not* record) and saves `.rmw` and `.rmt`;
`compare_exports.ps1` and `CrossProgramExportTest` prove both handlers
identical byte for byte. This is the same approach as the export
comparison and the only way to test the C++ handler without a MIDI cable.

## 4. Batches

### M0 - quantization in `Song.playVBI` (prerequisite, bug fix) - DONE 2026-09-29

Port the omitted branch: a pending note (`quantizationNote` 0..NOTESNUM-1,
`quantizationInstr` valid) -> `trackSetNoteInstrVol(note, instr, vol,
respectVolume, undo)` with `vol` replaced by the line's volume when
`respectVolume`, then `setPlayPressedTonesTNIV(activeColumn, ...)`; `-2` ->
`trackSetNoteActualInstrVol(-1, ...)` + `trackSetVol(0, ...)`; reset to -1.
`respectVolume` and `undo` come in as parameters from `AudioEngine`
(explicit-parameter rule; the engine has the session). Update the javadoc.
Tests: `SongTest`/`AudioEngineTest` - a quantized note lands on the next
line during follow-play; `-2` clears it; nothing happens without
follow-play. Change history entry (Java only: "a note typed during
follow-play in the first half of a line was lost").

### M1 - `RmtMidi`: device lifecycle, options, toolbar

`MidiDevices` interface + Java Sound implementation + test fake; `RmtMidi`
with the C++ semantics of 1.1 (including "not found -> warning, name
cleared", "on before init -> on after", channel arrays reset on `midiOn()`);
session/window/options/config wiring of 3.2; `MIDIONOFF` live. Tests
(`RmtMidiTest`, headless with the fake): init with no name -> no device,
button disabled; init with a known name -> device found, `midiOn()` opens
it, button enabled and checked, toggle closes; unknown name -> the warning
through the recorded `Messages`, name cleared and written back to the ini
on the next `writeRMTConfig`; `applyOptions` with a changed device
re-inits. Manual check with the user's controller (Windows first, the
device name as `Rmt.exe` writes it).

### M2 - `MidiInput`: channel 1 recording, channels 2-9 live play

The handler of 1.2 items 1, 2, 3 and 5 with the script command of 3.4 in
Java. Tests (`MidiInputTest`, headless `RmtSession`, events pushed
directly): note on records note/instrument/volume at the cursor and moves
down `skipLinesAfterNoteInsert` lines; velocity 0 ignored without
`midiNoteOff`, deletes the last note with it; touch response volume `offset
+ velocity/8` clamped 1-15 and `song.volume` updated; no touch response ->
current volume; outside TRACKS / in jam mode / Shift held -> played, not
recorded; without focus -> ignored; during follow-play first half ->
quantized (M0 consumes it); program change -> active instrument; channel 2
note -> `setPlayPressedTonesTNIV` on track 0 with the channel's instrument,
nothing recorded, works without focus; control 123 -> note -1 volume 0;
system reset -> driver init + arrays reset. The `m_speed < m_speed/2` fix
applied (Java uses `speeda`).

### M3 - the CH16/CH10 controller mode

Item 4 of 1.2, faithfully, including the `MIDI_CH15_MODE` knobs, drumpads
and the "direct notes" block with its note-table reads from driver memory
(`0xB000`-`0xB4C0` pages - confirm the addresses against
`tracker_obx.h`/the Java driver binaries, they are the same driver). Tests:
control 118 switches to `MIDI_CH15_MODE`, 116 stops, 117 plays; knob 71
sets the upper nibble of `memory[0x3178]`; drumpad 60 toggles bit 2 of
`memory[0x3C69]`; a CH16 note in edit mode records like channel 1 with the
mod-wheel offset; a CH16 note in `MIDI_CH15_MODE` writes AUDF/AUDC of the
first free track. Recommendation: port it - `MIDI_CH15_MODE` is already
in the Java enum and unreachable without it, and the mode is what the
C++ author uses; but it stays documented as experimental, as the C++
comments say.

### M4 - C++ side: `midi` script command, `hasFocus` parameter (the fix of 1.4 is done)

`CScriptRunner` gets `midi` (3.4); `CSong::MidiEvent(DWORD, bool hasFocus)`
(`MidiInProc` passes `g_RmtHasFocus`); the two `m_speed < m_speed/2`
lines become `m_speeda`; `test-resources/scripts/midi.rmtscript` +
`compare_exports.ps1`/`CrossProgramExportTest` extended; the daily build
runs it through `:compare_exports` already. `doc/rmt_scripting.md`
documents the command. C++ unit test of `MidiEvent` through the stubbed
`CSong` if `SongEditingTests` can reach it (it calls `g_AtariTrackerDriver`
only on system reset - guard or stub).

### M5 - documentation and bookkeeping

- `doc/rmt_en.md`: a new section "MIDI Input" (after "PROVE (JAM) MODE"):
  the options, the toolbar toggle, channel 1 recording rules (3rd octave
  base, touch response formula, note off, focus rule, quantization),
  channels 2-9 multitimbral play with program change per channel, the
  CH16/CH10 controller mapping table (marked experimental). The manual has
  no MIDI text at all today (neither has the 1.28 manual).
- `doc/rmt_changes.md`: 1.36 entries (Java: MIDI input; C++: the note-off
  quantization fix, the `midi` script command); the 1.37 planned line "MIDI
  input and the Pokey Explorer in Java port" loses the MIDI half.
- `README.md` "Not ported: printing, MIDI input and the Pokey Explorer" ->
  printing and the Pokey Explorer. `plans/README.md` status, `NOTES.md`.

## 5. Decisions for the user

1. **M3 scope**: port the CH16/CH10 controller mode faithfully (recommended,
   see M3) or leave it out and keep `MIDI_CH15_MODE` unreachable in Java.
2. **The C++ fix** of the note-off quantization comparison (1.4): apply in
   both (recommended, the standing rule) or Java only.
3. **The `midi` script command in C++** (M4): needed for the cross-program
   comparison; without it the Java handler is tested against the C++ code
   by reading, not by running.

## 6. Verification

Per batch: `mvn -o clean package` (all tests), for M4 the Release build of
`Rmt.sln` and `RmtTests.exe`, `build/compare_exports.ps1` with the new
script (every file identical), `dump actions` unchanged (the button exists
already). Manual: a real controller on Windows against both programs -
same notes land in the same cells with the same volumes; the toolbar
toggle; unplugging the device -> the C++ warning text in both.

## 7. Estimate

M0 small (one method + tests). M1 medium. M2 medium-large (the handler is
the bulk; the tests carry the C++ semantics). M3 medium (mechanical, many
constants). M4 small-medium. M5 small. Order: M0, M1, M2, M4 (so the
comparison exists before M3), M3, M5.
