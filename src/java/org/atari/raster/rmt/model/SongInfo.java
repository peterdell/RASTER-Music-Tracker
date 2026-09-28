package org.atari.raster.rmt.model;

/**
 * Ported from the C++ struct TInfo (SongTypes.h) - a snapshot of
 * {@link Song}'s name/speed fields, populated by {@link Song#getSongInfoPars}
 * and used by {@link Undo#changeInfo} to record/restore them independently
 * of the live {@link Song} instance.
 */
public final class SongInfo {

	public static final int SONG_NAME_MAX_LEN = 64;

	public final char[] songName = new char[SONG_NAME_MAX_LEN];
	public int speed;
	public int mainSpeed;
	public int instrumentSpeed;
	public int songNameCursor; // to return the cursor to the appropriate position when undo changes in the song name

	public void copyFrom(SongInfo other) {
		System.arraycopy(other.songName, 0, songName, 0, SONG_NAME_MAX_LEN);
		speed = other.speed;
		mainSpeed = other.mainSpeed;
		instrumentSpeed = other.instrumentSpeed;
		songNameCursor = other.songNameCursor;
	}
}
