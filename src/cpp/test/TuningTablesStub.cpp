#include "StdAfx.h"

// Real, linked definition for CTuning::GenerateTable()/InitTuning() (see
// ..\TuningTables.cpp, now linked directly - see TuningTests.cpp for why
// this is safe: as long as a test sets the song's base tuning to something
// nonzero before calling InitTuning(), its MessageBox+exit(1) guard is
// never reached). Global.cpp itself isn't linked here (it pulls in the
// live timer, MIDI, real Atari hardware access), so this one global needs
// its own definition, unlike the tuning settings, which CSong owns now
// (plans/44_SONG_PROPERTIES_DIALOG_PLAN.md).
int g_notesperoctave = 12;
