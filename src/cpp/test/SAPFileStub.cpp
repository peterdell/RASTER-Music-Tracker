#include "Song.h"

// Link-only stubs: SAPFile.h includes Song.h (needed for CSAPFile::Init(),
// which takes a "const CSong&"), so the whole SAPFile.cpp translation unit
// needs these 4 CSong methods resolved at link time even though tests never
// call Init() and never construct a real CSong (which has its own heavy
// g_Atari-coupled constructor - see plans/NOTES.md).
CString CSong::GetName() const { return ""; }
bool CSong::IsStereo() const { return false; }
BOOL CSong::IsNTSC() const { return FALSE; }
int CSong::GetInstrumentSpeed() const { return 1; }
