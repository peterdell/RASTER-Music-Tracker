package com.wudsn.tools.rmt.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Mirrors src/cpp/test/TracksTests.cpp's already-tested subset (see
 * Tracks's own javadoc for what's deferred and why).
 */
class TracksTest {

	private static final int TRACK_A = 0;
	private static final int TRACK_B = 1;

	private Tracks tracks;

	@BeforeEach
	void setUp() {
		tracks = new Tracks();
		tracks.initTracks();
	}

	@Test
	void newlyInitializedTrackIsEmpty() {
		assertTrue(tracks.isEmptyTrack(TRACK_A));
	}

	@Test
	void clearTrackResetsFieldsToInvalid() {
		Track tr = tracks.getTrack(TRACK_A);
		tr.note[0] = 5;
		tr.volume[0] = 10;
		tracks.clearTrack(TRACK_A);
		assertEquals(-1, tracks.getNote(TRACK_A, 0));
		assertEquals(-1, tracks.getVol(TRACK_A, 0));
	}

	@Test
	void insertLineShiftsSubsequentLinesDownAndClearsInsertedLine() {
		Track tr = tracks.getTrack(TRACK_A);
		tr.len = 4;
		tr.note[0] = 1;
		tr.note[1] = 2;
		tr.note[2] = 3;

		tracks.insertLine(TRACK_A, 1);

		assertEquals(1, tr.note[0]);
		assertEquals(-1, tr.note[1]);
		assertEquals(2, tr.note[2]);
		assertEquals(3, tr.note[3]);
	}

	@Test
	void deleteLineShiftsSubsequentLinesUpAndClearsLastLine() {
		Track tr = tracks.getTrack(TRACK_A);
		tr.len = 4;
		tr.note[0] = 1;
		tr.note[1] = 2;
		tr.note[2] = 3;
		tr.note[3] = 4;

		tracks.deleteLine(TRACK_A, 1);

		assertEquals(1, tr.note[0]);
		assertEquals(3, tr.note[1]);
		assertEquals(4, tr.note[2]);
		assertEquals(-1, tr.note[3]);
	}

	@Test
	void calculateNotEmptyDetectsNoteData() {
		assertFalse(tracks.calculateNotEmpty(TRACK_A));
		tracks.getTrack(TRACK_A).note[0] = 5;
		assertTrue(tracks.calculateNotEmpty(TRACK_A));
	}

	@Test
	void compareTracksDetectsDifference() {
		assertTrue(tracks.compareTracks(TRACK_A, TRACK_B)); // both freshly cleared, identical
		tracks.getTrack(TRACK_B).note[0] = 3;
		assertFalse(tracks.compareTracks(TRACK_A, TRACK_B));
	}

	// Characterizes a moderately intricate cleanup pass: a "volume 0" line with no
	// note/instrument sitting directly between two other zero-volume lines is
	// redundant and gets cancelled (set to -1), while a zero-volume line that
	// *does* carry a note/instrument (line 1 here) is dropped once a second
	// zero-volume line closes the gap after it.
	@Test
	void trackOptimizeVol0RemovesRedundantZeroVolumeEntries() {
		Track tr = tracks.getTrack(TRACK_A);
		tr.len = 4;
		tr.volume[0] = 0;
		tr.note[1] = 5;
		tr.instr[1] = 0;
		tr.volume[1] = 0;
		tr.volume[2] = 0;

		tracks.trackOptimizeVol0(TRACK_A);

		assertEquals(0, tr.volume[0]);
		assertEquals(-1, tr.note[1]);
		assertEquals(-1, tr.instr[1]);
		assertEquals(-1, tr.volume[1]);
		assertEquals(-1, tr.volume[2]);
	}

	@Nested
	class ModifiedValueTest {

		@Test
		void getModifiedNoteTransposesWithinRange() {
			assertEquals(8, tracks.getModifiedNote(5, 3));
		}

		@Test
		void getModifiedNoteWrapsBelowZero() {
			assertEquals(11, tracks.getModifiedNote(0, -1));
		}

