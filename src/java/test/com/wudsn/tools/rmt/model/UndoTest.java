package com.wudsn.tools.rmt.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Mirrors src/cpp/test/UndoTests.cpp in full - see Undo's own javadoc for
 * what's omitted (no Java equivalent yet) and the one latent C++ bug found
 * along the way (not fixed, preserved for parity).
 */
class UndoTest {

	private Tracks tracks;
	private Instruments instruments;
	private Song song;
	private Undo undo;

	@BeforeEach
	void setUp() {
		tracks = new Tracks();
		tracks.setMaxTrackLength(64);
		tracks.initTracks();

		instruments = new Instruments();
		instruments.initInstruments();

		song = new Song(instruments, tracks);
		blankSong(song);
		song.songSetActiveLine(0);
		song.setActiveLine(0);

		undo = new Undo(tracks, instruments, song, new TrackClipboard());
		undo.clear();
		undo.setActivePart(Part.PART_TRACKS);
	}

	private static void blankSong(Song s) {
		for (int line = 0; line < Song.SONGLEN; line++) {
			for (int col = 0; col < Song.SONGTRACKS; col++) {
				s.getSong()[line][col] = -1;
			}
			s.getSongGo()[line] = -1;
		}
	}

	// --- Bookkeeping-only methods ---

	@Test
	void getUndoStepsIsZeroInitially() {
		assertEquals(0, undo.getUndoSteps());
	}

	@Test
	void getRedoStepsIsZeroInitially() {
		assertEquals(0, undo.getRedoSteps());
	}

	@Test
	void dropLastOnEmptyHistoryIsANoOp() {
		undo.dropLast();
		assertEquals(0, undo.getUndoSteps());
	}

	@Test
	void separatorOnEmptyHistoryIsANoOp() {
		undo.separator();
		assertEquals(0, undo.getUndoSteps());
	}

	@Test
	void posIsEqualComparesGroupType0UsingTwoElements() {
		int[] a = { 1, 2 };
		int[] b = { 1, 2 };
		int[] c = { 1, 3 };
		assertTrue(undo.posIsEqual(a, b, UndoType.UETYPE_NOTEINSTRVOL)); // 1 >> 6 == 0
		assertFalse(undo.posIsEqual(a, c, UndoType.UETYPE_NOTEINSTRVOL));
	}

	@Test
	void posIsEqualComparesGroupType1UsingOneElement() {
		int[] a = { 5 };
		int[] b = { 5 };
		int[] c = { 6 };
		assertTrue(undo.posIsEqual(a, b, UndoType.UETYPE_SONGDATA)); // 65 >> 6 == 1
		assertFalse(undo.posIsEqual(a, c, UndoType.UETYPE_SONGDATA));
	}

	@Test
	void posIsEqualComparesGroupType2UsingThreeElements() {
		// No real UndoType value falls in 128-191 (highest defined is 69) -
		// use an arbitrary value in range directly, since Java has no
		// equivalent to C++'s static_cast<UndoType>(150) for a closed enum
		// (see UndoType's own javadoc for why this class uses plain ints).
		int type = 150; // 150 >> 6 == 2
		int[] a = { 1, 2, 3 };
		int[] b = { 1, 2, 3 };
		int[] c = { 1, 2, 4 };
		assertTrue(undo.posIsEqual(a, b, type));
		assertFalse(undo.posIsEqual(a, c, type));
	}

	@Test
	void posIsEqualReturnsFalseForOutOfRangeType() {
		int type = 200; // 200 >> 6 == 3, no matching case
		int[] a = { 1 };
		int[] b = { 1 };
		assertFalse(undo.posIsEqual(a, b, type));
	}

	// --- undo()/redo() on empty history ---

	@Test
	void undoOnEmptyHistoryReturnsFalse() {
		assertFalse(undo.undo());
	}

	@Test
	void redoOnEmptyHistoryReturnsFalse() {
		assertFalse(undo.redo());
	}

	// --- changeTrack / performEvent(UETYPE_NOTEINSTRVOL/...(SPEED)/SPEED/LENGO/TRACKDATA/TRACKSALL) ---

	@Test
	void changeTrackNoteInstrVolIsSwappedByUndoAndRedo() {
		Track tr = tracks.getTrack(0);
		tr.note[0] = 5;
		tr.instr[0] = 2;
		tr.volume[0] = 10;

		undo.changeTrack(0, 0, UndoType.UETYPE_NOTEINSTRVOL, 1);

		tr.note[0] = 7;
		tr.instr[0] = 3;
		tr.volume[0] = 12;

		assertTrue(undo.undo());
		assertEquals(5, tr.note[0]);
		assertEquals(2, tr.instr[0]);
		assertEquals(10, tr.volume[0]);

		assertTrue(undo.redo());
		assertEquals(7, tr.note[0]);
		assertEquals(3, tr.instr[0]);
		assertEquals(12, tr.volume[0]);
	}

