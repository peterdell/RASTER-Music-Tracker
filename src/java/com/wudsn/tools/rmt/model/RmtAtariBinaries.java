package com.wudsn.tools.rmt.model;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Ported from CRmtAtariBinaries (src/cpp/AtariBinaries.h/.cpp) - only
 * {@code GetTrackerDriverBinary}. {@code GetVUPlayerBinary} isn't exercised
 * by any test - deferred.
 *
 * <p>The resource path is resolved as C++'s {@code GetResourceFilePath}
 * does, relative to the program folder ({@link ProgramFolder} - the
 * executable's own directory, which the build copies the checked-in
 * {@code rmt/} folder into; in a checkout that folder's {@code rmt/}
 * sub-folder).
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
		Path path = ProgramFolder.getResourceFilePath(Path.of("resources", "drivers"), "rmt_driver_v" + trackerDriverVersion.ordinal() + ".obx");
		try {
			return Files.readAllBytes(path);
		} catch (IOException e) {
			return null;
		}
	}
}
