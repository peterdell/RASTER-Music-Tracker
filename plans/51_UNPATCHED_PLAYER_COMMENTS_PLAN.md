# Plan 51: Raster's player commentary, translated and merged into Patch-16

Status: DONE 2026-10-27 (closed by the user). U1 and U2 DONE 2026-10-22;
U3 mechanical pass DONE 2026-10-23. The remaining review items are not
plan work: the `;V:`, `;R:` and `TODO:` markers left in the player
sources are open points for future, independent changes to the player.

U3 groundwork (the comparison the user asked for first): Patch-16 vs
the stripped 1.28 rmtplayr.a65, normalized code diff - 69% of the
original code survives verbatim (911 of 1324 lines); 279 lines replaced
by 664, 300 inserted, 134 deleted, in 155 blocks. The big blocks are
additive machinery under Patch-16's own flags (feat_full_16bit, the
feat_two_tone/v_skctl block, the command-7/AUDCTL rework, the
do_tuning_tables section); the genuinely reworked regions are the
variable-area layout, the cmd0/cmd7 dispatch and the SetPokey/AUDCTL
assembly area.

U3 mechanical pass results: 328 comments merged into
asm/Patch-16/rmtplayr.a65, marked `;R: ` (256 trailing on
verbatim-surviving code lines that had no comment of Vin's, 72
standalone prose lines inserted above their verbatim anchor). Proof:
all three Patch-16 targets (tracker.obx = rmt_driver_v6.obx,
rmtplayer.xex, rmtplayer.sap, built with the vendored MADS per
build.cmd) are byte-identical before and after. The review list - 484
items in the session scratchpad (u3-baseline/review.txt) - breaks down
as: 202 anchored on changed/deleted code (the misleading-comment trap,
one-by-one review), 113 file-header lines (RESOLVED 2026-10-23: the
whole block - change history, priority/portamento notes, TODO - was
carried over verbatim into Patch-16's header under one `;R: ----`
provenance line, per the user; builds stayed byte-identical), 96
commented-out-code lines (RESOLVED 2026-10-23: not taken over, per the
user - they document disabled code that exists only in rmt.a65 and can
be looked up there), 73 whose target line already carries a Vin comment
(the two voices meet - manual placement; RESOLVED 2026-10-23: merged by
the user in WinMerge against a generated proposal file in which each
collision line carried Raster's comment in place of Vin's - taken with
plain `;`, no `;R:` marker, and in many places rewritten to merge both
voices into one text; all three targets rebuilt byte-identical
afterwards). Remaining for review: the 202 changed-code anchors
(reviewed the same way, against a regenerated proposal with `;R?: ...
[was on: <original code>]` insertion lines at best-guess positions).

U2 results: 630 of the 916 plain comments translated in place (the
rest are pure numbers, bit patterns, register names, commented-out
code, or already English); both proofs green - the comment-stripped
sources are byte-identical to before, and all four targets assemble to
binaries byte-identical to the pre-translation baseline. Translation
conventions: Czech "takt" is "row" in track context and "cycle" in CPU
context; OPTIMALIZACE -> OPTIMIZATION; the `;*`/`;` prefixes kept.
Lower-confidence renderings for review (no `;?` markers placed - each
has a reasonable reading, listed instead): rmt.a65:78 and :583 ("o N
taktu rychlejsi" read as CPU cycles), rmt.a65:706 (implicit subject,
read as "the bmi below uses this"), rmt.a65:846-847 (Raster's cryptic
carry bookkeeping, translated literally), rmt_ata.a65:44 ("modu 3f00"
read as "module 3f00"), rmt_msx.a65:20 (colloquial "smankote" rendered
"good grief").

U1 results: all six .a65 copied byte-identically; the strip-diff
reproduces the 1.28 distribution's rmtplayr.a65 byte for byte (one
subtlety: the php chop()s EVERY line, so trailing whitespace on kept
`;*` lines is trimmed too); all four targets assemble cleanly with
xasm, and rmt_ata.obx is BYTE-IDENTICAL to
rmt/resources/drivers/rmt_driver_v1.obx (2488 bytes) - asm/Unpatched is
the proven source of the unpatched driver binary. rmt_msx.a65 turned
out to be the screen shell only (it calls a separately assembled player
at $3400, no icl of rmt.a65). Recorded in asm/Unpatched/README.md and
asm/Legacy/README.md.

asm/Legacy/rmt.a65 is the sole surviving source of Raster's own
commentary on the RMT player (plan 50's archaeology, recorded in
asm/Legacy/README.md): ~800 private working comments, mostly Czech,
that rmt_bez_poznamek.php stripped before anything shipped. Everything
downstream - the 1.28 distribution, Patch-16, ASAP's rmt.asx - grew out
of the stripped file, so Raster's explanations and VinsCool's 2021-2022
annotations have never met. This plan brings them together: a clean
English-commented copy of the unpatched player, then Raster's comments
merged into the living Patch-16 source.

