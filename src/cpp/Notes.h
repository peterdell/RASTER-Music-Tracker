#pragma once

#include "StdAfx.h"

typedef int Notation;
typedef int Note;

class CNotes {
public:
    static constexpr int NOTESNUM = 61; // Notes 0-60 inclusive

    // KNOWN BUG, deliberately not fixed: this accepts note == 61, one past
    // the documented "0-60 inclusive" range above (should be "< NOTESNUM",
    // not "<= NOTESNUM"). It's live production logic (called from
    // Tracks.cpp/InstrumentsCore.cpp/IO_Tracks.cpp/SongEditing.cpp via
    // CTracks::IsValidNote's delegation), so fixing it needs its own
    // investigation of every call site first - see
    // test/NotesTests.cpp::IsValidNoteAcceptsOneOffTheEndOfItsDocumentedRange
    // and the Java port's com.wudsn.tools.rmt.model.Notes, which preserves
    // this exact behavior deliberately, not by omission.
    static bool IsValidNote(Note note);

    static const char* GetNoteAndScale(Notation notation, Note note);
    static const char* GetNote(Note note);
};