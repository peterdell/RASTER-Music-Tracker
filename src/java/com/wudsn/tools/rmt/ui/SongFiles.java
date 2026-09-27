package com.wudsn.tools.rmt.ui;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

import com.wudsn.tools.rmt.model.AsmFileExporter;
import com.wudsn.tools.rmt.model.AssemblerFormat;
import com.wudsn.tools.rmt.model.Instruments;
import com.wudsn.tools.rmt.model.MessageAnswer;
import com.wudsn.tools.rmt.model.MessageButtons;
import com.wudsn.tools.rmt.model.ModImporter;
import com.wudsn.tools.rmt.model.PlayMode;
import com.wudsn.tools.rmt.model.PokeyStream;
import com.wudsn.tools.rmt.model.RmtExporter;
import com.wudsn.tools.rmt.model.SapFile;
import com.wudsn.tools.rmt.model.SapFileExporter;
import com.wudsn.tools.rmt.model.Song;
import com.wudsn.tools.rmt.model.SongExporter;
import com.wudsn.tools.rmt.model.SongIOType;
import com.wudsn.tools.rmt.model.TmcImporter;
import com.wudsn.tools.rmt.model.Tracks;
import com.wudsn.tools.rmt.model.UndoType;
import com.wudsn.tools.rmt.model.WaveFileExporter;
import com.wudsn.tools.rmt.model.XexFile;

/**
 * The song/instrument/track file commands - the port of {@code IO_Song.cpp}'s
 * {@code CSong::FileOpen/FileReload/FileSave/FileSaveAs/FileNew/
 * FileInstrumentSave/FileInstrumentLoad/FileTrackSave/FileTrackLoad} and
 * {@code GUI_Song.cpp}'s {@code WarnUnsavedChanges()}. The file dialogs and
 * the two small dialogs (New Module, Tracks loading) stay behind
 * {@link Host}, so the flows are tested headless with a stub; the reading,
 * decoding and writing use the model's already-ported loaders and savers.
 * {@link #fileImport} and {@link #fileExportAs} port {@code FileImport()},
 * {@code FileExportAs()} and {@code ExportV2()} with the dialog wrappers
 * around the importers/exporters ({@code ImportMOD}/{@code ImportTMC},
 * {@code ExportAsStrippedRMT}, {@code ExportAsAsm},
 * {@code ExportAsRelocatableAsmForRmtPlayer}, {@code CSongExporter}'s
 * SAP-R/LZSS/SAP/XEX/WAV); the POKEY stream every stream export needs is
 * generated once per export as {@code CSongContainer} does lazily.
 *
 * <p>{@code std::ifstream}/{@code ofstream} become whole-file reads and
 * writes; TXT files are byte-transparent ISO-8859-1 text, written with the
 * platform's line separator (C++'s text-mode streams).
 */
public final class SongFiles {

	/** One entry of a {@code CFileDialog} filter string: "RMT song file (*.rmt)" and its extensions (with the dot). */
	public record FileFilter(String description, List<String> extensions) {
		public FileFilter(String description, String... extensions) {
			this(description, List.of(extensions));
		}
	}

	/** A file dialog's outcome: the path and the 1-based filter index ({@code m_ofn.nFilterIndex}). */
	public record FileChoice(Path path, int filterIndex) {
	}

	/** {@code CFileNewDlg}'s result. */
	public record FileNewChoice(int maxTrackLength, boolean stereo) {
	}

	/** {@code TExportDescription}: a module built by {@link Song#makeModule} at {@code targetAddrOfModule}, ending before {@code firstByteAfterModule}. */
	public record ModuleDescription(byte[] mem, int targetAddrOfModule, int firstByteAfterModule, byte[] instrumentSavedFlags, byte[] trackSavedFlags) {
		public int length() {
			return firstByteAfterModule - targetAddrOfModule;
		}
	}

	/** {@code CImportModDlg}'s result: {@code firstChoice} = the first track-order radio, then the seven option boxes (the Fourier one is dead in C++). */
	public record ImportModChoice(boolean firstChoice, boolean shiftDownOctave, boolean portamento, boolean fullVolumeRange, boolean volumeIncrease, boolean decreaseInstrument, boolean optimizeLoops, boolean truncateUnusedParts) {
	}

	/** {@code CImportTmcDlg}'s result. */
	public record ImportTmcChoice(boolean useTable, boolean optimizeLoops, boolean truncateUnusedParts) {
	}

	/** {@code CExportStrippedRMTDialog}'s result. */
	public record StrippedRmtChoice(int exportAddr, AssemblerFormat assemblerFormat, boolean sfxSupport, boolean globalVolumeFade, boolean noStartingSongLine) {
	}

	/** {@code CExportAsmDlg}'s result: {@code exportType} 1 = tracks, 2 = whole song; {@code notesIndexOrFreq} 1 = indexes, 2 = frequencies; {@code durationsType} 1 = notes only, 2 = note,duration, 3 = duration,note. */
	public record AsmChoice(int exportType, int notesIndexOrFreq, int durationsType, String prefixForAllAsmLabels) {
	}

	/** {@code CSAPFileExportDialog}'s result. */
	public record SapChoice(String author, String name, String date, String subsongs) {
	}

	/** {@code CExpMSXDlg}'s result: the (at most 5x40) text, the rasterbar options and the region setting. */
	public record XexChoice(String text, boolean rasterbar, boolean shuffle, boolean regionAuto, int color) {
	}

	/** The window services the flows need. */
	public interface Host {
		/** {@code CFileDialog(TRUE, ...)}: {@code null} if cancelled. */
		FileChoice chooseOpenFile(String title, List<FileFilter> filters, String initialDir, int initialFilterIndex);

		/** {@code CFileDialog(FALSE, ..., OFN_OVERWRITEPROMPT)}: {@code null} if cancelled; the extension of the chosen filter is ensured. */
		FileChoice chooseSaveFile(String title, List<FileFilter> filters, String initialDir, int initialFilterIndex, String suggestedFileName);

