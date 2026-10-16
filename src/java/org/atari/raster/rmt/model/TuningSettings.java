package org.atari.raster.rmt.model;

/**
 * Ported from TTuningSettings (src/cpp/TuningTypes.h/.cpp). C++ leaves
 * basetuning/basenote genuinely uninitialized until initialize() runs (only
 * temperament has an in-class default); Java always zero-initializes fields,
 * so that hazard doesn't carry over here - the two value set fields default
 * to the values initialize() sets rather than to null.
 *
 * <p>Where C++ carries basenote/temperament as raw ints, this class holds
 * the {@link BaseNote}/{@link Temperament} instances (see
 * plans/48_TUNING_VALUE_SETS_PLAN.md); the ini file still stores their
 * {@code getNumber()}.
 */
public final class TuningSettings {

	/** {@code DDV_MinMaxDouble}'s range of {@link #basetuning} ("should be more than enough..."). */
	public static final double BASETUNING_MIN = 6.875;
	public static final double BASETUNING_MAX = 7040;

	public double basetuning;
	public BaseNote basenote = BaseNote.A;
	public Temperament temperament = Temperament.EQUAL;
	/** The interval ratios {@link Temperament#CUSTOM} is played from - part of the tuning settings, not a sibling of them. */
	public final TuningRatios ratios = new TuningRatios();

	public void initialize(boolean ntsc) {
		basetuning = ntsc ? 444.895778867913 : 440.83751645933;
		basenote = BaseNote.A;
		temperament = Temperament.EQUAL;
		ratios.initialize();
	}
}
