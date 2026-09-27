package com.wudsn.tools.rmt.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
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
	void scalingEnlargesEveryPixelWithoutSmoothing() {
		BufferedImage image = new BufferedImage(2, 1, BufferedImage.TYPE_INT_ARGB);
		image.setRGB(0, 0, 0xFF112233);
		image.setRGB(1, 0, 0);
		assertSame(image, RmtToolBars.scaled(image, 1));

		BufferedImage twice = RmtToolBars.scaled(image, 2);
		assertEquals(4, twice.getWidth());
		assertEquals(2, twice.getHeight());
		for (int y = 0; y < 2; y++) {
			assertEquals(0xFF112233, twice.getRGB(0, y));
			assertEquals(0xFF112233, twice.getRGB(1, y));
			assertEquals(0, twice.getRGB(2, y));
			assertEquals(0, twice.getRGB(3, y));
		}
	}

	@Test
	void theIconScaleRoundsTheDeviceScale() {
		assertEquals(1, RmtToolBars.iconScale(1.0));
		assertEquals(1, RmtToolBars.iconScale(1.25));
		assertEquals(2, RmtToolBars.iconScale(1.5));
		assertEquals(2, RmtToolBars.iconScale(2.0));
		assertEquals(3, RmtToolBars.iconScale(2.5));
		assertTrue(RmtToolBars.deviceScale() >= 1);
	}

	@Test
	void aPixelIconReportsItsLogicalSizeAndPaintsInDevicePixels() {
		BufferedImage device = new BufferedImage(64, 60, BufferedImage.TYPE_INT_ARGB); // a 32x30 face at 2x
		for (int y = 0; y < 60; y++) {
			for (int x = 0; x < 64; x++) {
				device.setRGB(x, y, 0xFF00FF00);
			}
		}
		RmtToolBars.PixelIcon icon = new RmtToolBars.PixelIcon(device, 1.5);
		assertEquals(43, icon.getIconWidth()); // ceil(64 / 1.5)
		assertEquals(40, icon.getIconHeight()); // ceil(60 / 1.5)

		// painted through a 1.5x transform, the pixels still land 1:1: 64x60 device pixels from the device position of (10, 10)
		BufferedImage screen = new BufferedImage(200, 200, BufferedImage.TYPE_INT_ARGB);
		java.awt.Graphics2D g = screen.createGraphics();
		g.scale(1.5, 1.5);
		icon.paintIcon(null, g, 10, 10);
		g.dispose();
		assertEquals(0xFF00FF00, screen.getRGB(15, 15));
		assertEquals(0xFF00FF00, screen.getRGB(15 + 63, 15 + 59));
		assertEquals(0, screen.getRGB(15 + 64, 15));
		assertEquals(0, screen.getRGB(15, 15 + 60));
	}
}
