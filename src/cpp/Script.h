#pragma once

// The RMT script format (doc/rmt_scripting.md) - the parser, shared by the
// C++ program and (as an equal implementation) the Java port: one command per
// line; '#' starts a comment; blank lines are ignored; tokens are separated
// by whitespace and may be quoted with "..." (the quotes are removed, blanks
// kept; \" and \\ are the two escapes); an unquoted token name=value (the
// name a letter followed by letters, digits, '-' or '_') is an option whose
// value may itself be quoted; everything else is a positional argument.
// Command and option names are case-insensitive (stored lower case); values
// keep their case. No MFC here, so it links into the test project as is.

#include <map>
#include <stdexcept>
#include <string>
#include <vector>

struct TScriptCommand {
    int line = 0; // 1-based, for messages
    std::string name; // lower case
    std::vector<std::string> arguments;
    std::map<std::string, std::string> options; // names lower case

    bool HasOption(const std::string& optionName) const;
    // The option's value, or defaultValue when the script did not give it.
    std::string GetOption(const std::string& optionName, const std::string& defaultValue) const;
    // The argument at index, or "" when there is none.
    std::string GetArgument(size_t index) const;
};

class CScriptError : public std::runtime_error {
public:
    CScriptError(int line, const std::string& message);
    int GetLine() const;
    // "line 3: message", or just the message for line 0 (the file as a whole).
    std::string GetLocatedMessage() const;

private:
    int m_line;
};

class CScriptParser {
public:
    static std::vector<TScriptCommand> Parse(const std::vector<std::string>& lines);
    // One line into a command; false for a blank/comment line. Throws CScriptError.
    static bool ParseLine(int lineNumber, const std::string& line, TScriptCommand& command);
    // The file's lines (any of CR LF, LF, CR as line ends).
    static std::vector<std::string> SplitLines(const std::string& text);
};
