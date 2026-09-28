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

	// Disabled buttons must look disabled on every display: Swing grays an ImageIcon by itself but not a PixelIcon (the HiDPI
	// case), so the toolbar sets the grayed face explicitly (the user, 2026-09-29: the Java icons did not look disabled)
	@Test
	void aDisabledFaceIsGrayWhereTheFaceHadColorAndTransparentWhereItWasTransparent() {
		BufferedImage face = new BufferedImage(2, 1, BufferedImage.TYPE_INT_ARGB);
		face.setRGB(0, 0, 0xFFFF0000); // opaque red
		face.setRGB(1, 0, 0); // transparent

		BufferedImage disabled = RmtToolBars.disabledFace(face);

		int rgb = disabled.getRGB(0, 0);
		assertEquals(0xFF, rgb >>> 24, "still opaque");
		int r = (rgb >> 16) & 0xFF, gr = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
		assertTrue(r == gr && gr == b, "gray: " + Integer.toHexString(rgb));
		assertTrue(r != 0xFF, "not the original red");
		assertEquals(0, disabled.getRGB(1, 0) >>> 24, "transparency kept");
	}

	@Test
	void theIconIsAPixelIconOnAScaledDisplayAndAnImageIconOtherwise() {
		BufferedImage face = new BufferedImage(32, 30, BufferedImage.TYPE_INT_ARGB);
		assertTrue(RmtToolBars.icon(face, 1.5) instanceof RmtToolBars.PixelIcon);
		assertTrue(RmtToolBars.icon(face, 1.0) instanceof javax.swing.ImageIcon);
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
