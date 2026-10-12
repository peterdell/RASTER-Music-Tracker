package org.atari.raster.rmt.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Mirrors src/cpp/test/FrequencyTablesTests.cpp.
 *
 * <p>{@link FrequencyTables} reads the page half of its answer out of the
 * driver's own {@code TABLES_MSB} dispatch table, so these tests run against a
 * really loaded rmt_driver_v6.obx - and the expected pages below are the ONE
 * place in the Java port that writes the mapping down. If
 * asm/Patch-16/rmtplayr.a65 ever reshuffles the pages, this test fails and
 * says so, instead of the two programs silently disagreeing with the driver
 * the way they did for years
 * (plans/38_GETFREQUENCY_TABLE_OFFSETS_PLAN.md,
 * plans/39_MIDI_TABLE_OFFSETS_PLAN.md).
 */
class FrequencyTablesTest {

	private Atari atari;
	private byte[] memory;

	@BeforeEach
	void setUp() {
		atari = new Atari();
		assertTrue(new AtariTrackerDriver(atari).loadRMTRoutines(TrackerDriverVersion.PATCH16) > 0);
		memory = atari.getMemory();
	}

	@Test
	void everyDistortionTakesThePageTheDriverDispatchesItTo() {
		// distortion -> page, as asm/Patch-16/rmtplayr.a65's TABLES_MSB has it.
		int[] expected = { 0xB200, 0xB000, 0xB100, 0xB200, 0xB200, 0xB200, 0xB300, 0xB400 };

		for (int i = 0; i < 8; i++) {
			int distortion = i * 2;
			assertEquals(expected[i], FrequencyTables.getTableAddress(memory, distortion, FrequencyTables.Mode.CLOCK_64KHZ),
					() -> String.format("distortion $%02X", distortion));
		}
	}

	@Test
	void theClockAndWidthSelectTheSliceInsideThatPage() {
		// store_tables_lsb: 64 kHz at +$00, 1.79 MHz at +$40, 16-bit at +$80.
		assertEquals(Atari.RMT_FRQTAB_PURE_64KHZ, FrequencyTables.getTableAddress(memory, 0x0A, FrequencyTables.Mode.CLOCK_64KHZ));
		assertEquals(Atari.RMT_FRQTAB_PURE_64KHZ + 0x40, FrequencyTables.getTableAddress(memory, 0x0A, FrequencyTables.Mode.CLOCK_179MHZ));
		assertEquals(Atari.RMT_FRQTAB_PURE_64KHZ + 0x80, FrequencyTables.getTableAddress(memory, 0x0A, FrequencyTables.Mode.JOIN_16BIT));
	}

	@Test
	void fifteenKhzComesFromPageExtraZeroAndNotFromTheDispatchTable() {
		// check_15khz: distortion C and E take the buzzy table, the rest the
		// pure one - so the gritty page has no 15 kHz table of its own.
		for (int distortion : new int[] { 0x00, 0x02, 0x04, 0x06, 0x08, 0x0A }) {
			assertEquals(Atari.RMT_FRQTAB_PURE_15KHZ, FrequencyTables.getTableAddress(memory, distortion, FrequencyTables.Mode.CLOCK_15KHZ),
					() -> String.format("distortion $%02X", distortion));
		}
		assertEquals(Atari.RMT_FRQTAB_BUZZY_15KHZ, FrequencyTables.getTableAddress(memory, 0x0C, FrequencyTables.Mode.CLOCK_15KHZ));
		assertEquals(Atari.RMT_FRQTAB_BUZZY_15KHZ, FrequencyTables.getTableAddress(memory, 0x0E, FrequencyTables.Mode.CLOCK_15KHZ));
	}

	@Test
	void everyDistortionTakesTheAudcTheDriverDispatchesWithIt() {
		// distortion -> AUDC, the second byte of each TABLES_MSB pair. Not
		// simply the distortion shifted up: $06 plays with $A0 and $0E with
		// $C0, which is why the MIDI path used to get those two wrong.
		int[] expected = { 0x00, 0x20, 0x40, 0xA0, 0x80, 0xA0, 0xC0, 0xC0 };

		for (int i = 0; i < 8; i++) {
			int distortion = i * 2;
			assertEquals(expected[i], FrequencyTables.getDistortionAudc(memory, distortion),
					() -> String.format("distortion $%02X", distortion));
		}
	}

	@Test
	void thereIsNoAudcForADistortionOutsideZeroToEOrWithoutADriver() {
		assertEquals(-1, FrequencyTables.getDistortionAudc(memory, -1));
		assertEquals(-1, FrequencyTables.getDistortionAudc(memory, 0x10));

		atari.clearMemory();
		assertEquals(-1, FrequencyTables.getDistortionAudc(memory, 0x0A));
	}

