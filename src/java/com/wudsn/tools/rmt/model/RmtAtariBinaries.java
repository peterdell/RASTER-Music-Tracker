package com.wudsn.tools.rmt.model;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Ported from CRmtAtariBinaries (src/cpp/AtariBinaries.h/.cpp) - only
 * {@code GetTrackerDriverBinary}. {@code GetVUPlayerBinary} isn't exercised
 * by any test - deferred.
 *
 * <p>C++ resolves the resource path relative to {@code g_prgpath} (the
 * running executable's own directory, which the build copies the
 * checked-in {@code rmt/} folder into - see {@code Rmt.vcxproj}'s
 * PostBuildEvent). This port instead resolves it relative to the current
 * working directory, matching this whole Java project's own documented
 * convention of running tests as {@code mvn -o test} from the repository
 * root (see CLAUDE.md) - {@code rmt/resources/drivers/} already exists
 * there, unmodified.
 *
 * <p>C++'s per-version in-memory cache ({@code m_trackerDriverVersionBinary})
 * isn't reproduced - no test depends on a binary being loaded only once,
 * and re-reading a small file from disk on each call is simple and safe.
 */
public final class RmtAtariBinaries {

	private RmtAtariBinaries() {
	}

	/** The driver binary's raw bytes, or {@code null} if no matching file exists (mirrors C++'s {@code bool} success/failure return). */
	public static byte[] getTrackerDriverBinary(TrackerDriverVersion trackerDriverVersion) {
		Path path = Path.of("rmt", "resources", "drivers", "rmt_driver_v" + trackerDriverVersion.ordinal() + ".obx");
		try {
			return Files.readAllBytes(path);
		} catch (IOException e) {
			return null;
		}
	}
}
