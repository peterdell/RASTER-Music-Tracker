#include "gtest/gtest.h"

#include "Notes.h"

TEST(NotesTest, IsValidNoteRejectsNegative) {
    EXPECT_FALSE(CNotes::IsValidNote(-1));
}

TEST(NotesTest, IsValidNoteAcceptsRange) {
    EXPECT_TRUE(CNotes::IsValidNote(0));
    EXPECT_TRUE(CNotes::IsValidNote(60));
}

// Characterization test for existing (surprising) behavior: NOTESNUM is
// documented as "Notes 0-60 inclusive" (61 values), but IsValidNote() compares
// with "<= NOTESNUM" (61) instead of "< NOTESNUM", so it also accepts 61. This
// is off by one relative to the documented range; flagged here rather than
// silently fixed, since GetNote()'s backing array happens to have padding
// entries that make note 61 not crash, so nothing currently visibly breaks.
TEST(NotesTest, IsValidNoteAcceptsOneOffTheEndOfItsDocumentedRange) {
    EXPECT_TRUE(CNotes::IsValidNote(61));
    EXPECT_FALSE(CNotes::IsValidNote(62));
}

TEST(NotesTest, GetNoteReturnsFirstAndLastOctaveOneNote) {
    EXPECT_STREQ(CNotes::GetNote(0), "C-1");
    EXPECT_STREQ(CNotes::GetNote(11), "B-1");
}

TEST(NotesTest, GetNoteReturnsSixthOctaveC) {
    EXPECT_STREQ(CNotes::GetNote(60), "C-6");
}

TEST(NotesTest, GetNoteAndScaleSharpNotation) {
    EXPECT_STREQ(CNotes::GetNoteAndScale(0, 1), "C#");
    EXPECT_STREQ(CNotes::GetNoteAndScale(0, 11), "B-");
}

TEST(NotesTest, GetNoteAndScaleFlatNotation) {
    EXPECT_STREQ(CNotes::GetNoteAndScale(1, 1), "Db");
}

TEST(NotesTest, GetNoteAndScaleGermanNotationUsesHInsteadOfB) {
    EXPECT_STREQ(CNotes::GetNoteAndScale(2, 11), "H-");
    EXPECT_STREQ(CNotes::GetNoteAndScale(3, 11), "H-");
}
