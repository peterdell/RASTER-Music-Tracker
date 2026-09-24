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

		assertTrue(song.songInsertCopyOrCloneOfSongLinesApply(1, 0, 0, false, 0, 100, undo, 4));

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
		assertTrue(song.songInsertCopyOrCloneOfSongLinesApply(1, 0, 0, true, 0, 100, undo, 4));

		assertEquals(5, song.getSong()[0][0]); // source untouched
		int clonedTrack = song.getSong()[1][0];
		assertTrue(clonedTrack != 5); // cloned into a different, previously-unused track
		assertTrue(clonedTrack != -1);

		Track dst = tracks.getTrack(clonedTrack);
		assertEquals(10, dst.note[0]);
		assertEquals(1, dst.instr[0]);
		assertEquals(8, dst.volume[0]);
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
}
