package org.atari.raster.rmt.ui;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

import javax.imageio.ImageIO;

/**
 * Ported from CCanvasXY (src/cpp/CanvasXY.h/.cpp) - the bitmap-font
 * renderer everything on screen is drawn with. There is no real text
 * rendering anywhere: every character is an opaque 8x16 (or 8x8 "mini")
 * cell copied out of one glyph sheet, {@code gfx-8x16.bmp} (the C++
 * {@code IDB_GFX} resource, checked in here as a plain file next to this
 * class), whose 128 glyphs sit at {@code x = (char & 0x7f) << 3} and whose
 * color is chosen by which horizontal band is copied from
 * ({@link TextColor#getSheetY()}/{@link TextMiniColor#getSheetY()}). The
 * sheet's own background is {@link RgbColor#BACKGROUND}, so an opaque copy
 * of a cell paints that background too - exactly like C++'s
 * {@code BitBlt(..., SRCCOPY)}; only spaces are skipped where C++ skips
 * them (method by method, see each one).
 *
 * <p>C++'s {@code CDC* mem_dc} (the off-screen bitmap) becomes a
 * {@link #setTarget target} {@link BufferedImage}; its static
 * {@code g_gfx_dc} becomes the {@link #loadGlyphSheet() glyph sheet} passed
 * to the constructor. The two globals C++'s highlighting reads -
 * {@code g_mouse} (via {@code IsHoveredXY()}) and {@code g_prove} (via
 * {@code IsProveMode()}) - come from the {@link UiState} passed in, per
 * this port's explicit-collaborator idiom.
 */
public final class CanvasXY {

	public static final int GLYPH_WIDTH = 8;
	public static final int GLYPH_HEIGHT = 16;
	public static final int MINI_GLYPH_HEIGHT = 8;

	private static final int ICON_WIDTH = 32;
	private static final int ICON_HEIGHT = 6;
	/** C++'s {@code static constexpr int c = 128 - 6} in {@code IconMiniXY}: the icon strip's top row. */
	private static final int ICON_SHEET_Y = 128 - ICON_HEIGHT;

	private final BufferedImage glyphSheet;
	private final UiState uiState;

	private BufferedImage target;
	private Graphics2D graphics;
	private Color lineColor = RgbColor.LINES;
	private int penX;
	private int penY;

