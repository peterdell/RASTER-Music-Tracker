package com.wudsn.tools.rmt.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Mirrors src/cpp/test/AtariTests.cpp, including the Init(bool) tests that
 * turned out not to need their original deferral either (see Atari's own
 * javadoc).
 */
class AtariTest {

	@Test
	void newInstanceHasZeroedMemoryAndIsNotNtsc() {
		Atari atari = new Atari();
		assertEquals(0, atari.getByteAt(0));
		assertEquals(0, atari.getByteAt(Atari.MEMORY_SIZE - 1));
		assertFalse(atari.isNTSC());
	}

	@Test
	void setByteAtAndGetByteAtRoundTrip() {
		Atari atari = new Atari();
		atari.setByteAt(0x1234, 0xAB);
		assertEquals(0xAB, atari.getByteAt(0x1234));
		assertEquals(0, atari.getByteAt(0x1233)); // neighboring bytes untouched
	}

	@Test
	void getMemoryReturnsTheSameBackingBuffer() {
		Atari atari = new Atari();
		atari.setByteAt(0x4000, 0x42);
		assertEquals(0x42, atari.getMemory()[0x4000] & 0xFF);

		atari.getMemory()[0x4001] = (byte) 0x99;
		assertEquals(0x99, atari.getByteAt(0x4001));
	}

	@Test
	void clearMemoryResetsAllBytes() {
		Atari atari = new Atari();
		atari.setByteAt(0x100, 0xFF);
		atari.clearMemory();
		assertEquals(0, atari.getByteAt(0x100));
	}

	@Test
	void staticClockFrequencyMatchesRegion() {
		assertEquals(Atari.FREQ_17_NTSC, Atari.getClockFrequency(true));
		assertEquals(Atari.FREQ_17_PAL, Atari.getClockFrequency(false));
		assertEquals(1789773, Atari.getClockFrequency(true));
		assertEquals(1773447, Atari.getClockFrequency(false));
	}

	@Test
	void staticFrameCycleCountMatchesRegion() {
		assertEquals(114 * 262, Atari.getFrameCycleCount(true));
		assertEquals(114 * 312, Atari.getFrameCycleCount(false));
	}

	@Test
	void instanceClockAndCycleCountFollowIsNtscDefaultingToPal() {
		Atari atari = new Atari(); // isNTSC() is false by default
		assertEquals(Atari.FREQ_17_PAL, atari.getClockFrequency());
		assertEquals(114 * 312, atari.getFrameCycleCount());
	}

	// init(boolean) populates this instance's own memory with pitch tables -
	// reuses the exact golden-master byte values already captured in
	// TuningTest for the Distortion-2 (Bell) table at RMT_FRQTABLES+0x000/
	// 0x001, since the point here is to characterize init()'s own wiring
	// (right clock, right instance, right memory offset), not
	// initTuning()'s arithmetic (already covered).

	@Test
	void initPalPopulatesOwnMemoryWithTuningTables() {
		TuningSettings tuningSettings = new TuningSettings();
		tuningSettings.initialize(false); // PAL
		TuningRatios tuningRatios = new TuningRatios();
		tuningRatios.initialize();

		Atari atari = new Atari();
		atari.init(false, tuningSettings, tuningRatios);

		assertFalse(atari.isNTSC());
		assertEquals(Atari.FREQ_17_PAL, atari.getClockFrequency());
		assertEquals(62, atari.getByteAt(Atari.RMT_FRQTABLES + 0x000));
		assertEquals(58, atari.getByteAt(Atari.RMT_FRQTABLES + 0x001));
	}

	@Test
	void initNtscPopulatesOwnMemoryWithTuningTables() {
		TuningSettings tuningSettings = new TuningSettings();
		tuningSettings.initialize(true); // NTSC
		TuningRatios tuningRatios = new TuningRatios();
		tuningRatios.initialize();

		Atari atari = new Atari();
		atari.init(true, tuningSettings, tuningRatios);

		assertTrue(atari.isNTSC());
		assertEquals(Atari.FREQ_17_NTSC, atari.getClockFrequency());
		assertEquals(62, atari.getByteAt(Atari.RMT_FRQTABLES + 0x000));
		assertEquals(58, atari.getByteAt(Atari.RMT_FRQTABLES + 0x001));
	}
}
