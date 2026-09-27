package org.atari.raster.rmt.model;

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
 * <p>The JSR calls are real since the audio batch (B8): {@link Atari#jsr}
 * runs the loaded tracker driver on {@link AtariCpu} (ASAP's 6502). On a
 * memory-only {@link Atari} (the model tests, matching the C++ test build's
 * link-only {@code C6502::JSR} stub) they return their registers unchanged,
 * so every method keeps its other observable effects - the
 * {@code g_rmtinstr} bookkeeping and {@link #instrumentTurnOff}'s
 * POKEY-register memory reset - and {@link #init} returns 0 there.
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

	// The RMT tracker driver's entry points (Atari.h / tracker_obx.h)
	public static final int RMT_INIT = 0x3400;
	public static final int RMT_PLAY = RMT_INIT + 3;
	public static final int RMT_P3 = RMT_INIT + 6;
	public static final int RMT_SILENCE = RMT_INIT + 9;
	public static final int RMT_SETPOKEY = RMT_INIT + 12;
	public static final int RMT_ATA_SETNOTEINSTR = 0x3D00;
	public static final int RMT_ATA_SETVOLUME = 0x3E00;
	public static final int RMT_ATA_INSTROFF = 0x3E80;

	/** {@code Init()}: JSR {@code RMT_INIT} with A=0, X=0, Y=$3F and every channel's tracked instrument reset; returns the routine's A (0 on a memory-only Atari, whose JSR is a no-op). */
	public int init() {
		AtariCpu.Registers r = atari.jsr(RMT_INIT, 0, 0x00, 0x3f);
		for (int i = 0; i < SONGTRACKS; i++) {
			rmtInstr[i] = -1;
		}
		return r.a();
	}

	/** {@code Play()} outside the special prove mode: one run of the RMT routine from {@code RMT_P3} (wrap processing), then {@code RMT_SETPOKEY}. */
	public void play() {
		play(false);
	}

	/** {@code Play()}: in the special prove mode only {@code RMT_SETPOKEY} runs (the notes are set directly), otherwise {@code RMT_P3} first. */
	public void play(boolean specialProveMode) {
		if (!specialProveMode) {
			atari.jsr(RMT_P3, 0, 0, 0);
		}
		atari.jsr(RMT_SETPOKEY, 0, 0, 0);
	}

	/** {@code SetPokey()}: the driver stores its POKEY registers into the shadow at $D200/$D210. */
	public void setPokey() {
		atari.jsr(RMT_SETPOKEY, 0, 0, 0);
	}

	/** {@code Silence()}. */
	public void silence() {
		atari.jsr(RMT_SILENCE, 0, 0, 0);
	}

	/** {@code SetTrackNoteInstrumentVolume(t, n, i, v)}: {@code RMT_ATA_SETNOTEINSTR} (A=note, X=track, Y=instrument) then {@code RMT_ATA_SETVOLUME} (A=volume, X=track). */
	public void setTrackNoteInstrumentVolume(int t, int n, int i, int v) {
		atari.jsr(RMT_ATA_SETNOTEINSTR, n, t, i);
		atari.jsr(RMT_ATA_SETVOLUME, v, t, 0);
		rmtInstr[t] = i;
	}

	/** {@code SetTrackVolume(t, v)}. */
	public void setTrackVolume(int t, int v) {
		atari.jsr(RMT_ATA_SETVOLUME, v, t, 0);
	}

	/** {@code InstrumentTurnOff(instr)}: every channel sounding {@code instr} is stopped ({@code RMT_ATA_INSTROFF}, X=channel) and its AUDCx shadow byte cleared. */
	public void instrumentTurnOff(int instr) {
		for (int i = 0; i < SONGTRACKS; i++) {
			if (rmtInstr[i] == instr) {
				atari.jsr(RMT_ATA_INSTROFF, 0, i, 0);
				atari.setByteAt(0xd200 + i * 2 + 1 + (i >= 4 ? 16 : 0), 0); // Reset POKEY AUDCx memory
				rmtInstr[i] = -1;
			}
		}
	}
}
