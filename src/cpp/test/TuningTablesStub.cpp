#include "StdAfx.h"

// Real, linked definition for CTuning::GenerateTable()/InitTuning() (see
// ..\TuningTables.cpp, now linked directly - see TuningTests.cpp for why
// this is safe: as long as a test sets g_tuning.basetuning to something
// nonzero before calling InitTuning(), its MessageBox+exit(1) guard is
// never reached). Global.cpp itself isn't linked here (it pulls in the
// live timer, MIDI, real Atari hardware access), so this one global needs
// its own definition, matching g_tuning/g_tuningRatios's own treatment in
// SongEditingStub.cpp.
int g_notesperoctave = 12;
