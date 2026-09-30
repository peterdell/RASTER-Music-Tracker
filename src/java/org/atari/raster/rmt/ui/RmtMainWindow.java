package org.atari.raster.rmt.ui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import javax.imageio.ImageIO;
import javax.swing.BoxLayout;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.WindowConstants;
import javax.swing.filechooser.FileNameExtensionFilter;

import com.wudsn.tools.base.gui.MainWindow;

/**
 * The application's main window - the port of {@code CMainFrame}
 * (MainFrm.cpp), on WUDSN Base's {@link MainWindow} (a {@link JFrame} that
 * tracks its un-maximized geometry for the preferences store): the
 * {@link RmtMainMenu}, the two {@link RmtToolBars} with the skip-lines
 * combo box, the {@link TrackerPanel} filling the rest, and a status line
 * at the bottom. Commands run through {@link RmtCommands}; their
 * enabled/checked states are refreshed on every display tick (MFC's idle
 * {@code ON_UPDATE_COMMAND_UI} pass), and the menu's accelerator table is
 * consulted for every key before the tracker's own key handling
 * ({@code TranslateAccelerator}).
 *
 * <p>Start-up follows {@code CRmtView::OnInitialUpdate()}: the two
 * configuration files are read ({@link RmtConfig}) and the view elements
 * applied without writing; the window geometry is restored from
 * {@link RmtWindowPreferences} ({@code CMainFrame::PreCreateWindow}). Exit
 * follows {@code OnFileExit}/{@code CMainFrame::OnClose}: both
 * configuration files and the geometry are written. Still to come: the
 * unsaved-changes prompt on close ({@code WarnUnsavedChanges}, B9).
 */
public final class RmtMainWindow implements RmtCommands.Host, SongFiles.Host, SongDialogs.Host {

	/** What {@code CRmtApp::GetVersionAndBuild()} produces for the C++ build ("RASTER Music Tracker 1.36 (Sep 28 2026 01:30:01)"); the Java port carries no build stamp yet. */
	/** {@code CRmtApp::GetVersionAndBuild()}: {@code "<version> (<build date>)"} - the date from the jar manifest's {@code Build-Date} (written by the Maven build), "Java" alone when running from a classes folder. */
	public static final String VERSION_AND_BUILD = versionAndBuild();

	private static String versionAndBuild() {
		String build = "Java";
		try (java.io.InputStream in = RmtMainWindow.class.getResourceAsStream("/META-INF/MANIFEST.MF")) {
			if (in != null) {
				java.util.jar.Attributes main = new java.util.jar.Manifest(in).getMainAttributes();
				String date = main.getValue("Build-Date");
				if (date != null && RmtApplication.class.getName().equals(main.getValue("Main-Class"))) {
					build = "Java " + date;
				}
			}
		} catch (IOException | RuntimeException e) {
			// the plain "Java" then
		}
		return org.atari.raster.rmt.model.RmtVersion.RMT_VERSION_STRING + " (" + build + ")";
	}

	private final RmtSession session;
	private final RmtConfig config;
	private final RmtWindowPreferences preferences;
	private final MainWindow mainWindow = new MainWindow();
	private final TrackerPanel trackerPanel;
	private final RmtCommands commands;
	private final RmtMainMenu mainMenu;
	private final RmtToolBars toolBars;
	/** {@code g_SongTimer} + {@code g_Pokey}: the frame thread that plays and renders. */
	private final AudioEngine audioEngine;
	/** {@code CSong::MidiEvent}: what the MIDI IN device's messages do; fed by {@link RmtMidi} on the device's thread, under the session lock. */
	private final MidiInput midiInput;
	/** {@code g_RmtHasFocus}: {@code CRmtView::OnSetFocus}/{@code OnKillFocus} - MIDI recording needs the tracker panel focused (read on the MIDI thread). */
	private volatile boolean trackerHasFocus;
	private final JPanel toolBarPanel = new JPanel();
	/** C++'s status bar shows the command prompts and {@code SetStatusBarText()} messages; a plain label until B9 decides on WUDSN's {@code StatusBar}. */
	private final JLabel statusLine = new JLabel(" ");

