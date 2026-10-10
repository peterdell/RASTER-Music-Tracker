# Plan 33: The player's replay proven on emulated hardware, headlessly

Status: DONE 2026-10-10 (written retroactively - the work grew out of plan
32's player repairs before it had a plan file). Follow-ups V5-V6 wait on
the upstream pull request.

The question this answers: does the Simple RMT Player (asm/Patch-16's
rmtplayer.a65, repaired under plan 32 and bundled with Captain Future
Theme) actually replay the module the way RMT defines it? Listening and
WAV correlation say "sounds right"; the standard this repository holds
itself to is byte identity. The proof: record the POKEY registers the
player writes on an emulated Atari, frame by frame, and compare against
RMT's own SAP-R export of the module - which holds exactly one full,
non-repeating pass by construction, so it also defines "full replay".

## V1. GUI automation of the Windows Altirra - built, then retired

Altirra 4.40 records SAP type R from its UI. The undocumented autotest
subsystem (mapped from the binary: /autotest registers .autotest_*
debugger commands; startup.atdbg auto-runs; /debugcmd hands commands to
a running instance via /singleinstance) made a scripted recording
possible: build/record_sapr_altirra.ps1 drove it end to end, on a
private portable copy, with the exit confirmation's persisted form
found (DialogDefaults / "DiscardMemory" = "ok" - the "don't ask this
again" checkbox). It worked, but remained what it was: window focus,
a save dialog, SendKeys, real-time pacing, no CI. Retired and deleted
(history: c57b68f); the dialog acrobatics were the signal to stop.

## V2. AltirraBridge - the reliable, headless way

The AltirraSDL fork (github.com/ilmenit/AltirraSDL - the same
cycle-accurate Altirra core, SDL3 frontend) ships AltirraBridgeServer:
a windowless emulator scripted over a local socket with line-delimited
JSON. Its prebuilt Windows nightly is installed beside the Windows
Altirra at C:\jac\system\Atari800\Tools\EMU\AltirraSDL. The verbs map
exactly onto the problem: CONFIG (pal/stereo), BOOT (a path, no
dialog), FRAME n (frame-exact stepping, unthrottled), POKEY (the
write-latch register state - one SAP-R frame's content).

## V3. build/record_sapr_bridge.py - the recorder and comparator

Boots the player XEX, steps FRAME 1 + POKEY per frame, writes a .sapr.
With --reference (RMT's export) it sizes the recording to one full pass
and compares register by register: alignment candidates are scored
(tunes repeat sections, a short probe can anchor on the wrong
repetition) and both reference chips are tried. Two throughput lessons
live in the code: the server's output must go to a file (an undrained
pipe blocks it), and the FRAME/POKEY pairs are pipelined in batches
(each round trip costs a server main-loop tick of ~16 ms; emulation
itself runs FRAME 200 in 0.05 s). A full 150.5 s pass records in ~3 s.

Result: FULL PASS IDENTICAL - 7524 of 7524 frames, the player's first
POKEY against the reference's second half (the player's physical chip
order is swapped relative to RMT's dump layout; content identical).

## V4. The second POKEY: patched upstream

The bridge exposed only the primary chip. Patch submitted as
https://github.com/ilmenit/AltirraSDL/pull/96 (branch
bridge-pokey-chip-index on the peterdell fork, 6 files, +125/-8): a
read-only GetSlave() accessor on ATPokeyEmulator, POKEY [chip] in the
bridge (chip 2 = the stereo slave), the Python SDK's pokey(chip=1),
PROTOCOL.md, and bridge_pokey_chip_test.py registered in ctest. Built
and verified locally (windows-sdl-release preset,
-DALTIRRA_BRIDGE_SERVER=ON; patched server in C:\TEMP\claude\asdl):
with FRAME 1 + POKEY + POKEY 2 pipelined, both chips record in one
pass - 7524 of 7524 full 18-byte stereo frames identical to RMT's
dump, in 2.9 s. The complete stereo replay is proven.

## Follow-ups

- **V5 (open, waits on PR #96):** when merged and a new bridge nightly
  exists, update the installed AltirraSDL and give
  build/record_sapr_bridge.py a both-chips mode (the proof script's
  FRAME 1 + POKEY + POKEY 2 loop shows how); drop its docstring's
  single-chip limitation note.
- **V6 (open):** nothing runs this verification automatically. If the
  drivers or the player sources change more often than expected, a CI
  job could run the recorder against a committed reference .sapr -
  today the driver byte-check (check-drivers.yml) plus this on-demand
  tool are judged enough.