		/** {@code CFileNewDlg}: {@code null} if cancelled. */
		FileNewChoice showFileNew();

		/** {@code CTracksLoadDlg}: 0 = load to {@code trackFrom}.., 1 = to the tracks' original places, -1 if cancelled. */
		int showTracksLoad(int trackFrom, int trackNum);

		/** {@code SetRMTTitle()} - and the mono/stereo layout may have changed too. */
		void songChanged();

		/** {@code FileImport()}'s {@code SetWindowText("Imported " + fn)}. */
		void songImported(String fileName);

		/** {@code CImportModDlg}: {@code null} if cancelled. */
		ImportModChoice showImportMod(String info, String radio1, String radio2);

		/** {@code CImportTmcDlg}: {@code null} if cancelled. */
		ImportTmcChoice showImportTmc(String info);

		/** {@code CImportModFinishedDlg}/{@code CImportTmcFinishedDlg}: OK pressed (only possible with the "I understand" box checked, which {@link ExportSettings} remembers). */
		boolean showImportFinished(boolean mod, String info);

		/** {@code CExportStrippedRMTDialog}: {@code null} if cancelled. */
		StrippedRmtChoice showExportStrippedRmt(ModuleDescription stripped, ModuleDescription withSfx, String filename);

		/** {@code CExportAsmDlg}: {@code null} if cancelled. */
		AsmChoice showExportAsm();

		/** {@code CExportRelocatableAsmForRmtPlayer}: {@code null} if cancelled. */
		AsmFileExporter.RelocatableAsmExportParams showExportRelocatableAsm(ModuleDescription stripped, ModuleDescription withSfx);

		/** {@code CSAPFileExportDialog}: {@code null} if cancelled. */
		SapChoice showExportSap(SapFile sapFile, String subsongs);

		/** {@code CExpMSXDlg}: {@code null} if cancelled. */
		XexChoice showExportXex(String text, String speedInfo);
	}

	/** {@code FILE_LOADSAVE}: RMT = 1, TXT = 2, RMW = 3. */
	public static final List<FileFilter> SONG_FILTERS = List.of(new FileFilter("RMT song file", ".rmt"), new FileFilter("TXT song file", ".txt"), new FileFilter("RMW song work file", ".rmw"));
	public static final int FILTER_RMT = 1;
	public static final int FILTER_TXT = 2;
	public static final int FILTER_RMW = 3;
	/** {@code FILE_IMPORT}: MOD = 1, TMC = 2. */
	public static final List<FileFilter> IMPORT_FILTERS = List.of(new FileFilter("ProTracker Modules", ".mod"), new FileFilter("TMC Song Files", ".tmc", ".tm8"));
	public static final int FILTER_MOD = 1;
	public static final int FILTER_TMC = 2;
	/** {@code FILE_EXPORT}: the eight formats, in {@link #EXPORT_IO_TYPES}' order. */
	public static final List<FileFilter> EXPORT_FILTERS = List.of(new FileFilter("RMT stripped song file", ".rmt"), new FileFilter("ASM simple notation source", ".asm"), new FileFilter("SAP-R data stream", ".sapr"), new FileFilter("Compressed SAP-R data stream", ".lzss"),
			new FileFilter("SAP file + LZSS driver", ".sap"), new FileFilter("XEX Atari executable + LZSS driver", ".xex"), new FileFilter("Relocatable ASM for RMTPlayer", ".asm"), new FileFilter("WAV audio file", ".wav"));
	static final SongIOType[] EXPORT_IO_TYPES = { SongIOType.RMTSTRIPPED, SongIOType.ASM, SongIOType.SAPR, SongIOType.LZSS, SongIOType.LZSS_SAP, SongIOType.LZSS_XEX, SongIOType.ASM_RMTPLAYER, SongIOType.WAV };
	public static final List<FileFilter> INSTRUMENT_FILTERS = List.of(new FileFilter("RMT instrument file", ".rti"));
	public static final List<FileFilter> TRACK_FILTERS = List.of(new FileFilter("TXT track file", ".txt"));

	/** C++ streams TXT as raw bytes; ISO-8859-1 maps them 1:1 onto chars. */
	static final Charset TEXT_CHARSET = StandardCharsets.ISO_8859_1;

	private final RmtSession session;
	private final Host host;

	public SongFiles(RmtSession session, Host host) {
		this.session = session;
		this.host = host;
	}

	/** {@code FileDialogParameters::EnsureFileExtension()}: appends the filter's extension unless the name already ends with it (case-insensitively). */
	public static Path ensureFileExtension(Path path, List<FileFilter> filters, int filterIndex) {
		if (filterIndex < 1 || filterIndex > filters.size()) {
			return path;
		}
		String ext = filters.get(filterIndex - 1).extensions().get(0);
		String name = path.getFileName().toString();
		if (name.toLowerCase(Locale.ROOT).endsWith(ext)) {
			return path;
		}
		return path.resolveSibling(name + ext);
	}

	private static boolean isValidFilterIndex(List<FileFilter> filters, int index) {
		return index >= 1 && index <= filters.size();
	}

	/** {@code GetFilePath()}: the folder part, "" for none. */
	private static String folderOf(Path path) {
		Path parent = path.toAbsolutePath().getParent();
		return parent == null ? "" : parent.toString();
	}

	private String songsInitialDir() {
		RmtOptions o = session.options;
		return !o.lastSongsPath.isEmpty() ? o.lastSongsPath : o.defaultSongsPath;
	}

	private String instrumentsInitialDir() {
		RmtOptions o = session.options;
		return !o.lastInstrumentsPath.isEmpty() ? o.lastInstrumentsPath : o.defaultInstrumentsPath;
	}

	private String tracksInitialDir() {
		RmtOptions o = session.options;
		return !o.lastTracksPath.isEmpty() ? o.lastTracksPath : o.defaultTracksPath;
	}

