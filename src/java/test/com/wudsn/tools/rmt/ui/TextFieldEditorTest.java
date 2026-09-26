package com.wudsn.tools.rmt.ui;

import static com.wudsn.tools.rmt.ui.VirtualKey.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

class TextFieldEditorTest {

	private static char[] field(String text, int size) {
		char[] f = new char[size];
		Arrays.fill(f, ' ');
		text.getChars(0, text.length(), f, 0);
		return f;
	}

	private static int type(char[] txt, int cur, boolean shift, int... vks) {
		for (int vk : vks) {
			cur = TextFieldEditor.editText(vk, shift, false, txt, cur, txt.length).cursor();
		}
		return cur;
	}

	@Test
	void lettersAreInsertedLowercaseOrUppercaseWithShift() {
		char[] txt = field("", 8);
		int cur = type(txt, 0, false, VK_A, VK_B);
		cur = type(txt, cur, true, VK_C);
		assertEquals("abC     ", new String(txt));
		assertEquals(3, cur);
	}

	@Test
	void digitsGiveTheUsShiftedSymbols() {
		char[] txt = field("", 8);
		type(txt, 0, true, VK_0 + 1, VK_0 + 2, VK_0);
		assertEquals("!@)     ", new String(txt));
	}

	@Test
	void insertingShiftsTheRestRightAndTheLastCharacterFallsOff() {
		char[] txt = field("abcdefgh", 8);
		int cur = type(txt, 2, false, VK_X);
		assertEquals("abxcdefg", new String(txt));
		assertEquals(3, cur);
	}

	@Test
	void backspaceDeleteAndInsertKeys() {
		char[] txt = field("abcdef", 8);
		int cur = type(txt, 3, false, VK_BACK);
		assertEquals("abdef   ", new String(txt));
		assertEquals(2, cur);
		type(txt, cur, false, VK_DELETE);
		assertEquals("abef    ", new String(txt));
		type(txt, cur, false, VK_INSERT);
		assertEquals("ab ef   ", new String(txt));
	}

	@Test
	void homeEndLeftRightMoveTheCursorAndEndGoesPastTheLastNonSpace() {
		char[] txt = field("abc", 8);
		assertEquals(3, type(txt, 0, false, VK_END));
		assertEquals(0, type(txt, 3, false, VK_HOME));
		assertEquals(1, type(txt, 0, false, VK_RIGHT));
		assertEquals(0, type(txt, 1, false, VK_LEFT, VK_LEFT));
		assertEquals(7, type(txt, 0, false, VK_RIGHT, VK_RIGHT, VK_RIGHT, VK_RIGHT, VK_RIGHT, VK_RIGHT, VK_RIGHT, VK_RIGHT)); // clamps at max - 1
	}

	@Test
	void tabAndEnterFinishControlledKeysAreIgnored() {
		char[] txt = field("abc", 8);
		assertTrue(TextFieldEditor.editText(VK_TAB, false, false, txt, 1, 8).done());
		assertTrue(TextFieldEditor.editText(VK_RETURN, false, false, txt, 1, 8).done());
		TextFieldEditor.Result r = TextFieldEditor.editText(VK_A, false, true, txt, 1, 8);
		assertFalse(r.done());
		assertEquals("abc     ", new String(txt));
	}
}
