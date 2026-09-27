#pragma once

// Runs an RMT script (doc/rmt_scripting.md) against the loaded program state
// - the same commands, format names, options and defaults as the Java port's
// ScriptRunner: open, save, export (the Export dialog's eight formats, each
// through the dialog-independent *Apply/exporter entry point with the
// parameters the dialog would have collected), set overwrite|ntsc|driver,
// echo, quit. Paths are relative to the script's folder. Exit codes: 0 =
// every command succeeded, 1 = a command failed (the script stops there),
// 2 = the script could not be read or parsed.
//
// Started from Rmt.exe /SCRIPT:<file> (CRmtApp::InitInstance) after the
// window exists but before it is shown: the register dump pumps the window's
// messages, so the window is needed, hidden. Message boxes go to the console
// (or a .log next to the script) - see SetScriptMessageMode() in Messages.h.

#include "Script.h"
#include "StdAfx.h"

#include <filesystem>

class CSong;

class CScriptRunner {
public:
    static const int EXIT_OK = 0;
    static const int EXIT_COMMAND_FAILED = 1;
    static const int EXIT_SCRIPT_INVALID = 2;

    explicit CScriptRunner(CSong& song);

    // Attaches the parent console, or redirects stdout/stderr to <script>.log;
    // reads, parses and runs the script; returns the exit code.
    int RunFile(const CString& scriptFilePath);

    // Runs parsed commands, resolving relative paths against baseFolder.
    int Run(const std::vector<TScriptCommand>& commands, const std::filesystem::path& baseFolder);

private:
    CSong& m_song;
    std::filesystem::path m_baseFolder;
    bool m_overwrite = false;

    // false for quit; throws CScriptError on a failure
    bool Execute(const TScriptCommand& command);
    void Open(const TScriptCommand& command);
    void Save(const TScriptCommand& command);
    void Export(const TScriptCommand& command);
    void Set(const TScriptCommand& command);

    std::filesystem::path Resolve(const std::string& path) const;
    void CheckOverwrite(const TScriptCommand& command, const std::filesystem::path& file) const;
    // The problem text of the message boxes shown during the command, "" for none.
    static std::string Problems();
};

// The console setup for a script run: AttachConsole(ATTACH_PARENT_PROCESS)
// when started from a console, else <scriptFile>.log. Public for InitInstance.
void AttachScriptConsole(const CString& scriptFilePath);
