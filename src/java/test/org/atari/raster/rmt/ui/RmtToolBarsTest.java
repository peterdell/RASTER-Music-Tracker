package org.atari.raster.rmt.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;

import org.junit.jupiter.api.Test;

class RmtToolBarsTest {

	@Test
	void theStripsLoadWithTheButtonFaceGrayTransparent() {
		BufferedImage strip = RmtToolBars.loadStrip("toolbar_main_window-32x30.bmp");
		assertEquals(30, strip.getHeight());
		assertTrue(strip.getWidth() >= 32 * 18, "18 button images, was " + strip.getWidth());
		boolean transparent = false;
		for (int x = 0; x < 32 && !transparent; x++) {
			transparent = (strip.getRGB(x, 0) >>> 24) == 0;
		}
		assertTrue(transparent, "the first image's top row has transparent pixels");
	}

	@Test
	void theDeviceScaleIsAtLeastOne() {
		assertTrue(RmtToolBars.deviceScale() >= 1);
	}

	// The button images keep Rmt.exe's size on every display: 32x30 device pixels, whatever the Windows scaling (the user's decision of 2026-09-28)
	@Test
	void aPixelIconReportsItsLogicalSizeAndPaintsInDevicePixels() {
		BufferedImage device = new BufferedImage(32, 30, BufferedImage.TYPE_INT_ARGB); // a button face, drawn 1:1
		for (int y = 0; y < 30; y++) {
			for (int x = 0; x < 32; x++) {
				device.setRGB(x, y, 0xFF00FF00);
			}
		}
		RmtToolBars.PixelIcon icon = new RmtToolBars.PixelIcon(device, 1.5);
		assertEquals(22, icon.getIconWidth()); // ceil(32 / 1.5)
		assertEquals(20, icon.getIconHeight()); // ceil(30 / 1.5)

		// painted through a 1.5x transform, the pixels still land 1:1: 32x30 device pixels from the device position of (10, 10)
		BufferedImage screen = new BufferedImage(200, 200, BufferedImage.TYPE_INT_ARGB);
		java.awt.Graphics2D g = screen.createGraphics();
		g.scale(1.5, 1.5);
		icon.paintIcon(null, g, 10, 10);
		g.dispose();
		assertEquals(0xFF00FF00, screen.getRGB(15, 15));
		assertEquals(0xFF00FF00, screen.getRGB(15 + 31, 15 + 29));
		assertEquals(0, screen.getRGB(15 + 32, 15));
		assertEquals(0, screen.getRGB(15, 15 + 30));
	}
}
