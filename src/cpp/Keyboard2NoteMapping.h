#pragma once

extern char NoteKey(int vk); // 0xff if not mapped
extern char NumbKey(int vk);
extern char Numblock09Key(int vk);

// The note keys of both layouts as a Markdown document ("### QWERTY", a
// Note/Keys table, "### AZERTY", ...) - doc/rmt_note_keys.md, written by
// "dump notekeys <file>" (the Java port writes the same text).
#include <string>
extern std::string NoteKeysTable();