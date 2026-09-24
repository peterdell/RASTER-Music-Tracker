package com.wudsn.tools.rmt.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Mirrors src/cpp/test/AtariTrackerDriverTests.cpp's AtariTrackerDriverTest
 * fixture - only what's still meaningful once C++'s JSR() calls are
 * entirely unimplemented rather than merely stubbed (see
 * AtariTrackerDriver's class javadoc): Play/SetPokey/Silence are
 * characterized elsewhere in C++ purely as "doesn't crash", which a
 * no-op Java method trivially satisfies without a dedicated test here.
 */
class AtariTrackerDriverTest {

	private Atari atari;
	private AtariTrackerDriver driver;

	@BeforeEach
	void setUp() {
		atari = new Atari();
		driver = new AtariTrackerDriver(atari);
	}

	@Test
	void loadRMTRoutinesLoadsTheDefaultDriverBinaryIntoMemory() {
		int bytesRead = driver.loadRMTRoutines(TrackerDriverVersion.PATCH16);

		assertTrue(bytesRead > 0);
		// rmt/resources/drivers/rmt_driver_v6.obx's first real block is
		// fromAddr=$3200, toAddr=$3245, first data byte 0x80 - hand-verified
		// against the checked-in file's own bytes.
		assertEquals(0x80, driver.getByteAt(0x3200));
	}

	@Test
	void loadRMTRoutinesReturnsZeroForAMissingDriverVersion() {
		// TrackerDriverVersion.NONE has no matching rmt_driver_v0.obx file.
		assertEquals(0, driver.loadRMTRoutines(TrackerDriverVersion.NONE));
	}

	@Test
	void initResetsEveryChannelsInstrumentAndReturnsZero() {
		// Unlike the C++ test, this doesn't pre-set g_rmtinstr[i]=5 first -
		// SetTrackNoteInstrumentVolume() (the only real-world writer besides
		// init() itself) isn't ported here (no dedicated test coverage - see
		// AtariTrackerDriver's class javadoc). A freshly-constructed
		// AtariTrackerDriver's rmtInstr defaults to 0 (Java's int[] default),
		// so asserting -1 after init() still demonstrates a real reset.
		assertEquals(0, driver.init());

		for (int i = 0; i < 8; i++) {
			assertEquals(-1, driver.getRmtInstrument(i));
		}
	}
}
