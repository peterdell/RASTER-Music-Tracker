package org.atari.raster.rmt.ui;

/**
 * Ported from the C++ class CRmtScreenLayout (RmtScreenLayout.h) - the
 * fixed-pixel screen layout, in units of the 8x16 character cell. The
 * whole screen is composed at this fixed logical resolution and scaled as
 * one image afterward (see {@code plans/18_JAVA_UI_PORT_PLAN.md}).
 */
public final class RmtScreenLayout {

	private RmtScreenLayout() {
	}

	public static final int CHARACTER_WIDTH = 8;
	public static final int CHARACTER_HEIGHT = 16;

	public static final int TRACKS_X = 2 * CHARACTER_WIDTH;
	public static final int TRACKS_Y = 8 * CHARACTER_HEIGHT + 8;

	public static final int SONG_X = 96 * CHARACTER_WIDTH;
	public static final int SONG_Y = 1 * CHARACTER_HEIGHT;

	// Info area, shown at top-left, 6 lines of text
	public static final int INFO_X = 2 * CHARACTER_WIDTH;
	public static final int INFO_Y = 1 * CHARACTER_HEIGHT;

	public static final int INFO_Y_LINE_1 = INFO_Y;
	public static final int INFO_Y_LINE_2 = INFO_Y + 1 * CHARACTER_HEIGHT;
	public static final int INFO_Y_LINE_3 = INFO_Y + 2 * CHARACTER_HEIGHT;
	public static final int INFO_Y_LINE_4 = INFO_Y + 3 * CHARACTER_HEIGHT;
	public static final int INFO_Y_LINE_5 = INFO_Y + 4 * CHARACTER_HEIGHT;
	public static final int INFO_Y_LINE_6 = INFO_Y + 5 * CHARACTER_HEIGHT;
}