	private static int filterIndexOf(SongIOType ioType) {
		return switch (ioType) {
		case RMT -> FILTER_RMT;
		case TXT -> FILTER_TXT;
		case RMW -> FILTER_RMW;
		default -> 0;
		};
	}

	private static int filterIndexOfExtension(String filename) {
		String ext = filename.length() >= 4 ? filename.substring(filename.length() - 4).toLowerCase(Locale.ROOT) : "";
		return switch (ext) {
		case ".rmt" -> FILTER_RMT;
		case ".txt" -> FILTER_TXT;
		case ".rmw" -> FILTER_RMW;
		default -> 0;
		};
	}

	/** {@code ClearSong(g_tracks4_8)} as the UI sees it: the model's clear plus {@code g_changes = 0}, which C++'s {@code ClearSong} does itself. */
	private void clearSong() {
		clearSong(session.tracks4_8);
	}

	private void clearSong(int numOfTracks) {
		session.setTracks4_8(session.song.clearSong(numOfTracks, session.undo));
		session.uiState.changes = false;
		session.exportSettings.resetOnClearSong();
	}

	private void stop() {
		session.song.stop(session.undo);
	}

	/** {@code CSong::WarnUnsavedChanges()}: returns {@code true} upon cancellation (or a failed save). */
	public boolean warnUnsavedChanges() {
		if (!session.uiState.changes) {
			return false;
		}
		MessageAnswer r = session.messages.sendQuestionMessage("Current song has been changed", "Save current changes?", MessageButtons.YES_NO_CANCEL);
		if (r == MessageAnswer.CANCEL) {
			return true;
		}
		if (r == MessageAnswer.YES) {
			fileSave();
			host.songChanged();
			if (session.uiState.changes) {
				return true; // failed to save or canceled
			}
		}
		return false;
	}

	/** {@code CSong::FileCanBeReloaded()}. */
	public boolean fileCanBeReloaded() {
		return !session.song.getFilename().isEmpty();
	}

	/** {@code CSong::FileReload()}. */
	public void fileReload() {
		if (!fileCanBeReloaded()) {
			return;
		}
		stop();
		MessageAnswer answer = session.messages.sendQuestionMessage("Reload", "Discard all changes since your last save?\n\nWarning: Undo operation won't be possible!!!", MessageButtons.YES_NO_CANCEL);
		if (answer == MessageAnswer.YES) {
			fileOpen(Path.of(session.song.getFilename()), false); // Without warning for unsaved changes
		}
	}

	/**
	 * {@code CSong::FileOpen(filename, warnOfUnsavedChanges)}: with a
	 * {@code filename} (the command line, a reload) the format follows its
	 * extension; without one the dialog asks. Returns whether a song was
	 * loaded.
	 */
	public boolean fileOpen(Path filename, boolean warnOfUnsavedChanges) {
		Song song = session.song;
		stop();
		if (warnOfUnsavedChanges && warnUnsavedChanges()) {
			return false;
		}

		Path fileToLoad;
		int filterIndex;
		if (filename != null) {
			fileToLoad = filename;
			filterIndex = filterIndexOfExtension(filename.getFileName().toString());
		} else {
			FileChoice choice = host.chooseOpenFile("Load song file", SONG_FILTERS, songsInitialDir(), filterIndexOf(song.getIOType()));
			if (choice == null) {
				return false; // If not ok, it's over
			}
			fileToLoad = choice.path();
			filterIndex = choice.filterIndex();
		}
		if (filterIndex == 0) {
			return false; // Continue only when a file was selected in the FileDialog or specified at startup
		}
		session.options.lastSongsPath = folderOf(fileToLoad);
		if (!isValidFilterIndex(SONG_FILTERS, filterIndex)) {
			return false;
		}

		byte[] data;
		try {
			data = Files.readAllBytes(fileToLoad); // Open the input file in binary format (even the text file)
		} catch (IOException ex) {
			session.messages.sendErrorMessage("Open error", "Can't open this file: " + fileToLoad);
			return false;
		}

		clearSong(); // Deletes the current song

		boolean loadedOk;
		SongIOType ioType;
		switch (filterIndex) {
		case FILTER_RMT -> {
			Song.LoadRmtResult r = song.loadRMT(data);
			loadedOk = r.success();
			if (loadedOk) {
				session.setTracks4_8(r.tracks4_8());
				session.exportSettings.rmtStrippedAddress = r.moduleAddress(); // The main block of the module is OK => take its boot address
			}
			ioType = SongIOType.RMT;
		}
		case FILTER_TXT -> {
			session.setTracks4_8(song.loadTxt(new String(data, TEXT_CHARSET), session.undo).tracks4_8());
			loadedOk = true; // LoadTxt "does not mean the resultant data is valid"
			ioType = SongIOType.TXT;
		}
		default -> {
			Song.LoadRmwResult r = song.loadRMW(data, session.undo);
			loadedOk = r.success();
			if (loadedOk) {
				session.setTracks4_8(r.tracks4_8());
			}
			ioType = SongIOType.RMW;
		}
		}

		if (!loadedOk) {
			clearSong(); // Something in the Load... function failed - erases everything
			host.songChanged();
			return false;
		}

		song.setLoadedFile(fileToLoad.toString(), ioType); // m_filename, m_ioType, m_speed = m_mainSpeed
		host.songChanged(); // Window name
		session.channelControl.setAllChannelsOn();
		return true;
	}