	@Test
	void changeTrackNoteInstrVolSpeedIsSwappedByUndoAndRedo() {
		Track tr = tracks.getTrack(0);
		tr.note[0] = 5;
		tr.instr[0] = 2;
		tr.volume[0] = 10;
		tr.speed[0] = 3;

		undo.changeTrack(0, 0, UndoType.UETYPE_NOTEINSTRVOLSPEED, 1);

		tr.note[0] = 7;
		tr.instr[0] = 3;
		tr.volume[0] = 12;
		tr.speed[0] = 4;

		assertTrue(undo.undo());
		assertEquals(5, tr.note[0]);
		assertEquals(2, tr.instr[0]);
		assertEquals(10, tr.volume[0]);
		assertEquals(3, tr.speed[0]);
	}

	@Test
	void changeTrackSpeedIsSwappedByUndo() {
		Track tr = tracks.getTrack(0);
		tr.speed[0] = 3;

		undo.changeTrack(0, 0, UndoType.UETYPE_SPEED, 1);
		tr.speed[0] = 4;

		assertTrue(undo.undo());
		assertEquals(3, tr.speed[0]);
	}

	@Test
	void changeTrackLenGoIsSwappedByUndo() {
		Track tr = tracks.getTrack(0);
		tr.len = 32;
		tr.go = -1;

		undo.changeTrack(0, 0, UndoType.UETYPE_LENGO, 1);
		tr.len = 64;
		tr.go = 5;

		assertTrue(undo.undo());
		assertEquals(32, tr.len);
		assertEquals(-1, tr.go);
	}

	@Test
	void changeTrackWholeTrackDataIsSwappedByUndo() {
		Track tr = tracks.getTrack(0);
		tr.note[0] = 5;

		undo.changeTrack(0, 0, UndoType.UETYPE_TRACKDATA, 1);
		tr.note[0] = 9;
		tr.note[1] = 1; // any other field changing too must also be reverted

		assertTrue(undo.undo());
		assertEquals(5, tr.note[0]);
		assertEquals(-1, tr.note[1]); // back to clearTrack's default
	}

	@Test
	void changeTrackAllTracksIsSwappedByUndo() {
		tracks.getTrack(0).note[0] = 5;
		tracks.getTrack(1).note[0] = 6;

		undo.changeTrack(0, 0, UndoType.UETYPE_TRACKSALL, 1);

		tracks.getTrack(0).note[0] = 9;
		tracks.getTrack(1).note[0] = 9;

		assertTrue(undo.undo());
		assertEquals(5, tracks.getTrack(0).note[0]);
		assertEquals(6, tracks.getTrack(1).note[0]);
	}

	@Test
	void changeTrackWithInvalidTypeIsCharacterizedAsANoOpDataEvent() {
		// Guard-only branch - characterized as-is rather than avoided, since
		// insertEvent()/performEvent() still handle a null-data event
		// gracefully (no crash).
		undo.changeTrack(0, 0, 999, 1);
		assertEquals(1, undo.getUndoSteps());
		assertTrue(undo.undo());
		assertEquals(1, undo.getRedoSteps());
	}

	@Test
	void changeTrackIgnoresOutOfRangeTrackOrLine() {
		undo.changeTrack(-1, 0, UndoType.UETYPE_SPEED, 1);
		undo.changeTrack(0, -1, UndoType.UETYPE_SPEED, 1);
		assertEquals(0, undo.getUndoSteps());
	}

	// --- changeSong / performEvent(UETYPE_SONGTRACK/SONGGO/SONGDATA) ---

	@Test
	void changeSongTrackIsSwappedByUndo() {
		song.getSong()[0][0] = 5;

		undo.changeSong(0, 0, UndoType.UETYPE_SONGTRACK, 1);
		song.getSong()[0][0] = 7;

		assertTrue(undo.undo());
		assertEquals(5, song.getSong()[0][0]);
	}

	@Test
	void changeSongGoIsSwappedByUndo() {
		song.getSongGo()[0] = -1;

		undo.changeSong(0, 0, UndoType.UETYPE_SONGGO, 1);
		song.getSongGo()[0] = 3;

		assertTrue(undo.undo());
		assertEquals(-1, song.getSongGo()[0]);
	}

	@Test
	void changeSongWholeSongDataIsSwappedByUndo() {
		song.getSong()[0][0] = 5;
		song.getSongGo()[1] = 2;

		undo.changeSong(0, 0, UndoType.UETYPE_SONGDATA, 1);

		song.getSong()[0][0] = 9;
		song.getSongGo()[1] = 8;

		assertTrue(undo.undo());
		assertEquals(5, song.getSong()[0][0]);
		assertEquals(2, song.getSongGo()[1]);
	}

	@Test
	void changeSongIgnoresNegativeSonglineOrColumn() {
		undo.changeSong(-1, 0, UndoType.UETYPE_SONGTRACK, 1);
		undo.changeSong(0, -1, UndoType.UETYPE_SONGTRACK, 1);
		assertEquals(0, undo.getUndoSteps());
	}

	// --- changeInstrument / performEvent(UETYPE_INSTRDATA/INSTRSALL) ---

