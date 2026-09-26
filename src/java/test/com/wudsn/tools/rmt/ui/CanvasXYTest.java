package com.wudsn.tools.rmt.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Point;
import java.awt.image.BufferedImage;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.wudsn.tools.rmt.model.EditMode;

/**
 * Proves CanvasXY's blit arithmetic against the glyph sheet itself
 * (analytically, cell by cell) rather than against golden images: after
 * drawing, each affected canvas cell must equal the exact sheet cell C++'s
 * {@code BitBlt} would have copied, and every other pixel must still hold
 * the sentinel color the canvas was filled with. There is no C++
 * characterization test to mirror - {@code CanvasXY.cpp} was never
 * testable there (real GDI).
 */
class CanvasXYTest {

	private static final int SENTINEL = 0xFF00FF; // magenta - not a color the sheet contains
	private static final int BACKGROUND = 0x223250; // RgbColor.BACKGROUND, also the sheet's own background

	private static BufferedImage sheet;

	private UiState uiState;
	private CanvasXY canvas;
	private BufferedImage target;

	@BeforeAll
	static void loadSheet() {
		sheet = CanvasXY.loadGlyphSheet();
	}

	@BeforeEach
	void setUp() {
		uiState = new UiState();
		uiState.mouseX = -100; // hovering nothing
		uiState.mouseY = -100;
		canvas = new CanvasXY(sheet, uiState);
		target = new BufferedImage(200, 100, BufferedImage.TYPE_INT_RGB);
		for (int y = 0; y < target.getHeight(); y++) {
			for (int x = 0; x < target.getWidth(); x++) {
				target.setRGB(x, y, SENTINEL);
			}
		}
		canvas.setTarget(target);
	}

	private int rgb(int x, int y) {
		return target.getRGB(x, y) & 0xFFFFFF;
	}

	private int sheetRgb(int x, int y) {
		return sheet.getRGB(x, y) & 0xFFFFFF;
	}

	/** Asserts the {@code w}x{@code h} canvas cell at ({@code x},{@code y}) is a copy of the sheet cell at ({@code sx},{@code sy}). */
	private void assertCellCopied(int x, int y, int w, int h, int sx, int sy) {
		for (int dy = 0; dy < h; dy++) {
			for (int dx = 0; dx < w; dx++) {
				assertEquals(sheetRgb(sx + dx, sy + dy), rgb(x + dx, y + dy), "pixel (" + (x + dx) + "," + (y + dy) + ")");
			}
		}
	}

	private void assertCellUntouched(int x, int y, int w, int h) {
		for (int dy = 0; dy < h; dy++) {
			for (int dx = 0; dx < w; dx++) {
				assertEquals(SENTINEL, rgb(x + dx, y + dy), "pixel (" + (x + dx) + "," + (y + dy) + ")");
			}
		}
	}

	@Test
	void glyphSheetHasTheExpectedGeometryAndBackground() {
		assertEquals(1024, sheet.getWidth()); // 128 glyphs x 8 pixels
		assertEquals(240, sheet.getHeight()); // 15 bands x 16 pixels
		assertEquals(BACKGROUND, sheetRgb(0, 0));
	}

	@Test
	void textXYCopiesEachGlyphCellFromTheColorsBand() {
		canvas.textXY("AB", 16, 32, TextColor.GREEN);

		int bandY = TextColor.GREEN.band << 4; // 176
		assertEquals(176, bandY);
		assertCellCopied(16, 32, 8, 16, 'A' << 3, bandY);
		assertCellCopied(24, 32, 8, 16, 'B' << 3, bandY);
		assertCellUntouched(32, 32, 8, 16); // nothing past the text
		assertCellUntouched(16, 48, 8, 16); // nothing below it
	}

	@Test
	void textXYDefaultsToWhiteAndSkipsSpaces() {
		canvas.textXY(" A", 0, 0);

		assertCellUntouched(0, 0, 8, 16); // the space is not drawn
		assertCellCopied(8, 0, 8, 16, 'A' << 3, TextColor.WHITE.getSheetY());
	}

	@Test
	void textXYMasksTheCharacterToSevenBits() {
		canvas.textXY("Á", 0, 0, TextColor.WHITE); // 0xC1 & 0x7f == 'A'

		assertCellCopied(0, 0, 8, 16, 'A' << 3, 0);
	}

	@Test
	void textXYSelNHighlightsCharacterNAndDrawsSpaces() {
		canvas.textXYSelN("A B", 2, 0, 0, TextColor.GRAY);

		assertCellCopied(0, 0, 8, 16, 'A' << 3, TextColor.GRAY.getSheetY());
		assertCellCopied(8, 0, 8, 16, ' ' << 3, TextColor.GRAY.getSheetY()); // the space cell is copied here
		assertCellCopied(16, 0, 8, 16, 'B' << 3, TextColor.SELECTED.getSheetY()); // INVERSE_RED, band 9
	}

	@Test
	void textXYSelNUsesTheProveModeSelectionColorInJamMode() {
		uiState.editMode = EditMode.JAM_MONO_MODE;

		canvas.textXYSelN("A", 0, 0, 0, TextColor.WHITE);

		assertCellCopied(0, 0, 8, 16, 'A' << 3, TextColor.SELECTED_PROVE.getSheetY()); // INVERSE_BLUE, band 3
	}

