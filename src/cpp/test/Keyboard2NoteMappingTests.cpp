#include "gtest/gtest.h"

#include "General.h"
#include "Keyboard2NoteMapping.h"

extern KeyboardLayout g_keyboard_layout;

class Keyboard2NoteMappingTest : public ::testing::Test {
  protected:
    void TearDown() override {
        g_keyboard_layout = KeyboardLayout::QWERTY; // restore default
    }
};

TEST_F(Keyboard2NoteMappingTest, NoteKeyUnmappedVirtualKeyReturnsMinusOne) {
    g_keyboard_layout = KeyboardLayout::QWERTY;
    EXPECT_EQ(NoteKey(0), -1);
}

TEST_F(Keyboard2NoteMappingTest, NoteKeyQwertyMapsZKeyToNoteZero) {
    // Virtual key 0x5A ('Z') maps to note 0x00 in the QWERTY layout table.
    g_keyboard_layout = KeyboardLayout::QWERTY;
    EXPECT_EQ(NoteKey(0x5A), (char)0x00);
}

TEST_F(Keyboard2NoteMappingTest, NoteKeyDiffersBetweenQwertyAndAzertyForSameKey) {
    // Virtual key 0x41 ('A') is unmapped in QWERTY but maps to 0x0C in AZERTY
    // (AZERTY swaps the A/Q row relative to QWERTY).
    g_keyboard_layout = KeyboardLayout::QWERTY;
    EXPECT_EQ(NoteKey(0x41), (char)0xFF);

    g_keyboard_layout = KeyboardLayout::AZERTY;
    EXPECT_EQ(NoteKey(0x41), (char)0x0C);
}

TEST_F(Keyboard2NoteMappingTest, NoteKeyQwertzIsQwertyByPosition) {
    g_keyboard_layout = KeyboardLayout::QWERTZ;
    EXPECT_EQ(NoteKey('Y'), (char)0x00); // C-1 bottom left
    EXPECT_EQ(NoteKey('Z'), (char)0x15); // A-2 under the 6
    EXPECT_EQ(NoteKey(0xBA), (char)0x1D); // u umlaut: F-3 (QWERTY [)
    EXPECT_EQ(NoteKey(0xBB), (char)0x1F); // +: G-3 (QWERTY ])
    EXPECT_EQ(NoteKey(0xC0), (char)0x0F); // o umlaut: D#2 (QWERTY ;)
    EXPECT_EQ(NoteKey(0xDD), (char)0x1E); // acute: F#3 (QWERTY =)
    EXPECT_EQ(NoteKey(0xBD), (char)0x10); // -: E-2 (QWERTY /)
    EXPECT_EQ(NoteKey(0xDB), (char)0xFF); // sharp s: unmapped like QWERTY -
    EXPECT_EQ(NoteKey(0xBF), (char)0xFF); // #: no QWERTY key there
    EXPECT_EQ(NoteKey('Q'), (char)0x0C); // the rest as QWERTY
}

TEST_F(Keyboard2NoteMappingTest, ToQwertyPositionMapsTheLetterRowsAndKeepsPlusAndMinus) {
    EXPECT_EQ(ToQwertyPosition('Z', KeyboardLayout::QWERTZ), 'Y');
    EXPECT_EQ(ToQwertyPosition('Y', KeyboardLayout::QWERTZ), 'Z');
    EXPECT_EQ(ToQwertyPosition(0xBB, KeyboardLayout::QWERTZ), 0xBB); // + stays the divisor key
    EXPECT_EQ(ToQwertyPosition(0xBD, KeyboardLayout::QWERTZ), 0xBD);
    EXPECT_EQ(ToQwertyPosition('A', KeyboardLayout::AZERTY), 'Q');
    EXPECT_EQ(ToQwertyPosition('Q', KeyboardLayout::AZERTY), 'A');
    EXPECT_EQ(ToQwertyPosition('W', KeyboardLayout::AZERTY), 'Z'); // the ISO key discounted
    EXPECT_EQ(ToQwertyPosition(0xBC, KeyboardLayout::AZERTY), 'M');
    EXPECT_EQ(ToQwertyPosition('Q', KeyboardLayout::QWERTY), 'Q');
    EXPECT_EQ(ToQwertyPosition(VK_RETURN, KeyboardLayout::QWERTZ), VK_RETURN); // not in the rows: itself
}