	@Test
	void changeInstrumentDataIsSwappedByUndo() {
		Instrument instr = instruments.getInstrument(0);
		instr.octave = 3;

		undo.changeInstrument(0, 0, UndoType.UETYPE_INSTRDATA, 1);
		instr.octave = 5;

		assertTrue(undo.undo());
		assertEquals(3, instr.octave);
	}

	@Test
	void changeInstrumentAllInstrumentsIsSwappedByUndo() {
		instruments.getInstrument(0).octave = 3;
		instruments.getInstrument(1).octave = 4;

		undo.changeInstrument(0, 0, UndoType.UETYPE_INSTRSALL, 1);

		instruments.getInstrument(0).octave = 9;
		instruments.getInstrument(1).octave = 9;

		assertTrue(undo.undo());
		assertEquals(3, instruments.getInstrument(0).octave);
		assertEquals(4, instruments.getInstrument(1).octave);
	}

	// --- changeInfo / performEvent(UETYPE_INFODATA) ---

	@Test
	void changeInfoDataIsSwappedByUndo() {
		SongInfo info = new SongInfo();
		song.getSongInfoPars(info);
		info.speed = 6;
		song.setSongInfoPars(info);

		undo.changeInfo(0, UndoType.UETYPE_INFODATA, 1);

		song.getSongInfoPars(info);
		info.speed = 9;
		song.setSongInfoPars(info);

		assertTrue(undo.undo());
		song.getSongInfoPars(info);
		assertEquals(6, info.speed);
	}

	// --- Multi-step history / separator semantics ---

	@Test
	void twoCompletedChangesAreTwoIndependentUndoSteps() {
		Track tr = tracks.getTrack(0);
		tr.speed[0] = 1;
		undo.changeTrack(0, 0, UndoType.UETYPE_SPEED, 1); // step 1: snapshot speed=1
		tr.speed[0] = 2;

		tr.speed[1] = 10;
		undo.changeTrack(0, 1, UndoType.UETYPE_SPEED, 1); // step 2: snapshot line 1's speed=10
		tr.speed[1] = 20;

		assertEquals(2, undo.getUndoSteps());

		assertTrue(undo.undo()); // undoes step 2 only
		assertEquals(10, tr.speed[1]);
		assertEquals(2, tr.speed[0]); // step 1 untouched
		assertEquals(1, undo.getUndoSteps());
		assertEquals(1, undo.getRedoSteps());

		assertTrue(undo.undo()); // undoes step 1
		assertEquals(1, tr.speed[0]);
		assertEquals(0, undo.getUndoSteps());
		assertEquals(2, undo.getRedoSteps());
	}

	@Test
	void repeatedChangeAtTheSameCursorAndPositionCoalescesIntoOneStep() {
		// Default separator (0, "accumulate") at an unchanged cursor/type/
		// position coalesces into a single undo step, keeping only the first
		// snapshot - real production behavior for e.g. typing several notes
		// in a row before ever moving the cursor or calling separator().
		Track tr = tracks.getTrack(0);
		tr.speed[0] = 1;
		undo.changeTrack(0, 0, UndoType.UETYPE_SPEED); // separator defaults to 0
		tr.speed[0] = 2;
		undo.changeTrack(0, 0, UndoType.UETYPE_SPEED); // same cursor/type/pos - coalesces
		tr.speed[0] = 3;

		assertEquals(1, undo.getUndoSteps());
		assertTrue(undo.undo());
		assertEquals(1, tr.speed[0]); // restores all the way back to the first snapshot
	}

	// --- init / clear ---

	@Test
	void clearResetsUndoAndRedoStepsToZero() {
		Track tr = tracks.getTrack(0);
		tr.speed[0] = 1;
		undo.changeTrack(0, 0, UndoType.UETYPE_SPEED, 1);
		undo.undo();
		assertEquals(1, undo.getRedoSteps());

		undo.clear();

		assertEquals(0, undo.getUndoSteps());
		assertEquals(0, undo.getRedoSteps());
		assertFalse(undo.undo());
		assertFalse(undo.redo());
	}

	@Test
	void initResetsUndoAndRedoStepsToZero() {
		Track tr = tracks.getTrack(0);
		tr.speed[0] = 1;
		undo.changeTrack(0, 0, UndoType.UETYPE_SPEED, 1);

		undo.init();

		assertEquals(0, undo.getUndoSteps());
		assertFalse(undo.undo());
	}

	// --- dropLast ---

	@Test
	void dropLastRemovesTheMostRecentUndoStepWithoutPerformingIt() {
		Track tr = tracks.getTrack(0);
		tr.speed[0] = 1;
		undo.changeTrack(0, 0, UndoType.UETYPE_SPEED, 1);
		tr.speed[0] = 2; // never undone - dropLast just discards the recording

		undo.dropLast();

		assertEquals(0, undo.getUndoSteps());
		assertFalse(undo.undo());
		assertEquals(2, tr.speed[0]); // unchanged - dropLast doesn't touch live data
	}
}