	/** Reads {@code gfx-8x16.bmp} from next to this class and converts its 8-bit indexed pixels to RGB once, so cells can be copied and compared as plain RGB. */
	public static BufferedImage loadGlyphSheet() {
		try (InputStream in = CanvasXY.class.getResourceAsStream("gfx-8x16.bmp")) {
			if (in == null) {
				throw new IOException("Glyph sheet resource gfx-8x16.bmp not found");
			}
			BufferedImage indexed = ImageIO.read(in);
			BufferedImage rgb = new BufferedImage(indexed.getWidth(), indexed.getHeight(), BufferedImage.TYPE_INT_RGB);
			Graphics2D g = rgb.createGraphics();
			try {
				g.drawImage(indexed, 0, 0, null);
			} finally {
				g.dispose();
			}
			return rgb;
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	public CanvasXY(BufferedImage glyphSheet, UiState uiState) {
		this.glyphSheet = glyphSheet;
		this.uiState = uiState;
	}

	/** C++'s {@code SetCDC()}: the off-screen image everything is drawn into. Replaces (and releases the graphics of) any previous target. */
	public void setTarget(BufferedImage target) {
		if (graphics != null) {
			graphics.dispose();
		}
		this.target = target;
		this.graphics = target.createGraphics();
	}

	public BufferedImage getTarget() {
		return target;
	}

	/** C++'s {@code SelectObject(pen)}: the color subsequent {@link #lineTo} calls draw with. */
	public void setLineColor(Color color) {
		this.lineColor = color;
	}

	/** Returns the previous pen position, as C++'s {@code CDC::MoveTo} does. */
	public Point moveTo(int x, int y) {
		Point previous = new Point(penX, penY);
		penX = x;
		penY = y;
		return previous;
	}

	/**
	 * C++'s {@code CDC::LineTo}: draws from the pen position up to but
	 * <em>excluding</em> the end point (GDI's rule - the reference
	 * screenshots confirm it: the track and envelope lines stop one pixel
	 * short of their {@code LineTo} coordinate), then moves the pen there.
	 * Java's {@code drawLine} includes both ends, so the last pixel is
	 * stepped off. RMT only draws horizontal and vertical lines; the general
	 * (diagonal) case would also need GDI's exact Bresenham, which is not
	 * reproduced.
	 */
	public void lineTo(int x, int y) {
		int endX = x - Integer.signum(x - penX);
		int endY = y - Integer.signum(y - penY);
		if (x != penX || y != penY) {
			graphics.setColor(lineColor);
			graphics.drawLine(penX, penY, endX, endY);
		}
		penX = x;
		penY = y;
	}

	public void fillSolidRect(int x, int y, int cx, int cy, Color color) {
		graphics.setColor(color);
		graphics.fillRect(x, y, cx, cy);
	}

	/** C++'s {@code BitBltText}: copies one opaque cell from the glyph sheet. */
	private void bitBltText(int x, int y, int width, int height, int xSrc, int ySrc) {
		graphics.drawImage(glyphSheet, x, y, x + width, y + height, xSrc, ySrc, xSrc + width, ySrc + height, null);
	}

	private static int glyphX(char c) {
		return (c & 0x7f) << 3;
	}

	public void textXY(String txt, int x, int y) {
		textXY(txt, x, y, TextColor.WHITE);
	}

	/** Draws 8x16 characters; spaces are skipped (left as whatever is underneath), as in C++. */
	public void textXY(String txt, int x, int y, TextColor color) {
		int colorY = color.getSheetY();
		for (int i = 0; i < txt.length(); i++, x += GLYPH_WIDTH) {
			char c = txt.charAt(i);
			if (c == ' ') {
				continue; // Don't draw the space
			}
			bitBltText(x, y, GLYPH_WIDTH, GLYPH_HEIGHT, glyphX(c), colorY);
		}
	}

	/**
	 * Draws text with embedded color-change escapes ({@code '\u0080'} white,
	 * {@code '\u0082'} yellow, {@code '\u0083'} selected-prove,
	 * {@code '\u0085'} cyan, {@code '\u0086'} red, {@code '\u0089'} selected,
	 * {@code '\u008B'} green, {@code '\u008C'} dark gray, {@code '\u008D'}
	 * blue) and newlines; spaces are skipped. C++'s {@code int& x, int& y}
	 * in/out parameters become the returned final position.
	 */
	public Point textXYFull(String txt, int x, int y) {
		TextColor color = TextColor.WHITE;
		int oriX = x;
		for (int i = 0; i < txt.length(); i++) {
			char c = txt.charAt(i);
			switch (c) {
			case '\n' -> {
				x = oriX;
				y += GLYPH_HEIGHT;
				continue;
			}
			case ' ' -> {
				x += GLYPH_WIDTH;
				continue;
			}
			case '\u0080' -> {
				color = TextColor.WHITE;
				continue;
			}
			case '\u0082' -> {
				color = TextColor.YELLOW;
				continue;
			}
			case '\u0083' -> {
				color = TextColor.SELECTED_PROVE;
				continue;
			}
			case '\u0085' -> {
				color = TextColor.CYAN;
				continue;
			}
			case '\u0086' -> {
				color = TextColor.RED;
				continue;
			}
			case '\u0089' -> {
				color = TextColor.SELECTED;
				continue;
			}
			case '\u008B' -> {
				color = TextColor.GREEN;
				continue;
			}
			case '\u008C' -> {
				color = TextColor.DARK_GRAY;
				continue;
			}
			case '\u008D' -> {
				color = TextColor.BLUE;
				continue;
			}
			default -> {
			}
			}
			bitBltText(x, y, GLYPH_WIDTH, GLYPH_HEIGHT, glyphX(c), color.getSheetY());
			x += GLYPH_WIDTH;
		}
		return new Point(x, y);
	}

	private int selectedSheetY() {
		return (uiState.editMode.isProveMode() ? TextColor.SELECTED_PROVE : TextColor.SELECTED).getSheetY();
	}

	/** Draws text with character {@code n} in the "selected" color (prove-mode aware) and any hovered character in the "hovered" color. Unlike {@link #textXY}, spaces are drawn (their cell carries the highlight background). */
	public void textXYSelN(String txt, int n, int x, int y, TextColor color) {
		int colorY = color.getSheetY();
		int col = selectedSheetY();
		int cur = TextColor.HOVERED.getSheetY();
		for (int i = 0; i < txt.length(); i++, x += GLYPH_WIDTH) {
			char c = txt.charAt(i);
			int sheetY = uiState.isHovered(x, y, GLYPH_WIDTH, GLYPH_HEIGHT) ? cur : i == n ? col : colorY;
			bitBltText(x, y, GLYPH_WIDTH, GLYPH_HEIGHT, glyphX(c), sheetY);
		}
	}

	/**
	 * Draws a track line's text with one of its columns selected:
	 * {@code acu} 0 = note (chars 1-3), 1 = instrument (5-6), 2 = volume
	 * (8-9), 3 = effects (11-13), anything else = no selection. Hovered
	 * characters use the hovered color; spaces are skipped.
	 */
	public void textXYCol(String txt, int x, int y, int acu, TextColor color) {
		int colorY = color.getSheetY();
		int col = selectedSheetY();
		int cur = TextColor.HOVERED.getSheetY();

		int num = 0;
		switch (acu) {
		case 0 -> {
			acu = 1;
			num = 3;
		} // Note
		case 1 -> {
			acu = 5;
			num = 2;
		} // Instrument
		case 2 -> {
			acu = 8;
			num = 2;
		} // Volume
		case 3 -> {
			acu = 11;
			num = 3;
		} // Effect(s)
		default -> acu = -1;
		}

		for (int i = 0; i < txt.length(); i++, x += GLYPH_WIDTH) {
			char c = txt.charAt(i);
			if (c == ' ') {
				continue; // Don't draw the space
			}
			int sheetY = uiState.isHovered(x, y, GLYPH_WIDTH, GLYPH_HEIGHT) ? cur : i >= acu && i < acu + num ? col : colorY;
			bitBltText(x, y, GLYPH_WIDTH, GLYPH_HEIGHT, glyphX(c), sheetY);
		}
	}

	/** Draws 8x16 characters one below the other; spaces are drawn, as in C++. */
	public void textDownXY(String txt, int x, int y, TextColor color) {
		int colorY = color.getSheetY();
		for (int i = 0; i < txt.length(); i++, y += GLYPH_HEIGHT) {
			bitBltText(x, y, GLYPH_WIDTH, GLYPH_HEIGHT, glyphX(txt.charAt(i)), colorY);
		}
	}

	/** Draws a byte as two 8x8 hex digits from the mini font, whose glyphs 0-15 are the hex digits. */
	public void numberMiniXY(int num, int x, int y, TextMiniColor color) {
		int colorY = color.getSheetY();
		bitBltText(x, y, GLYPH_WIDTH, MINI_GLYPH_HEIGHT, (num & 0xf0) >> 1, colorY);
		bitBltText(x + GLYPH_WIDTH, y, GLYPH_WIDTH, MINI_GLYPH_HEIGHT, (num & 0x0f) << 3, colorY);
	}

	/** Draws 8x8 mini-font characters; spaces are skipped, as in C++. */
	public void textMiniXY(String txt, int x, int y, TextMiniColor color) {
		int colorY = color.getSheetY();
		for (int i = 0; i < txt.length(); i++, x += GLYPH_WIDTH) {
			char c = txt.charAt(i);
			if (c == ' ') {
				continue; // Don't draw the space
			}
			bitBltText(x, y, GLYPH_WIDTH, MINI_GLYPH_HEIGHT, glyphX(c), colorY);
		}
	}

	/** Draws one of the four 32x6 icons kept in a strip of the glyph sheet; {@code icon} 1-4, anything else draws nothing. */
	public void iconMiniXY(int icon, int x, int y) {
		if (icon >= 1 && icon <= 4) {
			bitBltText(x, y, ICON_WIDTH, ICON_HEIGHT, (icon - 1) * ICON_WIDTH, ICON_SHEET_Y);
		}
	}
}
