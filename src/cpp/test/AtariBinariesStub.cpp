#include "StdAfx.h"

#include "Messages.h"
#include <filesystem>

// Real, simple globals/functions needed by AtariBinaries.cpp (already
// linked - LoadByteArray()/LoadResourceByteArray()/CRmtAtariBinaries have
// no coupling of their own beyond these) - GetResourceFilePath()/g_prgpath/
// SetProgramFolderPath() themselves live in the wider Global.cpp (not
// linked here), so their bodies are copied verbatim.
//
// g_prgpath is set once, at static-init time, to this repo's own checked-in
// rmt/ folder - computed from this file's own __FILE__ (stable as long as
// the repo isn't moved between building and running the test binary, both
// always done locally on the same machine here). CExportSAP_B_LZSS()/
// ExportXEX_LZSS() need a real resource file there
// (resources/players/vu_player_v2.obx) - the first real on-disk dependency
// in this test suite (see plans/SAP_LZSS_WAV_XEX_PLAN.md).

CString g_prgpath;

void SetProgramFolderPath(const CString& folderPath) {
    g_prgpath = folderPath;
}

CString GetResourceFilePath(const std::filesystem::path& relativeFolderPath, const CString& fileName) {
    std::filesystem::path path;
    path.append(g_prgpath.GetString());
    path.append(relativeFolderPath.c_str());
    path.append(fileName.GetString());
    return path.c_str();
}

namespace {
    struct InitProgramFolderPath {
        InitProgramFolderPath() {
            std::filesystem::path sourceFile(__FILE__);          // .../src/cpp/test/AtariBinariesStub.cpp
            std::filesystem::path repoRoot = sourceFile
                .parent_path()  // .../src/cpp/test
                .parent_path()  // .../src/cpp
                .parent_path()  // .../src
                .parent_path(); // repo root
            SetProgramFolderPath((repoRoot / "rmt").string().c_str());
        }
    } g_initProgramFolderPath;
}

// Real implementations for CSAPFileExporter::ExportSAP_B_LZSS()
// (SAPFileExporterCore.cpp) - copied verbatim from Messages.cpp (not
// linked here). g_statusBar defaults to nullptr and no test sets it, so
// the "if (g_statusBar == nullptr)" branch below is always taken - the
// MessageBox() branch is preserved as real code but is unreachable here,
// same treatment as CSongTimer::WaitForTimerRoutineProcessed()'s guard
// (see test/SongEditingStub.cpp).
extern HWND g_hwnd; // real global, see test/SongEditingStub.cpp

CStatusBar* g_statusBar = nullptr;

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