	@Test
	void textXYSelNUsesTheHoveredColorForTheCharacterUnderTheMouse() {
		uiState.mouseX = 12; // inside the second character's 8x16 cell
		uiState.mouseY = 5;

		canvas.textXYSelN("AB", -1, 0, 0, TextColor.WHITE);

		assertCellCopied(0, 0, 8, 16, 'A' << 3, TextColor.WHITE.getSheetY());
		assertCellCopied(8, 0, 8, 16, 'B' << 3, TextColor.HOVERED.getSheetY()); // INVERSE_WHITE, band 4
	}

	@Test
	void textXYColSelectsTheColumnForEachAcuValue() {
		String line = "0123456789ABCD"; // 14 characters, no spaces
		int y = 0;
		for (int acu = 0; acu <= 4; acu++, y += 16) {
			canvas.textXYCol(line, 0, y, acu, TextColor.WHITE);
		}

		int[][] selected = { { 1, 3 }, { 5, 6 }, { 8, 9 }, { 11, 13 } }; // note, instrument, volume, effects
		y = 0;
		for (int acu = 0; acu <= 4; acu++, y += 16) {
			for (int i = 0; i < line.length(); i++) {
				boolean isSelected = acu < 4 && i >= selected[acu][0] && i <= selected[acu][1];
				int bandY = (isSelected ? TextColor.SELECTED : TextColor.WHITE).getSheetY();
				assertCellCopied(i * 8, y, 8, 16, line.charAt(i) << 3, bandY);
			}
		}
	}

	@Test
	void textXYColSkipsSpaces() {
		canvas.textXYCol("A B", 0, 0, 0, TextColor.WHITE);

		assertCellUntouched(8, 0, 8, 16);
	}

	@Test
	void textDownXYStacksCharactersVertically() {
		canvas.textDownXY("A B", 0, 0, TextColor.CYAN);

		int bandY = TextColor.CYAN.getSheetY();
		assertCellCopied(0, 0, 8, 16, 'A' << 3, bandY);
		assertCellCopied(0, 16, 8, 16, ' ' << 3, bandY); // spaces are drawn here
		assertCellCopied(0, 32, 8, 16, 'B' << 3, bandY);
	}

	@Test
	void textXYFullHonorsColorEscapesNewlinesAndReturnsTheFinalPosition() {
		Point end = canvas.textXYFull("A\u008BB\nC", 16, 0);

		assertCellCopied(16, 0, 8, 16, 'A' << 3, TextColor.WHITE.getSheetY());
		assertCellCopied(24, 0, 8, 16, 'B' << 3, TextColor.GREEN.getSheetY());
		assertCellCopied(16, 16, 8, 16, 'C' << 3, TextColor.GREEN.getSheetY()); // color persists across the newline
		assertEquals(new Point(24, 16), end);
	}

	@Test
	void textMiniXYCopiesEightByEightCellsFromTheMiniBands() {
		canvas.textMiniXY("A B", 0, 0, TextMiniColor.YELLOW);

		int bandY = 112 + (3 << 3); // 136
		assertEquals(bandY, TextMiniColor.YELLOW.getSheetY());
		assertCellCopied(0, 0, 8, 8, 'A' << 3, bandY);
		assertCellUntouched(8, 0, 8, 8); // mini spaces are skipped too
		assertCellCopied(16, 0, 8, 8, 'B' << 3, bandY);
		assertCellUntouched(0, 8, 8, 8); // only 8 rows tall
	}

	@Test
	void numberMiniXYDrawsHighAndLowNibbleFromTheMiniHexGlyphs() {
		canvas.numberMiniXY(0xA5, 0, 0, TextMiniColor.GRAY);

		int bandY = TextMiniColor.GRAY.getSheetY(); // 112
		assertCellCopied(0, 0, 8, 8, 0xA << 3, bandY); // (0xA0 >> 1) == glyph 10's x offset
		assertCellCopied(8, 0, 8, 8, 0x5 << 3, bandY);
	}

	@Test
	void iconMiniXYCopiesFromTheIconStrip() {
		canvas.iconMiniXY(2, 0, 0);
		canvas.iconMiniXY(5, 0, 50); // out of range: nothing drawn

		assertCellCopied(0, 0, 32, 6, 32, 122);
		assertCellUntouched(0, 50, 32, 6);
	}

	@Test
	void fillSolidRectAndLinesUseTheGivenColors() {
		canvas.fillSolidRect(10, 10, 4, 2, RgbColor.BACKGROUND);
		canvas.setLineColor(RgbColor.NORMAL);
		Point previous = canvas.moveTo(0, 50);
		canvas.lineTo(3, 50);

		assertEquals(new Point(0, 0), previous);
		assertEquals(BACKGROUND, rgb(10, 10));
		assertEquals(BACKGROUND, rgb(13, 11));
		assertEquals(SENTINEL, rgb(14, 10));
		for (int x = 0; x <= 3; x++) {
			assertEquals(0xFFFFFF, rgb(x, 50));
		}
		assertTrue(rgb(4, 50) == SENTINEL);
	}
}
