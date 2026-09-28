#include "StdAfx.h"

#include "Atari.h"
#include "C6502.h"
#include "Tuning.h"

// Link-only stubs. CAtari::Init()/DeInit()/JSR() call these real C6502
// methods, but C6502::Init() actually loads "sa_c6502.dll" via LoadLibrary()
// and shows a blocking MessageBox if it's missing - a real hazard for a test
// binary, not just an untested dependency - so tests here never call
// CAtari::Init()/DeInit()/JSR(), only the pure memory-buffer methods. These
// stub bodies just need to exist for the linker.
int C6502::Init(byte*) {
	return 0;
}
void C6502::DeInit() {
}
void C6502::JSR(C6502::Address&, C6502::Register&, C6502::Register&, C6502::Register&, C6502::CycleCount&) {
}

// Real, default-constructed CTuning (cheap and safe - see TuningTests.cpp).
// CAtari::Init(bool) calls g_Tuning.InitTuning(...), but tests never call
// that overload either (it would hit the same MessageBox+exit(1) guard
// characterized for CTuning::InitTuning() earlier if g_tuning.basetuning is
// still 0, which it is by default), so this only needs to exist for linking.
CTuning g_Tuning;

// Real, default-constructed CAtari (cheap and safe - see AtariTest.* above).
// SongCore.cpp's CSong constructor takes its address for
// "new CPokeyController(&g_Atari)".
CAtari g_Atari;
