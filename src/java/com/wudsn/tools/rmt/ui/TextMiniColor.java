package com.wudsn.tools.rmt.ui;

/**
 * Ported from the C++ enum class TextMiniColor (TextColors.h) - the four
 * colors of the 8x8 "mini" font, which lives in the glyph sheet's rows
 * 112-143 (the two 16-pixel bands {@link TextColor} leaves unused).
 */
public enum TextMiniColor {
	GRAY, BLUE, WHITE, YELLOW;

	/** The mini font's first sheet row - the {@code 112 + ...} offset in C++'s {@code TextMiniXY}/{@code NumberMiniXY}. */
	static final int SHEET_Y_BASE = 112;

	/** The color's top pixel row in the glyph sheet - C++'s {@code 112 + GetColorY(TextMiniColor)}, i.e. {@code 112 + ((int)color << 3)}. */
	public int getSheetY() {
		return SHEET_Y_BASE + (ordinal() << 3);
	}
}
