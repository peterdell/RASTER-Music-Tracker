#include "StdAfx.h"

#include "Song.h"
#include "Clipboard.h"
#include "TuningTypes.h"

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

// Link-only no-op stubs for GuiHelpers.cpp's status bar helpers (see
// ClipboardCore.cpp) - their real bodies just no-op or OutputDebugString
// when there's no real status bar window, which is always the case here.
void ClearStatusBar() {}
void SetStatusBarText(const char*) {}

// Link-only stub: the real CSong::Stop() lives in Song.cpp (not linked here
// - it needs g_SongTimer, a real OS multimedia timer, a genuine hazard -
// see plans/SONG_IO_SONG_REMAINING_PLAN.md) and is reachable through
// CSong::TracksAllBuildLoops()/TracksAllExpandLoops() (see
// SongEditing.cpp). Its real body only does anything when
// GetPlayMode() != PLAY_STOP, which is never true in these tests (nothing
// calls Play() on the instance first), so an empty stub is behaviorally
// identical to the real Stop() for every test that reaches it.
void CSong::Stop() {}

// Link-only stub: the real CSong::ReInitSound() lives in Song.cpp (not
// linked here - it needs g_AtariTrackerDriver/g_Pokey, real Atari hardware
// simulation) and is reachable through CSong::SetTracks() (see
// SongEditing.cpp), which calls it only when the track count actually
// changes. Tests characterize SetTracks()'s own effect (the g_tracks4_8
// assignment), not the resulting sound reinitialization.
void CSong::ReInitSound() {}
