# Java port, Phase B batch B8: real-time audio

Status: **B8a DONE 2026-09-27** (section 4 has the findings); B8b/B8c
open. Plan reviewed and its four decisions accepted by the user on
2026-09-27. Companion to `plans/JAVA_UI_PORT_PLAN.md` (DECISION 4 there:
"UI first, real-time audio deferred to B8"). Batches B1-B7 are committed;
every menu command is wired except printing, "Open ASAP file", local help,
MIDI and the Pokey explorer.

## 1. What the C++ program does per frame

`CSongTimer` (a Windows multimedia timer, 20 ms PAL / 17-17-16 ms NTSC
"groove") calls `CSong::TimerRoutine()` on its own thread:

1. `PlayVBI()` - advance the play position (ported: `Song.playVBI`); its
   `PlayBeat()` hands every new note to the 6502 driver:
   `g_AtariTrackerDriver->SetTrackNoteInstrumentVolume(t, n, i, v)` etc.
2. `PlayPressedTones()` - the keyboard-preview notes, same driver calls
   (ported: `Song.playPressedTones`).
3. `g_Pokey.RenderSound1_50(m_instrumentSpeed)` - for each instrument-speed
   sub-frame: `g_AtariTrackerDriver->Play()` (JSR `RMT_P3` = one tick of
   the RMT player routine, then JSR `RMT_SETPOKEY` = the routine stores
   AUDF/AUDC/AUDCTL into **plain RAM** at $D200-$D208 / $D210-$D218),
   `CopyAtariMemoryToPokey()` (those bytes, each channel masked to 0 when
   `g_ChannelControl` has it off, into the POKEY emulator), and one slice
   of samples rendered into the DirectSound ring buffer (3 chunks = ~60 ms
   latency, with the "render more/less than a chunk" catch-up logic).
4. `g_playtime++` while playing.

The 6502 is `sa_c6502.dll` (`C6502::JSR(adr, a, x, y, cycles)` on the 64K
array `CAtari::m_memory`); the POKEY is `sa_pokey.dll`/Altirra. The RMT
tracker driver binary (`rmt_driver_v<n>.obx`, loaded at start-up and on a
driver-version change) has this memory map (`tracker_obx.h`/`Atari.h`):

| Address | Meaning |
|---|---|
| $3400 / +3 / +6 / +9 / +12 | `RMT_INIT` (A=0, X=0, Y=$3F), `RMT_PLAY`, `RMT_P3`, `RMT_SILENCE`, `RMT_SETPOKEY` |
| $3D00 / $3E00 / $3E80 | `RMT_ATA_SETNOTEINSTR` (A=note, X=track, Y=instr), `RMT_ATA_SETVOLUME` (A=vol, X=track), `RMT_ATA_INSTROFF` (X=track) |
| $4000 + instr * 256 | the instrument data `CInstruments::Update(instr)` writes with `InstrToAta` (B8a: `Instruments.update()` does the same once `attachAtari` was called) |
| $B000 | `RMT_FRQTABLES` (written by `Atari.init`; **the driver binaries carry their own tables there too** - see the B8a findings in section 4) |
| $D200-$D208, $D210-$D218 | the POKEY register shadow the UI's analyzer/POKEY view read (plain RAM to the driver) |

