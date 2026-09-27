package com.wudsn.tools.rmt.ui;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** A scriptable {@link SongFiles.Host} for headless tests: answers the next queued file choice/dialog result, or cancels. */
final class StubSongFilesHost implements SongFiles.Host {

	final List<String> calls = new ArrayList<>();
	/** The next open/save dialog's answer ({@code null} = cancel). */
	SongFiles.FileChoice nextChoice;
	SongFiles.FileNewChoice nextFileNew;
	int nextTracksLoad = -1;
	String lastInitialDir;
	int lastInitialFilterIndex;
	String lastSuggestedFileName;
	int songChangedCount;

	/** Queues an open/save answer: {@code path} with 1-based {@code filterIndex}. */
	void answer(Path path, int filterIndex) {
		nextChoice = new SongFiles.FileChoice(path, filterIndex);
	}

	@Override
	public SongFiles.FileChoice chooseOpenFile(String title, List<SongFiles.FileFilter> filters, String initialDir, int initialFilterIndex) {
		calls.add("open:" + title);
		lastInitialDir = initialDir;
		lastInitialFilterIndex = initialFilterIndex;
		SongFiles.FileChoice c = nextChoice;
		nextChoice = null;
		return c;
	}

	@Override
	public SongFiles.FileChoice chooseSaveFile(String title, List<SongFiles.FileFilter> filters, String initialDir, int initialFilterIndex, String suggestedFileName) {
		calls.add("save:" + title);
		lastInitialDir = initialDir;
		lastInitialFilterIndex = initialFilterIndex;
		lastSuggestedFileName = suggestedFileName;
		SongFiles.FileChoice c = nextChoice;
		nextChoice = null;
		return c;
	}

	@Override
	public SongFiles.FileNewChoice showFileNew() {
		calls.add("fileNew");
		SongFiles.FileNewChoice c = nextFileNew;
		nextFileNew = null;
		return c;
	}

	@Override
	public int showTracksLoad(int trackFrom, int trackNum) {
		calls.add("tracksLoad:" + trackFrom + ":" + trackNum);
		return nextTracksLoad;
	}

	@Override
	public void songChanged() {
		songChangedCount++;
	}
}
