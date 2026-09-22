# Plan: `ExportV2` triage

## Context

`CSong::ExportV2()` (`IO_Song.cpp`) is a static dispatcher, previously dropped
from Batch 3 (`plans/SONG_IO_SONG_REMAINING_PLAN.md`) as needing its own
triage rather than folding into "format encode/decode via streams". It
builds a module via the already-safe `MakeModule()`, then switches on
`SongIOType` to delegate to one of three exporter classes:

```cpp
bool CSong::ExportV2(CSong& song, std::ofstream& ou, SongIOType iotype, LPCTSTR filename)
{
    TExportDescription exportDesc{};
    exportDesc.targetAddrOfModule = 0x4000;
    int maxAddr = song.MakeModule(exportDesc.mem, exportDesc.targetAddrOfModule, iotype, exportDesc.instrumentSavedFlags, exportDesc.trackSavedFlags);
    if (maxAddr < 0) return false;
    exportDesc.firstByteAfterModule = maxAddr;

    CSongContainer songContainer(song);
    CSongExporter songExporter;
    CSongExport songExport(songContainer, filename);
    switch (iotype)
    {
    case SongIOType::RMT: return CRmtExporter::ExportAsRMT(song, ou, &exportDesc);
    case SongIOType::RMTSTRIPPED: return CRmtExporter::ExportAsStrippedRMT(song, ou, &exportDesc, filename);
    case SongIOType::ASM: return CASMFileExporter::ExportAsAsm(song, ou, &exportDesc);
    case SongIOType::ASM_RMTPLAYER: return CASMFileExporter::ExportAsRelocatableAsmForRmtPlayer(song, ou, &exportDesc);
    case SongIOType::SAPR: return songExporter.ExportSAP_R(songExport, ou);
    case SongIOType::LZSS: return songExporter.ExportLZSS(songExport, ou);
    case SongIOType::LZSS_SAP: return songExporter.ExportSAP_B_LZSS(songExport, ou);
    case SongIOType::LZSS_XEX: return songExporter.ExportXEX_LZSS(songExport, ou);
    case SongIOType::WAV: return songExporter.ExportWAV(songExport, ou, g_Pokey, g_Atari.GetMemoryAt(0));
    }
    return false;
}
```

Two real call sites, both in `IO_Song.cpp` (`ExportV2(*this, out, SongIOType::RMT)`
and a `FileExportAs()`-driven path with a filename) - both pass a genuine
`std::ofstream`.

## Key findings from triage

**`ExportV2` itself is a safe dispatcher, even though it unconditionally
constructs `CSongContainer`/`CSongExporter`/`CSongExport` regardless of
`iotype`.** Confirmed by reading each constructor: `CSongContainer`'s ctor
only stores a pointer and throws `RuntimeException` if the song isn't
`PLAY_STOP` (a real but simple guard, not a dialog/hazard); `CSongExport`'s
ctor just stores pointers. Neither touches the real Atari rendering
pipeline - that only happens lazily, inside `CSongContainer::GetPokeyStream()`,
the first time something actually calls it. So `ExportV2`'s own dispatch
logic is testable regardless of which `iotype` branch is exercised.

The nine `iotype` branches split into two starkly different hazard tiers:

### Tier 1 - RMT/ASM export family (same shape as the already-solved "real dialog cluster")

- **`CRmtExporter::ExportAsRMT`** (`RmtExporter.cpp`) - **fully clean already**.
  No dialog, no `MessageBox`, no unreviewed globals (`g_Instruments` is
  already safe). Only calls `CAtariIO::SaveBinaryBlock()` (`AtariIO.cpp`,
  already confirmed zero-global and already linked in the test project,
  same file as the already-safe `LoadBinaryBlock`) plus `song.GetName()`/
  `song.MakeModule()` (both already safe). Its only blocker is that
  `SaveBinaryBlock()` still takes `std::ofstream&` rather than
  `std::ostream&` - the same widening already done for `LoadBinaryBlock`/
  `SaveRMW`/etc. would let tests use an in-memory stream.
