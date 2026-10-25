# Plan 58: Vertical alignment of inline comments in the player sources

Status: APPLIED (2026-10-25). Addendum (same day, user's decision): bare
`EIF`/`ELS` directive lines and label-only lines with a trailing comment
are their own class - exempt from run alignment and transparent to runs
(runs bridge across them) - and their comments carry no marker prefixes:
`EIF ; FEAT_X`, not `EIF ; /FEAT_X`, no "else"/"<1>"/"end of" wording,
since the directive itself says what ends or branches. With that class
in place the aligner reruns leave zero genuinely ragged runs in either
player. First applied as variant A with all three proposed answers,
per the user's decision. 155 lines realigned in rmtplayr.a65 (15 outlier
lines kept at their own one-tab gap), 20 in rmt.a65 (2 outliers); every
remaining unaligned run is outlier-explained. All proofs green.

## Goal

The inline (trailing) comments in `asm/Patch-16/rmtplayr.a65` and
`asm/Unpatched/rmt.a65` should be vertically aligned as well as possible
for consecutive lines, for readability. This plan analyzes where they are
today and proposes how to realign them.

## Analysis (2026-10-25, after the comment-style and rename commits)

Definitions used throughout:

- An *inline comment* is a `;` comment on a line that carries code before
  it.
- A *run* is a maximal group of directly consecutive lines that each carry
  an inline comment. A run is *aligned* when every comment in it starts in
  the same screen column.
- Columns are measured with tabs expanded. The sources assume **4-column
  tab stops**: with tab=4, `rmt.a65` has 85 of its 99 runs aligned; with
  tab=8 only 23 of 99. (The historical alignment only works out at 4.)

Current state:

| File | Inline comments | Runs (>= 2 lines) | Aligned | Misaligned | Lines in misaligned runs |
| --- | --- | --- | --- | --- | --- |
| `asm/Patch-16/rmtplayr.a65` | 786 | 156 | 69 | 87 | 302 |
| `asm/Unpatched/rmt.a65` | 521 | 99 | 85 | 14 | 46 |

The dominant comment columns are 28-40 in both files (most frequent:
36 in `rmtplayr.a65`, 28 in `rmt.a65`). The drift is concentrated in
`rmtplayr.a65`: the Patch-16 era edits placed comments "one tab after the
code", so the comment column follows the code length instead of a fixed
stop. A typical broken run (`rmtplayr.a65` 243-247):

```
trackn_portafrqc		org *+TRACKS		; Target portamento frequency
trackn_portafrqa		org *+TRACKS		; Current portamento frequency
trackn_portaspeed 	org *+TRACKS	; Portamento speed
trackn_portaspeeda 	org *+TRACKS	; Current portamento speed
trackn_portadepth 	org *+TRACKS	; Portamento depth
```

Two root causes show in this example: a stray space glued to a label
shifts every following tab stop, and the one-tab-after-code habit lets
the comment column wander with the code width.

## Proposal

**A (recommended): conservative per-run realignment.** Only misaligned
runs change. For each misaligned run the target column is the run's most
frequent comment column, rounded up to a 4-column tab stop - or, when
that column does not clear every code line, the smallest tab stop that is
at least one column right of the run's longest code. The gap between code
and `;` is rewritten as tabs. Runs that are already aligned, and single
inline comments outside any run, stay untouched.

Impact (simulated): 87 runs / 170 lines move in `rmtplayr.a65`,
14 runs / 22 lines in `rmt.a65`. The example above becomes:

```
trackn_portafrqc		org *+TRACKS		; Target portamento frequency
trackn_portafrqa		org *+TRACKS		; Current portamento frequency
trackn_portaspeed 	org *+TRACKS		; Portamento speed
trackn_portaspeeda 	org *+TRACKS		; Current portamento speed
trackn_portadepth 	org *+TRACKS		; Portamento depth
```

**B (rejected): full normalization.** Pull every run - aligned or not -
to the smallest fitting tab stop. That would move ~600 lines, drag
intentionally wide alignments leftward, and bury the real improvements
in diff noise.

Open points to decide before execution:

1. **Outlier rule.** When a single line's code is much longer (>= 8
   columns) than the rest of its run, should it keep its own minimal
   one-tab gap instead of dragging the whole run's comments right?
   Proposed: yes, treat it as an outlier.
2. **Run boundaries.** Strictly consecutive lines only (as analyzed), or
   should lines separated only by a blank line or a whole-line comment
   count as one block? Proposed: strictly consecutive first; blocks can
   be a second pass if the result still looks ragged.
   RESOLVED 2026-10-26: the block pass ran with the refined rule - blocks
   bridge blank lines, the exempt directive/label class, bare ";" spacers
   and prose narration comments, and break on ";---" separator runs,
   section-marker comments ("Start of"/"End of", unified from "END of")
   and code without an inline comment. 26 + 15 lines unified.
   Two follow-up passes the same day: (a) floating comments - indented
   whole-line narration like "; With filtering" - snap to their block's
   comment column (74 + 8 lines; continuations tied to the previous
   line's comment column are left alone); (b) cross-file twin-block
   unification - blocks paired through lines whose code and comment text
   are unique and identical in both players take the wider of the two
   columns (193 block pairs, 139 lines). Twin lines sharing their column
   across the files went from 242 of 374 to 365 of 374.
3. **Stray spaces in code.** Normalize a trailing space before the
   code-to-comment gap (the `trackn_portaspeed ` case) while touching the
   line anyway? Proposed: yes - the space is invisible and breaks the
   tab math.

## Mechanics and proof

The change is whitespace between code and `;` only, which neither MADS
nor xasm sees, and it is applied by a deterministic script so both files
are treated identically (shared regions get identical columns because
their line content is identical). Proof is the standing byte-identity
chain: all seven targets byte-identical under both assemblers
(tracker/XEX/SAP against baselines, rmt_ata/sap4/sap8/msx via
`build/check_xasm_compatibility.sh`), `build/check_drivers.sh`, and the
feature-flag matrix. No C++/Java is involved.

## Steps

- [x] A1. Analysis: measure columns, runs, tab-stop convention, drift.
- [x] A2. Proposal with impact numbers and open points (this document).
- [x] A3. User decision on the open points: variant A, all three proposed
      answers (outlier rule yes, strict runs, stray spaces normalized).
- [x] A4. Apply the realignment script to both files (155 + 20 lines).
- [x] A5. Run the full proof chain - all seven targets byte-identical,
      xasm/driver checks and the feature-flag matrix green.
- [ ] A6. Commit on the user's word.
