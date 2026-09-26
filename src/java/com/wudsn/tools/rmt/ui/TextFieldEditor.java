package com.wudsn.tools.rmt.ui;

/**
 * Ported from {@code EditText()} (GuiHelpers.cpp) - the shared editor of
 * the two fixed-width text fields (song name, instrument name): insert/
 * overwrite typing, Backspace/Delete/Insert, Home/End/Left/Right, and
 * "done" on Tab/Enter. Characters are derived from the virtual-key code
 * and the Shift state the way C++ does (US layout: {@code ')!@#$%^&*('}
 * over the digits, the OEM keys' pairs), not from the platform's own
 * character translation.
 *
 * <p>C++'s {@code int& cur} in/out parameter becomes the {@link Result}'s
 * {@code cursor}; {@code max} is the field's capacity (the buffer has at
 * least {@code max} characters - C++ decrements it once to address the
 * last one).
 */
public final class TextFieldEditor {

	private TextFieldEditor() {
	}

	/** {@code done} is C++'s "returns 1 if TAB or ENTER was pressed"; {@code cursor} the (possibly moved) cursor position. */
	public record Result(boolean done, int cursor) {
	}

	public static Result editText(int vk, boolean shift, boolean control, char[] txt, int cur, int max) {
		max--;
		if (vk == VirtualKey.VK_BACK) {
			if (cur > 0) {
				cur--;
				for (int j = cur; j <= max - 1; j++) {
					txt[j] = txt[j + 1];
				}
				txt[max] = ' ';
			}
		} else if (vk == VirtualKey.VK_TAB || vk == VirtualKey.VK_RETURN) {
			return new Result(true, cur);
		} else if (vk == VirtualKey.VK_INSERT) {
			for (int j = max - 1; j >= cur; j--) {
				txt[j + 1] = txt[j];
			}
			txt[cur] = ' ';
		} else if (vk == VirtualKey.VK_DELETE) {
			for (int j = cur; j <= max - 1; j++) {
				txt[j] = txt[j + 1];
			}
			txt[max] = ' ';
		} else {
			if (control) {
				return new Result(false, cur);
			}
			char a = 0;
			if (vk >= 'A' && vk <= 'Z') {
				a = (char) (shift ? vk : vk + 32);
			} // letters - uppercase with SHIFT
			else if (vk >= '0' && vk <= '9') {
				a = shift ? ")!@#$%^&*(".charAt(vk - 48) : (char) vk;
			} // numbers - special characters with SHIFT
			else if (vk == ' ') {
				a = ' '; // space
			} else if (vk == VirtualKey.VK_OEM_MINUS) {
				a = shift ? '_' : '-';
			} else if (vk == VirtualKey.VK_OEM_PLUS) {
				a = shift ? '+' : '=';
			} else if (vk == VirtualKey.VK_OEM_4) {
				a = shift ? '{' : '[';
			} else if (vk == VirtualKey.VK_OEM_6) {
				a = shift ? '}' : ']';
			} else if (vk == VirtualKey.VK_OEM_1) {
				a = shift ? ':' : ';';
			} else if (vk == VirtualKey.VK_OEM_7) {
				a = shift ? '"' : '\'';
			} else if (vk == VirtualKey.VK_OEM_COMMA) {
				a = shift ? '<' : ',';
			} else if (vk == VirtualKey.VK_OEM_PERIOD) {
				a = shift ? '>' : '.';
			} else if (vk == VirtualKey.VK_OEM_2) {
				a = shift ? '?' : '/';
			} else if (vk == VirtualKey.VK_OEM_5) {
				a = shift ? '|' : '\\';
			} else if (vk == VirtualKey.VK_RIGHT) {
				if (cur < max) {
					cur++;
				}
			} else if (vk == VirtualKey.VK_LEFT) {
				if (cur > 0) {
					cur--;
				}
			} else if (vk == VirtualKey.VK_HOME) {
				cur = 0;
			} else if (vk == VirtualKey.VK_END) {
				int j;
				for (j = max; j >= 0 && (txt[j] == ' '); j--) {
					// find the last non-space character
				}
				cur = (j < max) ? j + 1 : max;
			}

			if (a > 0) {
				for (int j = max - 1; j >= cur; j--) {
					txt[j + 1] = txt[j];
				}
				txt[cur] = a;
				if (cur < max) {
					cur++;
				}
			}
		}
		return new Result(false, cur);
	}
}
