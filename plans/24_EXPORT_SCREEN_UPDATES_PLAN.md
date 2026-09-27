# Exports without screen updates (proposal)

Status: **approved 2026-09-28** (decisions 1-3 as recommended, D and E
later); E1 in progress. Origin: the user's
observation that the C++ program updates the screen during exports,
"probably due to reusing the UI logic/timer", which creates visual noise
and slows the export down.

## 1. What happens today (C++)

Every export that needs the register stream (SAP-R, LZSS, SAP, XEX, WAV)
runs through `CSongContainer::GetPokeyStream()` ->
`CSong::DumpSongToPokeyStream()` (`Song_DumpSong.cpp`), on the UI thread,
inside the export command:

1. `Stop()`, driver `Init()`, all channels off, `m_pokeyStream` set, then
   `Play(playMode)` - the song is put into the **real playback state**
   (`m_play`, the play lines, `m_songactiveline`/`m_trackactiveline` set to
   the start line).
2. A tight loop until the song stops: `PlayVBI()`, `g_playtime++`, the
   driver's `Play()` and `pokeyStream.Record()` per instrument-speed step -
   the "quick mode", many times faster than real time.
3. **Per iteration `RefreshScreen(1)`** (`GuiHelpers.cpp`): `Invalidate()`,
   `SCREENUPDATE`, `UpdateWindow(g_viewhwnd)`, i.e. a synchronous
   `WM_PAINT` -> `CRmtView::OnDraw()` -> `Resize()`, `DrawAll()` (info,
   song, volume analyzer, play-time counter, the whole track or instrument
   area) and the `StretchBlt` of the entire client area. The "frameskip"
   argument only prevents a second redraw within the same timer tick:
   `g_timerGlobalCount` is incremented by the song timer thread
   (`CSong::TimerRoutine`, a `timeSetEvent` every 20 ms / 17 ms), so the
   dump redraws the full screen **up to 50-60 times per second for its
   whole duration**, plus a status bar text per redraw.
4. Because the song is in playback state, every redraw shows the song
   "playing" at quick-mode speed: the play-time counter races, the volume
   analyzer flickers, the play lines run - the visual noise.
