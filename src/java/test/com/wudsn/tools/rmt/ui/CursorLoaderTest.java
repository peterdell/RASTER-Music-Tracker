package com.wudsn.tools.rmt.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Structural checks of the .cur parser against the five real, checked-in RMT cursor files (no pixel goldens - the assets themselves are the reference). */
class CursorLoaderTest {

	@ParameterizedTest
	@ValueSource(strings = { "cursor_channel_on_off.cur", "cursor_envelope_volume.cur", "cursor_goto.cur", "cursor_goto_dialog.cur", "cursor_set_position.cur" })
	void everyRmtCursorDecodesTo32x32WithAnInBoundsHotspotAndBothOpaqueAndTransparentPixels(String resourceName) {
		CursorLoader.CursorImage cursor = CursorLoader.load(resourceName);

		BufferedImage image = cursor.image();
		assertEquals(32, image.getWidth());
		assertEquals(32, image.getHeight());
		assertTrue(cursor.hotspot().x >= 0 && cursor.hotspot().x < 32, "hotspot x " + cursor.hotspot().x);
		assertTrue(cursor.hotspot().y >= 0 && cursor.hotspot().y < 32, "hotspot y " + cursor.hotspot().y);

		int opaque = 0;
		int transparent = 0;
		for (int y = 0; y < 32; y++) {
			for (int x = 0; x < 32; x++) {
				if ((image.getRGB(x, y) >>> 24) == 0xFF) {
					opaque++;
				} else {
					transparent++;
				}
			}
		}
		assertTrue(opaque > 0, "no opaque pixels");
		assertTrue(transparent > 0, "no transparent pixels");
	}

	@Test
	void rejectsNonCursorData() {
		byte[] icon = new byte[326];
		icon[2] = 1; // ICONDIR type 1 = icon, not 2 = cursor
		assertThrows(IllegalArgumentException.class, () -> CursorLoader.parse(icon));
	}
}
