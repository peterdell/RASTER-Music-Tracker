package org.atari.raster.rmt.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Mirrors src/cpp/test/AtariTrackerDriverTests.cpp's AtariTrackerDriverTest
 * fixture on a memory-only Atari - the C++ test build's situation, whose
 * JSR is a link-only stub (so init() returns 0 there and here). The driver
 * running for real is covered by AtariCpuTest (a note through the PATCH16
 * binary) and the ui package's LivePlaybackTest (Delta.rmt against ASAP's
 * independent emulation).
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

	/**
	 * The return value above was ignored by every caller in both programs, so
	 * a driver file that was not there left the emulated Atari playing silence
	 * and every export quiet, with nothing said about it. The state is asked
	 * for by name now, and reported - at start-up, when the Options switch the
	 * version, and by the script runner before it writes a sound file.
	 */
	@Test
	void areRoutinesLoadedFollowsTheLastLoad() {
		assertFalse(driver.areRoutinesLoaded(), "nothing is loaded before the first load");

		assertTrue(driver.loadRMTRoutines(TrackerDriverVersion.PATCH16) > 0);
		assertTrue(driver.areRoutinesLoaded());

		assertEquals(0, driver.loadRMTRoutines(TrackerDriverVersion.NONE));
		assertFalse(driver.areRoutinesLoaded(), "switching to a version without a file clears it again");
	}

	@Test
	void initResetsEveryChannelsInstrumentAndReturnsZero() {
		// The C++ test pre-sets g_rmtinstr[i]=5; setTrackNoteInstrumentVolume
		// does the same here (its JSRs are no-ops on this memory-only Atari).
		// init() returns the routine's A - 0 when no CPU runs it (the C++
		// test build), 1 from every real driver version (AtariCpuTest).
		for (int i = 0; i < 8; i++) {
			driver.setTrackNoteInstrumentVolume(i, 10, 5, 8);
			assertEquals(5, driver.getRmtInstrument(i));
		}
		assertEquals(0, driver.init());

		for (int i = 0; i < 8; i++) {
			assertEquals(-1, driver.getRmtInstrument(i));
		}
	}
}
