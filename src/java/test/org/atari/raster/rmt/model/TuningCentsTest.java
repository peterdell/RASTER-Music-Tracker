package org.atari.raster.rmt.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Mirrors src/cpp/test/TuningTypesTests.cpp's TuningCentsTest. */
class TuningCentsTest {

	private TuningCents cents;

	@BeforeEach
	void setUp() {
		cents = new TuningCents();
		cents.initialize();
	}

	@Test
	void defaultsAreEqualTemperament() {
		for (int i = 0; i < TuningCents.COUNT; i++) {
			assertEquals(i * 100, cents.values[i], "semitone " + i);
		}
	}

	@Test
	void namesCoverEverySemitone() {
		assertEquals(TuningCents.COUNT, TuningCents.NAMES.length);
		assertEquals("UNISON", TuningCents.NAMES[0]);
		assertEquals("MAJ_7TH", TuningCents.NAMES[11]);
	}

	@Test
	void copyFromCopiesAllValues() {
		TuningCents other = new TuningCents();
		other.values[4] = 386.3137138648348; // a just major third (5/4)
		cents.copyFrom(other);
		assertEquals(386.3137138648348, cents.values[4]);
		assertEquals(0, cents.values[1]); // other was never initialized
	}
}