Live playback therefore never uses an exported module: the Java model
already produces every driver call; before B8a the JSRs were no-ops
(`AtariTrackerDriver`'s javadoc explained why).

## 2. What Java has

- `net.sf.asap` vendored as source (`src/java/net/sf/asap`, ASAP 8.0.0),
  already carrying two documented RMT extensions in `ASAP.java`
  (`stepFrame`, `getPokeyRegisterShadow`). `Cpu6502` (package-private
  fields `memory[65536]`, `cycle`, `pc`, `a/x/y`, `doFrame(cycleLimit)`)
  routes $D000-$D7FF accesses to `ASAP.peekHardware/pokeHardware`;
  `PokeyPair` (`initialize(ntsc, stereo, sampleRate)`, `poke(addr, data,
  cycle)`, `startFrame()`, `endFrame(cycle)`, `generate(buffer, offset,
  blocks, format)`) is a complete dual-POKEY renderer in U8 or S16.
- `AsapEmulator` + `PokeyStream`: the export path (`dumpSongToPokeyStream`)
  played a whole exported module inside ASAP and recorded registers per
  frame. *Was* planned to stay unchanged by B8; B8a replaced it with C++'s
  own driver path once the cross-check exposed the difference (section 4).
- `Song.playVBI/playBeat/playPressedTones/play/stop`, `ChannelControl`,
  `UiState.playTime`, the TIME/BPM display - all in place; nothing calls
  `playVBI` in the UI yet (play commands only set the play mode).

## 3. Design

### 3.1 Emulation core (model, package `net.sf.asap` + `com.wudsn.tools.rmt.model`)

An "RMT mode" section appended to `ASAP.java` (the same way the two
existing extensions are, keeping the upstream code untouched):

- `rmtInitialize(boolean ntsc, boolean stereo, int sampleRate)`: resets the
  CPU, makes **$D000-$D7FF plain RAM** (a flag consulted by
  `peekHardware/pokeHardware`; the driver's POKEY stores must land in
  memory as in C++, the chip is fed separately), points `nextEventCycle`
  past any frame so no ASAP player call ever happens, initializes
  `PokeyPair`.
- `int rmtJsr(int addr, int a, int x, int y, int cycleLimit)`: A/X/Y in, a
  `JSR addr` + halt opcode planted where ASAP's own `call6502()` plants it,
  `doFrame(cycleLimit)`, A/X/Y out (packed); `cycleLimit` = the frame's
  cycle count (114*312 PAL, 114*262 NTSC, as `CAtari::GetFrameCycleCount`).
- `byte[] rmtMemory()`: the CPU's 64K, which becomes `Atari`'s memory
  (`Atari` gets a constructor taking the array, so `Atari.getMemory()`,
  `setByteAt`, `init`'s table write, `Instruments`' frequency reads and the
  UI's shadow reads all keep working on the one array).
- `rmtPokeRegister(int offset, int data)` / `rmtRender(int cycles, byte[]
  buffer, int offset)`: `CopyAtariMemoryToPokey`'s writes at cycle 0 of a
  sub-frame and the slice rendering (`startFrame`, `endFrame(cycles)`,
  `generate`), 16-bit little-endian, 2 channels (a mono song feeds both
  channels the base POKEY, as C++'s 2-channel output does).

`Atari` gains `jsr(addr, a, x, y)` (returns the registers) and
`AtariTrackerDriver`'s methods become the real JSR sequences of
`AtariTrackerDriver.cpp`/`Core.cpp` (`init` returns the routine's A;
`play(boolean specialProveMode)` skips `RMT_P3` in that mode as C++ does).
`Instruments.update(instr)` additionally writes `instrToAta` to
`$4000 + instr * 256` when an Atari is attached
(`Instruments.attachAtari(Atari, BooleanSupplier stereo)`, set by
`RmtSession`); every loader already calls `update` per instrument (B2), so
a loaded song's instruments reach the driver without further changes.

### 3.2 Audio engine (UI package: `AudioEngine`)

One daemon thread, paced by the sound card through a blocking
`SourceDataLine.write` (buffer = 3 frames, C++'s latency), per frame:

1. take the session lock;
2. `song.playVBI(tracks4_8, driver)`, `song.playPressedTones(driver)`;
3. for `instrumentSpeed` sub-frames: `driver.play(prove)`, copy the masked
   $D200-$D208 (+ $D210-$D218 for stereo) into the POKEY, render
   `frameSamples / instrumentSpeed` samples;
4. `uiState.playTime++` if playing; release the lock; write the frame's
   samples to the line.

Frame length = exact cycles per frame, so PAL/NTSC timing is the sound
card's clock (no "17-17-16 groove" needed); the visible play position and
TIME/BPM counter follow. NTSC/stereo changes (`setNTSC`, the mono/stereo
switch, a load) are detected at the top of the frame and re-initialize the
POKEY pair and the tuning tables (`ReInitSound()` + `g_Atari.Init()`).
Without an audio device the thread still advances the model on a 20/16.7 ms
sleep so playback stays visible (headless-safe; that is also how the
engine is tested).

### 3.3 Thread safety

C++ mutates the model from the timer thread and the UI thread
simultaneously, guarded only by `busyInCallback` spins. The port uses one
explicit lock on the session, held by the engine for step 1-4 above (well
under a millisecond) and by the EDT for a key/mouse event, a command, a
paint, and a dialog's model calls. Rules: the engine never touches Swing;
the EDT never waits for the engine except through that lock; `Song.stop`'s
`WaitForTimerRoutineProcessed` becomes "the silence is applied by the
next frame" (the engine keeps running while stopped, exactly like C++'s
timer, so pressed-tone preview works with playback stopped).

### 3.4 What else B8 covers

- `RmtCommands`: Play/Stop already set the state; Esc's "reset the Atari
  sound routines" option -> `driver.init()`; the media keys
  (`VK_MEDIA_PLAY_PAUSE/NEXT_TRACK/PREV_TRACK`, RmtView.cpp) in
  `SongInput`; the Effects dialog's Play/Stop and the block-play command
  become audible without changes.
- `noHwSoundBuffer`: no Java equivalent (documented, kept in `rmt.ini`).
- "Open ASAP file" (Tools): `ID_TOOLS_OPEN_ASAP_FILE` has **no handler
  anywhere in the C++ sources** (only its resource ID), so MFC shows the
  item disabled. The Java item should be disabled too (B8c, one line in
  `RmtCommands.isEnabled`) instead of reporting "not available".
- `WaveFileExporter` (B8c): C++'s `ExportWAV` replays the recorded
  `PokeyStream` through the POKEY renderer; the port plays the exported
  module through ASAP's player instead, which B8a showed is *not*
  observably identical (the player's classic frequency tables vs the
  tracker driver's, see section 4). Replay the stream through
  `AtariCpu.pokeRegister`/`render` as C++ does; ASAP's module player then
  serves only the `LivePlaybackTest` reference.

## 4. Sub-batches

- **B8a - emulation core. DONE 2026-09-27.** ASAP RMT mode, `AtariCpu`,
  `Atari(AtariCpu)`/`Atari.jsr`, the real `AtariTrackerDriver` JSR
  sequences, `Instruments.update`/`attachAtari` memory writes, `RmtSession`
  on a CPU-backed Atari. Tests: `AtariCpuTest` (a five-instruction program
  through `jsr`, the cycle limit, the PATCH16 driver playing a note and the
  POKEY rendering it), `LivePlaybackTest` (Delta.rmt), a PLAY_FROM test in
  `SongEditingTest`. Findings:
  - `RMT_INIT` returns A=1 for every driver version (decision 4: asserted);
    it also writes SKCTL=3 to $D20F/$D21F itself, so `RmtSession`'s manual
    pokes went away.
  - ASAP's `PokeyPair.initialize` leaves every channel muted with
    `MUTE_SONG_INIT` until a SAP INIT routine has run; `rmtInitialize`
    clears it (`endSongInit`) - the first render was silent without that.
  - **Cross-check result**: the dump of Delta.rmt through the tracker
    driver matches ASAP's independent emulation of the exported module
    **frame for frame over all 3840 frames** with the UNPATCHED driver.
    With the default PATCH16 driver every AUDC/AUDCTL byte still matches
    but AUDF bytes differ by 1-2: the patched drivers carry their own
    frequency tables at $B000 (1211 non-zero bytes in the PATCH16 binary,
    366 differing from the generated tuning tables, none for the notes
    Delta.rmt plays), and ASAP's player uses the classic ones. C++ startup
    loads the driver *after* `g_Atari.Init()` (so the binary's tables win
    until the next `g_Atari.Init()`, which every song load does).
  - Consequence: the port's SAP-R dump (module through ASAP's player) was
    **not** observably identical to C++'s (the live driver) - a deviation
    from the port's rule that had been undetectable. `dumpSongToPokeyStream`
    now runs `atariTrackerDriver.play()` and `PokeyStream.record()` reads
    the $D200/$D210 shadow through the driver, exactly as
    `Song_DumpSong.cpp`; `AsapEmulator` is deleted (ASAP's module player
    remains for the WAV export and the test reference).
  - A Java-only bug fixed on the way: the dump set the *play* lines before
    `play()`, which PLAY_FROM overwrites from the *active* lines (C++ sets
    those) - every XEX subsong was dumped from the cursor line.
    `dumpSongToPokeyStreamPlayFromStartsAtTheGivenSongline` guards it.
- **B8b - audio engine.** `AudioEngine`, the session lock in
  `TrackerPanel`/`RmtCommands`/dialogs, start/stop with the window,
  NTSC/stereo re-init, `playTime`. Tests: the engine stepped manually
  (no line) advances `playVBI` and the play position; lock discipline by
  inspection. Live check: Delta.rmt audible, TIME/BPM running, keyboard
  preview in jam mode, channel mute, block play from the Effects dialog.
- **B8c - the rest of 3.4** and the NOTES/plan entries.

## 5. Decisions requested

1. **Modify the vendored `ASAP.java` further** (an "RMT mode" section next
   to the existing two extensions) rather than copying `Cpu6502`/
   `PokeyPair` into the RMT package. *Recommended: modify* - the same
   precedent, no duplicated emulator code, and `lib/java/README.md` already
   documents the deviation from upstream.
2. **Session lock** as in 3.3 rather than running the frame update on the
   EDT. *Recommended: the lock* - the EDT is blocked by every modal dialog,
   and C++ audio runs through dialogs (the Effects dialog depends on it).
3. **Output format** 16-bit / 44.1 kHz / 2 channels always (C++: 8-bit,
   2 channels). *Recommended: 16-bit* - the Java line API prefers it and
   the POKEY renderer produces it natively.
4. `driver.init()`'s C++ test asserts a return of 0 because the test build
   stubs the JSR; with a real CPU the routine's A may differ. *Recommended:
   assert the real value once known and note it in NOTES.md.* **Resolved in
   B8a: A=1 from every driver binary; 0 stays the memory-only answer.**

## 6. Risks

- The RMT routine may rely on zero-page or page-6 state the C++ `C6502_
  Initialise` sets up; if `RMT_INIT` misbehaves under ASAP's CPU, the
  cross-check in B8a will show it first.
- Timing: `SourceDataLine` latency on Windows (DirectSound/WASAPI mixer)
  can exceed C++'s 60 ms; the buffer size is the tuning knob.
- The lock adds a contention point between painting (60 fps) and the
  engine (50 fps); both critical sections are short, but the FPS counter
  is the place to watch.
