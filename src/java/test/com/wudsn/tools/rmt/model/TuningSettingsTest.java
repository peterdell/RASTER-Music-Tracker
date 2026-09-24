package com.wudsn.tools.rmt.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** Mirrors src/cpp/test/TuningTypesTests.cpp's TuningSettingsTest. */
class TuningSettingsTest {

	@Test
	void initializePal() {
		TuningSettings settings = new TuningSettings();
		settings.initialize(false);
		assertEquals(440.83751645933, settings.basetuning);
		assertEquals(3, settings.basenote);
		assertEquals(0, settings.temperament);
	}

	@Test
	void initializeNtsc() {
		TuningSettings settings = new TuningSettings();
		settings.initialize(true);
		assertEquals(444.895778867913, settings.basetuning);
		assertEquals(3, settings.basenote);
		assertEquals(0, settings.temperament);
	}
}