	/** {@code CSong::FileSave()}. */
	public void fileSave() {
		Song song = session.song;
		stop();

		// If the song has no filename, prompt the "save as" dialog first
		if (song.getFilename().isEmpty() || song.getIOType() == SongIOType.NONE) {
			fileSaveAs();
			return;
		}

		// If the RMT module hasn't met the conditions required to be valid, it won't be saved/overwritten
		if (song.getIOType() == SongIOType.RMT && !song.testBeforeFileSave(session.tracks4_8, session.messages)) {
			session.messages.sendWarningMessage("Warning", "Warning!\nNo data has been saved!");
			host.songChanged();
			return;
		}

		byte[] content = switch (song.getIOType()) {
		case RMT -> saveRmtBytes();
		case TXT -> textBytes(song.saveTxt(session.tracks4_8));
		case RMW -> {
			// Remembers the current octave and volume for the active instrument (for saving to RMW)
			session.instruments.memorizeOctaveAndVolume(song.getActiveInstr(), song.getOctave(), song.getVolume(), session.options.keyboardRememberOctavesAndVolumes);
			yield song.saveRMW(session.tracks4_8);
		}
		default -> null;
		};

		Path path = Path.of(song.getFilename());
		boolean saveResult = content != null;
		if (saveResult) {
			try {
				Files.write(path, content);
			} catch (IOException ex) {
				session.messages.sendErrorMessage("Write error", "Can't create this file");
				return;
			}
		}

		if (!saveResult) { // failed to save
			try {
				Files.deleteIfExists(path);
			} catch (IOException ignored) {
				// DeleteFile's failure is silent in C++ too
			}
			session.messages.sendWarningMessage("Save aborted", "RMT save aborted.\nFile was deleted, beware of data loss!");
		} else { // saved successfully
			session.uiState.changes = false; // changes have been saved
		}
		host.songChanged();
	}

	/** {@code ExportV2(*this, out, SongIOType::RMT)}: the module built at $4000, then {@code ExportAsRMT}; {@code null} if the module can't be built. */
	private byte[] saveRmtBytes() {
		byte[] mem = new byte[65536];
		int targetAddrOfModule = 0x4000; // Standard RMT modules are set to start @ $4000
		byte[] instrumentSavedFlags = new byte[Instruments.INSTRSNUM];
		byte[] trackSavedFlags = new byte[Tracks.TRACKSNUM];
		int maxAddr = session.song.makeModule(mem, targetAddrOfModule, SongIOType.RMT, instrumentSavedFlags, trackSavedFlags, session.tracks4_8);
		if (maxAddr < 0) {
			return null; // If the module could not be created, the export process is immediately aborted
		}
		return RmtExporter.exportAsRMT(session.song, session.instruments, mem, targetAddrOfModule, maxAddr, instrumentSavedFlags);
	}

	/** A text file as C++'s text-mode {@code ofstream} writes it: the platform's line ends, bytes 1:1. */
	static byte[] textBytes(String text) {
		return text.replace("\n", System.lineSeparator()).getBytes(TEXT_CHARSET);
	}

	/** {@code CSong::FileSaveAs()}. */
	public void fileSaveAs() {
		Song song = session.song;
		stop();

		// Specifies the name of the file according to the last saved one
		String suggested = "";
		if (!song.getFilename().isEmpty()) {
			String f = song.getFilename();
			int pos = Math.max(f.lastIndexOf('\\'), f.lastIndexOf('/'));
			if (pos >= 0) {
				suggested = f.substring(pos + 1);
			}
		}

		FileChoice choice = host.chooseSaveFile("Save song as...", SONG_FILTERS, songsInitialDir(), filterIndexOf(song.getIOType()), suggested);
		if (choice == null) {
			return; // if not ok, nothing will be saved
		}
		int filterIndex = choice.filterIndex();
		if (!isValidFilterIndex(SONG_FILTERS, filterIndex)) {
			return;
		}
		Path path = ensureFileExtension(choice.path(), SONG_FILTERS, filterIndex);
		session.options.lastSongsPath = folderOf(path);
		SongIOType ioType = switch (filterIndex) {
		case FILTER_RMT -> SongIOType.RMT;
		case FILTER_TXT -> SongIOType.TXT;
		default -> SongIOType.RMW;
		};
		song.setLoadedFile(path.toString(), ioType);
		// If everything went well, the file will now be saved
		fileSave();
	}

	/** {@code CSong::FileNew()}. */
	public void fileNew() {
		stop();
		if (warnUnsavedChanges()) {
			return; // If the last changes were not saved, nothing will be created
		}
		FileNewChoice choice = host.showFileNew();
		if (choice == null) {
			return;
		}
		session.setTracks4_8(session.song.fileNewApply(choice.maxTrackLength(), choice.stereo() ? 8 : 4, session.undo));
		session.uiState.changes = false; // ClearSong
		session.exportSettings.resetOnClearSong();
		host.songChanged();
		session.channelControl.setAllChannelsOn(); // All channels ON (unmute all)
	}

	/** {@code CSong::FileInstrumentSave()}: the active instrument as an {@code .rti} file. */
	public void fileInstrumentSave() {
		stop();
		FileChoice choice = host.chooseSaveFile("Save RMT instrument file", INSTRUMENT_FILTERS, instrumentsInitialDir(), 1, "");
		if (choice == null) {
			return; // If it's not ok, nothing is saved
		}
		Path path = ensureFileExtension(choice.path(), INSTRUMENT_FILTERS, 1);
		session.options.lastInstrumentsPath = folderOf(path);
		byte[] data = session.instruments.saveInstrumentRti(session.song.getActiveInstr(), session.song.isStereo(session.tracks4_8));
		try {
			Files.write(path, data);
		} catch (IOException ex) {
			session.messages.sendErrorMessage("Write error", "Can't create the instrument file: " + path);
		}
	}

	/** {@code CSong::FileInstrumentLoad()}: an {@code .rti} file into the active instrument (one undo step). */
	public void fileInstrumentLoad() {
		stop();
		FileChoice choice = host.chooseOpenFile("Load RMT instrument file", INSTRUMENT_FILTERS, instrumentsInitialDir(), 1);
		if (choice == null) {
			return; // If it's not ok, nothing will be loaded
		}
		int activeInstr = session.song.getActiveInstr();
		session.undo.changeInstrument(activeInstr, 0, UndoType.UETYPE_INSTRDATA, 1);
		session.options.lastInstrumentsPath = folderOf(choice.path());
		byte[] data;
		try {
			data = Files.readAllBytes(choice.path());
		} catch (IOException ex) {
			session.messages.sendErrorMessage("Open error", "Can't open this file: " + choice.path());
			return;
		}
		if (!session.instruments.loadInstrumentRti(activeInstr, data, session.song.isStereo(session.tracks4_8))) {
			session.messages.sendErrorMessage("Data error", "Failed to load RTI format (standard version 0)");
		}
	}

