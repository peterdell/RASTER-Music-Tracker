#include "StdAfx.h"

#include "GuiHelpers.h"

// Link-only stubs for CSong::DumpSongToPokeyStream()'s (Song_DumpSong.cpp)
// two GuiHelpers.cpp dependencies - GuiHelpers.cpp itself isn't linked here
// (it has its own wider Global.h/real-window dependencies via EditText()/
// IsHoveredXY(), unrelated to DumpSongToPokeyStream()).
//
// RefreshScreen()'s real body starts with "if (!g_hwnd || !g_viewhwnd ||
// g_closeApplication) return 0;" - g_hwnd is always NULL in this test
// binary (see SongEditingStub.cpp), so the real function already always
// returns 0 immediately, without reaching any real GDI/window call. This
// stub just mirrors that guaranteed-taken guard clause directly.
BOOL RefreshScreen(int) { return 0; }

// DisableEventSection's real ctor/dtor call SetCursor()/EnableWindow(g_hwnd, ...) -
// real but non-blocking WinAPI calls with no bearing on what
// DumpSongToPokeyStream()'s tests characterize (it's just a "show a wait
// cursor and disable input while recording" UI nicety). No-op here rather
// than linking GuiHelpers.cpp for a purely cosmetic effect.
DisableEventSection::DisableEventSection() {}
DisableEventSection::~DisableEventSection() {}
