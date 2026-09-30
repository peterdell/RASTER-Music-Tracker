package org.atari.raster.rmt.ui;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import javax.swing.AbstractButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.KeyStroke;

import com.wudsn.tools.base.gui.ElementFactory;
import com.wudsn.tools.base.repository.Action;

/**
 * The main menu bar - {@code IDR_MAIN_WINDOW MENU} of Rmt.rc, item for item
 * and in the same order, every item built from its {@link Actions} entry
 * via {@link ElementFactory} and dispatching its {@link RmtCommandId} to
 * the executor given to the constructor ({@link RmtCommands#execute}).
 * {@link #updateStates} is the port of the {@code ON_UPDATE_COMMAND_UI}
 * handlers (enabled/checked/dynamic text), driven from the display timer.
 * {@link #lookupAccelerator} is what {@code TranslateAccelerator} did in
 * MFC's message loop: {@link TrackerPanel} asks it first for every key.
 */
public final class RmtMainMenu {

	public final JMenuBar menuBar = new JMenuBar();

	private final Consumer<RmtCommandId> executor;
	private final Map<RmtCommandId, List<AbstractButton>> items = new EnumMap<>(RmtCommandId.class);
	private final Map<KeyStroke, RmtCommandId> accelerators = new HashMap<>();

	public RmtMainMenu(Consumer<RmtCommandId> executor) {
		this.executor = executor;
		menuBar.add(createFileMenu());
		menuBar.add(createEditMenu());
		menuBar.add(createViewMenu());
		menuBar.add(createPlayMenu());
		menuBar.add(createChannelsMenu());
		menuBar.add(createSongMenu());
		menuBar.add(createInstrumentMenu());
		menuBar.add(createTrackMenu());
		menuBar.add(createBlockMenu());
		menuBar.add(createPokeyMenu());
		menuBar.add(createToolsMenu());
		menuBar.add(createHelpMenu());
		accelerators.put(RmtCommandId.SONG_TOGGLE_NTSC.action.getAccelerator(), RmtCommandId.SONG_TOGGLE_NTSC); // Ctrl+F12: an accelerator without a menu item, as in Rmt.rc
	}

	/** The buttons (menu items, and via {@link #register} toolbar buttons) that trigger {@code id}. */
	List<AbstractButton> getButtons(RmtCommandId id) {
		return items.getOrDefault(id, List.of());
	}

	/** The command a menu item or registered toolbar button triggers, or {@code null} ({@link ActionInfos}). */
	RmtCommandId commandOf(AbstractButton button) {
		for (Map.Entry<RmtCommandId, List<AbstractButton>> entry : items.entrySet()) {
			if (entry.getValue().contains(button)) {
				return entry.getKey();
			}
		}
		return null;
	}

	/** Lets {@link RmtToolBars} share the same state updates. */
	void register(RmtCommandId id, AbstractButton button) {
		items.computeIfAbsent(id, k -> new ArrayList<>()).add(button);
	}

	private JMenuItem item(JMenu menu, RmtCommandId id) {
		JMenuItem item;
		if (id.checkable) {
			item = ElementFactory.createCheckBoxMenuItem(id.action);
			item.setAccelerator(id.action.getAccelerator());
		} else {
			item = ElementFactory.createMenuItem(id.action, id.name());
		}
		item.addActionListener(e -> executor.accept(id));
		register(id, item);
		if (id.action.getAccelerator() != null && !id.acceleratorIsHint) {
			accelerators.put(id.action.getAccelerator(), id);
		}
		menu.add(item);
		return item;
	}

	private static JMenu submenu(JMenu parent, Action action) {
		JMenu menu = ElementFactory.createMenu(action);
		parent.add(menu);
		return menu;
	}

