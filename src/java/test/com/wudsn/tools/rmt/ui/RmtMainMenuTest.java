package com.wudsn.tools.rmt.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JMenuItem;
import javax.swing.KeyStroke;

import org.junit.jupiter.api.Test;

/** The menu bar and toolbars are built headless (no window needed); this checks structure, accelerator lookup and state updates. */
class RmtMainMenuTest {

	@Test
	void everyCommandHasAtLeastOneWidgetAndTheToolbarsHaveTheirButtons() {
		List<RmtCommandId> executed = new ArrayList<>();
		RmtMainMenu menu = new RmtMainMenu(executed::add);
		RmtToolBars toolBars = new RmtToolBars(menu, executed::add, index -> {
		});

		assertEquals(12, menu.menuBar.getMenuCount());
		for (RmtCommandId id : RmtCommandId.values()) {
			assertFalse(menu.getButtons(id).isEmpty(), id + " has no menu item or toolbar button");
		}
		assertEquals(17 + 7 + 1, toolBars.mainToolBar.getComponentCount()); // 17 buttons, 7 separators, the combo box
		assertEquals(10 + 3, toolBars.blockToolBar.getComponentCount());
		assertEquals(8, menu.getButtons(RmtCommandId.POKEY_REGISTER_INCREASE_BY_01).size()); // the 8 register submenus share the 4 register commands

		menu.getButtons(RmtCommandId.FILE_EXIT).get(0).doClick();
		assertEquals(List.of(RmtCommandId.FILE_EXIT), executed);
	}

	@Test
	void realAcceleratorsAreFoundAndHintOnlyOnesAreNot() {
		RmtMainMenu menu = new RmtMainMenu(id -> {
		});
		assertEquals(RmtCommandId.EDIT_UNDO, menu.lookupAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Z, InputEvent.CTRL_DOWN_MASK)));
		assertEquals(RmtCommandId.PART_TRACKS, menu.lookupAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F2, 0)));
		assertEquals(RmtCommandId.PART_INFO, menu.lookupAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F4, InputEvent.SHIFT_DOWN_MASK)));
		assertEquals(RmtCommandId.SONG_STOP, menu.lookupAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0)));
		assertEquals(RmtCommandId.SONG_INCREASE_PATTERN_STEP_SIZE, menu.lookupAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_ADD, 0)));
		// Ctrl+U is TrackKey's/SongKey's own key, shown in the Song menu only as a hint
		assertNull(menu.lookupAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_U, InputEvent.CTRL_DOWN_MASK)));
		assertNull(menu.lookupAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_C, InputEvent.CTRL_DOWN_MASK)));
		// but the item still displays it
		JMenuItem deleteLine = (JMenuItem) menu.getButtons(RmtCommandId.SONG_DELETEACTUALLINE).get(0);
		assertEquals(KeyStroke.getKeyStroke(KeyEvent.VK_U, InputEvent.CTRL_DOWN_MASK), deleteLine.getAccelerator());
	}

	@Test
	void updateStatesAppliesEnabledCheckedAndDynamicText() {
		RmtSession session = new RmtSession();
		RmtMainMenu menu = new RmtMainMenu(id -> {
		});
		RmtCommands commands = new RmtCommands(session, new SongInput(session), new RmtCommands.Host() {
			@Override
			public void applyViewElements() {
			}

			@Override
			public void updateMinimumSize() {
			}

			@Override
			public void exit() {
			}

			@Override
			public void notAvailable(String feature) {
			}

			@Override
			public void skipLinesChanged() {
			}
		});

		menu.updateStates(commands);
		JMenuItem undo = (JMenuItem) menu.getButtons(RmtCommandId.EDIT_UNDO).get(0);
		assertFalse(undo.isEnabled());
		assertEquals("Undo", undo.getText().trim());
		assertTrue(menu.getButtons(RmtCommandId.CHANNELS_CHANNEL1).get(0).isSelected());
		assertEquals("Switch song to Mono 4 tracks...", ((JMenuItem) menu.getButtons(RmtCommandId.SONG_SONG_TOGGLE_TRACK_NUMBER).get(0)).getText());

		session.channelControl.toggleChannelOnOff(0);
		session.tracks4_8 = 4;
		menu.updateStates(commands);
		assertFalse(menu.getButtons(RmtCommandId.CHANNELS_CHANNEL1).get(0).isSelected());
		assertEquals("Switch song to Stereo 8 tracks...", ((JMenuItem) menu.getButtons(RmtCommandId.SONG_SONG_TOGGLE_TRACK_NUMBER).get(0)).getText());
		assertFalse(menu.getButtons(RmtCommandId.INSTRUMENT_PASTESPECIAL_VOLUMERENVELOPEONLY).get(0).isEnabled()); // mono
	}
}
