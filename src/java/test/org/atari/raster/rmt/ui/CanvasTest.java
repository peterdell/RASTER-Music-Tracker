package org.atari.raster.rmt.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.awt.image.BufferedImage;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The cursor wrapper's origin/column/row arithmetic, checked by drawing through it and through CanvasXY directly and comparing the canvases. */
class CanvasTest {

	private static BufferedImage sheet;

	private CanvasXY expectedXY;
	private CanvasXY actualXY;
	private BufferedImage expected;
	private BufferedImage actual;

	@BeforeAll
	static void loadSheet() {
		sheet = CanvasXY.loadGlyphSheet();
	}

	@BeforeEach
	void setUp() {
		UiState uiState = new UiState();
		uiState.mouseX = -100;
		uiState.mouseY = -100;
		expectedXY = new CanvasXY(sheet, uiState);
		actualXY = new CanvasXY(sheet, uiState);
		expected = new BufferedImage(300, 300, BufferedImage.TYPE_INT_RGB);
		actual = new BufferedImage(300, 300, BufferedImage.TYPE_INT_RGB);
		expectedXY.setTarget(expected);
		actualXY.setTarget(actual);
	}

	private void assertSameImage() {
		for (int y = 0; y < expected.getHeight(); y++) {
			for (int x = 0; x < expected.getWidth(); x++) {
				assertEquals(expected.getRGB(x, y), actual.getRGB(x, y), "pixel (" + x + "," + y + ")");
			}
		}
	}

	@Test
	void printMiniDrawsAtOriginPlusColumnAndRowTimesEightInWhiteByDefault() {
		new Canvas(actualXY, 100, 200).at(3, 2).printMini("F");

		expectedXY.textMiniXY("F", 100 + 3 * 8, 200 + 2 * 8, TextMiniColor.WHITE);
		assertSameImage();
	}

	@Test
	void chainedPositioningAndColorAreHonored() {
		Canvas canvas = new Canvas(actualXY, 8, 8);
		assertSame(canvas, canvas.colorMini(TextMiniColor.YELLOW).at(1, 1).printMini("A").nextRow().atColumn(4).printMini("B"));

		expectedXY.textMiniXY("A", 8 + 1 * 8, 8 + 1 * 8, TextMiniColor.YELLOW);
		expectedXY.textMiniXY("B", 8 + 4 * 8, 8 + 2 * 8, TextMiniColor.YELLOW);
		assertSameImage();
	}

	@Test
	void printNibbleAndPrintByteFormatHexAndTruncate() {
		new Canvas(actualXY, 0, 0).at(0, 0).printNibble(0xA).at(0, 1).printByte(0x3C).at(0, 2).printNibble(0x1A);

		expectedXY.textMiniXY("A", 0, 0, TextMiniColor.WHITE);
		expectedXY.textMiniXY("3C", 0, 8, TextMiniColor.WHITE);
		expectedXY.textMiniXY("1", 0, 16, TextMiniColor.WHITE); // C++'s "%01hX" of 0x1A0, truncated to one character
		assertSameImage();
	}

	@Test
	void printfMiniTruncatesToTheGivenSize() {
		new Canvas(actualXY, 0, 0).printfMini(3, "%d", 123456);

		expectedXY.textMiniXY("123", 0, 0, TextMiniColor.WHITE);
		assertSameImage();
	}

	@Test
	void fillSolidRectIsOffsetByTheOrigin() {
		new Canvas(actualXY, 50, 60).fillSolidRect(1, 2, 3, 4, RgbColor.LINES);

		expectedXY.fillSolidRect(51, 62, 3, 4, RgbColor.LINES);
		assertSameImage();
	}

	@Test
	void originIsExposed() {
		Canvas canvas = new Canvas(actualXY, 7, 9);
		assertEquals(7, canvas.getOriginX());
		assertEquals(9, canvas.getOriginY());
	}
}
