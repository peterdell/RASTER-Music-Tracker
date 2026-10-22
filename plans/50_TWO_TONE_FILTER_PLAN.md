# Plan 50: The Two-Tone filter audible everywhere it is recorded

Status: DONE 2026-10-22. T1 (Java live playback), T2 (C++ live
playback was never broken - verified against the AltirraRMT plugin
sources and documented, no code change) and T3 (Java WAV export,
rescoped from "both languages" by T2's findings).

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
- The Java port's renderer is the port's ONLY path from the shadow to
  the audible POKEY pair, and it copied just the nine
  AUDF/AUDC/AUDCTL bytes - SKCTL never reached the pair, so live
  playback ran every Two-Tone song with the filter off.
- C++ was never broken, although its CopyAtariMemoryToPokey has the
  same nine-byte loop (so T1's first write-up wrongly blamed it too,
  corrected by T2). The shipped sa_pokey.dll/sa_c6502.dll are Avery
  Lee's AltirraRMT plugin pair (Altirra src/AltirraRMTPOKEY and
  src/AltirraRMTC6502; local checkouts in
  C:\jac\system\Windows\Programming\Repositories\Tools\Altirra-440 and
  ...\AltirraSDL, sources identical): on load they link to each other
  (AltirraRMT_LinkFromCPU/LinkFromPOKEY), the CPU maps the whole $D2xx
  page (SetHandlers(0xD2, ...)) and hands every write, cycle-stamped,
  to the full Altirra POKEY - SKCTL and the Two-Tone filter included.
  "Works in daily 1.35" on the issue is exactly this mechanism. In
  linked mode Pokey_PutByte - all CopyAtariMemoryToPokey does - never
  touches the POKEY: it fills the plugin's channel-mute shadow, of
  which only the AUDC volume nibbles are read (zero there mutes the
  channel with hysteresis). An SKCTL PutByte would be a byte nothing
  reads, which is why T2 landed as documentation, not code.

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

- **T2 - C++ live playback (DONE 2026-10-22, no code change).**
  Planned as the C++ twin of T1; investigation showed it is not
  needed (section 1, last bullet). Probed empirically first: a
  standalone harness driving sa_pokey.dll alone, and then the DLL
  pair with a 6502 routine doing the STA $D2xx writes, always rendered
  flat 0x80 - which exposed that Pokey_Process fills RMT's buffer with
  silence BY DESIGN and the plugin plays audio through its own native
  output (ATCreateAudioOutput), RMT's DirectSound buffer carries
  nothing. The AltirraRMT plugin sources then confirmed the bypass
  link carries every register. Landed as comments: the C++
  CopyAtariMemoryToPokey documents the linked-mode reality (and why
  SKCTL is deliberately absent there), and the Java-side comments from
  T1 no longer claim C++ shared the bug. No changelog entry - the
  Windows program's behavior is unchanged.

- **T3 - WAV export (DONE 2026-10-22, Java).** The Java WAV exporter
  replayed the recorded SAP-R stream, in which Two-Tone is encoded as
  AUDC1 bit 4 - straight into AUDC1, which a POKEY reads as
  volume-only, and it never rebuilt SKCTL: a Two-Tone song's WAV was
  doubly wrong (filter off AND channel 1 forced volume-only). The
  LZSS player's actual semantics, read from vu_player_v2.obx by
  disassembling CheckForTwoToneBit at $0EB8: AUDC1 >= $F0 is exempt
  (genuine volume-only, untouched, SKCTL $03); below that, bit 4 set
  means SKCTL $8B and the bit is cleared (EOR #$10) before AUDC1 is
  stored. WaveFileExporter.pokeChip now mirrors all three, per chip.
  Test (WavExportTwoToneTest): the Makary example's dump carries the
  convention, the WAV's samples equal a translated replay and differ
  from the raw replay (the old behavior); verified to fail with the
  translation disabled. 721 tests green.

  The C++ half dissolved under T2's findings: with the shipped
  AltirraRMT pair, CWaveFileExporter::ExportWAV writes WAVs of pure
  digital silence - RenderPartV2's Pokey_Process returns the 0x80-
  filled buffer regardless of any register writes, since the plugin
  routes audio to its own output. The Two-Tone translation is moot on
  a silent file; the real C++ WAV defect is "the export records
  nothing", a pre-existing problem of the Altirra-plugin era that
  build/compare_exports.ps1 never caught because it deliberately skips
  .wav files. Under the C++ phase-out rule this is documented here
  (and worth a GitHub issue), not fixed; the Java exporter is the one
  that renders real samples.

T1/T2/T3 are each their own commit.

## 3. Notes

- The SAP-R AUDC1-bit-4 convention conflates Two-Tone with a genuine
  volume-only on channel 1. The whole pipeline (PokeyStream.record,
  the LZSS player, T3's replay) shares that assumption; nothing new to
  invent, just mirror it.
- The PokeyController "M" toggle (Pokey menu, SKCTL two-tone bits) was
  inaudible in the Java port before T1 - it eors the driver's SKCTL
  variable, which only reached the PokeyView display. T1 makes it
  audible. (In C++ it was always audible, through the bypass link.)
- The T2 probe harness lives in the session scratchpad
  (sapokey-skctl/probe.c, probe2.c): LoadLibrary + the Pokey_*/C6502_*
  exports, MSVC-built. Useful again if the DLL contract is ever in
  question.
