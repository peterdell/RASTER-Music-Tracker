package org.atari.raster.rmt.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The Atari binaries are files under {@code resources/} next to the
 * program, so one can be replaced by hand to test a driver
 * (plans/30_DISTRIBUTION_LAYOUT_PLAN.md, decision 5.4).
 */
class RmtAtariBinariesTest {

	private final Path originalProgramFolder = ProgramFolder.get();

	@AfterEach
	void restoreProgramFolder() {
		ProgramFolder.set(originalProgramFolder);
	}

	@Test
	void everyShippedDriverAndThePlayerAreFound() {
		for (TrackerDriverVersion version : TrackerDriverVersion.getValues()) {
			byte[] binary = RmtAtariBinaries.getTrackerDriverBinary(version);
			if (version == TrackerDriverVersion.NONE) {
				assertNull(binary, "there is no rmt_driver_v0.obx");
			} else {
				assertNotNull(binary, version.getId() + " was not found");
				assertTrue(binary.length > 1000, version.getId() + " looks too small: " + binary.length);
			}
		}
		assertNotNull(RmtAtariBinaries.getVUPlayerBinary());
	}

	@Test
	void theBinaryIsTheCheckedInFileRmtExeAlsoReads() throws IOException {
		assertArrayEquals(Files.readAllBytes(Path.of("rmt", "resources", "drivers", "rmt_driver_v6.obx")),
				RmtAtariBinaries.getTrackerDriverBinary(TrackerDriverVersion.PATCH16));
		assertArrayEquals(Files.readAllBytes(Path.of("rmt", "resources", "players", "vu_player_v2.obx")),
				RmtAtariBinaries.getVUPlayerBinary());
	}

	/** Replacing a driver by hand: the file next to the program is what is loaded. */
	@Test
	void aDriverReplacedByHandIsTheOneThatLoads(@TempDir Path dir) throws IOException {
		Path drivers = dir.resolve("resources").resolve("drivers");
		Files.createDirectories(drivers);
		byte[] own = new byte[] { (byte) 0xFF, (byte) 0xFF, 0x00, 0x20, 0x01, 0x20, 0x42, 0x43 };
		Files.write(drivers.resolve("rmt_driver_v6.obx"), own);
		ProgramFolder.set(dir);

		assertArrayEquals(own, RmtAtariBinaries.getTrackerDriverBinary(TrackerDriverVersion.PATCH16));
		assertNull(RmtAtariBinaries.getTrackerDriverBinary(TrackerDriverVersion.PATCH8), "only the file that is there can be loaded");

		Files.delete(drivers.resolve("rmt_driver_v6.obx"));
		assertNull(RmtAtariBinaries.getTrackerDriverBinary(TrackerDriverVersion.PATCH16));
	}

	/** The path an error message shows, which is also where a replacement goes. */
	@Test
	void thePathIsUnderResourcesNextToTheProgram() {
		ProgramFolder.set(Path.of("some", "where"));
		assertTrue(RmtAtariBinaries.getPath("players", "vu_player_v2.obx").endsWith(Path.of("resources", "players", "vu_player_v2.obx")));
		assertTrue(RmtAtariBinaries.getPath("drivers", "rmt_driver_v6.obx").endsWith(Path.of("resources", "drivers", "rmt_driver_v6.obx")));
	}

	/** A driver that is not there must be reported, not played as silence. */
	@Test
	void aMissingDriverIsReportedWhenTheVersionIsSwitched(@TempDir Path dir) throws IOException {
		org.atari.raster.rmt.ui.RmtSession session = new org.atari.raster.rmt.ui.RmtSession();
		java.util.List<String> warnings = new java.util.ArrayList<>();
		session.messages.setHandler(new Messages.Handler() {
			@Override
			public void showError(String title, String message) {
			}

			@Override
			public void showWarning(String title, String message) {
				warnings.add(message);
			}

			@Override
			public void showInformation(String title, String message) {
			}

			@Override
			public MessageAnswer askQuestion(String title, String message, MessageButtons buttons) {
				return MessageAnswer.YES;
			}
		});

		Files.createDirectories(dir.resolve("resources").resolve("drivers")); // the folder is there, the driver is not
		ProgramFolder.set(dir);
		session.setTrackerDriverVersion(TrackerDriverVersion.PATCH8);

		assertEquals(1, warnings.size(), "the silent failure must be reported");
		assertTrue(warnings.get(0).contains("rmt_driver_v5.obx"), warnings.get(0));
		assertTrue(warnings.get(0).contains("silent"), warnings.get(0));
	}
}
