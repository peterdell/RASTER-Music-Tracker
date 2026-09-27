package com.wudsn.tools.rmt.ui;

import java.awt.Desktop;
import com.wudsn.tools.rmt.model.ProgramFolder;
import java.nio.file.Path;
import java.nio.file.Files;
import java.net.URI;

import com.wudsn.tools.rmt.model.EditMode;
import com.wudsn.tools.rmt.model.InstrumentSection;
import com.wudsn.tools.rmt.model.MessageAnswer;
import com.wudsn.tools.rmt.model.MessageButtons;
import com.wudsn.tools.rmt.model.Part;
import com.wudsn.tools.rmt.model.PlayMode;
import com.wudsn.tools.rmt.model.Song;
import com.wudsn.tools.rmt.model.SongIOType;
import com.wudsn.tools.rmt.model.TrackClipboard;
import com.wudsn.tools.rmt.model.UndoType;

/**
 * The menu/toolbar/accelerator command handlers - the {@code On...()}
 * methods of {@code CRmtView} (RmtView.cpp) and the three of {@code CRmtApp}
 * (help/about), one {@code case} each in {@link #execute}, plus their
 * {@code OnUpdate...()} enable/check/text logic in {@link #isEnabled}/
 * {@link #isChecked}/{@link #getLabel}. Almost all are one-line delegations
 * to model methods, with the same {@code MessageBox} confirmations (through
 * {@link com.wudsn.tools.rmt.model.Messages}, which the window points at
 * real boxes) and the same Undo bookkeeping.
 *
 * <p>What a handler needs from the window goes through {@link Host}. The
 * handlers that open a dialog or a file chooser ({@code FileOpen}, the
 * export/import/renumber/change/order dialogs, About, Options) report
 * {@link Host#notAvailable} until their batch (B7); the printing
 * commands are MFC's own and stay unavailable; MIDI and the Pokey Explorer
 * commands are disabled (no MIDI, {@code CPokeyController} unported).
 * Playback commands change the play state exactly as C++ does; the
 * {@link AudioEngine} thread makes it audible and advancing.
 */
public final class RmtCommands {

	/** The window services the handlers call. */
	public interface Host {
		/** {@code ChangeViewElements()}: show/hide the toolbars and the status bar per the view options. */
		void applyViewElements();

		/** {@code OnGetMinMaxInfo}'s input changed (mono/stereo switch). */
		void updateMinimumSize();

		/** {@code OnFileExit}. */
		void exit();

		/** The command's dialog/feature is not ported yet - tell the user (status bar). */
		void notAvailable(String feature);

		/** {@code g_SkipLinesAfterNoteInsert} changed by a command - sync the toolbar combo. */
		void skipLinesChanged();

		/** {@code OnToolsOptions()}'s dialog part: edit the values (Options dialog), returning whether OK was pressed. */
		boolean editOptions(OptionsValues values);

		/** {@code m_width = m_height = 0; Resize()}: the scaling option changed, redo the canvas without waiting for a window resize. */
		void rescale();

		/** {@code CRmtApp::OnHelpAboutApp()}: the About dialog. */
		void showAbout();
	}

	static final String ONLINE_HELP_URL = "https://html-preview.github.io/?url=https://github.com/raster-atari-org/RASTER-Music-Tracker/blob/1.35/doc//rmt_en.html";
	static final String ASMA_URL = "https://asma.atari.org/";

	private final RmtSession session;
	private final SongInput songInput;
	private final Host host;
	private final SongFiles songFiles;
	private final SongDialogs songDialogs;

	public RmtCommands(RmtSession session, SongInput songInput, Host host, SongFiles songFiles, SongDialogs songDialogs) {
		this.session = session;
		this.songInput = songInput;
		this.host = host;
		this.songFiles = songFiles;
		this.songDialogs = songDialogs;
	}

	public SongFiles getSongFiles() {
		return songFiles;
	}

	public SongDialogs getSongDialogs() {
		return songDialogs;
	}

	private boolean askYes(String title, String message) {
		return session.messages.sendQuestionMessage(title, message, MessageButtons.YES_NO_CANCEL) == MessageAnswer.YES;
	}

	private boolean askYesNo(String title, String message) {
		return session.messages.sendQuestionMessage(title, message, MessageButtons.YES_NO) == MessageAnswer.YES;
	}

	private void info(String title, String message) {
		session.messages.sendInformationMessage(title, message);
	}

	private void play(PlayMode mode) {
		Song song = session.song;
		song.play(mode, song.getFollowPlayMode(), session.undo, session.tracks4_8, session.atariTrackerDriver, session.clipboard);
	}

	private void stop() {
		session.song.stop(session.undo);
	}

	private void undoTrackData() {
		session.undo.changeTrack(session.song.songGetActiveTrack(), session.song.getActiveLine(), UndoType.UETYPE_TRACKDATA);
	}

	private void switchEditMode() {
		session.uiState.switchEditMode(EditMode.EDIT_MODE, session.song.isStereo(session.tracks4_8));
	}