	public RmtMainWindow(RmtSession session, RmtConfig config, RmtWindowPreferences preferences) {
		this.session = session;
		this.config = config;
		this.preferences = preferences;
		this.trackerPanel = new TrackerPanel(session);
		SongDialogs songDialogs = new SongDialogs(session, this);
		this.commands = new RmtCommands(session, trackerPanel.getSongInput(), this, new SongFiles(session, this), songDialogs);
		trackerPanel.setSongDialogs(songDialogs);
		trackerPanel.getSongInput().setInsertCopyOrCloneAction(songDialogs::insertCopyOrCloneOfSongLines);
		trackerPanel.getSongInput().setBlockEffectAction(songDialogs::blockEffectFromKey);
		this.mainMenu = new RmtMainMenu(this::executeCommand);
		this.toolBars = new RmtToolBars(mainMenu, this::executeCommand, this::skipLinesSelected);
		this.midiInput = new MidiInput(session, trackerPanel.getSongInput(), session.midi);
		session.midi.setListener((status, data1, data2) -> {
			session.locked(() -> midiInput.midiEvent(status, data1, data2, trackerHasFocus));
			trackerPanel.refreshScreen(); // repaint() is thread-safe; the display timer paints
		});
		trackerPanel.addFocusListener(new java.awt.event.FocusAdapter() {
			@Override
			public void focusGained(java.awt.event.FocusEvent e) {
				trackerHasFocus = true; // RMT main window has focus
			}

			@Override
			public void focusLost(java.awt.event.FocusEvent e) {
				trackerHasFocus = false; // RMT main window does not have focus
			}
		});

		this.audioEngine = new AudioEngine(session);

		JFrame frame = mainWindow.getFrame();
		session.messages.setHandler(new SwingMessages(frame, session));
		// CMainFrame::OnClose: the close box goes through ID_FILE_EXIT (which saves everything), the frame only closes once that decided to
		frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
		frame.addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent e) {
				executeCommand(RmtCommandId.FILE_EXIT);
			}

