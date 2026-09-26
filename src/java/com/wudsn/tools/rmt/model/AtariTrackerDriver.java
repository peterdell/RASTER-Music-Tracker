package com.wudsn.tools.rmt.model;

/**
 * Ported from CAtariTrackerDriver (src/cpp/AtariTrackerDriver.h,
 * AtariTrackerDriver.cpp, AtariTrackerDriverCore.cpp) - the subset
 * {@code AtariTrackerDriverTests.cpp} exercises directly (the constructor,
 * {@link #getByteAt}, {@link #loadRMTRoutines}, {@link #init}, {@link #play},
 * {@link #setPokey}, {@link #silence}), plus {@link #setTrackNoteInstrumentVolume}/
 * {@link #setTrackVolume}/{@link #instrumentTurnOff}, pulled forward for
 * {@code SongEditingTests.cpp}'s sub-batch 9 ({@code PlayPressedTones}/
 * {@code PlayBeat}). {@code GetAtari} still has no call site here - deferred.
 *
 * <p><b>C++'s {@code C6502::JSR}/{@code CAtari::JSR} are entirely
 * unimplemented</b>, not merely stubbed: they wrap a real 6502 CPU
 * emulator loaded from an external DLL ({@code sa_c6502.dll}), and even in
 * the C++ test build, {@code C6502::JSR} is a link-only no-op stub (see
 * {@code AtariStub.cpp}) that leaves every register/cycle-count argument
 * unchanged. Since every method that would call it - {@link #init},
 * {@link #play}, {@link #setPokey}, {@link #silence}, and now
 * {@link #setTrackNoteInstrumentVolume}/{@link #setTrackVolume}/
 * {@link #instrumentTurnOff} - has no *other* observable effect from the
 * JSR call itself once it's removed, this port omits every JSR call rather
 * than modeling a no-op 6502 register calling convention that has nothing
 * left to do; each of the three new methods keeps every *other* real,
 * observable effect its C++ body has (the {@code g_rmtinstr} bookkeeping,
 * and {@link #instrumentTurnOff}'s POKEY-register memory reset). This also
 * makes {@link #play}'s {@code IsSpecialProveMode()} branch dead (both
 * branches would call only no-op JSRs regardless), so it isn't ported
 * either - {@code AtariTrackerDriverTests.cpp} itself only characterizes
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

	/** C++'s {@code GetAtari()} - the UI's volume analyzer and POKEY view read the register shadow from its memory. */
	public Atari getAtari() {
		return atari;
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

	/** Records which instrument is now sounding on track {@code t} - the only observable effect once the JSR calls that would set the actual POKEY registers are omitted (see class javadoc). */
	public void setTrackNoteInstrumentVolume(int t, int n, int i, int v) {
		rmtInstr[t] = i;
	}

	/** No-op - C++'s body only issues a JSR call (see class javadoc), with no other observable effect. */
	public void setTrackVolume(int t, int v) {
	}

	/** Clears track {@code instr} was sounding on and resets its POKEY AUDCx register - matches C++'s body minus the omitted JSR call (see class javadoc). */
	public void instrumentTurnOff(int instr) {
		for (int i = 0; i < SONGTRACKS; i++) {
			if (rmtInstr[i] == instr) {
				atari.setByteAt(0xd200 + i * 2 + 1 + (i >= 4 ? 16 : 0), 0);
				rmtInstr[i] = -1;
			}
		}
	}
}