		@Test
		void getModifiedNoteWrapsAboveNotesnum() {
			assertEquals(52, tracks.getModifiedNote(59, 5));
		}

		@Test
		void getModifiedNoteRejectsInvalidNote() {
			assertEquals(-1, tracks.getModifiedNote(-1, 3));
		}

		@Test
		void getModifiedInstrWrapsAroundInstrsnum() {
			assertEquals(63, tracks.getModifiedInstr(0, -1));
			assertEquals(4, tracks.getModifiedInstr(63, 5));
		}

		@Test
		void getModifiedVolumePScalesAndClamps() {
			assertEquals(10, tracks.getModifiedVolumeP(10, 100));
			assertEquals(5, tracks.getModifiedVolumeP(10, 50));
			assertEquals(15, tracks.getModifiedVolumeP(15, 200)); // clamped to MAXVOLUME
			assertEquals(-1, tracks.getModifiedVolumeP(-1, 100));
			assertEquals(0, tracks.getModifiedVolumeP(10, 0));
		}
	}

	// trackToAta()/ataToTrack() round-trip the compact on-Atari track encoding.
	// Expected byte values were hand-derived from the format comments in
	// IO_Tracks.cpp, then confirmed to round-trip back to the original track data.
	@Nested
	class TrackAtaFormatTest {

		@Test
		void singleNoteEncodesAndDecodesRoundTrip() {
			Track src = tracks.getTrack(TRACK_A);
			src.len = 1;
			src.note[0] = 0;
			src.instr[0] = 0;
			src.volume[0] = 10;

			byte[] buffer = new byte[16];
			int size = tracks.trackToAta(TRACK_A, buffer);

			assertEquals(3, size);
			assertEquals((byte) 0x80, buffer[0]);
			assertEquals((byte) 0x02, buffer[1]);
			assertEquals((byte) 0xFF, buffer[2]); // end marker

			assertTrue(tracks.ataToTrack(buffer, size, TRACK_B));
			Track decoded = tracks.getTrack(TRACK_B);
			assertEquals(1, decoded.len);
			assertEquals(0, decoded.note[0]);
			assertEquals(0, decoded.instr[0]);
			assertEquals(10, decoded.volume[0]);
		}

		@Test
		void leadingPauseThenNoteEncodesAndDecodesRoundTrip() {
			Track src = tracks.getTrack(TRACK_A);
			src.len = 2;
			// Line 0 stays fully empty (a 1-beat pause); line 1 has a note.
			src.note[1] = 5;
			src.instr[1] = 2;
			src.volume[1] = 7;

			byte[] buffer = new byte[16];
			int size = tracks.trackToAta(TRACK_A, buffer);

			assertEquals(4, size);
			assertEquals((byte) 0x7E, buffer[0]); // 1-beat pause
			assertEquals((byte) 0xC5, buffer[1]);
			assertEquals((byte) 0x09, buffer[2]);
			assertEquals((byte) 0xFF, buffer[3]); // end marker

			assertTrue(tracks.ataToTrack(buffer, size, TRACK_B));
			Track decoded = tracks.getTrack(TRACK_B);
			assertEquals(2, decoded.len);
			assertEquals(-1, decoded.note[0]);
			assertEquals(5, decoded.note[1]);
			assertEquals(2, decoded.instr[1]);
			assertEquals(7, decoded.volume[1]);
		}
	}

	// trackBuildLoop() searches for the earliest/shortest repeating suffix (at
	// least 2 lines, matching an earlier segment, with more than 1 non-empty
	// line inside it) and, if found, truncates the track into a loop instead.
	// Expected values reuse the exact golden-master values already captured
	// on the C++ side (TrackBuildLoopTest in TracksTests.cpp).
	@Nested
	class TrackBuildLoopTest {

		@BeforeEach
		void setMaxLength() {
			tracks.setMaxTrackLength(4);
		}

		@Test
		void returnsZeroForEmptyTrack() {
			Track tr = tracks.getTrack(TRACK_A);
			tr.len = 4;
			assertEquals(0, tracks.trackBuildLoop(TRACK_A));
		}

