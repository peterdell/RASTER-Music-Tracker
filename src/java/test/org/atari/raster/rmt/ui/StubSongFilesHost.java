package org.atari.raster.rmt.ui;

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

	String importedFileName;
	SongFiles.ImportModChoice nextImportMod;
	SongFiles.ImportTmcChoice nextImportTmc;
	boolean importFinishedOk = true;
	String lastImportInfo;
	String lastFinishedInfo;
	SongFiles.StrippedRmtChoice nextStrippedRmt;
	SongFiles.AsmChoice nextAsm;
	org.atari.raster.rmt.model.AsmFileExporter.RelocatableAsmExportParams nextRelocatableAsm;
	SongFiles.SapChoice nextSap;
	SongFiles.XexChoice nextXex;
	SongFiles.ModuleDescription lastStripped;
	SongFiles.ModuleDescription lastWithSfx;
	org.atari.raster.rmt.model.SapFile lastSapFile;
	String lastSapAuthor;
	String lastSubsongs;
	String lastXexText;
	String lastSpeedInfo;

	@Override
	public void songImported(String fileName) {
		importedFileName = fileName;
	}

	@Override
	public SongFiles.ImportModChoice showImportMod(String info, String radio1, String radio2) {
		calls.add("importMod:" + radio1 + "|" + radio2);
		lastImportInfo = info;
		return nextImportMod;
	}

	@Override
	public SongFiles.ImportTmcChoice showImportTmc(String info) {
		calls.add("importTmc");
		lastImportInfo = info;
		return nextImportTmc;
	}

	@Override
	public boolean showImportFinished(boolean mod, String info) {
		calls.add("importFinished:" + (mod ? "mod" : "tmc"));
		lastFinishedInfo = info;
		return importFinishedOk;
	}

	@Override
	public SongFiles.StrippedRmtChoice showExportStrippedRmt(SongFiles.ModuleDescription stripped, SongFiles.ModuleDescription withSfx, String filename) {
		calls.add("exportStrippedRmt");
		lastStripped = stripped;
		lastWithSfx = withSfx;
		return nextStrippedRmt;
	}

	@Override
	public SongFiles.AsmChoice showExportAsm() {
		calls.add("exportAsm");
		return nextAsm;
	}

	@Override
	public org.atari.raster.rmt.model.AsmFileExporter.RelocatableAsmExportParams showExportRelocatableAsm(SongFiles.ModuleDescription stripped, SongFiles.ModuleDescription withSfx) {
		calls.add("exportRelocatableAsm");
		lastStripped = stripped;
		lastWithSfx = withSfx;
		return nextRelocatableAsm;
	}

	/** True = the SAP and XEX dialogs answer with their untouched fields (what OK without edits returns), instead of {@link #nextSap}/{@link #nextXex}. */
	boolean answerDialogDefaults;

	@Override
	public SongFiles.SapChoice showExportSap(org.atari.raster.rmt.model.SapFile sapFile, String subsongs) {
		calls.add("exportSap:" + sapFile.getType());
		lastSapFile = sapFile;
		lastSapAuthor = sapFile.getAuthor();
		lastSubsongs = subsongs;
		if (answerDialogDefaults) {
			return new SongFiles.SapChoice(sapFile.getAuthor(), sapFile.getName(), sapFile.getDate(), subsongs);
		}
		return nextSap;
	}

	@Override
	public SongFiles.XexChoice showExportXex(String text, String speedInfo) {
		calls.add("exportXex");
		lastXexText = text;
		lastSpeedInfo = speedInfo;
		if (answerDialogDefaults) {
			return new SongFiles.XexChoice(text, true, true, true, 6); // ExportSettings' initial values
		}
		return nextXex;
	}
}
