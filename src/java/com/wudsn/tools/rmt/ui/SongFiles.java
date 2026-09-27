package com.wudsn.tools.rmt.ui;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

import com.wudsn.tools.rmt.model.Instruments;
import com.wudsn.tools.rmt.model.MessageAnswer;
import com.wudsn.tools.rmt.model.MessageButtons;
import com.wudsn.tools.rmt.model.RmtExporter;
import com.wudsn.tools.rmt.model.Song;
import com.wudsn.tools.rmt.model.SongIOType;
import com.wudsn.tools.rmt.model.Tracks;
import com.wudsn.tools.rmt.model.UndoType;

/**
 * The song/instrument/track file commands - the port of {@code IO_Song.cpp}'s
 * {@code CSong::FileOpen/FileReload/FileSave/FileSaveAs/FileNew/
 * FileInstrumentSave/FileInstrumentLoad/FileTrackSave/FileTrackLoad} and
 * {@code GUI_Song.cpp}'s {@code WarnUnsavedChanges()}. The file dialogs and
 * the two small dialogs (New Module, Tracks loading) stay behind
 * {@link Host}, so the flows are tested headless with a stub; the reading,
 * decoding and writing use the model's already-ported loaders and savers.
 * Import and export ({@code FileImport}/{@code FileExportAs}) are the next
 * sub-batch's.
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
	}

	/** {@code FILE_LOADSAVE}: RMT = 1, TXT = 2, RMW = 3. */
	public static final List<FileFilter> SONG_FILTERS = List.of(new FileFilter("RMT song file", ".rmt"), new FileFilter("TXT song file", ".txt"), new FileFilter("RMW song work file", ".rmw"));
	public static final int FILTER_RMT = 1;
	public static final int FILTER_TXT = 2;
	public static final int FILTER_RMW = 3;
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
		session.tracks4_8 = session.song.clearSong(session.tracks4_8, session.undo);
		session.uiState.changes = false;
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
				session.tracks4_8 = r.tracks4_8();
			}
			ioType = SongIOType.RMT;
		}
		case FILTER_TXT -> {
			session.tracks4_8 = song.loadTxt(new String(data, TEXT_CHARSET), session.undo).tracks4_8();
			loadedOk = true; // LoadTxt "does not mean the resultant data is valid"
			ioType = SongIOType.TXT;
		}
		default -> {
			Song.LoadRmwResult r = song.loadRMW(data, session.undo);
			loadedOk = r.success();
			if (loadedOk) {
				session.tracks4_8 = r.tracks4_8();
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
		session.tracks4_8 = session.song.fileNewApply(choice.maxTrackLength(), choice.stereo() ? 8 : 4, session.undo);
		session.uiState.changes = false; // ClearSong
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
}