	/** {@code CSong::FileTrackSave()}: the active track as a TXT file. */
	public void fileTrackSave() {
		int track = session.song.songGetActiveTrack();
		if (!session.tracks.isValidTrack(track)) {
			return;
		}
		stop();
		FileChoice choice = host.chooseSaveFile("Save TXT track file", TRACK_FILTERS, tracksInitialDir(), 1, "");
		if (choice == null) {
			return; // if not ok, nothing will be saved
		}
		Path path = ensureFileExtension(choice.path(), TRACK_FILTERS, 1);
		session.options.lastTracksPath = folderOf(path);
		try {
			Files.write(path, textBytes(session.tracks.saveTrackTxt(track)));
		} catch (IOException ex) {
			session.messages.sendErrorMessage("Write error", "Can't create this file: " + path);
		}
	}

	/** {@code CSong::FileTrackLoad()}: one or more {@code [TRACK]} sections from a TXT file, from the active track on or into their original places. */
	public void fileTrackLoad() {
		int track = session.song.songGetActiveTrack();
		if (!session.tracks.isValidTrack(track)) {
			return;
		}
		stop();
		FileChoice choice = host.chooseOpenFile("Load TXT track file", TRACK_FILTERS, tracksInitialDir(), 1);
		if (choice == null) {
			return; // If not ok, nothing will be loaded
		}
		session.undo.changeTrack(0, 0, UndoType.UETYPE_TRACKSALL, 1);
		session.options.lastTracksPath = folderOf(choice.path());
		String text;
		try {
			text = new String(Files.readAllBytes(choice.path()), TEXT_CHARSET);
		} catch (IOException ex) {
			session.messages.sendErrorMessage("Open error", "Can't open this file: " + choice.path());
			return;
		}

		int nt = Tracks.countTrackSectionsTxt(text); // number of tracks
		int type = 0; // type when loading multiple tracks
		if (nt == 0) {
			session.messages.sendErrorMessage("Data error", "Sorry, this file doesn't contain any track in TXT format");
			return;
		} else if (nt > 1) {
			type = host.showTracksLoad(track, nt);
			if (type < 0) {
				return;
			}
		}

		Tracks.LoadTracksTxtResult result = session.tracks.loadTracksTxt(text, track, type == 1);
		if (result.maximumReached()) {
			session.messages.sendErrorMessage("Error", "Track's maximum number reached.\nLoading aborted.");
		}
		int nr = result.loaded();
		if (nr == 0 || nr > 1) { // if it has not found any or more than 1
			session.messages.sendInformationMessage("Track(s) loading finished.", nr + " track(s) loaded.");
		}
	}

	// ---- FileImport (IO_Song.cpp) + ImportMOD/ImportTMC (IO_Importer.cpp) ----

	/** {@code CSong::FileImport()}: a ProTracker module or a TMC song into a new song. */
	public void fileImport() {
		ExportSettings es = session.exportSettings;
		stop();
		if (warnUnsavedChanges()) {
			return;
		}
		FileChoice choice = host.chooseOpenFile("Import Song File", IMPORT_FILTERS, songsInitialDir(), es.lastImportTypeIndex); // Restore the last imported file type
		if (choice == null) {
			return; // If not ok, nothing will be imported
		}
		Path fn = choice.path();
		session.options.lastSongsPath = folderOf(fn); // direct way
		int filterIndex = choice.filterIndex();
		if (!isValidFilterIndex(IMPORT_FILTERS, filterIndex)) {
			return;
		}
		es.lastImportTypeIndex = filterIndex;

		byte[] data;
		try {
			data = Files.readAllBytes(fn);
		} catch (IOException ex) {
			session.messages.sendErrorMessage("Open error", "Can't open this file: " + fn);
			return;
		}

		boolean importResult = filterIndex == FILTER_MOD ? importMOD(data) : importTMC(data);
		session.song.setLoadedFile("", session.song.getIOType()); // m_filename = "" (and m_speed = m_mainSpeed, which C++ does only on success - harmless on a cleared song)

		if (!importResult) { // Import failed?
			clearSong(); // Delete everything
		} else {
			host.songImported(fn.toString()); // window name "Imported ..."
		}
		session.channelControl.setAllChannelsOn(); // All channels ON (unmute all)
		session.reInitSound(); // Initialise RMT routine
	}

