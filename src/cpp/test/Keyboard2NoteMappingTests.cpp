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

TEST_F(Keyboard2NoteMappingTest, NoteKeyReturnsMinusOneForUnknownLayout) {
    g_keyboard_layout = static_cast<KeyboardLayout>(2); // neither QWERTY nor AZERTY
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
