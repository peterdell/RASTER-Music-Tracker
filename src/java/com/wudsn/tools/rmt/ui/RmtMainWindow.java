package com.wudsn.tools.rmt.ui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.io.InputStream;

import javax.imageio.ImageIO;
import javax.swing.BoxLayout;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.WindowConstants;

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
 * <p>Still to come: saving the geometry through {@code MainWindowPreferences}
 * (B6) and the unsaved-changes prompt on close ({@code WarnUnsavedChanges},
 * B9) - until then closing the window simply exits.
 */
public final class RmtMainWindow implements RmtCommands.Host {

	/** What {@code CRmtApp::GetVersionAndBuild()} produces for the C++ build ("RASTER Music Tracker 1.35 (Sep 25 2026 01:30:01)"); the Java port carries no build stamp yet. */
	public static final String VERSION_AND_BUILD = "RASTER Music Tracker 1.35 (Java)";

	private final RmtSession session;
	private final MainWindow mainWindow = new MainWindow();
	private final TrackerPanel trackerPanel;
	private final RmtCommands commands;
	private final RmtMainMenu mainMenu;
	private final RmtToolBars toolBars;
	private final JPanel toolBarPanel = new JPanel();
	/** C++'s status bar shows the command prompts and {@code SetStatusBarText()} messages; a plain label until B9 decides on WUDSN's {@code StatusBar}. */
	private final JLabel statusLine = new JLabel(" ");

	public RmtMainWindow(RmtSession session) {
		this.session = session;
		this.trackerPanel = new TrackerPanel(session);
		this.commands = new RmtCommands(session, trackerPanel.getSongInput(), this);
		this.mainMenu = new RmtMainMenu(this::executeCommand);
		this.toolBars = new RmtToolBars(mainMenu, this::executeCommand, this::skipLinesSelected);

		JFrame frame = mainWindow.getFrame();
		session.messages.setHandler(new SwingMessages(frame));
		frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
		frame.addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosed(WindowEvent e) {
				trackerPanel.stopDisplayTimer();
				System.exit(0); // B9: WarnUnsavedChanges first
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

		trackerPanel.setAcceleratorDispatcher(keyStroke -> {
			RmtCommandId id = mainMenu.lookupAccelerator(keyStroke);
			if (id == null) {
				return false;
			}
			executeCommand(id);
			return true;
		});
		trackerPanel.setIdleAction(this::updateCommandStates);

		applyViewElements();
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

	/** {@code CSong::SetRMTTitle()}: the file name (or the version text for an unnamed song), with " *" appended once there are unsaved changes. */
	public void updateTitle() {
		String filename = session.song.getFilename();
		String title;
		if (filename.isEmpty()) {
			title = session.uiState.changes ? "Noname *" : VERSION_AND_BUILD;
		} else {
			title = session.uiState.changes ? filename + " *" : filename;
		}
		if (!title.equals(getFrame().getTitle())) {
			getFrame().setTitle(title);
		}
	}

	/** {@code CRmtView::ChangeViewElements()}. */
	@Override
	public void applyViewElements() {
		RmtOptions.ViewState view = session.options.view;
		toolBars.mainToolBar.setVisible(view.mainToolbar);
		toolBars.blockToolBar.setVisible(view.blockToolbar);
		toolBarPanel.setVisible(view.mainToolbar || view.blockToolbar);
		statusLine.setVisible(view.statusBar);
		getFrame().revalidate();
	}

	@Override
	public void exit() {
		getFrame().dispose(); // B9: WarnUnsavedChanges, WriteRMTConfig, WriteTuningConfig first
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

	/** Shows the window and starts the display timer ({@code CRmtApp::InitInstance()}'s {@code ShowWindow} plus {@code CRmtView::OnInitialUpdate()}'s {@code SetTimer}). */
	public void show() {
		getFrame().setVisible(true);
		trackerPanel.requestFocusInWindow();
		trackerPanel.startDisplayTimer();
	}
}
