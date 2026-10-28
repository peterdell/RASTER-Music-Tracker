// CInstruments::ClearInstrument()/MemorizeOctaveAndVolume()/
// RememberOctaveAndVolume() used to be stubbed here too, but their only
// real dependencies (g_AtariTrackerDriver, via Update()'s InstrumentTurnOff();
// g_keyboard_RememberOctavesAndVolumes) turned out already real and linked
// (AtariTrackerDriverCore.cpp, SongEditingStub.cpp) once actually checked -
// see plans/04_BROADER_SURVEY_PLAN.md. Instruments.cpp is linked directly now
// (RmtTests.vcxproj), giving all four real behavior in tests
// (InstrumentsTests.cpp).
//
// CInstruments::Update() was likewise stubbed here once, until
// IO_Instruments.cpp turned out to need no Global.h dependency at all (only
// g_Atari, already safe) - see plans/01_SONG_IO_SONG_REMAINING_PLAN.md.