	private JMenu createFileMenu() {
		JMenu menu = ElementFactory.createMenu(Actions.MainMenu_File);
		item(menu, RmtCommandId.FILE_NEW);
		item(menu, RmtCommandId.FILE_OPEN);
		item(menu, RmtCommandId.FILE_REOPEN);
		menu.addSeparator();
		item(menu, RmtCommandId.FILE_SAVE);
		item(menu, RmtCommandId.FILE_SAVE_AS);
		menu.addSeparator();
		item(menu, RmtCommandId.FILE_IMPORT);
		item(menu, RmtCommandId.FILE_EXPORT);
		menu.addSeparator();
		item(menu, RmtCommandId.FILE_PRINT);
		item(menu, RmtCommandId.FILE_PRINT_PREVIEW);
		item(menu, RmtCommandId.FILE_PRINT_SETUP);
		menu.addSeparator();
		item(menu, RmtCommandId.FILE_PROPERTIES);
		menu.addSeparator();
		item(menu, RmtCommandId.FILE_EXIT);
		return menu;
	}

	private JMenu createEditMenu() {
		JMenu menu = ElementFactory.createMenu(Actions.MainMenu_Edit);
		item(menu, RmtCommandId.EDIT_UNDO);
		item(menu, RmtCommandId.EDIT_REDO);
		menu.addSeparator();
		item(menu, RmtCommandId.EDIT_CLEAR_UNDO_REDO_HISTORY);
		menu.addSeparator();
		item(menu, RmtCommandId.PART_TRACKS);
		item(menu, RmtCommandId.PART_INSTRUMENTS);
		item(menu, RmtCommandId.PART_INFO);
		item(menu, RmtCommandId.PART_SONG);
		menu.addSeparator();
		item(menu, RmtCommandId.EDIT_SWITCH_EDIT_MODE);
		return menu;
	}

	private JMenu createViewMenu() {
		JMenu menu = ElementFactory.createMenu(Actions.MainMenu_View);
		item(menu, RmtCommandId.VIEW_TOOLBAR);
		item(menu, RmtCommandId.VIEW_BLOCKTOOLBAR);
		menu.addSeparator();
		item(menu, RmtCommandId.VIEW_STATUS_BAR);
		menu.addSeparator();
		item(menu, RmtCommandId.VIEW_PLAYTIMECOUNTER);
		item(menu, RmtCommandId.VIEW_VOLUMEANALYZER);
		menu.addSeparator();
		item(menu, RmtCommandId.VIEW_INSTRUMENTACTIVEHELP);
		return menu;
	}

	private JMenu createPlayMenu() {
		JMenu menu = ElementFactory.createMenu(Actions.MainMenu_Play);
		item(menu, RmtCommandId.SONG_PLAY_FROM_BOOKMARK);
		item(menu, RmtCommandId.SONG_PLAY_FROM_START);
		item(menu, RmtCommandId.SONG_PLAY_FROM_CURRENT_POSITION);
		item(menu, RmtCommandId.SONG_PLAY_FROM_CURRENT_POSITION_AND_LOOP);
		item(menu, RmtCommandId.SONG_STOP);
		item(menu, RmtCommandId.SONG_PLAY_FOLLOW);
		return menu;
	}

	private JMenu createChannelsMenu() {
		JMenu menu = ElementFactory.createMenu(Actions.MainMenu_Channels);
		item(menu, RmtCommandId.CHANNELS_CHANNEL1);
		item(menu, RmtCommandId.CHANNELS_CHANNEL2);
		item(menu, RmtCommandId.CHANNELS_CHANNEL3);
		item(menu, RmtCommandId.CHANNELS_CHANNEL4);
		menu.addSeparator();
		item(menu, RmtCommandId.CHANNELS_CHANNEL5);
		item(menu, RmtCommandId.CHANNELS_CHANNEL6);
		item(menu, RmtCommandId.CHANNELS_CHANNEL7);
		item(menu, RmtCommandId.CHANNELS_CHANNEL8);
		menu.addSeparator();
		item(menu, RmtCommandId.CHANNELS_TOGGLE_ACTIVE_CHANNEL_ON_OFF);
		item(menu, RmtCommandId.CHANNELS_TOGGLE_ALL_CHANNELS_ON_OFF);
		item(menu, RmtCommandId.CHANNELS_TOGGLE_ACTIVE_CHANNEL_SOLO);
		return menu;
	}

