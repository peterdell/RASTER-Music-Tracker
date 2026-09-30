package org.atari.raster.rmt.model;

/**
 * Ported from Keyboard2NoteMapping.h/.cpp - fully, matching
 * {@code Keyboard2NoteMappingTests.cpp}'s own complete coverage. The C++
 * global {@code g_keyboard_layout} becomes an explicit {@code keyboardLayout}
 * parameter to {@link #noteKey}.
 *
 * <p>C++'s lookup tables are {@code unsigned char[256]} holding {@code 0xFF}
 * for "unmapped", read through a {@code char}-returning function - on this
 * project's platform that's a signed narrowing conversion, turning
 * {@code 0xFF} into {@code -1}. The Java tables are {@code byte[]} with
 * {@code -1} written directly in place of every {@code 0xFF}, the same bit
 * pattern a Java {@code byte} would hold anyway.
 *
 * <p>Neither this class nor its C++ original bounds-checks {@code vk}
 * against the tables' {@code 0..255} range - callers are expected to only
 * ever pass a raw virtual-key byte, and the fragile contract is preserved
 * as-is rather than hardened, matching {@code Keyboard2NoteMappingTests.cpp}
 * itself only ever exercising in-range values.
 */
public final class Keyboard2NoteMapping {

	private Keyboard2NoteMapping() {
	}

	// QWERTY keys layout (package-visible: KeyboardLayout hands each layout its own table)
	static final byte[] KEYNOTES_QWERTY = {
			-1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
			-1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
			-1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
			0x1B, -1, 0x0D, 0x0F, -1, 0x12, 0x14, 0x16, -1, 0x19, -1, -1, -1, -1, -1, -1,
			-1, -1, 0x07, 0x04, 0x03, 0x10, -1, 0x06, 0x08, 0x18, 0x0A, -1, 0x0D, 0x0B, 0x09, 0x1A,
			0x1C, 0x0C, 0x11, 0x01, 0x13, 0x17, 0x05, 0x0E, 0x02, 0x15, 0x00, -1, -1, -1, -1, -1,
			-1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
			-1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
			-1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
			-1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
			-1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
			-1, -1, -1, -1, -1, -1, -1, -1, -1, -1, 0x0F, 0x1E, 0x0C, -1, 0x0E, 0x10,
			-1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
			-1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, 0x1D, -1, 0x1F, -1, -1,
			-1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
			-1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1
	};

	// AZERTY keys layout
	static final byte[] KEYNOTES_AZERTY = {
			-1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
			-1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
			-1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
			0x1B, -1, 0x0D, 0x0F, -1, 0x12, 0x14, 0x16, -1, 0x19, -1, -1, -1, -1, -1, -1,
			-1, 0x0C, 0x07, 0x04, 0x03, 0x10, -1, 0x06, 0x08, 0x18, 0x0A, -1, 0x0D, 0x0F, 0x09, 0x1A,
			0x1C, -1, 0x11, 0x01, 0x13, 0x17, 0x05, 0x00, 0x02, 0x15, 0x0E, -1, -1, -1, -1, -1,
			-1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
			-1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
			-1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
			-1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
			-1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
			-1, -1, -1, -1, -1, -1, -1, -1, -1, -1, 0x1F, 0x1E, 0x0B, -1, 0x0C, 0x0E,
			-1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
			-1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, 0x1D, -1, 0x10,
			-1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
			-1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1
	};

	private static final byte[] KEYNUMBS = {
			-1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
			-1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
			-1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
			0, 1, 2, 3, 4, 5, 6, 7, 8, 9, -1, -1, -1, -1, -1, -1,
			-1, 10, 11, 12, 13, 14, 15, -1, -1, -1, -1, -1, -1, -1, -1, -1,
			-1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
			0, 1, 2, 3, 4, 5, 6, 7, 8, 9, -1, -1, -1, -1, -1, -1,
			-1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
			-1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
			-1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
			-1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
			-1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
			-1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
			-1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
			-1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
			-1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1
	};

	private static final byte[] KEYNUMBLOCK09 = {
			-1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
			-1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
			-1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
			-1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
			-1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
			-1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
			0, 1, 2, 3, 4, 5, 6, 7, 8, 9, -1, -1, -1, -1, -1, -1,
			-1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
			-1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
			-1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
			-1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
			-1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
			-1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
			-1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
			-1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
			-1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1
	};

	// QWERTZ keys layout: the QWERTY table by key position - Y and Z exchanged, the OEM keys moved to the German keys at the QWERTY
	// positions (ü + for [ ], ö for ;, ´ for =, - for /; ß ä # < unmapped like -, ' and the keys QWERTY has not there)
	static final byte[] KEYNOTES_QWERTZ = qwertz();

	private static byte[] qwertz() {
		byte[] keys = KEYNOTES_QWERTY.clone();
		keys['Y'] = 0x00; // C-1, QWERTY Z (bottom left)
		keys['Z'] = 0x15; // A-2, QWERTY Y (under the 6)
		keys[0xBA] = 0x1D; // ü: F-3, QWERTY [
		keys[0xBB] = 0x1F; // +: G-3, QWERTY ]
		keys[0xBD] = 0x10; // -: E-2, QWERTY /
		keys[0xBF] = -1; // #: no QWERTY key there
		keys[0xC0] = 0x0F; // ö: D#2, QWERTY ;
		keys[0xDB] = -1; // ß: QWERTY - (unmapped)
		keys[0xDD] = 0x1E; // ´: F#3, QWERTY =
		return keys;
	}

	public static byte noteKey(int vk, KeyboardLayout keyboardLayout) {
		return keyboardLayout.getNoteKeys()[vk];
	}

	public static byte numbKey(int vk) {
		return KEYNUMBS[vk];
	}

	public static byte numblock09Key(int vk) {
		return KEYNUMBLOCK09[vk];
	}
}
