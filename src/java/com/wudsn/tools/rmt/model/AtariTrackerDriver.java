package com.wudsn.tools.rmt.model;

/**
 * Ported from CAtariTrackerDriver (src/cpp/AtariTrackerDriver.h,
 * AtariTrackerDriver.cpp, AtariTrackerDriverCore.cpp) - only the subset
 * {@code AtariTrackerDriverTests.cpp} exercises: the constructor,
 * {@link #getByteAt}, {@link #loadRMTRoutines}, {@link #init},
 * {@link #play}, {@link #setPokey}, {@link #silence}. {@code GetAtari}/
 * {@code SetTrackNoteInstrumentVolume}/{@code SetTrackVolume}/
 * {@code InstrumentTurnOff} (all in {@code AtariTrackerDriverCore.cpp})
 * have no dedicated test coverage here - deferred.
 *
 * <p><b>C++'s {@code C6502::JSR}/{@code CAtari::JSR} are entirely
 * unimplemented</b>, not merely stubbed: they wrap a real 6502 CPU
 * emulator loaded from an external DLL ({@code sa_c6502.dll}), and even in
 * the C++ test build, {@code C6502::JSR} is a link-only no-op stub (see
 * {@code AtariStub.cpp}) that leaves every register/cycle-count argument
 * unchanged. Since every method that would call it - {@link #init},
 * {@link #play}, {@link #setPokey}, {@link #silence} - has no other
 * observable effect once the (already-a-no-op) JSR call is removed, this
 * port simply omits the call rather than modeling a no-op 6502 register
 * calling convention that has nothing left to do. This also makes
 * {@link #play}'s {@code IsSpecialProveMode()} branch dead (both branches
 * would call only no-op JSRs regardless), so it isn't ported either -
 * {@code AtariTrackerDriverTests.cpp} itself only characterizes
 * {@link #play} as not crashing in either mode, matching this.
 */
public final class AtariTrackerDriver {

	private static final int SONGTRACKS = 8;

	private final Atari atari;
	private final int[] rmtInstr = new int[SONGTRACKS];

	public AtariTrackerDriver(Atari atari) {
		this.atari = atari;
	}

	public int getByteAt(int address) {
		return atari.getByteAt(address);
	}

	public int getRmtInstrument(int track) {
		return rmtInstr[track];
	}

	/** Loads the given tracker driver version's binary from disk into the Atari's own memory, returning the number of bytes loaded (0 if no matching file exists). */
	public int loadRMTRoutines(TrackerDriverVersion trackerDriverVersion) {
		byte[] bin = RmtAtariBinaries.getTrackerDriverBinary(trackerDriverVersion);
		if (bin == null) {
			return 0;
		}
		return AtariIO.loadDataAsBinaryFile(bin, atari.getMemory()).bytesRead();
	}

	/** Resets every channel's tracked RMT instrument. Always returns 0 - C++'s 'a' register never changes from its initial 0 once the no-op JSR call is removed (see class javadoc). */
	public int init() {
		for (int i = 0; i < SONGTRACKS; i++) {
			rmtInstr[i] = -1;
		}
		return 0;
	}

	/** No-op - see class javadoc for why C++'s JSR calls (and the branch selecting between them) have nothing left to port. */
	public void play() {
	}

	/** No-op - see class javadoc. */
	public void setPokey() {
	}

	/** No-op - see class javadoc. */
	public void silence() {
	}
}
