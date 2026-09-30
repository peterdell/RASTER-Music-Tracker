package org.atari.raster.rmt.model;

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
	/** The German keyboard (since 2026-09-30, plan 27): the QWERTY piano by key position, Y and Z exchanged, the OEM keys moved. */
	public static final int QWERTZ = 2;

	/** The layout of a keyboard language ({@link java.util.Locale#getLanguage()}): German QWERTZ, French AZERTY, else QWERTY - the first-start default. */
	public static int forLanguage(String language) {
		if ("de".equals(language)) {
			return QWERTZ;
		}
		if ("fr".equals(language)) {
			return AZERTY;
		}
		return QWERTY;
	}
}
