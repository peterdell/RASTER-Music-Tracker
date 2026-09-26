package com.wudsn.tools.rmt.ui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.io.InputStream;

import javax.imageio.ImageIO;
import javax.swing.JFrame;
import javax.swing.WindowConstants;

import com.wudsn.tools.base.gui.MainWindow;
import com.wudsn.tools.base.gui.StatusBar;

/**
 * The application's main window - the port of {@code CMainFrame}
 * (MainFrm.cpp), on WUDSN Base's {@link MainWindow} (a {@link JFrame} that
 * tracks its un-maximized geometry for the preferences store) with a
 * {@link StatusBar} at the bottom and the {@link TrackerPanel} filling the
 * rest.
 *
 * <p>Scope so far (batch B1): frame, status bar, panel, the mono/stereo
 * minimum size of {@code CMainFrame::OnGetMinMaxInfo()}, and the title of
 * {@code CSong::SetRMTTitle()}. Menus, the two toolbars with the
 * skip-lines combo box and the accelerators come with B5; saving the
 * geometry through {@code MainWindowPreferences} and the unsaved-changes
 * prompt on close ({@code WarnUnsavedChanges}) with B6/B9 - until then
 * closing the window simply exits.
 */
public final class RmtMainWindow {

	/** What {@code CRmtApp::GetVersionAndBuild()} produces for the C++ build ("RASTER Music Tracker 1.35 (Sep 25 2026 01:30:01)"); the Java port carries no build stamp yet. */
	public static final String VERSION_AND_BUILD = "RASTER Music Tracker 1.35 (Java)";

	private final RmtSession session;
	private final MainWindow mainWindow = new MainWindow();
	private final StatusBar statusBar = new StatusBar();
	private final TrackerPanel trackerPanel;

	public RmtMainWindow(RmtSession session) {
		this.session = session;
		this.trackerPanel = new TrackerPanel(session);

		JFrame frame = mainWindow.getFrame();
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

		frame.setLayout(new BorderLayout());
		frame.add(trackerPanel, BorderLayout.CENTER);
		frame.add(statusBar.getComponent(), BorderLayout.SOUTH);
		frame.pack();
		frame.setLocationRelativeTo(null);

		updateMinimumSize();
		updateTitle();
	}

	public JFrame getFrame() {
		return mainWindow.getFrame();
	}

	public TrackerPanel getTrackerPanel() {
		return trackerPanel;
	}

	/** {@code CMainFrame::OnGetMinMaxInfo()}: 800x600 for a mono song, 1120x600 for a stereo one. */
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
		getFrame().setTitle(title);
	}

	/** Shows the window and starts the display timer ({@code CRmtApp::InitInstance()}'s {@code ShowWindow} plus {@code CRmtView::OnInitialUpdate()}'s {@code SetTimer}). */
	public void show() {
		getFrame().setVisible(true);
		trackerPanel.requestFocusInWindow();
		trackerPanel.startDisplayTimer();
	}
}
