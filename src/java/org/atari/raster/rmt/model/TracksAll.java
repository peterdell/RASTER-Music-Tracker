package org.atari.raster.rmt.model;

/**
 * Ported from the C++ struct TTracksAll (src/cpp/TracksTypes.h) - a full
 * snapshot of every track plus the current max track length, used by
 * {@link Undo} to save/restore all track state at once via
 * {@link Tracks#getTracksAll}/{@link Tracks#setTracksAll}.
 */
public final class TracksAll {

	public int maxTrackLength;
	public final Track[] tracks;

	public TracksAll() {
		tracks = new Track[Tracks.TRACKSNUM];
		for (int i = 0; i < tracks.length; i++) {
			tracks[i] = new Track();
		}
	}

	public void copyFrom(TracksAll other) {
		maxTrackLength = other.maxTrackLength;
		for (int i = 0; i < tracks.length; i++) {
			tracks[i].copyFrom(other.tracks[i]);
		}
	}
}