	private JMenu createSongMenu() {
		JMenu menu = ElementFactory.createMenu(Actions.MainMenu_Song);
		item(menu, RmtCommandId.SONG_COPY_LINE);
		item(menu, RmtCommandId.SONG_PASTE_LINE);
		item(menu, RmtCommandId.SONG_CLEAR_LINE);
		menu.addSeparator();
		item(menu, RmtCommandId.SONG_SET_BOOKMARK);
		item(menu, RmtCommandId.SONG_CLEAR_BOOKMARK);
		menu.addSeparator();
		item(menu, RmtCommandId.SONG_DELETEACTUALLINE);
		item(menu, RmtCommandId.SONG_INSERTNEWEMPTYLINE);
		item(menu, RmtCommandId.SONG_INSERTNEWLINEWITHUNUSEDTRACKS);
		item(menu, RmtCommandId.SONG_INSERTCOPYORCLONEOFSONGLINES);
		item(menu, RmtCommandId.SONG_PUTNEWEMPTYUNUSEDTRACK);
		item(menu, RmtCommandId.SONG_MAKETRACKSDUPLICATE);
		menu.addSeparator();
		item(menu, RmtCommandId.SONG_SONG_TOGGLE_TRACK_NUMBER);
		item(menu, RmtCommandId.SONG_TRACKSORDERCHANGE);
		item(menu, RmtCommandId.SONG_SONGCHANGEMAXIMALLENGTHOFTRACKS);
		menu.addSeparator();
		item(menu, RmtCommandId.SONG_SIZEOPTIMIZATION);
		return menu;
	}

	private JMenu createInstrumentMenu() {
		JMenu menu = ElementFactory.createMenu(Actions.MainMenu_Instrument);
		item(menu, RmtCommandId.INSTR_COPY);
		item(menu, RmtCommandId.INSTR_PASTE);
		JMenu special = submenu(menu, Actions.MainMenu_Instrument_PasteSpecial);
		item(special, RmtCommandId.INSTRUMENT_PASTESPECIAL_VOLUMELRENVELOPESONLY);
		item(special, RmtCommandId.INSTRUMENT_PASTESPECIAL_ENVELOPEPARAMETERSONLY);
		item(special, RmtCommandId.INSTRUMENT_PASTESPECIAL_VOLUMEENVANDENVELOPEPARSONLY);
		item(special, RmtCommandId.INSTRUMENT_PASTESPECIAL_INSERTVOLUMEENVSANDENVELOPEPARSTOCURSORPOSITION);
		special.addSeparator();
		item(special, RmtCommandId.INSTRUMENT_PASTESPECIAL_VOLUMELENVELOPEONLY);
		item(special, RmtCommandId.INSTRUMENT_PASTESPECIAL_VOLUMERENVELOPEONLY);
		item(special, RmtCommandId.INSTRUMENT_PASTESPECIAL_VOLUMERTOLENVELOPEONLY);
		item(special, RmtCommandId.INSTRUMENT_PASTESPECIAL_VOLUMELTORENVELOPEONLY);
		special.addSeparator();
		item(special, RmtCommandId.INSTRUMENT_PASTESPECIAL_TABLEONLY);
		item(menu, RmtCommandId.INSTR_CUT);
		item(menu, RmtCommandId.INSTR_DELETE);
		menu.addSeparator();
		item(menu, RmtCommandId.INSTRUMENT_INFO);
		item(menu, RmtCommandId.INSTRUMENT_CHANGE);
		item(menu, RmtCommandId.INSTRUMENT_RENUMBERALLINSTRUMENTS);
		menu.addSeparator();
		item(menu, RmtCommandId.INSTR_LOAD);
		item(menu, RmtCommandId.INSTR_SAVE);
		menu.addSeparator();
		item(menu, RmtCommandId.INSTRUMENT_CLEARALLUNUSEDINSTRUMENTS);
		item(menu, RmtCommandId.INSTR_ALLINSTRUMENTSCLEANUP);
		return menu;
	}