	/**
	 * {@code CRmtView::OnFileSave()}: with "Prompt a save dialog box each time
	 * Ctrl+S is pressed" on and a file to overwrite, asks first - No goes to
	 * Save As, Cancel does nothing. (The two {@code Sleep(128)} calls around
	 * the save have no purpose here.)
	 */
	private void onFileSave() {
		Song song = session.song;
		String filename = song.getFilename();
		if (session.options.keyboardAskWhenControlS && (!filename.isEmpty() || song.getIOType() != SongIOType.NONE)) {
			// If a question is asked and if a file already exists (=> there will be a "Save as ..." dialog)
			MessageAnswer r = session.messages.sendQuestionMessage("Save song", "Do you want to save song file '" + filename + "'?\nIs it okay to overwrite?", MessageButtons.YES_NO_CANCEL);
			if (r == MessageAnswer.NO) {
				songFiles.fileSaveAs();
				return;
			}
			if (r != MessageAnswer.YES) {
				return;
			}
		}
		songFiles.fileSave();
	}

	/** {@code CRmtApp::OnHelp()}: {@code CShell::OpenLocalFile(GetResourceFilePath("docs", "rmt_en.html"))} - the browser is the help viewer; a missing file is reported (the shell would report it in C++). */
	private void openLocalHelp() {
		Path help = localHelpFile();
		if (!Files.isRegularFile(help)) {
			session.messages.sendErrorMessage("Help", "The help file '" + help + "' was not found.");
			return;
		}
		try {
			if (Desktop.isDesktopSupported()) {
				Desktop.getDesktop().browse(help.toUri());
			}
		} catch (Exception e) {
			// Nothing sensible to do - C++'s ShellExecute failure is silent too
		}
	}

	/** {@code docs/rmt_en.html} in the program folder's layout. */
	static Path localHelpFile() {
		return ProgramFolder.getResourceFilePath(Path.of("docs"), "rmt_en.html");
	}

	private static void browse(String url) {
		try {
			if (Desktop.isDesktopSupported()) {
				Desktop.getDesktop().browse(new URI(url));
			}
		} catch (Exception e) {
			// Nothing sensible to do - C++'s ShellExecute failure is silent too
		}
	}

	/**
	 * {@code CRmtView::OnToolsOptions()}'s {@code if (dlg.DoModal() == IDOK)}
	 * block: the dialog's values into the options and the session, with the
	 * side effects C++ has on a change - the canvas rescaled, NTSC switched
	 * through {@link RmtSession#setNTSC}, the driver reloaded, the sound
	 * re-initialized on a sound-buffer change (the option itself has no
	 * effect on the Java line); {@code g_Midi.MidiInit()} has no counterpart
	 * (MIDI not ported).
	 */
	public void applyOptions(OptionsValues dlg) {
		RmtOptions o = session.options;
		// GENERAL
		if (o.scalingPercentage != dlg.scalingPercentage) {
			o.scalingPercentage = dlg.scalingPercentage;
			host.rescale(); // Necessary to scale everything without manually resizing the window first
		}
		if (o.noHwSoundBuffer != dlg.noHwSoundBuffer) {
			session.reInitSound(); // Justified for testing, but this might be a little redundant
		}
		o.noHwSoundBuffer = dlg.noHwSoundBuffer;
		if (session.song.isNTSC() != dlg.ntsc) {
			session.setNTSC(dlg.ntsc);
		}
		if (o.trackerDriverVersion != dlg.trackerDriverVersion) {
			session.setTrackerDriverVersion(dlg.trackerDriverVersion);
		}
		o.view.smoothScrolling = dlg.doSmoothScrolling;
		o.view.debugDisplay = dlg.viewDebugDisplay;
		o.trackLinePrimaryHighlight = dlg.trackLinePrimaryHighlight;
		o.trackLineSecondaryHighlight = dlg.trackLineSecondaryHighlight;
		o.trackLineAltNumbering = dlg.trackLineAltNumbering;
		o.displayFlatNotes = dlg.displayFlatNotes;
		o.useGermanNotation = dlg.useGermanNotation;
		// KEYBOARD
		o.keyboardLayout = dlg.keyboardLayout;
		o.keyboardEscResetAtariSound = dlg.keyboardEscResetAtariSound;
		o.keyboardUpDownContinue = dlg.keyboardUpDownContinue;
		o.keyboardRememberOctavesAndVolumes = dlg.keyboardRememberOctavesAndVolumes;
		o.keyboardAskWhenControlS = dlg.keyboardAskWhenControlS;
		// MIDI
		o.midiDevice = dlg.midiDevice;
		o.midiTouchResponse = dlg.midiTouchResponse;
		o.midiVolumeOffset = dlg.midiVolumeOffset;
		o.midiNoteOff = dlg.midiNoteOff;
	}

