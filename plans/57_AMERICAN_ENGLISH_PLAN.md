# Plan 57: the whole project uses American English

Status: DONE 2026-10-25 (uncommitted pending the user's word).

## Goal

The sources mixed American and British English - VinsCool's Patch-16
era texts are largely British (colour, initialise, optimise, grey) -
which the user noticed during the plan-51 comment alignment: the whole
project shall use American English. The rule is recorded for good in
README.md under "Conventions"; this plan records the normalization
pass that made it true.

## The pass

Spelling fixes (colour -> color, initialise -> initialize, optimise ->
optimize, behaviour -> behavior, grey/greyed -> gray/grayed, analyser
-> analyzer, analyse -> analyze, synchronisation -> synchronization,
organisation -> organization, recognised -> recognized, normalised ->
normalized, neighbour -> neighbor, flavour -> flavor, colourfull ->
colorful) across:

- Java sources and tests, including identifier renames:
  `optimiseAudc/Audctl/Audf` -> `optimize...`, the `optimisation`
  locals/parameters, and the nested test classes
  `OptimiseAud*Test` -> `OptimizeAud*Test`.
- C++ sources and tests: comments, the "Blue-grey" -> "Blue-gray"
  color name in the XEX export dialog (the Java dialog's list said
  "Blue-grey" too and was fixed alike), and - on the user's explicit
  word, overriding the C++ phase-out hesitation - the identifiers
  `CCompressLzss::Optimise_AUDC/AUDCTL/AUDF` ->
  `Optimize_...` with the gtest suites
  `OptimiseAudcTest/OptimiseAudcTlTest/OptimiseAudfTest` ->
  `Optimize...`.
- asm/Patch-16 and asm/Unpatched comments (including
  rmtplayr.a65/rmt.a65 once the user's WinMerge session was closed).
- plans/, doc/ (rmt_changes.md among them), the READMEs; citations of
  renamed identifiers updated everywhere (Java javadoc, Tuning.cpp/.h,
  TuningTests.cpp, plans 13/36, NOTES.md).

## Exclusions (recorded in README.md's Conventions section)

- Byte-pristine historical artifacts: `asm/Legacy`, `legacy/`.
- Vendored third-party code: `lib/`, `src/cpp/asap/`,
  `src/cpp/test/googletest/` (three googletest comment lines slipped
  into the first commit and were reverted right after - vendored code
  stays verbatim).
- Generated files carrying external label names: `src/cpp/lzssp.h`
  (`LZSSP_RASTERBAR_COLOUR` etc. are VUPlayer's own labels) and the
  comments citing those defines.
- External API names: the `C6502_Initialise`/`Pokey_Initialise` DLL
  exports (word-boundary seds left them untouched by construction).
- Verbatim quotes of any of the above.

## Proof

All 722 Java tests green (the renamed methods/classes among them); the
C++ Release build and all 452 C++ tests green after the identifier
renames; all seven asm targets byte-identical (tracker.obx ==
rmt_driver_v6.obx, rmt_ata.obx == rmt_driver_v1.obx);
check_xasm_compatibility.sh green; the feature matrix unchanged across
all 96 configurations. A repository-wide grep for the British forms
finds only the exclusions. One slip cleaned up on the way: the blanket
sed rewrote the line endings of ~171 files it did not change; they
were restored from the index, leaving exactly the 69 files with real
content changes.
