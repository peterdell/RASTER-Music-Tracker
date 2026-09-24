package com.wudsn.tools.rmt.model;

/**
 * Ported from TTuningSettings (src/cpp/TuningTypes.h/.cpp). C++ leaves
 * basetuning/basenote genuinely uninitialized until initialize() runs (only
 * temperament has an in-class default); Java always zero-initializes fields,
 * so that hazard doesn't carry over here.
 */
public final class TuningSettings {

	public double basetuning;
	public int basenote;
	public int temperament = 0;

	public void initialize(boolean ntsc) {
		basetuning = ntsc ? 444.895778867913 : 440.83751645933;
		basenote = 3;
		temperament = 0;
	}
}