	private JMenu createTrackMenu() {
		JMenu menu = ElementFactory.createMenu(Actions.MainMenu_Track);
		item(menu, RmtCommandId.TRACK_COPY);
		item(menu, RmtCommandId.TRACK_PASTE);
		item(menu, RmtCommandId.TRACK_CUT);
		item(menu, RmtCommandId.TRACK_DELETE);
		menu.addSeparator();
		item(menu, RmtCommandId.SONG_INCREASE_PATTERN_STEP_SIZE);
		item(menu, RmtCommandId.SONG_DECREASE_PATTERN_STEP_SIZE);
		menu.addSeparator();
		item(menu, RmtCommandId.TRACK_INFOABOUTUSINGOFACTUALTRACK);
		item(menu, RmtCommandId.TRACK_SEARCHANDBUILDLOOP);
		item(menu, RmtCommandId.TRACK_EXPANDLOOP);
		menu.addSeparator();
		item(menu, RmtCommandId.SONG_SEARCHANDBUILDLOOPSINALLTRACKS);
		item(menu, RmtCommandId.SONG_EXPANDLOOPSINALLTRACKS);
		item(menu, RmtCommandId.TRACK_RENUMBERALLTRACKS);
		menu.addSeparator();
		item(menu, RmtCommandId.TRACK_LOAD);
		item(menu, RmtCommandId.TRACK_SAVE);
		menu.addSeparator();
		item(menu, RmtCommandId.TRACK_CLEARALLDUPLICATEDTRACKS);
		item(menu, RmtCommandId.TRACK_CLEARALLTRACKSUNUSEDINSONG);
		item(menu, RmtCommandId.TRACK_ALLTRACKSCLEANUP);
		return menu;
	}

	private JMenu createBlockMenu() {
		JMenu menu = ElementFactory.createMenu(Actions.MainMenu_Block);
		item(menu, RmtCommandId.BLOCK_RESTORE_FROM_BACKUP);
		menu.addSeparator();
		item(menu, RmtCommandId.BLOCK_COPY);
		item(menu, RmtCommandId.BLOCK_PASTE);
		JMenu special = submenu(menu, Actions.MainMenu_Block_PasteSpecial);
		item(special, RmtCommandId.BLOCK_PASTESPECIAL_MERGEWITHCURRENTCONTENT);
		item(special, RmtCommandId.BLOCK_PASTESPECIAL_VOLUMEVALUESONLY);
		item(special, RmtCommandId.BLOCK_PASTESPECIAL_SPEEDVALUESONLY);
		item(menu, RmtCommandId.BLOCK_CUT);
		item(menu, RmtCommandId.BLOCK_DELETE);
		item(menu, RmtCommandId.BLOCK_EXCHANGE);
		menu.addSeparator();
		item(menu, RmtCommandId.BLOCK_APPLY_EFFECTS);
		menu.addSeparator();
		item(menu, RmtCommandId.BLOCK_SELECTALL);
		return menu;
	}

