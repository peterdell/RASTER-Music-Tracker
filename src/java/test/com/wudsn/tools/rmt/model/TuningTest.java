package com.wudsn.tools.rmt.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Mirrors src/cpp/test/TuningTests.cpp's GetPitch/GetAUDF/GetPOKEYPitch
 * coverage - the pure-math subset of CTuning ported so far (see Tuning's own
 * javadoc for why GenerateTable()/InitTuning() aren't ported yet). Expected
 * values were captured by running the actual C++ implementation (a "golden
 * master"/characterization approach), not hand-derived.
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
}
