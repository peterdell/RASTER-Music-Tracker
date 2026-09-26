package com.wudsn.tools.rmt.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Rectangle;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.wudsn.tools.rmt.model.EditArea;
import com.wudsn.tools.rmt.model.EnvelopeParameter;
import com.wudsn.tools.rmt.model.Instrument;
import com.wudsn.tools.rmt.model.InstrumentSection;
import com.wudsn.tools.rmt.model.Part;

/** Logic-level tests of {@code MouseAction}: click/move/wheel at logical positions taken from the screen layout, assert the model and the chosen cursor. */
class MouseInputTest {

	/** Records what the popups/commands were asked for and answers with preset values. */
	private static final class StubCallbacks implements MouseInput.Callbacks {
		int octaveAnswer = -1;
		MouseInput.VolumeSelection volumeAnswer;
		int instrumentAnswer = -1;
		String lastCall = "";

		@Override
		public int selectOctave(int x, int y, int octave) {
			lastCall = "octave@" + x + "," + y + " current " + octave;
			return octaveAnswer;
		}

		@Override
		public MouseInput.VolumeSelection selectVolume(int x, int y, int volume, boolean respectVolume) {
			lastCall = "volume current " + volume;
			return volumeAnswer;
		}

		@Override
		public int selectInstrument(int x, int y, int instrument) {
			lastCall = "instrument@" + x + "," + y + " current " + instrument;
			return instrumentAnswer;
		}

		@Override
		public void changeMaxTrackLength() {
			lastCall = "changeMaxTrackLength";
		}

		@Override
		public void switchMonoStereo() {
			lastCall = "switchMonoStereo";
		}

		@Override
		public void toggleNTSC() {
			lastCall = "toggleNTSC";
		}
	}

	private RmtSession session;
	private StubCallbacks callbacks;
	private MouseInput mouse;

	@BeforeEach
	void setUp() {
		session = new RmtSession(); // stereo, empty
		for (int col = 0; col < 8; col++) {
			session.song.getSong()[0][col] = col;
			session.song.getSong()[1][col] = 8 + col;
		}
		TrackerPanel.computeLayout(session.uiState, session.options, 1278, 654); // the reference layout: 26 track lines, SONG block at x 828 (compact)
		session.uiState.cursorActView = 0 + 8 - session.uiState.lineY; // what drawTracks would have computed
		callbacks = new StubCallbacks();
		mouse = new MouseInput(session, new SongInput(session), callbacks);
	}

	private void click(int x, int y) {
		mouse.buttonDown(x, y, MouseInput.MK_LBUTTON);
		mouse.buttonUp(MouseInput.MK_LBUTTON);
	}

	private void rightClick(int x, int y) {
		mouse.buttonDown(x, y, MouseInput.MK_RBUTTON);
		mouse.buttonUp(MouseInput.MK_RBUTTON);
	}

	@Test
	void hoveringNothingGivesTheArrowAndTheTrackAreaTheGotoCursor() {
		mouse.mouseMove(1200, 600);
		assertEquals(RmtCursor.ARROW, session.uiState.cursor);
		assertEquals(1200, session.uiState.mouseX);
		mouse.mouseMove(RmtScreenLayout.TRACKS_X + 6 * 8 + 10, RmtScreenLayout.TRACKS_Y + 48 + 10);
		assertEquals(RmtCursor.GOTO, session.uiState.cursor);
	}

	@Test
	void clickingATrackLineMovesTheCursorToThatLineColumnAndSubColumn() {
		// column 1 (x offset 128..255), the volume sub-column (chars 7-9), 3 rows below the top visible line
		int lineTop = RmtScreenLayout.TRACKS_Y + 48;
		click(RmtScreenLayout.TRACKS_X + 6 * 8 + 128 + 7 * 8 + 2, lineTop + 3 * 16 + 5);
		// the top visible line is cursorActView - 8 = -13 -> row 3 is line -10: out of the pattern, nothing happens
		assertEquals(0, session.song.getActiveLine());

		int row = 13 + 4; // the active line sits at row 13; four rows below it is line 4
		click(RmtScreenLayout.TRACKS_X + 6 * 8 + 128 + 7 * 8 + 2, lineTop + row * 16 + 5);
		assertEquals(4, session.song.getActiveLine());
		assertEquals(1, session.song.getActiveColumn());
		assertEquals(2, session.song.getTrackActiveCur());
		assertEquals(Part.PART_TRACKS, session.uiState.activePart);
	}

