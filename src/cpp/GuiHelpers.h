#pragma once

#include "StdAfx.h"
#include <cassert>

// Helper defines to make the code a bit more readable
#define SCALE(x) ((x) * g_scaling_percentage) / 100
#define INVERSE_SCALE(x) ((x) * 100) / g_scaling_percentage
#define SCREENUPDATE g_screenupdate = TRUE
#define NO_SCREENUPDATE g_screenupdate = FALSE

class CSong;

// The export guard (plans/24_EXPORT_SCREEN_UPDATES_PLAN.md, B): for the
// duration of an export the window takes no input and shows the wait
// cursor, and the song timer thread is stopped - the exporters drive the
// tracker driver and the POKEY themselves (the register dump in quick
// mode, the WAV rendering), and the timer rendering through the same POKEY
// in between crashed the WAV export before. On leaving, the timer is
// re-armed, the status bar cleared and the screen refreshed once. Nests.
class CExportSection {
public:
    explicit CExportSection(CSong& song);
    ~CExportSection();

private:
    static int s_depth;
    static HCURSOR s_oldCursor;
    CSong& m_song;
};

// Status bar handling.
extern void ClearStatusBar();
extern void SetStatusBarText(const char* text);

extern BOOL RefreshScreen(int frameskip = 0);

extern int EditText(int vk, int shift, int control, char* txt, int& cur, int max);

extern BOOL IsHoveredXY(int x, int y, int xLength, int yLength);
