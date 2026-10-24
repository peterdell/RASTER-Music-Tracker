package org.atari.raster.rmt.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Mirrors src/cpp/test/FrequencyTablesTests.cpp.
 *
 * <p>{@link FrequencyTables} reads both halves of its answer out of the
 * driver's own dispatch table, so these tests run against really loaded driver
 * binaries - and the expected values below are the ONE place in the Java port
 * that writes the two mappings down. If asm/Patch-16/rmtplayr.a65 or
 * asm/Legacy/rmt.a65 ever reshuffles its tables, these tests fail and say so,
 * instead of the two programs silently disagreeing with the driver the way
 * they did for years (plans/38_GETFREQUENCY_TABLE_OFFSETS_PLAN.md,
 * plans/39_MIDI_TABLE_OFFSETS_PLAN.md,
 * plans/40_DRIVER_TABLE_LAYOUTS_PLAN.md).
 */
class FrequencyTablesTest {

	private Atari atari;
	private byte[] memory;

	/** Every driver RMT ships, in {@code getNumber()} order. NONE has no binary. */
	private static List<TrackerDriverVersion> allVersions() {
		List<TrackerDriverVersion> result = new ArrayList<TrackerDriverVersion>(TrackerDriverVersion.getValues());
		result.remove(TrackerDriverVersion.NONE);
		return result;
	}

	/** That driver really loaded into an Atari of its own, so no earlier load can leave bytes behind. */
	private static byte[] loaded(TrackerDriverVersion version) {
		Atari own = new Atari();
		assertTrue(new AtariTrackerDriver(own).loadRMTRoutines(version) > 0, () -> "driver " + version.getId());
		return own.getMemory();
	}

	@BeforeEach
	void setUp() {
		atari = new Atari();
		assertTrue(new AtariTrackerDriver(atari).loadRMTRoutines(TrackerDriverVersion.PATCH16) > 0);
		memory = atari.getMemory();
	}

	// --- the Patch 16 layout ---

	@Test
	void everyDistortionTakesThePageTheDriverDispatchesItTo() {
		// distortion -> page, as asm/Patch-16/rmtplayr.a65's tabbeganddistor has it.
		int[] expected = { 0xB200, 0xB000, 0xB100, 0xB200, 0xB200, 0xB200, 0xB300, 0xB400 };

		for (int i = 0; i < 8; i++) {
			int distortion = i * 2;
			assertEquals(expected[i],
					FrequencyTables.getTableAddress(memory, TrackerDriverVersion.PATCH16, distortion, FrequencyTables.Mode.CLOCK_64KHZ),
					() -> String.format("distortion $%02X", distortion));
		}
	}

	@Test
	void theClockAndWidthSelectTheSliceInsideThatPage() {
		// store_tables_lsb: 64 kHz at +$00, 1.79 MHz at +$40, 16-bit at +$80.
		assertEquals(Atari.RMT_FRQTAB_PURE_64KHZ,
				FrequencyTables.getTableAddress(memory, TrackerDriverVersion.PATCH16, 0x0A, FrequencyTables.Mode.CLOCK_64KHZ));
		assertEquals(Atari.RMT_FRQTAB_PURE_64KHZ + 0x40,
				FrequencyTables.getTableAddress(memory, TrackerDriverVersion.PATCH16, 0x0A, FrequencyTables.Mode.CLOCK_179MHZ));
		assertEquals(Atari.RMT_FRQTAB_PURE_64KHZ + 0x80,
				FrequencyTables.getTableAddress(memory, TrackerDriverVersion.PATCH16, 0x0A, FrequencyTables.Mode.JOIN_16BIT));
	}

	@Test
	void fifteenKhzComesFromPageExtraZeroAndNotFromTheDispatchTable() {
		// check_15khz: distortion C and E take the buzzy table, the rest the
		// pure one - so the gritty page has no 15 kHz table of its own.
		for (int distortion : new int[] { 0x00, 0x02, 0x04, 0x06, 0x08, 0x0A }) {
			assertEquals(Atari.RMT_FRQTAB_PURE_15KHZ,
					FrequencyTables.getTableAddress(memory, TrackerDriverVersion.PATCH16, distortion, FrequencyTables.Mode.CLOCK_15KHZ),
					() -> String.format("distortion $%02X", distortion));
		}
		assertEquals(Atari.RMT_FRQTAB_BUZZY_15KHZ,
				FrequencyTables.getTableAddress(memory, TrackerDriverVersion.PATCH16, 0x0C, FrequencyTables.Mode.CLOCK_15KHZ));
		assertEquals(Atari.RMT_FRQTAB_BUZZY_15KHZ,
				FrequencyTables.getTableAddress(memory, TrackerDriverVersion.PATCH16, 0x0E, FrequencyTables.Mode.CLOCK_15KHZ));
	}