	/** {@code CSong::ImportMOD()}: returns whether the import ran (an import aborted in the final dialog still counts as run, as in C++). */
	boolean importMOD(byte[] data) {
		int originalTracks4_8 = session.tracks4_8; // keeps the original value for Abort
		ModImporter.ParseHeaderResult header = ModImporter.parseHeader(data, session.song, session.tracks, session.undo); // ClearSong(8)
		session.setTracks4_8(8);
		session.uiState.changes = false;
		session.exportSettings.resetOnClearSong();
		if (!header.ok()) {
			switch (header.errorCode()) {
			case 1 -> session.messages.sendErrorMessage("Error", "Bad file format.");
			case 2 -> session.messages.sendErrorMessage("Error", "There isn't ProTracker identification header bytes.\nAllowed headers are \"M.K.\" or from \"4CHN\" to \"8CHN\",\nbut there is \"" + modHeaderBytes(data) + "\".");
			default -> session.messages.sendErrorMessage("Error", "Bad file.");
			}
			return false;
		}

		int[] trackOrder = { 0, 1, 2, 3, 4, 5, 6, 7 }; // track layout
		int rmttype = 0;
		String info = header.chnls() + " channels " + header.modSamples() + " samples ProTracker module detected.\n(Header bytes \"" + modHeaderBytes(data) + "\".)";
		ImportModChoice dlg;
		if (header.chnls() == 4) { // 4 channels module
			dlg = host.showImportMod(info, "RMT4 with 1,2,3,4 tracks order", "RMT8 with 1,4 / 2,3 tracks order");
			if (dlg != null) {
				if (dlg.firstChoice()) {
					rmttype = 4;
				} else {
					rmttype = 8;
					trackOrder[0] = 0;
					trackOrder[1] = 4;
					trackOrder[2] = 5;
					trackOrder[3] = 1;
				}
			}
		} else { // 5-8 channels module
			dlg = host.showImportMod(info, "RMT8 with 1,4,5,8 / 2,3,6,7 tracks order", "RMT8 with 1,2,3,4 / 5,6,7,8 tracks order");
			if (dlg != null) {
				rmttype = 8;
				if (dlg.firstChoice()) {
					trackOrder = new int[] { 0, 4, 5, 1, 2, 6, 7, 3 };
				}
			}
		}
		if (dlg == null || (rmttype != 4 && rmttype != 8)) {
			return false; // did not select the back option (cancel in the dialog)
		}

		ModImporter.ApplyResult result = ModImporter.apply(header, rmttype, trackOrder, dlg.shiftDownOctave(), dlg.portamento(), dlg.fullVolumeRange(), dlg.volumeIncrease(), dlg.decreaseInstrument(), dlg.optimizeLoops(), dlg.truncateUnusedParts(), session.song, session.tracks,
				session.instruments, session.undo);
		session.setTracks4_8(result.tracks4_8());

		// FINAL DIALOGUE AFTER IMPORT
		String finished = result.destNum() + " tracks, " + result.nonEmptySamples() + " instruments, " + result.songLines() + " songlines";
		if (dlg.optimizeLoops()) {
			finished += "\nOptimization: Loops in " + result.optiTracks() + " tracks (" + result.optiBeats() + " beats/lines)";
		}
		if (dlg.truncateUnusedParts()) {
			finished += "\nOptimization: Cleared " + result.clearedTracks() + ", truncated " + result.truncatedTracks() + " tracks (" + result.truncatedBeats() + " beats/lines)";
		}
		if (!host.showImportFinished(true, finished)) {
			clearSong(originalTracks4_8); // did not give Ok, so it deletes - returns the original value
			session.messages.sendInformationMessage("Import...", "Module import aborted.");
		}
		return true;
	}

	/** The four identification bytes at offset 1080 ({@code header.head + 1080}), as C++ prints them. */
	private static String modHeaderBytes(byte[] data) {
		StringBuilder sb = new StringBuilder();
		for (int i = 1080; i < 1084 && i < data.length; i++) {
			if (data[i] == 0) {
				break;
			}
			sb.append((char) (data[i] & 0xFF));
		}
		return sb.toString();
	}

	/** {@code CSong::ImportTMC()}. */
	boolean importTMC(byte[] data) {
		int originalTracks4_8 = session.tracks4_8;
		TmcImporter.ParseHeaderResult header = TmcImporter.parseHeader(data, session.song, session.tracks, session.undo); // ClearSong(8)
		session.setTracks4_8(8);
		session.uiState.changes = false;
		session.exportSettings.resetOnClearSong();
		if (!header.ok()) {
			session.messages.sendErrorMessage("Open error", "Corrupted TMC file or unsupported format version.");
			return false;
		}

		ImportTmcChoice dlg = host.showImportTmc("TMC module: " + session.song.getName());
		if (dlg == null) {
			return false;
		}

		TmcImporter.ApplyResult result = TmcImporter.apply(header, dlg.useTable(), dlg.optimizeLoops(), dlg.truncateUnusedParts(), session.song, session.tracks, session.instruments, session.undo);
		session.setTracks4_8(result.tracks4_8());

		// FINAL DIALOGUE AFTER IMPORT
		String finished = result.numOfTracks() + " tracks, " + result.nonEmptyInstruments() + " instruments, " + result.songLines() + " songlines";
		if (dlg.optimizeLoops()) {
			finished += "\nOptimization: Loops in " + result.optiTracks() + " tracks (" + result.optiBeats() + " beats/lines)";
		}
		if (dlg.truncateUnusedParts()) {
			finished += "\nOptimization: Cleared " + result.clearedTracks() + ", truncated " + result.truncatedTracks() + " tracks (" + result.truncatedBeats() + " beats/lines)";
		}
		if (!host.showImportFinished(false, finished)) {
			clearSong(originalTracks4_8);
			session.messages.sendInformationMessage("Import...", "Module import aborted.");
		}
		return true;
	}

	// ---- FileExportAs (IO_Song.cpp) + ExportV2 (SongExportV2.cpp) ----

	private static int exportFilterIndexOf(SongIOType ioType) {
		for (int i = 0; i < EXPORT_IO_TYPES.length; i++) {
			if (EXPORT_IO_TYPES[i] == ioType) {
				return i + 1;
			}
		}
		return 0;
	}

