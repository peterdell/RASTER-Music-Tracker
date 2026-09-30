#pragma once

#include "General.h" // KeyboardLayout

extern char NoteKey(int vk); // 0xff if not mapped
extern char NumbKey(int vk);
extern char Numblock09Key(int vk);

// The note keys of the three layouts as a Markdown document ("### QWERTY", a
// Note/Keys table, "### AZERTY", ..., "### QWERTZ", ...) - doc/rmt_note_keys.md,
// written by "dump notekeys <file>" (the Java port writes the same text).
#include <string>
extern std::string NoteKeysTable();
// The QWERTY key at the position of vk on the layout's keyboard, for keys that
// mean a position (the Pokey Explorer's): the three letter rows, the ISO key
// discounted; + and - (0xBB/0xBD) and everything else stay themselves.
extern int ToQwertyPosition(int vk, KeyboardLayout layout);
