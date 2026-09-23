// Provides storage for the g_Undo global (declared via Undo.h), which
// Global.cpp normally defines - deliberately not linked wholesale by this
// test project (see plans/BROADER_SURVEY_PLAN.md).
//
// CUndo's own methods used to be stubbed here too: Init/Clear/DeleteEvent/
// GetUndoSteps/GetRedoSteps/DropLast/Separator/PosIsEqual were verbatim
// copies (they touch no globals), and InsertEvent/PerformEvent/Undo/Redo/
// ChangeTrack/ChangeSong/ChangeInstrument/ChangeInfo were no-op stubs
// (their real bodies need g_Song/g_Tracks/g_Instruments, which weren't yet
// confirmed safe here). All of that has since been investigated and found
// safe - see plans/UNDO_PLAN.md - so the whole class links for real now
// (..\Undo.cpp in RmtTests.vcxproj) and no method stubs are needed at all.

#include "Undo.h"

CUndo g_Undo;