	/** {@code CSong::FileExportAs()}: validation, the format/file dialog, then {@link #exportV2}; a failed or cancelled export deletes the (already created) file, as C++ does. */
	public void fileExportAs() {
		Song song = session.song;
		stop();

		// Verify the integrity of the .rmt module to save first, so it won't be saved if it's not meeting the conditions for it
		if (!song.testBeforeFileSave(session.tracks4_8, session.messages)) {
			session.messages.sendWarningMessage("Warning", "Warning!\nNo data has been saved!");
			return;
		}

		FileChoice choice = host.chooseSaveFile("Export song as...", EXPORT_FILTERS, songsInitialDir(), exportFilterIndexOf(song.getLastExportIOType()), "");
		if (choice == null) {
			return; // If not ok, nothing will be saved
		}
		int filterIndex = choice.filterIndex();
		if (!isValidFilterIndex(EXPORT_FILTERS, filterIndex)) {
			return;
		}
		Path fn = ensureFileExtension(choice.path(), EXPORT_FILTERS, filterIndex);
		session.options.lastSongsPath = folderOf(fn);

		// Try and create the output file (C++ opens the ofstream here, before the format's own dialog)
		try {
			Files.write(fn, new byte[0]);
		} catch (IOException ex) {
			session.messages.sendErrorMessage("Export error", "Can't create this file: " + fn);
			return;
		}

		song.setLastExportIOType(EXPORT_IO_TYPES[filterIndex - 1]);
		boolean exportResult;
		try {
			exportResult = exportV2(song.getLastExportIOType(), fn);
		} catch (IOException ex) {
			exportResult = false;
		}
		if (!exportResult) {
			try {
				Files.deleteIfExists(fn);
			} catch (IOException ignored) {
				// DeleteFile's failure is silent in C++ too
			}
			session.messages.sendWarningMessage("Export aborted", "Incomplete export file '" + fn + "' was deleted.");
		}
	}

	/** {@code MakeModule} into a fresh 64K image at $4000 for the given {@code iotype}; {@code null} if it fails. */
	private ModuleDescription makeModule(SongIOType iotype) {
		byte[] mem = new byte[65536];
		byte[] instrumentSavedFlags = new byte[Instruments.INSTRSNUM];
		byte[] trackSavedFlags = new byte[Tracks.TRACKSNUM];
		int targetAddrOfModule = 0x4000; // Standard RMT modules are set to start @ $4000
		int maxAddr = session.song.makeModule(mem, targetAddrOfModule, iotype, instrumentSavedFlags, trackSavedFlags, session.tracks4_8);
		if (maxAddr < 0) {
			return null;
		}
		return new ModuleDescription(mem, targetAddrOfModule, maxAddr, instrumentSavedFlags, trackSavedFlags);
	}

	/** {@code CSongContainer::GetPokeyStream()}: the whole song recorded once from its start. */
	private PokeyStream generatePokeyStream() {
		PokeyStream pokeyStream = new PokeyStream();
		session.song.dumpSongToPokeyStream(pokeyStream, PlayMode.PLAY_SONG, 0, 0, session.tracks4_8, session.atariTrackerDriver, session.channelControl, session.clipboard, session.undo);
		return pokeyStream;
	}

	/** {@code CSong::ExportV2()}: the module is built first (aborting the export if that fails), then the format's dialog and writer run. Returns whether the file was written. */
	boolean exportV2(SongIOType iotype, Path fn) throws IOException {
		Song song = session.song;
		ExportSettings es = session.exportSettings;
		ModuleDescription exportDesc = makeModule(iotype);
		if (exportDesc == null) {
			return false; // If the module could not be created, the export process is immediately aborted
		}
		switch (iotype) {
		case RMTSTRIPPED -> {
			// CRmtExporter::ExportAsStrippedRMT
			ModuleDescription withSfx = makeModule(SongIOType.RMT);
			if (withSfx == null) {
				return false;
			}
			StrippedRmtChoice dlg = host.showExportStrippedRmt(exportDesc, withSfx, fn.toString());
			if (dlg == null) {
				return false;
			}
			es.rmtStrippedAddress = dlg.exportAddr();
			es.rmtStrippedSfx = dlg.sfxSupport();
			es.rmtStrippedGlobalVolumeFade = dlg.globalVolumeFade();
			es.rmtStrippedNoStartingSongLine = dlg.noStartingSongLine();
			es.asmFormat = dlg.assemblerFormat();
			byte[] out = RmtExporter.exportAsStrippedRMTApply(song, es.rmtStrippedAddress, es.rmtStrippedSfx, session.tracks4_8);
			if (out == null) {
				return false;
			}
			Files.write(fn, out);
			return true;
		}
		case ASM -> {
			// CASMFileExporter::ExportAsAsm
			AsmChoice dlg = host.showExportAsm();
			if (dlg == null) {
				return false;
			}
			es.prefixForAllAsmLabels = dlg.prefixForAllAsmLabels();
			String code = AsmFileExporter.exportAsAsmApply(song, session.instruments, session.tracks, session.atari.getMemory(), es.prefixForAllAsmLabels, session.tracks4_8, dlg.exportType(), dlg.notesIndexOrFreq(), dlg.durationsType());
			Files.write(fn, textBytes(code));
			return true;
		}
		case ASM_RMTPLAYER -> {
			// CASMFileExporter::ExportAsRelocatableAsmForRmtPlayer
			ModuleDescription withSfx = makeModule(SongIOType.RMT);
			if (withSfx == null) {
				return false;
			}
			AsmFileExporter.RelocatableAsmExportParams p = host.showExportRelocatableAsm(exportDesc, withSfx);
			if (p == null) {
				return false;
			}
			es.asmLabelForStartOfSong = p.strAsmLabelForStartOfSong();
			es.asmWantRelocatableInstruments = p.wantRelocatableInstruments();
			es.asmWantRelocatableTracks = p.wantRelocatableTracks();
			es.asmWantRelocatableSongLines = p.wantRelocatableSongLines();
			es.asmInstrumentsLabel = p.strAsmInstrumentsLabel();
			es.asmTracksLabel = p.strAsmTracksLabel();
			es.asmSongLinesLabel = p.strAsmSongLinesLabel();
			es.asmFormat = p.assemblerFormat();
			es.rmtStrippedSfx = p.sfxSupport();
			es.rmtStrippedGlobalVolumeFade = p.globalVolumeFade();
			es.rmtStrippedNoStartingSongLine = p.noStartingSongLine();
			AsmFileExporter.Result r = AsmFileExporter.exportAsRelocatableAsmForRmtPlayerApply(song, session.instruments, session.tracks, session.tracks4_8, exportDesc.mem(), exportDesc.targetAddrOfModule(), exportDesc.firstByteAfterModule(),
					exportDesc.instrumentSavedFlags(), withSfx.mem(), withSfx.targetAddrOfModule(), withSfx.firstByteAfterModule(), withSfx.instrumentSavedFlags(), p);
			if (!r.success()) {
				return false;
			}
			Files.write(fn, textBytes(r.code()));
			return true;
		}
		case SAPR -> {
			SapFile sapFile = new SapFile();
			sapFile.setType("R");
			if (!showSapDialog(sapFile)) {
				return false;
			}
			Files.write(fn, SapFileExporter.exportSapR(sapFile, generatePokeyStream()));
			return true;
		}
		case LZSS -> {
			// CSongExporter::ExportLZSS: the full tune into the chosen file, the intro and loop sections into "_INTRO.lzss"/"_LOOP.lzss" siblings, each only when longer than 16 bytes
			SongExporter.LzssExportResult r = SongExporter.exportLzss(generatePokeyStream());
			String base = fn.toString();
			base = base.substring(0, Math.max(0, base.length() - 5)); // In order to keep the filename without the extension
			if (r.full().length > 16) {
				Files.write(fn, r.full());
			}
			if (r.intro().length > 16) {
				Files.write(Path.of(base + "_INTRO.lzss"), r.intro());
			}
			if (r.loop().length > 16) {
				Files.write(Path.of(base + "_LOOP.lzss"), r.loop());
			}
			return true;
		}
		case LZSS_SAP -> {
			SapFile sapFile = new SapFile();
			sapFile.setType("B");
			if (!showSapDialog(sapFile)) {
				return false;
			}
			try {
				Files.write(fn, SapFileExporter.exportSapBLzss(sapFile, song, session.tracks4_8, generatePokeyStream()));
			} catch (IllegalStateException ex) { // the driver file missing, or the LZSS data too big for memory - C++'s two error boxes
				session.messages.sendErrorMessage("Export aborted", ex.getMessage());
				return false;
			}
			return true;
		}
		case LZSS_XEX -> {
			XexFile xexFile = XexFile.fromSong(song, session.tracks4_8);
			if (!showXexDialog(xexFile)) {
				return false;
			}
			try {
				Files.write(fn, SongExporter.exportXexLzss(song, session.tracks4_8, xexFile, session.atariTrackerDriver, session.channelControl, session.clipboard, session.undo));
			} catch (IllegalStateException ex) {
				session.messages.sendErrorMessage("Export aborted", ex.getMessage());
				return false;
			}
			return true;
		}
		case WAV -> {
			// CWaveFileExporter::ExportWAV: the recorded stream up to its loop point, replayed through the POKEY
			PokeyStream pokeyStream = generatePokeyStream();
			session.atariTrackerDriver.init(); // Reset the Atari memory
			session.channelControl.setAllChannelsOn();
			Files.write(fn, WaveFileExporter.exportWav(pokeyStream, song.isNTSC(), song.isStereo(session.tracks4_8), song.getInstrumentSpeed()));
			session.channelControl.setAllChannelsOff(); // as C++ ("TODO: Set channels on again?")
			return true;
		}
		default -> {
			return false; // Failed
		}
		}
	}

