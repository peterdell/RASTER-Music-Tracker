# Plan 49: Answer Fox's review of ASAP merge request #4 and re-submit on GitHub

Status: DONE 2026-10-22, except the SourceForge closing comment (B7),
which is a browser step for the user. The re-submission is
https://github.com/pfusik/asap/pull/1 (branch `pokey-register-shadow`,
commit `ec43b1b`, one commit over 8.0.1).

What execution decided beyond the plan:

- The step is named `StepPlayer`, not `StepFrame`: the user found
  "DoFrame does cycles" and SAP-R's row-"frame" inconsistent with the
  Atari video frame, so the public step joins ASAP's existing player
  vocabulary (`Call6502Player`, `NextPlayerCycle`,
  `GetPlayerRateScanlines`), and the internal `DoFrame`/`Do6502Frame`,
  which now take their cycle window as a parameter, became
  `DoCycles`/`Do6502Cycles` - "frame" in identifiers stays reserved for
  the video frame (`GetFrameCycles`, `Pokeys.StartFrame`/`EndFrame`).
- fut 3.3.9 (released 2026-10-22) regenerates pristine 8.0.1
  byte-identically, so the generator matches Fox's - the R3 parity
  check passed outright. The 3.3.9 binary ran from the extracted MSI in
  the session scratchpad; the proper MSI install into
  `C:\jac\system\Windows\Tools\FUT` is still for the user to run.
- The R1 teeth: `LivePlaybackTest.aDoubleSpeedSongStepsAsapOncePer`
  `DriverTickNotPerVideoFrame` plays Delta at instrument speed 2 and
  fails against per-video-frame stepping (verified by reverting the
  step), passes with `stepPlayer`. 718 Java tests green,
  `compare_exports` identical.
- The NMIST concern resolved benignly: between direct player calls the
  CPU sits at the escape address (`Call6502Player`), so nothing polls
  NMIST at sub-frame cadence in any tune the API targets.

Fox (Piotr Fusik) reviewed merge request #4
(https://sourceforge.net/p/asap/code/merge-requests/4/, opened by plan 31
step A5) on 2026-10-20. His reply has four points, one of which is a real
design gap, and ends with a venue change:

1. "What about player routines called multiple times per frame?"
2. "`& 0xff` are not needed as the variables already contain 8-bit
   unsigned values."
3. "Also, update your `fut`."
4. "I moved the repo to https://github.com/pfusik/asap. You can re-submit
   your updated PR there."

This plan answers all four and re-submits the contribution as a GitHub
pull request. Plan 31 stays the umbrella (its A6, the RMT-mode block, is
untouched); this plan replaces its A5 follow-through.

## 1. The review, point by point

### R1 - player routines called multiple times per frame (the real one)

`StepFrame` calls `DoFrame`, which emulates one full video frame
(262/312 scanlines). But the player routine is called from
`HandleEvent` every `114 * ModuleInfo.GetPlayerRateScanlines()` cycles,
so a fast tune (SAP `FASTPLAY` below a frame, an RMT module above speed
1) runs its player two or more times inside one `StepFrame` - and
`GetPokeyRegisterShadow` then only shows the writes of the *last* call.
Intermediate rows of the register dump are silently lost.

This is not a documentation gap, it hits both known users:

- A SAP-R dump records one 9-byte row per *player call* (SAP-R calls
  that row a "frame", at the `FASTPLAY` rate, not the video rate).
- RMT's own reference dump (`LivePlaybackTest.asapReference`) expects
  one row per driver tick. It currently passes only because the test
  song is single-speed, where the two rates coincide.

**Resolution to offer:** make the step one *player call*, not one video
frame - for standard tunes the two are identical, so nothing changes
for them. Implementation sketch: the stepping path runs
`114 * ModuleInfo.GetPlayerRateScanlines()` cycles instead of
`Do6502Frame`'s fixed full-frame count; `Pokeys.StartFrame`/`EndFrame`
already take the cycle count as a parameter, and `Generate`'s own
`DoFrame` use is untouched.

Open implementation question to settle while coding: `Do6502Frame`
advances `Nmist` (the VBlank NMI status) once per invocation. A
sub-frame step must not multiply the VBlank cadence for tunes that
watch NMIST - either advance `Nmist` only when a video-frame boundary
is crossed, or show that no supported format both uses NMIST and plays
faster than the frame rate.

Naming: SAP-R itself calls each row a frame, so `StepFrame` can keep
its name with the doc corrected to "emulates up to the next player
call; for standard tunes this is one video frame". Offer the rename
(`StepPlayer`) in the PR text and let Fox pick - it is his API.

