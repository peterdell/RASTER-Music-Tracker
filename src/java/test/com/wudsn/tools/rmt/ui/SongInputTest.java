package com.wudsn.tools.rmt.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.event.KeyEvent;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.wudsn.tools.rmt.model.Part;

/** Logic-level tests of the navigation keys: feed key codes, assert the cursor state in {@code Song}. */
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

	private void press(int keyCode) {
		input.keyDown(keyCode);
		input.keyUp(keyCode);
	}

	@Test
	void downAndUpMoveTheTrackLine() {
		press(KeyEvent.VK_DOWN);
		assertEquals(1, session.song.getActiveLine());
		press(KeyEvent.VK_UP);
		assertEquals(0, session.song.getActiveLine());
	}

	@Test
	void upFromLineZeroContinuesIntoThePreviousSonglineBecauseUpDownContinueIsOn() {
		press(KeyEvent.VK_UP);
		assertEquals(255, session.song.songGetActiveLine());
		assertEquals(63, session.song.getActiveLine());
	}

	@Test
	void shiftAndControlAreTrackedAcrossKeyDownAndUp() {
		input.keyDown(KeyEvent.VK_SHIFT);
		assertTrue(session.uiState.shiftKey);
		input.keyDown(KeyEvent.VK_CONTROL);
		assertTrue(session.uiState.controlKey);
		input.keyUp(KeyEvent.VK_SHIFT);
		assertFalse(session.uiState.shiftKey);
		input.keyUp(KeyEvent.VK_CONTROL);
		assertFalse(session.uiState.controlKey);
	}

	@Test
	void controlDownMovesToTheNextSongline() {
		input.keyDown(KeyEvent.VK_CONTROL);
		input.keyDown(KeyEvent.VK_DOWN);
		assertEquals(1, session.song.songGetActiveLine());
		assertEquals(0, session.song.getActiveLine());
	}

	@Test
	void rightMovesThroughTheSubColumnsThenToTheNextColumnAndTabSkipsStraightToIt() {
		press(KeyEvent.VK_RIGHT);
		assertEquals(0, session.song.getActiveColumn());
		assertEquals(1, session.song.getTrackActiveCur());
		press(KeyEvent.VK_TAB);
		assertEquals(1, session.song.getActiveColumn());
		assertEquals(1, session.song.getTrackActiveCur());
		input.keyDown(KeyEvent.VK_SHIFT);
		input.keyDown(KeyEvent.VK_TAB);
		assertEquals(0, session.song.getActiveColumn());
	}

	@Test
	void pageDownJumpsToTheNextPrimaryHighlightAndPageUpBack() {
		press(KeyEvent.VK_PAGE_DOWN);
		assertEquals(8, session.song.getActiveLine());
		press(KeyEvent.VK_DOWN);
		press(KeyEvent.VK_PAGE_UP);
		assertEquals(8, session.song.getActiveLine());
		press(KeyEvent.VK_PAGE_UP);
		assertEquals(0, session.song.getActiveLine());
	}

	@Test
	void pageDownStopsAtTheLastHighlightInsideThePattern() {
		session.song.setActiveLine(60);
		press(KeyEvent.VK_PAGE_DOWN);
		assertEquals(56, session.song.getActiveLine()); // 64 would be past the 64-line pattern
	}

	@Test
	void endGoesToTheLastLineAndHomeBackToTheFirst() {
		press(KeyEvent.VK_END);
		assertEquals(63, session.song.getActiveLine());
		press(KeyEvent.VK_HOME);
		assertEquals(0, session.song.getActiveLine());
	}

	@Test
	void songPartNavigatesSonglinesAndEnterReturnsToTheTracks() {
		session.uiState.activePart = Part.PART_SONG;
		press(KeyEvent.VK_DOWN);
		assertEquals(1, session.song.songGetActiveLine());
		press(KeyEvent.VK_RIGHT);
		assertEquals(1, session.song.getActiveColumn());
		press(KeyEvent.VK_END);
		assertEquals(1, session.song.songGetActiveLine()); // the last songline with a track
		press(KeyEvent.VK_HOME);
		assertEquals(0, session.song.songGetActiveLine());
		press(KeyEvent.VK_ENTER);
		assertEquals(Part.PART_TRACKS, session.uiState.activePart);
	}

	@Test
	void theLastKeyIsRememberedForTheDebugLine() {
		press(KeyEvent.VK_DOWN);
		assertEquals(KeyEvent.VK_DOWN, session.uiState.lastKeyPressed);
	}
}