	@Test
	void theChannelDecidesWhatTheAudctlBitsMean() {
		// Only channels 1 and 3 of each chip can run at 1.79 MHz, and a 16-bit
		// pair is addressed through its high byte, the channel above.
		assertEquals(FrequencyTables.Mode.CLOCK_64KHZ, FrequencyTables.getModeForChannel(0x00, 0));
		assertEquals(FrequencyTables.Mode.CLOCK_15KHZ, FrequencyTables.getModeForChannel(0x01, 0));

		for (int track : new int[] { 0, 4 }) {
			assertEquals(FrequencyTables.Mode.CLOCK_179MHZ, FrequencyTables.getModeForChannel(0x40, track));
		}
		for (int track : new int[] { 1, 2, 3, 5, 6, 7 }) {
			assertEquals(FrequencyTables.Mode.CLOCK_64KHZ, FrequencyTables.getModeForChannel(0x40, track));
		}
		for (int track : new int[] { 2, 6 }) {
			assertEquals(FrequencyTables.Mode.CLOCK_179MHZ, FrequencyTables.getModeForChannel(0x20, track));
		}

		// join 1+2 with channel 1 at 1.79 MHz: the pair answers on channel 2.
		assertEquals(FrequencyTables.Mode.JOIN_16BIT, FrequencyTables.getModeForChannel(0x50, 1));
		assertEquals(FrequencyTables.Mode.JOIN_16BIT, FrequencyTables.getModeForChannel(0x50, 5));
		assertEquals(FrequencyTables.Mode.CLOCK_179MHZ, FrequencyTables.getModeForChannel(0x50, 0));
		// join 3+4 with channel 3 at 1.79 MHz: on channel 4.
		assertEquals(FrequencyTables.Mode.JOIN_16BIT, FrequencyTables.getModeForChannel(0x28, 3));
		assertEquals(FrequencyTables.Mode.JOIN_16BIT, FrequencyTables.getModeForChannel(0x28, 7));
		assertEquals(FrequencyTables.Mode.CLOCK_179MHZ, FrequencyTables.getModeForChannel(0x28, 2));
	}

	@Test
	void oneSeventyNineAndSixteenBitOverrideTheFifteenKhzClock() {
		assertEquals(FrequencyTables.Mode.CLOCK_179MHZ, FrequencyTables.getModeForChannel(0x41, 0));
		assertEquals(FrequencyTables.Mode.JOIN_16BIT, FrequencyTables.getModeForChannel(0x51, 1));
		// ...but a channel that cannot do either keeps the 15 kHz clock.
		assertEquals(FrequencyTables.Mode.CLOCK_15KHZ, FrequencyTables.getModeForChannel(0x41, 3));
	}

	@Test
	void thereIsNoTableForADistortionOutsideZeroToE() {
		assertEquals(0, FrequencyTables.getTableAddress(memory, -1, FrequencyTables.Mode.CLOCK_64KHZ));
		assertEquals(0, FrequencyTables.getTableAddress(memory, 0x10, FrequencyTables.Mode.CLOCK_64KHZ));
	}

	@Test
	void onlyThePatchSixteenDriverIsAcceptedAsCarryingADispatchTable() {
		// The check is a plausibility test on the bytes, not a version test,
		// so the thing worth asserting is that it separates the drivers RMT
		// really ships: in the other six, $3208 holds their own
		// frequency-table data, far outside the $B0-$B4 the pages need. Each
		// version gets its own Atari so no earlier load can leave bytes
		// behind.
		for (TrackerDriverVersion version : TrackerDriverVersion.getValues()) {
			Atari own = new Atari();
			boolean loaded = new AtariTrackerDriver(own).loadRMTRoutines(version) > 0;
			if (!loaded) {
				continue; // TrackerDriverVersion.NONE has no binary
			}

			assertEquals(version == TrackerDriverVersion.PATCH16, FrequencyTables.hasDispatchTable(own.getMemory()),
					() -> "driver " + version.getId());
		}
	}

	@Test
	void thereIsNoTableWithoutADriverThatHasADispatchTable() {
		// The v1-family drivers keep their tables inside the player and never
		// read this region, so nothing writes a dispatch table for them. The
		// 15 kHz addresses are fixed and answer regardless - the caller
		// decides whether that is useful.
		atari.clearMemory();

		assertEquals(0, FrequencyTables.getTableAddress(memory, 0x0A, FrequencyTables.Mode.CLOCK_64KHZ));
		assertEquals(0, FrequencyTables.getTableAddress(memory, 0x0A, FrequencyTables.Mode.CLOCK_179MHZ));
	}
}
