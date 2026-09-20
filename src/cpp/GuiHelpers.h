#pragma once

#include "StdAfx.h"
#include <cassert>

// Helper defines to make the code a bit more readable
#define SCALE(x) ((x) * g_scaling_percentage) / 100
#define INVERSE_SCALE(x) ((x) * 100) / g_scaling_percentage
#define SCREENUPDATE g_screenupdate = TRUE
#define NO_SCREENUPDATE g_screenupdate = FALSE


class DisableEventSection {
public:
    DisableEventSection();
    ~DisableEventSection();

private:
    static int eventsDisabledCounter;
    static HCURSOR oldCursor;

    static void DisableEvents();
    static void EnableEvents();
};

// Status bar handling.
extern void ClearStatusBar();
extern void SetStatusBarText(const char* text);

extern BOOL RefreshScreen(int frameskip = 0);

extern int EditText(int vk, int shift, int control, char* txt, int& cur, int max);

extern BOOL IsHoveredXY(int x, int y, int xLength, int yLength);


