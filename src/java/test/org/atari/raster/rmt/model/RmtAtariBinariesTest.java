package org.atari.raster.rmt.model;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
 * The Atari binaries come from inside the jar, and a file of the same name
 * next to the program overrides it - the way a driver is tested by hand
 * (plans/30_DISTRIBUTION_LAYOUT_PLAN.md, D1).
 */
class RmtAtariBinariesTest {

	private final Path originalProgramFolder = ProgramFolder.get();

	@AfterEach
	void restoreProgramFolder() {
		ProgramFolder.set(originalProgramFolder);
	}

	@Test
	void everyShippedBinaryIsInTheJar() {
		// The program folder of this test run has a resources folder (the checkout's rmt/), so
		// point it at a folder that has none: what comes back can only be the bundled copy.
		ProgramFolder.set(Path.of("target"));
		for (TrackerDriverVersion version : TrackerDriverVersion.getValues()) {
			byte[] binary = RmtAtariBinaries.getTrackerDriverBinary(version);
			if (version == TrackerDriverVersion.NONE) {
				assertNull(binary, "there is no rmt_driver_v0.obx");
			} else {
				assertNotNull(binary, version.getId() + " is not bundled");
				assertTrue(binary.length > 1000, version.getId() + " looks too small: " + binary.length);
			}
		}
		assertNotNull(RmtAtariBinaries.getVUPlayerBinary());
	}

	@Test
	void theBundledBinaryIsTheCheckedInFile() throws IOException {
		ProgramFolder.set(Path.of("target"));
		byte[] bundled = RmtAtariBinaries.getTrackerDriverBinary(TrackerDriverVersion.PATCH16);
		byte[] checkedIn = Files.readAllBytes(Path.of("rmt", "resources", "drivers", "rmt_driver_v6.obx"));
		assertArrayEquals(checkedIn, bundled, "the jar must carry the same bytes Rmt.exe reads from disk");
	}

	@Test
	void aFileNextToTheProgramOverridesTheBundledBinary(@TempDir Path dir) throws IOException {
		Path drivers = dir.resolve("resources").resolve("drivers");
		Files.createDirectories(drivers);
		byte[] own = new byte[] { (byte) 0xFF, (byte) 0xFF, 0x00, 0x20, 0x01, 0x20, 0x42, 0x43 };
		Files.write(drivers.resolve("rmt_driver_v6.obx"), own);
		ProgramFolder.set(dir);

		assertArrayEquals(own, RmtAtariBinaries.getTrackerDriverBinary(TrackerDriverVersion.PATCH16), "the hand-dropped driver wins");
		// its neighbours still come from the jar
		byte[] patch8 = RmtAtariBinaries.getTrackerDriverBinary(TrackerDriverVersion.PATCH8);
		assertNotNull(patch8);
		assertFalse(java.util.Arrays.equals(own, patch8));

		Files.delete(drivers.resolve("rmt_driver_v6.obx")); // deleting it goes back to the shipped one
		assertArrayEquals(Files.readAllBytes(Path.of("rmt", "resources", "drivers", "rmt_driver_v6.obx")),
				RmtAtariBinaries.getTrackerDriverBinary(TrackerDriverVersion.PATCH16));
	}

	@Test
	void theVuPlayerCanBeOverriddenTheSameWay(@TempDir Path dir) throws IOException {
		Path players = dir.resolve("resources").resolve("players");
		Files.createDirectories(players);
		byte[] own = new byte[] { (byte) 0xFF, (byte) 0xFF, 0x00, 0x30, 0x03, 0x30, 1, 2, 3, 4 };
		Files.write(players.resolve("vu_player_v2.obx"), own);
		ProgramFolder.set(dir);

		assertArrayEquals(own, RmtAtariBinaries.getVUPlayerBinary());
	}

	/** The name an error message shows, which is also where a replacement goes. */
	@Test
	void theResourceNameIsThePathBesideTheProgram() {
		assertTrue(RmtAtariBinaries.getResourceName("drivers", "rmt_driver_v6.obx").equals("resources/drivers/rmt_driver_v6.obx"));
		ProgramFolder.set(Path.of("some", "where"));
		assertTrue(RmtAtariBinaries.getOverridePath("players", "vu_player_v2.obx").endsWith(Path.of("resources", "players", "vu_player_v2.obx")));
	}
}