5. Only `WM_PAINT` is processed. No message loop runs (`UpdateWindow` does
   not pump), the window is disabled for input by `DisableEventSection`
   (`EnableWindow(FALSE)` + wait cursor). The redraws therefore serve no
   responsiveness purpose; they are a progress display, and an expensive
   one. (The comment in `DumpSongToPokeyStream` - "MFC messages are being
   pumped" - is not accurate.)
6. The song timer thread keeps running during the dump. Its
   `TimerRoutine()` is bypassed while `m_pokeyStream` is recording (it only
   re-arms itself and counts), but the moment the dump returns it renders
   sound again through the shared POKEY - the cause of the WAV export crash
   fixed in C1 by stopping the timer inside `ExportWAV` only.
7. After the dump, the LZSS stage (`CSongExporter::ExportLZSS`,
   `SongExporterCore.cpp`) brute-forces 8 optimisation patterns with one
   `RefreshScreen()` after each - 8 redraws, harmless; the XEX export
   repeats the dump per subsong.
8. The dump leaves its traces: `m_songactiveline`/`m_trackactiveline` were
   set to the start line and `g_playtime` was counted up, so after an export
   the cursor is no longer where the user left it. (To be confirmed by
   hand; the Java port ports the same behaviour.)

In a script run (`Rmt.exe /SCRIPT`) the window exists but is hidden, so
`Invalidate()` produces no `WM_PAINT` and the cost is not paid. The noise
and the slowdown are a property of the interactive export.

**The Java port** (`Song.dumpSongToPokeyStream`) runs the same loop without
any screen update, under the session lock, on the event dispatch thread:
no noise, but also no progress display - the window is frozen until the
export finishes.

## 2. Cost

Not measured yet; the first step of the batch is a measurement, because
the numbers decide how much the display is allowed to cost. Proposed
measurement: the time of `export sapr` for the two reference songs (Delta
~3 200 frames, the stereo song ~20 000 frames) in the window at 100 % and
at 300 % scaling, once as is and once with `RefreshScreen(1)` commented
out (the script run through the hidden window is a third data point that
approximates "no redraws"). The redraw is a full `DrawAll()` plus a scaled
blit of the client area; at 50 per second that is a good share of a loop
whose own work per frame is a handful of 6502 calls.

## 3. Proposals

### A. No screen redraw during the dump, a throttled status text instead (recommended)

`DumpSongToPokeyStream` stops calling `RefreshScreen(1)`. Progress goes to
the status bar only - `SetStatusBarText` is a `SetWindowText` on the
status bar control, cheap - throttled by wall clock (`GetTickCount()`,
about four updates per second) rather than by the timer tick, so it does
not depend on the timer thread at all. One `RefreshScreen()` at the end
restores the screen. The LZSS stage keeps its 8 redraws or gets the same
status-only treatment ("pass 3 of 8, best so far 12 345 bytes"). The
frames dumped so far is the only number available for the dump (the loop
end is detected, not known in advance); the LZSS stage knows its 8 passes.

Removes the noise and the cost with a change of a dozen lines. The user
still sees the wait cursor and a live status text.

### B. One export guard for every exporter

An RAII "export section" (`CExportSection`) that stops the song timer,
disables the window (today's `DisableEventSection`), sets the wait cursor,
and on leaving re-arms the timer, refreshes the screen once and clears the
status bar - used by `CSong::ExportV2` around the whole export, so every
exporter is covered uniformly. Replaces the per-exporter handling that
exists today (`DisableEventSection` in the dump and the LZSS stage,
`StopTimer`/`ChangeTimer` inside `ExportWAV` from the C1 fix) and closes
the remaining window in which the timer thread renders through the POKEY
between the stages. Small, and the right place for A's final refresh.

### C. The dump leaves no traces

Save `m_songactiveline`, `m_trackactiveline`, `g_playtime` (and the
follow-play state) before the dump and restore them after `Stop()`, so an
export does not move the cursor. Same in the Java port, whose dump has the
same side effect. A few lines each, verified by the existing dump tests
plus one assertion per program.

### D. Progress dialog with Cancel

A modeless progress dialog (frames, stage, Cancel button) with a proper
`PeekMessage` loop inside the dump, instead of redrawing the main view.
Gives the user a way out of a long export (the stereo song's WAV takes a
while). Medium effort: the loop, the dialog, a cancel flag checked in the
dump and the LZSS passes, the partial output file removed on cancel. Can
be added on top of A and B later without undoing them.

### E. The export on a worker thread

The real fix for a frozen UI, but the exporters work on globals shared
with the timer thread and the view (`g_Song`, the driver, the POKEY, the
status bar), none of which is thread-safe. Not recommended for the C++
program. For the Java port it is the natural shape: the session lock
exists for exactly this, the `AudioEngine` already waits on it, and a
`SwingWorker` holding the lock could report progress to the status line -
but it is a Java-only improvement and a separate batch.

## 4. Batches

- **E1**: the measurement (section 2), then A + B + C in the C++ program;
  the Java port gets C (no traces) so both dumps stay comparable
  (`CrossProgramExportTest` must stay byte-identical - the export data does
  not depend on any of this); NOTES with the measured numbers.
- **E2** (optional, later): D for the C++ program.
- **E3** (optional, later): E for the Java port.

## 5. Decisions requested

1. **A** as the display during exports: status bar text only, about four
   times per second, no redraw of the view. *Recommended.*
2. **B** the single export guard around `ExportV2`. *Recommended.*
3. **C** restore the cursor and play time after an export, in both
   programs. *Recommended*, pending the hand check that the cursor really
   moves today.
4. **D** Cancel support: now, later, or not at all. *Recommended: later*
   (E2), once A shows how long the exports really take without the
   redraws.
5. **E** the Java port's export on a worker thread with progress. *Later*
   (E3), if the frozen window during long exports bothers.