	@Test
	void everyDistortionTakesTheAudcTheDriverDispatchesWithIt() {
		// distortion -> AUDC, the second byte of each tabbeganddistor pair. Not
		// simply the distortion shifted up: $06 plays with $A0 and $0E with
		// $C0, which is why the MIDI path used to get those two wrong.
		int[] expected = { 0x00, 0x20, 0x40, 0xA0, 0x80, 0xA0, 0xC0, 0xC0 };

		for (int i = 0; i < 8; i++) {
			int distortion = i * 2;
			assertEquals(expected[i], FrequencyTables.getDistortionAudc(memory, TrackerDriverVersion.PATCH16, distortion),
					() -> String.format("distortion $%02X", distortion));
		}
	}

	// --- the v1-family layout ---

	@Test
	void theVOneFamilyTakesItsTablesFromTabbeganddistor() {
		// offset into frqtab ($3200) and AUDC per distortion, as
		// asm/Legacy/rmt.a65's tabbeganddistor has it: frqtabbass1 at +$00 for
		// distortions $06 and $0C, frqtabbass2 at +$40 for $0E, frqtabpure at
		// +$80 for the rest.
		int[] expectedTable = { 0x3280, 0x3280, 0x3280, 0x3200, 0x3280, 0x3280, 0x3200, 0x3240 };
		int[] expectedAudc = { 0x00, 0x20, 0x40, 0xC0, 0x80, 0xA0, 0xC0, 0xC0 };

		byte[] v1 = loaded(TrackerDriverVersion.UNPATCHED);

		for (int i = 0; i < 8; i++) {
			int distortion = i * 2;
			assertEquals(expectedTable[i],
					FrequencyTables.getTableAddress(v1, TrackerDriverVersion.UNPATCHED, distortion, FrequencyTables.Mode.CLOCK_64KHZ),
					() -> String.format("distortion $%02X", distortion));
			assertEquals(expectedAudc[i], FrequencyTables.getDistortionAudc(v1, TrackerDriverVersion.UNPATCHED, distortion),
					() -> String.format("distortion $%02X", distortion));
		}
	}

	@Test
	void theVOneFamilyReadsTheSameTableWhateverTheClock() {
		// That player has no 1.79 MHz and no 15 kHz tables at all: it builds
		// its pointer from >frqtab and the tabbeganddistor offset and indexes
		// it by the note, whatever clock the channel runs at. So those two
		// clocks play out of tune there rather than playing nothing - and
		// reporting the same table is what the player really does.
		byte[] v1 = loaded(TrackerDriverVersion.UNPATCHED);

		for (FrequencyTables.Mode mode : new FrequencyTables.Mode[] { FrequencyTables.Mode.CLOCK_64KHZ, FrequencyTables.Mode.CLOCK_179MHZ,
				FrequencyTables.Mode.CLOCK_15KHZ }) {
			assertEquals(0x3280, FrequencyTables.getTableAddress(v1, TrackerDriverVersion.UNPATCHED, 0x0A, mode), () -> "mode " + mode);
		}

		// ...but its 16-bit bass reads frqtabbasslo and frqtabbasshi, two
		// tables a page apart, which no single address can name.
		assertEquals(0, FrequencyTables.getTableAddress(v1, TrackerDriverVersion.UNPATCHED, 0x06, FrequencyTables.Mode.JOIN_16BIT));
	}

	@Test
	void eachPatchedDriverIsReadWithItsOwnTableAndNotVersionOnesCopy() {
		// The five patched binaries have no source in this repository, and
		// each one changed the table: PATCH6 plays distortion $06 with AUDC
		// $A0 where UNPATCHED plays it with $C0, and PATCH8 and
		// PATCH_PRINCE_OF_PERSIA do not even keep their tables on $40
		// boundaries. Reading the bytes is the only way to be right about all
		// of them - these are the shipped values, measured, and this test is
		// what catches a driver binary being replaced.
		int[][] expected = {
				// version number, the distortion $06 table, its AUDC
				{ 1, 0x3200, 0xC0 }, { 2, 0x3200, 0xC0 }, { 3, 0x3200, 0x80 }, { 4, 0x3200, 0xA0 }, { 5, 0x3200, 0xC0 },
				{ 7, 0x3270, 0xC0 }, };

		for (int[] e : expected) {
			TrackerDriverVersion version = TrackerDriverVersion.getInstance(e[0]);
			byte[] driverMemory = loaded(version);

			assertEquals(e[1], FrequencyTables.getTableAddress(driverMemory, version, 0x06, FrequencyTables.Mode.CLOCK_64KHZ),
					() -> "driver " + version.getId());
			assertEquals(e[2], FrequencyTables.getDistortionAudc(driverMemory, version, 0x06), () -> "driver " + version.getId());
		}
	}

