#include "General.h"

// Storage for g_keyboard_layout (declared extern in Keyboard2NoteMapping.cpp),
// so tests can flip between QWERTY/AZERTY/neither without linking the rest
// of Global.cpp.
KeyboardLayout g_keyboard_layout = KeyboardLayout::QWERTY;