	private JMenu createPokeyMenu() {
		JMenu menu = ElementFactory.createMenu(Actions.MainMenu_Pokey);
		item(menu, RmtCommandId.VIEW_POKEYREGS);
		item(menu, RmtCommandId.EDIT_ACTIVATE_POKEY_EXPLORER_MODE);
		Action[] channels = { Actions.MainMenu_Pokey_Channel1, Actions.MainMenu_Pokey_Channel2, Actions.MainMenu_Pokey_Channel3, Actions.MainMenu_Pokey_Channel4 };
		Action[] audf = { Actions.MainMenu_Pokey_AUDF0, Actions.MainMenu_Pokey_AUDF1, Actions.MainMenu_Pokey_AUDF2, Actions.MainMenu_Pokey_AUDF3 };
		Action[] audc = { Actions.MainMenu_Pokey_AUDC0, Actions.MainMenu_Pokey_AUDC1, Actions.MainMenu_Pokey_AUDC2, Actions.MainMenu_Pokey_AUDC3 };
		// IDR_POKEY_EXPLORER ACCELERATORS: per register, the digit increases and the letter decreases, Shift for the 0x10 steps -
		// displayed as the C++ menu displays them (hints: PokeyController reads the keys itself, in the explorer mode); the letters are
		// the QWERTY row under the digits, Q W E R T Y U I (the C++ menu said Z for AUDC2 until 2026-09-30 - a German keyboard's view)
		int[] decreaseAudf = { java.awt.event.KeyEvent.VK_Q, java.awt.event.KeyEvent.VK_E, java.awt.event.KeyEvent.VK_T, java.awt.event.KeyEvent.VK_U };
		int[] decreaseAudc = { java.awt.event.KeyEvent.VK_W, java.awt.event.KeyEvent.VK_R, java.awt.event.KeyEvent.VK_Y, java.awt.event.KeyEvent.VK_I };
		RmtCommandId[][] registerCommands = { // [register = channel * 2 + (0 AUDF, 1 AUDC)][increase 01, increase 10, decrease 01, decrease 10]
				{ RmtCommandId.POKEY_AUDF0_INCREASE_BY_01, RmtCommandId.POKEY_AUDF0_INCREASE_BY_10, RmtCommandId.POKEY_AUDF0_DECREASE_BY_01, RmtCommandId.POKEY_AUDF0_DECREASE_BY_10 },
				{ RmtCommandId.POKEY_AUDC0_INCREASE_BY_01, RmtCommandId.POKEY_AUDC0_INCREASE_BY_10, RmtCommandId.POKEY_AUDC0_DECREASE_BY_01, RmtCommandId.POKEY_AUDC0_DECREASE_BY_10 },
				{ RmtCommandId.POKEY_AUDF1_INCREASE_BY_01, RmtCommandId.POKEY_AUDF1_INCREASE_BY_10, RmtCommandId.POKEY_AUDF1_DECREASE_BY_01, RmtCommandId.POKEY_AUDF1_DECREASE_BY_10 },
				{ RmtCommandId.POKEY_AUDC1_INCREASE_BY_01, RmtCommandId.POKEY_AUDC1_INCREASE_BY_10, RmtCommandId.POKEY_AUDC1_DECREASE_BY_01, RmtCommandId.POKEY_AUDC1_DECREASE_BY_10 },
				{ RmtCommandId.POKEY_AUDF2_INCREASE_BY_01, RmtCommandId.POKEY_AUDF2_INCREASE_BY_10, RmtCommandId.POKEY_AUDF2_DECREASE_BY_01, RmtCommandId.POKEY_AUDF2_DECREASE_BY_10 },
				{ RmtCommandId.POKEY_AUDC2_INCREASE_BY_01, RmtCommandId.POKEY_AUDC2_INCREASE_BY_10, RmtCommandId.POKEY_AUDC2_DECREASE_BY_01, RmtCommandId.POKEY_AUDC2_DECREASE_BY_10 },
				{ RmtCommandId.POKEY_AUDF3_INCREASE_BY_01, RmtCommandId.POKEY_AUDF3_INCREASE_BY_10, RmtCommandId.POKEY_AUDF3_DECREASE_BY_01, RmtCommandId.POKEY_AUDF3_DECREASE_BY_10 },
				{ RmtCommandId.POKEY_AUDC3_INCREASE_BY_01, RmtCommandId.POKEY_AUDC3_INCREASE_BY_10, RmtCommandId.POKEY_AUDC3_DECREASE_BY_01, RmtCommandId.POKEY_AUDC3_DECREASE_BY_10 } };
		for (int c = 0; c < 4; c++) {
			JMenu channel = submenu(menu, channels[c]);
			for (int r = 0; r < 2; r++) {
				Action register = r == 0 ? audf[c] : audc[c];
				int increaseKey = java.awt.event.KeyEvent.VK_1 + 2 * c + r;
				int decreaseKey = r == 0 ? decreaseAudf[c] : decreaseAudc[c];
				RmtCommandId[] commands = registerCommands[c * 2 + r];
				JMenu reg = submenu(channel, register);
				item(reg, commands[0]).setAccelerator(KeyStroke.getKeyStroke(increaseKey, 0));
				item(reg, commands[1]).setAccelerator(KeyStroke.getKeyStroke(increaseKey, java.awt.event.InputEvent.SHIFT_DOWN_MASK));
				item(reg, commands[2]).setAccelerator(KeyStroke.getKeyStroke(decreaseKey, 0));
				item(reg, commands[3]).setAccelerator(KeyStroke.getKeyStroke(decreaseKey, java.awt.event.InputEvent.SHIFT_DOWN_MASK));
			}
		}
		JMenu audctl = submenu(menu, Actions.MainMenu_Pokey_AUDCTL);
		item(audctl, RmtCommandId.POKEY_AUDCTL_BIT0);
		item(audctl, RmtCommandId.POKEY_AUDCTL_BIT1);
		item(audctl, RmtCommandId.POKEY_AUDCTL_BIT2);
		item(audctl, RmtCommandId.POKEY_AUDCTL_BIT3);
		item(audctl, RmtCommandId.POKEY_AUDCTL_BIT4);
		item(audctl, RmtCommandId.POKEY_AUDCTL_BIT5);
		item(audctl, RmtCommandId.POKEY_AUDCTL_BIT6);
		item(audctl, RmtCommandId.POKEY_AUDCTL_BIT7);
		JMenu skctl = submenu(menu, Actions.MainMenu_Pokey_SKCTL);
		item(skctl, RmtCommandId.POKEY_SKCTL_TWO_TONE_MODE);
		JMenu debug = submenu(menu, Actions.MainMenu_Pokey_DebugChannel);
		item(debug, RmtCommandId.POKEY_NEXTCHANNEL);
		item(debug, RmtCommandId.POKEY_PREVIOUSCHANNEL);
		JMenu divisor = submenu(menu, Actions.MainMenu_Pokey_Divisor);
		item(divisor, RmtCommandId.POKEY_DIVISOR_INCREASE_BY_01);
		item(divisor, RmtCommandId.POKEY_DIVISOR_INCREASE_BY_1);
		item(divisor, RmtCommandId.POKEY_DIVISOR_DECREASE_BY_01);
		item(divisor, RmtCommandId.POKEY_DIVISOR_DECREASE_BY_1);
		return menu;
	}

