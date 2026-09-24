package com.wudsn.tools.rmt.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Mirrors src/cpp/test/TuningTypesTests.cpp's TuningRatiosTest. */
class TuningRatiosTest {

	private TuningRatios ratios;

	@BeforeEach
	void setUp() {
		ratios = new TuningRatios();
		ratios.initialize();
	}

	@Test
	void unisonAndOctaveAreWholeNumberRatios() {
		assertEquals(1, ratios.unison.numerator);
		assertEquals(1, ratios.unison.denominator);
		assertEquals(2, ratios.octave.numerator);
		assertEquals(1, ratios.octave.denominator);
	}

	// Characterization test: the literal for min2nd is written as 40/38 in
	// TuningTypes.cpp, but Fraction's constructor always reduces to lowest
	// terms, so the stored value is actually 20/19.
	@Test
	void minorSecondIsStoredReduced() {
		assertEquals(20, ratios.min2nd.numerator);
		assertEquals(19, ratios.min2nd.denominator);
	}

	@Test
	void remainingRatiosMatchTheirLiterals() {
		assertEquals(10, ratios.maj2nd.numerator);
		assertEquals(9, ratios.maj2nd.denominator);

		assertEquals(20, ratios.min3rd.numerator);
		assertEquals(17, ratios.min3rd.denominator);

		assertEquals(5, ratios.maj3rd.numerator);
		assertEquals(4, ratios.maj3rd.denominator);

		assertEquals(4, ratios.perf4th.numerator);
		assertEquals(3, ratios.perf4th.denominator);

		assertEquals(60, ratios.tritone.numerator);
		assertEquals(43, ratios.tritone.denominator);

		assertEquals(3, ratios.perf5th.numerator);
		assertEquals(2, ratios.perf5th.denominator);

		assertEquals(30, ratios.min6th.numerator);
		assertEquals(19, ratios.min6th.denominator);

		assertEquals(5, ratios.maj6th.numerator);
		assertEquals(3, ratios.maj6th.denominator);

		assertEquals(30, ratios.min7th.numerator);
		assertEquals(17, ratios.min7th.denominator);

		assertEquals(15, ratios.maj7th.numerator);
		assertEquals(8, ratios.maj7th.denominator);
	}
}
