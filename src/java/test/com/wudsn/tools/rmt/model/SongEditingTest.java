package com.wudsn.tools.rmt.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Mirrors src/cpp/test/SongEditingTests.cpp's SongEditingTest fixture -
 * only the "sub-batch 1" methods scoped in plans/JAVA_SONGEDITING_PLAN.md
 * (cursor/navigation helpers with no new dependencies beyond Tracks/
 * Instruments/Song's own state, already ported). {@code getUECursor}/
 * {@code setUECursor}/{@code songGetGo} are already covered by
 * {@code SongTest}/{@code UndoTest} - not repeated here.
 */
class SongEditingTest {

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
		undo = new Undo(tracks, instruments, song);

		for (int line = 0; line < Song.SONGLEN; line++) {
			for (int col = 0; col < Song.SONGTRACKS; col++) {
				song.getSong()[line][col] = -1;
			}
			song.getSongGo()[line] = -1;
		}
		song.songSetActiveLine(0);
		song.setActiveLine(0);
	}

	// --- getSubsongParts ---

	@Test
	void getSubsongPartsReturnsZeroWhenSongHasNoGotoLines() {
		Song.SubsongParts result = song.getSubsongParts(4);
		assertEquals(0, result.count());
		assertEquals("", result.parts());
	}

	@Test
	void getSubsongPartsFindsOneSubsongEndingInAGotoLine() {
		song.getSong()[0][0] = 5; // a used track at line 0
		song.getSongGo()[1] = 0; // line 1: "goto line 0" - closes the loop

		Song.SubsongParts result = song.getSubsongParts(4);
		assertEquals(1, result.count());
		assertEquals("00 ", result.parts());
	}

	// --- markTfUsed / markTfNoEmpty ---

	@Test
	void markTfUsedMarksTracksReferencedByNonGotoLines() {
		song.getSong()[0][0] = 3;
		song.getSongGo()[1] = 5; // goto line: its track column is ignored
		song.getSong()[1][0] = 7;

		byte[] flags = new byte[Tracks.TRACKSNUM];
		song.markTfUsed(flags, 4);

		assertEquals(TrackFlag.TF_USED, flags[3]);
		assertEquals(0, flags[7]);
	}

	@Test
	void markTfNoEmptyMarksTracksWithData() {
		tracks.getTrack(2).note[0] = 0; // any valid note makes it "not empty"

		byte[] flags = new byte[Tracks.TRACKSNUM];
		song.markTfNoEmpty(flags);

		assertEquals(TrackFlag.TF_NOEMPTY, flags[2] & TrackFlag.TF_NOEMPTY);
		assertEquals(0, flags[3] & TrackFlag.TF_NOEMPTY);
	}

	// --- activeInstrSet / Prev / Next ---

	@Test
	void activeInstrSetChangesActiveInstrument() {
		song.activeInstrSet(5, false);
		assertEquals(5, song.getActiveInstr());
	}

	@Test
	void activeInstrPrevAndNextStepAndWrapAt6Bits() {
		song.activeInstrSet(5, false);
		song.activeInstrPrev(undo, false);
		assertEquals(4, song.getActiveInstr());

		song.activeInstrNext(undo, false);
		assertEquals(5, song.getActiveInstr());

		song.activeInstrSet(0, false);
		song.activeInstrPrev(undo, false);
		assertEquals(0x3f, song.getActiveInstr()); // (0 - 1) & 0x3f wraps to 63
	}

	// --- trackLeft / trackRight (observed via getUECursor) ---

	@Test
	void trackLeftWrapsColumnAndCursorAtZero() {
		song.trackLeft(false, 4, undo);
		int[] cursor = song.getUECursor(Part.PART_TRACKS);
		assertEquals(4 - 1, cursor[2]); // column wrapped
		assertEquals(3, cursor[3]); // cursor wrapped to the previous speed column
	}

	@Test
	void trackRightAdvancesCursorWithoutChangingColumn() {
		song.trackRight(false, 4, undo);
		int[] cursor = song.getUECursor(Part.PART_TRACKS);
		assertEquals(0, cursor[2]); // column unchanged
		assertEquals(1, cursor[3]); // cursor advanced
	}

	@Test
	void trackLeftColumnModeSkipsTheCursorAndMovesColumnDirectly() {
		song.trackLeft(true, 4, undo);
		int[] cursor = song.getUECursor(Part.PART_TRACKS);
		assertEquals(4 - 1, cursor[2]);
		assertEquals(0, cursor[3]); // cursor untouched in column mode
	}

	// --- respectBoundaries ---

	@Test
	void respectBoundariesClampsActiveLineToSmallestTrackLength() {
		tracks.getTrack(0).len = 8; // active track for song line 0 is only 8 lines long
		song.getSong()[0][0] = 0;
		song.setActiveLine(20); // out of bounds for an 8-line track

		song.respectBoundaries(4);

		assertEquals(7, song.getActiveLine()); // clamped to length - 1
	}

	// --- trackGetLoopingNoteInstrVol ---

	@Test
	void trackGetLoopingNoteInstrVolReturnsMinusOneWithoutALoop() {
		tracks.getTrack(0).len = 4;
		tracks.getTrack(0).go = -1;
		song.setActiveLine(10); // beyond the track's length, and no loop to fall back on

		Song.NoteInstrVol result = song.trackGetLoopingNoteInstrVol(0);

		assertEquals(-1, result.note());
		assertEquals(-1, result.instr());
		assertEquals(-1, result.vol());
	}

	// --- songTrackSet / SetByNum / Dec / Inc / Empty / GoOnOff ---

	@Test
	void songTrackSetWritesTheActiveSongPosition() {
		song.songTrackSet(12, undo);
		assertEquals(12, song.getSong()[0][0]);
	}

	@Test
	void songTrackSetByNumShiftsInALowNibbleWhenNotAGoLine() {
		song.songTrackSet(0x02, undo);
		song.songTrackSetByNum(0x5, undo); // 0x02 -> (0x2 << 4 | 0x5) = 0x25
		assertEquals(0x25, song.getSong()[0][0]);
	}

	@Test
	void songTrackSetByNumShiftsInAGoTargetWhenOnAGoLine() {
		song.songTrackGoOnOff(undo); // turns GO on for the active line (was -1, becomes 0)
		song.songTrackSetByNum(0x7, undo);
		assertEquals(0x07, song.songGetGo());
	}

	@Test
	void songTrackDecWrapsAtMinusOneToLastTrack() {
		// The active position is already -1 ("--", from the fixture's blank
		// song), and the wrap only triggers once decrementing goes past -1.
		song.songTrackDec(undo);
		assertEquals(Tracks.TRACKSNUM - 1, song.getSong()[0][0]);
	}

	@Test
	void songTrackIncWrapsAtLastTrackToMinusOne() {
		song.songTrackSet(Tracks.TRACKSNUM - 1, undo);
		song.songTrackInc(undo);
		assertEquals(-1, song.getSong()[0][0]);
	}

	@Test
	void songTrackEmptySetsTheActivePositionToMinusOne() {
		song.songTrackSet(4, undo);
		song.songTrackEmpty(undo);
		assertEquals(-1, song.getSong()[0][0]);
	}

	@Test
	void songTrackGoOnOffTogglesGoAtTheActiveLine() {
		assertEquals(-1, song.songGetGo());
		song.songTrackGoOnOff(undo);
		assertEquals(0, song.songGetGo());
		song.songTrackGoOnOff(undo);
		assertEquals(-1, song.songGetGo());
	}

	// --- instrInfo ---
	// Called with a non-null info, per its own info-guarded design - never
	// builds/shows the (unported) MessageBox("Instrument info") branch.

	@Test
	void instrInfoPopulatesTheOutputStructWithoutShowingAMessageBox() {
		Track tr = tracks.getTrack(0);
		tr.len = 4;
		tr.instr[0] = 2;
		tr.note[0] = 10;
		tr.volume[0] = 8;

		Song.InstrInfo info = new Song.InstrInfo();
		song.instrInfo(info, 2);

		assertEquals(1, info.count);
		assertEquals(1, info.usedInTracks);
		assertEquals(2, info.instrFrom);
		assertEquals(2, info.instrTo);
		assertEquals(10, info.minNote);
		assertEquals(10, info.maxNote);
		assertEquals(8, info.minVol);
		assertEquals(8, info.maxVol);
	}

	// --- instrChangeApply ---
	// Extracted from InstrChange() - dual-mode like instrInfo/trackInfo,
	// returning the summary directly instead of showing it in a MessageBox.

	@Test
	void instrChangeApplyRemapsMatchingNotesInstrumentsAndVolumes() {
		Track tr = tracks.getTrack(0);
		tr.len = 1;
		tr.note[0] = 10;
		tr.instr[0] = 2;
		tr.volume[0] = 8;

		Song.InstrChangeParams p = new Song.InstrChangeParams();
		p.snotefrom = 10;
		p.snoteto = 10;
		p.svolmin = 8;
		p.svolmax = 8;
		p.sinstrfrom = 2;
		p.sinstrto = 2;
		p.dnotefrom = 15;
		p.dnoteto = 15;
		p.dvolmin = 5;
		p.dvolmax = 5;
		p.dinstrfrom = 3;
		p.dinstrto = 3;
		p.onlytrack = -1;
		p.onlychannels = -1;
		p.onlysonglinefrom = -1;
		p.onlysonglineto = -1;

		String resultMsg = song.instrChangeApply(p, undo, 4);

		assertEquals(15, tr.note[0]);
		assertEquals(3, tr.instr[0]);
		assertEquals(5, tr.volume[0]);
		assertTrue(resultMsg.contains("successfully"));
	}

	@Test
	void instrChangeApplyOnlyTrackRestrictsTheChangeToOneTrack() {
		Track tr0 = tracks.getTrack(0);
		tr0.len = 1;
		tr0.note[0] = 10;
		tr0.instr[0] = 2;
		tr0.volume[0] = 8;

		Track tr1 = tracks.getTrack(1);
		tr1.len = 1;
		tr1.note[0] = 10;
		tr1.instr[0] = 2;
		tr1.volume[0] = 8;

		Song.InstrChangeParams p = new Song.InstrChangeParams();
		p.snotefrom = 10;
		p.snoteto = 10;
		p.svolmin = 8;
		p.svolmax = 8;
		p.sinstrfrom = 2;
		p.sinstrto = 2;
		p.dnotefrom = 15;
		p.dnoteto = 15;
		p.dvolmin = 5;
		p.dvolmax = 5;
		p.dinstrfrom = 3;
		p.dinstrto = 3;
		p.onlytrack = 0; // restrict to track 0 only
		p.onlychannels = -1;
		p.onlysonglinefrom = -1;
		p.onlysonglineto = -1;

		song.instrChangeApply(p, undo, 4);

		assertEquals(15, tr0.note[0]); // changed
		assertEquals(10, tr1.note[0]); // untouched, restricted to track 0 only
	}

	// --- trackInfo ---
	// Called with a non-null info, per its own info-guarded design (mirroring
	// instrInfo) - never builds/shows the (unported) MessageBox("Track Info") branch.

	@Test
	void trackInfoPopulatesTheOutputStructWithoutShowingAMessageBox() {
		song.getSong()[0][0] = 5;
		song.getSong()[1][2] = 5;

		Song.TrackInfo info = new Song.TrackInfo();
		song.trackInfo(5, info, 4);

		assertEquals(2, info.count);
		assertEquals(2, info.lines);
		assertEquals(1, info.usedInColumn[0]);
		assertEquals(0, info.usedInColumn[1]);
		assertEquals(1, info.usedInColumn[2]);
		assertEquals(0, info.usedInColumn[3]);
	}

	@Test
	void trackInfoLeavesTheOutputStructUntouchedForAnOutOfRangeTrack() {
		Song.TrackInfo info = new Song.TrackInfo();
		info.count = 99;
		info.lines = 99;
		java.util.Arrays.fill(info.usedInColumn, 1);

		song.trackInfo(-1, info, 4);
		assertEquals(99, info.count);

		song.trackInfo(Tracks.TRACKSNUM, info, 4);
		assertEquals(99, info.count);
	}
}
