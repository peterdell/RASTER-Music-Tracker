package com.wudsn.tools.rmt.ui;

/**
 * Ported from the C++ enum class TextColor (TextColors.h). Each value is
 * the index of a 16-pixel-high horizontal band in the glyph sheet
 * ({@code gfx-8x16.bmp}) holding the whole character set pre-rendered in
 * that color - see {@link CanvasXY}. The C++ enum's explicit values are
 * kept as {@link #band} because they are file offsets, not just
 * identifiers: 7 and 8 are deliberately unused here, since those two bands
 * hold the 8x8 "mini" font instead ({@link TextMiniColor}).
 */
public enum TextColor {
	WHITE(0), GRAY(1), YELLOW(2), INVERSE_BLUE(3), INVERSE_WHITE(4), CYAN(5), RED(6), INVERSE_RED(9), EXTRA(10), GREEN(11), DARK_GRAY(12), BLUE(13), TURQUOISE(14);

	/** Ported from C++'s {@code LogicalTextColor::SELECTED} - the highlight color. */
	public static final TextColor SELECTED = INVERSE_RED;
	/** Ported from C++'s {@code LogicalTextColor::SELECTED_PROVE} - the highlight color in prove (jam) mode. */
	public static final TextColor SELECTED_PROVE = INVERSE_BLUE;
	/** Ported from C++'s {@code LogicalTextColor::HOVERED} - the highlight color under the mouse cursor. */
	public static final TextColor HOVERED = INVERSE_WHITE;

	public final int band;

	TextColor(int band) {
		this.band = band;
	}

	/** The band's top pixel row in the glyph sheet - C++'s {@code GetColorY(TextColor)}, i.e. {@code (int)color << 4}. */
	public int getSheetY() {
		return band << 4;
	}
}
