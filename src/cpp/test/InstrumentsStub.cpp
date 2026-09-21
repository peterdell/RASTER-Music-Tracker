// Provides storage for g_tracks4_8 (declared via Global.h, which
// InstrumentsAtaFormat.cpp includes for exactly this global), so tests can
// read/write it directly to switch between mono/stereo envelope-volume
// packing without linking the rest of Global.cpp's global state.
int g_tracks4_8 = 4;

#include "Instruments.h"

// Link-only stub: the real CInstruments::ClearInstrument() lives in
// Instruments.cpp and needs g_AtariTrackerDriver/g_Atari (via Update()),
// which this test project deliberately doesn't link. It's only reachable
// here through InitInstruments(), which tests don't call (they set up
// TInstrument fields directly instead), but InitInstruments() is compiled
// into the same translation unit as the rest of InstrumentsCore.cpp, so the
// symbol still needs to exist to link.
void CInstruments::ClearInstrument(int) {}
