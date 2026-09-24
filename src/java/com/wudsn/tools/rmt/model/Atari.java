package com.wudsn.tools.rmt.model;

import java.util.Arrays;

/**
 * Ported from CAtari (src/cpp/Atari.h/.cpp) - the already-tested subset,
 * plus {@link #init}, which turned out not to need its original deferral
 * either (see below) once characterized. {@code Init()}/{@code DeInit()}/
 * {@code JSR()} stay deferred - genuine 6502 DLL/hardware interop, not a
 * scoping question.
 *
 * <p>C++'s {@code GetMemoryAt}/{@code GetConstMemoryAt} (pointer-into-buffer
 * accessors, used for write-through/read-through access to a range) become
 * a single {@link #getMemory()} returning the backing array directly - Java
 * array indexing already gives write-through access to the same buffer, so
 * there's no need for an offset-pointer equivalent or a separate
 * const/non-const pair.
 *
 * <p>{@link #init} takes {@link TuningSettings}/{@link TuningRatios} as
 * explicit parameters instead of reading C++'s g_tuning/g_tuningRatios
 * globals (same pattern as {@link Tuning}), and constructs its own
 * short-lived {@link Tuning} instance to compute the tables - C++'s global
 * {@code g_Tuning} has no Java equivalent yet, and none is needed here,
 * since nothing outside this call uses the temporary instance afterward.
 * {@code Tuning.initTuning()} writes at offsets relative to the *start* of
 * whatever buffer it's given (matching its own {@code generateTable}'s
 * explicit-offset design), so a small scratch buffer sized to just the
 * table region is filled first, then copied into this instance's own
 * memory at {@link #RMT_FRQTABLES} - the Java equivalent of C++ passing
 * {@code GetMemoryAt(RMT_FRQTABLES)} (a pointer already offset into the
 * full 64K buffer).
 *
 * <p><b>{@code Init(bool)} turned out not to need its deferral</b>: it
 * calls {@code g_Tuning.InitTuning()}, which is safe as long as
 * {@code g_tuning.basetuning} is set first (already characterized in
 * {@code TuningTests.cpp}) - the same "re-verify instead of trusting the
 * old scoping note" finding as {@code CSong}'s constructor and
 * {@code CInstruments::GetFrequency} earlier in this project. Backfilled
 * as {@code AtariTest.InitPal/NtscPopulatesOwnMemoryWithTuningTables} in
 * the C++ source, reusing {@code TuningTests.cpp}'s own golden-master byte
 * values (this only needs to characterize {@code Init(bool)}'s own wiring
 * - right clock, right instance, right memory offset - not
 * {@code InitTuning()}'s arithmetic, which is already covered elsewhere).
 */
public final class Atari {

	public static final int MEMORY_SIZE = 0x10000;

	// The true clock frequency for the NTSC Atari 8-bit computer is 1.7897725 MHz
	public static final int FREQ_17_NTSC = 1789773;
	// The true clock frequency for the PAL Atari 8-bit computer is 1.7734470 MHz
	public static final int FREQ_17_PAL = 1773447;

	// Matches RMT_FRQTABLES (Atari.h): RMTPLAYR_PAGE_DISTORTION_2 (tracker_obx.h).
	public static final int RMT_FRQTABLES = 0xB000;

	public static int getClockFrequency(boolean ntsc) {
		return ntsc ? FREQ_17_NTSC : FREQ_17_PAL;
	}

	// The maximum clock count for the entire screen in PAL (default) and NTSC region
	public static int getFrameCycleCount(boolean ntsc) {
		final int MAXSCREENCYCLES_NTSC = 114 * 262;
		final int MAXSCREENCYCLES_PAL = 114 * 312;
		return ntsc ? MAXSCREENCYCLES_NTSC : MAXSCREENCYCLES_PAL;
	}

	private final byte[] memory = new byte[MEMORY_SIZE];
	private boolean ntsc;

	public void clearMemory() {
		Arrays.fill(memory, (byte) 0);
	}

	public int getByteAt(int address) {
		return memory[address] & 0xFF;
	}

	public void setByteAt(int address, int value) {
		memory[address] = (byte) value;
	}

	/** The backing memory buffer - see class javadoc for why this replaces C++'s GetMemoryAt()/GetConstMemoryAt(). */
	public byte[] getMemory() {
		return memory;
	}

	public boolean isNTSC() {
		return ntsc;
	}

	public int getClockFrequency() {
		return getClockFrequency(ntsc);
	}

	public int getFrameCycleCount() {
		return getFrameCycleCount(ntsc);
	}

	/**
	 * Initialize the tuning variables, and generate the POKEY frequencies (AUDF) lookup tables into this instance's own memory.
	 *
	 * @param ntsc whether this is an NTSC (vs. PAL) machine
	 * @param tuningSettings the base tuning/temperament/basenote to generate the tables from (C++ reads these from the g_tuning global instead)
	 * @param tuningRatios the custom-temperament ratios to generate the tables from (C++ reads these from the g_tuningRatios global instead)
	 */
	public void init(boolean ntsc, TuningSettings tuningSettings, TuningRatios tuningRatios) {
		this.ntsc = ntsc;
		Tuning tuning = new Tuning(getClockFrequency());
		byte[] tableBuffer = new byte[0x600]; // exactly the table region InitTuning() writes
		tuning.initTuning(tableBuffer, tuningSettings, tuningRatios);
		System.arraycopy(tableBuffer, 0, memory, RMT_FRQTABLES, tableBuffer.length);
	}
}
