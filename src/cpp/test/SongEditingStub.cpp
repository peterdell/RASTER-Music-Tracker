#include "StdAfx.h"

#include "Song.h"
#include "Clipboard.h"

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