Principles:

- asm/Legacy stays byte-pristine - it is the historical artifact; every
  derived work lands elsewhere.
- Comments only, never code: each step carries a mechanical proof that
  the assembled bytes did not change.
- Raster's `;*` (public) vs `;` (private) distinction is semantic
  (rmt_bez_poznamek.php's keep-marker) and is preserved - in
  asm/Legacy. (Relaxed for asm/Unpatched 2026-10-24, per the user: it
  is the living English-commented source, the strip process does not
  apply to it anymore, so `;*` markers there may be edited away; the
  strip-diff proof anchors only the U1 import, the binding invariant
  is assembled byte-identity.)

## U1 - asm/Unpatched: the copy

Copy every .a65 directly in asm/Legacy, byte-identical: `rmt.a65` (the
commented player core, 1.20090108) and all four of Raster's target
harnesses that include it - `rmt_ata.a65` (the tracker-driver build
with the $3D00 entry points), `rmt_sap4.a65`/`rmt_sap8.a65` (the SAP
builds, mono/stereo) and `rmt_msx.a65` (the standalone
player-with-screen) - plus `rmt_feat.a65` (verified byte-identical to
the distribution's copy already). Only the RMF experiment stays behind.
With the full set, every original object file is buildable from
readable sources, the way Raster's own rmt_ata_sap4_8_msx.bat built
them. The folder name matches TrackerDriverVersion.UNPATCHED - this
player IS the unpatched driver. Add a README.md naming the source
revision, the targets and the build, pointing back to asm/Legacy and to
this plan.

Verifications while at it:

- Apply rmt_bez_poznamek.php's algorithm (keep lines containing `;*`,
  truncate at plain `;`, drop emptied lines) to the copied rmt.a65 and
  diff against legacy/rmt_128/asm_src/rmtplayr.a65 - proving the
  revision claim ("the same 1.20090108 the distribution was stripped
  from") instead of asserting it.
- Assemble all four targets with xasm
  (C:/jac/system/Atari800/Tools/ASM/XASM/xasm.exe, the Legacy bat's own
  toolchain). Compare rmt_ata.obx against
  rmt/resources/drivers/rmt_driver_v1.obx (UNPATCHED = driver number
  1). Identical: the repository gains the proven source of the
  unpatched driver binary, which it never had - record that in both
  READMEs. Different: record the delta and what explains it; the copy
  is still only a comment carrier. sap4/sap8/msx have no committed
  binary to compare against (ASAP's rmt4/rmt8.obx are the older 2005
  revision) - for them, assembling cleanly is the check, and the .obx
  outputs are not committed.

## U2 - the translation

Translate the plain-`;` Czech comments of all copied asm/Unpatched
sources to English, in place (the bulk is rmt.a65; the four harnesses
carry a few dozen more). `;*` comments are already English and
stay untouched. Rules:

- Only text after `;` changes; code columns stay byte-identical.
- The `;*` vs `;` prefix of every line is preserved.
- A comment too terse or idiomatic to translate with confidence is
  marked (e.g. `;?`) and listed for the user's review rather than
  guessed silently.

Proof, both mechanical: (a) strip-diff - every file with all comments
removed is byte-identical before and after; (b) xasm-assemble all four
targets before and after, byte-identical .obx each.

## U3 - the merge into Patch-16

Merge the translated Raster comments into asm/Patch-16/rmtplayr.a65,
next to VinsCool's existing annotations. The hard step - Patch-16 is
2306 lines against rmt.a65's 1356, restructured and extended:

- Anchor by normalized code-line matching (the plan-50 diff technique:
  comments and whitespace stripped); only unambiguous anchors get a
  comment merged automatically.
- Provenance stays visible: Raster's merged comments are marked as his
  (suffix/prefix convention to be fixed in execution, e.g. `;R:`), so
  the two voices never blur. (Relaxed 2026-10-24, per the user: where
  the comment text is verbatim in asm/Unpatched/rmt.a65, the `R:`
  marker was the only difference between the two files and only added
  noise to the side-by-side comparison - 317 markers stripped to plain
  `;`, byte-identity re-proven. `;R:` remains on the 11 lines whose
  text has no verbatim counterpart anymore, the header-block intro
  among them.)
- Anchors where Vin CHANGED the adjacent code are the trap - a correct
  comment on the old code misleads on the new. These are collected and
  reviewed one by one, not merged blindly; where the code diverged too
  far, the comment stays out and the gap is listed.
- Proof: the assembled Patch-16 outputs (the driver obx / rmtplayer
  build in asm/Patch-16) are byte-identical before and after the merge;
  the existing driver byte-checks stay green.

One commit per step. U2 and U3 both produce a review list for the user
(uncertain translations; diverged anchors) - the step is done when the
list is resolved, not when the script ran.
