#include "Song.h"

// SAPFile.h includes Song.h (needed for CSAPFile::Init(), which takes a
// "const CSong&"), so the whole SAPFile.cpp translation unit needs CSong's
// methods resolved at link time. These used to be link-only stubs here, but
// now that SongCore.cpp (linked directly, see RmtTests.vcxproj) provides the
// real - and equally cheap - implementations of GetName()/IsStereo()/
// IsNTSC()/GetInstrumentSpeed(), the stubs would just collide at link time.
