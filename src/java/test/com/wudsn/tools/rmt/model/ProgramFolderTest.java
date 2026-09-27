package com.wudsn.tools.rmt.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
}
