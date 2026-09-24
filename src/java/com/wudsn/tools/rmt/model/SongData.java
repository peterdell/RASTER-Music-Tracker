package com.wudsn.tools.rmt.model;

/**
 * Ported from the C++ struct TSong ("due to Undo" - SongTypes.h) - a full
 * snapshot of {@link Song}'s song grid/goto table/bookmark, used by
 * {@link Undo} to record/restore the whole song independently of the live
 * {@link Song} instance.
 */
public final class SongData {

	public static final int SONGLEN = 256;
	public static final int SONGTRACKS = 8;

	public final int[][] song = new int[SONGLEN][SONGTRACKS];
	public final int[] songGo = new int[SONGLEN]; // if >= 0, then GO applies
	public final Bookmark bookmark = new Bookmark();
}
