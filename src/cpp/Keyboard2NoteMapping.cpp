#include "StdAfx.h"

#include "Keyboard2NoteMapping.h"

#include "General.h"

extern KeyboardLayout g_keyboard_layout;

//QWERTY keys layout
const unsigned char keynotes_QWERTY[256] =
    {
        0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF,
        0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF,
        0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF,
        0x1B, 0xFF, 0x0D, 0x0F, 0xFF, 0x12, 0x14, 0x16, 0xFF, 0x19, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF,
        0xFF, 0xFF, 0x07, 0x04, 0x03, 0x10, 0xFF, 0x06, 0x08, 0x18, 0x0A, 0xFF, 0x0D, 0x0B, 0x09, 0x1A,
        0x1C, 0x0C, 0x11, 0x01, 0x13, 0x17, 0x05, 0x0E, 0x02, 0x15, 0x00, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF,
        0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF,
        0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF,
        0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF,
        0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF,
        0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF,
        0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0x0F, 0x1E, 0x0C, 0xFF, 0x0E, 0x10,
        0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF,
        0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0x1D, 0xFF, 0x1F, 0xFF, 0xFF,
        0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF,
        0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF};

//AZERTY keys layout
const unsigned char keynotes_AZERTY[256] =
    {
        0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF,
        0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF,
        0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF,
        0x1B, 0xFF, 0x0D, 0x0F, 0xFF, 0x12, 0x14, 0x16, 0xFF, 0x19, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF,
        0xFF, 0x0C, 0x07, 0x04, 0x03, 0x10, 0xFF, 0x06, 0x08, 0x18, 0x0A, 0xFF, 0x0D, 0x0F, 0x09, 0x1A,
        0x1C, 0xFF, 0x11, 0x01, 0x13, 0x17, 0x05, 0x00, 0x02, 0x15, 0x0E, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF,
        0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF,
        0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF,
        0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF,
        0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF,
        0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF,
        0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0x1F, 0x1E, 0x0B, 0xFF, 0x0C, 0x0E,
        0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF,
        0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0x1D, 0xFF, 0x10,
        0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF,
        0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF};

/*
const char keynotes[256] =
{
    //0
    -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
    -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
    -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
    -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
    -1, -1, -1, -1, -1, -1, -1, -1, 27, -1,
    //50
    13, 15, -1, 18, 20, 22, -1, 25, -1, -1,
    -1, -1, -1, -1, -1, -1,  7,  4,  3, 16,
    -1,  6,  8, 24, 10, -1, 13, 11,  9, 26,
    28, 12, 17,  1, 19, 23,  5, 14,  2, 21,
    0, -1, -1, -1, -1, -1, -1, -1, -1, -1,
    //100
    -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
    -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
    -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
    -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
    -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
    //150
    -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
    -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
    -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
    -1, -1, -1, -1, -1, -1, 15, 30, 12, -1,
    14, 16, -1, -1, -1, -1, -1, -1, -1, -1,
    //200
    -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
    -1, -1, -1, -1, -1, -1, -1, -1, -1, 29,
    -1, 31, -1, -1, -1, -1, -1, -1, -1, -1,
    -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
    -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
    //250
    -1, -1, -1, -1, -1, -1
};
*/

const char keynumbs[256] =
    {
        //0
        -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
        -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
        -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
        0, 1, 2, 3, 4, 5, 6, 7, 8, 9, -1, -1, -1, -1, -1, -1,
        //64
        -1, 10, 11, 12, 13, 14, 15, -1, -1, -1, -1, -1, -1, -1, -1, -1,
        -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
        0, 1, 2, 3, 4, 5, 6, 7, 8, 9, -1, -1, -1, -1, -1, -1,
        -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
        //128
        -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
        -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
        -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
        -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
        //192
        -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
        -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
        -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
        -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1};

const char keynumblock09[256] =
    {
        //0
        -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
        -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
        -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
        -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
        //64
        -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
        -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
        0, 1, 2, 3, 4, 5, 6, 7, 8, 9, -1, -1, -1, -1, -1, -1,
        -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
        //128
        -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
        -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
        -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
        -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
        //192
        -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
        -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
        -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
        -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1};

char NoteKey(int vk) {
    if (g_keyboard_layout == KeyboardLayout::QWERTY) {
        return keynotes_QWERTY[vk];
    } else if (g_keyboard_layout == KeyboardLayout::AZERTY) {
        return keynotes_AZERTY[vk];
    } else {
        return -1;
    }
};

char NumbKey(int vk) {
    return keynumbs[vk];
};

char Numblock09Key(int vk) {
    return keynumblock09[vk];
};

#include "Notes.h"
#include <cstdio>
#include <vector>