TEST_F(Keyboard2NoteMappingTest, NoteKeyReturnsMinusOneForUnknownLayout) {
    g_keyboard_layout = static_cast<KeyboardLayout>(3); // none of the three layouts
    EXPECT_EQ(NoteKey(0x5A), -1);
}

TEST(NumbKeyTest, MapsTopRowDigitsAndNumpadDigits) {
    EXPECT_EQ(NumbKey(0x30), (char)0); // top-row '0'
    EXPECT_EQ(NumbKey(0x39), (char)9); // top-row '9'
    EXPECT_EQ(NumbKey(0x60), (char)0); // VK_NUMPAD0
    EXPECT_EQ(NumbKey(0x69), (char)9); // VK_NUMPAD9
}

TEST(NumbKeyTest, UnmappedVirtualKeyReturnsMinusOne) {
    EXPECT_EQ(NumbKey(0), (char)-1);
    EXPECT_EQ(NumbKey(0x3A), (char)-1); // just past the top-row digits
}

TEST(Numblock09KeyTest, MapsNumpadDigitsOnly) {
    EXPECT_EQ(Numblock09Key(0x60), (char)0); // VK_NUMPAD0
    EXPECT_EQ(Numblock09Key(0x69), (char)9); // VK_NUMPAD9
    EXPECT_EQ(Numblock09Key(0x30), (char)-1); // top-row digits are NOT mapped here
}

TEST_F(Keyboard2NoteMappingTest, NoteKeysTableListsBothLayoutsWithTheirLegends) {
    std::string table = NoteKeysTable();
    EXPECT_EQ(table.rfind("### QWERTY\n\n```\n 1    2    3    4    5    6    7    8    9    0    -    =\n     C#2  D#2       F#2  G#2  A#2       C#3  D#3       F#3\n   Q    W    E", 0), 0u) << table;
    EXPECT_NE(table.find("       Z    X    C    V    B    N    M    ,    .    /\n      C-1  D-1  E-1  F-1  G-1  A-1  B-1  C-2  D-2  E-2\n```\n\n| Note | Keys |\n|---|---|\n| C-1 | `Z` |\n"), std::string::npos) << table;
    EXPECT_NE(table.find("| C-2 | `Q`, `,` |\n"), std::string::npos) << table; // two keys, an octave apart on the two rows
    EXPECT_NE(table.find("| F#3 | `=` |\n"), std::string::npos) << table; // an OEM key by its US legend
    EXPECT_NE(table.find("\n### AZERTY\n\n```\n &    \xC3\xA9    \"    '    (    -    \xC3\xA8    _    \xC3\xA7    \xC3\xA0    )    =\n"), std::string::npos) << table; // the French number row
    EXPECT_NE(table.find("| C-1 | `W` |\n"), std::string::npos) << table;
    EXPECT_NE(table.find("| C-2 | `A`, `;` |\n"), std::string::npos) << table; // the French legends
    EXPECT_NE(table.find("\n### QWERTZ\n\n```\n 1    2    3    4    5    6    7    8    9    0    \xC3\x9F    \xC2\xB4\n     C#2  D#2       F#2  G#2  A#2       C#3  D#3       F#3\n   Q    W    E    R    T    Z    U    I    O    P    \xC3\xBC    +\n  C-2  D-2  E-2  F-2  G-2  A-2  B-2  C-3  D-3  E-3  F-3  G-3\n"), std::string::npos) << table;
    EXPECT_NE(table.find("       <    Y    X    C    V    B    N    M    ,    .    -\n           C-1  D-1  E-1  F-1  G-1  A-1  B-1  C-2  D-2  E-2\n"), std::string::npos) << table; // the German bottom row
}
