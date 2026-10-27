package org.atari.raster.rmt.model;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Mirrors src/cpp/test/TuningTests.cpp exactly, including GenerateTable()/
 * InitTuning() (see Tuning's own javadoc for why those two are redesigned to
 * take explicit TuningSettings parameters here instead of
 * reading C++ globals). Expected values are the same golden-master captures
 * used on the C++ side - CTuning's own test coverage was extended first
 * specifically so this batch would have real values to port against, not
 * hand-derived or freshly guessed ones.
 */
class TuningTest {

	// PAL POKEY clock, matching CAtari::FREQ_17_PAL in Atari.h.
	private static final int PAL_CLOCK = 1773447;

	private Tuning tuning;

	@BeforeEach
	void setUp() {
		tuning = new Tuning(PAL_CLOCK);
	}

	@Test
	void getPitchNoDivisorNoCycle() {
		assertEquals(31668.696428571428, tuning.getPitch(0, 28, 1, 1));
	}

	@Test
	void getPitchWithCoarseDivisor64khz() {
		assertEquals(313.55144978783591, tuning.getPitch(100, 28, 1, 1));
	}

	@Test
	void getPitchWithCoarseDivisor15khz() {
		assertEquals(77.012636789994787, tuning.getPitch(100, 114, 1, 1));
	}

	@Test
	void getPitch179mhzMode() {
		assertEquals(883.19073705179278, tuning.getPitch(1000, 1, 1, 4));
	}

	@Test
	void getAUDFRoundTripsWithGetPitch() {
		// 440 Hz (concert A), 64kHz mode, no fine divisor.
		assertEquals(71, tuning.getAUDF(440.0, 28, 1, 1));
	}

	@Test
	void getAUDFWithFineDivisor() {
		assertEquals(9, tuning.getAUDF(440.0, 28, 7.5, 1));
	}

	@Test
	void getPOKEYPitchPureToneDistortionA() {
		// AUDC 0xA0 = Distortion A (Pure), no volume-only bit.
		assertEquals(313.55144978783591, tuning.getPOKEYPitch(0xA0, 100, 0x00, 0));
	}

	@Test
	void getPOKEYPitchDistortionCBuzzy64khz() {
		// AUDC 0xC1 = Distortion C variant, 64kHz clock (audctl 0x00).
		assertEquals(41.806859971711461, tuning.getPOKEYPitch(0xC1, 100, 0x00, 0));
	}

	@Test
	void getPOKEYPitch179mhzChannel0() {
		// CH1_179 (audctl 0x40) with channel 0 selects the 1.79MHz clock path.
		assertEquals(883.19073705179278, tuning.getPOKEYPitch(0xA0, 1000, 0x40, 0));
	}

	@Test
	void getPOKEYPitchPolyNoiseModeReturnsZeroOnInvalidModulo() {
		// AUDC 0x00 = distortion 0x00; POLY9 comes from audctl (0x80) here.
		// audf chosen so (audf+cycle) % 31 == 0, which this distortion path
		// treats as an invalid frequency and returns 0.
		assertEquals(0.0, tuning.getPOKEYPitch(0x00, 30, 0x80, 0));
	}

	@Test
	void getTruePitchEqualTemperamentBaseNote() {
		assertEquals(8.1913611114619442, tuning.getTruePitch(TuningSettings.PAL_A4_FREQUENCY, Temperament.EQUAL, 3, 0));
	}

	@Test
	void getTruePitchEqualTemperamentOneOctaveUpIsDouble() {
		// semitone=12 is exactly one octave above semitone=0 (same note, multi
		// doubles) - characterizes the octave-doubling relationship explicitly.
		assertEquals(16.382722222923899, tuning.getTruePitch(TuningSettings.PAL_A4_FREQUENCY, Temperament.EQUAL, 3, 12));
	}

	@Test
	void getTruePitchPresetTemperamentUsesItsMultipliers() {
		// The golden value captured from C++ for temperament number 1; the
		// multipliers live on the Temperament instance since plan 59 step 4.
		// (The step 1 retirement made every surviving preset 12-note with a
		// 2:1 octave, so the C++ ragged-row notes-per-octave scan - once
		// characterized here through the retired pentatonic row 24 - has no
		// Java counterpart anymore.)
		assertEquals(8.1809110925559629, tuning.getTruePitch(TuningSettings.PAL_A4_FREQUENCY, Temperament.YOUNG_1799_NO1, 3, 0));
	}

	@Test
	void calculateDeltaAUDFDefaultBranchStepsByOne() {
		// Timbre.BELL - distortion 0x20, neither the 0x40 nor 0xC0 special
		// cases, so the "simplest delta method" (+-1) applies.
		assertEquals(99, tuning.calculateDeltaAUDF(313.55144978783591, 100, 28, 31, 1, Timbre.BELL));
	}

