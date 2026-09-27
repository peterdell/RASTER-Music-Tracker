#include "Messages.h"

#include "GuiHelpers.h"

// g_hwnd itself lives in the much wider Global.cpp/Global.h (not included
// here) - declared directly instead, same "declare individual externs,
// avoid Global.h" pattern as elsewhere (see plans/MESSAGEBOX_REFACTOR_PLAN.md).
// This is what lets this file link directly into the test project - no
// verbatim copies of these functions needed anywhere else anymore.
extern HWND g_hwnd;

CStatusBar* g_statusBar = nullptr;

void SendInfoMessage(const char* message) {
    SetStatusBarText(message);
}

namespace {
bool g_scriptMessageMode = false;
bool g_scriptInteractive = false; // script mode with the window: boxes shown, problems still collected
std::string g_scriptProblems;

// One line: "title: message" with the message's line ends flattened.
std::string OneLine(const char* title, const char* message) {
    std::string text = message ? message : "";
    for (char& c : text) {
        if (c == '\r' || c == '\n') {
            c = ' ';
        }
    }
    while (!text.empty() && text.back() == ' ') {
        text.pop_back();
    }
    return (title && *title ? std::string(title) + ": " : std::string()) + text;
}

// Shared by every fire-and-forget Send<Type>Message() below: logs
// instead of showing a real MessageBox whenever no real status bar/UI
// is present (i.e. in every test), so these call sites are always safe
// to trigger there. In script mode the console takes the box's place.
void SendMessageBox(const char* logPrefix, const char* title, const char* message, UINT icon) {
    if (g_scriptMessageMode) {
        std::string line = OneLine(title, message);
        if (icon != MB_ICONINFORMATION) {
            g_scriptProblems += line + "\n"; // fails the command, in both script modes
        }
        if (!g_scriptInteractive) {
            if (icon == MB_ICONINFORMATION) {
                printf("%s\n", line.c_str());
            } else {
                fprintf(stderr, "%s\n", line.c_str());
            }
            return;
        }
        // interactive (Tools > Run Script): the box is shown as always
    }
    if (g_statusBar == nullptr) {
        OutputDebugString(logPrefix);
        if (title) {
            OutputDebugString(title);
            OutputDebugString("\n");
        }

        OutputDebugString(message);
        OutputDebugString("\n");
    } else {
        MessageBox(g_hwnd, message, title, icon);
    }
}
} // namespace

void SendErrorMessage(const char* message) {
    SendErrorMessage(nullptr, message);
}

void SendErrorMessage(const char* title, const char* message) {
    SendMessageBox("ERROR: ", title, message, MB_ICONERROR);
}

void SendWarningMessage(const char* message) {
    SendWarningMessage(nullptr, message);
}

void SendWarningMessage(const char* title, const char* message) {
    SendMessageBox("WARNING: ", title, message, MB_ICONWARNING);
}

void SendInformationMessage(const char* message) {
    SendInformationMessage(nullptr, message);
}

void SendInformationMessage(const char* title, const char* message) {
    SendMessageBox("INFO: ", title, message, MB_ICONINFORMATION);
}

namespace {
MessageAnswer g_testQuestionAnswer = MessageAnswer::Cancel;
}

void SetTestQuestionAnswer(MessageAnswer answer) {
    g_testQuestionAnswer = answer;
}

void SetScriptMessageMode(bool enabled, bool interactive) {
    g_scriptMessageMode = enabled;
    g_scriptInteractive = enabled && interactive;
    g_scriptProblems.clear();
}

void ClearScriptProblems() {
    g_scriptProblems.clear();
}

std::string GetScriptProblems() {
    std::string result = g_scriptProblems;
    while (!result.empty() && result.back() == '\n') {
        result.pop_back();
    }
    return result;
}

MessageAnswer SendQuestionMessage(const char* title, const char* message, MessageButtons buttons) {
    if (g_scriptMessageMode && !g_scriptInteractive) {
        fprintf(stderr, "%s (a script answers No)\n", OneLine(title, message).c_str());
        return buttons == MessageButtons::OkCancel ? MessageAnswer::Cancel : MessageAnswer::No;
    }
    if (g_statusBar == nullptr) {
        OutputDebugString("QUESTION: ");
        if (title) {
            OutputDebugString(title);
            OutputDebugString("\n");
        }

        OutputDebugString(message);
        OutputDebugString("\n");
        return g_testQuestionAnswer;
    }

    UINT winButtons = MB_OKCANCEL;
    switch (buttons) {
    case MessageButtons::YesNo:
        winButtons = MB_YESNO;
        break;
    case MessageButtons::YesNoCancel:
        winButtons = MB_YESNOCANCEL;
        break;
    case MessageButtons::OkCancel:
        winButtons = MB_OKCANCEL;
        break;
    }

    int result = MessageBox(g_hwnd, message, title, winButtons | MB_ICONQUESTION);
    switch (result) {
    case IDYES:
        return MessageAnswer::Yes;
    case IDNO:
        return MessageAnswer::No;
    case IDOK:
        return MessageAnswer::Ok;
    case IDCANCEL:
    default:
        return MessageAnswer::Cancel;
    }
}
