package com.wudsn.tools.rmt.ui;

import java.awt.EventQueue;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.CodeSource;

import javax.swing.JOptionPane;
import javax.swing.UIManager;

/**
 * Application entry point - the port of {@code CRmtApp} (Rmt.cpp): build
 * the model composition root ({@link RmtSession}), create the window (which
 * reads {@code rmt.ini}/{@code tuning.ini} from the program folder, as
 * {@code CRmtView::OnInitialUpdate()} does), open the file named on the
 * command line, show the window and start the display timer. Follows the
 * same bootstrap shape as dis6502's {@code Dis6502.main} (everything on the
 * Swing event dispatch thread, native look and feel with a silent
 * fallback).
 *
 * <p>Command line so far: an optional {@code .rmt} file path (C++'s
 * {@code CCommandLineInfo::FileOpen}); the {@code /SCRIPT} and
 * {@code /TEST} switches come with B9. The system property
 * {@code rmt.config.dir} overrides the configuration folder.
 */
public final class RmtApplication {

	/** System property naming the folder for {@code rmt.ini}/{@code tuning.ini}; default: the program folder, see {@link #getProgramFolder()}. */
	public static final String CONFIG_DIR_PROPERTY = "rmt.config.dir";

	private RmtApplication() {
	}

	public static void main(String[] args) {
		EventQueue.invokeLater(() -> {
			setNativeLookAndFeel();

			RmtSession session = new RmtSession();
			RmtConfig config = new RmtConfig(getProgramFolder());
			RmtMainWindow window = new RmtMainWindow(session, config, RmtWindowPreferences.forUser());

			if (args.length > 0) {
				openFile(session, window, Path.of(args[0]));
			}

			window.show();
		});
	}

	/**
	 * C++'s {@code g_prgpath} ({@code CRmtApp::InitInstance()}: the folder of
	 * the executable): the {@code rmt.config.dir} property if set; else the
	 * folder holding the jar this class runs from; else - running from a
	 * classes directory during development - the working directory.
	 */
	public static Path getProgramFolder() {
		String override = System.getProperty(CONFIG_DIR_PROPERTY);
		if (override != null && !override.isEmpty()) {
			return Path.of(override);
		}
		try {
			CodeSource source = RmtApplication.class.getProtectionDomain().getCodeSource();
			if (source != null && source.getLocation() != null) {
				Path location = Path.of(source.getLocation().toURI());
				if (Files.isRegularFile(location) && location.getParent() != null) {
					return location.getParent();
				}
			}
		} catch (URISyntaxException | RuntimeException ex) {
			// fall through to the working directory
		}
		return Path.of(System.getProperty("user.dir", "."));
	}

	private static void openFile(RmtSession session, RmtMainWindow window, Path path) {
		boolean loaded;
		String problem = null;
		try {
			loaded = session.openRmtFile(path);
		} catch (IOException ex) {
			loaded = false;
			problem = ex.getMessage();
		}
		if (!loaded) {
			// CSong::FileOpen()'s SendErrorMessage("Open error", ...) - Java has no separate "can't decode" text, so the same box serves both
			JOptionPane.showMessageDialog(window.getFrame(), "Can't open this file: " + path + (problem != null ? "\n" + problem : ""), "Open error", JOptionPane.ERROR_MESSAGE);
		}
		window.updateMinimumSize();
		window.updateTitle();
	}

	/** Same as dis6502's: the host OS's look and feel, or whatever Swing already selected if that fails. */
	static void setNativeLookAndFeel() {
		try {
			UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
		} catch (Exception ex) {
			// Ignore: keep whatever look and feel Swing already selected.
		}
	}
}
