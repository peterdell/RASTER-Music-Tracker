# Plan 50: The Two-Tone filter audible everywhere it is recorded

Status: T1 DONE 2026-10-22 (Java live playback); T2 (C++ live playback)
and T3 (WAV export, both languages) open.

GitHub issue #30: the Two-Tone filter (the envelope editor's $7FE/$7FD
command, SKCTL=$8B) is inaudible in the Java version's live playback,
although the exported .xex plays it. The reporter's example is the
committed test song test-resources/songs/rmt134/makary_two_tone_filter
(extended by a 10-second pause so the xex's VU player does not fade and
loop it inside a comparison window - see its README.md).

## 1. The diagnosis

Vin's two hypotheses on the issue were "not emulated with ASAP" and
"writes to SKCTL are missing during playback". The second is right:

- ASAP's POKEY emulates Two-Tone completely: Pokey.poke case 15 stores
  SKCTL, and the per-tick channel-1/2 timer linking checks
  `skctl & 8` / `(skctl & 136) == 8`.
- The driver does its part: Patch-16's command 7 handles $7FE/$7FD
  (rmtplayr.a65, FEAT_TWO_TONE) and holds $8B/$03 in its SKCTL
  variable, which reaches the shadow at $D20F. That is why every
  export is right: the SAP-R dump records the convention (AUDC1 bit 4
  set while $D20F reads $8B, PokeyStream.record), and the SAP/XEX
  exports' LZSS player turns that bit back into an SKCTL write
  (lzssp.h: CHECKFORTWOTONEBIT/NOTWOTONE/SETTWOTONE).
- But the one bridge between the shadow and the audible POKEY pair -
  CopyAtariMemoryToPokey, in both languages - copied only the nine
  AUDF/AUDC/AUDCTL bytes. SKCTL never reached the pair, so live
  playback ran every Two-Tone song with the filter off. The gap is
  inherited: in this repository's whole C++ history (back through the
  1.31-1.34 VinsCool import, XPokey.cpp's register loop `0-7 + 8audctl`)
  SKCTL was never written to the sound DLL. "Works in daily 1.35" on
  the issue therefore cannot be this codebase; the working reference
  was presumably a build line with a different sound path.

## 2. Steps

- **T1 - Java live playback (DONE 2026-10-22).**
  AudioEngine.copyAtariMemoryToPokey also forwards $D20F (and $D21F in
  stereo) to the POKEY pair; AtariCpu.pokeRegister and the ASAP
  RMT-mode section document offset 15/31 = SKCTL. Tests
  (AudioEngineTest): theTwoToneFilterReachesThePokeyPair - two
  identical sessions play the same pressed tones, one with the filter
  toggled on; SKCTL is the only shadow difference, so differing
  samples prove the forwarding (verified to fail with the forwarding
  removed) - and theMakaryTwoToneSongRaisesSkctlDuringLivePlayback -
  the committed example's $7FE envelope command drives $D20F to $8B
  during real live playback. TestSongs.MAKARY_TWO_TONE_FILTER added.
  720 tests green.

- **T2 - C++ live playback (open).** The same two PutByte calls in
  CXPokey::CopyAtariMemoryToPokey (PokeyRendererCore.cpp): register
  0x0F from $D20F, and 0x1F from $D21F in stereo. Shared-behavior
  work, not C++-only: it is the C++ half of T1. To verify, the sound
  DLLs must accept the offset: apokeysnd.dll is ASAP's generated
  PokeySound (case 15 exists); sa_pokey.dll needs a check that
  Pokey_PutByte(0x0F, ...) reaches its SKCTL - if it does not, the fix
  only helps the apokeysnd path and the limitation is documented.
  Changelog entry required (the Windows program changes audibly).

- **T3 - WAV export, both languages (open).** The WAV exporter replays
  the recorded SAP-R stream, in which Two-Tone is encoded as AUDC1
  bit 4 - straight into AUDC1, which a POKEY reads as volume-only, and
  it never rebuilds SKCTL. A Two-Tone song's WAV is therefore doubly
  wrong (filter off AND channel 1 forced volume-only). Fix like the
  LZSS player: per frame, SKCTL = (AUDC1 & 0x10) ? $8B : $03 into the
  pair, and write AUDC1 with bit 4 cleared. First confirm against the
  LZSS player's actual code whether it clears the bit before storing
  AUDC1 (expected: yes, otherwise its own playback would be
  volume-only too). Java: WaveFileExporter.exportWav; C++:
  CWaveFileExporter::ExportWAV (it routes through
  CopyAtariMemoryToPokey reading the $D200 shadows, so T2's SKCTL copy
  plus writing the translated SKCTL/AUDC1 into the shadows covers it).
  Test: a WAV of the Makary example differs between SKCTL $8B and $03
  frames, and no frame plays channel 1 volume-only unless the module
  says so.

Each step is its own commit; T2 and T3 can land independently of each
other.

## 3. Notes

- The SAP-R AUDC1-bit-4 convention conflates Two-Tone with a genuine
  volume-only on channel 1. The whole pipeline (PokeyStream.record,
  the LZSS player, T3's replay) shares that assumption; nothing new to
  invent, just mirror it.
- The PokeyController "M" toggle (Pokey menu, SKCTL two-tone bits) was
  equally inaudible before T1 - it eors the driver's SKCTL variable,
  which only ever reached the PokeyView display. T1 makes it audible.