	@Test
	void calculateDeltaAUDFSmoothFourVerifiesMod3Integrity() {
		assertEquals(98, tuning.calculateDeltaAUDF(313.55144978783591, 100, 28, 77.5, 1, Timbre.SMOOTH_4));
	}

	@Test
	void calculateDeltaAUDFBuzzyFourAvoidsMod3Mod5Mod31() {
		assertEquals(100, tuning.calculateDeltaAUDF(313.55144978783591, 100, 28, 232.5, 1, Timbre.BUZZY_4));
	}

	// calculateDeltaAUDF()'s "invalid parameter" fallbacks (for a distortion
	// 0x40/0xC0 timbre that isn't one of the ones handled by name) are only
	// reachable in the C++ test via static_cast<Timbre>(...) on a value with
	// no matching enumerator. Java's Timbre is a closed, type-safe enum with
	// no equivalent way to synthesize an out-of-range constant, and every
	// real Timbre value with a 0x40/0xC0 high nibble is already handled by
	// name below - so these fallbacks are genuinely unreachable here and
	// aren't characterized on the Java side.

	@Test
	void calculateDeltaAUDFFifteenKhzModeAvoidsMod5() {
		// coarse_divisor == 114 selects the 15kHz-mode branch regardless of
		// which distortion-C timbre is passed.
		assertEquals(100, tuning.calculateDeltaAUDF(77.012636789994787, 100, 114, 7.5, 1, Timbre.BUZZY_C));
	}

	@Test
	void calculateDeltaAUDFBuzzyCVerifiesMod3Integrity() {
		assertEquals(98, tuning.calculateDeltaAUDF(313.55144978783591, 100, 28, 2.5, 1, Timbre.BUZZY_C));
	}

	@Test
	void calculateDeltaAUDFGrittyCAvoidsMod3AndMod5() {
		assertEquals(100, tuning.calculateDeltaAUDF(313.55144978783591, 100, 28, 7.5, 1, Timbre.GRITTY_C));
	}

	@Test
	void calculateDeltaAUDFUnstableCVerifiesMod5Integrity() {
		assertEquals(99, tuning.calculateDeltaAUDF(313.55144978783591, 100, 28, 1.5, 1, Timbre.UNSTABLE_C));
	}

	@Nested
	class GenerateTableTest {

		private TuningSettings tuningSettings;

		@BeforeEach
		void setUp() {
			tuningSettings = new TuningSettings();
			tuningSettings.initialize(false); // PAL
		}

		@Test
		void generatesAnEightBitBellTable() {
			byte[] table = new byte[4];
			tuning.generateTable(table, 0, 4, 0, Timbre.BELL, 0x00, tuningSettings);
			assertEquals((byte) 124, table[0]);
			assertEquals((byte) 117, table[1]);
			assertEquals((byte) 110, table[2]);
			assertEquals((byte) 104, table[3]);
		}

		@Test
		void generatesAnEightBitPureATable() {
			// Semitone offset 48 matches how initTuning() actually calls this
			// table (dist_a_pure.table64khz(4) * notesPerOctave(12)) -
			// semitone 0 directly would ask for an implausibly low pitch that
			// clamps to 0xFF for every entry.
			byte[] table = new byte[4];
			tuning.generateTable(table, 0, 4, 48, Timbre.PURE_A, 0x00, tuningSettings);
			assertEquals((byte) 241, table[0]);
			assertEquals((byte) 227, table[1]);
			assertEquals((byte) 214, table[2]);
			assertEquals((byte) 202, table[3]);
		}

		@Test
		void generatesASixteenBitJoinedTableUsingTwoBytesPerEntry() {
			// audctl 0x50 = JOIN_12 (0x10) | CH1_179 (0x40) -> join16bit for
			// channel-agnostic table generation, so each entry is 2 bytes.
			// Semitone offset 24 matches dist_a_pure.table16bit(2) * notesPerOctave(12).
			byte[] table = new byte[8];
			tuning.generateTable(table, 0, 4, 24, Timbre.PURE_A, 0x50, tuningSettings);
			assertEquals((byte) 176, table[0]);
			assertEquals((byte) 105, table[1]);
			assertEquals((byte) 193, table[2]);
			assertEquals((byte) 99, table[3]);
			assertEquals((byte) 39, table[4]);
			assertEquals((byte) 94, table[5]);
			assertEquals((byte) 222, table[6]);
			assertEquals((byte) 88, table[7]);
		}
	}

	@Nested
	class InitTuningTest {

		private TuningSettings tuningSettings;
		private byte[] memory;

		@BeforeEach
		void setUp() {
			tuningSettings = new TuningSettings();
			tuningSettings.initialize(false); // PAL, the ratios inside included
			memory = new byte[0x600];
			tuning.initTuning(memory, tuningSettings);
		}

