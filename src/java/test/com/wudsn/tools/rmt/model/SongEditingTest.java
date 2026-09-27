package com.wudsn.tools.rmt.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Mirrors src/cpp/test/SongEditingTests.cpp's SongEditingTest fixture -
 * the sub-batches of SongEditing.cpp scoped/ported so far in
 * plans/JAVA_SONGEDITING_PLAN.md. {@code getUECursor}/{@code setUECursor}/
 * {@code songGetGo} are already covered by {@code SongTest}/
 * {@code UndoTest} - not repeated here.
 */
class SongEditingTest {

	private Tracks tracks;
	private Instruments instruments;
	private Song song;
	private Undo undo;
	private Messages messages;
	private AtariTrackerDriver atariTrackerDriver;
	private TrackClipboard clipboard;

	@BeforeEach
	void setUp() {
		tracks = new Tracks();
		tracks.setMaxTrackLength(64);
		tracks.initTracks();

		instruments = new Instruments();
		instruments.initInstruments();
		// Instruments.clearInstrument() has real behavior here (sets a
		// default "Instrument XX" display name), unlike the C++ test binary
		// (where it's a no-op stub, and the fixture's own manual
		// memset(..., 0, sizeof(TInstrument)) loop is what actually blanks
		// every instrument, name included) - blank the names the same way
		// here so name-comparison tests start from the same blank slate.
		for (int i = 0; i < Instruments.INSTRSNUM; i++) {
			java.util.Arrays.fill(instruments.getInstrument(i).name, '\0');
		}

		song = new Song(instruments, tracks);
		clipboard = new TrackClipboard();
		undo = new Undo(tracks, instruments, song, clipboard);
		messages = new Messages();
		atariTrackerDriver = new AtariTrackerDriver(new Atari());

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

	// --- songInsertLine / songDeleteLine ---

	@Test
	void songInsertLineShiftsSubsequentLinesDownAndClearsInsertedLine() {
		song.getSong()[0][0] = 1;
		song.getSong()[1][0] = 2;

		song.songInsertLine(1, undo, 4);

		assertEquals(1, song.getSong()[0][0]); // untouched
		assertEquals(-1, song.getSong()[1][0]); // newly inserted, empty
		assertEquals(2, song.getSong()[2][0]); // shifted down
	}

	@Test
	void songDeleteLineShiftsSubsequentLinesUp() {
		song.getSong()[0][0] = 1;
		song.getSong()[1][0] = 2;

		song.songDeleteLine(0, undo, 4);

		assertEquals(2, song.getSong()[0][0]); // shifted up
		assertEquals(-1, song.getSong()[Song.SONGLEN - 1][0]); // vacated slot at the end
	}

	// --- songInsertCopyOrCloneOfSongLinesApply ---
	// Its two guard-only error paths are avoided here by using valid
	// from/to/line values and enough free tracks.

	@Test
	void songInsertCopyOrCloneOfSongLinesApplyCopiesTheSourceLineWhenNotCloning() {
		song.getSong()[0][0] = 5;

		assertTrue(song.songInsertCopyOrCloneOfSongLinesApply(1, 0, 0, false, 0, 100, undo, 4, clipboard));

		assertEquals(5, song.getSong()[0][0]); // source untouched
		assertEquals(5, song.getSong()[1][0]); // copy landed at the insert point, same track number
	}

	@Test
	void songInsertCopyOrCloneOfSongLinesApplyClonesIntoANewTrackWhenCloning() {
		song.getSong()[0][0] = 5;
		Track src = tracks.getTrack(5);
		src.len = 2;
		src.note[0] = 10;
		src.instr[0] = 1;
		src.volume[0] = 8;

		// tuning=0, volumep=100 - no actual edit, just characterizes that
		// cloning creates a distinct track rather than reusing track 5.
		assertTrue(song.songInsertCopyOrCloneOfSongLinesApply(1, 0, 0, true, 0, 100, undo, 4, clipboard));

		assertEquals(5, song.getSong()[0][0]); // source untouched
		int clonedTrack = song.getSong()[1][0];
		assertTrue(clonedTrack != 5); // cloned into a different, previously-unused track
		assertTrue(clonedTrack != -1);

		Track dst = tracks.getTrack(clonedTrack);
		assertEquals(10, dst.note[0]);
		assertEquals(1, dst.instr[0]);
		assertEquals(8, dst.volume[0]);
	}

	// --- Tracks.delNoteInstrVolSpeed / setNoteInstrVol / setInstr / setVol /
	// setSpeed / setEnd / setGo (TracksEdit.cpp) ---
	// Mirrors SongEditingTests.cpp's own section exactly - only the visible
	// track-data effect is asserted, not the undo recording (UndoTest
	// covers Undo itself).

	@Test
	void delNoteInstrVolSpeedReturnsFalseForAnInvalidTrack() {
		assertFalse(tracks.delNoteInstrVolSpeed(0xF, -1, 0, undo));
		assertFalse(tracks.delNoteInstrVolSpeed(0xF, Tracks.TRACKSNUM, 0, undo));
	}

	@Test
	void delNoteInstrVolSpeedReturnsFalseAndChangesNothingWhenLineIsOutOfBounds() {
		Track tr = tracks.getTrack(0);
		tr.note[0] = 5;

		assertFalse(tracks.delNoteInstrVolSpeed(0xF, 0, tr.len, undo));

		assertEquals(5, tr.note[0]);
	}

	@Test
	void delNoteInstrVolSpeedClearsOnlyTheMaskedFields() {
		Track tr = tracks.getTrack(0);
		tr.note[0] = 5;
		tr.instr[0] = 3;
		tr.volume[0] = 8;
		tr.speed[0] = 2;

		assertTrue(tracks.delNoteInstrVolSpeed(1 /* note only */, 0, 0, undo));

		assertEquals(-1, tr.note[0]);
		assertEquals(3, tr.instr[0]);
		assertEquals(8, tr.volume[0]);
		assertEquals(2, tr.speed[0]);
	}

	@Test
	void delNoteInstrVolSpeedClearsAllFourFieldsWhenAllBitsAreSet() {
		Track tr = tracks.getTrack(0);
		tr.note[0] = 5;
		tr.instr[0] = 3;
		tr.volume[0] = 8;
		tr.speed[0] = 2;

		assertTrue(tracks.delNoteInstrVolSpeed(0xF, 0, 0, undo));

		assertEquals(-1, tr.note[0]);
		assertEquals(-1, tr.instr[0]);
		assertEquals(-1, tr.volume[0]);
		assertEquals(-1, tr.speed[0]);
	}

	@Test
	void setNoteInstrVolReturnsFalseForAnInvalidTrack() {
		assertFalse(tracks.setNoteInstrVol(10, 2, 5, -1, 0, false, undo));
	}

	@Test
	void setNoteInstrVolReturnsFalseAndChangesNothingWhenLineIsOutOfBounds() {
		Track tr = tracks.getTrack(0);
		tr.note[0] = 1;

		assertFalse(tracks.setNoteInstrVol(10, 2, 5, 0, tr.len, false, undo));

		assertEquals(1, tr.note[0]);
	}

	@Test
	void setNoteInstrVolForcesInstrAndVolumeToMinusOneWhenNoteIsNegative() {
		Track tr = tracks.getTrack(0);

		assertTrue(tracks.setNoteInstrVol(-1, 2, 5, 0, 0, false, undo));

		assertEquals(-1, tr.note[0]);
		assertEquals(-1, tr.instr[0]);
		assertEquals(-1, tr.volume[0]);
	}

	@Test
	void setNoteInstrVolAlwaysOverwritesVolumeWhenRespectVolumeIsOff() {
		Track tr = tracks.getTrack(0);
		tr.volume[0] = 9;

		assertTrue(tracks.setNoteInstrVol(10, 2, 5, 0, 0, false, undo));

		assertEquals(10, tr.note[0]);
		assertEquals(2, tr.instr[0]);
		assertEquals(5, tr.volume[0]);
	}

	@Test
	void setNoteInstrVolPreservesExistingVolumeWhenRespectVolumeIsOnAndBothVolumesAreNonNegative() {
		Track tr = tracks.getTrack(0);
		tr.volume[0] = 9;

		assertTrue(tracks.setNoteInstrVol(10, 2, 5, 0, 0, true, undo));

		assertEquals(10, tr.note[0]); // note/instr are set unconditionally
		assertEquals(2, tr.instr[0]);
		assertEquals(9, tr.volume[0]); // volume preserved
	}

	@Test
	void setNoteInstrVolOverwritesVolumeWhenRespectVolumeIsOnButTheNewVolumeIsNegative() {
		Track tr = tracks.getTrack(0);
		tr.volume[0] = 9;

		assertTrue(tracks.setNoteInstrVol(10, 2, -1, 0, 0, true, undo));

		assertEquals(-1, tr.volume[0]);
	}

	@Test
	void setNoteInstrVolOverwritesVolumeWhenRespectVolumeIsOnButTheExistingVolumeIsNegative() {
		Track tr = tracks.getTrack(0);
		tr.volume[0] = -1;

		assertTrue(tracks.setNoteInstrVol(10, 2, 5, 0, 0, true, undo));

		assertEquals(5, tr.volume[0]);
	}

	@Test
	void setInstrReturnsFalseForAnInvalidTrack() {
		assertFalse(tracks.setInstr(2, -1, 0, undo));
	}

	@Test
	void setInstrReturnsFalseAndChangesNothingWhenLineIsOutOfBounds() {
		Track tr = tracks.getTrack(0);
		tr.instr[0] = 1;

		assertFalse(tracks.setInstr(2, 0, tr.len, undo));

		assertEquals(1, tr.instr[0]);
	}

	@Test
	void setInstrSetsTheInstrumentAtTheGivenLine() {
		Track tr = tracks.getTrack(0);

		assertTrue(tracks.setInstr(2, 0, 0, undo));

		assertEquals(2, tr.instr[0]);
	}

	@Test
	void setVolReturnsFalseForAnInvalidTrack() {
		assertFalse(tracks.setVol(8, -1, 0, undo));
	}

	@Test
	void setVolReturnsFalseAndChangesNothingWhenLineIsOutOfBounds() {
		Track tr = tracks.getTrack(0);
		tr.volume[0] = 1;

		assertFalse(tracks.setVol(8, 0, tr.len, undo));

		assertEquals(1, tr.volume[0]);
	}

	@Test
	void setVolSetsTheVolumeAtTheGivenLine() {
		Track tr = tracks.getTrack(0);

		assertTrue(tracks.setVol(8, 0, 0, undo));

		assertEquals(8, tr.volume[0]);
	}

	@Test
	void setSpeedReturnsFalseForAnInvalidTrack() {
		assertFalse(tracks.setSpeed(3, -1, 0, undo));
	}

	@Test
	void setSpeedReturnsFalseAndChangesNothingWhenLineIsOutOfBounds() {
		Track tr = tracks.getTrack(0);
		tr.speed[0] = 1;

		assertFalse(tracks.setSpeed(3, 0, tr.len, undo));

		assertEquals(1, tr.speed[0]);
	}

	@Test
	void setSpeedSetsTheSpeedAtTheGivenLine() {
		Track tr = tracks.getTrack(0);

		assertTrue(tracks.setSpeed(3, 0, 0, undo));

		assertEquals(3, tr.speed[0]);
	}

	@Test
	void setEndReturnsFalseForAnInvalidTrack() {
		assertFalse(tracks.setEnd(-1, 5, undo));
	}

	@Test
	void setEndSetsLengthToMaxTrackLengthWhenLineIsZero() {
		Track tr = tracks.getTrack(0);
		tr.len = 10;

		assertTrue(tracks.setEnd(0, 0, undo));

		assertEquals(64, tr.len);
	}

	@Test
	void setEndSetsLengthToTheGivenLineWhenDifferentFromTheCurrentLength() {
		Track tr = tracks.getTrack(0); // tr.len starts at 64 (setUp's maxTrackLength)

		assertTrue(tracks.setEnd(0, 10, undo));

		assertEquals(10, tr.len);
	}

	@Test
	void setEndTogglesBackToMaxTrackLengthWhenLineEqualsTheCurrentLength() {
		Track tr = tracks.getTrack(0);
		tr.len = 10;

		assertTrue(tracks.setEnd(0, 10, undo));

		assertEquals(64, tr.len);
	}

	@Test
	void setEndResetsGoWhenItFallsOutsideTheNewLength() {
		Track tr = tracks.getTrack(0);
		tr.go = 20;

		tracks.setEnd(0, 10, undo); // len becomes 10, go (20) no longer fits

		assertEquals(-1, tr.go);
	}

	@Test
	void setEndPreservesGoWhenItStillFitsWithinTheNewLength() {
		Track tr = tracks.getTrack(0);
		tr.go = 5;

		tracks.setEnd(0, 10, undo);

		assertEquals(5, tr.go);
	}

	@Test
	void setGoReturnsFalseForAnInvalidTrack() {
		assertFalse(tracks.setGo(-1, 5, undo));
	}

	@Test
	void setGoReturnsFalseAndChangesNothingWhenLineIsNotBeforeTheTrackEnd() {
		Track tr = tracks.getTrack(0);
		tr.go = 3;

		assertFalse(tracks.setGo(0, tr.len, undo));

		assertEquals(3, tr.go);
	}

	@Test
	void setGoSetsTheGoLine() {
		Track tr = tracks.getTrack(0);

		assertTrue(tracks.setGo(0, 5, undo));

		assertEquals(5, tr.go);
	}

	@Test
	void setGoTogglesOffWhenCalledAgainWithTheSameLine() {
		Track tr = tracks.getTrack(0);
		tr.go = 5;

		assertTrue(tracks.setGo(0, 5, undo));

		assertEquals(-1, tr.go);
	}

	// --- trackCopy / trackPaste / trackDelete / trackCut / trackCopyFromTo / trackSwapFromTo ---

	@Test
	void trackCopyAndPasteRoundTripTrackData() {
		song.getSong()[0][0] = 5;
		tracks.getTrack(5).note[0] = 9;

		song.trackCopy();

		song.getSong()[0][0] = 6; // switch active track to an empty one
		song.trackPaste();

		assertEquals(9, tracks.getTrack(6).note[0]);
	}

	@Test
	void trackDeleteClearsTheActiveTrack() {
		song.getSong()[0][0] = 5;
		tracks.getTrack(5).note[0] = 9;

		song.trackDelete();

		assertTrue(tracks.isEmptyTrack(5));
	}

	@Test
	void trackCutCopiesThenClearsTheActiveTrack() {
		song.getSong()[0][0] = 5;
		tracks.getTrack(5).note[0] = 9;

		song.trackCut();
		assertTrue(tracks.isEmptyTrack(5));

		song.getSong()[0][0] = 6;
		song.trackPaste();
		assertEquals(9, tracks.getTrack(6).note[0]); // survived via the copy step
	}

	@Test
	void trackCopyFromToCopiesTrackData() {
		tracks.getTrack(3).note[0] = 4;
		song.trackCopyFromTo(3, 9);
		assertEquals(4, tracks.getTrack(9).note[0]);
	}

	@Test
	void trackSwapFromToExchangesTrackData() {
		tracks.getTrack(3).note[0] = 4;
		tracks.getTrack(9).note[0] = 7;

		song.trackSwapFromTo(3, 9);

		assertEquals(7, tracks.getTrack(3).note[0]);
		assertEquals(4, tracks.getTrack(9).note[0]);
	}

	// --- TrackClipboard block selection (songBlockSetBegin / songBlockSetEnd /
	// blockDeselect / isBlockSelected / blockCopyToClipboard / blockPaste) ---

	@Test
	void blockSetBeginEndDeselectAndIsBlockSelectedRoundTrip() {
		song.getSong()[0][0] = 5; // a valid track at the active song position
		assertFalse(song.isBlockSelected(clipboard));

		song.songBlockSetBegin(clipboard, tracks);
		assertTrue(song.isBlockSelected(clipboard));

		song.songBlockSetEnd(clipboard, tracks);
		assertTrue(song.isBlockSelected(clipboard));

		song.blockDeselect(clipboard);
		assertFalse(song.isBlockSelected(clipboard));
	}

	@Test
	void blockPastePastesTheCopiedTrackOntoTheActiveTrack() {
		// blockPaste() reads from TrackClipboard's block-selection clipboard
		// (populated by blockCopyToClipboard() after a songBlockSetBegin/
		// songBlockSetEnd selection) - a separate mechanism from trackCopy()'s
		// whole-track clipboard, which blockPaste() does not use.
		song.getSong()[0][0] = 5;
		tracks.getTrack(5).note[0] = 3;
		song.songBlockSetBegin(clipboard, tracks);
		song.songBlockSetEnd(clipboard, tracks);
		clipboard.blockCopyToClipboard(tracks);
		clipboard.blockDeselect();

		song.getSong()[0][0] = 6; // switch the active track to an empty one

		song.blockPaste(0, clipboard, tracks, undo, 4);

		assertEquals(3, tracks.getTrack(6).note[0]);
	}

	// --- TrackClipboard.blockAllOnOff / blockExchangeClipboard / blockClear /
	// blockRestoreFromBackup / blockNoteTransposition / blockInstrumentChange /
	// blockVolumeChange ---
	// Every track defaults to len == tracks.getMaxTrackLength() (64 here)
	// via Tracks.initTracks(), so none of these tests need to set it
	// explicitly.

	@Test
	void blockAllOnOffTogglesWhetherChangesApplyToAllInstruments() {
		song.getSong()[0][0] = 5;
		tracks.getTrack(5).note[0] = 10;
		tracks.getTrack(5).instr[0] = 3;

		song.songBlockSetBegin(clipboard, tracks);
		song.songBlockSetEnd(clipboard, tracks);

		clipboard.blockAllOnOff(tracks); // all: true -> false (now only affects the matching instrument)

		clipboard.blockNoteTransposition(7 /* a different instrument */, 1, tracks);

		// Unchanged: the track's instrument (3) doesn't match the filter (7),
		// and "all instruments" is now off.
		assertEquals(10, tracks.getTrack(5).note[0]);
	}

	@Test
	void blockExchangeClipboardSwapsTrackAndClipboardData() {
		song.getSong()[0][0] = 5;
		tracks.getTrack(5).note[0] = 3;
		song.songBlockSetBegin(clipboard, tracks);
		song.songBlockSetEnd(clipboard, tracks);
		clipboard.blockCopyToClipboard(tracks); // clipboard now holds note=3 at position 0

		tracks.getTrack(5).note[0] = 9; // change the live track's note

		int len = clipboard.blockExchangeClipboard(tracks);

		assertEquals(1, len); // one line in the block
		assertEquals(3, tracks.getTrack(5).note[0]); // the clipboard's old data is swapped back in
	}

	@Test
	void blockClearErasesDataWithinTheSelectedRange() {
		song.getSong()[0][0] = 5;
		tracks.getTrack(5).note[0] = 3;
		tracks.getTrack(5).note[1] = 7;
		tracks.getTrack(5).note[2] = 9;

		song.setActiveLine(0);
		song.songBlockSetBegin(clipboard, tracks);
		song.setActiveLine(1);
		song.songBlockSetEnd(clipboard, tracks); // selects lines 0-1

		int cleared = clipboard.blockClear(tracks);

		assertEquals(2, cleared);
		assertEquals(-1, tracks.getTrack(5).note[0]);
		assertEquals(-1, tracks.getTrack(5).note[1]);
		assertEquals(9, tracks.getTrack(5).note[2]); // outside the block, untouched
	}

	@Test
	void blockRestoreFromBackupRestoresTheTrackAsItWasWhenSelected() {
		song.getSong()[0][0] = 5;
		tracks.getTrack(5).note[0] = 3;

		song.songBlockSetBegin(clipboard, tracks); // backs up track 5's current state (note[0] = 3)
		song.songBlockSetEnd(clipboard, tracks);

		tracks.getTrack(5).note[0] = 99; // modify the track after selection

		boolean ok = clipboard.blockRestoreFromBackup(tracks);

		assertTrue(ok);
		assertEquals(3, tracks.getTrack(5).note[0]); // restored to the backed-up state
	}

	@Test
	void blockNoteTranspositionShiftsNotesMatchingTheInstrumentFilter() {
		song.getSong()[0][0] = 5;
		tracks.getTrack(5).note[0] = 10;
		tracks.getTrack(5).instr[0] = 3;

		song.songBlockSetBegin(clipboard, tracks);
		song.songBlockSetEnd(clipboard, tracks);

		clipboard.blockNoteTransposition(3 /* instrument filter, matches */, 2 /* +2 semitones */, tracks);

		assertEquals(12, tracks.getTrack(5).note[0]);
	}

	@Test
	void blockInstrumentChangeShiftsInstrumentsMatchingTheFilter() {
		song.getSong()[0][0] = 5;
		tracks.getTrack(5).instr[0] = 3;

		song.songBlockSetBegin(clipboard, tracks);
		song.songBlockSetEnd(clipboard, tracks);

		clipboard.blockInstrumentChange(3 /* instrument filter, matches */, 1, tracks);

		assertEquals(4, tracks.getTrack(5).instr[0]);
	}

	@Test
	void blockVolumeChangeShiftsVolumeForTheLastSeenInstrument() {
		song.getSong()[0][0] = 5;
		tracks.getTrack(5).instr[0] = 3;
		tracks.getTrack(5).volume[0] = 5;

		song.songBlockSetBegin(clipboard, tracks);
		song.songBlockSetEnd(clipboard, tracks);

		clipboard.blockVolumeChange(3 /* instrument filter, matches */, 4, tracks);

		assertEquals(9, tracks.getTrack(5).volume[0]);
	}

	// --- instrCopy / instrCut / instrDelete ---
	// Instruments.clearInstrument() has real behavior here (unlike the C++
	// test binary, which link-time-stubs it as a no-op for this one test
	// file - see plans/JAVA_PORT_PLAN.md's ActiveInstrSet note for the same
	// pattern), so these characterize a bit more than their C++ counterparts,
	// but the assertions themselves (active instrument index unaffected)
	// still hold either way.

	@Test
	void instrCopyDoesNotCrashOrChangeActiveInstrument() {
		song.activeInstrSet(5, false);
		song.instrCopy();
		assertEquals(5, song.getActiveInstr());
	}

	@Test
	void instrDeleteDoesNotCrash() {
		song.activeInstrSet(5, false);
		song.instrDelete();
		assertEquals(5, song.getActiveInstr());
	}

	@Test
	void instrCutDoesNotCrash() {
		song.activeInstrSet(5, false);
		song.instrCut();
		assertEquals(5, song.getActiveInstr());
	}

	// --- songCopyLine / songPasteLine / songClearLine ---

	@Test
	void songCopyLineAndPasteLineRoundTripLineData() {
		song.getSong()[0][0] = 5;
		song.songCopyLine(4);

		song.getSong()[0][0] = -1; // overwrite before pasting back

		song.songPasteLine(undo, 4);

		assertEquals(5, song.getSong()[0][0]);
	}

	@Test
	void songClearLineBlanksTheActiveLine() {
		song.getSong()[0][0] = 5;
		song.songTrackGoOnOff(undo); // also set a GO to confirm it gets cleared too

		song.songClearLine(undo, 4);

		assertEquals(-1, song.getSong()[0][0]);
		assertEquals(-1, song.songGetGo());
	}

	// --- getEffectiveMaxtracklen / getSmallestMaxtracklen / changeMaxtracklen ---

	@Test
	void getSmallestMaxtracklenReturnsShortestTrackOnTheLine() {
		song.getSong()[0][0] = 0;
		song.getSong()[0][1] = 1;
		tracks.getTrack(0).len = 32;
		tracks.getTrack(1).len = 16;

		assertEquals(16, song.getSmallestMaxtracklen(0, 4));
	}

	@Test
	void getSmallestMaxtracklenReturnsZeroForAGotoLine() {
		song.songTrackGoOnOff(undo);
		assertEquals(0, song.getSmallestMaxtracklen(0, 4));
	}

	@Test
	void getEffectiveMaxtracklenReturnsTheLargestShortestLineAcrossTheSong() {
		song.getSong()[0][0] = 0;
		tracks.getTrack(0).len = 40;

		assertEquals(40, song.getEffectiveMaxtracklen(4));
	}

	@Test
	void changeMaxtracklenShortensLongerTracksAndUpdatesTheGlobalLength() {
		tracks.getTrack(0).len = 64;
		tracks.getTrack(0).note[50] = 1;

		song.changeMaxtracklen(32);

		assertEquals(32, tracks.getTrack(0).len);
		assertEquals(-1, tracks.getTrack(0).go);
		assertEquals(32, tracks.getMaxTrackLength());
	}

	// --- songClearUnusedTracksAndParts / songClearDuplicatedTracks / songClearUnusedTracks ---

	@Test
	void songClearUnusedTracksAndPartsDeletesTracksNotReferencedInTheSong() {
		// Track 0 is referenced by the song; track 1 is not and has data.
		song.getSong()[0][0] = 0;
		tracks.getTrack(1).note[0] = 1;

		Song.ClearUnusedResult result = song.songClearUnusedTracksAndParts(4);

		assertEquals(1, result.clearedTracks());
		assertTrue(tracks.isEmptyTrack(1));
	}

	@Test
	void songClearDuplicatedTracksMergesIdenticalTracksAndRemapsTheSong() {
		tracks.getTrack(0).note[0] = 5;
		tracks.getTrack(1).note[0] = 5; // identical to track 0
		song.getSong()[0][0] = 1; // song points at the duplicate

		int cleared = song.songClearDuplicatedTracks(4);

		assertEquals(1, cleared);
		assertEquals(0, song.getSong()[0][0]); // remapped to the surviving track
	}

	@Test
	void songClearUnusedTracksDeletesTracksNotReferencedInTheSong() {
		song.getSong()[0][0] = 0;
		tracks.getTrack(1).note[0] = 1;

		int cleared = song.songClearUnusedTracks(4);

		assertEquals(1, cleared);
		assertTrue(tracks.isEmptyTrack(1));
	}

	// --- tracksAllBuildLoops / tracksAllExpandLoops ---
	// Both call stop() first, which is a no-op here since nothing calls
	// setPlayMode(PLAY_SONG) on "song" first.

	@Test
	void tracksAllBuildLoopsFindsARepeatingPatternAndShortensTheTrack() {
		Track tr = tracks.getTrack(0);
		for (int i = 0; i < Track.TRACKLEN; i++) {
			tr.note[i] = 5; // a full-length track repeating every line
		}

		Song.TracksAllLoopResult result = song.tracksAllBuildLoops(undo);

		assertEquals(1, result.tracksModified());
		assertEquals(63, result.beatsOrLoops());
		assertEquals(1, tr.len);
		assertEquals(0, tr.go);
	}

	@Test
	void tracksAllExpandLoopsExpandsALoopingTrackToFullLength() {
		Track tr = tracks.getTrack(0);
		tr.len = 2;
		tr.go = 0;
		tr.note[0] = 7;
		tr.note[1] = 8;

		Song.TracksAllLoopResult result = song.tracksAllExpandLoops(undo);

		assertEquals(1, result.tracksModified());
		assertEquals(62, result.beatsOrLoops());
		assertEquals(tracks.getMaxTrackLength(), tr.len);
		assertEquals(-1, tr.go);
		assertEquals(8, tr.note[63]); // (63 - 2) % 2 == 1 -> repeats note[1]
	}

	// --- renumberAllTracks ---

	@Test
	void renumberAllTracksByColumnsMovesTheFirstUsedTrackToZero() {
		song.getSong()[0][0] = 5;
		tracks.getTrack(5).note[0] = 9;

		song.renumberAllTracks(1, 4); // 1 = vertically in columns

		assertEquals(9, tracks.getTrack(0).note[0]);
		assertEquals(0, song.getSong()[0][0]);
	}

	// --- clearAllInstrumentsUnusedInAnyTrack ---

	@Test
	void clearAllInstrumentsUnusedInAnyTrackCountsInstrumentsNotReferencedByAnyTrack() {
		tracks.getTrack(0).len = 4;
		tracks.getTrack(0).instr[0] = 2; // instrument 2 is used

		instruments.getInstrument(7).parameters[0] = 1; // instrument 7 is unused but not empty

		int cleared = song.clearAllInstrumentsUnusedInAnyTrack();

		assertEquals(1, cleared);
	}

	// --- renumberAllInstruments ---

	@Test
	void renumberAllInstrumentsType1RemovesGapsInUsageOrder() {
		tracks.getTrack(0).len = 4;
		tracks.getTrack(0).instr[0] = 5; // only instrument 5 is used, elsewhere is a gap

		setName(instruments.getInstrument(5).name, "Lead");

		song.renumberAllInstruments(1); // 1 = remove gaps

		assertEquals("Lead", nameToString(instruments.getInstrument(0).name));
		assertEquals(0, tracks.getTrack(0).instr[0]); // remapped to the new index
	}

	@Test
	void renumberAllInstrumentsType2OrdersByFirstUseInTracks() {
		tracks.getTrack(0).len = 4;
		tracks.getTrack(0).instr[0] = 3;
		tracks.getTrack(0).instr[1] = 1; // used second, but has a lower instrument number

		setName(instruments.getInstrument(3).name, "First");
		setName(instruments.getInstrument(1).name, "Second");

		song.renumberAllInstruments(2); // 2 = order by usage in tracks

		assertEquals("First", nameToString(instruments.getInstrument(0).name));
		assertEquals("Second", nameToString(instruments.getInstrument(1).name));
		assertEquals(0, tracks.getTrack(0).instr[0]);
		assertEquals(1, tracks.getTrack(0).instr[1]);
	}

	private static void setName(char[] name, String value) {
		for (int i = 0; i < value.length(); i++) {
			name[i] = value.charAt(i);
		}
	}

	private static String nameToString(char[] name) {
		int end = 0;
		while (end < name.length && name[end] != '\0') {
			end++;
		}
		return new String(name, 0, end);
	}

	// --- tracksOrderChangeApply ---

	@Test
	void tracksOrderChangeApplyReordersAndClearsColumnsPerMapping() {
		song.getSong()[0][0] = 10;
		song.getSong()[0][1] = 20;
		song.getSong()[0][2] = 30;
		song.getSong()[0][3] = 40;
		song.getSong()[1][0] = 11;
		song.getSong()[1][1] = 21;
		song.getSong()[1][2] = 31;
		song.getSong()[1][3] = 41;

		int[] tracksorder = { 1, 0, -1, 3, -1, -1, -1, -1 };
		song.tracksOrderChangeApply(0, 1, tracksorder, 4);

		assertEquals(20, song.getSong()[0][0]); // new col0 <- old col1
		assertEquals(10, song.getSong()[0][1]); // new col1 <- old col0
		assertEquals(-1, song.getSong()[0][2]); // cleared
		assertEquals(40, song.getSong()[0][3]); // new col3 <- old col3 (unchanged)

		assertEquals(21, song.getSong()[1][0]);
		assertEquals(11, song.getSong()[1][1]);
		assertEquals(-1, song.getSong()[1][2]);
		assertEquals(41, song.getSong()[1][3]);
	}

	// --- setBookmark ---

	@Test
	void setBookmarkStoresCurrentPositionWhenSpeedIsValid() {
		song.songSetActiveLine(3);
		song.setActiveLine(4);
		SongInfo info = new SongInfo();
		song.getSongInfoPars(info);
		info.speed = 6;
		song.setSongInfoPars(info);

		assertTrue(song.setBookmark());
		assertEquals(3, song.getBookmark().songline);
		assertEquals(4, song.getBookmark().trackline);
		assertEquals(6, song.getBookmark().speed);
	}

	@Test
	void setBookmarkFailsWhenTheActiveTrackLineIsOutOfBounds() {
		song.setActiveLine(100); // beyond tracks.getMaxTrackLength()'s default of 64
		assertFalse(song.setBookmark());
	}

	// --- setTracks / setNTSC ---
	// Both drop C++'s conditional ReInitSound() call - real
	// g_AtariTrackerDriver/g_Pokey hardware simulation, no-op-stubbed in
	// every C++ test too.

	@Test
	void setTracksReturnsTheNewTrackCount() {
		assertEquals(8, song.setTracks(8));
		assertEquals(4, song.setTracks(4));
	}

	// --- resetTuningVariables ---

	@Test
	void resetTuningVariablesUsesTheNtscOrPalBaseTuning() {
		TuningSettings tuning = new TuningSettings();
		TuningRatios tuningRatios = new TuningRatios();

		song.setNTSC(true);
		song.resetTuningVariables(tuning, tuningRatios);
		assertEquals(444.895778867913, tuning.basetuning, 0.0);
		assertEquals(3, tuning.basenote);
		assertEquals(0, tuning.temperament);

		song.setNTSC(false);
		song.resetTuningVariables(tuning, tuningRatios);
		assertEquals(440.83751645933, tuning.basetuning, 0.0);
	}

	// --- makeModule / decodeModule ---
	// Round-trip test, mirroring songToAta/ataToSong's approach in SongTest:
	// lets the real encode/decode logic prove itself internally consistent
	// rather than hand-deriving the RMT header's byte layout.

	@Test
	void makeModuleAndDecodeModuleRoundTripASimpleSong() {
		// mainSpeed/instrumentSpeed default to 0, but decodeModule() rejects
		// a decoded speed byte of 0 as invalid (there can be no zero speed) -
		// give them valid values first.
		SongInfo info = new SongInfo();
		song.getSongInfoPars(info);
		info.mainSpeed = 6;
		info.instrumentSpeed = 2;
		song.setSongInfoPars(info);

		song.getSong()[0][0] = 5; // song line 0, column 0 references track 5

		Track tr = tracks.getTrack(5);
		tr.len = 4;
		tr.note[0] = 10;
		tr.instr[0] = 2;
		tr.volume[0] = 8;

		setName(instruments.getInstrument(2).name, "Lead");
		instruments.getInstrument(2).parameters[0] = 5;

		byte[] mem = new byte[8192];
		byte[] instrSavedFlags = new byte[Instruments.INSTRSNUM];
		byte[] trackSavedFlags = new byte[Tracks.TRACKSNUM];

		int endAddr = song.makeModule(mem, 0, SongIOType.RMT, instrSavedFlags, trackSavedFlags, 4);
		assertTrue(endAddr > 0);
		assertTrue(endAddr <= mem.length);

		Song decoded = new Song(instruments, tracks);
		byte[] instrLoadedFlags = new byte[Instruments.INSTRSNUM];
		byte[] trackLoadedFlags = new byte[Tracks.TRACKSNUM];
		Song.DecodeModuleResult result = decoded.decodeModule(mem, 0, endAddr, instrLoadedFlags, trackLoadedFlags);

		assertEquals(RmtFormatVersion.V1, result.version());
		assertEquals(5, decoded.getSong()[0][0]);
		// decodeModule() decodes back into the same shared tracks/instruments
		// it was encoded from (there's only one in production too).
		assertEquals(10, tracks.getTrack(5).note[0]);
		assertEquals(2, tracks.getTrack(5).instr[0]);
		assertEquals(8, tracks.getTrack(5).volume[0]);
		assertEquals("Lead", nameToString(instruments.getInstrument(2).name));
		assertEquals(5, instruments.getInstrument(2).parameters[0]);
	}

	// --- clearSong ---
	// clearSong()'s only real hazard in C++ (a real MFC AfxGetMainWnd()/
	// CMainFrame call to sync a UI combo box) has no Java equivalent and
	// isn't reproduced (see Song.clearSong()'s own javadoc for the full list
	// of omitted globals/calls - none of them are observed by any test).

	@Test
	void clearSongResetsSongDataAndPositionBackToDefaults() {
		song.getSong()[0][0] = 5;
		song.getSongGo()[2] = 7;

		song.songSetActiveLine(3);
		song.setActiveLine(4);
		song.songSetPlayLine(3);
		song.setPlayLine(4);
		song.activeInstrSet(5, false);
		song.setFollowPlayMode(false);

		SongInfo info = new SongInfo();
		song.getSongInfoPars(info);
		info.speed = 6;
		info.mainSpeed = 6;
		info.instrumentSpeed = 3;
		setName(info.songName, "Custom");
		song.setSongInfoPars(info);

		assertTrue(song.setBookmark());

		song.clearSong(4, undo);

		assertEquals(-1, song.getSong()[0][0]);
		assertEquals(-1, song.getSongGo()[2]);
		assertEquals(0, song.songGetActiveLine());
		assertEquals(0, song.getActiveLine());
		assertEquals(0, song.songGetPlayLine());
		assertEquals(0, song.getPlayLine());
		assertEquals(0, song.getActiveInstr());
		assertTrue(song.getFollowPlayMode());

		SongInfo cleared = new SongInfo();
		song.getSongInfoPars(cleared);
		assertEquals(16, cleared.speed);
		assertEquals(16, cleared.mainSpeed);
		assertEquals(1, cleared.instrumentSpeed);
		assertEquals("Noname song", song.getName()); // getName() trims trailing padding; nameToString() doesn't

		assertEquals(-1, song.getBookmark().songline);
		assertEquals(-1, song.getBookmark().trackline);
		assertEquals(-1, song.getBookmark().speed);

		assertEquals("", song.getFilename());
		assertEquals(SongIOType.NONE, song.getIOType());
	}

	@Test
	void clearSongSetsTheTrackCount() {
		assertEquals(8, song.clearSong(8, undo));
		assertEquals(4, song.clearSong(4, undo));
	}

	// --- saveTxt ---

	@Test
	void saveTxtWritesModuleHeaderAndSongLineData() {
		SongInfo info = new SongInfo();
		song.getSongInfoPars(info);
		info.mainSpeed = 6;
		info.instrumentSpeed = 2;
		song.setSongInfoPars(info);

		song.getSong()[0][0] = 5; // only line 0 has data - the rest stay "--"

		String text = song.saveTxt(4);

		assertTrue(text.contains("[MODULE]"));
		assertTrue(text.contains("[SONG]"));
		assertTrue(text.contains("05 -- -- --\n")); // track 05 in column 0, columns 1-3 empty
	}

	// --- loadTxt ---
	// Round-trips through saveTxt, same philosophy as loadRMT's round trip
	// below. loadTxt() has no unconditional-success dialog to avoid (unlike
	// loadRMW's version-mismatch MessageBox) - it only ever fails silently by
	// leaving fields at their clearSong() defaults for a segment it doesn't
	// recognize.

	@Test
	void loadTxtRoundTripsTheModuleHeaderAndTheSongData() {
		SongInfo info = new SongInfo();
		song.getSongInfoPars(info);
		info.mainSpeed = 6;
		info.instrumentSpeed = 2;
		setName(info.songName, "TestSong");
		song.setSongInfoPars(info);

		song.getSong()[0][0] = 5;

		String text = song.saveTxt(4);

		Song loaded = new Song(instruments, tracks);
		Undo loadedUndo = new Undo(tracks, instruments, loaded, new TrackClipboard());
		Song.LoadTxtResult result = loaded.loadTxt(text, loadedUndo);

		// The [MODULE] header block parses correctly...
		assertEquals(4, result.tracks4_8()); // RMT: 04 round-trips tracks4_8 via setTracks()
		SongInfo loadedInfo = new SongInfo();
		loaded.getSongInfoPars(loadedInfo);
		assertEquals(6, loadedInfo.mainSpeed);
		assertEquals(2, loadedInfo.instrumentSpeed);
		assertEquals("TestSong", loaded.getName());

		// ...and so does "[SONG]", restoring the saved track 5.
		assertEquals(5, loaded.getSong()[0][0]);
	}

	// Mirrors src/cpp/test/SongEditingTests.cpp's
	// SaveTxtAndLoadTxtRoundTripNonEmptyInstrumentAndTrack - exercises
	// Instruments/Tracks' saveAllTxt/loadInstrumentTxt/loadTrackTxt for
	// real (every other TXT round-trip test above has no non-empty
	// instruments/tracks, so saveAllTxt's non-empty-only rule never
	// actually emits an "[INSTRUMENT]"/"[TRACK]" segment). instruments/
	// tracks are the same objects both `song` and `loaded` share
	// (loadInstrumentTxt/loadTrackTxt write through them directly, not
	// through any per-Song state) - matching loadRMTDecodesTheModuleAndNamesBlocks's
	// own established pattern.
	@Test
	void saveTxtAndLoadTxtRoundTripNonEmptyInstrumentAndTrack() {
		SongInfo info = new SongInfo();
		song.getSongInfoPars(info);
		info.mainSpeed = 6;
		info.instrumentSpeed = 2;
		song.setSongInfoPars(info);

		song.getSong()[0][0] = 5;

		Instrument instr = instruments.getInstrument(2);
		setName(instr.name, "Lead");
		instr.parameters[Instrument.PAR_ENV_LENGTH] = 2;
		instr.envelope[0][EnvelopeParameter.VOLUMEL] = 10;

		Track tr = tracks.getTrack(5);
		tr.len = 2;
		tr.note[0] = 10;
		tr.instr[0] = 2;
		tr.volume[0] = 8;

		String text = song.saveTxt(4);
		assertTrue(text.contains("[INSTRUMENT]"));
		assertTrue(text.contains("[TRACK]"));

		Song loaded = new Song(instruments, tracks);
		Undo loadedUndo = new Undo(tracks, instruments, loaded, new TrackClipboard());
		assertTrue(loaded.loadTxt(text, loadedUndo).tracks4_8() > 0);

		assertEquals("Lead", new String(instruments.getInstrument(2).name).stripTrailing());
		assertEquals(10, instruments.getInstrument(2).envelope[0][EnvelopeParameter.VOLUMEL]);

		assertEquals(2, tracks.getTrack(5).len);
		assertEquals(10, tracks.getTrack(5).note[0]);
		assertEquals(2, tracks.getTrack(5).instr[0]);
		assertEquals(8, tracks.getTrack(5).volume[0]);
	}

	// --- saveRMW ---

	@Test
	void saveRMWWritesTheVersionStringFirst() {
		byte[] out = song.saveRMW(4);

		String prefix = new String(out, 0, RmtVersion.RMT_VERSION_STRING.length(), java.nio.charset.StandardCharsets.US_ASCII);
		assertEquals(RmtVersion.RMT_VERSION_STRING, prefix);
	}

	// FIXED BUG (was pre-existing, from when this project was 32-bit - see
	// plans/JAVA_SONGEDITING_PLAN.md's sub-batch 8 entry): SaveRMW/LoadRMW's
	// main-parameters loop used to write/read 8 bytes per parameter instead
	// of 4 on this 64-bit C++ build. This Java port always writes exactly 4
	// bytes per parameter (no pointer-array sizeof to get wrong in the first
	// place), but this test proves the exact byte layout directly anyway,
	// mirroring the C++ regression test added alongside that fix.
	@Test
	void saveRMWWritesEachMainParameterAsExactlyFourBytes() {
		song.getSong()[0][0] = 5; // the first value after the main-parameters block

		byte[] out = song.saveRMW(4);

		int offset = RmtVersion.RMT_VERSION_STRING.length() + 1; // version line + '\n'
		offset += SongInfo.SONG_NAME_MAX_LEN + 1; // songName plus the extra C++ byte

		int paramCount = readIntLE(out, offset);
		offset += 4;
		assertEquals(31, paramCount);

		int firstParam = readIntLE(out, offset); // tracks4_8, passed as 4
		assertEquals(4, firstParam);

		// If each parameter were (incorrectly) 8 bytes, this offset would land
		// in the middle of the main-parameters block instead of at song[0][0].
		offset += paramCount * 4;
		int firstSongValue = readIntLE(out, offset);
		assertEquals(5, firstSongValue);
	}

	private static int readIntLE(byte[] data, int pos) {
		return (data[pos] & 0xFF) | ((data[pos + 1] & 0xFF) << 8) | ((data[pos + 2] & 0xFF) << 16) | ((data[pos + 3] & 0xFF) << 24);
	}

	// --- loadRMW ---
	// Round-trips through saveRMW. loadRMW's version-mismatch branch (a real
	// MessageBox, unconditional on the error path) is deliberately never
	// exercised - only ever fed a byte array that starts with a matching
	// version string, same "avoidable with valid test data" treatment as
	// loadRMT's guard-only error branches.

	@Test
	void loadRMWRoundTripsSongDataThroughSaveRMW() {
		SongInfo info = new SongInfo();
		song.getSongInfoPars(info);
		info.mainSpeed = 6;
		info.instrumentSpeed = 2;
		setName(info.songName, "TestSong");
		song.setSongInfoPars(info);

		song.getSong()[0][0] = 5;
		song.getSongGo()[2] = 7;

		byte[] out = song.saveRMW(4);

		Song loaded = new Song(instruments, tracks);
		Undo loadedUndo = new Undo(tracks, instruments, loaded, new TrackClipboard());
		Song.LoadRmwResult result = loaded.loadRMW(out, loadedUndo);

		assertTrue(result.success());
		assertEquals(5, loaded.getSong()[0][0]);
		assertEquals(7, loaded.getSongGo()[2]);

		SongInfo loadedInfo = new SongInfo();
		loaded.getSongInfoPars(loadedInfo);
		assertEquals(6, loadedInfo.mainSpeed);
		assertEquals(2, loadedInfo.instrumentSpeed);
		assertEquals("TestSong", loaded.getName());
	}

	// Mirrors src/cpp/test/SongEditingTests.cpp's
	// SaveRMWAndLoadRMWRoundTripNonEmptyInstrumentAndTrack. Unlike TXT's
	// saveAllTxt, RMW's always writes every instrument/track
	// unconditionally, so this is really about proving non-trivial data
	// (not just all-default/empty content) survives the round trip.
	@Test
	void saveRMWAndLoadRMWRoundTripNonEmptyInstrumentAndTrack() {
		SongInfo info = new SongInfo();
		song.getSongInfoPars(info);
		info.mainSpeed = 6;
		info.instrumentSpeed = 2;
		song.setSongInfoPars(info);

		song.getSong()[0][0] = 5;

		Instrument instr = instruments.getInstrument(2);
		setName(instr.name, "Lead");
		instr.parameters[Instrument.PAR_ENV_LENGTH] = 2;
		instr.envelope[0][EnvelopeParameter.VOLUMEL] = 10;

		Track tr = tracks.getTrack(5);
		tr.len = 2;
		tr.note[0] = 10;
		tr.instr[0] = 2;
		tr.volume[0] = 8;

		byte[] out = song.saveRMW(4);

		Song loaded = new Song(instruments, tracks);
		Undo loadedUndo = new Undo(tracks, instruments, loaded, new TrackClipboard());
		assertTrue(loaded.loadRMW(out, loadedUndo).success());

		assertEquals("Lead", nameToString(instruments.getInstrument(2).name));
		assertEquals(10, instruments.getInstrument(2).envelope[0][EnvelopeParameter.VOLUMEL]);

		assertEquals(2, tracks.getTrack(5).len);
		assertEquals(10, tracks.getTrack(5).note[0]);
		assertEquals(2, tracks.getTrack(5).instr[0]);
		assertEquals(8, tracks.getTrack(5).volume[0]);
	}

	// --- loadRMT ---
	// Builds a valid two-block RMT file in memory (module block via
	// makeModule, names block by hand) rather than hand-deriving the RMT
	// header's byte layout - same round-trip philosophy as
	// makeModule/decodeModule above.

	private static void writeBinaryBlock(java.io.ByteArrayOutputStream out, byte[] mem, int fromAddr, int toAddr) {
		out.write(fromAddr & 0xFF);
		out.write((fromAddr >> 8) & 0xFF);
		out.write(toAddr & 0xFF);
		out.write((toAddr >> 8) & 0xFF);
		out.write(mem, fromAddr, toAddr - fromAddr + 1);
	}

	@Test
	void loadRMTDecodesTheModuleAndNamesBlocks() {
		SongInfo info = new SongInfo();
		song.getSongInfoPars(info);
		info.mainSpeed = 6;
		info.instrumentSpeed = 2;
		song.setSongInfoPars(info);

		song.getSong()[0][0] = 5;
		tracks.getTrack(5).len = 4;
		tracks.getTrack(5).note[0] = 10;
		tracks.getTrack(5).instr[0] = 2;
		setName(instruments.getInstrument(2).name, "Lead");

		byte[] mem = new byte[8192];
		byte[] instrSavedFlags = new byte[Instruments.INSTRSNUM];
		byte[] trackSavedFlags = new byte[Tracks.TRACKSNUM];
		int fromAddr = 0x100;
		int endAddr = song.makeModule(mem, fromAddr, SongIOType.RMT, instrSavedFlags, trackSavedFlags, 4);
		assertTrue(endAddr > 0);

		java.io.ByteArrayOutputStream blocks = new java.io.ByteArrayOutputStream();
		writeBinaryBlock(blocks, mem, fromAddr, endAddr - 1);

		// Names block: song name, then the name of each *loaded* instrument
		// (in index order) - here just instrument 2, since it's the only one used.
		byte[] namesMem = new byte[64];
		int p = 0;
		for (char c : "TestSong".toCharArray()) {
			namesMem[p++] = (byte) c;
		}
		namesMem[p++] = 0;
		for (char c : "Lead".toCharArray()) {
			namesMem[p++] = (byte) c;
		}
		namesMem[p++] = 0;
		writeBinaryBlock(blocks, namesMem, 0, p - 1);

		Song decoded = new Song(instruments, tracks);
		Song.LoadRmtResult loaded = decoded.loadRMT(blocks.toByteArray());
		assertTrue(loaded.success());
		assertEquals(4, loaded.tracks4_8());

		assertEquals("TestSong", decoded.getName());
		// Unlike getName(), the raw instrument name field isn't trimmed -
		// loadRMT() fills the remainder with spaces up to the name's own length.
		assertEquals("Lead", new String(instruments.getInstrument(2).name).stripTrailing());
	}

	// --- RmtExporter.exportAsRMT ---
	// Round-trips through loadRMT - the two blocks it writes are exactly what
	// loadRMT expects, so this exercises exportAsRMT for real rather than
	// hand-deriving the RMT header's byte layout, same philosophy as loadRMT's
	// own test above (which instead builds those blocks by hand).

	@Test
	void exportAsRMTRoundTripsThroughLoadRMT() {
		SongInfo info = new SongInfo();
		song.getSongInfoPars(info);
		info.mainSpeed = 6; // decodeModule rejects a zero speed byte as invalid
		info.instrumentSpeed = 2;
		setName(info.songName, "TestSong");
		song.setSongInfoPars(info);

		song.getSong()[0][0] = 5;
		tracks.getTrack(5).len = 4;
		tracks.getTrack(5).note[0] = 10;
		tracks.getTrack(5).instr[0] = 2;
		setName(instruments.getInstrument(2).name, "Lead");

		byte[] mem = new byte[Atari.MEMORY_SIZE];
		byte[] instrSavedFlags = new byte[Instruments.INSTRSNUM];
		byte[] trackSavedFlags = new byte[Tracks.TRACKSNUM];
		int targetAddrOfModule = 0x4000;
		int firstByteAfterModule = song.makeModule(mem, targetAddrOfModule, SongIOType.RMT, instrSavedFlags, trackSavedFlags, 4);
		assertTrue(firstByteAfterModule > 0);

		byte[] out = RmtExporter.exportAsRMT(song, instruments, mem, targetAddrOfModule, firstByteAfterModule, instrSavedFlags);

		Song decoded = new Song(instruments, tracks);
		assertTrue(decoded.loadRMT(out).success());

		assertEquals("TestSong", decoded.getName());
		assertEquals("Lead", new String(instruments.getInstrument(2).name).stripTrailing());
	}

	// --- RmtExporter.exportAsStrippedRMTApply ---
	// Decoded directly via AtariIO.loadBinaryBlock()/Song.decodeModule() rather
	// than loadRMT(): exportAsStrippedRMTApply() only ever writes a single
	// block (no names block), and loadRMT() shows a real, blocking "Info"
	// MessageBox when it doesn't find a second block (confirmed firsthand on
	// the C++ side - see plans/NOTES.md), so it's never fed a single-block input.

	@Test
	void exportAsStrippedRMTApplyWritesADecodableModuleBlock() {
		SongInfo info = new SongInfo();
		song.getSongInfoPars(info);
		info.mainSpeed = 6; // decodeModule rejects a zero speed byte as invalid
		info.instrumentSpeed = 2;
		song.setSongInfoPars(info);

		song.getSong()[0][0] = 5;
		tracks.getTrack(5).len = 4;
		tracks.getTrack(5).note[0] = 10;
		tracks.getTrack(5).instr[0] = 2;

		byte[] out = RmtExporter.exportAsStrippedRMTApply(song, 0x4000, false, 4);
		assertTrue(out != null);

		byte[] mem = new byte[Atari.MEMORY_SIZE];
		AtariIO.BinaryBlockResult block = AtariIO.loadBinaryBlock(out, 0, mem);
		assertTrue(block.length() > 0);
		assertEquals(0x4000, block.fromAddr());

		byte[] instrLoadedFlags = new byte[Instruments.INSTRSNUM];
		byte[] trackLoadedFlags = new byte[Tracks.TRACKSNUM];
		Song decoded = new Song(instruments, tracks);
		Song.DecodeModuleResult result = decoded.decodeModule(mem, block.fromAddr(), block.toAddr() + 1, instrLoadedFlags, trackLoadedFlags);
		assertTrue(result.version() > 0);
	}

	@Test
	void exportAsStrippedRMTApplyWritesADecodableModuleBlockWhenSfxSupportIsOn() {
		SongInfo info = new SongInfo();
		song.getSongInfoPars(info);
		info.mainSpeed = 6;
		info.instrumentSpeed = 2;
		song.setSongInfoPars(info);

		song.getSong()[0][0] = 5;
		tracks.getTrack(5).len = 4;
		tracks.getTrack(5).note[0] = 10;
		tracks.getTrack(5).instr[0] = 2;

		byte[] out = RmtExporter.exportAsStrippedRMTApply(song, 0x5000, true, 4);
		assertTrue(out != null);

		byte[] mem = new byte[Atari.MEMORY_SIZE];
		AtariIO.BinaryBlockResult block = AtariIO.loadBinaryBlock(out, 0, mem);
		assertTrue(block.length() > 0);
		assertEquals(0x5000, block.fromAddr()); // sfxSupport doesn't affect the target address

		byte[] instrLoadedFlags = new byte[Instruments.INSTRSNUM];
		byte[] trackLoadedFlags = new byte[Tracks.TRACKSNUM];
		Song decoded = new Song(instruments, tracks);
		Song.DecodeModuleResult result = decoded.decodeModule(mem, block.fromAddr(), block.toAddr() + 1, instrLoadedFlags, trackLoadedFlags);
		assertTrue(result.version() > 0);
	}

	// --- AsmFileExporter.exportAsAsmApply ---
	// g_PrefixForAllAsmLabels/atariMemory are left empty here - not exercised
	// by this test (durationsType/notesIndexOrFreq are both "notes").

	@Test
	void exportAsAsmApplyWritesTracksOnlyOutput() {
		song.getSong()[0][0] = 5; // marks track 5 as "used" via markTfUsed
		Track tr = tracks.getTrack(5);
		tr.len = 2;
		tr.note[0] = 10;
		tr.instr[0] = 2;

		String out = AsmFileExporter.exportAsAsmApply(song, instruments, tracks, new byte[Atari.MEMORY_SIZE], "", 4, 1 /* Tracks only */, 1 /* notes */, 1 /* notes only */);

		assertTrue(out.contains(";ASM notation source"));
		assertTrue(out.contains(";Track $05"));
	}

	// --- AsmFileExporter.buildRelocatableAsm ---
	// Already a pure, dialog-independent function - no split needed.

	@Test
	void buildRelocatableAsmProducesAssemblerSourceForAValidModule() {
		SongInfo info = new SongInfo();
		song.getSongInfoPars(info);
		info.mainSpeed = 6;
		info.instrumentSpeed = 2;
		song.setSongInfoPars(info);

		song.getSong()[0][0] = 5;
		tracks.getTrack(5).len = 4;
		tracks.getTrack(5).note[0] = 10;
		tracks.getTrack(5).instr[0] = 2;

		byte[] mem = new byte[Atari.MEMORY_SIZE];
		byte[] instrSavedFlags = new byte[Instruments.INSTRSNUM];
		byte[] trackSavedFlags = new byte[Tracks.TRACKSNUM];
		int targetAddrOfModule = 0x4000;
		int firstByteAfterModule = song.makeModule(mem, targetAddrOfModule, SongIOType.RMT, instrSavedFlags, trackSavedFlags, 4);
		assertTrue(firstByteAfterModule > 0);

		AsmFileExporter.Result result = AsmFileExporter.buildRelocatableAsm(song, instruments, tracks, 4, mem, targetAddrOfModule, firstByteAfterModule, instrSavedFlags, "MY_SONG", "", "", "",
				AssemblerFormat.XASM, false, false, false, false);
		assertTrue(result.success());

		assertTrue(result.code().contains("MY_SONG"));
		assertTrue(result.code().contains("RMT4")); // matches the module header's "RMTx" marker (4 tracks)
	}

	// --- AsmFileExporter.exportAsRelocatableAsmForRmtPlayerApply ---
	// Extracted from ExportAsRelocatableAsmForRmtPlayer() - mostly a thin
	// wrapper around the already-tested buildRelocatableAsm() above.

	@Test
	void exportAsRelocatableAsmForRmtPlayerApplyWritesToStream() {
		SongInfo info = new SongInfo();
		song.getSongInfoPars(info);
		info.mainSpeed = 6;
		info.instrumentSpeed = 2;
		song.setSongInfoPars(info);

		song.getSong()[0][0] = 5;
		tracks.getTrack(5).len = 4;
		tracks.getTrack(5).note[0] = 10;
		tracks.getTrack(5).instr[0] = 2;

		byte[] memStripped = new byte[Atari.MEMORY_SIZE];
		byte[] instrSavedFlagsStripped = new byte[Instruments.INSTRSNUM];
		byte[] trackSavedFlagsStripped = new byte[Tracks.TRACKSNUM];
		int targetAddrOfModule = 0x4000;
		int firstByteAfterModule = song.makeModule(memStripped, targetAddrOfModule, SongIOType.RMT, instrSavedFlagsStripped, trackSavedFlagsStripped, 4);
		assertTrue(firstByteAfterModule > 0);

		// exportDescWithSFX's content doesn't matter here - sfxSupport is false below
		AsmFileExporter.RelocatableAsmExportParams params = new AsmFileExporter.RelocatableAsmExportParams("MY_SONG", false, false, false, "", "", "", AssemblerFormat.XASM, false, false, false);

		AsmFileExporter.Result result = AsmFileExporter.exportAsRelocatableAsmForRmtPlayerApply(song, instruments, tracks, 4, memStripped, targetAddrOfModule, firstByteAfterModule,
				instrSavedFlagsStripped, memStripped, targetAddrOfModule, firstByteAfterModule, instrSavedFlagsStripped, params);

		assertTrue(result.success());
		assertTrue(result.code().contains("MY_SONG"));
	}

	// --- songJump / songUp / songDown / songSubsongPrev / songSubsongNext ---
	// All conditionally call stop()/play() only inside "playMode != PLAY_STOP
	// && followplay", never taken here (playMode defaults to PLAY_STOP).

	@Test
	void songJumpMovesForwardViaSongDown() {
		song.songSetActiveLine(5);
		song.songJump(3, undo, clipboard); // toline=8>5 -> songSetActiveLine(7) then songDown() -> 8
		assertEquals(8, song.songGetActiveLine());
	}

	@Test
	void songJumpMovesBackwardViaSongUp() {
		song.songSetActiveLine(5);
		song.songJump(-3, undo, clipboard); // toline=2<5 -> songSetActiveLine(3) then songUp() -> 2
		assertEquals(2, song.songGetActiveLine());
	}

	@Test
	void songUpWrapsToTheLastLineFromLineZero() {
		song.songSetActiveLine(0);
		song.songUp(undo, clipboard);
		assertEquals(Song.SONGLEN - 1, song.songGetActiveLine());
	}

	@Test
	void songDownWrapsToLineZeroFromTheLastLine() {
		song.songSetActiveLine(Song.SONGLEN - 1);
		song.songDown(undo, clipboard);
		assertEquals(0, song.songGetActiveLine());
	}

	@Test
	void songSubsongNextJumpsToTheLineAfterTheNextGotoMarker() {
		song.songSetActiveLine(5);
		song.getSongGo()[7] = 2;
		song.songSubsongNext(undo);
		assertEquals(8, song.songGetActiveLine());
	}

	@Test
	void songSubsongPrevJumpsToTheLineAfterThePreviousGotoMarker() {
		song.songSetActiveLine(10);
		song.setActiveLine(5); // nonzero trackactiveline avoids the extra "i--" that only applies when it's exactly 0
		song.getSongGo()[7] = 3;
		song.songSubsongPrev(undo);
		assertEquals(8, song.songGetActiveLine());
		assertEquals(0, song.getActiveLine()); // trackactiveline always resets
	}

	// --- trackUp / trackDown ---
	// keyboardUpDownContinue defaults to false here, matching the C++ test
	// binary's stub default (SongEditingStub.cpp), not the real app's true
	// default (set in RmtView.cpp, not modeled - no settings dialog exists yet).

	@Test
	void trackUpMovesActiveLineUpWithinBounds() {
		song.setActiveLine(5);
		song.trackUp(2, 4, false, undo, clipboard);
		assertEquals(3, song.getActiveLine());
	}

	@Test
	void trackUpWrapsToTheBottomWhenGoingBelowZero() {
		song.setActiveLine(1);
		song.trackUp(3, 4, false, undo, clipboard); // 1-3=-2, keyboardUpDownContinue is off, so -2 + trlen(64) = 62
		assertEquals(62, song.getActiveLine());
	}

	@Test
	void trackDownMovesActiveLineDownWithinBounds() {
		song.setActiveLine(3);
		song.trackDown(2, false, 4, false, undo, clipboard); // stoponlastline=false avoids trackGetLastLine()'s -1-for-no-track edge case
		assertEquals(5, song.getActiveLine());
	}

	// --- songPrepareNewLine / songPutnewemptyunusedtrack ---

	@Test
	void songPrepareNewLineFillsTheNewLineWithUnusedTracks() {
		assertTrue(song.songPrepareNewLine(2, -1, true, undo, 4));

		// With nothing else in the song, each column gets the next free track.
		assertEquals(0, song.getSong()[2][0]);
		assertEquals(1, song.getSong()[2][1]);
		assertEquals(2, song.getSong()[2][2]);
		assertEquals(3, song.getSong()[2][3]);
	}

	@Test
	void songPutnewemptyunusedtrackAssignsAFreeTrackToTheActivePosition() {
		song.songSetActiveLine(0); // getActiveColumn() defaults to column 0

		assertTrue(song.songPutnewemptyunusedtrack(undo, 4));

		assertEquals(0, song.getSong()[0][0]); // first free track assigned
	}

	// --- songMaketracksduplicate / songswitch4_8 ---

	@Test
	void songMaketracksduplicateReturnsZeroOnAGotoLine() {
		song.songSetActiveLine(0);
		song.getSongGo()[0] = 1; // songline 0 is a goto line

		assertFalse(song.songMaketracksduplicate(undo, 4, messages));
	}

	@Test
	void songMaketracksduplicateReturnsZeroWhenNoTrackSelected() {
		song.songSetActiveLine(0); // getActiveColumn() defaults to column 0
		song.getSong()[0][0] = -1; // no track at the active position

		assertFalse(song.songMaketracksduplicate(undo, 4, messages));
	}

	@Test
	void songMaketracksduplicateDuplicatesTheTrackWhenConfirmed() {
		song.songSetActiveLine(0); // getActiveColumn() defaults to column 0
		song.getSong()[0][0] = 5; // used only here -> triggers the confirm prompt
		tracks.getTrack(5).len = 2;
		tracks.getTrack(5).note[0] = 10;
		tracks.getTrack(5).instr[0] = 2;

		messages.setTestQuestionAnswer(MessageAnswer.OK);
		assertTrue(song.songMaketracksduplicate(undo, 4, messages));

		int newTrack = song.getSong()[0][0];
		assertTrue(newTrack != 5); // moved to a different, free track
		assertEquals(10, tracks.getTrack(newTrack).note[0]); // content duplicated
		assertEquals(2, tracks.getTrack(newTrack).instr[0]);
	}

	@Test
	void songMaketracksduplicateLeavesTheTrackUnchangedWhenCancelled() {
		song.songSetActiveLine(0);
		song.getSong()[0][0] = 5;
		tracks.getTrack(5).len = 2;
		tracks.getTrack(5).note[0] = 10;

		messages.setTestQuestionAnswer(MessageAnswer.CANCEL);
		assertFalse(song.songMaketracksduplicate(undo, 4, messages));

		assertEquals(5, song.getSong()[0][0]); // unchanged
	}

	@Test
	void songswitch4_8LeavesStateUnchangedWhenCancelled() {
		int tracks4_8 = song.setTracks(8);
		song.getSong()[0][4] = 5; // an R1 column entry that would be erased if confirmed

		messages.setTestQuestionAnswer(MessageAnswer.CANCEL);
		tracks4_8 = song.songswitch4_8(tracks4_8, 4, undo, messages);

		assertEquals(8, tracks4_8); // unchanged
		assertEquals(5, song.getSong()[0][4]); // unchanged
	}

	@Test
	void songswitch4_8ClearsStereoColumnsWhenConfirmed() {
		int tracks4_8 = song.setTracks(8);
		song.getSong()[0][4] = 5; // R1 column entry

		messages.setTestQuestionAnswer(MessageAnswer.YES);
		tracks4_8 = song.songswitch4_8(tracks4_8, 4, undo, messages);

		assertEquals(4, tracks4_8);
		assertEquals(-1, song.getSong()[0][4]); // R1-R4 columns cleared
	}

	@Test
	void songswitch4_8SwitchesToStereoWhenConfirmed() {
		int tracks4_8 = 4;
		messages.setTestQuestionAnswer(MessageAnswer.YES);
		tracks4_8 = song.songswitch4_8(tracks4_8, 8, undo, messages);

		assertEquals(8, tracks4_8);
	}

	// --- playPressedTones ---
	// Confirmed safe: AtariTrackerDriver's methods only need the rmtInstr
	// bookkeeping and the already-stubbed no-op JSR call (see
	// AtariTrackerDriver's class javadoc).

	@Test
	void playPressedTonesRecordsTheInstrumentAndConsumesThePendingState() {
		song.setPlayPressedTonesTNIV(0, 5, 2, 10); // track 0: note 5, instr 2, volume 10

		assertTrue(song.playPressedTones(atariTrackerDriver));
		assertEquals(2, atariTrackerDriver.getRmtInstrument(0));

		// The pending state was consumed (volume reset to -1) - a second call
		// has nothing left to play.
		assertTrue(song.playPressedTones(atariTrackerDriver));
		assertEquals(2, atariTrackerDriver.getRmtInstrument(0)); // unchanged - nothing pending
	}

	// --- instrPaste ---

	@Test
	void instrPasteNormalPasteCopiesTheClipboardIntoTheActiveInstrument() {
		song.activeInstrSet(3, false);
		setName(instruments.getInstrument(3).name, "Lead");
		song.instrCopy(); // populates instrClipboard for real

		song.activeInstrSet(5, false); // switch to a different, empty instrument
		song.instrPaste(0, undo, atariTrackerDriver); // 0 = normal paste

		assertEquals("Lead", nameToString(instruments.getInstrument(5).name));
		assertEquals(InstrumentSection.NAME, instruments.getInstrument(5).activeEditSection);
	}

	// --- play ---
	// Also exercises the real playBeat()/g_SongTimer-equivalent no-op path
	// (see play()'s/playBeat()'s class javadoc).

	@Test
	void playSetsPlayModeAndInitializesPlayLines() {
		song.songSetActiveLine(3);
		song.setActiveLine(4);

		assertTrue(song.play(PlayMode.PLAY_TRACK, false, undo, 4, atariTrackerDriver, clipboard));

		assertEquals(PlayMode.PLAY_TRACK, song.getPlayMode());
		assertEquals(3, song.songGetPlayLine()); // songPlayLine = songActiveLine
		assertEquals(0, song.getPlayLine()); // special=0 (default) -> trackPlayLine = 0
	}

	// --- playBeat ---

	@Test
	void playBeatSendsTheNoteAndInstrumentFromTheCurrentTrackLine() {
		song.getSong()[0][0] = 5; // song line 0, column 0 -> track 5
		song.songSetActiveLine(0);
		song.setPlayMode(PlayMode.PLAY_TRACK);
		song.songSetPlayLine(0);
		song.setPlayLine(0);

		Track tr = tracks.getTrack(5);
		tr.len = 4;
		tr.note[0] = 20;
		tr.instr[0] = 3;
		tr.volume[0] = 10;

		assertTrue(song.playBeat(4, atariTrackerDriver));
		assertEquals(3, atariTrackerDriver.getRmtInstrument(0));
	}

	// --- playVBI ---

	@Test
	void playVBIAdvancesTheTrackPlayLineOnceSpeedElapses() {
		song.setPlayMode(PlayMode.PLAY_TRACK);
		song.songSetPlayLine(0);
		song.setPlayLine(5); // speeda defaults to 0, so "speeda--" makes the "too soon" check fail and it proceeds

		assertTrue(song.playVBI(4, atariTrackerDriver));
		assertEquals(6, song.getPlayLine());
	}

	// --- dumpSongToPokeyStream ---
	// Ported from CSong::DumpSongToPokeyStream() (Song_DumpSong.cpp). The
	// C++ test binary stubs the JSR, so its dump tests record silence; here
	// useRealAtari() gives the fixture's driver a CPU-backed Atari, so the
	// real tracker driver records real register data. songGo[1]=0
	// guarantees an immediate 2-line loop, so recording finishes in a
	// handful of frames instead of running the song's full 256 lines.

	/**
	 * Replaces the fixture's memory-only Atari with a CPU-backed one holding
	 * the generated tuning tables, the default (PATCH16) tracker driver and
	 * the fixture's instruments at $4000 - call it after the test has edited
	 * its instruments, as RmtSession does through Instruments.update().
	 */
	private void useRealAtari() {
		useRealAtari(false);
	}

	private void useRealAtari(boolean stereo) {
		Atari atari = new Atari(new AtariCpu(false, stereo));
		TuningSettings tuningSettings = new TuningSettings();
		tuningSettings.initialize(false);
		TuningRatios tuningRatios = new TuningRatios();
		tuningRatios.initialize();
		atari.init(false, tuningSettings, tuningRatios);
		atariTrackerDriver = new AtariTrackerDriver(atari);
		assertTrue(atariTrackerDriver.loadRMTRoutines(TrackerDriverVersion.PATCH16) > 0);
		instruments.attachAtari(atari, () -> stereo);
	}

	@Test
	void dumpSongToPokeyStreamRecordsPokeyRegisterDataUntilTheLoopPoint() {
		SongInfo info = new SongInfo();
		song.getSongInfoPars(info);
		info.mainSpeed = 6;
		info.instrumentSpeed = 1;
		song.setSongInfoPars(info);

		song.getSong()[0][0] = 5;
		Track tr = tracks.getTrack(5);
		tr.len = 2;
		tr.note[0] = 10;
		tr.instr[0] = 2;
		tr.volume[0] = 10;
		instruments.getInstrument(2).envelope[0][EnvelopeParameter.VOLUMEL] = 10; // a real (non-silent) envelope, so the driver has something to play
		song.getSongGo()[1] = 0; // guarantees a fast loop

		useRealAtari();
		ChannelControl channelControl = new ChannelControl(4);
		PokeyStream pokeyStream = new PokeyStream();

		song.dumpSongToPokeyStream(pokeyStream, PlayMode.PLAY_SONG, 0, 0, 4, atariTrackerDriver, channelControl, clipboard, undo);

		assertEquals(PlayMode.PLAY_STOP, song.getPlayMode());
		assertTrue(pokeyStream.getFirstCountPoint() > 0, "expected at least one frame before the loop point");

		byte[] frames = pokeyStream.getFrameBytes(pokeyStream.getFirstCountPoint(), 0);
		assertEquals(pokeyStream.getFirstCountPoint() * 9, frames.length); // mono song -> 9 bytes/frame

		// The real tracker driver ran, unlike in the C++ characterization
		// test's no-op-JSR test binary - confirm at least one byte of real,
		// non-zero POKEY register data was actually captured.
		boolean anyNonZero = false;
		for (byte b : frames) {
			if (b != 0) {
				anyNonZero = true;
				break;
			}
		}
		assertTrue(anyNonZero, "expected at least one non-zero POKEY register byte");
	}

	@Test
	void dumpSongToPokeyStreamPlayFromStartsAtTheGivenSongline() {
		// C++ sets the *active* lines before Play(), which PLAY_FROM (the XEX
		// exporter's per-subsong mode) starts from; the port used to set the
		// play lines instead, which PLAY_FROM then overwrote from the active
		// lines - every subsong was dumped from the cursor line.
		SongInfo info = new SongInfo();
		song.getSongInfoPars(info);
		info.mainSpeed = 6;
		info.instrumentSpeed = 1;
		song.setSongInfoPars(info);

		song.getSong()[0][0] = 5;
		Track tr = tracks.getTrack(5);
		tr.len = 2;
		tr.note[0] = 10;
		tr.instr[0] = 2;
		tr.volume[0] = 10;
		song.getSong()[1][0] = 6;
		tr = tracks.getTrack(6);
		tr.len = 2;
		tr.note[0] = 30;
		tr.instr[0] = 2;
		tr.volume[0] = 10;
		song.getSongGo()[2] = 1; // line 1 loops onto itself
		instruments.getInstrument(2).envelope[0][EnvelopeParameter.VOLUMEL] = 10;
		useRealAtari();
		ChannelControl channelControl = new ChannelControl(4);

		PokeyStream fromLineOne = new PokeyStream();
		song.dumpSongToPokeyStream(fromLineOne, PlayMode.PLAY_FROM, 1, 0, 4, atariTrackerDriver, channelControl, clipboard, undo);
		PokeyStream fromTheStart = new PokeyStream();
		song.dumpSongToPokeyStream(fromTheStart, PlayMode.PLAY_SONG, 0, 0, 4, atariTrackerDriver, channelControl, clipboard, undo);

		int audf1FromLineOne = fromLineOne.getFrameBytes(1, 0)[0] & 0xFF;
		int audf1FromTheStart = fromTheStart.getFrameBytes(1, 0)[0] & 0xFF;
		assertTrue(audf1FromLineOne != 0 && audf1FromTheStart != 0, "both dumps start with a sounding note");
		assertTrue(audf1FromLineOne != audf1FromTheStart, "PLAY_FROM 1 starts with note 30 (track 6), PLAY_SONG with note 10 (track 5): AUDF1 " + audf1FromLineOne + " vs " + audf1FromTheStart);
	}

	// --- SapFileExporter.exportSapR ---
	// Extracted implicitly: CSongExporter::ExportSAP_R() (SongExporter.cpp)
	// shows a real dialog then delegates to this dialog-independent method
	// with an already-populated CSAPFile - same "dialog gathers params, real
	// work happens independently" shape as the RMT/ASM exporters. Takes an
	// already-recorded PokeyStream directly (see SapFileExporter's own class
	// javadoc for why CSongContainer/CSongExport weren't ported).

	@Test
	void exportSapRWritesTheHeaderAndRealPokeyStreamData() {
		SongInfo info = new SongInfo();
		song.getSongInfoPars(info);
		info.mainSpeed = 6;
		info.instrumentSpeed = 1;
		song.setSongInfoPars(info);

		song.getSong()[0][0] = 5;
		Track tr = tracks.getTrack(5);
		tr.len = 2;
		tr.note[0] = 10;
		tr.instr[0] = 2;
		tr.volume[0] = 10;
		instruments.getInstrument(2).envelope[0][EnvelopeParameter.VOLUMEL] = 10;
		song.getSongGo()[1] = 0; // guarantees a fast loop

		useRealAtari();
		ChannelControl channelControl = new ChannelControl(4);
		PokeyStream pokeyStream = new PokeyStream();
		song.dumpSongToPokeyStream(pokeyStream, PlayMode.PLAY_SONG, 0, 0, 4, atariTrackerDriver, channelControl, clipboard, undo);

		SapFile sapFile = new SapFile();
		sapFile.setAuthor("RCoder");
		sapFile.setName("RSong");
		sapFile.setDate("01/01/2000");

		byte[] out = SapFileExporter.exportSapR(sapFile, pokeyStream);

		String text = new String(out, java.nio.charset.StandardCharsets.US_ASCII);
		assertTrue(text.contains("TYPE R"));
		assertTrue(out.length > 64);

		int headerLen = text.indexOf("TYPE R") + "TYPE R".length() + 4; // + the double EOL SapFile.export() always ends with
		boolean anyNonZero = false;
		for (int i = headerLen; i < out.length; i++) {
			if (out[i] != 0) {
				anyNonZero = true;
				break;
			}
		}
		assertTrue(anyNonZero, "expected at least one non-zero POKEY register byte after the header");
	}

	// --- SapFileExporter.exportSapBLzss ---
	// Needs a real on-disk resource file (rmt/resources/players/vu_player_v2.obx,
	// checked into the repo - see SapFileExporter's own VU_PLAYER_PATH), the
	// first real file-system dependency in this Java test suite. Caller
	// (matching the real dialog) sets the SapFile's type to "B" itself.

	@Test
	void exportSapBLzssLoadsTheRealResourceAndWritesPatchedMemory() {
		SongInfo info = new SongInfo();
		song.getSongInfoPars(info);
		info.mainSpeed = 6;
		info.instrumentSpeed = 1;
		song.setSongInfoPars(info);

		song.getSong()[0][0] = 5;
		Track tr = tracks.getTrack(5);
		tr.len = 2;
		tr.note[0] = 10;
		tr.instr[0] = 2;
		tr.volume[0] = 10;
		instruments.getInstrument(2).envelope[0][EnvelopeParameter.VOLUMEL] = 10;
		song.getSongGo()[1] = 0; // guarantees a fast loop

		useRealAtari();
		ChannelControl channelControl = new ChannelControl(4);
		PokeyStream pokeyStream = new PokeyStream();
		song.dumpSongToPokeyStream(pokeyStream, PlayMode.PLAY_SONG, 0, 0, 4, atariTrackerDriver, channelControl, clipboard, undo);

		SapFile sapFile = new SapFile();
		sapFile.setType("B");
		sapFile.setAuthor("RCoder");
		sapFile.setName("RSong");
		sapFile.setDate("01/01/2000");

		byte[] out = SapFileExporter.exportSapBLzss(sapFile, song, 4, pokeyStream);

		String header = new String(out, 0, Math.min(out.length, 200), java.nio.charset.StandardCharsets.US_ASCII);
		assertTrue(header.contains("TYPE B"));
		assertTrue(header.contains("INIT "));
		assertTrue(header.contains("PLAYER "));
		assertTrue(out.length > 200);
	}

	// --- WaveFileExporter.exportWav ---
	// CWaveFileExporter::ExportWAV replays the recorded PokeyStream through
	// the POKEY (C++: CXPokey; here AtariCpu's pair), frame by frame up to
	// the loop point.

	@Test
	void exportWavRendersTheStreamUpToTheLoopPointAsAValidRiffWaveFile() {
		SongInfo info = new SongInfo();
		song.getSongInfoPars(info);
		info.mainSpeed = 6;
		info.instrumentSpeed = 1;
		song.setSongInfoPars(info);

		song.getSong()[0][0] = 5;
		Track tr = tracks.getTrack(5);
		tr.len = 2;
		tr.note[0] = 10;
		tr.instr[0] = 2;
		tr.volume[0] = 10;
		instruments.getInstrument(2).envelope[0][EnvelopeParameter.VOLUMEL] = 10;
		song.getSongGo()[1] = 0; // guarantees a fast loop

		useRealAtari();
		ChannelControl channelControl = new ChannelControl(4);
		PokeyStream pokeyStream = new PokeyStream();
		song.dumpSongToPokeyStream(pokeyStream, PlayMode.PLAY_SONG, 0, 0, 4, atariTrackerDriver, channelControl, clipboard, undo);

		byte[] out = WaveFileExporter.exportWav(pokeyStream, false, false, song.getInstrumentSpeed());

		assertEquals("RIFF", new String(out, 0, 4, java.nio.charset.StandardCharsets.US_ASCII));
		assertEquals("WAVE", new String(out, 8, 4, java.nio.charset.StandardCharsets.US_ASCII));
		assertEquals(2, out[22], "channels");
		assertEquals(44100, (out[24] & 0xFF) | (out[25] & 0xFF) << 8 | (out[26] & 0xFF) << 16, "sample rate");
		assertEquals(16, out[34], "bits per sample");
		int dataLength = (out[40] & 0xFF) | (out[41] & 0xFF) << 8 | (out[42] & 0xFF) << 16 | (out[43] & 0xFF) << 24;
		assertEquals(out.length - 44, dataLength);
		// one PAL frame is ~884 blocks of 4 bytes
		int frames = pokeyStream.getFirstCountPoint();
		assertTrue(dataLength >= frames * 880 * 4 && dataLength <= frames * 888 * 4, "data bytes for " + frames + " frames: " + dataLength);
		boolean anyNonZero = false;
		for (int i = 44; i < out.length; i++) {
			anyNonZero |= out[i] != 0;
		}
		assertTrue(anyNonZero, "audible samples");
	}

	@Test
	void exportWavOfAStereoSongPutsTheSecondPokeyOnTheRightChannel() {
		// a note on track 4 only (the right-hand POKEY): the left channel must stay silent - C++'s ExportWAV read the
		// stream's second-POKEY bytes as the first POKEY's (fixed in both languages)
		SongInfo info = new SongInfo();
		song.getSongInfoPars(info);
		info.mainSpeed = 6;
		info.instrumentSpeed = 1;
		song.setSongInfoPars(info);

		song.getSong()[0][4] = 5;
		Track tr = tracks.getTrack(5);
		tr.len = 2;
		tr.note[0] = 10;
		tr.instr[0] = 2;
		tr.volume[0] = 10;
		instruments.getInstrument(2).envelope[0][EnvelopeParameter.VOLUMEL] = 10;
		instruments.getInstrument(2).envelope[0][EnvelopeParameter.VOLUMER] = 10;
		song.getSongGo()[1] = 0; // guarantees a fast loop

		useRealAtari(true);
		ChannelControl channelControl = new ChannelControl(8);
		PokeyStream pokeyStream = new PokeyStream();
		song.dumpSongToPokeyStream(pokeyStream, PlayMode.PLAY_SONG, 0, 0, 8, atariTrackerDriver, channelControl, clipboard, undo);
		assertEquals(pokeyStream.getFirstCountPoint() * 18, pokeyStream.getFrameBytes(pokeyStream.getFirstCountPoint(), 0).length); // stereo: 18 bytes/frame

		byte[] out = WaveFileExporter.exportWav(pokeyStream, false, true, 1);

		boolean leftNonZero = false;
		boolean rightNonZero = false;
		for (int i = 44; i + 3 < out.length; i += 4) {
			leftNonZero |= out[i] != 0 || out[i + 1] != 0;
			rightNonZero |= out[i + 2] != 0 || out[i + 3] != 0;
		}
		assertFalse(leftNonZero, "the first POKEY (left) is silent");
		assertTrue(rightNonZero, "the second POKEY (right) sounds");
	}

	// --- SongExporter.exportLzss ---
	// Mirrors SongEditingTests.cpp's own honest finding for this method: a
	// minimal test song's recorded PokeyStream data is small/repetitive
	// enough that none of the three sections ever cross ExportLZSS's own
	// "> 16 compressed bytes" threshold - for a different reason than the
	// C++ test's own (there, the no-op JSR stub means the data is
	// permanently near-silent; here, the real driver produces real register
	// data, but this particular 2-line loop still doesn't have enough
	// distinct content to compress past 16 bytes). This also exercises
	// thirdCountPoint's legitimate zero-frames case, which surfaced (and is
	// now guarded against, see SongExporter#compressSection) a pre-existing,
	// previously-unexercised CompressLzss edge case.

	@Test
	void exportLzssStaysBelowTheCompressedSizeThresholdForAMinimalSong() {
		SongInfo info = new SongInfo();
		song.getSongInfoPars(info);
		info.mainSpeed = 6;
		info.instrumentSpeed = 1;
		song.setSongInfoPars(info);

		song.getSong()[0][0] = 5;
		Track tr = tracks.getTrack(5);
		tr.len = 2;
		tr.note[0] = 10;
		tr.instr[0] = 2;
		tr.volume[0] = 10;
		instruments.getInstrument(2).envelope[0][EnvelopeParameter.VOLUMEL] = 10;
		song.getSongGo()[1] = 0; // guarantees a fast loop

		useRealAtari();
		ChannelControl channelControl = new ChannelControl(4);
		PokeyStream pokeyStream = new PokeyStream();
		song.dumpSongToPokeyStream(pokeyStream, PlayMode.PLAY_SONG, 0, 0, 4, atariTrackerDriver, channelControl, clipboard, undo);

		SongExporter.LzssExportResult result = SongExporter.exportLzss(pokeyStream);

		assertEquals(0, result.full().length);
		assertEquals(0, result.intro().length);
		assertEquals(0, result.loop().length);
	}

	// --- SongExporter.exportXexLzss ---
	// Needs the same real on-disk resource file as exportSapBLzss
	// (rmt/resources/players/vu_player_v2.obx). Calls
	// Song#dumpSongToPokeyStream directly (once per subsong, PLAY_FROM
	// mode) rather than through the unported CSongContainer/CSongExport
	// caching pair - see SapFileExporter's own class javadoc for why.

	@Test
	void exportXexLzssLoadsTheRealResourceAndWritesReconstructedBinary() {
		SongInfo info = new SongInfo();
		song.getSongInfoPars(info);
		info.mainSpeed = 6;
		info.instrumentSpeed = 1;
		song.setSongInfoPars(info);

		song.getSong()[0][0] = 5;
		Track tr = tracks.getTrack(5);
		tr.len = 2;
		tr.note[0] = 10;
		tr.instr[0] = 2;
		tr.volume[0] = 10;
		instruments.getInstrument(2).envelope[0][EnvelopeParameter.VOLUMEL] = 10;
		song.getSongGo()[1] = 0; // guarantees a fast loop

		XexFile xexFile = XexFile.fromSong(song, 4);
		xexFile.autoRegion = true; // skips the NOP-patching branch, simplifying the test
		xexFile.displayRasterbar = false;
		xexFile.rasterbarColor = 0;
		java.util.Arrays.fill(xexFile.atariText, (byte) ' ');

		useRealAtari();
		ChannelControl channelControl = new ChannelControl(4);
		byte[] out = SongExporter.exportXexLzss(song, 4, xexFile, atariTrackerDriver, channelControl, clipboard, undo);

		// The output is a reconstructed Atari binary (headers + raw data, not
		// text) - just confirm a substantial amount of it actually landed,
		// i.e. the real resource file loaded and the LZSS/dumpSongToPokeyStream
		// pipeline produced real data rather than silently failing.
		assertTrue(out.length > 3500, "expected a substantial reconstructed binary, was " + out.length + " bytes");
	}
}
