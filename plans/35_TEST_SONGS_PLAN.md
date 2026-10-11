# Plan 35: The test songs and their reference exports in test-resources/songs

Status: DONE 2026-10-11. T1-T4 executed the same day: both songs and their
READMEs moved, the Java tests go through the new TestSongs helper, the
three scripts and the ui-reference README follow; 676 Java tests green,
all four comparison scripts byte-identical in both programs.

## Reasoning

The two test songs live inside `test-resources/ui-reference/`, but they
are shared *inputs* - nine Java test classes and three rmtscripts open
them - while the golden screenshots are the *expected outputs* of just
one of those consumers. Housing the shared input inside one consumer's
folder is the scatter noticed in the test-resource survey. After the
move the taxonomy is clean: `test-resources/songs/` holds the inputs
(one folder per song: the `.rmt`, its README metadata sheet, and any
committed reference exports beside it - the Captain Future pattern from
`rmt/songs/rmt134/buddy/`), `ui-reference/` holds golden screenshots
only, `scripts/` holds the drivers.

The version buckets `rmt128`/`rmt134` repeat the convention of
`rmt/songs` (compatibility, not creation version: 1.28-compatible
versus requires 1.34 or newer), so the repository keeps one layout
rule. `Why_Do_You_Dance_With_Me` was composed in RMT 1.32 and its
driver is still marked "to be checked"; since it provably needs more
than 1.28 and only 1.34+ is shipped, it goes into `rmt134` until the
driver check says otherwise. No author level under the buckets - for
test fixtures the author is a README line, not a path.

Boundaries that stay as they are: the ephemeral rmtscript outputs
remain generated and gitignored (regenerated and cross-compared on
every run - stronger than any committed copy); Raster's historical
exports remain in `rmt/exports`, `legacy/` and `others/` (preservation,
not fixtures); a reference export is committed into a song's folder
only when a check consumes it.

## Target tree

    test-resources/songs/
      rmt128/delta/
        Delta.rmt
        README.md
      rmt134/why_do_you_dance_with_me/
        Why_Do_You_Dance_With_Me.rmt
        README.md

## Steps

- **T1:** `git mv` the two songs and their READMEs out of
  `ui-reference/song1-mono/` and `song2-stereo/` into the tree above;
  the screenshot folders keep their scenario names and the PNGs.
- **T2:** centralize the song paths in the Java tests in one helper
  (`TestSongs`), replacing the ~20 `ReferenceScreenshot.ROOT`-based
  song resolutions; screenshots keep resolving through
  `ReferenceScreenshot`.
- **T3:** update the `open` lines of the three rmtscripts and the
  folder table in `test-resources/ui-reference/README.md`.
- **T4:** verify: full `mvn -o test` and
  `build/compare_exports.ps1` (all exported files byte-identical in
  both programs).