		@Test
		void returnsZeroWhenTrackAlreadyHasALoop() {
			Track tr = tracks.getTrack(TRACK_A);
			tr.len = 4;
			tr.go = 0;
			tr.note[0] = 5; // non-empty, so the empty-track guard doesn't short-circuit first
			assertEquals(0, tracks.trackBuildLoop(TRACK_A));
		}

		@Test
		void returnsZeroWhenNotFullLength() {
			Track tr = tracks.getTrack(TRACK_A);
			tr.len = 3; // maxTrackLength is 4
			tr.note[0] = 5;
			assertEquals(0, tracks.trackBuildLoop(TRACK_A));
		}

		// Lines 2-3 exactly repeat lines 0-1 (2 distinct non-empty lines in the
		// repeated segment), so a 2-line loop back to line 0 should be found.
		@Test
		void findsASimpleRepeatingSuffix() {
			Track tr = tracks.getTrack(TRACK_A);
			tr.len = 4;
			tr.note[0] = 5;
			tr.instr[0] = 0;
			tr.volume[0] = 10;
			tr.note[1] = 6;
			tr.instr[1] = 0;
			tr.volume[1] = 10;
			tr.note[2] = 5;
			tr.instr[2] = 0;
			tr.volume[2] = 10;
			tr.note[3] = 6;
			tr.instr[3] = 0;
			tr.volume[3] = 10;

			assertEquals(2, tracks.trackBuildLoop(TRACK_A));
			assertEquals(2, tr.len);
			assertEquals(0, tr.go);
		}

		@Test
		void returnsZeroWhenNoRepeatingPatternExists() {
			Track tr = tracks.getTrack(TRACK_A);
			tr.len = 4;
			tr.note[0] = 1;
			tr.note[1] = 2;
			tr.note[2] = 3;
			tr.note[3] = 4;

			assertEquals(0, tracks.trackBuildLoop(TRACK_A));
		}

		// Lines 1-4 are all fully empty and exactly match each other, but the
		// only non-empty line (line 0) falls outside every candidate loop
		// region, so no candidate ever has more than 1 non-empty line inside
		// it - the "not just 0-1 nonzero lines" guard should reject every
		// candidate, leaving the track unmodified.
		@Test
		void ignoresAnAllEmptyMatchingSuffix() {
			tracks.setMaxTrackLength(5);
			Track tr = tracks.getTrack(TRACK_A);
			tr.len = 5;
			tr.note[0] = 5; // keeps the track non-empty; lines 1-4 stay fully cleared (-1)

			assertEquals(0, tracks.trackBuildLoop(TRACK_A));
			assertEquals(5, tr.len);
			assertEquals(-1, tr.go);
		}
	}

	// trackExpandLoop() is the inverse of trackBuildLoop(): expands a track
	// with a go-loop back out to full length by cyclically repeating the
	// [go, len) segment.
	@Nested
	class TrackExpandLoopTest {

		@BeforeEach
		void setMaxLength() {
			tracks.setMaxTrackLength(6);
		}

		@Test
		void returnsZeroForEmptyTrack() {
			assertEquals(0, tracks.trackExpandLoop(TRACK_A));
		}

		@Test
		void returnsZeroWhenThereIsNoLoop() {
			Track tr = tracks.getTrack(TRACK_A);
			tr.len = 2;
			tr.go = -1;
			tr.note[0] = 5;
			assertEquals(0, tracks.trackExpandLoop(TRACK_A));
		}

		@Test
		void nullTrackOverloadReturnsZero() {
			assertEquals(0, tracks.trackExpandLoop((Track) null));
		}

		// Cyclically repeats the 2-line [0,2) loop segment out to the full
		// 6-line track length, including reading back its own just-written
		// expansion (line 4 copies from line 2, which this same call already
		// wrote).
		@Test
		void expandsALoopCyclicallyToFullLength() {
			Track tr = tracks.getTrack(TRACK_A);
			tr.len = 2;
			tr.go = 0;
			tr.note[0] = 5;
			tr.instr[0] = 0;
			tr.volume[0] = 10;
			tr.note[1] = 6;
			tr.instr[1] = 0;
			tr.volume[1] = 10;

			assertEquals(4, tracks.trackExpandLoop(TRACK_A));
			assertEquals(6, tr.len);
			assertEquals(-1, tr.go);
			int[] expectedNotes = { 5, 6, 5, 6, 5, 6 };
			for (int i = 0; i < 6; i++) {
				assertEquals(expectedNotes[i], tr.note[i], "at line " + i);
			}
		}
	}