		@Test
		void generatesTheIdenticalPageForTheNtscDefaultsOnTheNtscClock() {
			// The NTSC default is the PAL default scaled by exactly the
			// clock ratio (TuningSettings.NTSC_A4_FREQUENCY): every pitch is
			// proportional to the base tuning and every AUDF is computed
			// from clock / pitch, so the scaling cancels before any rounding
			// and the whole page is bit-identical - the invariant that lets
			// one player binary serve both systems
			// (doc/rmt_tracker_drivers.md, "Tuning"). It holds for the
			// custom temperament's cents too, which setUp()'s initialize()
			// included.
			TuningSettings ntscSettings = new TuningSettings();
			ntscSettings.initialize(true);
			byte[] ntscMemory = new byte[0x600];
			new Tuning(Atari.FREQ_17_NTSC).initTuning(ntscMemory, ntscSettings);
			assertArrayEquals(memory, ntscMemory);
		}

		@Test
		void populatesTheDistortionTwoBellTableAtOffsetZero() {
			assertEquals((byte) 62, memory[0x000]);
			assertEquals((byte) 58, memory[0x001]);
		}

		@Test
		void populatesTheDistortionFourSmoothTableAtOffsetHundred() {
			assertEquals((byte) 23, memory[0x100]);
			assertEquals((byte) 23, memory[0x101]);
		}

		@Test
		void populatesTheDistortionAPureTableAtOffsetTwoHundred() {
			assertEquals((byte) 241, memory[0x200]);
			assertEquals((byte) 227, memory[0x201]);
		}

		@Test
		void populatesTheDistortionCBuzzyTableAtOffsetThreeHundred() {
			assertEquals((byte) 127, memory[0x300]);
			assertEquals((byte) 121, memory[0x301]);
		}

		@Test
		void populatesTheDistortionCGrittyTableAtOffsetFourHundred() {
			// The first entry clamps to 0xFF (255) - real behavior at this
			// table's actual starting semitone offset, not a test artifact.
			assertEquals((byte) 255, memory[0x400]);
			assertEquals((byte) 243, memory[0x401]);
		}

		@Test
		void populatesTheFifteenKhzPureATableAtOffsetFiveEightyOne() {
			// dist_a_pure.table15khz is written only at 0x580 (no 15kHz table
			// exists for Distortion 2/4/C - see initTuning()'s own comments).
			assertEquals((byte) 236, memory[0x580]);
			assertEquals((byte) 223, memory[0x581]);
		}

		@Test
		void populatesTheFifteenKhzBuzzyCTableAtOffsetFiveC0() {
			assertEquals((byte) 188, memory[0x5C0]);
			assertEquals((byte) 178, memory[0x5C1]);
		}

		@Test
		void generatesExactlyThePageTheDriverBinaryShips() {
			// asm/Patch-16/rmtdata.a65's page is kept equal to what this
			// generator produces for the PAL defaults, so that a module
			// assembled with the standalone player sounds as it did to the
			// composer in RMT (plan 37, T7). Comparing against the shipped
			// binary, which check-drivers.yml pins to the assembler source,
			// closes the chain rmtdata.a65 -> obx -> generator. The sawtooth
			// range ($500-$57F) is the one part no formula produces: it is
			// held as constants and copied (T6), and before T6 the generator
			// left it zero.
			Atari atari = new Atari();
			assertTrue(new AtariTrackerDriver(atari).loadRMTRoutines(TrackerDriverVersion.PATCH16) > 0);
			for (int offset = 0x000; offset < 0x600; offset++) {
				int at = offset;
				assertEquals(atari.getByteAt(Atari.RMT_FRQTABLES + at), memory[at] & 0xFF,
						() -> String.format("offset $%03X", at));
			}
		}

		@Test
		void getTruePitchCustomTemperamentUsesCentsPopulatedByInitTuning() {
			// The custom temperament reads Tuning's private custom[] array,
			// only ever populated as a side effect of initTuning() (from the
			// settings' cents since plan 59) - there's no public setter, so
			// this can only be characterized here, after setUp()'s
			// initTuning() call. The cents defaults are equal temperament
			// (0, 100, ..., 1100), so the custom path reproduces the
			// equal-temperament pitch.
			assertEquals(8.191361111461944, tuning.getTruePitch(TuningSettings.PAL_A4_FREQUENCY, Temperament.CUSTOM, 3, 0));
			assertEquals(tuning.getTruePitch(TuningSettings.PAL_A4_FREQUENCY, Temperament.EQUAL, 3, 0), tuning.getTruePitch(TuningSettings.PAL_A4_FREQUENCY, Temperament.CUSTOM, 3, 0));
		}

		@Test
		void initTuningThrowsWhenBasetuningIsZero() {
			assertThrows(IllegalStateException.class, () -> tuning.initTuning(memory, new TuningSettings()));
		}
	}
}
