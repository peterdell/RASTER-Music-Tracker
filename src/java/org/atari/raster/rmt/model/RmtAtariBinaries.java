package org.atari.raster.rmt.model;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Ported from CRmtAtariBinaries (src/cpp/AtariBinaries.h/.cpp): the Atari
 * binaries the program runs on - the tracker driver of each
 * {@link TrackerDriverVersion} and the VU player the LZSS exports patch.
 *
 * <p>They are files under {@code resources/} next to the program, exactly
 * as {@code Rmt.exe} reads them, so one of them can be replaced by hand to
 * test a driver: drop it into {@code resources/drivers} and delete it again
 * to go back to the shipped one. They travel inside the application image,
 * next to the jar, so they come along when the application is moved
 * (plans/30_DISTRIBUTION_LAYOUT_PLAN.md, decision 5.4 - they were briefly
 * bundled inside the jar instead, which cost that in-place replacement on
 * the user's platform for no gain the image did not already provide).
 *
 * <p>C++'s per-version in-memory cache ({@code m_trackerDriverVersionBinary})
 * isn't reproduced - no test depends on a binary being loaded only once,
 * and re-reading 7 KB on each call is simple and safe.
 */
public final class RmtAtariBinaries {

	/** The folder next to the program that holds the binaries. */
	public static final String RESOURCES_FOLDER = "resources";

	static final String DRIVERS_FOLDER = "drivers";
	static final String VU_PLAYER_FOLDER = "players";
	static final String VU_PLAYER_FILE = "vu_player_v2.obx";

	private RmtAtariBinaries() {
	}

	/** The driver binary's raw bytes, or {@code null} if no matching file exists (mirrors C++'s {@code bool} success/failure return). */
	public static byte[] getTrackerDriverBinary(TrackerDriverVersion trackerDriverVersion) {
		return load(DRIVERS_FOLDER, "rmt_driver_v" + trackerDriverVersion.getNumber() + ".obx");
	}

	/** {@code GetVUPlayerBinary()}: the VU player the LZSS/SAP exports patch, or {@code null}. */
	public static byte[] getVUPlayerBinary() {
		return load(VU_PLAYER_FOLDER, VU_PLAYER_FILE);
	}

	/** The file a binary is read from - named in an error message, and where a replacement goes. */
	public static Path getPath(String folder, String fileName) {
		return ProgramFolder.getResourceFilePath(Path.of(RESOURCES_FOLDER, folder), fileName);
	}

	private static byte[] load(String folder, String fileName) {
		try {
			return Files.readAllBytes(getPath(folder, fileName));
		} catch (IOException e) {
			return null;
		}
	}
}
