package org.atari.raster.rmt.ui;

import java.awt.event.KeyEvent;

/**
 * Windows virtual-key codes ({@code VK_*} of {@code winuser.h}), which is
 * what every C++ key handler switches on and what the
 * {@link org.atari.raster.rmt.model.Keyboard2NoteMapping} tables are indexed
 * by. Swing reports {@link KeyEvent} key codes instead; the two agree for
 * letters, digits, function keys, the numeric keypad and most navigation
 * keys but not for Enter, Insert, Delete and the punctuation ("OEM") keys,
 * so {@link #fromKeyEvent} translates once at the panel and everything
 * behind it stays literal to the C++ code.
 *
 * <p>The OEM codes are the US-layout ones, exactly as {@code Rmt.exe} sees
 * them on a US layout; on another layout Windows and Swing each assign
 * those keys differently, which this port doesn't try to reconcile.
 */
public final class VirtualKey {

	private VirtualKey() {
	}

	public static final int VK_BACK = 0x08;
	public static final int VK_TAB = 0x09;
	public static final int VK_RETURN = 0x0D;
	public static final int VK_SHIFT = 0x10;
	public static final int VK_CONTROL = 0x11;
	public static final int VK_MENU = 0x12; // Alt
	public static final int VK_CAPITAL = 0x14;
	public static final int VK_ESCAPE = 0x1B;
	public static final int VK_SPACE = 0x20;
	public static final int VK_PRIOR = 0x21; // Page Up
	public static final int VK_NEXT = 0x22; // Page Down
	public static final int VK_END = 0x23;
	public static final int VK_HOME = 0x24;
	public static final int VK_LEFT = 0x25;
	public static final int VK_UP = 0x26;
	public static final int VK_RIGHT = 0x27;
	public static final int VK_DOWN = 0x28;
	public static final int VK_INSERT = 0x2D;
	public static final int VK_DELETE = 0x2E;
	public static final int VK_0 = 0x30;
	public static final int VK_9 = 0x39;
	public static final int VK_A = 0x41;
	public static final int VK_B = 0x42;
	public static final int VK_C = 0x43;
	public static final int VK_D = 0x44;
	public static final int VK_E = 0x45;
	public static final int VK_F = 0x46;
	public static final int VK_G = 0x47;
	public static final int VK_I = 0x49;
	public static final int VK_M = 0x4D;
	public static final int VK_N = 0x4E;
	public static final int VK_O = 0x4F;
	public static final int VK_P = 0x50;
	public static final int VK_Q = 0x51;
	public static final int VK_U = 0x55;
	public static final int VK_V = 0x56;
	public static final int VK_X = 0x58;
	public static final int VK_Z = 0x5A;
	public static final int VK_NUMPAD0 = 0x60;
	public static final int VK_NUMPAD9 = 0x69;
	public static final int VK_MULTIPLY = 0x6A;
	public static final int VK_ADD = 0x6B;
	public static final int VK_SUBTRACT = 0x6D;
	public static final int VK_DECIMAL = 0x6E;
	public static final int VK_DIVIDE = 0x6F;
	public static final int VK_F1 = 0x70;
	public static final int VK_F2 = 0x71;
	public static final int VK_F3 = 0x72;
	public static final int VK_F4 = 0x73;
	public static final int VK_F11 = 0x7A;
	// The media keys CRmtView::OnKeyDown handles. AWT has no key codes for them (they arrive as VK_UNDEFINED on Windows), so
	// fromKeyEvent never produces them; SongInput handles them for a caller that can.
	public static final int VK_MEDIA_NEXT_TRACK = 0xB0;
	public static final int VK_MEDIA_PREV_TRACK = 0xB1;
	public static final int VK_MEDIA_PLAY_PAUSE = 0xB3;
	public static final int VK_F12 = 0x7B;
	public static final int VK_OEM_1 = 0xBA; // ;:
	public static final int VK_OEM_PLUS = 0xBB; // =+
	public static final int VK_OEM_COMMA = 0xBC;
	public static final int VK_OEM_MINUS = 0xBD;
	public static final int VK_OEM_PERIOD = 0xBE;
	public static final int VK_OEM_2 = 0xBF; // /?
	public static final int VK_OEM_3 = 0xC0; // `~
	public static final int VK_OEM_4 = 0xDB; // [{
	public static final int VK_OEM_5 = 0xDC; // \|
	public static final int VK_OEM_6 = 0xDD; // ]}
	public static final int VK_OEM_7 = 0xDE; // '"

	/** The Windows virtual-key code for a Swing key event's key code, or -1 for a key Windows has no code for in this table. */
	public static int fromKeyEvent(int keyCode) {
		switch (keyCode) {
		case KeyEvent.VK_ENTER:
			return VK_RETURN;
		case KeyEvent.VK_INSERT:
			return VK_INSERT;
		case KeyEvent.VK_DELETE:
			return VK_DELETE;
		case KeyEvent.VK_ALT:
			return VK_MENU;
		case KeyEvent.VK_SEMICOLON:
			return VK_OEM_1;
		case KeyEvent.VK_EQUALS:
			return VK_OEM_PLUS;
		case KeyEvent.VK_COMMA:
			return VK_OEM_COMMA;
		case KeyEvent.VK_MINUS:
			return VK_OEM_MINUS;
		case KeyEvent.VK_PERIOD:
			return VK_OEM_PERIOD;
		case KeyEvent.VK_SLASH:
			return VK_OEM_2;
		case KeyEvent.VK_BACK_QUOTE:
			return VK_OEM_3;
		case KeyEvent.VK_OPEN_BRACKET:
			return VK_OEM_4;
		case KeyEvent.VK_BACK_SLASH:
			return VK_OEM_5;
		case KeyEvent.VK_CLOSE_BRACKET:
			return VK_OEM_6;
		case KeyEvent.VK_QUOTE:
			return VK_OEM_7;
		default:
			// Letters, digits, F1-F12, numpad, space, tab, backspace, escape, arrows, home/end/page, shift/control, caps lock: identical codes
			return keyCode >= 0 && keyCode <= 0xFF ? keyCode : -1;
		}
	}
}
