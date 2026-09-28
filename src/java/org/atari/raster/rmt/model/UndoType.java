package org.atari.raster.rmt.model;

/**
 * Ported from the C++ enum UndoType (Undo.h) - plain
 * {@code public static final int} constants rather than a Java enum,
 * because {@code Undo.posIsEqual}/{@code changeTrack}/etc. need to accept
 * arbitrary out-of-range values too (C++'s own characterization tests
 * exercise this via {@code static_cast<UndoType>(999)}-style casts - Java's
 * closed enum type has no equivalent, so those methods take a plain
 * {@code int} type code instead of this class's type).
 */
public final class UndoType {

	public static final int UETYPE_NOTEINSTRVOL = 1;
	public static final int UETYPE_NOTEINSTRVOLSPEED = 2;
	public static final int UETYPE_SPEED = 3;
	public static final int UETYPE_LENGO = 4;
	public static final int UETYPE_TRACKDATA = 5;

	public static final int UETYPE_SONGTRACK = 33;
	public static final int UETYPE_SONGGO = 34;

	public static final int UETYPE_SONGDATA = 65;
	public static final int UETYPE_INSTRDATA = 66;
	public static final int UETYPE_TRACKSALL = 67;
	public static final int UETYPE_INSTRSALL = 68;
	public static final int UETYPE_INFODATA = 69;

	private UndoType() {
	}
}
