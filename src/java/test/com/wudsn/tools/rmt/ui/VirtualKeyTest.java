package com.wudsn.tools.rmt.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.awt.event.KeyEvent;

import org.junit.jupiter.api.Test;

class VirtualKeyTest {

	@Test
	void keysWhoseCodesDifferBetweenSwingAndWindowsAreTranslated() {
		assertEquals(VirtualKey.VK_RETURN, VirtualKey.fromKeyEvent(KeyEvent.VK_ENTER));
		assertEquals(VirtualKey.VK_INSERT, VirtualKey.fromKeyEvent(KeyEvent.VK_INSERT));
		assertEquals(VirtualKey.VK_DELETE, VirtualKey.fromKeyEvent(KeyEvent.VK_DELETE));
		assertEquals(VirtualKey.VK_MENU, VirtualKey.fromKeyEvent(KeyEvent.VK_ALT));
		assertEquals(VirtualKey.VK_OEM_1, VirtualKey.fromKeyEvent(KeyEvent.VK_SEMICOLON));
		assertEquals(VirtualKey.VK_OEM_COMMA, VirtualKey.fromKeyEvent(KeyEvent.VK_COMMA));
		assertEquals(VirtualKey.VK_OEM_4, VirtualKey.fromKeyEvent(KeyEvent.VK_OPEN_BRACKET));
	}

	@Test
	void keysWithIdenticalCodesPassThrough() {
		assertEquals(VirtualKey.VK_A, VirtualKey.fromKeyEvent(KeyEvent.VK_A));
		assertEquals(VirtualKey.VK_0, VirtualKey.fromKeyEvent(KeyEvent.VK_0));
		assertEquals(VirtualKey.VK_PRIOR, VirtualKey.fromKeyEvent(KeyEvent.VK_PAGE_UP));
		assertEquals(VirtualKey.VK_NUMPAD0, VirtualKey.fromKeyEvent(KeyEvent.VK_NUMPAD0));
		assertEquals(VirtualKey.VK_MULTIPLY, VirtualKey.fromKeyEvent(KeyEvent.VK_MULTIPLY));
		assertEquals(VirtualKey.VK_F11, VirtualKey.fromKeyEvent(KeyEvent.VK_F11));
		assertEquals(VirtualKey.VK_CAPITAL, VirtualKey.fromKeyEvent(KeyEvent.VK_CAPS_LOCK));
	}

	@Test
	void keysOutsideTheByteRangeHaveNoWindowsCode() {
		assertEquals(-1, VirtualKey.fromKeyEvent(KeyEvent.VK_ALT_GRAPH)); // 0xFF20
		assertEquals(-1, VirtualKey.fromKeyEvent(KeyEvent.VK_UNDEFINED - 1));
	}
}
