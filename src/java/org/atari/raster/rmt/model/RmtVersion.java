package org.atari.raster.rmt.model;

/**
 * Ported from RmtVersion.h - mirrors {@code Rmt.rc}'s {@code IDS_RMT_VERSION}
 * string resource as a compile-time constant. Must be kept in sync with
 * {@code Rmt.rc}'s {@code IDS_RMT_VERSION} entry and the C++
 * {@code RMT_VERSION_STRING} constant by hand.
 */
public final class RmtVersion {

	private RmtVersion() {
	}

	public static final String RMT_VERSION_STRING = "RASTER Music Tracker 1.35";
}
