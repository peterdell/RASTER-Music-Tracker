package com.wudsn.tools.rmt.ui;

import static com.wudsn.tools.rmt.ui.VirtualKey.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.wudsn.tools.rmt.model.EditArea;
import com.wudsn.tools.rmt.model.EditMode;
import com.wudsn.tools.rmt.model.EnvelopeParameter;
import com.wudsn.tools.rmt.model.Instrument;
import com.wudsn.tools.rmt.model.InstrumentSection;
import com.wudsn.tools.rmt.model.Part;

/**
 * Logic-level tests of the key handlers: feed Windows virtual-key codes (as
 * {@link TrackerPanel} would after translating Swing's), assert the model.
 * Note keys are the QWERTY table's: {@code Z} = C-1 (note 0), {@code Q} =
 * C-2 (note 12).
 */
class SongInputTest {

	private RmtSession session;
	private SongInput input;

	@BeforeEach
	void setUp() {
		session = new RmtSession(); // empty stereo song, 64-line tracks, cursor at 0/0
		// Give songlines 0 and 1 real tracks so the cursor has somewhere to go
		for (int col = 0; col < 8; col++) {
			session.song.getSong()[0][col] = col;
			session.song.getSong()[1][col] = 8 + col;
		}
		input = new SongInput(session);
	}

	private void press(int vk) {
		input.keyDown(vk);
		input.keyUp(vk);
	}

	private void pressWith(int modifier, int vk) {
		input.keyDown(modifier);
		press(vk);
		input.keyUp(modifier);
	}

	// --- media keys (CRmtView::OnKeyDown, B8c) ---

	@Test
	void mediaPlayPauseTogglesPlaybackAndNextPrevSeekBySongline() {
		press(VK_MEDIA_PLAY_PAUSE);
		assertEquals(com.wudsn.tools.rmt.model.PlayMode.PLAY_SONG, session.song.getPlayMode());
		press(VK_MEDIA_PLAY_PAUSE);
		assertEquals(com.wudsn.tools.rmt.model.PlayMode.PLAY_STOP, session.song.getPlayMode());

		press(VK_MEDIA_NEXT_TRACK);
		assertEquals(com.wudsn.tools.rmt.model.PlayMode.PLAY_FROM, session.song.getPlayMode()); // PLAY_SEEK_NEXT plays from the next songline
		assertEquals(1, session.song.songGetPlayLine());
		press(VK_MEDIA_PREV_TRACK);
		assertEquals(0, session.song.songGetPlayLine());
		session.song.stop(session.undo);
	}

	// --- navigation (B1) ---

	@Test
	void downAndUpMoveTheTrackLine() {
		press(VK_DOWN);
		assertEquals(1, session.song.getActiveLine());
		press(VK_UP);
		assertEquals(0, session.song.getActiveLine());
	}

	@Test
	void upFromLineZeroContinuesIntoThePreviousSonglineBecauseUpDownContinueIsOn() {
		press(VK_UP);
		assertEquals(255, session.song.songGetActiveLine());
		assertEquals(63, session.song.getActiveLine());
	}

	@Test
	void shiftAndControlAreTrackedAcrossKeyDownAndUp() {
		input.keyDown(VK_SHIFT);
		assertTrue(session.uiState.shiftKey);
		input.keyDown(VK_CONTROL);
		assertTrue(session.uiState.controlKey);
		input.keyUp(VK_SHIFT);
		assertFalse(session.uiState.shiftKey);
		input.keyUp(VK_CONTROL);
		assertFalse(session.uiState.controlKey);
	}

	@Test
	void controlDownMovesToTheNextSongline() {
		pressWith(VK_CONTROL, VK_DOWN);
		assertEquals(1, session.song.songGetActiveLine());
		assertEquals(0, session.song.getActiveLine());
	}

	@Test
	void rightMovesThroughTheSubColumnsThenToTheNextColumnAndTabSkipsStraightToIt() {
		press(VK_RIGHT);
		assertEquals(0, session.song.getActiveColumn());
		assertEquals(1, session.song.getTrackActiveCur());
		press(VK_TAB);
		assertEquals(1, session.song.getActiveColumn());
		assertEquals(1, session.song.getTrackActiveCur());
		pressWith(VK_SHIFT, VK_TAB);
		assertEquals(0, session.song.getActiveColumn());
	}