	@Test
	void theWheelOverTheTracksMovesTheLine() {
		mouse.mouseWheel(RmtScreenLayout.TRACKS_X + 6 * 8 + 10, RmtScreenLayout.TRACKS_Y + 48 + 10, -120);
		assertEquals(1, session.song.getActiveLine());
		mouse.mouseWheel(RmtScreenLayout.TRACKS_X + 6 * 8 + 10, RmtScreenLayout.TRACKS_Y + 48 + 10, 120);
		assertEquals(0, session.song.getActiveLine());
	}

	@Test
	void clickingATrackHeaderTogglesTheChannelAndRightClickSolosIt() {
		int x = RmtScreenLayout.TRACKS_X + 5 * 8 + 2 * 128 + 10; // "TRACK L3"
		int y = RmtScreenLayout.TRACKS_Y;
		click(x, y);
		assertEquals(RmtCursor.CHANNEL_ON_OFF, session.uiState.cursor);
		assertFalse(session.channelControl.isChannelOn(2));
		click(x, y);
		assertTrue(session.channelControl.isChannelOn(2));
		rightClick(x, y);
		assertTrue(session.channelControl.isChannelOn(2));
		assertFalse(session.channelControl.isChannelOn(0));
	}

	@Test
	void clickingInTheSongBlockSelectsTheSonglineAndColumn() {
		int songOffset = 828; // stereo, 1278 wide -> compact 5-line block: the active line is its row 2
		int x = songOffset + 6 * 8 + 3 * 24 + 4; // column 3
		int rowOfActive = RmtScreenLayout.SONG_Y + 16 + 2 * 16;
		click(x, rowOfActive + 16 + 4); // one row below the active line
		assertEquals(RmtCursor.GOTO, session.uiState.cursor);
		assertEquals(1, session.song.songGetActiveLine());
		assertEquals(3, session.song.getActiveColumn());
		assertEquals(Part.PART_SONG, session.uiState.activePart);

		mouse.mouseWheel(x, rowOfActive, 120);
		assertEquals(0, session.song.songGetActiveLine());
	}

	@Test
	void clickingTheSongHeaderTogglesAChannel() {
		click(828 + 6 * 8 + 4 * 24 + 2, RmtScreenLayout.SONG_Y + 2); // "R1"
		assertFalse(session.channelControl.isChannelOn(4));
	}

	@Test
	void clickingTheInfoFieldsMovesTheInfoCursor() {
		click(64 + 5 * 8 + 3, 32 + 4); // the song name, character 5
		assertEquals(Part.PART_INFO, session.uiState.activePart);
		assertEquals(EditArea.NAME, session.song.getInfoAct());
		assertEquals(5, session.song.getSongNameCursor());
		assertTrue(session.uiState.isEditingInfos);

		click(120 + 3 * 8 + 4, 48 + 4); // MUSIC SPEED, the main speed digits
		assertEquals(EditArea.MAIN_SPEED, session.song.getInfoAct());
		assertFalse(session.uiState.isEditingInfos);

		click(432 + 3 * 8 + 4, 16 + 4); // HIGHLIGHT, the secondary value
		assertEquals(EditArea.SECOND_HIGHLIGHT, session.song.getInfoAct());
	}

	@Test
	void theWheelOverTheHighlightsOctaveVolumeAndInstrumentFieldsStepsThem() {
		mouse.mouseWheel(432 + 4, 16 + 4, -120);
		assertEquals(9, session.options.trackLinePrimaryHighlight);
		mouse.mouseWheel(432 + 3 * 8 + 4, 16 + 4, 120);
		assertEquals(3, session.options.trackLineSecondaryHighlight);

		mouse.mouseWheel(456 + 4, 64 + 4, -120);
		assertEquals(1, session.song.getOctave());
		mouse.mouseWheel(472 + 4, 80 + 4, 120);
		assertEquals(14, session.song.getVolume());
		mouse.mouseWheel(136 + 4, 80 + 4, -120);
		assertEquals(1, session.song.getActiveInstr());
	}

	@Test
	void theInfoCommandsAndPopupsGoThroughTheCallbacks() {
		click(336 + 4, 48 + 4);
		assertEquals("changeMaxTrackLength", callbacks.lastCall);
		assertEquals(RmtCursor.DIALOG, session.uiState.cursor);
		click(384 + 4, 48 + 4);
		assertEquals("switchMonoStereo", callbacks.lastCall);
		click(280 + 4, 16 + 4);
		assertEquals("toggleNTSC", callbacks.lastCall);

		callbacks.octaveAnswer = 3;
		click(456 + 4, 64 + 4);
		assertEquals("octave@460,68 current 0", callbacks.lastCall);
		assertEquals(3, session.song.getOctave());

		callbacks.volumeAnswer = new MouseInput.VolumeSelection(9, true);
		click(472 + 4, 80 + 4);
		assertEquals(9, session.song.getVolume());
		assertTrue(session.uiState.respectVolume);

		callbacks.instrumentAnswer = 7;
		click(136 + 4, 80 + 4);
		assertEquals(7, session.song.getActiveInstr());
		assertEquals(0, session.uiState.mouseButtonsHeld); // "because the dialog swallows the OnLButtonUp event"
	}

