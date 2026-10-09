# Plan 32: What the RITMO fork did, and what of it this project wants

Status: DONE 2026-10-08 (code); section 7's addendum on the non-code
areas added three follow-ups: the stereo test coverage and the stale
emulation doc page are closed (2026-10-09, see below), the ca65 driver
builds remain open. R1-R3 done; R4 decided: no explicit note to the
fork - its maintainer monitors this repository, so the commits and
change-history entries crediting his commits ARE the communication. The
two open checks are closed: an LF-only .txt loads identically in both
programs (no change needed), and the channels check found a fourth real
bug - every sound export left the tracker muted - fixed in both programs
with a per-format test (ChannelsAfterExportTest).

[RITMO Music Tracker](https://github.com/gianlucarenzi/RITMO-Music-Tracker)
is Gianluca Renzi's fork of this repository: the C++ program ported from
MFC to Qt6, so that the C++ line runs natively on Linux, Windows and macOS.
This project is retiring its C++ program in favour of the Java port, so the
fork's direction is not ours - but 150 commits of real work on the shared
code may contain fixes we want. This plan analyses what was done there and
decides, change by change, what flows back.

## 1. The two lines, measured

Both repositories share history. The newest common commit is `92f371a`
(2026-01-14, the merge of pull request 16), early in the 1.35 work and
before everything 1.36: before ASAP replaced the sound DLLs, before the
scripting, before the Java port existed, before `src/` moved to
`src/cpp/`.

Since that commit:

| | commits | what they hold |
|---|---|---|
| RITMO | 150 | the Qt6 port in phases (started 2026-07), CMake, PortAudio sound, packaging (AppImage for x64/aarch64/riscv64/ppc64, Windows installer, macOS), releases v2.2.1 to v2.5, and fixes to the shared core |
| here | ~378 | 1.35 finished, all of 1.36, the Java port |

81 of their 150 commits touch `src/`. Their `src/` keeps this
repository's file names (`Song.cpp`, `ASMFileExporter.cpp`, ...) in the
pre-move flat layout, so their diffs map onto `src/cpp/` here by name.

The flow is already two-way: their `7af613d` ("Port RMT 1.36 bug fixes
from the MSVC/Java repository (batch 1)") takes fixes from here. Changes
of theirs that originated here must be recognised in R1, not re-imported.

Both repositories are GPL-3.0 and RITMO's README names the fork
relationship, so code may be taken verbatim where that is the easiest
form; each fix still lands through this project's own conventions
(characterisation test first, both programs in step).

## 2. What counts as useful here

- **Core bug fixes that this repository does not already have.** The
  standing preference applies: a provable, low-risk bug is fixed in both
  languages together - the C++ program still ships until the Java port
  replaces it. What does not apply any more is new C++-only capability
  (plan 24's E2 decision), so their Qt, CMake, PortAudio and packaging
  work is read for understanding, not adopted.
- **Behaviour differences that are really fixes**, found by reading their
  diffs rather than their commit subjects.
- **Ideas worth a note, not work**: their riscv64/ppc64/AppImage
  packaging answers the old RISC-V question for the C++ line; the Java
  port answers it with the runtime-free archive instead. Their
  `RMT_AUDIO_DEBUG` diagnostics and "cheaper redraw" are Qt-specific but
  may inspire Java equivalents if the need ever shows.

## 3. Batches

- **R1 - inventory.** Classify all 150 commits: Qt/MFC port mechanics,
  build and CI, documentation, originated-here (the `7af613d` family),
  core behaviour change. The result is a table appended to this plan,
  one row per `src`-touching commit with a verdict. Expected outcome:
  a short list of genuine candidates; the survey already surfaced
  these subjects:

  | their commit | subject | first guess |
  |---|---|---|
  | `2724160` | stripped RMT export end address | candidate |
  | `4e8b1f2` | loading TXT songs/instruments with LF line ends | candidate |
  | `b94585a` | fix the WAV export | likely superseded here by the ASAP 16-bit path - verify |
  | `bbb1548` | driver re-initialised after ClearSong and import | may itself be a port of ours - verify origin |
  | `ab338c1` | fix all compiler warnings | mine it for real bugs only; warning hygiene in retiring C++ is not worth porting |
  | `d1257b7` | 100 % CPU, exit segfault, cheaper redraw | Qt-specific; idea note at most |

- **R2 - disposition.** For each candidate: extract the diff, find the
  corresponding code as it is today (names moved, exporters refactored,
  the sound path replaced), and record one of: *already fixed here* /
  *superseded, subsystem replaced* / *portable fix*. Where the C++ code
  they fixed still exists here unchanged, the diff applies almost
  directly; the Java port then needs the same fix expressed in its own
  terms.

- **R3 - port the accepted fixes.** Per fix: a failing characterisation
  test here first, then the fix in Java and, where the code still
  exists, in C++. `compare_exports.ps1` must stay byte-identical - or
  change identically in both programs, recorded in the fix's commit.
  Each commit credits the RITMO commit it ports.

- **R4 - record and reciprocate.** NOTES entry with the tally
  (adopted / already-had / superseded / not-applicable), change-history
  entries for user-visible fixes, and - if wanted - a note to the fork:
  what flowed back, and that their base predates everything since
  2026-01-14, which they are re-porting piecemeal.

## 4. Decisions requested

1. **C++ scope**: accepted fixes go into both programs while the C++ one
   ships (the standing bug-fix preference), or Java only? *Recommended:
   both, since R3's fixes are bugs, not features.*
2. **Reciprocity**: tell the fork what was taken and what they are
   missing, e.g. one issue over there with the tally? *Recommended: yes -
   the fork credits RMT openly and already ports our fixes by hand.*
   **Decided 2026-10-08: no explicit note.** The maintainer monitors this
   repository's branch, so the documented changes reach him as they are.
3. **Reference clone**: keep a persistent clone (beside the other
   third-party checkouts) for the duration of the analysis, or re-clone
   per session? The analysis only reads it.

## 5. Verification

The usual gates per R3 fix: the new test fails before and passes after,
`mvn -o clean package` green, Release `RmtTests.exe` green where C++ is
touched, `compare_exports.ps1` identical in both programs. R1/R2 produce
no code, so their verification is the table itself: every one of the 150
commits appears exactly once.

## 6. R1 as measured, R2 as verified (2026-10-08)

The 150 commits classify as: 48 Qt port, 32 build and CI, 21 core+Qt mixed
(mostly the port phases and renames), 19 documentation and data, 14 release
notes and packaging, 13 core-only, 3 explicitly importing our 1.36 work.
The verdicts on everything that looked like a candidate:

| theirs | subject | verdict here |
|---|---|---|
| `b243f10` | SAP type B export with VU-Player V2 | **CONFIRMED BUG HERE, both programs**: our freshly exported `d1.sap` fails in ASAP with "INIT routine didn't return", while a checked-in SAP plays (mean sample level 1729). Their diagnosis: the export writes the old player's blocks ($1900-$27FF) while `vu_player_v2.obx` lives at $0C1B-$1F3F, so INIT jumps into bytes the file does not contain. Every SAP this project has ever exported from the 1.36 line is unplayable. |
| `60743c3` | WAV at instrument speeds 2-4 | **CONFIRMED BUG HERE, both programs**: the stereo reference song is 24,325 SAP-R frames at FASTPLAY 78 = 121.6 s; our exported WAV is 487.8 s - each driver call rendered as a whole VBI, 4x too slow at speed 4. Invisible to `compare_exports.ps1`, which excludes the WAV bytes by design. |
| `e732254` | stereo: each POKEY its own AUDCTL | **CONFIRMED BUG HERE, C++ only**: our loop is already right, but the AUDCTL tail writes the second chip's value to register 8 again instead of $18 (`PokeyRendererCore.cpp`), so in live stereo playback the base POKEY gets the right chip's AUDCTL and the second chip gets none. The Java port (`AudioEngine.copyAtariMemoryToPokey`) is correct, so the two programs render stereo differently live - which nothing compares. |
| `dcd62bd` | channels on again after a dump | partially ours already: C++ restores them in the LZSS path and the WAV exporter; the Java dump documents not restoring. **Open check**: export each sound format in each program and assert the channels end on. |
| `4e8b1f2` | TXT songs/instruments with LF line ends | C++ still has one raw one-byte read (`IO_Instruments.cpp:234`); the Java loader works on whole lines. **Open check**: load an LF-only .txt in both. |
| `2724160` | stripped RMT end address (inclusive) | already fixed here: `RmtExporterCore.cpp` saves `firstByteAfterModule - 1`. |
| `b94585a` | fix the WAV export | their MMIO shim for Qt; not applicable - different subsystem here. |
| `ed3dd98` | timer runs again after an export | their own regression in their timer port; our `CExportSection` re-arm was measured working in plan 24 E1. |
| `4747de2` | unsigned-char platforms (ARM, RISC-V, PPC) | not our platforms: MSVC keeps `char` signed on x64 and ARM64, and Java bytes are signed. Idea note only. |
| `964d2a1` | ask to open AUTOFILTER songs with the Unpatched driver | a genuine compatibility feature (Patch16 keeps the filter channels in phase); new capability, so Java-only if wanted - a decision, not a bug. |
| `1050c05` | stereo SAP/XEX size message | our 1.36 fixed the crash; their message naming both sizes is a nicety. Their small `StereoShort.rmt` test song is a good idea for the comparison scripts. |
| `ab338c1` | warning sweep | hygiene in a retiring C++ program; mine only if a warning marks a real bug. |
| `186d6c1`, `68cdd37`, `d1257b7`, `1d42af9` | window-fits-screen, audio buffer setting, cheaper redraw, audio diagnostics | Qt-specific; idea notes. The first is worth remembering for the Java port now that 200 % scaling is the default. |

So R3 has three confirmed fixes to port - the SAP type B player blocks
(both programs), the WAV speed (both programs), the C++ stereo AUDCTL
line - plus the two open checks that may add the channel restoration and
the LF tolerance. The two headline bugs shared one lesson: byte-identical
cross-program comparison proves the programs agree, not that they are
right - both inherited the same defects, and the WAV is excluded from
comparison entirely. R3 therefore adds at least one *external* check: the
exported SAP must play in ASAP.

## 7. The non-code areas (addendum, 2026-10-08)

The first pass classified the fork's documentation, assembler and data
commits but did not mine them. This pass does, area by area:

- **asm/ - the one substantial find.** `2e18376` is a complete conversion
  of the 6502 sources to ca65 (the cc65 toolchain): a
  `scripts/mads2ca65.py` translator, a Makefile, and - the part that
  makes it credible - `make check` proving that `rmt_driver_v1.obx` and
  `rmt_driver_v6.obx` assemble **byte for byte** identical to the shipped
  binaries, plus a POKEY-register check of the player SAP against the
  Patch16 driver. Today this repository can only rebuild the drivers with
  MADS/XASM on Windows; cc65 runs everywhere the Java port does. Worth
  adopting if the drivers are ever to be built from source in CI instead
  of trusted as checked-in binaries. **Follow-up, decision wanted.**

- **doc/ - one stale page of ours found through their fixes.** Their
  `c2c03c7` rewrote the emulation chapter because it still described the
  sound as requiring the external DLLs. Our `doc/rmt_tracker.md` said
  exactly that. **Closed 2026-10-09**, after the fact-check the note
  asked for - which also corrected the note's own guess ("live playback
  moved to ASAP"): the C++ program's live playback AND WAV render both
  still run through the runtime-loaded DLLs (`apokeysnd.dll` preferred
  when present, the shipped `sa_pokey.dll` as fallback, `sa_c6502.dll`
  for the CPU); the vendored ASAP C sources in `src/cpp/asap/` compile
  into `Rmt.exe` but serve no feature yet (the "Tools > Open ASAP File"
  menu entry exists unwired). The rewritten page now describes each
  program: the C++ one with its shipped DLLs and the warning-and-silence
  behavior when they are missing, the Java one with ASAP's emulation
  vendored as source (`src/java/net/sf/asap`, "RMT mode") and no
  external libraries at all. Their other documentation
  work - the LaTeX manual with per-architecture installation chapters,
  per-release notes files, the anchor rewriting - belongs to their book
  pipeline and packaging, not here.

- **test-resources/ - one good idea, taken without the file.**
  `StereoShort.rmt`, a 6 KB stereo song, joined their reference songs so
  stereo SAP exports are tested without dumping the 24,000-frame
  reference song - the full song cannot become a SAP at all, its LZSS
  streams outgrow the player's memory window, so a cut is unavoidable.
  Our SAP tests ran on mono Delta only. **Closed 2026-10-09:** instead
  of copying their derived file (unsettled provenance), the new
  `SapPlayabilityTest.aStereoSapPlaysOnBothChannels` makes the cut
  in-memory on our own stereo reference song: it plants a goto at
  songline 5 - the same "first five songlines" their file freezes - so
  the dump ends there, then asserts STEREO in the header, two channels
  in ASAP, and audible energy on BOTH channels (the coverage the AUDCTL
  bug wanted). No new test resource, no provenance question.

- **rmt/ and scripts/ - noted, not wanted.** The 436 `rmt/` file changes
  are the RITMO rename (`ritmo.ini`, headers, screenshots) plus
  `axel_f.mod` as a MOD-import sample - a copyrighted tune, not something
  to adopt. The 27 `scripts/` commits are packaging (AppImage, .deb,
  installers) for the retiring C++ line, their `check-sap.py` (this
  repository's `SapPlayabilityTest` covers the same ground from inside
  the test suite), and `test-endian.sh` for their big-endian port - the
  Java port is endian-safe by language. `legacy/` had no commits at all
  since the fork point; its 279 changed files are the initial cleanup
  before it.

