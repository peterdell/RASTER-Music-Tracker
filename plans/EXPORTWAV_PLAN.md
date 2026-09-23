# Plan: `CWaveFileExporter::ExportWAV` (`ExportV2`'s remaining Tier 2 item)

## Context

`plans/SAP_LZSS_WAV_XEX_PLAN.md` deferred `ExportWAV` as "genuinely
hazardous" without investigating it in depth, on the assumption that it
needed the same real audio-hardware coupling (`CXPokey`/
`LPDIRECTSOUNDBUFFER`) already confirmed for `TimerRoutine()`. This plan
re-investigates from scratch, per this effort's "verify before trusting
old triage" discipline, since - as with `Undo.cpp` and the
`AtariTrackerDriver.cpp` remainder - the assumption turned out to be only
partially right.

## Key findings from investigation

1. **`ExportWAV` calls `CXPokey::RenderSoundV2()`, not `RenderSound1_50()`
   - and only the latter touches DirectSound.** Read `PokeyRenderer.cpp` in
   full: `RenderSoundV2()` only drives `m_pokey` (a `CPokey` member) via
   `CopyAtariMemoryToPokey()`/`GetSoundDriver()`-gated switches - it never
   references `m_SoundBuffer`/`g_lpds`/`g_lpdsbPrimary` at all.
2. **`CPokey::GetSoundDriver()` defaults to `NONE` until
   `CPokey::InitSound()` explicitly `LoadLibrary()`s a POKEY DLL
   (`apokeysnd.dll`/`sa_pokey.dll`)** - confirmed via `Pokey.cpp`. Every
   `switch (GetSoundDriver())` in `RenderSoundV2()`/`PutByte()`/
   `InitPokeys()` has no `case NONE`, so they silently no-op when the
   driver was never loaded - the same "provably safe until explicitly
   initialized" shape as `CSongTimer`'s `m_timerRoutine` guard. Nothing in
   this test ever calls `CPokey::InitSound()`.
3. **`CWaveFile` (`WaveFile.cpp`) uses `mmioOpen`/`mmioCreateChunk`/
   `mmioWrite`/etc.** - pure Windows Multimedia *file* I/O (RIFF chunk
   writing to a regular file), not audio hardware. Needs `winmm.lib`
   (already a production dependency, added to `RmtTests.vcxproj` too) but
   no DirectSound, no device, no DLL.
4. **Real, uninitialized-member bug found**: `CXPokey`'s constructor left
   every member (including the `WAVEFORMATEX` `GetSoundFormat()` returns,
   and the `bool stereo` `CopyAtariMemoryToPokey()` branches on) completely
   uninitialized - harmless in production (`InitSound()` always runs first
   there) but indeterminate for a freshly test-constructed `CXPokey`.
   **Decision** (user, after an initial over-engineered proposal to give
   `m_SoundFormat` "realistic" 44100Hz/8-bit/stereo defaults): just
   zero-initialize every member, matching the simpler `CTracks::m_track`/
   `CInstruments::m_instr` precedent - a degenerate all-zero
   `WAVEFORMATEX` doesn't crash `mmioCreateChunk` (which doesn't validate
   format sanity, just writes bytes), and real production code never reads
   these defaults anyway since `InitSound()` always populates them first.
   Fixed in `PokeyRenderer.h` (default member initializers on every field).
5. **`ExportWAV`/`CWaveFile::OpenFile()` is this suite's first real file
   *write*** (as opposed to `ExportSAP_B_LZSS`/`ExportXEX_LZSS`'s real file
   *reads*) - `CWaveFile` always opens the actual on-disk path via
   `mmioOpen`, unlike the other exporters (which take a generic
   `std::ostream&` the test can point at an in-memory
   `std::ostringstream`). Written to the OS temp directory
   (`std::filesystem::temp_directory_path()`), verified, then deleted at
   the end of the test.