	/** Executes a command exactly as its {@code CRmtView::On...()} handler does; a disabled command does nothing (MFC never routes those). */
	public void execute(RmtCommandId id) {
		if (!isEnabled(id)) {
			return;
		}
		Song song = session.song;
		UiState ui = session.uiState;
		RmtOptions options = session.options;
		TrackClipboard clipboard = session.clipboard;
		int tracks4_8 = session.tracks4_8;

		switch (id) {
		// --- File ---
		case FILE_NEW -> songFiles.fileNew();
		case FILE_OPEN -> songFiles.fileOpen(null, true);
		case FILE_REOPEN -> songFiles.fileReload();
		case FILE_SAVE -> onFileSave();
		case FILE_SAVE_AS -> songFiles.fileSaveAs();
		case FILE_IMPORT -> songFiles.fileImport();
		case FILE_EXPORT -> songFiles.fileExportAs();
		case FILE_PRINT, FILE_PRINT_PREVIEW, FILE_PRINT_SETUP, FILE_PROPERTIES -> host.notAvailable("Printing");
		case FILE_EXIT -> host.exit();

		// --- Edit ---
		case EDIT_UNDO -> session.undo.undo();
		case EDIT_REDO -> session.undo.redo();
		case EDIT_CLEAR_UNDO_REDO_HISTORY -> session.undo.clear();
		case PART_TRACKS -> {
			session.undo.separator();
			ui.activePart = ui.activeTi = Part.PART_TRACKS;
		}
		case PART_INSTRUMENTS -> {
			session.undo.separator();
			ui.activePart = ui.activeTi = Part.PART_INSTRUMENTS;
			clipboard.blockDeselect();
		}
		case PART_INFO -> {
			session.undo.separator();
			ui.activePart = Part.PART_INFO;
			clipboard.blockDeselect();
		}
		case PART_SONG -> {
			session.undo.separator();
			ui.activePart = Part.PART_SONG;
			clipboard.blockDeselect();
		}
		case EDIT_SWITCH_EDIT_MODE, TOOLBAR_SWITCH_EDIT_MODE -> switchEditMode();

		// --- View ---
		case VIEW_TOOLBAR -> {
			options.view.mainToolbar = !options.view.mainToolbar;
			host.applyViewElements();
		}
		case VIEW_BLOCKTOOLBAR -> {
			options.view.blockToolbar = !options.view.blockToolbar;
			host.applyViewElements();
		}
		case VIEW_STATUS_BAR -> {
			options.view.statusBar = !options.view.statusBar;
			host.applyViewElements();
		}
		case VIEW_PLAYTIMECOUNTER -> options.view.playTimeCounter = !options.view.playTimeCounter;
		case VIEW_VOLUMEANALYZER -> options.view.volumeAnalyzer = !options.view.volumeAnalyzer;
		case VIEW_INSTRUMENTACTIVEHELP -> options.view.instrumentEditHelp = !options.view.instrumentEditHelp;
		case VIEW_POKEYREGS -> options.view.pokeyRegisters = !options.view.pokeyRegisters;

		// --- Play ---
		case SONG_PLAY_FROM_BOOKMARK -> play(PlayMode.PLAY_BOOKMARK); // from the bookmark - with respect to followplay
		case SONG_PLAY_FROM_START -> play(PlayMode.PLAY_SONG); // whole song from start - with respect to followplay
		case SONG_PLAY_FROM_CURRENT_POSITION -> play(PlayMode.PLAY_FROM); // from the current position - with respect to followplay
		case SONG_PLAY_FROM_CURRENT_POSITION_AND_LOOP -> play(PlayMode.PLAY_TRACK); // from current pattern and loop - with respect to followplay
		case SONG_STOP -> {
			// Stop the music
			if (song.getPlayMode() == PlayMode.PLAY_STOP) {
				return;
			}
			stop();
			ui.playTime = 0;
			// Reset RMT routines automatically?
			if (options.keyboardEscResetAtariSound) {
				session.atariTrackerDriver.init();
			}
		}
		case SONG_PLAY_FOLLOW -> song.setFollowPlayMode(!song.getFollowPlayMode());

		// --- Channels ---
		case CHANNELS_CHANNEL1 -> session.channelControl.toggleChannelOnOff(0);
		case CHANNELS_CHANNEL2 -> session.channelControl.toggleChannelOnOff(1);
		case CHANNELS_CHANNEL3 -> session.channelControl.toggleChannelOnOff(2);
		case CHANNELS_CHANNEL4 -> session.channelControl.toggleChannelOnOff(3);
		case CHANNELS_CHANNEL5 -> session.channelControl.toggleChannelOnOff(4);
		case CHANNELS_CHANNEL6 -> session.channelControl.toggleChannelOnOff(5);
		case CHANNELS_CHANNEL7 -> session.channelControl.toggleChannelOnOff(6);
		case CHANNELS_CHANNEL8 -> session.channelControl.toggleChannelOnOff(7);
		case CHANNELS_TOGGLE_ACTIVE_CHANNEL_ON_OFF -> session.channelControl.toggleChannelOnOff(song.getActiveColumn());
		case CHANNELS_TOGGLE_ACTIVE_CHANNEL_SOLO -> session.channelControl.setChannelSolo(song.getActiveColumn());
		case CHANNELS_TOGGLE_ALL_CHANNELS_ON_OFF -> session.channelControl.toggleAllChannelsOnOff();

		// --- Song ---
		case SONG_COPY_LINE -> song.songCopyLine(tracks4_8);
		case SONG_PASTE_LINE -> song.songPasteLine(session.undo, tracks4_8);
		case SONG_CLEAR_LINE -> song.songClearLine(session.undo, tracks4_8);
		case SONG_SET_BOOKMARK -> song.setBookmark();
		case SONG_CLEAR_BOOKMARK -> song.clearBookmark();
		case SONG_DELETEACTUALLINE -> song.songDeleteLine(song.songGetActiveLine(), session.undo, tracks4_8);
		case SONG_INSERTNEWEMPTYLINE -> song.songInsertLine(song.songGetActiveLine(), session.undo, tracks4_8);
		case SONG_INSERTNEWLINEWITHUNUSEDTRACKS -> {
			int line = song.songGetActiveLine();
			song.songPrepareNewLine(line, -1, true, session.undo, tracks4_8);
			song.songSetActiveLine(line);
		}
		case SONG_INSERTCOPYORCLONEOFSONGLINES -> songDialogs.insertCopyOrCloneOfSongLines();
		case SONG_PUTNEWEMPTYUNUSEDTRACK -> song.songPutnewemptyunusedtrack(session.undo, tracks4_8);
		case SONG_MAKETRACKSDUPLICATE -> song.songMaketracksduplicate(session.undo, tracks4_8, session.messages);
		case SONG_SONG_TOGGLE_TRACK_NUMBER -> songDialogs.songswitch4_8();
		case SONG_TRACKSORDERCHANGE -> songDialogs.tracksOrderChange();
		case SONG_SONGCHANGEMAXIMALLENGTHOFTRACKS -> songDialogs.changeMaxTrackLength();
		case SONG_SIZEOPTIMIZATION -> sizeOptimization();

		// --- Instrument ---
		case INSTR_COPY -> song.instrCopy();
		case INSTR_PASTE -> song.instrPaste(0, session.undo, session.atariTrackerDriver);
		case INSTR_CUT -> {
			session.undo.changeInstrument(song.getActiveInstr(), 0, UndoType.UETYPE_INSTRDATA, 1);
			song.instrCut();
		}
		case INSTR_DELETE -> {
			session.undo.changeInstrument(song.getActiveInstr(), 0, UndoType.UETYPE_INSTRDATA, 1);
			song.instrDelete();
		}
		case INSTRUMENT_PASTESPECIAL_VOLUMELRENVELOPESONLY -> song.instrPaste(1, session.undo, session.atariTrackerDriver); // L/R
		case INSTRUMENT_PASTESPECIAL_VOLUMERENVELOPEONLY -> song.instrPaste(2, session.undo, session.atariTrackerDriver); // R
		case INSTRUMENT_PASTESPECIAL_VOLUMELENVELOPEONLY -> song.instrPaste(3, session.undo, session.atariTrackerDriver); // L
		case INSTRUMENT_PASTESPECIAL_ENVELOPEPARAMETERSONLY -> song.instrPaste(4, session.undo, session.atariTrackerDriver); // ENVELOPE PARS
		case INSTRUMENT_PASTESPECIAL_TABLEONLY -> song.instrPaste(5, session.undo, session.atariTrackerDriver); // TABLE
		case INSTRUMENT_PASTESPECIAL_VOLUMEENVANDENVELOPEPARSONLY -> song.instrPaste(6, session.undo, session.atariTrackerDriver); // VOL+ENV
		case INSTRUMENT_PASTESPECIAL_INSERTVOLUMEENVSANDENVELOPEPARSTOCURSORPOSITION -> song.instrPaste(7, session.undo, session.atariTrackerDriver); // VOL+ENV TO CURPOS
		case INSTRUMENT_PASTESPECIAL_VOLUMELTORENVELOPEONLY -> song.instrPaste(8, session.undo, session.atariTrackerDriver); // volume L to R
		case INSTRUMENT_PASTESPECIAL_VOLUMERTOLENVELOPEONLY -> song.instrPaste(9, session.undo, session.atariTrackerDriver); // volume R to L
		case INSTRUMENT_INFO -> songDialogs.instrInfo();
		case INSTRUMENT_CHANGE -> songDialogs.instrChange();
		case INSTRUMENT_RENUMBERALLINSTRUMENTS -> songDialogs.renumberAllInstruments();
		case INSTR_LOAD -> songFiles.fileInstrumentLoad();
		case INSTR_SAVE -> songFiles.fileInstrumentSave();
		case INSTRUMENT_CLEARALLUNUSEDINSTRUMENTS -> {
			stop();
			if (!askYes("Clear unused instruments", "Are you sure you want to delete all unused instruments in any tracks?")) {
				return;
			}
			session.undo.changeInstrument(0, 0, UndoType.UETYPE_INSTRSALL);
			int clearedinstrs = song.clearAllInstrumentsUnusedInAnyTrack();
			info("Clear unused instruments", "Deleted " + clearedinstrs + " unused instruments.");
		}
		case INSTR_ALLINSTRUMENTSCLEANUP -> {
			// Delete all instruments
			stop();
			if (askYesNo("All instruments cleanup", "WARNING:\nAre you sure you want to cleanup all the instruments?")) {
				session.undo.changeInstrument(0, 0, UndoType.UETYPE_INSTRSALL);
				session.instruments.initInstruments();
			}
		}

		// --- Track ---
		case TRACK_COPY -> song.trackCopy();
		case TRACK_PASTE -> {
			undoTrackData();
			song.trackPaste();
		}
		case TRACK_CUT -> {
			undoTrackData();
			song.trackCut();
		}
		case TRACK_DELETE -> {
			undoTrackData();
			song.trackDelete();
		}
		case SONG_INCREASE_PATTERN_STEP_SIZE -> {
			options.skipLinesAfterNoteInsert++;
			if (options.skipLinesAfterNoteInsert > 8) {
				options.skipLinesAfterNoteInsert = 0;
			}
			host.skipLinesChanged();
		}
		case SONG_DECREASE_PATTERN_STEP_SIZE -> {
			options.skipLinesAfterNoteInsert--;
			if (options.skipLinesAfterNoteInsert < 0) {
				options.skipLinesAfterNoteInsert = 8;
			}
			host.skipLinesChanged();
		}
		case TRACK_INFOABOUTUSINGOFACTUALTRACK -> info("Track Info", trackInfoText(song.songGetActiveTrack()));
		case TRACK_SEARCHANDBUILDLOOP -> {
			stop();
			int track = song.songGetActiveTrack();
			if (track >= 0) {
				session.undo.changeTrack(track, song.getActiveLine(), UndoType.UETYPE_TRACKDATA);
				session.tracks.trackBuildLoop(track);
			}
		}
		case TRACK_EXPANDLOOP -> {
			stop();
			int track = song.songGetActiveTrack();
			if (track >= 0) {
				session.undo.changeTrack(track, song.getActiveLine(), UndoType.UETYPE_TRACKDATA);
				session.tracks.trackExpandLoop(track);
			}
		}
		case SONG_SEARCHANDBUILDLOOPSINALLTRACKS -> {
			// Stop the music first
			stop();
			if (!askYes("Search and rebuild loops", "Are you sure you want to search and rebuild wise loops in all tracks?")) {
				return;
			}
			session.undo.changeTrack(0, 0, UndoType.UETYPE_TRACKSALL);
			// first unpack all existing loops
			song.tracksAllExpandLoops(session.undo);
			// and now search all and create loops again
			Song.TracksAllLoopResult r = song.tracksAllBuildLoops(session.undo);
			info("Search and rebuild loops", "Found and rebuilt loops in " + r.tracksModified() + " tracks (" + r.beatsOrLoops() + " beats/lines).");
		}
		case SONG_EXPANDLOOPSINALLTRACKS -> {
			// Stop the music first
			stop();
			if (!askYes("Expand loops", "Are you sure you want to expand loops in all tracks?")) {
				return;
			}
			session.undo.changeTrack(0, 0, UndoType.UETYPE_TRACKSALL);
			Song.TracksAllLoopResult r = song.tracksAllExpandLoops(session.undo);
			info("Expand loops", "Found and expanded loops in " + r.tracksModified() + " tracks (" + r.beatsOrLoops() + " beats/lines).");
		}
		case TRACK_RENUMBERALLTRACKS -> songDialogs.renumberAllTracks();
		case TRACK_LOAD -> songFiles.fileTrackLoad();
		case TRACK_SAVE -> songFiles.fileTrackSave();
		case TRACK_CLEARALLDUPLICATEDTRACKS -> {
			stop();
			if (!askYes("Clear all duplicated tracks", "Are you sure you want to clear all duplicated tracks and adjust song?")) {
				return;
			}
			session.undo.changeTrack(0, 0, UndoType.UETYPE_TRACKSALL, -1);
			session.undo.changeSong(0, 0, UndoType.UETYPE_SONGDATA, 1);
			int clearedtracks = song.songClearDuplicatedTracks(tracks4_8);
			info("Clear all duplicated tracks", "Deleted " + clearedtracks + " duplicated tracks.");
		}
		case TRACK_CLEARALLTRACKSUNUSEDINSONG -> {
			stop();
			if (!askYes("Clear all unused tracks", "Are you sure you want to delete all tracks unused in song?")) {
				return;
			}
			session.undo.changeTrack(0, 0, UndoType.UETYPE_TRACKSALL);
			int clearedtracks = song.songClearUnusedTracks(tracks4_8);
			info("Clear all unused tracks", "Deleted " + clearedtracks + " tracks unused in song.");
		}
		case TRACK_ALLTRACKSCLEANUP -> {
			// Delete all tracks
			stop();
			if (askYesNo("All tracks cleanup", "WARNING:\nReally cleanup all tracks?")) {
				session.undo.changeTrack(0, 0, UndoType.UETYPE_TRACKSALL);
				session.tracks.initTracks();
			}
		}

		// --- Block ---
		case BLOCK_RESTORE_FROM_BACKUP -> {
			session.undo.changeTrack(song.songGetActiveTrack(), song.getActiveLine(), UndoType.UETYPE_TRACKDATA, 1);
			clipboard.blockRestoreFromBackup(session.tracks);
		}
		case BLOCK_COPY -> songInput.trackKey(VirtualKey.VK_C, false, true); // Ctrl+C
		case BLOCK_PASTE -> song.blockPaste(0, clipboard, session.tracks, session.undo, tracks4_8); // Paste normal
		case BLOCK_PASTESPECIAL_MERGEWITHCURRENTCONTENT -> song.blockPaste(1, clipboard, session.tracks, session.undo, tracks4_8); // paste special - merge
		case BLOCK_PASTESPECIAL_VOLUMEVALUESONLY -> song.blockPaste(2, clipboard, session.tracks, session.undo, tracks4_8); // paste special - volumes only
		case BLOCK_PASTESPECIAL_SPEEDVALUESONLY -> song.blockPaste(3, clipboard, session.tracks, session.undo, tracks4_8); // paste special - speeds only
		case BLOCK_CUT -> songInput.trackKey(VirtualKey.VK_X, false, true); // Ctrl+X
		case BLOCK_DELETE -> songInput.trackKey(VirtualKey.VK_DELETE, false, true); // Del
		case BLOCK_EXCHANGE -> songInput.trackKey(VirtualKey.VK_E, false, true); // Ctrl+E
		case BLOCK_APPLY_EFFECTS -> songDialogs.blockEffectFromKey(); // OnBlockEffect: TrackKey(70, 0, 1) = Ctrl+F
		case BLOCK_SELECTALL -> songInput.trackKey(VirtualKey.VK_A, false, true); // Ctrl+A
		case BLOCK_TRANSPOSE_NOTES_UP -> clipboard.blockNoteTransposition(song.getActiveInstr(), 1, session.tracks);
		case BLOCK_TRANSPOSE_NOTES_DOWN -> clipboard.blockNoteTransposition(song.getActiveInstr(), -1, session.tracks);
		case BLOCK_USE_PREVIOUS_INSTRUMENT -> clipboard.blockInstrumentChange(song.getActiveInstr(), -1, session.tracks);
		case BLOCK_USE_NEXT_INSTRUMENT -> clipboard.blockInstrumentChange(song.getActiveInstr(), 1, session.tracks);
		case BLOCK_INCREASE_VOLUME -> clipboard.blockVolumeChange(song.getActiveInstr(), 1, session.tracks);
		case BLOCK_DECREASE_VOLUME -> clipboard.blockVolumeChange(song.getActiveInstr(), -1, session.tracks);
		case BLOCK_TOGGLE_MODIFICATION_MODE -> clipboard.blockAllOnOff(session.tracks);
		case BLOCK_PLAY_AND_LOOP -> play(PlayMode.PLAY_BLOCK); // selected block and loop - with respect to followplay

		// --- Pokey ---
		case EDIT_ACTIVATE_POKEY_EXPLORER_MODE -> ui.editMode = EditMode.POKEY_EXPLORER_MODE;
		case POKEY_REGISTER_INCREASE_BY_01, POKEY_REGISTER_INCREASE_BY_10, POKEY_REGISTER_DECREASE_BY_01, POKEY_REGISTER_DECREASE_BY_10, POKEY_AUDCTL_BIT0, POKEY_AUDCTL_BIT1, POKEY_AUDCTL_BIT2, POKEY_AUDCTL_BIT3, POKEY_AUDCTL_BIT4, POKEY_AUDCTL_BIT5, POKEY_AUDCTL_BIT6, POKEY_AUDCTL_BIT7, POKEY_SKCTL_TWO_TONE_MODE, POKEY_NEXTCHANNEL, POKEY_PREVIOUSCHANNEL, POKEY_DIVISOR_INCREASE_BY_01, POKEY_DIVISOR_INCREASE_BY_1, POKEY_DIVISOR_DECREASE_BY_01, POKEY_DIVISOR_DECREASE_BY_1 -> {
			// never enabled - CPokeyController is unported
		}

		// --- Tools ---
		case TOOLS_OPEN_ASMA -> browse(ASMA_URL);
		case TOOLS_OPEN_ASAP_FILE -> {
			// ID_TOOLS_OPEN_ASAP_FILE has no handler anywhere in the C++ sources (only its resource ID): MFC shows it disabled, see isEnabled()
		}
		case TOOLS_OPTIONS -> {
			OptionsValues values = OptionsValues.from(session);
			if (host.editOptions(values)) {
				applyOptions(values);
			}
		}

		// --- Help ---
		case HELP -> openLocalHelp();
		case CONTEXT_HELP -> browse(ONLINE_HELP_URL);
		case HELP_ABOUT_APP -> host.showAbout();

		case MIDIONOFF -> {
			// never enabled - no MIDI
		}
		}
	}

