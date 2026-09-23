// Provides storage for g_tracks4_8 (declared via Global.h, which
// InstrumentsAtaFormat.cpp includes for exactly this global), so tests can
// read/write it directly to switch between mono/stereo envelope-volume
// packing without linking the rest of Global.cpp's global state.
int g_tracks4_8 = 4;

// CInstruments::ClearInstrument()/MemorizeOctaveAndVolume()/
// RememberOctaveAndVolume() used to be stubbed here too, but their only
// real dependencies (g_AtariTrackerDriver, via Update()'s InstrumentTurnOff();
// g_keyboard_RememberOctavesAndVolumes) turned out already real and linked
// (AtariTrackerDriverCore.cpp, SongEditingStub.cpp) once actually checked -
// see plans/BROADER_SURVEY_PLAN.md. Instruments.cpp is linked directly now
// (RmtTests.vcxproj), giving all four real behavior in tests
// (InstrumentsTests.cpp).
//
// CInstruments::Update() was likewise stubbed here once, until
// IO_Instruments.cpp turned out to need no Global.h dependency at all (only
// g_Atari, already safe) - see plans/SONG_IO_SONG_REMAINING_PLAN.md.
