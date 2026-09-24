package com.wudsn.tools.rmt.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
		void returnsMinusOneForUnknownLayout() {
			assertEquals((byte) -1, Keyboard2NoteMapping.noteKey(0x5A, 2)); // neither QWERTY nor AZERTY
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