	// --- the mode, which is the layouts' shared question ---

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

	// --- what neither layout answers ---

	@Test
	void thereIsNoTableForADistortionOutsideZeroToE() {
		for (TrackerDriverVersion version : allVersions()) {
			assertEquals(0, FrequencyTables.getTableAddress(memory, version, -1, FrequencyTables.Mode.CLOCK_64KHZ),
					() -> "driver " + version.getId());
			assertEquals(0, FrequencyTables.getTableAddress(memory, version, 0x10, FrequencyTables.Mode.CLOCK_64KHZ),
					() -> "driver " + version.getId());
		}
	}

	@Test
	void thereIsNoAudcForADistortionOutsideZeroToEOrWithoutADriver() {
		assertEquals(-1, FrequencyTables.getDistortionAudc(memory, TrackerDriverVersion.PATCH16, -1));
		assertEquals(-1, FrequencyTables.getDistortionAudc(memory, TrackerDriverVersion.PATCH16, 0x10));

		atari.clearMemory();
		for (TrackerDriverVersion version : allVersions()) {
			assertEquals(-1, FrequencyTables.getDistortionAudc(memory, version, 0x0A), () -> "driver " + version.getId());
		}
	}

	@Test
	void versionNoneHasNoLayoutAtAll() {
		// Not a driver, so neither layout applies - including the 15 kHz
		// addresses, which the Patch 16 branch otherwise answers from
		// constants.
		for (FrequencyTables.Mode mode : FrequencyTables.Mode.values()) {
			assertEquals(0, FrequencyTables.getTableAddress(memory, TrackerDriverVersion.NONE, 0x0A, mode), () -> "mode " + mode);
		}
		assertFalse(FrequencyTables.hasDispatchTable(memory, TrackerDriverVersion.NONE));
	}

	@Test
	void eachDriverIsAcceptedOnlyForItsOwnFamilysLayout() {
		// The check is a plausibility test on the bytes, not a version test,
		// so the thing worth asserting is that it separates the two families
		// in the drivers RMT really ships: a v1-family driver's $3208 holds
		// its own frequency-table data, far outside the $B0-$B4 a page needs,
		// and the Patch 16 driver loads nothing at all into the v1 family's
		// $3182.
		for (TrackerDriverVersion version : allVersions()) {
			byte[] driverMemory = loaded(version);

			assertTrue(FrequencyTables.hasDispatchTable(driverMemory, version), () -> "driver " + version.getId());

			TrackerDriverVersion otherFamily = version == TrackerDriverVersion.PATCH16 ? TrackerDriverVersion.UNPATCHED
					: TrackerDriverVersion.PATCH16;
			assertFalse(FrequencyTables.hasDispatchTable(driverMemory, otherFamily), () -> "driver " + version.getId());
		}
	}

	@Test
	void thereIsNoTableWithoutADriverThatHasADispatchTable() {
		atari.clearMemory();

		for (TrackerDriverVersion version : allVersions()) {
			assertFalse(FrequencyTables.hasDispatchTable(memory, version), () -> "driver " + version.getId());
			assertEquals(0, FrequencyTables.getTableAddress(memory, version, 0x0A, FrequencyTables.Mode.CLOCK_64KHZ),
					() -> "driver " + version.getId());
			assertEquals(0, FrequencyTables.getTableAddress(memory, version, 0x0A, FrequencyTables.Mode.CLOCK_179MHZ),
					() -> "driver " + version.getId());
		}

		// The Patch 16 15 kHz addresses are fixed and answer regardless - they
		// are not in the dispatch table, so its absence cannot veto them. The
		// caller decides whether that is useful.
		assertEquals(Atari.RMT_FRQTAB_PURE_15KHZ,
				FrequencyTables.getTableAddress(memory, TrackerDriverVersion.PATCH16, 0x0A, FrequencyTables.Mode.CLOCK_15KHZ));
	}
}
