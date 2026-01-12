#pragma once

#include "StdAfx.h"


typedef int Notation;
typedef int Note;

class CNotes {
public:

    static constexpr int NOTESNUM = 61; // Notes 0-60 inclusive

    static bool IsValidNote(Note note);

    static const char* GetNoteAndScale(Notation notation, Note note);
    static const char* GetNote(Note note);

};