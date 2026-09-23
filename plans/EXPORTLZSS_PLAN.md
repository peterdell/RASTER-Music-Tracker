# Plan: `CSongExporter::ExportLZSS` / `ExportCompactLZSS`

## Context

The last two deliberately-deferred items from `plans/SAP_LZSS_WAV_XEX_PLAN.md`
("low priority given their real-file-write design and the 'hacked
up'/'currently unused?' self-assessment in their own comments"). Opened at
the user's explicit request after every other characterization-testing
candidate across every plan doc was closed out.

## Key findings from investigation

1. **Neither method shows a dialog** - both were already dialog-free,
   unlike most of the rest of `SongExporter.cpp`. The only reason they
   weren't linked yet is that they lived in `SongExporter.cpp` alongside
   the real dialog-showing methods (`ExportSAP_R`, `ExportSAP_B_LZSS`,
   `ShowXEXExportDialog`) - linking that file wholesale would also require
   linking `SAPFileExportDialog.cpp`/`ExportDlgs.cpp` (real MFC dialogs).
   **Fix**: moved both methods into the already-linked
   `SongExporterCore.cpp`, matching the exact same split already done for
   `ExportXEX_LZSS`'s dialog-independent overload.
2. **Both only need already-safe dependencies**: `songExport.GetPokeyStream()`
   (already established safe throughout `plans/SAP_LZSS_WAV_XEX_PLAN.md`),
   `CLZSSFile::GetFrameSize()` (already linked/tested), and
   `CCompressLzss::LZSS_SAP()` (already linked/tested - `LzssTests.cpp`).
   No new hazard category - this was mechanical linking, not a new
   investigation the way `ExportWAV`'s `CXPokey` coupling was.
3. **Both write real files** (same territory as `ExportWAV`'s real file
   write): `ExportLZSS` writes up to three `.lzss` files (the caller's own
   already-open path for the "full" section, plus `_INTRO.lzss`/
   `_LOOP.lzss` if their compressed sizes exceed a hardcoded "> 16 bytes"
   guard); `ExportCompactLZSS` writes one `.txt` log file unconditionally.
4. **Real finding, changed the test design mid-way**: no amount of
   note/instrument variation in a test song can make the recorded
   `CPokeyStream` bytes vary, in this or any test in this suite. The
   actual "note → POKEY register write" translation happens inside the
   real RMT 6502 driver routines, executed via `C6502::JSR()` - a no-op
   stub throughout this entire project (`test/AtariStub.cpp`), precisely
   because running real 6502 code isn't something this suite does. So the
   stream `CCompressLzss` compresses is always the same near-silent,
   highly repetitive pattern regardless of song content, and `ExportLZSS`'s
   own "> 16 compressed bytes" thresholds are - deterministically, not by
   chance - never crossed by anything this test environment can produce.
   Confirmed by trying three increasingly elaborate song shapes (more
   songlines, higher per-frame note/instrument/volume entropy, an explicit
   intro-then-loop structure to make `GetThirdCountPoint()` non-zero)
   before concluding this was structural, not a matter of trying harder -
   the LZSS compressor's own debug output ("stream #0 is empty") for every
   attempt was the direct evidence, not just the byte counts.
5. **Trivial test bug found**: `PADHEX` (`General.h`) prepends `"0x"` to
   its hex output, so `ExportCompactLZSS`'s log lines read `"Index: 0x00"`,
   not `"Index: 00"` - the test's own first attempt searched for the
   latter and failed.
6. **`ExportCompactLZSS`'s second `while` loop is genuinely dead code**:
   its body is `// I don't know anymore, at this point...` with no other
   statement - confirmed by reading it, not just inferring from the
   comment. Left alone (out of scope; not a bug affecting any observable
   output, since nothing reads whatever that loop was meant to compute).

## Implementation - DONE

- `SongExporter.cpp` → `SongExporterCore.cpp`: moved `ExportLZSS`/
  `ExportCompactLZSS` verbatim (plus `#include <iomanip>` for `PADHEX`/
  `PADDEC`'s `std::setfill`/`std::setw`, already used elsewhere in the
  file's own text-writing methods).
- `test/SongEditingTests.cpp`: two new tests.
  `ExportLZSSNeverCrossesTheCompressedSizeThresholdInThisTestEnvironment`
  characterizes the now-understood-to-be-deterministic outcome (the
  caller's own path ends up empty, `_INTRO.lzss`/`_LOOP.lzss` never get
  created) rather than fighting to force a specific byte count.
  `ExportCompactLZSSWritesADuplicateSonglineLogFile` verifies the always-
  created `.txt` log's content shape (per-songline offset/duplicate/byte-
  count lines).
- Verified incrementally with an explicit timeout (same posture as every
  real-file/hazard-adjacent test in this suite): both new tests alone
  first, then the full suite - no hangs, no crashes. Full solution rebuild
  (`Rmt.exe` + `RmtTests.exe`, Release|x64) confirmed 0 errors; 301 tests
  pass (up from 299, +2, 0 regressions).

This closes out `plans/SAP_LZSS_WAV_XEX_PLAN.md`'s entire `ExportV2` Tier 2
family - nothing from that plan's scope remains deferred.
