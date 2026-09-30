package org.atari.raster.rmt.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Mirrors src/cpp/test/Keyboard2NoteMappingTests.cpp. */
class Keyboard2NoteMappingTest {

	@Nested
	class NoteKeyTest {

		@Test
		void unmappedVirtualKeyReturnsMinusOne() {
			assertEquals((byte) -1, Keyboard2NoteMapping.noteKey(0, KeyboardLayout.QWERTY));
		}

		@Test
		void qwertyMapsZKeyToNoteZero() {
			// Virtual key 0x5A ('Z') maps to note 0x00 in the QWERTY layout table.
			assertEquals((byte) 0x00, Keyboard2NoteMapping.noteKey(0x5A, KeyboardLayout.QWERTY));
		}

		@Test
		void differsBetweenQwertyAndAzertyForSameKey() {
			// Virtual key 0x41 ('A') is unmapped in QWERTY but maps to 0x0C in
			// AZERTY (AZERTY swaps the A/Q row relative to QWERTY).
			assertEquals((byte) 0xFF, Keyboard2NoteMapping.noteKey(0x41, KeyboardLayout.QWERTY));
			assertEquals((byte) 0x0C, Keyboard2NoteMapping.noteKey(0x41, KeyboardLayout.AZERTY));
		}

		@Test
		void qwertzIsQwertyByPosition() {
			assertEquals((byte) 0x00, Keyboard2NoteMapping.noteKey('Y', KeyboardLayout.QWERTZ)); // C-1 bottom left
			assertEquals((byte) 0x15, Keyboard2NoteMapping.noteKey('Z', KeyboardLayout.QWERTZ)); // A-2 under the 6
			assertEquals((byte) 0x1D, Keyboard2NoteMapping.noteKey(0xBA, KeyboardLayout.QWERTZ)); // ü: F-3 (QWERTY [)
			assertEquals((byte) 0x1F, Keyboard2NoteMapping.noteKey(0xBB, KeyboardLayout.QWERTZ)); // +: G-3 (QWERTY ])
			assertEquals((byte) 0x0F, Keyboard2NoteMapping.noteKey(0xC0, KeyboardLayout.QWERTZ)); // ö: D#2 (QWERTY ;)
			assertEquals((byte) 0x1E, Keyboard2NoteMapping.noteKey(0xDD, KeyboardLayout.QWERTZ)); // ´: F#3 (QWERTY =)
			assertEquals((byte) 0x10, Keyboard2NoteMapping.noteKey(0xBD, KeyboardLayout.QWERTZ)); // -: E-2 (QWERTY /)
			assertEquals((byte) -1, Keyboard2NoteMapping.noteKey(0xDB, KeyboardLayout.QWERTZ)); // ß: unmapped like QWERTY -
			assertEquals((byte) -1, Keyboard2NoteMapping.noteKey(0xBF, KeyboardLayout.QWERTZ)); // #: no QWERTY key there
			assertEquals(Keyboard2NoteMapping.noteKey('Q', KeyboardLayout.QWERTY), Keyboard2NoteMapping.noteKey('Q', KeyboardLayout.QWERTZ)); // the rest as QWERTY
		}

		/**
		 * A layout is a value set since 2026-10-01, so an unknown one cannot reach
		 * {@code noteKey} at all: a number no layout has falls back to QWERTY where
		 * it enters the program (rmt.ini, the RMW parameters). C++ keeps its
		 * NoteKeyReturnsMinusOneForUnknownLayout test, where a cast can still make
		 * an invalid enum value and no key then plays a note.
		 */
		@Test
		void anUnknownLayoutNumberFallsBackToQwerty() {
			assertSame(KeyboardLayout.QWERTY, KeyboardLayout.getInstance(3));
			assertSame(KeyboardLayout.QWERTY, KeyboardLayout.getInstance(-1));
			assertSame(KeyboardLayout.QWERTZ, KeyboardLayout.getInstance(2));
		}
	}

	@Nested
	class NumbKeyTest {

		@Test
		void mapsTopRowDigitsAndNumpadDigits() {
			assertEquals((byte) 0, Keyboard2NoteMapping.numbKey(0x30)); // top-row '0'
			assertEquals((byte) 9, Keyboard2NoteMapping.numbKey(0x39)); // top-row '9'
			assertEquals((byte) 0, Keyboard2NoteMapping.numbKey(0x60)); // VK_NUMPAD0
			assertEquals((byte) 9, Keyboard2NoteMapping.numbKey(0x69)); // VK_NUMPAD9
		}

		@Test
		void unmappedVirtualKeyReturnsMinusOne() {
			assertEquals((byte) -1, Keyboard2NoteMapping.numbKey(0));
			assertEquals((byte) -1, Keyboard2NoteMapping.numbKey(0x3A)); // just past the top-row digits
		}
	}

	@Nested
	class Numblock09KeyTest {

		@Test
		void mapsNumpadDigitsOnly() {
			assertEquals((byte) 0, Keyboard2NoteMapping.numblock09Key(0x60)); // VK_NUMPAD0
			assertEquals((byte) 9, Keyboard2NoteMapping.numblock09Key(0x69)); // VK_NUMPAD9
			assertEquals((byte) -1, Keyboard2NoteMapping.numblock09Key(0x30)); // top-row digits are NOT mapped here
		}
	}
}