	@Test
	void onTheInstrumentScreenTheZonesEditTheInstrument() {
		session.uiState.activeTi = Part.PART_INSTRUMENTS;
		session.uiState.activePart = Part.PART_INSTRUMENTS;
		InstrumentsUI instrumentsUI = new InstrumentsUI(session);
		Instrument ai = session.instruments.getInstrument(0);
		ai.parameters[Instrument.PAR_ENV_LENGTH] = 3;

		// Drag over the left volume envelope: column 2, volume 10
		Rectangle left = instrumentsUI.getGUIArea(0, InstrumentsUI.Zone.ENVELOPE_LEFT_ENVELOPE);
		mouse.buttonDown(left.x + 2 * 8 + 3, left.y + (15 - 10) * 4 + 1, MouseInput.MK_LBUTTON);
		assertEquals(RmtCursor.ENVELOPE_VOLUME, session.uiState.cursor);
		assertEquals(10, ai.envelope[2][EnvelopeParameter.VOLUMEL]);
		mouse.mouseMove(left.x + 3 * 8 + 3, left.y + (15 - 6) * 4 + 1); // still held: keeps drawing
		assertEquals(6, ai.envelope[3][EnvelopeParameter.VOLUMEL]);
		mouse.buttonUp(MouseInput.MK_LBUTTON);
		mouse.mouseMove(left.x + 1 * 8 + 3, left.y + 1); // released: no change
		assertEquals(0, ai.envelope[1][EnvelopeParameter.VOLUMEL]);

		// The envelope parameter table: column 1, row DISTORTION
		Rectangle table = instrumentsUI.getGUIArea(0, InstrumentsUI.Zone.ENVELOPE_PARAM_TABLE);
		click(table.x + 1 * 8 + 2, table.y + 1 * 16 + 2);
		assertEquals(InstrumentSection.ENVELOPE, ai.activeEditSection);
		assertEquals(1, ai.editEnvelopeX);
		assertEquals(EnvelopeParameter.DISTORTION, ai.editEnvelopeY);

		// Right-clicking the LEN/GO arrow row sets the envelope length
		Rectangle arrows = instrumentsUI.getGUIArea(0, InstrumentsUI.Zone.LEN_AND_GOTO_ARROWS);
		rightClick(arrows.x + 6 * 8 + 2, arrows.y + 2);
		assertEquals(RmtCursor.SET_POSITION, session.uiState.cursor);
		assertEquals(6, ai.parameters[Instrument.PAR_ENV_LENGTH]);

		// The parameters block: DELAY is the first row on the left
		Rectangle params = instrumentsUI.getGUIArea(0, InstrumentsUI.Zone.PARAMETERS);
		click(params.x + 2, params.y + 2);
		assertEquals(InstrumentSection.PARAMETERS, ai.activeEditSection);
		assertEquals(Instrument.PAR_DELAY, ai.editParameterNr);

		// The name
		Rectangle name = instrumentsUI.getGUIArea(0, InstrumentsUI.Zone.INSTRUMENT_NAME);
		click(name.x + 6 * 8 + 4 * 8 + 2, name.y + 2);
		assertEquals(InstrumentSection.NAME, ai.activeEditSection);
		assertEquals(4, ai.editNameCursorPos);
		assertTrue(session.uiState.isEditingInstrumentName);

		// The "INSTRUMENT XX" title opens the instrument popup
		callbacks.instrumentAnswer = 5;
		Rectangle title = instrumentsUI.getGUIArea(0, InstrumentsUI.Zone.INSTRUMENT_NUMBER_DLG);
		click(title.x + 2, title.y + 2);
		assertEquals(5, session.song.getActiveInstr());
	}

	@Test
	void theRightChannelZonesExistOnlyForAStereoSong() {
		InstrumentsUI instrumentsUI = new InstrumentsUI(session);
		assertTrue(instrumentsUI.getGUIArea(0, InstrumentsUI.Zone.ENVELOPE_RIGHT_ENVELOPE) != null);
		session.tracks4_8 = 4;
		assertTrue(instrumentsUI.getGUIArea(0, InstrumentsUI.Zone.ENVELOPE_RIGHT_ENVELOPE) == null);
		assertTrue(instrumentsUI.getGUIArea(0, InstrumentsUI.Zone.ENVELOPE_RIGHT_VOL_NUMS) == null);
	}
}
