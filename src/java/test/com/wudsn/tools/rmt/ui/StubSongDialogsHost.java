package com.wudsn.tools.rmt.ui;

import java.util.ArrayList;
import java.util.List;

import com.wudsn.tools.rmt.model.Song;

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
}