	/** {@code OnSongSizeoptimization()}: the whole chain of size optimizations, with its summary box. */
	private void sizeOptimization() {
		Song song = session.song;
		int tracks4_8 = session.tracks4_8;
		if (!askYes("All size optimizations", "Are you sure you want to delete all tracks and instruments unused in song,\ntruncate unused tracks and rebuild wise tracks loops,\ndelete all duplicated tracks, renumber all tracks and instruments\nand change maximal tracks length to effective computed value?")) {
			return;
		}
		stop(); // Stop music

		session.undo.changeTrack(0, 0, UndoType.UETYPE_TRACKSALL, -1);
		session.undo.changeInstrument(0, 0, UndoType.UETYPE_INSTRSALL, -1);
		session.undo.changeSong(0, 0, UndoType.UETYPE_SONGDATA, 1);

		boolean chmaxtl = false;

		// First unpack all existing loops
		song.tracksAllExpandLoops(session.undo);

		// Find the effective length of maxtracklen and shorten it if necessary
		int maxtracklen = session.tracks.getMaxTrackLength();
		int effemaxtracklen = song.getEffectiveMaxtracklen(tracks4_8);
		if (effemaxtracklen < maxtracklen) {
			song.changeMaxtracklen(effemaxtracklen);
			chmaxtl = true;
		}

		// Now it will back up
		Song.TracksAllLoopResult opti = song.tracksAllBuildLoops(session.undo);

		// And until the end, don't use the tracks and their parts
		Song.ClearUnusedResult cleared = song.songClearUnusedTracksAndParts(tracks4_8);

		// And only now (after clearing unused tracks) it will remove unused instruments
		int clearedinstruments = song.clearAllInstrumentsUnusedInAnyTrack();

		// And now it eliminates double tracks and corrects their occurrences in the song
		// (may have been created by previous edits)
		int duplicatedtracks = song.songClearDuplicatedTracks(tracks4_8);

		// Now refines the tracks (to remove any gaps)
		song.renumberAllTracks(1, tracks4_8);

		// And now refines the instruments (to remove any gaps)
		song.renumberAllInstruments(1);

		String s = String.format("Deleted %d unused tracks, %d unused instruments,\ntruncated %d tracks (%d beats/lines),\nfound and rebuilt loops in %d tracks (%d beats/lines),\ndeleted %d duplicated tracks.", cleared.clearedTracks(), clearedinstruments, cleared.truncatedTracks(), cleared.truncatedBeats(), opti.tracksModified(), opti.beatsOrLoops(), duplicatedtracks);
		if (chmaxtl) {
			s += "\nMaximal length of tracks changed to " + effemaxtracklen + ".";
		}
		info("All size optimizations", s);
	}

