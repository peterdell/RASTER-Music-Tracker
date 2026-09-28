package org.atari.raster.rmt.model;

/** Ported from the C++ class TrackFlag (General.h) - bit flags for a track-usage byte array. */
public final class TrackFlag {

	private TrackFlag() {
	}

	public static final byte TF_NOEMPTY = 1;
	public static final byte TF_USED = 2;
}
