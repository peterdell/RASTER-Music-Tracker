package org.atari.raster.rmt.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** Mirrors src/cpp/test/TuningTypesTests.cpp's TuningSettingsTest. */
class TuningSettingsTest {

	@Test
	void initializePal() {
		TuningSettings settings = new TuningSettings();
		settings.initialize(false);
		assertEquals(TuningSettings.PAL_A4_FREQUENCY, settings.a4Frequency);
		assertEquals(Tonic.A, settings.tonic);
		assertEquals(Temperament.EQUAL, settings.temperament);
	}

	@Test
	void initializeNtsc() {
		TuningSettings settings = new TuningSettings();
		settings.initialize(true);
		assertEquals(TuningSettings.NTSC_A4_FREQUENCY, settings.a4Frequency);
		assertEquals(Tonic.A, settings.tonic);
		assertEquals(Temperament.EQUAL, settings.temperament);
	}
}
