#include "Tuning.h"

// Link-only stub: the real CTuning::InitTuning() lives in TuningTables.cpp and
// reads global tuning state (see that file's header comment for why), which
// this test project deliberately doesn't link. Tests use CTuning's test-only
// clockFrequency constructor instead and never call InitTuning(), but
// Tuning.cpp's two-arg InitTuning(clockFrequency, table_memory) overload still
// references the no-arg one at link time, so this empty body satisfies the
// linker without pulling in Global.h's dependency graph.
void CTuning::InitTuning() {}
