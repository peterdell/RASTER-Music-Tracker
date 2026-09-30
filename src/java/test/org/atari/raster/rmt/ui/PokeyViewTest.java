package org.atari.raster.rmt.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;

import org.junit.jupiter.api.Test;

/** The Pokey Explorer rows of {@link PokeyView}: the formula's numbers ({@link PokeyView#explorerValues}) and the rows' presence. */
class PokeyViewTest {

	@Test
	void theExplorerValuesFollowTheAudctlBits() {
		PokeyView.ExplorerValues plain = PokeyView.explorerValues(0x00, false, false, false);
		assertEquals(28, plain.coarseDivisor()); // 64 kHz
		assertEquals(1, plain.modoffset());
		assertEquals(255, plain.modulo(), "audf + 1 = 1 has no divisor from 3 up: the loop ends at 255 (as Rmt.exe shows for AUDF $00)");

		PokeyView.ExplorerValues clock15 = PokeyView.explorerValues(0x0B, false, false, true);
		assertEquals(114, clock15.coarseDivisor());
		assertEquals(1, clock15.modoffset());
		assertEquals(3, clock15.modulo()); // 12 % 3 == 0

		PokeyView.ExplorerValues clock179 = PokeyView.explorerValues(0x10, false, true, false);
		assertEquals(1, clock179.coarseDivisor());
		assertEquals(4, clock179.modoffset());
		assertEquals(4, clock179.modulo()); // 20 % 3 != 0, 20 % 4 == 0

		PokeyView.ExplorerValues joined = PokeyView.explorerValues(0x03, true, true, true);
		assertEquals(1, joined.coarseDivisor(), "joined 16-bit wins over the clocks");
		assertEquals(7, joined.modoffset());
		assertEquals(5, joined.modulo()); // 10 % 5
	}

	/** Draws the view into an image with and without the explorer mode; the explorer rows (row 21 up) are painted only with it, for the controller's channel. */
	@Test
	void theExplorerRowsAppearInTheExplorerModeOnly() {
		RmtSession session = new RmtSession();
		byte[] memory = session.atari.getMemory();
		memory[0xD200] = 0x40; // AUDF0 of the left POKEY
		memory[0xD201] = (byte) 0xA8;
		BufferedImage sheet = CanvasXY.loadGlyphSheet();

		BufferedImage off = render(session, sheet, false);
		BufferedImage on = render(session, sheet, true);

		int explorerTop = (3 + 9 + 9) * 8; // explorerRow * 8 pixels
		assertFalse(anyPixelSet(off, explorerTop), "nothing below the register dumps without the mode");
		assertTrue(anyPixelSet(on, explorerTop), "the three explorer rows with it");
		assertTrue(sameAbove(off, on, explorerTop), "the register dumps themselves are unchanged");

		session.pokeyController.nextChannel(); // channel 1: still drawn (a different AUDF), the rows move with the channel index only in content
		BufferedImage channel1 = render(session, sheet, true);
		assertTrue(anyPixelSet(channel1, explorerTop));
	}

	private static BufferedImage render(RmtSession session, BufferedImage sheet, boolean explorerMode) {
		BufferedImage image = new BufferedImage(700, 220, BufferedImage.TYPE_INT_RGB);
		CanvasXY canvasXY = new CanvasXY(sheet, session.uiState);
		canvasXY.setTarget(image);
		new PokeyView(new Canvas(canvasXY, 0, 0)).draw(true, session.tuning, session.tuningSettings, session.options.notesPerOctave, session.atari, explorerMode, session.pokeyController);
		return image;
	}

	private static boolean anyPixelSet(BufferedImage image, int fromY) {
		for (int y = fromY; y < image.getHeight(); y++) {
			for (int x = 0; x < image.getWidth(); x++) {
				if ((image.getRGB(x, y) & 0xFFFFFF) != 0) {
					return true;
				}
			}
		}
		return false;
	}

	private static boolean sameAbove(BufferedImage a, BufferedImage b, int belowY) {
		for (int y = 0; y < belowY; y++) {
			for (int x = 0; x < a.getWidth(); x++) {
				if (a.getRGB(x, y) != b.getRGB(x, y)) {
					return false;
				}
			}
		}
		return true;
	}
}
