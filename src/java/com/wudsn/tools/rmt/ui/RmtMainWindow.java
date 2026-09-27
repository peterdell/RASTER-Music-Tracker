package com.wudsn.tools.rmt.ui;

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
public final class RmtMainWindow implements RmtCommands.Host, SongFiles.Host {

	/** What {@code CRmtApp::GetVersionAndBuild()} produces for the C++ build ("RASTER Music Tracker 1.35 (Sep 25 2026 01:30:01)"); the Java port carries no build stamp yet. */
	public static final String VERSION_AND_BUILD = "RASTER Music Tracker 1.35 (Java)";

	private final RmtSession session;
	private final RmtConfig config;
	private final RmtWindowPreferences preferences;
	private final MainWindow mainWindow = new MainWindow();
	private final TrackerPanel trackerPanel;
	private final RmtCommands commands;
	private final RmtMainMenu mainMenu;
	private final RmtToolBars toolBars;
	private final JPanel toolBarPanel = new JPanel();
	/** C++'s status bar shows the command prompts and {@code SetStatusBarText()} messages; a plain label until B9 decides on WUDSN's {@code StatusBar}. */
	private final JLabel statusLine = new JLabel(" ");

	public RmtMainWindow(RmtSession session, RmtConfig config, RmtWindowPreferences preferences) {
		this.session = session;
		this.config = config;
		this.preferences = preferences;
		this.trackerPanel = new TrackerPanel(session);
		this.commands = new RmtCommands(session, trackerPanel.getSongInput(), this, new SongFiles(session, this));
		this.mainMenu = new RmtMainMenu(this::executeCommand);
		this.toolBars = new RmtToolBars(mainMenu, this::executeCommand, this::skipLinesSelected);

		JFrame frame = mainWindow.getFrame();
		session.messages.setHandler(new SwingMessages(frame));
		// CMainFrame::OnClose: the close box goes through ID_FILE_EXIT (which saves everything), the frame only closes once that decided to
		frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
		frame.addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent e) {
				executeCommand(RmtCommandId.FILE_EXIT);
			}

			@Override
			public void windowClosed(WindowEvent e) {
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
		commands.execute(id);
		updateCommandStates();
		updateTitle();
		trackerPanel.requestFocusInWindow();
		trackerPanel.refreshScreen();
	}

	/** {@code CMainFrame::OnSelChangedComboSkipLinesAfterNoteInsert()}. */
	private void skipLinesSelected(int index) {
		if (index >= 0 && index != session.options.skipLinesAfterNoteInsert) {
			session.options.skipLinesAfterNoteInsert = index;
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
		statusLine.setText(feature + " is not available in the Java port yet.");
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
		return new OptionsDialog(getFrame(), session, values).showDialog();
	}

	@Override
	public void rescale() {
		trackerPanel.rescale();
	}

	@Override
	public void showAbout() {
		new AboutDialog(getFrame()).setVisible(true);
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
		if (chooser.showOpenDialog(getFrame()) != JFileChooser.APPROVE_OPTION) {
			return null;
		}
		return new SongFiles.FileChoice(chooser.getSelectedFile().toPath(), filterIndexOf(chooser, filters));
	}

	/** {@code OFN_OVERWRITEPROMPT}: the file (with the filter's extension ensured, as C++ does right after the dialog) must not exist, or the user agrees. */
	@Override
	public SongFiles.FileChoice chooseSaveFile(String title, List<SongFiles.FileFilter> filters, String initialDir, int initialFilterIndex, String suggestedFileName) {
		JFileChooser chooser = createFileChooser(title, filters, initialDir, initialFilterIndex, suggestedFileName);
		while (true) {
			if (chooser.showSaveDialog(getFrame()) != JFileChooser.APPROVE_OPTION) {
				return null;
			}
			int filterIndex = filterIndexOf(chooser, filters);
			Path path = SongFiles.ensureFileExtension(chooser.getSelectedFile().toPath(), filters, filterIndex);
			if (!Files.exists(path) || JOptionPane.showConfirmDialog(getFrame(), path.getFileName() + " already exists.\nDo you want to replace it?", title, JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) == JOptionPane.YES_OPTION) {
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
		if (!dialog.showDialog()) {
			return null;
		}
		return new SongFiles.FileNewChoice(dialog.maxTrackLength, dialog.comboMonoOrStereo != 0);
	}

	@Override
	public int showTracksLoad(int trackFrom, int trackNum) {
		return new TracksLoadDialog(getFrame(), trackFrom, trackNum).showDialog();
	}

	/** {@code SetWindowText("Imported " + fn)}: shown until the next {@code SetRMTTitle()} would change the title anyway (an edit, a load, a save). */
	@Override
	public void songImported(String fileName) {
		importedTitle = "Imported " + fileName;
		getFrame().setTitle(importedTitle);
	}

	@Override
	public SongFiles.ImportModChoice showImportMod(String info, String radio1, String radio2) {
		return new ImportModDialog(getFrame(), info, radio1, radio2).showDialog();
	}

	@Override
	public SongFiles.ImportTmcChoice showImportTmc(String info) {
		return new ImportTmcDialog(getFrame(), info).showDialog();
	}

	@Override
	public boolean showImportFinished(boolean mod, String info) {
		ExportSettings es = session.exportSettings;
		ImportFinishedDialog dialog = new ImportFinishedDialog(getFrame(), mod, info, mod ? es.importModUnderstood : es.importTmcUnderstood);
		boolean ok = dialog.showDialog();
		if (mod) {
			es.importModUnderstood = dialog.isUnderstood();
		} else {
			es.importTmcUnderstood = dialog.isUnderstood();
		}
		return ok;
	}

	@Override
	public SongFiles.StrippedRmtChoice showExportStrippedRmt(SongFiles.ModuleDescription stripped, SongFiles.ModuleDescription withSfx, String filename) {
		return new ExportStrippedRmtDialog(getFrame(), session, stripped, withSfx, filename).showDialog();
	}

	@Override
	public SongFiles.AsmChoice showExportAsm() {
		return new ExportAsmDialog(getFrame(), session.exportSettings.prefixForAllAsmLabels).showDialog();
	}

	@Override
	public com.wudsn.tools.rmt.model.AsmFileExporter.RelocatableAsmExportParams showExportRelocatableAsm(SongFiles.ModuleDescription stripped, SongFiles.ModuleDescription withSfx) {
		return new ExportRelocatableAsmDialog(getFrame(), session, stripped, withSfx).showDialog();
	}

	@Override
	public SongFiles.SapChoice showExportSap(com.wudsn.tools.rmt.model.SapFile sapFile, String subsongs) {
		return new ExportSapDialog(getFrame(), sapFile, subsongs).showDialog();
	}

	@Override
	public SongFiles.XexChoice showExportXex(String text, String speedInfo) {
		return new ExportXexDialog(getFrame(), session.exportSettings, text, speedInfo).showDialog();
	}

	/** Shows the window and starts the display timer ({@code CRmtApp::InitInstance()}'s {@code ShowWindow} plus {@code CRmtView::OnInitialUpdate()}'s {@code SetTimer}). */
	public void show() {
		getFrame().setVisible(true);
		trackerPanel.requestFocusInWindow();
		trackerPanel.startDisplayTimer();
	}
}