	// getTracksAll()/setTracksAll() are a plain deep-copy round trip - used by
	// CUndo (not yet ported) to snapshot and restore every track at once.
	@Test
	void tracksAllRoundTripsMaxTrackLengthAndAllTrackData() {
		tracks.setMaxTrackLength(4);
		tracks.getTrack(TRACK_A).note[0] = 5;
		tracks.getTrack(TRACK_B).note[1] = 6;

		TracksAll saved = new TracksAll();
		tracks.getTracksAll(saved);

		// Mutate the live tracks after the snapshot, to prove setTracksAll()
		// actually overwrites rather than coincidentally matching.
		tracks.setMaxTrackLength(10);
		tracks.getTrack(TRACK_A).note[0] = 99;

		tracks.setTracksAll(saved);

		assertEquals(4, tracks.getMaxTrackLength());
		assertEquals(5, tracks.getNote(TRACK_A, 0));
		assertEquals(6, tracks.getNote(TRACK_B, 1));
	}

	// modifyTrack() applies a transposition/instrument-shift/volume-percentage
	// change across a line range, optionally filtered to only lines carrying
	// a specific instrument, using the already-tested
	// getModifiedNote/getModifiedInstr/getModifiedVolumeP.
	@Nested
	class ModifyTrackTest {

		@Test
		void returnsFalseForNullTrack() {
			assertFalse(tracks.modifyTrack(null, 0, 0, -1, 0, 0, 100));
		}

		@Test
		void transposesNotesAcrossRangeWhenInstrnumonlyIsNegative() {
			Track tr = tracks.getTrack(TRACK_A);
			tr.note[0] = 0;
			tr.instr[0] = 2;
			tr.volume[0] = 10;
			tr.note[1] = 1;
			tr.instr[1] = 2;
			tr.volume[1] = 10;

			assertTrue(tracks.modifyTrack(tr, 0, 1, -1, 3, 0, 100));

			assertEquals(3, tr.note[0]); // getModifiedNote(0, 3)
			assertEquals(4, tr.note[1]); // getModifiedNote(1, 3)
		}

		// instrnumonly filters by the *active* instrument at each line (the
		// most recent instr[] value seen at or after `from`, tracked as the
		// loop runs) - not by whether that exact line itself sets an
		// instrument.
		@Test
		void filtersByActiveInstrumentNumberOnly() {
			Track tr = tracks.getTrack(TRACK_A);
			tr.note[0] = 0;
			tr.instr[0] = 1; // active instrument becomes 1 from here on
			tr.volume[0] = 10;
			tr.note[1] = 1; // no instrument change on this line - stays active instrument 1
			tr.instr[1] = -1;
			tr.volume[1] = 10;
			tr.note[2] = 2;
			tr.instr[2] = 5; // active instrument becomes 5 from here on
			tr.volume[2] = 10;

			assertTrue(tracks.modifyTrack(tr, 0, 2, 1, 3, 0, 100));

			assertEquals(3, tr.note[0]); // instrument 1 active - modified
			assertEquals(4, tr.note[1]); // instrument 1 still active - modified
			assertEquals(2, tr.note[2]); // instrument 5 active - left unmodified
		}

		@Test
		void clampsToWithinTrackLength() {
			Track tr = tracks.getTrack(TRACK_A);
			tr.note[255] = 0;
			tr.instr[255] = 0;
			tr.volume[255] = 10;

			// "to" of 300 is past TRACKLEN (256) and should clamp to 255, not
			// crash writing out of bounds.
			assertTrue(tracks.modifyTrack(tr, 255, 300, -1, 3, 0, 100));

			assertEquals(3, tr.note[255]); // getModifiedNote(0, 3)
		}
	}
}
