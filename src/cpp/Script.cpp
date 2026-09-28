#include "Script.h"

#include <cctype>

namespace {

std::string ToLower(const std::string& s) {
    std::string result = s;
    for (char& c : result) {
        c = (char)std::tolower((unsigned char)c);
    }
    return result;
}

bool IsOptionName(const std::string& s) {
    if (s.empty() || !std::isalpha((unsigned char)s[0])) {
        return false;
    }
    for (size_t i = 1; i < s.size(); i++) {
        char c = s[i];
        if (!(std::isalnum((unsigned char)c) || c == '-' || c == '_')) {
            return false;
        }
    }
    return true;
}

struct TToken {
    std::string text;
    bool quoted = false; // started with a quote - never an option
    bool isOption = false;
    std::string optionName;
};

std::vector<TToken> Tokenize(int lineNumber, const std::string& line) {
    std::vector<TToken> tokens;
    size_t i = 0;
    size_t n = line.size();
    while (i < n) {
        char c = line[i];
        if (std::isspace((unsigned char)c)) {
            i++;
            continue;
        }
        if (c == '#') {
            break; // comment to the end of the line
        }
        TToken token;
        token.quoted = c == '"';
        std::string text;
        while (i < n && !std::isspace((unsigned char)line[i])) {
            c = line[i];
            if (c == '"') {
                i++;
                bool closed = false;
                while (i < n) {
                    char q = line[i];
                    if (q == '\\' && i + 1 < n && (line[i + 1] == '"' || line[i + 1] == '\\')) {
                        text += line[i + 1];
                        i += 2;
                        continue;
                    }
                    if (q == '"') {
                        closed = true;
                        i++;
                        break;
                    }
                    text += q;
                    i++;
                }
                if (!closed) {
                    throw CScriptError(lineNumber, "A closing quote is missing.");
                }
                continue;
            }
            if (c == '=' && !token.isOption && !token.quoted && IsOptionName(text)) {
                token.isOption = true;
                token.optionName = ToLower(text);
                text.clear();
                i++;
                continue;
            }
            text += c;
            i++;
        }
        token.text = text;
        tokens.push_back(token);
    }
    return tokens;
}

} // namespace

bool TScriptCommand::HasOption(const std::string& optionName) const {
    return options.find(optionName) != options.end();
}

std::string TScriptCommand::GetOption(const std::string& optionName, const std::string& defaultValue) const {
    auto it = options.find(optionName);
    return it != options.end() ? it->second : defaultValue;
}

std::string TScriptCommand::GetArgument(size_t index) const {
    return index < arguments.size() ? arguments[index] : std::string();
}

CScriptError::CScriptError(int line, const std::string& message) : std::runtime_error(message), m_line(line) {
}

int CScriptError::GetLine() const {
    return m_line;
}

std::string CScriptError::GetLocatedMessage() const {
    if (m_line > 0) {
        return "line " + std::to_string(m_line) + ": " + what();
    }
    return what();
}

std::vector<TScriptCommand> CScriptParser::Parse(const std::vector<std::string>& lines) {
    std::vector<TScriptCommand> commands;
    for (size_t i = 0; i < lines.size(); i++) {
        TScriptCommand command;
        if (ParseLine((int)i + 1, lines[i], command)) {
            commands.push_back(command);
        }
    }
    return commands;
}

bool CScriptParser::ParseLine(int lineNumber, const std::string& line, TScriptCommand& command) {
    std::vector<TToken> tokens = Tokenize(lineNumber, line);
    if (tokens.empty()) {
        return false;
    }
    const TToken& first = tokens[0];
    if (first.quoted || first.isOption) {
        throw CScriptError(lineNumber, "A command name is expected at the start of the line.");
    }
    command = TScriptCommand();
    command.line = lineNumber;
    command.name = ToLower(first.text);
    for (size_t t = 1; t < tokens.size(); t++) {
        const TToken& token = tokens[t];
        if (token.isOption) {
            if (command.options.find(token.optionName) != command.options.end()) {
                throw CScriptError(lineNumber, "The option '" + token.optionName + "' is given twice.");
            }
            command.options[token.optionName] = token.text;
        } else {
            command.arguments.push_back(token.text);
        }
    }
    return true;
}

std::vector<std::string> CScriptParser::SplitLines(const std::string& text) {
    std::vector<std::string> lines;
    std::string current;
    for (size_t i = 0; i < text.size(); i++) {
        char c = text[i];
        if (c == '\r') {
            lines.push_back(current);
            current.clear();
            if (i + 1 < text.size() && text[i + 1] == '\n') {
                i++;
            }
        } else if (c == '\n') {
            lines.push_back(current);
            current.clear();
        } else {
            current += c;
        }
    }
    if (!current.empty()) {
        lines.push_back(current);
    }
    return lines;
}