namespace {

// The legend of a virtual key as the keyboard of the layout prints it: the
// letters are the same physical keys in both layouts; the number row of the
// French AZERTY keyboard prints & é " ' ( - è _ ç à (the digits are its
// Shift level), and the OEM keys differ.
std::string KeyLegend(int vk, KeyboardLayout layout) {
    bool azerty = layout == KeyboardLayout::AZERTY;
    if (vk >= '0' && vk <= '9') {
        static const char* azertyDigits[10] = { "\xC3\xA0", "&", "\xC3\xA9", "\"", "'", "(", "-", "\xC3\xA8", "_", "\xC3\xA7" }; // à & é " ' ( - è _ ç
        return azerty ? azertyDigits[vk - '0'] : std::string(1, (char)vk);
    }
    if (vk >= 'A' && vk <= 'Z') {
        return std::string(1, (char)vk);
    }
    switch (vk) {
    case 0xBA: return azerty ? "$" : ";"; // VK_OEM_1
    case 0xBB: return "="; // VK_OEM_PLUS
    case 0xBC: return ","; // VK_OEM_COMMA
    case 0xBD: return "-"; // VK_OEM_MINUS
    case 0xBE: return azerty ? ";" : "."; // VK_OEM_PERIOD
    case 0xBF: return azerty ? ":" : "/"; // VK_OEM_2
    case 0xC0: return azerty ? "\xC3\xB9" : "`"; // VK_OEM_3 (u with grave accent, UTF-8)
    case 0xDB: return azerty ? ")" : "["; // VK_OEM_4
    case 0xDC: return azerty ? "*" : "\\"; // VK_OEM_5
    case 0xDD: return azerty ? "^" : "]"; // VK_OEM_6
    case 0xDE: return azerty ? "\xC2\xB2" : "'"; // VK_OEM_7 (superscript two, UTF-8)
    case 0xDF: return "!"; // VK_OEM_8
    case 0xE2: return "<"; // VK_OEM_102
    default:
        break;
    }
    char buffer[16];
    snprintf(buffer, sizeof(buffer), "VK_%02X", vk);
    return buffer;
}

// The four key rows of the layout's keyboard, from the number row to the
// bottom row, as virtual keys (0 = the ISO "<" key position when absent).
const std::vector<std::vector<int>>& KeyboardRows(KeyboardLayout layout) {
    static const std::vector<std::vector<int>> qwerty = {
        { '1', '2', '3', '4', '5', '6', '7', '8', '9', '0', 0xBD, 0xBB },
        { 'Q', 'W', 'E', 'R', 'T', 'Y', 'U', 'I', 'O', 'P', 0xDB, 0xDD },
        { 'A', 'S', 'D', 'F', 'G', 'H', 'J', 'K', 'L', 0xBA, 0xDE },
        { 'Z', 'X', 'C', 'V', 'B', 'N', 'M', 0xBC, 0xBE, 0xBF },
    };
    static const std::vector<std::vector<int>> azerty = {
        { '1', '2', '3', '4', '5', '6', '7', '8', '9', '0', 0xDB, 0xBB },
        { 'A', 'Z', 'E', 'R', 'T', 'Y', 'U', 'I', 'O', 'P', 0xDD, 0xBA },
        { 'Q', 'S', 'D', 'F', 'G', 'H', 'J', 'K', 'L', 'M', 0xC0, 0xDC },
        { 0xE2, 'W', 'X', 'C', 'V', 'B', 'N', 0xBC, 0xBE, 0xBF, 0xDF },
    };
    return layout == KeyboardLayout::AZERTY ? azerty : qwerty;
}

// A UTF-8 string padded with blanks to width columns (one column per code point).
std::string Pad(const std::string& s, size_t width) {
    size_t columns = 0;
    for (unsigned char c : s) {
        if ((c & 0xC0) != 0x80) {
            columns++;
        }
    }
    return columns < width ? s + std::string(width - columns, ' ') : s;
}

// The keyboard as a picture: per row a line of key legends and a line with
// the note each key plays, the rows staggered as on a keyboard.
void AppendKeyboard(std::string& out, const unsigned char* table, KeyboardLayout layout) {
    const auto& rows = KeyboardRows(layout);
    out += "```\n";
    for (size_t r = 0; r < rows.size(); r++) {
        std::string keys(r * 2, ' ');
        std::string notes(r * 2, ' ');
        for (int vk : rows[r]) {
            keys += Pad(" " + KeyLegend(vk, layout), 5);
            notes += Pad(table[vk] != 0xFF ? CNotes::GetNote(table[vk]) : "", 5);
        }
        while (!keys.empty() && keys.back() == ' ') {
            keys.pop_back();
        }
        while (!notes.empty() && notes.back() == ' ') {
            notes.pop_back();
        }
        out += keys + "\n" + notes + "\n";
    }
    out += "```\n\n";
}

void AppendLayout(std::string& out, const char* name, const unsigned char* table, KeyboardLayout layout) {
    out += "### ";
    out += name;
    out += "\n\n";
    AppendKeyboard(out, table, layout);
    out += "| Note | Keys |\n|---|---|\n";
    for (int note = 0; note < CNotes::NOTESNUM; note++) {
        std::string keys;
        for (int vk = 0; vk < 256; vk++) {
            if (table[vk] == note) {
                if (!keys.empty()) {
                    keys += ", ";
                }
                keys += "`" + KeyLegend(vk, layout) + "`";
            }
        }
        if (!keys.empty()) {
            out += "| ";
            out += CNotes::GetNote(note);
            out += " | " + keys + " |\n";
        }
    }
}

} // namespace

std::string NoteKeysTable() {
    std::string out;
    AppendLayout(out, "QWERTY", keynotes_QWERTY, KeyboardLayout::QWERTY);
    out += "\n";
    AppendLayout(out, "AZERTY", keynotes_AZERTY, KeyboardLayout::AZERTY);
    return out;
}