	/** {@code CSAPFileExportDialog::Show()}: {@code sapFile.Init(song)}, the dialog, then the subsong list parsed as hex song line numbers. */
	private boolean showSapDialog(SapFile sapFile) {
		Song song = session.song;
		sapFile.init(song, session.tracks4_8, today());
		SapChoice dlg = host.showExportSap(sapFile, song.getSubsongParts(session.tracks4_8).parts().trim());
		if (dlg == null) {
			return false;
		}
		sapFile.setAuthor(dlg.author());
		sapFile.setName(dlg.name());
		sapFile.setDate(dlg.date());
		sapFile.setSongs(parseSubsongs(dlg.subsongs()));
		return true;
	}

	/** {@code CTime::GetCurrentTime().Format("%d/%m/%Y")}. */
	private static String today() {
		return java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"));
	}

	/** The "Subsongs" line: hexadecimal numbers separated by anything else; how many there are (at most {@link SapFile#MAXSUBSONGS}). */
	static int parseSubsongs(String subsongs) {
		String str = subsongs.toUpperCase(Locale.ROOT) + " "; // Add space after the last character for parsing
		int count = 0;
		boolean isn = false;
		for (int i = 0; i < str.length(); i++) {
			char a = str.charAt(i);
			if ((a >= '0' && a <= '9') || (a >= 'A' && a <= 'F')) {
				isn = true;
			} else if (isn) {
				count++;
				if (count >= SapFile.MAXSUBSONGS) {
					break;
				}
				isn = false;
			}
		}
		return count;
	}

	/** {@code CSongExporter::ShowXEXExportDialog()}: the default 5-line text (or last time's), the dialog, then the text and options into {@code xexFile}. */
	private boolean showXexDialog(XexFile xexFile) {
		Song song = session.song;
		ExportSettings es = session.exportSettings;
		String txt;
		if (!es.msxText.isEmpty()) {
			txt = es.msxText; // same from last time, making repeated exports faster
		} else {
			txt = song.getName() + "\n" + (xexFile.stereo ? "STEREO" : "") + "\n" + today() + "\n" + "Author: (press SHIFT key)\n" + "Author: ???";
		}
		String speedInfo = "Playback speed will be adjusted to " + (xexFile.ntsc ? "60" : "50") + " Hz on both PAL and NTSC systems.";
		XexChoice dlg = host.showExportXex(txt, speedInfo);
		if (dlg == null) {
			return false;
		}
		es.msxText = dlg.text();
		es.msxRasterbar = dlg.rasterbar();
		es.msxShuffle = dlg.shuffle();
		es.msxRegionAuto = dlg.regionAuto();
		es.msxColor = dlg.color();
		xexFile.setDisplayedText(dlg.text());
		xexFile.rasterbarColor = dlg.color();
		xexFile.displayRasterbar = dlg.rasterbar();
		xexFile.autoRegion = dlg.regionAuto();
		return true;
	}
}
