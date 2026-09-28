package org.atari.raster.rmt.model;

/** Ported from the C++ struct TBookmark (SongTypes.h). */
public final class Bookmark {

	public int songline;
	public int trackline;
	public int speed;

	public void copyFrom(Bookmark other) {
		songline = other.songline;
		trackline = other.trackline;
		speed = other.speed;
	}
}
