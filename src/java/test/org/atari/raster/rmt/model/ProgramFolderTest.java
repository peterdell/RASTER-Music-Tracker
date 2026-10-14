package org.atari.raster.rmt.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProgramFolderTest {

	@Test
	void aCheckoutResolvesResourcesThroughItsRmtSubFolder() {
		// the tests run from the repository root, where rmt/resources exists
		Path driver = ProgramFolder.getResourceFilePath(Path.of("resources", "drivers"), "rmt_driver_v6.obx");
		assertEquals(Path.of("rmt").toAbsolutePath(), ProgramFolder.getResourceRoot().toAbsolutePath());
		assertTrue(Files.isRegularFile(driver), driver.toString());
	}

	@Test
	void anInstalledLayoutResolvesFromTheProgramFolderItself(@TempDir Path dir) throws IOException {
		Path original = ProgramFolder.get();
		try {
			Files.createDirectories(dir.resolve("resources").resolve("drivers"));
			Files.createDirectories(dir.resolve("docs"));
			ProgramFolder.set(dir);
			assertEquals(dir, ProgramFolder.getResourceRoot());
			assertEquals(dir.resolve("docs").resolve("rmt_en.html"), ProgramFolder.getResourceFilePath(Path.of("docs"), "rmt_en.html"));
		} finally {
			ProgramFolder.set(original);
		}
	}

	@Test
	void aConfigurationOnlyProgramFolderFindsTheInstalledResources(@TempDir Path dir) throws IOException {
		// -Drmt.config.dir=<config folder> with the jar installed in the rmt/ layout elsewhere
		Path original = ProgramFolder.get();
		try {
			Path config = Files.createDirectory(dir.resolve("config"));
			Path install = Files.createDirectories(dir.resolve("install").resolve("resources")).getParent();
			ProgramFolder.set(config);
			ProgramFolder.setInstallFolder(install);
			assertEquals(install, ProgramFolder.getResourceRoot());
		} finally {
			ProgramFolder.setInstallFolder(null);
			ProgramFolder.set(original);
		}
	}

	@Test
	void aProgramFolderWithoutResourcesFallsBackToTheWorkingDirectorysCheckout(@TempDir Path dir) {
		// java -jar target/rmt.jar from a checkout: the jar's folder has no resources, the working directory's rmt/ has
		Path original = ProgramFolder.get();
		try {
			ProgramFolder.set(dir);
			assertEquals(Path.of("rmt").toAbsolutePath().normalize(), ProgramFolder.getResourceRoot().toAbsolutePath().normalize());
			assertTrue(RmtAtariBinaries.getTrackerDriverBinary(TrackerDriverVersion.PATCH16).length > 0);
		} finally {
			ProgramFolder.set(original);
		}
	}
	// --- the content root: the user's own material, next to the application ---
	// (plans/30_DISTRIBUTION_LAYOUT_PLAN.md - the jar sits one level below it on
	// Windows, two on Linux and three out of a macOS bundle)

	/** Builds an unpacked distribution whose jar sits at {@code jarPath} below the archive root, and points the program folder there. */
	private static Path distribution(Path dir, String jarPath) throws IOException {
		Path archiveRoot = dir.resolve("rmt-java");
		Path appFolder = archiveRoot.resolve(jarPath);
		Files.createDirectories(appFolder.resolve("resources").resolve("drivers"));
		Files.createDirectories(archiveRoot.resolve("songs"));
		Files.createDirectories(archiveRoot.resolve("instruments"));
		Files.writeString(archiveRoot.resolve("rmt.ini"), "# RMT CONFIGURATION FILE");
		ProgramFolder.set(appFolder);
		ProgramFolder.setInstallFolder(appFolder);
		return archiveRoot;
	}

	@Test
	void theContentRootIsFoundAboveTheJarOnWindows(@TempDir Path dir) throws IOException {
		Path original = ProgramFolder.get();
		try {
			Path archiveRoot = distribution(dir, "app"); // rmt/app/rmt.jar
			assertEquals(archiveRoot, ProgramFolder.getContentRoot());
			assertEquals(archiveRoot, ProgramFolder.getConfigFolder(), "the settings stay next to the program");
			assertTrue(ProgramFolder.getResourceRoot().endsWith("app"), "the Atari binaries stay inside the application image");
		} finally {
			ProgramFolder.set(original);
			ProgramFolder.setInstallFolder(null);
		}
	}

	@Test
	void theContentRootIsFoundTwoLevelsAboveTheJarOnLinux(@TempDir Path dir) throws IOException {
		Path original = ProgramFolder.get();
		try {
			Path archiveRoot = distribution(dir, "lib/app"); // rmt/lib/app/rmt.jar
			assertEquals(archiveRoot, ProgramFolder.getContentRoot());
		} finally {
			ProgramFolder.set(original);
			ProgramFolder.setInstallFolder(null);
		}
	}

	@Test
	void theContentRootIsFoundOutsideAMacOsBundle(@TempDir Path dir) throws IOException {
		Path original = ProgramFolder.get();
		try {
			Path archiveRoot = distribution(dir, "rmt.app/Contents/app");
			assertEquals(archiveRoot, ProgramFolder.getContentRoot(), "three levels out of the bundle");
		} finally {
			ProgramFolder.set(original);
			ProgramFolder.setInstallFolder(null);
		}
	}

	/** An rmt.app dragged to Applications leaves its content behind: the program still runs, the settings go per user. */
	@Test
	void withoutAContentRootTheSettingsGoToTheUserFolder(@TempDir Path dir) throws IOException {
		Path original = ProgramFolder.get();
		try {
			Path appFolder = dir.resolve("Applications").resolve("rmt.app").resolve("Contents").resolve("app");
			Files.createDirectories(appFolder.resolve("resources").resolve("drivers"));
			ProgramFolder.set(appFolder);
			ProgramFolder.setInstallFolder(appFolder);

			assertNull(ProgramFolder.getContentRoot(), "nothing beside the bundle");
			assertEquals(ProgramFolder.getUserConfigFolder(), ProgramFolder.getConfigFolder());
			assertTrue(ProgramFolder.getResourceRoot().endsWith("app"), "the binaries still resolve, so the program runs");
		} finally {
			ProgramFolder.set(original);
			ProgramFolder.setInstallFolder(null);
		}
	}

	@Test
	void theUserConfigFolderFollowsTheOperatingSystem() {
		Path folder = ProgramFolder.getUserConfigFolder();
		assertTrue(folder.isAbsolute(), folder.toString());
		String os = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT);
		if (os.contains("win")) {
			assertTrue(folder.endsWith("RMT"), folder.toString());
		} else if (os.contains("mac")) {
			assertTrue(folder.endsWith(Path.of("Library", "Application Support", "RMT")), folder.toString());
		} else {
			assertTrue(folder.endsWith("rmt"), folder.toString());
		}
	}

	@Test
	void aCheckoutFindsItsContentInTheRmtFolder() {
		// the tests run from the repository root, where rmt/songs exists
		assertEquals(Path.of("rmt").toAbsolutePath(), ProgramFolder.getContentRoot());
	}
}