### R2 - drop the `& 0xff` masks

Three occurrences in `GetPokeyRegisterShadow` (`Audctl`, `Skctl`,
`Audf`/`Audc`). Fusion fields hold 8-bit unsigned values already; the
masks are noise. (`GetPokeyChannelVolume`'s `& 0xf` is different - it
extracts the volume nibble - and stays.)

### R3 - update `fut`

The branch was generated with Fusion Transpiler 3.2.13
(`C:\jac\system\Windows\Tools\FUT\Fusion\fut.exe`). Current release is
**fut 3.3.9** (2026-10-22, `fut-3.3.9-win64.msi` on
https://github.com/fusionlanguage/fut/releases). Upstream committed the
8.0.1 release on 2026-10-20, so its checked-in `asap.c`/`asap.h` are
fresh: after updating, regenerating the *pristine* sources must
reproduce upstream's files byte-identically - that proves the generator
matches Fox's before our hunks go on top. If it does not, diff the
noise and pin the fut version Fox actually used before committing
anything generated.

### R4 - re-submit on GitHub

The SourceForge repo is retired; upstream is now
https://github.com/pfusik/asap (`master`, currently `5e03120`
"[release] 8.0.1."; the old SF base `28af663` is in its history, so the
branch rebases normally). `gh` is logged in as `peterdell`; no fork of
`pfusik/asap` exists yet - create it. The SF fork `u/peterdell/asap`
becomes dormant.

## 2. Steps

- **B1 - toolchain.** Install fut 3.3.9 from the win64 MSI, confirm
  `fut --version`. 3.2.13 is an MSI install redirected to
  `C:\jac\system\Windows\Tools\FUT` (exe in its `Fusion` subfolder,
  which is on the user PATH); the 3.3.9 MSI has the same one-file
  `Fusion\fut.exe` layout, so choosing the same target directory keeps
  the PATH valid - letting it default to Program Files would strand the
  PATH entry. `xasm` stays on the path
  (`C:\jac\system\Atari800\Tools\ASM\XASM`) - the 6502 player binaries
  are assembled as a prerequisite of `asap.c`.
- **B2 - new baseline.** In the clone
  (`C:\jac\system\Fusion\Programming\Repositories\Tools\asap`): add a
  `github` remote for `pfusik/asap`, fetch, rebase
  `pokey-register-shadow` (one commit, `f4afcfd`) onto its `master`.
  Then the R3 parity check: regenerate from pristine sources and diff
  against upstream's committed `asap.c`/`asap.h`.
- **B3 - rework the commit.** Apply R1 (player-call stepping, the
  `Nmist` decision, corrected doc comments) and R2 (drop the masks) in
  `asap.fu`, regenerate `asap.c`/`asap.h`, amend into the one commit
  with an updated message that states the per-player-call semantics.
- **B4 - verify against this repository.** As in plan 31 A4: generate
  the Java sources from the reworked branch, drop them into
  `src/java/net/sf/asap/`, run `mvn -o test`. `LivePlaybackTest` is the
  sentinel. R1 needs teeth: check the test song's speed, and if it is
  single-speed (expected), add a second case with a faster-than-frame
  song so the per-player-call step is actually exercised against RMT's
  own per-tick dump.
- **B5 - adopt in the port.** Keep the updated
  `src/java/net/sf/asap/` copy and point `lib/java/README.md` at the
  new branch state (it names the fork branch until a release carries
  the change). No changelog entry - the Java port ships nothing yet and
  the Windows program is untouched.
- **B6 - submit.** *Outward-facing: show the user the PR title/body
  before sending.* Fork `pfusik/asap` as `peterdell`, push the branch,
  open the PR with `gh`. The body: the MR #4 description updated with
  the per-player-call answer to R1, a line each for R2/R3, a link back
  to MR #4, and the standard attribution footer.
- **B7 - close the loop.** Comment on SF MR #4 that the updated PR is
  on GitHub (link), and close the MR if SourceForge lets the submitter;
  SourceForge has no CLI, so this is a browser step for the user unless
  an Allura API token is set up. Update plan 31 (A5's follow-through
  now tracked here) and this plan's status plus the README row.

## 3. Why answer R1 with code instead of a caveat

Documenting "the shadow shows the frame's last write" would survive
review only until Fox tried it on a `FASTPLAY 156` SAP-R dump - the
format this accessor exists for records at exactly the rate the current
step skips. Both known users want player-call granularity; the change
is small and invisible to standard tunes. Answering the question with
the fix is also the strongest case for merging.
