package com.wudsn.tools.rmt.ui;

import java.awt.Color;

/** Ported from the C++ class CRGBColor (General.h) - the few solid RGB colors drawn directly (everything else is a glyph-sheet band, see {@link TextColor}). */
public final class RgbColor {

	private RgbColor() {
	}

	public static final Color MUTE = new Color(120, 160, 240); // Channel is muted
	public static final Color NORMAL = new Color(255, 255, 255); // Volume bar in white
	public static final Color VOLUME_ONLY = new Color(128, 255, 255); // Turquoise for volume only channel
	public static final Color TWO_TONE = new Color(128, 255, 0); // Green for two tone channel
	public static final Color BACKGROUND = new Color(34, 50, 80); // Dark blue - also the glyph sheet's own background
	public static final Color LINES = new Color(149, 194, 240); // Blue gray
	public static final Color BLACK = new Color(0, 0, 0);
	public static final int COL_BLOCK = 56; // Blue portion of analyzer background block
}
