package org.atari.raster.rmt.ui;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.function.Consumer;

import javax.imageio.ImageIO;
import javax.swing.AbstractButton;
import javax.swing.Icon;
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

	/** The buttons in Rmt.rc's order, {@code null} a separator ({@link ActionInfos} lists them too). */
	static final RmtCommandId[] MAIN_BUTTONS = { RmtCommandId.FILE_NEW, RmtCommandId.FILE_OPEN, RmtCommandId.FILE_SAVE, RmtCommandId.FILE_EXPORT, null, RmtCommandId.HELP_ABOUT_APP, null, RmtCommandId.SONG_PLAY_FROM_BOOKMARK, RmtCommandId.SONG_PLAY_FROM_START, RmtCommandId.SONG_PLAY_FROM_CURRENT_POSITION, RmtCommandId.SONG_PLAY_FROM_CURRENT_POSITION_AND_LOOP, RmtCommandId.SONG_STOP, null, RmtCommandId.SONG_PLAY_FOLLOW, null, RmtCommandId.PART_TRACKS, RmtCommandId.PART_INSTRUMENTS, RmtCommandId.PART_INFO, RmtCommandId.PART_SONG, null, RmtCommandId.TOOLBAR_SWITCH_EDIT_MODE, null, RmtCommandId.MIDIONOFF, null };
	static final RmtCommandId[] BLOCK_BUTTONS = { RmtCommandId.BLOCK_RESTORE_FROM_BACKUP, null, RmtCommandId.BLOCK_TRANSPOSE_NOTES_UP, RmtCommandId.BLOCK_TRANSPOSE_NOTES_DOWN, RmtCommandId.BLOCK_USE_PREVIOUS_INSTRUMENT, RmtCommandId.BLOCK_USE_NEXT_INSTRUMENT, RmtCommandId.BLOCK_INCREASE_VOLUME, RmtCommandId.BLOCK_DECREASE_VOLUME, RmtCommandId.BLOCK_APPLY_EFFECTS, null, RmtCommandId.BLOCK_TOGGLE_MODIFICATION_MODE, null, RmtCommandId.BLOCK_PLAY_AND_LOOP };

	public final JToolBar mainToolBar = new JToolBar();
	public final JToolBar blockToolBar = new JToolBar();
	/** {@code m_comboSkipLinesAfterNoteInsert}: 0..8. */
	public final JComboBox<String> skipLinesCombo = new JComboBox<>(new String[] { "0", "1", "2", "3", "4", "5", "6", "7", "8" });

	public RmtToolBars(RmtMainMenu menu, Consumer<RmtCommandId> executor, Consumer<Integer> skipLinesListener) {
		double deviceScale = deviceScale();
		int scale = iconScale(deviceScale);
		mainToolBar.setFloatable(false);
		blockToolBar.setFloatable(false);
		fill(mainToolBar, MAIN_BUTTONS, loadStrip("toolbar_main_window-32x30.bmp"), scale, deviceScale, menu, executor);
		fill(blockToolBar, BLOCK_BUTTONS, loadStrip("toolbar_block-32x30.bmp"), scale, deviceScale, menu, executor);

		skipLinesCombo.setToolTipText(Actions.Toolbar_SkipLinesAfterNoteInsert.getToolTip());
		skipLinesCombo.setSelectedIndex(1);
		skipLinesCombo.setMaximumSize(new Dimension(60, (int) Math.ceil(BUTTON_HEIGHT * scale / deviceScale)));
		skipLinesCombo.setFocusable(false); // OnRestoreFocusToMainWindow: the tracker keeps the keyboard
		skipLinesCombo.addActionListener(e -> skipLinesListener.accept(skipLinesCombo.getSelectedIndex()));
		mainToolBar.add(skipLinesCombo);
	}

	/** The default screen's device scale (1.5 at 150% Windows scaling); 1 headless. */
	static double deviceScale() {
		try {
			if (GraphicsEnvironment.isHeadless()) {
				return 1;
			}
			return GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDefaultConfiguration().getDefaultTransform().getScaleX();
		} catch (RuntimeException e) {
			return 1;
		}
	}

	/**
	 * The integer factor the 32x30 button images are enlarged by in device
	 * pixels on a HiDPI display: 1 below 150% Windows scaling, 2 from 150%,
	 * 3 from 250% (nearest-neighbour, keeping the pixel look). The enlarged
	 * image is drawn 1:1 in device pixels ({@link PixelIcon}), not through
	 * Swing's fractional scaling - the same device-pixel blit the canvas
	 * uses. A deliberate improvement over {@code Rmt.exe}, whose MFC toolbar
	 * is not DPI-aware and stays tiny on such displays.
	 */
	static int iconScale(double deviceScale) {
		return Math.max(1, (int) Math.round(deviceScale));
	}

	/**
	 * An icon holding a device-pixel image: Swing is told a logical size
	 * ({@code image size / device scale}) and the image is drawn with the
	 * scaling transform removed, so its pixels land 1:1 on the screen.
	 */
	static final class PixelIcon implements Icon {
		private final BufferedImage deviceImage;
		private final double deviceScale;

		PixelIcon(BufferedImage deviceImage, double deviceScale) {
			this.deviceImage = deviceImage;
			this.deviceScale = deviceScale;
		}

		@Override
		public int getIconWidth() {
			return (int) Math.ceil(deviceImage.getWidth() / deviceScale);
		}

		@Override
		public int getIconHeight() {
			return (int) Math.ceil(deviceImage.getHeight() / deviceScale);
		}

		@Override
		public void paintIcon(Component c, Graphics g, int x, int y) {
			Graphics2D g2 = (Graphics2D) g.create();
			try {
				AffineTransform transform = g2.getTransform();
				Point2D device = transform.transform(new Point2D.Double(x, y), null);
				g2.setTransform(new AffineTransform());
				g2.drawImage(deviceImage, (int) Math.round(device.getX()), (int) Math.round(device.getY()), null);
			} finally {
				g2.dispose();
			}
		}
	}

	/** {@code image} enlarged {@code scale} times without smoothing. */
	static BufferedImage scaled(BufferedImage image, int scale) {
		if (scale <= 1) {
			return image;
		}
		BufferedImage result = new BufferedImage(image.getWidth() * scale, image.getHeight() * scale, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < result.getHeight(); y++) {
			for (int x = 0; x < result.getWidth(); x++) {
				result.setRGB(x, y, image.getRGB(x / scale, y / scale));
			}
		}
		return result;
	}

	private static void fill(JToolBar toolBar, RmtCommandId[] ids, BufferedImage strip, int scale, double deviceScale, RmtMainMenu menu, Consumer<RmtCommandId> executor) {
		int image = 0;
		for (RmtCommandId id : ids) {
			if (id == null) {
				toolBar.addSeparator();
				continue;
			}
			AbstractButton button = id.checkable ? new JToggleButton() : new JButton();
			BufferedImage face = strip.getSubimage(image * BUTTON_WIDTH, 0, BUTTON_WIDTH, BUTTON_HEIGHT);
			button.setIcon(scale > 1 ? new PixelIcon(scaled(face, scale), deviceScale) : new ImageIcon(face));
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
