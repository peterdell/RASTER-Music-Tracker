#include "Stdafx.h"

#include "Notes.h"

static const char* notesAndScales[5][40] =
{
    // Standard Western Notation, Sharp (#) accidentals 
    { "C-", "C#", "D-", "D#", "E-", "F-", "F#", "G-", "G#", "A-", "A#", "B-" },

    // Standard Western Notation, Flat (b) accidentals 
    { "C-", "Db", "D-", "Eb", "E-", "F-", "Gb", "G-", "Ab", "A-", "Bb", "B-" },

    // German Notation, Sharp (#) accidentals 
    { "C-", "C#", "D-", "D#", "E-", "F-", "F#", "G-", "G#", "A-", "A#", "H-" },

    // German Notation, Flat (b) accidentals 
    { "C-", "Db", "D-", "Eb", "E-", "F-", "Gb", "G-", "Ab", "A-", "B-", "H-" },

    // Test Notation
    { "1-", "2-", "3-", "4-", "5-", "6-", "7-", "8-", "9-", "A-", "B-", "C-",
    "D-", "E-", "F-", "G-", "H-", "I-", "J-", "K-", "L-", "M-", "N-", "O-",
    "P-", "Q-", "R-", "S-", "T-", "U-", "V-", "W-", "X-", "Y-", "Z-" }
};

static const char* notes[] =
{ "C-1","C#1","D-1","D#1","E-1","F-1","F#1","G-1","G#1","A-1","A#1","B-1",
  "C-2","C#2","D-2","D#2","E-2","F-2","F#2","G-2","G#2","A-2","A#2","B-2",
  "C-3","C#3","D-3","D#3","E-3","F-3","F#3","G-3","G#3","A-3","A#3","B-3",
  "C-4","C#4","D-4","D#4","E-4","F-4","F#4","G-4","G#4","A-4","A#4","B-4",
  "C-5","C#5","D-5","D#5","E-5","F-5","F#5","G-5","G#5","A-5","A#5","B-5",
  "C-6","???","???","???"
};

bool CNotes::IsValidNote(Note note) {
    return (note >= 0) && (note <= NOTESNUM);
 }

const char* CNotes::GetNoteAndScale(Notation notation, Note note) {
    return notesAndScales[notation][note];
}

const char* CNotes::GetNote(Note note) {
    return notes[note];
}

