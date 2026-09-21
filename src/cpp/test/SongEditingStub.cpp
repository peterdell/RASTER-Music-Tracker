#include "StdAfx.h"

#include "Song.h"
#include "Clipboard.h"
#include "TuningTypes.h"
#include "AtariTrackerDriver.h"
#include "SongTimer.h"

extern CAtari g_Atari;

// Real CSongTimer for CSong::Play()/Stop()/PlayBeat()/PlayVBI() (see
// SongEditing.cpp), which only call WaitForTimerRoutineProcessed() on it -
// confirmed by reading CSongTimer.cpp that this is a safe no-op as long as
// m_timerRoutine stays 0, i.e. as long as SetTimer() (the one method that
// calls the real, hazardous Windows timeSetEvent()) is never invoked. None
// of Play()/Stop()/PlayBeat()/PlayVBI() call SetTimer(), ChangeTimer(), or
// StopTimer() - only CSong::ChangeTimer()/TimerRoutine() do (both stay
// unlinked/deferred, see plans/SONG_IO_SONG_REMAINING_PLAN.md), so this
// stays a guaranteed safe no-op for every test in this binary. Do not add
// a test that calls ChangeTimer()/StopTimer()/TimerRoutine() without
// re-verifying this reasoning first.
CSongTimer g_SongTimer;

// Link-only stub for just this one CSongTimer method: the rest of
// SongTimer.cpp (SetTimer()/KillTimer()/StopTimer()/Callback()) needs
// winmm.lib (timeSetEvent/timeKillEvent) and CSong::TimerRoutine() - both
// deliberately not linked here (see the g_SongTimer comment above). This
// mirrors the real body's "if (m_timerRoutine)" guard (see SongTimer.cpp) -
// m_timerRoutine stays 0 in this test binary since SetTimer() is never
// called, so the guard's body (a busy-wait loop) is provably unreachable
// and left out entirely, not behaviorally stubbed.
void CSongTimer::WaitForTimerRoutineProcessed() {
    if (m_timerRoutine) {
        // Unreachable here - see comment above.
    }
}

// Real CAtariTrackerDriver for CSong::PlayPressedTones()/InstrPaste() (see
// SongEditing.cpp) - its constructor just stores a pointer, and the 3
// methods these call (SetTrackNoteInstrumentVolume/SetTrackVolume/
// InstrumentTurnOff) only need g_rmtinstr and CAtari::JSR(), which
// delegates to the already-stubbed no-op C6502::JSR() (see AtariStub.cpp) -
// confirmed by reading AtariTrackerDriver.cpp itself, not assumed.
int g_rmtinstr[SONGTRACKS] = { -1, -1, -1, -1, -1, -1, -1, -1 };
CAtariTrackerDriver g_AtariTrackerDriverInstance(g_Atari);
CAtariTrackerDriver* g_AtariTrackerDriver = &g_AtariTrackerDriverInstance;

// Real, default-constructed globals for CSong::SongEditing.cpp's editing
// methods (see SongEditingTests.cpp) - CTracks/CInstruments/CTrackClipboard/
// CSong all have confirmed-cheap constructors (see TracksTests.cpp/
// InstrumentsTests.cpp/AtariTests.cpp/SongTests.cpp and ClipboardCore.cpp's
// header comment), so a real instance of each is safe to link directly
// rather than stub out.
CTracks g_Tracks;
CInstruments g_Instruments;
CTrackClipboard g_TrackClipboard;
CSong g_Song;

// Real TTuningSettings/TTuningRatios globals for CSong::ResetTuningVariables()/
// MakeModule()/DecodeModule() (see SongEditing.cpp) - both structs are
// already globals-free and tested (TuningTypesTests.cpp), so real instances
// are safe to link directly.
TTuningSettings g_tuning;
TTuningRatios g_tuningRatios;

// Real HWND global for CSong::MakeModule()/InstrInfo() (see SongEditing.cpp) -
// both only ever pass it to a MessageBox() call on a guard branch tests
// never reach (malformed track data, or InstrInfo() called with iinfo ==
// NULL, which these tests never do), so its value is never actually used.
HWND g_hwnd = NULL;

// Real, simple globals for DEFINE_MAINPARAMS (see SongEditing.cpp's
// SaveRMW()/LoadRMW() - actually only SaveRMW() is linked here, LoadRMW()
// needs ClearSong(), see plans/SONG_IO_SONG_REMAINING_PLAN.md). All are
// plain ints/enums/bools with no constructor or hazard of their own - only
// their *addresses* are taken, to build the RMW "main parameters" block.
// g_keyboard_layout already exists in Keyboard2NoteMappingStub.cpp.
Part g_activepart = Part::PART_TRACKS;
Part g_active_ti = Part::PART_TRACKS;
EditMode volatile g_prove = EditMode::EDIT_MODE;
BOOL volatile g_respectvolume = FALSE;
int g_trackLinePrimaryHighlight = 8;
BOOL g_tracklinealtnumbering = FALSE;
BOOL g_displayflatnotes = FALSE;
BOOL g_usegermannotation = FALSE;
int g_cursoractview = 0;
BOOL g_keyboard_escresetatarisound = FALSE;
BOOL g_keyboard_swapenter = FALSE;
BOOL g_keyboard_playautofollow = FALSE;
BOOL g_keyboard_updowncontinue = FALSE;
BOOL g_keyboard_RememberOctavesAndVolumes = FALSE;
WORD g_rmtstripped_adr_module = 0x4000;
long g_playtime = 0;

// Real, simple globals for CSong::ClearSong() (see SongEditing.cpp) - all
// plain BOOL/int/CString flags with no constructor or hazard of their own,
// same treatment as the DEFINE_MAINPARAMS globals above.
BOOL volatile g_rmtroutine = FALSE;
BOOL g_rmtstripped_sfx = FALSE;
BOOL g_rmtstripped_gvf = FALSE;
CString g_rmtmsxtext;
CString g_PrefixForAllAsmLabels;
BOOL g_changes = FALSE;
int g_SkipLinesAfterNoteInsert = 0;

// Real one-line implementation for CSong::ClearSong() (see SongEditing.cpp) -
// copied verbatim from Global.cpp. Global.cpp itself isn't linked here (it
// pulls in a much wider dependency graph - the live timer, MIDI, real Atari
// hardware access), but this one function only ever assigns to g_prove.
void SetEditMode(const EditMode editMode) {
    g_prove = editMode;
}

// Link-only no-op stubs for GuiHelpers.cpp's status bar helpers (see
// ClipboardCore.cpp) - their real bodies just no-op or OutputDebugString
// when there's no real status bar window, which is always the case here.
void ClearStatusBar() {}
void SetStatusBarText(const char*) {}

// Link-only stub: the real CSong::ReInitSound() lives in Song.cpp (not
// linked here - it needs g_AtariTrackerDriver/g_Pokey, real Atari hardware
// simulation) and is reachable through CSong::SetTracks() (see
// SongEditing.cpp), which calls it only when the track count actually
// changes. Tests characterize SetTracks()'s own effect (the g_tracks4_8
// assignment), not the resulting sound reinitialization.
void CSong::ReInitSound() {}

// Link-only no-op stub: the real CSong::SyncSkipLinesAfterNoteInsertComboBox()
// lives in Song.cpp (not linked here - it needs a real MFC AfxGetMainWnd()/
// CMainFrame, unavailable in this console test binary) and is called at the
// end of CSong::ClearSong() (see SongEditing.cpp) purely to push a value
// into a UI combo box - no state ClearSong()'s own tests characterize.
void CSong::SyncSkipLinesAfterNoteInsertComboBox() {}