	/** The text of {@code CSong::TrackInfo()}'s "Track Info" box. */
	String trackInfoText(int track) {
		final String[] cnames = { "L1", "L2", "L3", "L4", "R1", "R2", "R3", "R4" };
		Song.TrackInfo info = new Song.TrackInfo();
		session.song.trackInfo(track, info, session.tracks4_8);
		StringBuilder s = new StringBuilder(String.format("Track: %02X\nUsing in song:\n", track));
		for (int ch = 0; ch < session.tracks4_8; ch++) {
			s.append(cnames[ch]).append(": ").append(info.usedInColumn[ch]).append("   ");
		}
		s.append("\nUsed in ").append(info.lines).append(" songlines, globally ").append(info.count).append(" times.");
		return s.toString();
	}

	/** The {@code OnUpdate...()} handlers' {@code pCmdUI->Enable(...)}; commands without one are always enabled, unported ones never. */
	public boolean isEnabled(RmtCommandId id) {
		Song song = session.song;
		UiState ui = session.uiState;
		TrackClipboard clipboard = session.clipboard;
		boolean stereo = session.tracks4_8 > 4;
		int activeTrack = song.songGetActiveTrack();

		return switch (id) {
		case FILE_REOPEN -> songFiles.fileCanBeReloaded();
		case FILE_PRINT, FILE_PRINT_PREVIEW, FILE_PRINT_SETUP, FILE_PROPERTIES -> false;
		case EDIT_UNDO -> session.undo.getUndoSteps() > 0;
		case EDIT_REDO -> session.undo.getRedoSteps() > 0;
		case EDIT_CLEAR_UNDO_REDO_HISTORY -> session.undo.getUndoSteps() > 0 || session.undo.getRedoSteps() > 0;
		case EDIT_ACTIVATE_POKEY_EXPLORER_MODE -> ui.editMode != EditMode.POKEY_EXPLORER_MODE;
		case VIEW_POKEYREGS -> session.options.view.volumeAnalyzer;
		case SONG_PLAY_FROM_BOOKMARK -> song.isBookmark();
		case SONG_STOP -> song.getPlayMode() != PlayMode.PLAY_STOP;
		case CHANNELS_TOGGLE_ACTIVE_CHANNEL_ON_OFF, CHANNELS_TOGGLE_ACTIVE_CHANNEL_SOLO -> song.getActiveColumn() >= 0;
		case SONG_CLEAR_BOOKMARK -> song.isBookmark();
		case SONG_MAKETRACKSDUPLICATE -> activeTrack >= 0;
		case INSTRUMENT_PASTESPECIAL_INSERTVOLUMEENVSANDENVELOPEPARSTOCURSORPOSITION -> ui.activePart == Part.PART_INSTRUMENTS && session.instruments.getActiveEditSection(song.getActiveInstr()) == InstrumentSection.ENVELOPE; // when the envelope is being edited
		case INSTRUMENT_PASTESPECIAL_VOLUMELENVELOPEONLY, INSTRUMENT_PASTESPECIAL_VOLUMERENVELOPEONLY, INSTRUMENT_PASTESPECIAL_VOLUMERTOLENVELOPEONLY, INSTRUMENT_PASTESPECIAL_VOLUMELTORENVELOPEONLY -> stereo;
		case TRACK_COPY, TRACK_PASTE, TRACK_CUT, TRACK_DELETE -> ui.activePart != Part.PART_INSTRUMENTS && activeTrack >= 0;
		case TRACK_LOAD, TRACK_SAVE, TRACK_INFOABOUTUSINGOFACTUALTRACK -> activeTrack >= 0;
		case TRACK_SEARCHANDBUILDLOOP -> activeTrack >= 0 && session.tracks.getGoLine(activeTrack) < 0;
		case TRACK_EXPANDLOOP -> activeTrack >= 0 && session.tracks.getGoLine(activeTrack) >= 0;
		case BLOCK_RESTORE_FROM_BACKUP, BLOCK_CUT, BLOCK_DELETE, BLOCK_EXCHANGE, BLOCK_APPLY_EFFECTS, BLOCK_TRANSPOSE_NOTES_UP, BLOCK_TRANSPOSE_NOTES_DOWN, BLOCK_USE_PREVIOUS_INSTRUMENT, BLOCK_USE_NEXT_INSTRUMENT, BLOCK_INCREASE_VOLUME, BLOCK_DECREASE_VOLUME, BLOCK_TOGGLE_MODIFICATION_MODE, BLOCK_PLAY_AND_LOOP -> clipboard.isBlockSelected();
		case POKEY_REGISTER_INCREASE_BY_01, POKEY_REGISTER_INCREASE_BY_10, POKEY_REGISTER_DECREASE_BY_01, POKEY_REGISTER_DECREASE_BY_10, POKEY_AUDCTL_BIT0, POKEY_AUDCTL_BIT1, POKEY_AUDCTL_BIT2, POKEY_AUDCTL_BIT3, POKEY_AUDCTL_BIT4, POKEY_AUDCTL_BIT5, POKEY_AUDCTL_BIT6, POKEY_AUDCTL_BIT7, POKEY_SKCTL_TWO_TONE_MODE, POKEY_NEXTCHANNEL, POKEY_PREVIOUSCHANNEL, POKEY_DIVISOR_INCREASE_BY_01, POKEY_DIVISOR_INCREASE_BY_1, POKEY_DIVISOR_DECREASE_BY_01, POKEY_DIVISOR_DECREASE_BY_1 -> false; // CPokeyController unported
		case MIDIONOFF -> false; // no MIDI
		case TOOLS_OPEN_ASAP_FILE -> false; // no handler in C++ either - MFC greys out a menu item without one
		default -> true;
		};
	}

