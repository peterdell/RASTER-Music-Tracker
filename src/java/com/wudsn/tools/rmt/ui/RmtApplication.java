package com.wudsn.tools.rmt.ui;

import java.awt.EventQueue;
import java.io.IOException;
import java.nio.file.Path;

import javax.swing.JOptionPane;
import javax.swing.UIManager;

/**
 * Application entry point - the port of {@code CRmtApp} (Rmt.cpp): build
 * the model composition root ({@link RmtSession}), create the window, open
 * the file named on the command line, show the window and start the
 * display timer. Follows the same bootstrap shape as dis6502's
 * {@code Dis6502.main} (everything on the Swing event dispatch thread,
 * native look and feel with a silent fallback).
 *
 * <p>Command line so far: an optional {@code .rmt} file path (C++'s
 * {@code CCommandLineInfo::FileOpen}); the {@code /SCRIPT} and
 * {@code /TEST} switches come with B9.
 */
public final class RmtApplication {

	private RmtApplication() {
	}

	public static void main(String[] args) {
		EventQueue.invokeLater(() -> {
			setNativeLookAndFeel();

			RmtSession session = new RmtSession();
			// Stopgap until B6 reads rmt.ini: -Drmt.scaling=100..300 sets RMT's own scaling option
			session.options.scalingPercentage = Integer.getInteger("rmt.scaling", session.options.scalingPercentage);
			RmtMainWindow window = new RmtMainWindow(session);

			if (args.length > 0) {
				openFile(session, window, Path.of(args[0]));
			}

			window.show();
		});
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