- **`CRmtExporter::ExportAsStrippedRMT`** (`RmtExporter.cpp`) - same shape as
  `InstrChange`/etc.: `CExportStrippedRMTDialog` gathers 7 fields
  (`m_exportAddr`, `m_globalVolumeFade`, `m_noStartingSongLine`,
  `m_sfxSupport`, `m_assemblerFormat`, plus `m_song`/`m_filename` which are
  just the function's own existing parameters), all read into locals/globals
  immediately after `DoModal()`, then the rest of the function (regenerate
  the module, save the binary block) runs independently of the dialog
  object. Splittable the same way, once `SaveBinaryBlock` is widened.
- **`CASMFileExporter::ExportAsAsm`** (`ASMFileExporter.cpp`) - same shape:
  `CExportAsmDlg` supplies exactly 4 distinct fields
  (`m_prefixForAllAsmLabels`, `m_exportType`, `m_notesIndexOrFreq`,
  `m_durationsType`), referenced 16 times total through an otherwise
  dialog-independent ~250-line body that only touches already-safe
  `g_Instruments`/`g_Tracks`. Splittable the same way (pass the 4 values as
  parameters instead of reading `dlg.` inline).
- **`CASMFileExporter::ExportAsRelocatableAsmForRmtPlayer`** - its ~70-line
  wrapper reads 11 dialog fields into locals/globals then delegates *all*
  real work to `BuildRelocatableAsm(...)`, which takes those same values as
  **plain function parameters already** - it is not a `CASMFileExporter`
  method that touches `dlg` at all.
- **`CASMFileExporter::BuildRelocatableAsm`** (~545 lines, the bulk of
  `ASMFileExporter.cpp`) - **already a pure, dialog-independent function**,
  confirmed by scanning its entire body for `g_`-prefixed globals and
  `DoModal`/`MessageBox`: the only hits are `g_Instruments`/`g_Tracks`
  (already safe) and a string literal containing "g_" as part of a
  generated assembler label, not a real reference. No split needed at all -
  it just needs to be linked into the test project.

Two incidental blockers for this whole tier, neither hard:
1. `CAtariIO::SaveBinaryBlock` needs the same `std::ofstream&` →
   `std::ostream&` widening as its `LoadBinaryBlock` sibling.
2. **Stale duplicate global**: `g_PrefixForAllAsmLabels` is currently
   defined twice - once for real in `ASMFileExporter.cpp` (not yet linked)
   and once as a trivial stub in `test/SongEditingStub.cpp` (added during
   the `ClearSong` batch, when `ASMFileExporter.cpp` wasn't linked and
   `ClearSong()` still needed *some* definition to assign to). Whichever
   batch links `ASMFileExporter.cpp` for real must remove the stub copy
   first, or the link will fail with LNK2005.

### Tier 2 - SAP/LZSS/WAV/XEX export family (genuinely hazardous, recommend staying deferred)

`CSongExporter::ExportSAP_R/ExportSAP_B_LZSS/ExportLZSS/ExportCompactLZSS/
ExportXEX_LZSS/ExportWAV` (`SongExporter.cpp`) are gated behind
`CSongExport::GetPokeyStream()` → `CSongContainer::GetModifiablePokeyStream()`
→ `CSong::DumpSongToPokeyStream()` (`Song_DumpSong.cpp`) - the same real
Atari-hardware audio-rendering pipeline (`g_AtariTrackerDriver->Play()`,
`g_Pokey`'s `LPDIRECTSOUNDBUFFER`) that Batch 6 explicitly flagged as "a
real audio-hardware coupling, a categorically different (and not yet
investigated) hazard" when scoping `TimerRoutine`. On top of that:
- `ExportSAP_R`/`ExportSAP_B_LZSS` each show a real dialog
  (`CSAPFileExportDialog::Show`) before delegating to `CSAPFileExporter`.
- `ExportXEX_LZSS`'s 1-arg overload shows `ShowXEXExportDialog` (a real
  dialog) before calling its own 3-arg overload.
- `ExportWAV` needs a live `CXPokey&`/Atari memory image, delegating to
  `CWaveFileExporter::ExportWAV`.

None of this has been independently investigated (unlike `TimerRoutine`,
where re-reading the actual code turned "recommend defer" into "mostly
safe" - see Batch 6). Per the "verify before deferring" lesson, this is
flagged as **not yet ruled out**, just correctly identified as needing a
much deeper, dedicated investigation (whether `DumpSongToPokeyStream()` can
run safely in the test binary at all) before any characterization work
here - explicitly out of scope for this pass. Recommend leaving deferred
for now, same posture as `TimerRoutine`/`ChangeTimer`/`ReInitSound`.

## Suggested execution order

1. **Batch A - DONE**: widened `CAtariIO::SaveBinaryBlock` to `std::ostream&`;
   split `RmtExporter.cpp`'s safe half (`ExportAsRMT`) into a new
   `RmtExporterCore.cpp` and linked it directly - no code change needed to
   `ExportAsRMT` itself beyond widening its own `ou` parameter the same way,
   confirming it really was already clean. 1 new round-trip test (through
   `LoadRMT`), 224 tests passing (see `plans/NOTES.md` for the full
   writeup, including a real hazard the test-writing process surfaced).
2. **Batch B - DONE**: split `CRmtExporter::ExportAsStrippedRMT` into a thin
   dialog wrapper plus `ExportAsStrippedRMTApply(CSong&, std::ostream&, int
   targetAddrOfModule, BOOL sfxSupport)` (`RmtExporterCore.cpp`), same
   treatment as `InstrChange`/`TracksOrderChange`. 2 new tests, 226 tests
   passing (see `plans/NOTES.md` for the full writeup, including why these
   tests decode via `CAtariIO::LoadBinaryBlock`/`CSong::DecodeModule`
   directly rather than through `LoadRMT`).
3. **Batch C - DONE**: split `ExportAsAsm` into a thin wrapper plus
   `ExportAsAsmApply(const CSong&, std::ostream&, int exportType, int
   notesIndexOrFreq, int durationsType)`; split `ExportAsRelocatableAsmForRmtPlayer`
   into a thin wrapper plus `ExportAsRelocatableAsmForRmtPlayerApply(...)`
   (new `TRelocatableAsmExportParams` struct, `ASMFileExporter.h`); moved
   `BuildRelocatableAsm()` (already pure) and its `ComposeRMTFEATstring()`
   helper (confirmed pure too while scoping this) into a new
   `ASMFileExporterCore.cpp`. Removed the stale `g_PrefixForAllAsmLabels`
   stub from `test/SongEditingStub.cpp` - its real definition now lives in
   `ASMFileExporterCore.cpp` (the linked half), not the dialog-only
   `ASMFileExporter.cpp`. Also moved `CInstruments::GetFrequency()` into
   `InstrumentsCore.cpp` (a new orphan-safe-method finding, needed by
   `ExportAsAsmApply`'s frequency-lookup branch). 3 new tests, 229 tests
   passing (see `plans/NOTES.md` for the full writeup).
4. **Batch D - DONE, not testable as scoped**: attempted to characterize
   `ExportV2`'s own dispatch logic directly. Moved it out of `IO_Song.cpp`
   into its own `SongExportV2.cpp` for clarity (isolating it from the
   permanently-deferred `FileXxx` family), but **actually attempting to
   link it into the test project failed**: `ExportV2`'s switch statement
   references all 9 `iotype` branches syntactically, so the linker needs
   every one of them to resolve regardless of which case a test would
   exercise. Testing even just the `RMT` case would require linking or
   stubbing `CSongContainer`/`CSongExport`/`CSongExporter`'s constructors,
   all 5 of `CSongExporter`'s Tier-2 methods, the two real-dialog wrappers
   (`ExportAsStrippedRMT`/`ExportAsRelocatableAsmForRmtPlayer` - not their
   already-tested `*Apply()` siblings), and a real `g_Pokey` global - a lot
   of new surface for a thin dispatch layer whose every real branch is
   already directly tested via Batches A-C. `SongExportV2.cpp` stays
   production-only. See `plans/NOTES.md` for the full writeup.
5. **The SAP/LZSS/WAV/XEX family (Tier 2 above)**: investigated in its own
   `plans/SAP_LZSS_WAV_XEX_PLAN.md`. `DumpSongToPokeyStream()` turned out
   safe (traced by hand and confirmed bounded), unlocking `ExportSAP_R`;
   the other four methods each stay deferred for their own distinct
   reasons (a real on-disk resource-file dependency, genuine POKEY-
   audio-hardware rendering, or real multi-file disk writes) - see that
   doc for the per-method breakdown.
