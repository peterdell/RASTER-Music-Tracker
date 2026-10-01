package org.atari.raster.rmt.model;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Ported from CRmtAtariBinaries (src/cpp/AtariBinaries.h/.cpp): the Atari
 * binaries the program runs on - the tracker driver of each
 * {@link TrackerDriverVersion} and the VU player the LZSS exports patch.
 *
 * <p>They are bundled inside the jar (the build copies the checked-in
 * {@code rmt/resources}, see {@code pom.xml}), so nothing has to be
 * resolved for them and an installation that was moved or unpacked
 * incompletely still runs. A file of the same name <em>next to the
 * program</em> wins over the bundled one, which is how a driver is tested
 * by hand: drop it into {@code resources/drivers} beside the program and
 * delete it again to go back to the shipped one. The distribution ships no
 * such folder, so the override is empty until someone creates it. C++
 * reads the files from the program folder only; the bytes are the same,
 * which is what the cross-program export comparison checks.
 *
 * <p>C++'s per-version in-memory cache ({@code m_trackerDriverVersionBinary})
 * isn't reproduced - no test depends on a binary being loaded only once,
 * and re-reading 7 KB on each call is simple and safe.
 */
public final class RmtAtariBinaries {

	/** The folder inside the jar, and the folder next to the program that overrides it. */
	public static final String RESOURCES_FOLDER = "resources";

	private RmtAtariBinaries() {
	}

	/** The driver binary's raw bytes, or {@code null} if neither an override nor a bundled copy exists (mirrors C++'s {@code bool} success/failure return). */
	public static byte[] getTrackerDriverBinary(TrackerDriverVersion trackerDriverVersion) {
		return load("drivers", "rmt_driver_v" + trackerDriverVersion.getNumber() + ".obx");
	}

	/** {@code GetVUPlayerBinary()}: the VU player the LZSS/SAP exports patch, or {@code null}. */
	public static byte[] getVUPlayerBinary() {
		return load(VU_PLAYER_FOLDER, VU_PLAYER_FILE);
	}

	static final String VU_PLAYER_FOLDER = "players";
	static final String VU_PLAYER_FILE = "vu_player_v2.obx";

	/** How a binary is named in an error message: the path it would have beside the program, which is also where a replacement goes. */
	public static String getResourceName(String folder, String fileName) {
		return RESOURCES_FOLDER + "/" + folder + "/" + fileName;
	}

	/** The file that overrides the bundled binary, whether or not it exists. */
	public static Path getOverridePath(String folder, String fileName) {
		return ProgramFolder.getResourceFilePath(Path.of(RESOURCES_FOLDER, folder), fileName);
	}

	/** The override next to the program if it is readable, else the copy inside the jar, else {@code null}. */
	static byte[] load(String folder, String fileName) {
		Path override = getOverridePath(folder, fileName);
		if (Files.isRegularFile(override)) {
			try {
				return Files.readAllBytes(override);
			} catch (IOException e) {
				// an unreadable override must not hide the bundled binary
			}
		}
		try (InputStream in = RmtAtariBinaries.class.getResourceAsStream("/" + getResourceName(folder, fileName))) {
			return in == null ? null : in.readAllBytes();
		} catch (IOException e) {
			return null;
		}
	}
}
