package org.atari.raster.rmt.model;

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

	/** How far above the jar the content root may sit: three levels out of a macOS bundle ({@code Contents/app} -> {@code Contents} -> {@code rmt.app} -> beside it), one more for air. */
	private static final int CONTENT_ROOT_SEARCH_DEPTH = 4;

	/**
	 * The folder holding the user's own material - {@code songs/},
	 * {@code instruments/}, {@code exports/} - and the two ini files, or
	 * {@code null} when there is none.
	 *
	 * <p>It sits <em>next to the application</em>, not inside it, because a
	 * file chooser must reach it and the program must be able to write there;
	 * neither holds inside a macOS bundle or a Program Files install
	 * (plans/30_DISTRIBUTION_LAYOUT_PLAN.md). The jar is one level below it
	 * on Windows ({@code rmt/app}), two on Linux ({@code rmt/lib/app}) and
	 * three out of a macOS bundle, so the search simply walks up from the
	 * program folder, the install folder and the working directory - each
	 * also tried with its {@code rmt/} sub-folder, which is how a checkout
	 * resolves - and takes the first folder that has {@code songs/} or
	 * {@code rmt.ini}.
	 *
	 * <p>{@code null} is the macOS case of an {@code rmt.app} dragged to
	 * Applications, leaving its content behind: the program still runs,
	 * because the binaries travel inside the application image, and
	 * {@link #getConfigFolder()} then keeps the settings per user.
	 */
	public static Path getContentRoot() {
		// The working directory is deliberately not searched: a program started from
		// some folder that happens to hold songs must not adopt it as its content.
		Path found = findContentRoot("songs");
		return found != null ? found : findContentRoot("rmt.ini");
	}

	/**
	 * The first folder at or above the program folder or the install folder -
	 * each also tried as its {@code rmt/} sub-folder - that holds
	 * {@code marker}. {@code songs} is asked for first and {@code rmt.ini}
	 * only afterwards, so a stray ini file somewhere above the program cannot
	 * outrank the real content folder.
	 */
	private static Path findContentRoot(String marker) {
		for (Path base : new Path[] { folder, installFolder }) {
			if (base == null) {
				continue;
			}
			Path candidate = base.toAbsolutePath().normalize();
			for (int level = 0; level <= CONTENT_ROOT_SEARCH_DEPTH && candidate != null; level++) {
				if (Files.exists(candidate.resolve(marker))) {
					return candidate;
				}
				Path inRmt = candidate.resolve("rmt");
				if (Files.exists(inRmt.resolve(marker))) {
					return inRmt;
				}
				candidate = candidate.getParent();
			}
		}
		return null;
	}

	/**
	 * Where {@code rmt.ini} and {@code tuning.ini} are read and written: the
	 * {@link #getContentRoot() content root} when there is one - the program
	 * stays portable, settings travel with the folder - and otherwise the
	 * per-user configuration folder of the operating system, created on
	 * demand. The latter is the fallback for an application that was moved
	 * away from its content.
	 */
	public static Path getConfigFolder() {
		Path contentRoot = getContentRoot();
		if (contentRoot != null) {
			return contentRoot;
		}
		return getUserConfigFolder();
	}

	/** {@code %APPDATA%\\RMT}, {@code ~/Library/Application Support/RMT} or {@code $XDG_CONFIG_HOME/rmt} - whether or not it exists yet. */
	public static Path getUserConfigFolder() {
		String os = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT);
		Path home = Path.of(System.getProperty("user.home", "."));
		if (os.contains("win")) {
			String appData = System.getenv("APPDATA");
			return (appData != null && !appData.isEmpty() ? Path.of(appData) : home).resolve("RMT");
		}
		if (os.contains("mac")) {
			return home.resolve("Library").resolve("Application Support").resolve("RMT");
		}
		String xdg = System.getenv("XDG_CONFIG_HOME");
		return (xdg != null && !xdg.isEmpty() ? Path.of(xdg) : home.resolve(".config")).resolve("rmt");
	}
}