	@Test
	void pageDownJumpsToTheNextPrimaryHighlightAndPageUpBack() {
		press(VK_NEXT);
		assertEquals(8, session.song.getActiveLine());
		press(VK_DOWN);
		press(VK_PRIOR);
		assertEquals(8, session.song.getActiveLine());
		press(VK_PRIOR);
		assertEquals(0, session.song.getActiveLine());
	}

	@Test
	void endGoesToTheLastLineAndHomeBackToTheFirst() {
		press(VK_END);
		assertEquals(63, session.song.getActiveLine());
		press(VK_HOME);
		assertEquals(0, session.song.getActiveLine());
	}

	@Test
	void songPartNavigatesSonglinesAndEnterReturnsToTheTracks() {
		session.uiState.activePart = Part.PART_SONG;
		press(VK_DOWN);
		assertEquals(1, session.song.songGetActiveLine());
		press(VK_RIGHT);
		assertEquals(1, session.song.getActiveColumn());
		press(VK_END);
		assertEquals(1, session.song.songGetActiveLine()); // the last songline with a track
		press(VK_HOME);
		assertEquals(0, session.song.songGetActiveLine());
		press(VK_RETURN);
		assertEquals(Part.PART_TRACKS, session.uiState.activePart);
	}

	// --- track editing (B3) ---

	@Test
	void aNoteKeyEntersTheNoteWithTheActiveInstrumentAndVolumeAndMovesDown() {
		press(VK_Q); // C-2

		assertEquals(12, session.tracks.getNote(0, 0));
		assertEquals(0, session.tracks.getInstr(0, 0));
		assertEquals(15, session.tracks.getVol(0, 0)); // MAXVOLUME after clearSong
		assertEquals(1, session.song.getActiveLine()); // skipLinesAfterNoteInsert = 1
	}

	@Test
	void theOctaveKeysShiftWhatANoteKeyEnters() {
		press(VK_MULTIPLY); // octave up
		press(VK_Z); // C-1 + 1 octave
		assertEquals(12, session.tracks.getNote(0, 0));
		press(VK_DIVIDE);
		press(VK_DIVIDE); // clamps at 0
		press(VK_Z);
		assertEquals(0, session.tracks.getNote(0, 1));
	}

	@Test
	void digitsEditTheInstrumentVolumeAndSpeedColumns() {
		press(VK_Z); // a note on line 0, cursor to line 1
		press(VK_UP);

		press(VK_RIGHT); // instrument column
		press(VK_0 + 5);
		assertEquals(5, session.tracks.getInstr(0, 0));
		press(VK_0 + 2);
		assertEquals(2, session.tracks.getInstr(0, 0)); // $52 >= INSTRSNUM -> only the lower digit is kept

		press(VK_RIGHT); // volume column
		press(VK_0 + 7);
		assertEquals(7, session.tracks.getVol(0, 0));
		assertEquals(1, session.song.getActiveLine()); // volume entry moves down too
		press(VK_UP);

		press(VK_RIGHT); // speed column
		press(VK_A); // hex A
		assertEquals(0x0A, session.tracks.getSpeed(0, 0));
		press(VK_BACK); // deletes the speed
		assertEquals(-1, session.tracks.getSpeed(0, 0));
	}

	@Test
	void spaceClearsTheLineAndMovesDown() {
		press(VK_Z);
		press(VK_UP);
		press(VK_SPACE);
		assertEquals(-1, session.tracks.getNote(0, 0));
		assertEquals(-1, session.tracks.getVol(0, 0));
		assertEquals(1, session.song.getActiveLine());
	}

	@Test
	void shiftDownSelectsABlockAndEscapeDeselectsIt() {
		pressWith(VK_SHIFT, VK_DOWN);
		pressWith(VK_SHIFT, VK_DOWN);
		assertTrue(session.clipboard.isBlockSelected());
		assertEquals(0, session.clipboard.getFromTo().from());
		assertEquals(2, session.clipboard.getFromTo().to());
		press(VK_ESCAPE);
		assertFalse(session.clipboard.isBlockSelected());
	}

	@Test
	void controlASelectsTheWholeTrackAndControlXCutsIt() {
		press(VK_Z);
		pressWith(VK_CONTROL, VK_A);
		assertTrue(session.clipboard.isBlockSelected());
		assertEquals(63, session.clipboard.getFromTo().to());
		pressWith(VK_CONTROL, VK_X);
		assertEquals(-1, session.tracks.getNote(0, 0));
	}

	@Test
	void insertAndDeleteShiftTheTrackLines() {
		press(VK_Z); // note on line 0, cursor on 1
		press(VK_HOME);
		press(VK_INSERT);
		assertEquals(-1, session.tracks.getNote(0, 0));
		assertEquals(0, session.tracks.getNote(0, 1));
		press(VK_DELETE);
		assertEquals(0, session.tracks.getNote(0, 0));
	}

