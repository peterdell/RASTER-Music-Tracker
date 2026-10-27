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

	/**
	 * The default A-4 reference pitch, RMT's traditional tuning rather than
	 * concert pitch 440 Hz: the Patch 16 player's shipped frequency tables
	 * were generated with exactly this value.
	 */
	public static final double PAL_A4_FREQUENCY = 440.83751645933;

	/**
	 * The PAL default scaled by exactly the machine-clock ratio - the same
	 * conversion {@code RmtSession.setNTSC()} applies - so the two defaults
	 * generate bit-identical frequency tables by construction
	 * (444.8957788679134, which both config writers' 16 significant digits
	 * round-trip; a decimal literal here was truncated once).
	 */
	public static final double NTSC_A4_FREQUENCY = PAL_A4_FREQUENCY * Atari.FREQ_17_NTSC / Atari.FREQ_17_PAL;

	public double basetuning;
	public BaseNote basenote = BaseNote.A;
	public Temperament temperament = Temperament.EQUAL;
	/** The cents {@link Temperament#CUSTOM} is played from - part of the tuning settings, not a sibling of them. */
	public final TuningCents cents = new TuningCents();

	public void initialize(boolean ntsc) {
		basetuning = ntsc ? NTSC_A4_FREQUENCY : PAL_A4_FREQUENCY;
		basenote = BaseNote.A;
		temperament = Temperament.EQUAL;
		cents.initialize();
	}
}
