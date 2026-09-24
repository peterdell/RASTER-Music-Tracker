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
}