	@Test
	void f11TogglesRespectVolume() {
		assertFalse(session.uiState.respectVolume);
		press(VK_F11);
		assertTrue(session.uiState.respectVolume);
		press(VK_F11);
		assertFalse(session.uiState.respectVolume);
	}

	// --- jam (prove) mode ---

	@Test
	void inJamModeANoteKeyPlaysInsteadOfEnteringAndTheCursorStillMoves() {
		session.uiState.editMode = EditMode.JAM_MONO_MODE;
		press(VK_Z);
		assertEquals(-1, session.tracks.getNote(0, 0));
		assertEquals(0, session.song.getActiveLine()); // notes don't move the cursor in jam mode
		press(VK_DOWN);
		assertEquals(1, session.song.getActiveLine());
	}

	@Test
	void switchEditModeCyclesEditMonoJamStereoJamEdit() {
		UiState ui = session.uiState;
		ui.switchEditMode(EditMode.EDIT_MODE, true);
		assertEquals(EditMode.JAM_MONO_MODE, ui.editMode);
		ui.switchEditMode(EditMode.EDIT_MODE, true);
		assertEquals(EditMode.JAM_STEREO_MODE, ui.editMode);
		ui.switchEditMode(EditMode.EDIT_MODE, true);
		assertEquals(EditMode.EDIT_MODE, ui.editMode);
		ui.switchEditMode(EditMode.EDIT_MODE, false);
		ui.switchEditMode(EditMode.EDIT_MODE, false); // mono song: mono jam -> edit
		assertEquals(EditMode.EDIT_MODE, ui.editMode);
	}

	// --- info part ---

	@Test
	void typingIntoTheSongNameThenTabMovesOnToTheSpeedFields() {
		session.uiState.activePart = Part.PART_INFO; // infoAct starts at NAME
		press(VK_HOME);
		press(VK_A);
		pressWith(VK_SHIFT, VK_B);
		assertTrue(session.song.getName().startsWith("aBNoname song"));
		assertEquals(2, session.song.getSongNameCursor());

		press(VK_TAB);
		assertEquals(EditArea.SPEED, session.song.getInfoAct());
		press(VK_0 + 3); // low digit of $10 shifted: (0 << 4 | 3)
		assertEquals(3, session.song.getSpeed());
		press(VK_RIGHT);
		assertEquals(EditArea.MAIN_SPEED, session.song.getInfoAct());
		pressWith(VK_CONTROL, VK_UP);
		assertEquals(17, session.song.getMainSpeed());
		press(VK_RETURN);
		assertEquals(Part.PART_TRACKS, session.uiState.activePart);
	}

	// --- instrument part ---

	@Test
	void instrumentEnvelopeDigitsTabBetweenSectionsAndNameEditing() {
		session.uiState.activePart = Part.PART_INSTRUMENTS;
		Instrument ai = session.instruments.getInstrument(0);
		assertEquals(InstrumentSection.ENVELOPE, ai.activeEditSection); // a cleared instrument starts on the envelope, VOLUME L
		press(VK_F);
		assertEquals(15, ai.envelope[0][EnvelopeParameter.VOLUMEL]);

		pressWith(VK_CONTROL, VK_RIGHT); // Ctrl+Right steps the value: 15 + 1 wraps to 0 within the nibble
		assertEquals(0, ai.envelope[0][EnvelopeParameter.VOLUMEL]);

		press(VK_TAB);
		assertEquals(InstrumentSection.NOTETABLE, ai.activeEditSection);
		press(VK_0 + 4);
		assertEquals(4, ai.noteTable[0]);

		press(VK_TAB);
		assertEquals(InstrumentSection.PARAMETERS, ai.activeEditSection);
		assertEquals(Instrument.PAR_ENV_LENGTH, ai.editParameterNr);
		press(VK_0 + 3); // ENV LENGTH is a two-digit, 1-based field: the displayed "01" becomes "13" -> parameter $12
		assertEquals(0x12, ai.parameters[Instrument.PAR_ENV_LENGTH]);

		pressWith(VK_SHIFT, VK_TAB);
		assertEquals(InstrumentSection.NAME, ai.activeEditSection);
		press(VK_END);
		press(VK_X);
		assertTrue(new String(ai.name).startsWith("Instrument 00x")); // End puts the cursor right after the last non-space
	}
}
