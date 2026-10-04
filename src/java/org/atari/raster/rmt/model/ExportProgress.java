package org.atari.raster.rmt.model;

/**
 * How a long export reports where it is and asks whether it should stop.
 *
 * <p>The register dump ({@link Song#dumpSongToPokeyStream}) is the long part
 * of every export that needs sound, and it runs for as many iterations as the
 * song has frames. The C++ program writes its progress into the status bar
 * from inside that loop (plans/24_EXPORT_SCREEN_UPDATES_PLAN.md, batch E1);
 * the port reports it through this interface instead, so the model stays free
 * of any UI and the same loop can be driven from a worker thread, from a
 * script, or from a test.
 *
 * <p>{@link #NONE} is the default for callers that do not care - every script
 * command and every test - so adding progress reporting changed no existing
 * call site.
 */
public interface ExportProgress {

	/** Reports nothing and never cancels. */
	ExportProgress NONE = new ExportProgress() {
	};

	/**
	 * The number of frames recorded so far. Called from the dump loop a few
	 * times a second, not once per frame, so an implementation may do real
	 * work here (the port hands it to the status line).
	 */
	default void framesRecorded(int frames) {
	}

	/**
	 * Whether the export should stop. Checked once per frame, so this must be
	 * cheap and must not block; the port reads a worker's own cancelled flag.
	 * A dump that stops early leaves an incomplete stream, so the caller has
	 * to treat the export as failed - see
	 * {@code SongFiles.runExportAs}.
	 */
	default boolean isCancelled() {
		return false;
	}
}