			@Override
			public void windowClosed(WindowEvent e) {
				audioEngine.stop(); // CRmtApp::ExitInstance -> StopTimer + DeInitSound
				session.midi.midiOff(); // ~CRmtMidi
				trackerPanel.stopDisplayTimer();
				System.exit(0);
			}
		});
		try (InputStream in = RmtMainWindow.class.getResourceAsStream("application.png")) {
			if (in != null) {
				frame.setIconImage(ImageIO.read(in));
			}
		} catch (IOException ignored) {
			// No icon is not worth failing the start-up for.
		}

		frame.setJMenuBar(mainMenu.menuBar);
		toolBarPanel.setLayout(new BoxLayout(toolBarPanel, BoxLayout.Y_AXIS));
		toolBars.mainToolBar.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT); // the rebar's bands start at the left edge
		toolBars.blockToolBar.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
		toolBarPanel.add(toolBars.mainToolBar);
		toolBarPanel.add(toolBars.blockToolBar);
		frame.setLayout(new BorderLayout());
		frame.add(toolBarPanel, BorderLayout.NORTH);
		frame.add(trackerPanel, BorderLayout.CENTER);
		frame.add(statusLine, BorderLayout.SOUTH);
		frame.pack();
		frame.setLocationRelativeTo(null);
		// CMainFrame::PreCreateWindow: "only restore if there is a previously saved position"
		if (preferences.isStored()) {
			mainWindow.setWindowFromPreferences(preferences);
		}

		trackerPanel.setAcceleratorDispatcher(keyStroke -> {
			RmtCommandId id = mainMenu.lookupAccelerator(keyStroke);
			if (id == null) {
				return false;
			}
			executeCommand(id);
			return true;
		});
		trackerPanel.setIdleAction(() -> {
			updateCommandStates();
			updateTitle(); // g_changes may have been set by any edit (InsertEvent's SetRMTTitle)
		});

		// CRmtView::OnInitialUpdate: CONFIGURATION, tuning, view elements (without write!)
		config.readRMTConfig(session);
		config.readTuningConfig(session);
		showViewElements();
		updateMinimumSize();
		updateTitle();
		updateCommandStates();
	}

	private void executeCommand(RmtCommandId id) {
		session.locked(() -> {
			commands.execute(id);
			updateCommandStates();
			updateTitle();
		});
		trackerPanel.requestFocusInWindow();
		trackerPanel.refreshScreen();
	}

	/** {@code CMainFrame::OnSelChangedComboSkipLinesAfterNoteInsert()}. */
	private void skipLinesSelected(int index) {
		if (index >= 0 && index != session.options.skipLinesAfterNoteInsert) {
			session.locked(() -> session.options.skipLinesAfterNoteInsert = index);
		}
		trackerPanel.requestFocusInWindow(); // OnRestoreFocusToMainWindow
	}

	/** The idle-time {@code ON_UPDATE_COMMAND_UI} pass. */
	public void updateCommandStates() {
		mainMenu.updateStates(commands);
	}

	public JFrame getFrame() {
		return mainWindow.getFrame();
	}

	public TrackerPanel getTrackerPanel() {
		return trackerPanel;
	}

	public RmtCommands getCommands() {
		return commands;
	}

	/** {@code CMainFrame::OnGetMinMaxInfo()}: 800x600 for a mono song, 1120x600 for a stereo one. */
	@Override
	public void updateMinimumSize() {
		getFrame().setMinimumSize(new Dimension(session.tracks4_8 == 8 ? 1120 : 800, 600));
	}

	/** {@code FileImport()}'s "Imported ..." window text, until {@link #updateTitle} has a reason to replace it. */
	private String importedTitle;

	/** {@code CSong::SetRMTTitle()}: the file name (or the version text for an unnamed song), with " *" appended once there are unsaved changes. */
	public void updateTitle() {
		String filename = session.song.getFilename();
		String title;
		if (filename.isEmpty()) {
			if (importedTitle != null && !session.uiState.changes) {
				title = importedTitle; // C++ only overwrites "Imported ..." on the next SetRMTTitle (an edit's "Noname *", a load, ...)
			} else {
				importedTitle = null;
				title = session.uiState.changes ? "Noname *" : VERSION_AND_BUILD;
			}
		} else {
			importedTitle = null;
			title = session.uiState.changes ? filename + " *" : filename;
		}
		if (!title.equals(getFrame().getTitle())) {
			getFrame().setTitle(title);
		}
	}

	/** {@code CRmtView::ChangeViewElements(0)}: show/hide the bars without writing the configuration. */
	private void showViewElements() {
		RmtOptions.ViewState view = session.options.view;
		toolBars.mainToolBar.setVisible(view.mainToolbar);
		toolBars.blockToolBar.setVisible(view.blockToolbar);
		toolBarPanel.setVisible(view.mainToolbar || view.blockToolbar);
		statusLine.setVisible(view.statusBar);
		getFrame().revalidate();
	}

	/** {@code CRmtView::ChangeViewElements(1)} - the View menu's toggles also write {@code rmt.ini} at once. */
	@Override
	public void applyViewElements() {
		showViewElements();
		config.writeRMTConfig(session);
	}

	/** {@code OnFileExit} + {@code CMainFrame::OnClose}: the unsaved-changes prompt, then configuration, tuning and window geometry saved, then the frame closed. */
	@Override
	public void exit() {
		if (commands.getSongFiles().warnUnsavedChanges()) {
			return; // There is no exit
		}
		session.song.stop(session.undo);
		config.writeRMTConfig(session); // Save the current configuration
		config.writeTuningConfig(session); // Save the current tuning parameters
		mainWindow.setPreferencesFromWindow(preferences); // Save main window position
		preferences.flush();
		getFrame().dispose();
	}

	@Override
	public void notAvailable(String feature) {
		statusLine.setText(feature + " is not available in the Java port.");
	}

	@Override
	public void skipLinesChanged() {
		if (toolBars.skipLinesCombo.getSelectedIndex() != session.options.skipLinesAfterNoteInsert) {
			toolBars.skipLinesCombo.setSelectedIndex(session.options.skipLinesAfterNoteInsert);
		}
	}

	/** {@code COptionsDialog dlg; ... dlg.DoModal() == IDOK}. */
	@Override
	public boolean editOptions(OptionsValues values) {
		return session.unlocked(() -> new OptionsDialog(getFrame(), session, values).showDialog());
	}

	@Override
	public void rescale() {
		trackerPanel.rescale();
	}

	@Override
	public void showAbout() {
		session.unlocked(() -> new AboutDialog(getFrame()).setVisible(true));
	}

	// ---- SongFiles.Host: CFileDialog and the two small dialogs ----

	/** {@code SetRMTTitle()}, plus the minimum size a mono/stereo change implies. */
	@Override
	public void songChanged() {
		updateTitle();
		updateMinimumSize();
	}

	@Override
	public SongFiles.FileChoice chooseOpenFile(String title, List<SongFiles.FileFilter> filters, String initialDir, int initialFilterIndex) {
		JFileChooser chooser = createFileChooser(title, filters, initialDir, initialFilterIndex, "");
		if (session.unlocked(() -> chooser.showOpenDialog(getFrame())) != JFileChooser.APPROVE_OPTION) {
			return null;
		}
		return new SongFiles.FileChoice(chooser.getSelectedFile().toPath(), filterIndexOf(chooser, filters));
	}

	private static final List<SongFiles.FileFilter> SCRIPT_FILTERS = List.of(new SongFiles.FileFilter("RMT script file", ".rmtscript", ".txt"));

	@Override
	public Path chooseScriptFile() {
		JFileChooser chooser = createFileChooser("Run script", SCRIPT_FILTERS, session.options.lastSongsPath, 1, "");
		if (session.unlocked(() -> chooser.showOpenDialog(getFrame())) != JFileChooser.APPROVE_OPTION) {
			return null;
		}
		return chooser.getSelectedFile().toPath();
	}

	/** {@code OFN_OVERWRITEPROMPT}: the file (with the filter's extension ensured, as C++ does right after the dialog) must not exist, or the user agrees. */
	@Override
	public SongFiles.FileChoice chooseSaveFile(String title, List<SongFiles.FileFilter> filters, String initialDir, int initialFilterIndex, String suggestedFileName) {
		JFileChooser chooser = createFileChooser(title, filters, initialDir, initialFilterIndex, suggestedFileName);
		while (true) {
			if (session.unlocked(() -> chooser.showSaveDialog(getFrame())) != JFileChooser.APPROVE_OPTION) {
				return null;
			}
			int filterIndex = filterIndexOf(chooser, filters);
			Path path = SongFiles.ensureFileExtension(chooser.getSelectedFile().toPath(), filters, filterIndex);
			if (!Files.exists(path) || session.unlocked(() -> JOptionPane.showConfirmDialog(getFrame(), path.getFileName() + " already exists.\nDo you want to replace it?", title, JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE)) == JOptionPane.YES_OPTION) {
				return new SongFiles.FileChoice(path, filterIndex);
			}
		}
	}

	/** A {@code CFileDialog}: one selectable filter per {@code FILE_*} entry (no "all files"), the initial folder and filter, the suggested name. */
	private static JFileChooser createFileChooser(String title, List<SongFiles.FileFilter> filters, String initialDir, int initialFilterIndex, String suggestedFileName) {
		JFileChooser chooser = new JFileChooser();
		chooser.setDialogTitle(title);
		chooser.setAcceptAllFileFilterUsed(false);
		for (SongFiles.FileFilter filter : filters) {
			String[] extensions = filter.extensions().stream().map(e -> e.substring(1)).toArray(String[]::new);
			String description = filter.description() + " (" + String.join(", ", filter.extensions().stream().map(e -> "*" + e).toList()) + ")";
			chooser.addChoosableFileFilter(new FileNameExtensionFilter(description, extensions));
		}
		if (initialFilterIndex >= 1 && initialFilterIndex <= filters.size()) {
			chooser.setFileFilter(chooser.getChoosableFileFilters()[initialFilterIndex - 1]);
		}
		if (initialDir != null && !initialDir.isEmpty() && Files.isDirectory(Path.of(initialDir))) {
			chooser.setCurrentDirectory(Path.of(initialDir).toFile());
		}
		if (suggestedFileName != null && !suggestedFileName.isEmpty()) {
			chooser.setSelectedFile(new java.io.File(chooser.getCurrentDirectory(), suggestedFileName));
		}
		return chooser;
	}

	/** {@code m_ofn.nFilterIndex}: 1-based index of the filter the user left selected. */
	private static int filterIndexOf(JFileChooser chooser, List<SongFiles.FileFilter> filters) {
		javax.swing.filechooser.FileFilter[] choosable = chooser.getChoosableFileFilters();
		for (int i = 0; i < choosable.length; i++) {
			if (choosable[i] == chooser.getFileFilter()) {
				return i + 1;
			}
		}
		return filters.size() == 1 ? 1 : 0;
	}

	@Override
	public SongFiles.FileNewChoice showFileNew() {
		FileNewDialog dialog = new FileNewDialog(getFrame());
		if (!session.unlocked(dialog::showDialog)) {
			return null;
		}
		return new SongFiles.FileNewChoice(dialog.maxTrackLength, dialog.comboMonoOrStereo != 0);
	}

	@Override
	public int showTracksLoad(int trackFrom, int trackNum) {
		return session.unlocked(() -> new TracksLoadDialog(getFrame(), trackFrom, trackNum).showDialog());
	}

	/** {@code SetWindowText("Imported " + fn)}: shown until the next {@code SetRMTTitle()} would change the title anyway (an edit, a load, a save). */
	@Override
	public void songImported(String fileName) {
		importedTitle = "Imported " + fileName;
		getFrame().setTitle(importedTitle);
	}

	@Override
	public SongFiles.ImportModChoice showImportMod(String info, String radio1, String radio2) {
		return session.unlocked(() -> new ImportModDialog(getFrame(), info, radio1, radio2).showDialog());
	}

	@Override
	public SongFiles.ImportTmcChoice showImportTmc(String info) {
		return session.unlocked(() -> new ImportTmcDialog(getFrame(), info).showDialog());
	}

	@Override
	public boolean showImportFinished(boolean mod, String info) {
		ExportSettings es = session.exportSettings;
		ImportFinishedDialog dialog = new ImportFinishedDialog(getFrame(), mod, info, mod ? es.importModUnderstood : es.importTmcUnderstood);
		boolean ok = session.unlocked(dialog::showDialog);
		if (mod) {
			es.importModUnderstood = dialog.isUnderstood();
		} else {
			es.importTmcUnderstood = dialog.isUnderstood();
		}
		return ok;
	}

	@Override
	public SongFiles.StrippedRmtChoice showExportStrippedRmt(SongFiles.ModuleDescription stripped, SongFiles.ModuleDescription withSfx, String filename) {
		return session.unlocked(() -> new ExportStrippedRmtDialog(getFrame(), session, stripped, withSfx, filename).showDialog());
	}

	@Override
	public SongFiles.AsmChoice showExportAsm() {
		return session.unlocked(() -> new ExportAsmDialog(getFrame(), session.exportSettings.prefixForAllAsmLabels).showDialog());
	}

	@Override
	public org.atari.raster.rmt.model.AsmFileExporter.RelocatableAsmExportParams showExportRelocatableAsm(SongFiles.ModuleDescription stripped, SongFiles.ModuleDescription withSfx) {
		return session.unlocked(() -> new ExportRelocatableAsmDialog(getFrame(), session, stripped, withSfx).showDialog());
	}

	@Override
	public SongFiles.SapChoice showExportSap(org.atari.raster.rmt.model.SapFile sapFile, String subsongs) {
		return session.unlocked(() -> new ExportSapDialog(getFrame(), sapFile, subsongs).showDialog());
	}

	@Override
	public SongFiles.XexChoice showExportXex(String text, String speedInfo) {
		return session.unlocked(() -> new ExportXexDialog(getFrame(), session.exportSettings, text, speedInfo).showDialog());
	}

	// ---- SongDialogs.Host: the editing dialogs ----

	@Override
	public SongDialogs.InsertCopyChoice showInsertCopyOrClone(int lineFrom, int lineTo, int lineInto) {
		return session.unlocked(() -> new InsertCopyOrCloneDialog(getFrame(), lineFrom, lineTo, lineInto).showDialog());
	}

	@Override
	public org.atari.raster.rmt.model.Song.InstrChangeParams showInstrumentChange(int instr, int onlyTrack, int onlySongLine) {
		return session.unlocked(() -> new InstrumentChangeDialog(getFrame(), session, instr, onlyTrack, onlySongLine).showDialog());
	}

	@Override
	public SongDialogs.TracksOrderChoice showTracksOrder(String songLineFrom, String songLineTo) {
		return session.unlocked(() -> new TracksOrderDialog(getFrame(), session.tracks4_8, songLineFrom, songLineTo).showDialog());
	}

	@Override
	public int showChangeMaxTrackLength(String info, int maxTrackLength) {
		return session.unlocked(() -> new ChangeMaxTrackLengthDialog(getFrame(), info, maxTrackLength).showDialog());
	}

	@Override
	public int showRenumberTracks() {
		return session.unlocked(() -> RenumberDialogs.tracks(getFrame()).showDialog());
	}

	@Override
	public int showRenumberInstruments() {
		return session.unlocked(() -> RenumberDialogs.instruments(getFrame()).showDialog());
	}

	@Override
	public void songLayoutChanged() {
		updateMinimumSize();
	}

	@Override
	public boolean showBlockEffect(org.atari.raster.rmt.model.Track track, org.atari.raster.rmt.model.Track original, int bfro, int bto, int ainstr, boolean all, String info) {
		return session.unlocked(() -> new BlockEffectDialog(getFrame(), session, track, original, bfro, bto, ainstr, all, info).showDialog());
	}

	/** Shows the window and starts the display timer and the sound ({@code CRmtApp::InitInstance()}'s {@code ShowWindow} plus {@code CRmtView::OnInitialUpdate()}'s {@code SetTimer}, {@code InitSound} and {@code ChangeTimer}). */
	public void show() {
		getFrame().setVisible(true);
		trackerPanel.requestFocusInWindow();
		trackerPanel.startDisplayTimer();
		audioEngine.start();
		// Initialise MIDI (OnInitialUpdate)
		session.midi.midiInit();
		session.midi.midiOn();
	}

	public AudioEngine getAudioEngine() {
		return audioEngine;
	}
}
