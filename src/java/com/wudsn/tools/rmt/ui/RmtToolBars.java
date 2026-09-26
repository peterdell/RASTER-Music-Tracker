package com.wudsn.tools.rmt.ui;

import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.function.Consumer;

import javax.imageio.ImageIO;
import javax.swing.AbstractButton;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JToggleButton;
import javax.swing.JToolBar;

/**
 * The two toolbars of {@code CMainFrame} - {@code IDR_MAIN_WINDOW TOOLBAR}
 * ({@code m_wndToolBar}, with the "skip N lines after note insert" combo
 * box in its last slot) and {@code IDR_TOOLBAR_BLOCK} ({@code m_ToolBarBlock})
 * - with the buttons in Rmt.rc's order, each showing its 32x30 image cut
 * from the original strips {@code toolbar_main_window-32x30.bmp}/
 * {@code toolbar_block-32x30.bmp} (the strips' RGB(192,192,192) is the
 * transparent button-face color, as in MFC). Commands with a checked state
 * ({@code ON_UPDATE_COMMAND_UI ... SetCheck}) are toggle buttons. Buttons
 * are registered with the {@link RmtMainMenu} so one {@code updateStates}
 * pass serves both. ({@code IDR_TOOLBAR_PLAY}/{@code IDR_TOOLBAR_CHANNELS}
 * are defined in Rmt.rc but never created - confirmed dead.)
 */
public final class RmtToolBars {

	private static final int BUTTON_WIDTH = 32;
	private static final int BUTTON_HEIGHT = 30;
	private static final int TRANSPARENT = 0xC0C0C0;

	private static final RmtCommandId[] MAIN_BUTTONS = { RmtCommandId.FILE_NEW, RmtCommandId.FILE_OPEN, RmtCommandId.FILE_SAVE, RmtCommandId.FILE_EXPORT, null, RmtCommandId.HELP_ABOUT_APP, null, RmtCommandId.SONG_PLAY_FROM_BOOKMARK, RmtCommandId.SONG_PLAY_FROM_START, RmtCommandId.SONG_PLAY_FROM_CURRENT_POSITION, RmtCommandId.SONG_PLAY_FROM_CURRENT_POSITION_AND_LOOP, RmtCommandId.SONG_STOP, null, RmtCommandId.SONG_PLAY_FOLLOW, null, RmtCommandId.PART_TRACKS, RmtCommandId.PART_INSTRUMENTS, RmtCommandId.PART_INFO, RmtCommandId.PART_SONG, null, RmtCommandId.TOOLBAR_SWITCH_EDIT_MODE, null, RmtCommandId.MIDIONOFF, null };
	private static final RmtCommandId[] BLOCK_BUTTONS = { RmtCommandId.BLOCK_RESTORE_FROM_BACKUP, null, RmtCommandId.BLOCK_TRANSPOSE_NOTES_UP, RmtCommandId.BLOCK_TRANSPOSE_NOTES_DOWN, RmtCommandId.BLOCK_USE_PREVIOUS_INSTRUMENT, RmtCommandId.BLOCK_USE_NEXT_INSTRUMENT, RmtCommandId.BLOCK_INCREASE_VOLUME, RmtCommandId.BLOCK_DECREASE_VOLUME, RmtCommandId.BLOCK_APPLY_EFFECTS, null, RmtCommandId.BLOCK_TOGGLE_MODIFICATION_MODE, null, RmtCommandId.BLOCK_PLAY_AND_LOOP };

	public final JToolBar mainToolBar = new JToolBar();
	public final JToolBar blockToolBar = new JToolBar();
	/** {@code m_comboSkipLinesAfterNoteInsert}: 0..8. */
	public final JComboBox<String> skipLinesCombo = new JComboBox<>(new String[] { "0", "1", "2", "3", "4", "5", "6", "7", "8" });

	public RmtToolBars(RmtMainMenu menu, Consumer<RmtCommandId> executor, Consumer<Integer> skipLinesListener) {
		mainToolBar.setFloatable(false);
		blockToolBar.setFloatable(false);
		fill(mainToolBar, MAIN_BUTTONS, loadStrip("toolbar_main_window-32x30.bmp"), menu, executor);
		fill(blockToolBar, BLOCK_BUTTONS, loadStrip("toolbar_block-32x30.bmp"), menu, executor);

		skipLinesCombo.setToolTipText(Actions.Toolbar_SkipLinesAfterNoteInsert.getToolTip());
		skipLinesCombo.setSelectedIndex(1);
		skipLinesCombo.setMaximumSize(new Dimension(60, BUTTON_HEIGHT));
		skipLinesCombo.setFocusable(false); // OnRestoreFocusToMainWindow: the tracker keeps the keyboard
		skipLinesCombo.addActionListener(e -> skipLinesListener.accept(skipLinesCombo.getSelectedIndex()));
		mainToolBar.add(skipLinesCombo);
	}

	private static void fill(JToolBar toolBar, RmtCommandId[] ids, BufferedImage strip, RmtMainMenu menu, Consumer<RmtCommandId> executor) {
		int image = 0;
		for (RmtCommandId id : ids) {
			if (id == null) {
				toolBar.addSeparator();
				continue;
			}
			AbstractButton button = id.checkable ? new JToggleButton() : new JButton();
			button.setIcon(new ImageIcon(strip.getSubimage(image * BUTTON_WIDTH, 0, BUTTON_WIDTH, BUTTON_HEIGHT)));
			image++;
			String toolTip = id.action.getToolTip();
			button.setToolTipText(toolTip == null || toolTip.isEmpty() ? id.action.getLabelWithoutMnemonics() : toolTip);
			button.setFocusable(false);
			button.addActionListener(e -> executor.accept(id));
			menu.register(id, button);
			toolBar.add(button);
		}
	}

	/** Reads a strip next to this class and turns its button-face gray into transparency. */
	static BufferedImage loadStrip(String resourceName) {
		try (InputStream in = RmtToolBars.class.getResourceAsStream(resourceName)) {
			if (in == null) {
				throw new IOException("Toolbar strip resource " + resourceName + " not found");
			}
			BufferedImage source = ImageIO.read(in);
			BufferedImage argb = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
			for (int y = 0; y < source.getHeight(); y++) {
				for (int x = 0; x < source.getWidth(); x++) {
					int rgb = source.getRGB(x, y) & 0xFFFFFF;
					argb.setRGB(x, y, rgb == TRANSPARENT ? 0 : 0xFF000000 | rgb);
				}
			}
			return argb;
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}
}
