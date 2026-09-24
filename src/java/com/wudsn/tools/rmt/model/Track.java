package com.wudsn.tools.rmt.model;

/**
 * Ported from the C++ struct TTrack (src/cpp/TrackTypes.h) - a single
 * track's raw line data. Mutable, matching how C++ code accesses these
 * fields directly by index everywhere.
 */
public final class Track {

	public static final int TRACKLEN = 256; // Driver 128

	public int len; // Length of the track
	public int go;
	public final int[] note = new int[TRACKLEN];
	public final int[] instr = new int[TRACKLEN];
	public final int[] volume = new int[TRACKLEN];
	public final int[] speed = new int[TRACKLEN];

	/** Deep-copies another track's data into this one (matches C++'s memcpy(this, other, sizeof(TTrack))). */
	public void copyFrom(Track other) {
		len = other.len;
		go = other.go;
		System.arraycopy(other.note, 0, note, 0, TRACKLEN);
		System.arraycopy(other.instr, 0, instr, 0, TRACKLEN);
		System.arraycopy(other.volume, 0, volume, 0, TRACKLEN);
		System.arraycopy(other.speed, 0, speed, 0, TRACKLEN);
	}
}
