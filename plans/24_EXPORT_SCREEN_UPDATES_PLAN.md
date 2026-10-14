# Exports without screen updates (proposal)

Status: **approved 2026-09-28** (decisions 1-3 as recommended, D and E
later); **E1 DONE 2026-09-28** - A, B and C as proposed, measured
(section 6); **E2 RULED OUT 2026-10-04**; **E3 DONE 2026-10-04**
(section 7).
Origin: the user's
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
- **E2** ~~D for the C++ program~~ - **RULED OUT 2026-10-04**. The C++
  program is being phased out and will be removed once the Java port has
  taken over, so a new dialog that only it would ever show is not worth
  building. E1's own measurements argue the same way: the longest export
  measured was 1.98 seconds, and a Cancel button for two seconds of work
  is machinery nobody would use. The status text E1 added stays as the
  whole of the C++ progress display.
- **E3** (DONE 2026-10-04): E for the Java port - see section 7.

## 5. Decisions requested

1. **A** as the display during exports: status bar text only, about four
   times per second, no redraw of the view. *Recommended.*
2. **B** the single export guard around `ExportV2`. *Recommended.*
3. **C** restore the cursor and play time after an export, in both
   programs. *Recommended*, pending the hand check that the cursor really
   moves today.
4. **D** Cancel support: now, later, or not at all. *Recommended: later*
   (E2), once A shows how long the exports really take without the
   redraws. **Decided 2026-10-04: not at all** - see E2 in section 4.
5. **E** the Java port's export on a worker thread with progress. *Later*
   (E3), if the frozen window during long exports bothers. **Done
   2026-10-04**, see section 7.

## 6. E1 as built, and what the measurement showed

- `RMT_SCRIPT_SHOW_WINDOW=1` (new, documented) shows the window during a
  script run, so the interactive export could be timed without UI
  automation; the script runners of both programs print the export time
  ("Exported <file> (N ms)").
- **Finding on the way**: the "hidden" script window was not always
  hidden. MFC shows the frame itself while processing the shell command,
  with the `m_nCmdShow` that `CMainFrame::PreCreateWindow` restores from
  the saved window placement - so as soon as the program had once been
  closed interactively, script runs showed the window (and painted, and
  wrote a layout-dependent `g_cursoractview` into `.rmw` saves, which the
  cross-program comparison caught). Fixed: `m_nCmdShow = SW_HIDE` before
  the shell command for a script run, `PreCreateWindow` respects it.
- **Timings** (stereo reference song, Release build, this machine; the
  program's own export time, median of two runs; window shown):

  | Export | before | after (A + B) |
  |---|---|---|
  | `sapr` (the dump alone) | 920 ms | 520 ms |
  | `lzss` (dump + 8 compression passes) | 2 370 ms | 1 980 ms |
  | `wav` (dump + rendering) | 1 560 ms | 1 170 ms |

  Before and after, "hidden" and "shown" did not differ measurably - and
  in the "before" runs both were in fact shown (see the finding), so the
  redraws themselves cost little at this window size. The gain of about
  400 ms per export comes from B: the song timer thread no longer renders
  sound through the POKEY at 50 Hz beside the export. The visual noise was
  confirmed by a screenshot during the export (the play time racing, the
  song lines running, 49 frames per second in the debug display) and is
  gone: the screen stands still, the status bar counts the frames.
- A: `DumpSongToPokeyStream` without `RefreshScreen`, the status text every
  250 ms by `GetTickCount`; `ExportLZSS` without its 8 redraws.
- B: `CExportSection` (`GuiHelpers.h`) in `CSong::ExportV2` and in the
  script runner's `Export`: window disabled, wait cursor, song timer
  stopped; on leaving the timer re-armed, the status bar cleared, one
  refresh. `DisableEventSection` and `ExportWAV`'s own `StopTimer`/
  `ChangeTimer` removed.
- C: the dump restores `m_songactiveline`, `m_trackactiveline` and
  `g_playtime`; the Java dump restores the active lines and
  `SongFiles.fileExportAs` the UI's play time. Tests in both programs.

## 7. E3 as built

The export is split in two. `SongFiles.prepareExportAs()` keeps what belongs
on the event thread - stopping playback, checking the module, asking for the
file name, creating the file - and returns an `ExportRequest`, or null when
the user cancelled. `SongFiles.runExportAs(request, progress)` does the slow
part: the format's own dialog, the register dump and the writing.
`fileExportAs()` still calls both in order, so every script and every test is
unchanged and runs on whatever thread it was on.

`RmtMainWindow` runs the slow part in a `SwingWorker` that holds
`RmtSession.lock` for its whole run, so the audio thread waits for it instead
of racing it - the same guarantee the C++ program gets by stopping its song
timer. The window stays responsive, and the status line counts the frames.
The format dialogs are Swing components, so the host now shows them on the
event thread through an `onEdt` helper, the way `SwingMessages` already
showed the message boxes.

Progress and cancellation travel through `ExportProgress` (in the model, so
the dump stays free of any UI): `framesRecorded` about four times a second by
wall clock, as the C++ status text does, and `isCancelled` polled once per
frame. Cancelling is a flag the worker sets, not an interrupt, since
interrupting would leave the lock and a half-written file in an unclear
state. A cancelled dump leaves an incomplete stream, so `runExportAs` treats
it as a failure and deletes the file - without the "Export aborted" warning,
because the user asked for it. A Cancel button appears beside the status line
while an export runs, and a second export is refused until the first ends.

Note that the Cancel the C++ program will never get (E2) comes to the Java
port for free here: the worker needs a cancellation path anyway.

Verified: 666 tests pass, including three new ones - the two-phase export
writes exactly what the single call writes, a cancelled export leaves no file
and no warning, and the frame count never goes backwards - plus one that the
File > Export command still writes its file through the new split. The
cross-program comparison still reports every exported file identical in both
programs, and the window was started to confirm the status bar's new layout.
