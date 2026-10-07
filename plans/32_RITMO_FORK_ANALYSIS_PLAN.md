# Plan 32: What the RITMO fork did, and what of it this project wants

Status: PLANNED 2026-10-08.

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
3. **Reference clone**: keep a persistent clone (beside the other
   third-party checkouts) for the duration of the analysis, or re-clone
   per session? The analysis only reads it.

## 5. Verification

The usual gates per R3 fix: the new test fails before and passes after,
`mvn -o clean package` green, Release `RmtTests.exe` green where C++ is
touched, `compare_exports.ps1` identical in both programs. R1/R2 produce
no code, so their verification is the table itself: every one of the 150
commits appears exactly once.
