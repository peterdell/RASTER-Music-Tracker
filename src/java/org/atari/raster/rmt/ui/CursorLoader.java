package org.atari.raster.rmt.ui;

import java.awt.Cursor;
import java.awt.Point;
import java.awt.Toolkit;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

/**
 * Loads the five custom mouse cursors RMT's C++ view swaps in depending on
 * what is under the mouse ({@code res/cursor_*.cur}, checked in here
 * unchanged next to this class). {@code ImageIO} has no reader for the
 * Windows {@code .cur} format, so this parses it directly rather than
 * keeping a lossy PNG conversion of each asset: a {@code .cur} is the ICO
 * container (6-byte header, 16-byte directory entry - which for cursors
 * carries the hotspot where an icon would carry planes/bit count) around a
 * DIB whose {@code BITMAPINFOHEADER} height covers both the XOR (color)
 * mask and the AND (transparency) mask, each stored bottom-up in rows
 * padded to 4 bytes. All five RMT cursors are 32x32, 1 bit per pixel, so
 * only that case is supported.
 *
 * <p>An AND bit of 1 makes the pixel transparent (Windows' "AND-1/XOR-1
 * = inverted screen" case, which Java cannot express, is drawn as the
 * XOR color instead - none of RMT's five cursors use it).
 */
public final class CursorLoader {

	private CursorLoader() {
	}

	/** A decoded cursor: its ARGB image and the hotspot the file declares. */
	public record CursorImage(BufferedImage image, Point hotspot) {

		/** Converts to an AWT cursor - needs a real (non-headless) toolkit, so it is kept out of the pure parsing path that tests exercise. */
		public Cursor toCursor(String name) {
			return Toolkit.getDefaultToolkit().createCustomCursor(image, hotspot, name);
		}
	}

	public static CursorImage load(String resourceName) {
		try (InputStream in = CursorLoader.class.getResourceAsStream(resourceName)) {
			if (in == null) {
				throw new IOException("Cursor resource " + resourceName + " not found");
			}
			return parse(in.readAllBytes());
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	static CursorImage parse(byte[] data) {
		if (readWord(data, 0) != 0 || readWord(data, 2) != 2 || readWord(data, 4) < 1) {
			throw new IllegalArgumentException("Not a .cur file");
		}
		// First (and for RMT's cursors only) directory entry.
		int entry = 6;
		int hotspotX = readWord(data, entry + 4);
		int hotspotY = readWord(data, entry + 6);
		int offset = readDword(data, entry + 12);

		// BITMAPINFOHEADER
		int width = readDword(data, offset + 4);
		int height = readDword(data, offset + 8) / 2; // XOR + AND masks stacked
		int bitCount = readWord(data, offset + 14);
		if (bitCount != 1) {
			throw new IllegalArgumentException("Only 1-bit cursors are supported, got " + bitCount + " bpp");
		}
		int paletteOffset = offset + readDword(data, offset);
		int[] palette = { readBgr(data, paletteOffset), readBgr(data, paletteOffset + 4) };

		int rowBytes = ((width + 31) / 32) * 4; // 1 bpp rows, padded to 4 bytes
		int xorOffset = paletteOffset + 8;
		int andOffset = xorOffset + rowBytes * height;

		BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < height; y++) {
			int fileRow = height - 1 - y; // bottom-up
			for (int x = 0; x < width; x++) {
				int xorBit = bit(data, xorOffset + fileRow * rowBytes, x);
				int andBit = bit(data, andOffset + fileRow * rowBytes, x);
				int argb;
				if (andBit == 1 && xorBit == 0) {
					argb = 0; // transparent
				} else {
					argb = 0xFF000000 | palette[xorBit];
				}
				image.setRGB(x, y, argb);
			}
		}
		return new CursorImage(image, new Point(hotspotX, hotspotY));
	}

	private static int bit(byte[] data, int rowOffset, int x) {
		return (data[rowOffset + (x >> 3)] >> (7 - (x & 7))) & 1;
	}

	private static int readWord(byte[] data, int pos) {
		return (data[pos] & 0xFF) | ((data[pos + 1] & 0xFF) << 8);
	}

	private static int readDword(byte[] data, int pos) {
		return readWord(data, pos) | (readWord(data, pos + 2) << 16);
	}

	/** A palette entry is stored as B, G, R, reserved. */
	private static int readBgr(byte[] data, int pos) {
		return ((data[pos + 2] & 0xFF) << 16) | ((data[pos + 1] & 0xFF) << 8) | (data[pos] & 0xFF);
	}
}