	/** The {@code OnUpdate...()} handlers' {@code pCmdUI->SetCheck(...)}. */
	public boolean isChecked(RmtCommandId id) {
		Song song = session.song;
		UiState ui = session.uiState;
		RmtOptions.ViewState view = session.options.view;
		return switch (id) {
		case PART_TRACKS -> ui.activePart == Part.PART_TRACKS && ui.activeTi == Part.PART_TRACKS;
		case PART_INSTRUMENTS -> ui.activePart == Part.PART_INSTRUMENTS && ui.activeTi == Part.PART_INSTRUMENTS;
		case PART_INFO -> ui.activePart == Part.PART_INFO;
		case PART_SONG -> ui.activePart == Part.PART_SONG;
		case TOOLBAR_SWITCH_EDIT_MODE -> ui.editMode.isProveMode();
		case VIEW_TOOLBAR -> view.mainToolbar;
		case VIEW_BLOCKTOOLBAR -> view.blockToolbar;
		case VIEW_STATUS_BAR -> view.statusBar;
		case VIEW_PLAYTIMECOUNTER -> view.playTimeCounter;
		case VIEW_VOLUMEANALYZER -> view.volumeAnalyzer;
		case VIEW_INSTRUMENTACTIVEHELP -> view.instrumentEditHelp;
		case VIEW_POKEYREGS -> view.pokeyRegisters;
		case SONG_PLAY_FROM_BOOKMARK -> song.getPlayMode() == PlayMode.PLAY_BOOKMARK;
		case SONG_PLAY_FROM_START -> song.getPlayMode() == PlayMode.PLAY_SONG;
		case SONG_PLAY_FROM_CURRENT_POSITION -> song.getPlayMode() == PlayMode.PLAY_FROM;
		case SONG_PLAY_FROM_CURRENT_POSITION_AND_LOOP -> song.getPlayMode() == PlayMode.PLAY_TRACK;
		case SONG_PLAY_FOLLOW -> song.getFollowPlayMode();
		case CHANNELS_CHANNEL1 -> session.channelControl.isChannelOn(0);
		case CHANNELS_CHANNEL2 -> session.channelControl.isChannelOn(1);
		case CHANNELS_CHANNEL3 -> session.channelControl.isChannelOn(2);
		case CHANNELS_CHANNEL4 -> session.channelControl.isChannelOn(3);
		case CHANNELS_CHANNEL5 -> session.channelControl.isChannelOn(4);
		case CHANNELS_CHANNEL6 -> session.channelControl.isChannelOn(5);
		case CHANNELS_CHANNEL7 -> session.channelControl.isChannelOn(6);
		case CHANNELS_CHANNEL8 -> session.channelControl.isChannelOn(7);
		case BLOCK_TOGGLE_MODIFICATION_MODE -> session.clipboard.isAll();
		case BLOCK_PLAY_AND_LOOP -> song.getPlayMode() == PlayMode.PLAY_BLOCK;
		default -> false;
		};
	}

	/** The {@code OnUpdate...()} handlers' {@code pCmdUI->SetText(...)}: the Undo/Redo step counts and the mono/stereo switch wording; {@code null} keeps the static label. */
	public String getLabel(RmtCommandId id) {
		return switch (id) {
		case EDIT_UNDO -> session.undo.getUndoSteps() > 0 ? "&Undo (" + session.undo.getUndoSteps() + ")" : "&Undo";
		case EDIT_REDO -> session.undo.getRedoSteps() > 0 ? "&Redo (" + session.undo.getRedoSteps() + ")" : "&Redo";
		case SONG_SONG_TOGGLE_TRACK_NUMBER -> session.tracks4_8 <= 4 ? "Switch song to Stereo 8 tracks..." : "Switch song to Mono 4 tracks...";
		default -> null;
		};
	}
}
