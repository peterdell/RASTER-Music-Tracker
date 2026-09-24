package com.wudsn.tools.rmt.model;

/**
 * Ported from the C++ enum class KeyboardLayout (General.h). Plain
 * {@code int} constants rather than a Java {@code enum}: Keyboard2NoteMapping
 * Test.NoteKeyReturnsMinusOneForUnknownLayout exercises a synthetic
 * out-of-range value ({@code static_cast<KeyboardLayout>(2)} in C++), which
 * a closed Java enum can't represent.
 */
public final class KeyboardLayout {

	private KeyboardLayout() {
	}

	public static final int QWERTY = 0;
	public static final int AZERTY = 1;
}
