package org.atari.raster.rmt.ui;

import java.awt.EventQueue;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.CodeSource;

import javax.swing.JOptionPane;
import javax.swing.UIManager;

import org.atari.raster.rmt.model.ProgramFolder;

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
 * <p>Command line ({@link RmtCommandLine}): an optional song file path
 * (C++'s {@code CCommandLineInfo::FileOpen}); the C++ {@code /TEST} switch
 * and the developer routines behind {@code /TEST}/{@code /SCRIPT} are not
 * ported - {@code /SCRIPT} is reserved for the scripting feature (see
 * {@code plans/20_JAVA_B9_PLAN.md}) - both are rejected with C++'s "Invalid
 * Command Line Parameter" box. The system property {@code rmt.config.dir}
 * overrides the program folder ({@link ProgramFolder}: {@code rmt.ini},
 * {@code tuning.ini}, the Atari binaries under {@code resources/}, the
 * local help under {@code docs/}).
 */
public final class RmtApplication {

	/** System property naming the program folder ({@code rmt.ini}/{@code tuning.ini}, {@code resources/}, {@code docs/}); default: see {@link #getProgramFolder()}. */
	public static final String CONFIG_DIR_PROPERTY = "rmt.config.dir";

	/** Environment variable naming the output folder of a {@code /SCRIPT} run (both programs honour it; it overrides the script's {@code set output}). */
	public static final String SCRIPT_OUTPUT_VARIABLE = "RMT_SCRIPT_OUTPUT";

	private RmtApplication() {
	}

	public static void main(String[] args) {
		RmtCommandLine.Result commandLine = RmtCommandLine.parse(args);
		if (commandLine.rejection() == null && commandLine.scriptFile() != null) {
			System.exit(runScript(commandLine.scriptFile()));
		}
		EventQueue.invokeLater(() -> {
			setNativeLookAndFeel();

			if (commandLine.rejection() != null) {
				JOptionPane.showMessageDialog(null, commandLine.rejection(), RmtCommandLine.INVALID_PARAMETER_TITLE, JOptionPane.ERROR_MESSAGE);
				System.exit(1);
			}

			Path programFolder = getProgramFolder();
			ProgramFolder.set(programFolder); // g_prgpath
			ProgramFolder.setInstallFolder(getInstallFolder());
			RmtSession session = new RmtSession();
			RmtConfig config = new RmtConfig(programFolder);
			RmtMainWindow window = new RmtMainWindow(session, config, RmtWindowPreferences.forUser());

			if (commandLine.file() != null) {
				openFile(window, commandLine.file());
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
		return getInstallFolder();
	}

	/** The folder holding the jar this class runs from, or - from a classes directory during development - the working directory. */
	public static Path getInstallFolder() {
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

	/**
	 * {@code /SCRIPT:<file>}: the script on a fresh session, headless (no
	 * window, no Swing at all - so it runs without a display), with the
	 * program folder's {@code rmt.ini}/{@code tuning.ini} read as the window
	 * would read them (the tuning shapes the exports); returns the exit code.
	 */
	static int runScript(Path scriptFile) {
		System.setProperty("java.awt.headless", "true");
		Path programFolder = getProgramFolder();
		ProgramFolder.set(programFolder); // g_prgpath
		ProgramFolder.setInstallFolder(getInstallFolder());
		RmtSession session = new RmtSession();
		org.atari.raster.rmt.script.ScriptRunner runner = new org.atari.raster.rmt.script.ScriptRunner(session, System.out, System.err);
		RmtConfig config = new RmtConfig(programFolder);
		config.readRMTConfig(session);
		config.readTuningConfig(session);
		String outputOverride = System.getenv(SCRIPT_OUTPUT_VARIABLE);
		if (outputOverride != null && !outputOverride.isEmpty()) {
			runner.setOutputFolder(Path.of(outputOverride)); // the cross-program comparison runs one script into two folders
		}
		return runner.run(scriptFile);
	}

	/** {@code CRmtApp::InitInstance()}'s {@code g_Song.FileOpen(cmdInfo.m_strFileName, FALSE)}: the format follows the extension, errors come as message boxes. */
	private static void openFile(RmtMainWindow window, Path path) {
		window.getCommands().getSongFiles().fileOpen(path, false);
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
