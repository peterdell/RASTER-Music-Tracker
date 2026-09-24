package com.wudsn.tools.rmt.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Mirrors src/cpp/test/NotesTests.cpp (see Notes's own javadoc for the deliberately-preserved isValidNote() off-by-one). */
class NotesTest {

	@Test
	void isValidNoteRejectsNegative() {
		assertFalse(Notes.isValidNote(-1));
	}

	@Test
	void isValidNoteAcceptsRange() {
		assertTrue(Notes.isValidNote(0));
		assertTrue(Notes.isValidNote(60));
	}

	// Characterization test for existing (surprising) behavior: NOTESNUM is
	// documented as "Notes 0-60 inclusive" (61 values), but isValidNote()
	// compares with "<= NOTESNUM" (61) instead of "< NOTESNUM", so it also
	// accepts 61 - preserved deliberately, not fixed (see Notes's javadoc).
	@Test
	void isValidNoteAcceptsOneOffTheEndOfItsDocumentedRange() {
		assertTrue(Notes.isValidNote(61));
		assertFalse(Notes.isValidNote(62));
	}

	@Test
	void getNoteReturnsFirstAndLastOctaveOneNote() {
		assertEquals("C-1", Notes.getNote(0));
		assertEquals("B-1", Notes.getNote(11));
	}

	@Test
	void getNoteReturnsSixthOctaveC() {
		assertEquals("C-6", Notes.getNote(60));
	}

	@Test
	void getNoteAndScaleSharpNotation() {
		assertEquals("C#", Notes.getNoteAndScale(0, 1));
		assertEquals("B-", Notes.getNoteAndScale(0, 11));
	}

	@Test
	void getNoteAndScaleFlatNotation() {
		assertEquals("Db", Notes.getNoteAndScale(1, 1));
	}

	@Test
	void getNoteAndScaleGermanNotationUsesHInsteadOfB() {
		assertEquals("H-", Notes.getNoteAndScale(2, 11));
		assertEquals("H-", Notes.getNoteAndScale(3, 11));
	}
}
