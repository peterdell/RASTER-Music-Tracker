package org.atari.raster.rmt.ui;

import java.util.ArrayList;
import java.util.List;

import org.atari.raster.rmt.model.Song;

/** A scriptable {@link SongDialogs.Host} for headless tests: answers the queued dialog result, or cancels. */
final class StubSongDialogsHost implements SongDialogs.Host {

	final List<String> calls = new ArrayList<>();
	SongDialogs.InsertCopyChoice nextInsertCopy;
	Song.InstrChangeParams nextInstrChange;
	SongDialogs.TracksOrderChoice nextTracksOrder;
	int nextMaxTrackLength = -1;
	int nextRenumberTracks;
	int nextRenumberInstruments;
	int layoutChangedCount;

	@Override
	public SongDialogs.InsertCopyChoice showInsertCopyOrClone(int lineFrom, int lineTo, int lineInto) {
		calls.add("insertCopy:" + lineFrom + ":" + lineTo + ":" + lineInto);
		return nextInsertCopy;
	}

	@Override
	public Song.InstrChangeParams showInstrumentChange(int instr, int onlyTrack, int onlySongLine) {
		calls.add("instrChange:" + instr + ":" + onlyTrack + ":" + onlySongLine);
		return nextInstrChange;
	}

	@Override
	public SongDialogs.TracksOrderChoice showTracksOrder(String songLineFrom, String songLineTo) {
		calls.add("tracksOrder:" + songLineFrom + ":" + songLineTo);
		return nextTracksOrder;
	}

	@Override
	public int showChangeMaxTrackLength(String info, int maxTrackLength) {
		calls.add("maxTrackLength:" + maxTrackLength + ":" + info.replace('\n', '|'));
		return nextMaxTrackLength;
	}

	@Override
	public int showRenumberTracks() {
		calls.add("renumberTracks");
		return nextRenumberTracks;
	}

	@Override
	public int showRenumberInstruments() {
		calls.add("renumberInstruments");
		return nextRenumberInstruments;
	}

	@Override
	public void songLayoutChanged() {
		layoutChangedCount++;
	}

	/** What the stub "dialog" does to the track: {@code null} cancels (restoring the original), otherwise the effect index applied with these parameters and OK. */
	Integer nextBlockEffect;
	String[] nextBlockEffectParams = { "", "", "" };
	String lastBlockEffectInfo;

	@Override
	public boolean showBlockEffect(org.atari.raster.rmt.model.Track track, org.atari.raster.rmt.model.Track original, int bfro, int bto, int ainstr, boolean all, String info) {
		calls.add("blockEffect:" + bfro + ":" + bto + ":" + ainstr + ":" + all);
		lastBlockEffectInfo = info;
		if (nextBlockEffect == null) {
			track.copyFrom(original); // OnCancel -> OnEffectRestore
			return false;
		}
		org.atari.raster.rmt.model.BlockEffects.perform(track, original, nextBlockEffect, bfro, bto, ainstr, all, nextBlockEffectParams[0], nextBlockEffectParams[1], nextBlockEffectParams[2], tracks, new java.util.Random(1));
		return true;
	}

	/** The tracks the stub's "dialog" applies the effect with. */
	org.atari.raster.rmt.model.Tracks tracks;
}
