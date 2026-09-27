package org.atari.raster.rmt.model;

/**
 * Ported from the C++ enum RMTFormatVersion (SongTypes.h). Plain
 * {@code int} constants rather than a Java {@code enum} - {@code DecodeModule}
 * compares a raw byte value read from a file (0-255) against {@code V1}
 * with {@code >}, not just equality, so the full integer range needs to
 * stay comparable.
 */
public final class RmtFormatVersion {

	private RmtFormatVersion() {
	}

	public static final int V1 = 1;
	public static final int V2 = 2;
}
