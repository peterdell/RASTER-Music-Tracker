#pragma once

#include "General.h"
class CStatusBar;

// Helper defines to make the code a bit more readabl
#define SCALE(x) ((x) * g_scaling_percentage) / 100
#define INVERSE_SCALE(x) ((x) * 100) / g_scaling_percentage
#define SCREENUPDATE g_screenupdate = 1
#define NO_SCREENUPDATE g_screenupdate = 0


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

extern CStatusBar* g_statusBar;
extern void SetStatusBarText(const char* text);
extern void ClearStatusBar();
extern void SendErrorMessage(const char* title, const char* message);



extern BOOL RefreshScreen(int frameskip = 0);

extern int EditText(int vk, int shift, int control, char* txt, int& cur, int max);

extern BOOL IsHoveredXY(int x, int y, int xLength, int yLength);

extern void TextXY(const char* txt, int x, int y, int color = TEXT_COLOR_WHITE);
extern void TextXYFull(const char* txt, int& x, int& y);
extern void TextXYSelN(const char* txt, int n, int x, int y, int color = TEXT_COLOR_WHITE);
extern void TextXYCol(const char* txt, int x, int y, int acu, int color = TEXT_COLOR_WHITE);
extern void TextDownXY(const char* txt, int x, int y, int color = TEXT_COLOR_WHITE);
extern void NumberMiniXY(const BYTE num, int x, int y, int color = TEXT_MINI_COLOR_GRAY);
extern void TextMiniXY(const char* txt, int x, int y, int color = TEXT_MINI_COLOR_GRAY);
extern void IconMiniXY(const int icon, int x, int y);
