package com.wudsn.tools.rmt.model;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * C++'s {@code g_prgpath} + {@code GetResourceFilePath()} (Global.cpp): the
 * folder the program runs from, which the C++ build fills with the
 * checked-in {@code rmt/} layout ({@code resources/drivers}, {@code
 * resources/players}, {@code docs}, {@code instruments}, {@code songs},
 * {@code rmt.ini}, {@code tuning.ini}). The application sets it once at
 * start-up ({@link #set}); the default is the working directory, which is
 * what every test and the development run use.
 *
 * <p>{@link #getResourceRoot} adds one convenience for a checkout: when the
 * program folder has no {@code resources/}, the {@code rmt/} sub-folder of
 * the program folder or of the working directory serves - so {@code mvn
 * test} from the repository root and {@code java -jar target/rmt.jar} from
 * a checkout find the Atari binaries and the docs without any
 * configuration, while an installed copy (the jar inside the {@code rmt/}
 * layout) resolves exactly as {@code Rmt.exe} does. A process-wide setting,
 * as {@code g_prgpath} is;
 * the one such global this port keeps, because it is configuration of the
 * process rather than model state.
 */
public final class ProgramFolder {

	private static volatile Path folder = Path.of(System.getProperty("user.dir", "."));
	private static volatile Path installFolder;

	private ProgramFolder() {
	}

	/** Sets the program folder (the application does this before anything reads a resource). */
	public static void set(Path programFolder) {
		folder = programFolder;
	}

	/** The folder the program is installed in (the jar's folder), searched for resources after the program folder - for a {@code -Drmt.config.dir} that names a configuration-only folder. */
	public static void setInstallFolder(Path installFolder) {
		ProgramFolder.installFolder = installFolder;
	}

	/** {@code g_prgpath}. */
	public static Path get() {
		return folder;
	}

	/**
	 * The folder holding {@code resources/} and {@code docs/}: the first of
	 * the program folder, the install folder and the working directory - each
	 * followed by its {@code rmt/} sub-folder - that has a {@code resources/}
	 * folder. An installed copy resolves as {@code Rmt.exe} does, a checkout
	 * works whether the jar runs from {@code target/} or the classes folder,
	 * and a configuration-only {@code -Drmt.config.dir} still finds the
	 * installed resources. Without any match the program folder itself, so a
	 * missing file is reported there.
	 */
	public static Path getResourceRoot() {
		Path root = folder;
		Path workingDirectory = Path.of(System.getProperty("user.dir", "."));
		for (Path base : new Path[] { root, installFolder, workingDirectory }) {
			if (base == null) {
				continue;
			}
			for (Path candidate : new Path[] { base, base.resolve("rmt") }) {
				if (Files.isDirectory(candidate.resolve("resources"))) {
					return candidate;
				}
			}
		}
		return root;
	}

	/** {@code GetResourceFilePath(relativeFolderPath, fileName)}: {@code <resource root>/<relativeFolderPath>/<fileName>}. */
	public static Path getResourceFilePath(Path relativeFolderPath, String fileName) {
		return getResourceRoot().resolve(relativeFolderPath).resolve(fileName);
	}
}