6. **The known Altirra-plugin interop bug is unrelated**: `RenderSoundV2()`'s
   own header comment (and the checked GitHub issue #10, "Export as WAV
   does not work with Altirra runtime libraries") is specific to a real
   POKEY DLL being hijacked by the Altirra emulator's own audio hook - a
   real POKEY DLL is never loaded in this test at all, so this
   characterization is unaffected by (and doesn't attempt to fix) that bug.

## File splits required

Two more `*Core.cpp` splits, matching the pattern already used throughout
this effort (`Song.cpp`/`SongCore.cpp`, `Instruments.cpp`/
`InstrumentsCore.cpp`, `AtariTrackerDriver.cpp`/`AtariTrackerDriverCore.cpp`,
etc.) - in both cases, the "Core" half needed to move because it's the half
the test binary (and, in `CXPokey`'s case, `CXPokey`'s own destructor)
actually needs to link, while the remainder keeps the real hazard:

- **`Pokey.cpp` → `Pokey.cpp` + `PokeyCore.cpp`**: `CPokey`'s constructor/
  destructor/`DeInitSound`/`DeInitPokeyDll`/`GetAbout`/`GetSoundDriver`/
  `IsSoundDriverLoaded`/`InitPokeys`/`PutByte` (plus the 11
  `APokeySound_*`/`Pokey_*` function-pointer globals, since `InitPokeys`/
  `PutByte` reference them) moved to `PokeyCore.cpp`. `Pokey.cpp` keeps
  only `InitSound()`/`InitPokeyDll()` - the two methods that actually call
  `LoadLibrary()`/`GetProcAddress()`.
- **`PokeyRenderer.cpp` → `PokeyRenderer.cpp` + `PokeyRendererCore.cpp`**:
  `CXPokey`'s constructor/destructor/`DeInitSound`/`GetPokey`/
  `GetSoundFormat`/`GetChannels`/`GetChunkSize`/`GetLatencySize`/
  `GetSoundDriver`/`IsSoundDriverLoaded`/`RenderSoundV2`/
  `CopyAtariMemoryToPokey`/`GetFrameRate`/`GetCyclesPerFrame` moved to
  `PokeyRendererCore.cpp`, including `g_lpds`/`g_lpdsbPrimary` (changed
  from file-`static` to plain externs, since `DeInitSound()` - now in
  Core, needed by the destructor - and `InitSoundInternal()` - staying in
  the remainder - both need them). `PokeyRenderer.cpp` keeps
  `InitSoundInternal`/`InitSound`/`ReInitSound`/`RenderSound1_50` - the
  only methods that touch DirectSound.

Both splits' "Core" halves are linked into `RmtTests.vcxproj`; both halves
(old + new file) are linked into `Rmt.vcxproj` as always, so production
gets 100% of the original functionality back, just reorganized into more
files.

## Implementation - DONE

- `PokeyRenderer.h`: every `CXPokey` member given a default member
  initializer (zero/`nullptr`/`{}` as appropriate) - see finding #4.
- `Pokey.cpp`/`PokeyCore.cpp`, `PokeyRenderer.cpp`/`PokeyRendererCore.cpp`:
  split as described above.
- `Rmt.vcxproj`: added `PokeyCore.cpp`/`PokeyRendererCore.cpp`.
- `RmtTests.vcxproj`: added `PokeyCore.cpp`, `PokeyRendererCore.cpp`,
  `WaveFile.cpp`, `WaveFileExporter.cpp`, plus `winmm.lib` to both
  configurations' `AdditionalDependencies` (needed by `CWaveFile`'s real
  `mmio*` calls - `dsound.lib` was *not* added, since nothing in the linked
  code path touches DirectSound).
- `test/SongEditingTests.cpp`: one new test,
  `ExportWAVWritesAValidRiffWaveHeaderWhenNoPokeyDriverIsLoaded` - same
  song/track/info setup as the `ExportSAP_R`/`ExportXEX_LZSS` tests above
  it, constructs a fresh (never-`InitSound()`-called) `CXPokey`, writes to
  a temp-directory `.wav` path, asserts the file exists with a valid
  `RIFF`/`WAVE` header, then deletes it.
- Verified incrementally with an explicit timeout given the
  audio/file-I/O-adjacent hazard class, matching this effort's established
  caution for exactly this situation (`plans/SAP_LZSS_WAV_XEX_PLAN.md`
  Batch 6's `CSongTimer` verification): the new test alone first, then the
  full suite - no hangs. Full solution rebuild (`Rmt.exe` + `RmtTests.exe`,
  Release|x64) confirmed 0 errors; 299 tests pass (up from 298, +1, 0
  regressions).

## What's still deferred

`CSongExporter::ExportLZSS`/`ExportCompactLZSS` remain deliberately
deferred per `plans/SAP_LZSS_WAV_XEX_PLAN.md` (low priority - both write
multiple real files with filenames derived from `songExport.GetFilePath()`,
and the code's own comments call this "a hacked up method... I refuse to
touch RMT2LZSS ever again", with `ExportCompactLZSS` marked "Currently
unused?"). This plan doesn't reopen that assessment.

This closes out `plans/EXPORTV2_PLAN.md`'s Tier 2 family entirely:
`ExportSAP_R`, `ExportSAP_B_LZSS`, `ExportXEX_LZSS` (done in
`plans/SAP_LZSS_WAV_XEX_PLAN.md`) and now `ExportWAV`, leaving only the two
deliberately-deferred, low-value `LZSS` methods.
