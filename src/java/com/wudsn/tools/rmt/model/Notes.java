package com.wudsn.tools.rmt.model;

/**
 * Ported from CNotes (src/cpp/Notes.h/.cpp) - a stateless collection of
 * static note-name lookups, so this becomes a non-instantiable Java class
 * with all-static methods rather than an object with instance state.
 *
 * <p><b>{@link #isValidNote} has a known off-by-one bug, deliberately
 * preserved, not fixed</b>: NOTESNUM is documented as "Notes 0-60 inclusive"
 * (61 values), but the check accepts note == 61 too. Unlike Fraction's dead
 * {@code operator==} bug, this predicate is live production logic (called
 * from Tracks.cpp/InstrumentsCore.cpp/IO_Tracks.cpp/SongEditing.cpp via
 * CTracks::IsValidNote's delegation), so fixing it would be a real behavior
 * change requiring its own investigation of every call site - out of scope
 * for this port, per an explicit decision to preserve current behavior
 * faithfully instead.
 */
public final class Notes {

	public static final int NOTESNUM = 61; // Notes 0-60 inclusive

	private static final String[][] NOTES_AND_SCALES = {
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
					"P-", "Q-", "R-", "S-", "T-", "U-", "V-", "W-", "X-", "Y-", "Z-" }, };

	private static final String[] NOTES = {
			"C-1", "C#1", "D-1", "D#1", "E-1", "F-1", "F#1", "G-1", "G#1", "A-1", "A#1", "B-1",
			"C-2", "C#2", "D-2", "D#2", "E-2", "F-2", "F#2", "G-2", "G#2", "A-2", "A#2", "B-2",
			"C-3", "C#3", "D-3", "D#3", "E-3", "F-3", "F#3", "G-3", "G#3", "A-3", "A#3", "B-3",
			"C-4", "C#4", "D-4", "D#4", "E-4", "F-4", "F#4", "G-4", "G#4", "A-4", "A#4", "B-4",
			"C-5", "C#5", "D-5", "D#5", "E-5", "F-5", "F#5", "G-5", "G#5", "A-5", "A#5", "B-5",
			"C-6", "???", "???", "???", };

	private Notes() {
	}

	public static boolean isValidNote(int note) {
		return (note >= 0) && (note <= NOTESNUM);
	}

	public static String getNoteAndScale(int notation, int note) {
		return NOTES_AND_SCALES[notation][note];
	}

	public static String getNote(int note) {
		return NOTES[note];
	}
}