	private JMenu createToolsMenu() {
		JMenu menu = ElementFactory.createMenu(Actions.MainMenu_Tools);
		item(menu, RmtCommandId.TOOLS_OPEN_ASMA);
		item(menu, RmtCommandId.TOOLS_OPEN_ASAP_FILE);
		item(menu, RmtCommandId.TOOLS_RUN_SCRIPT); // the Java port's scripting (plans/21_JAVA_SCRIPTING_PLAN.md), no C++ counterpart
		menu.addSeparator();
		item(menu, RmtCommandId.TOOLS_OPTIONS);
		return menu;
	}

	private JMenu createHelpMenu() {
		JMenu menu = ElementFactory.createMenu(Actions.MainMenu_Help);
		item(menu, RmtCommandId.HELP);
		item(menu, RmtCommandId.CONTEXT_HELP);
		item(menu, RmtCommandId.HELP_ABOUT_APP);
		return menu;
	}

	/** The command a keystroke is a real accelerator of, or {@code null}. */
	public RmtCommandId lookupAccelerator(KeyStroke keyStroke) {
		return accelerators.get(keyStroke);
	}

	/** {@code ON_UPDATE_COMMAND_UI}: brings every item's (and registered toolbar button's) enabled/checked state and dynamic text up to date. */
	public void updateStates(RmtCommands commands) {
		for (Map.Entry<RmtCommandId, List<AbstractButton>> entry : items.entrySet()) {
			RmtCommandId id = entry.getKey();
			boolean enabled = commands.isEnabled(id);
			boolean checked = id.checkable && commands.isChecked(id);
			String label = commands.getLabel(id);
			for (AbstractButton button : entry.getValue()) {
				if (button.isEnabled() != enabled) {
					button.setEnabled(enabled);
				}
				if (id.checkable && button.isSelected() != checked) {
					button.setSelected(checked);
				}
				if (label != null && button instanceof JMenuItem && !(button instanceof JCheckBoxMenuItem)) {
					String text = label.replace("&", "");
					if (!text.equals(button.getText())) {
						button.setText(text);
					}
				}
			}
		}
	}
}
