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

// Display info messae in the status bar or in the log.
extern void SendInfoMessage(const char* message);

// Display error message in a message box or in the the log. Optionally with title.
extern void SendErrorMessage(const char* message);
extern void SendErrorMessage(const char* title, const char* message);

extern BOOL RefreshScreen(int frameskip = 0);

extern int EditText(int vk, int shift, int control, char* txt, int& cur, int max);

extern BOOL IsHoveredXY(int x, int y, int xLength, int yLength);


// ----------------------------------------------------------------------------
// GUI color setup
// The text is defined in IDB_GFX as bitmap font in various colors
// Text color is defined as a vertical offset in the bitmap font file
enum class TextColor : int {
    WHITE = 0,
    GRAY = 1,
    YELLOW = 2,
    INVERSE_BLUE = 3,
    INVERSE_WHITE = 4,
    CYAN = 5,
    RED = 6,
    INVERSE_RED = 9,
    EXTRA = 10,
    GREEN = 11,
    DARK_GRAY = 12,
    BLUE = 13,
    TURQUOISE = 14
};


class LogicalTextColor {
public:

    static const TextColor SELECTED = TextColor::INVERSE_RED;		// Highlight color
    static const TextColor SELECTED_PROVE = TextColor::INVERSE_BLUE;		// Highlight color in PROVE mode
    static const TextColor HOVERED = TextColor::INVERSE_WHITE;	// Highlight color from cursor hover
};

enum class TextMiniColor : int {
    GRAY = 0,
    BLUE = 1,
    WHITE = 2,
    YELLOW = 3
};


extern void TextXY(const char* txt, int x, int y, TextColor color = TextColor::WHITE);
extern void TextXYFull(const char* txt, int& x, int& y);
extern void TextXYSelN(const char* txt, int n, int x, int y, TextColor color = TextColor::WHITE);
extern void TextXYCol(const char* txt, int x, int y, int acu, TextColor color = TextColor::WHITE);
extern void TextDownXY(const char* txt, int x, int y, TextColor color = TextColor::WHITE);
extern void NumberMiniXY(const BYTE num, int x, int y, TextMiniColor color = TextMiniColor::GRAY);
extern void TextMiniXY(const char* txt, int x, int y, TextMiniColor color = TextMiniColor::GRAY);
extern void IconMiniXY(const int icon, int x, int y);


class CCanvas {
public:
    CCanvas(int originX, int originY);

    int GetOriginX() const {
        return originX;
    }

    int GetOriginY() const {
        return originY;
    }

    void TextMiniAt(const char* txt, int row, int column, TextMiniColor color = TextMiniColor::GRAY);
    void FillSolidRect(int x, int y, int width, int height, COLORREF color);
private:
    int originX;
    int originY;
    int charWidth = 8;
    int charHeight = 8;
};

template <typename T>
class TypedComboBox : public CComboBox {
public:
    void AddItem(const T value, const CString& text) {
        const auto i = this->AddString(text);
        assert(i != CB_ERRSPACE);
        this->SetItemData(i, (DWORD_PTR)value);
    }

    void SetSelectedItem(const  T value) {
        for (int i = 0; i < this->GetCount(); i++) {
            if (this->GetItemData(i) == (DWORD_PTR)value) {
                this->SetCurSel(i);
                return;
            }
        }
        if (this->GetCount() > 0) {
            this->SetCurSel(0);
        }
    }

    T GetSelectedItem(const T& defaultItem) const {
        auto i = this->GetCurSel();
        if (i != CB_ERR) {
            return (T)this->GetItemData(i);
        }
        return defaultItem;
    }
};

