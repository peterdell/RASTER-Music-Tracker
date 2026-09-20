#include "Messages.h"

#include "GuiHelpers.h"

#include "Global.h"

CStatusBar* g_statusBar = nullptr;

void SendInfoMessage(const char* message) {
    SetStatusBarText(message);
}

void SendErrorMessage(const char* message) {
    SendErrorMessage(nullptr, message);
}

void SendErrorMessage(const char* title, const char* message) {
    if (g_statusBar == nullptr) {
        OutputDebugString("ERROR: ");
        if (title) {
            OutputDebugString(title);
            OutputDebugString("\n");
        }

        OutputDebugString(message);
        OutputDebugString("\n");
    }
    else {
        MessageBox(g_hwnd, message, title, MB_ICONERROR);
    }
}


