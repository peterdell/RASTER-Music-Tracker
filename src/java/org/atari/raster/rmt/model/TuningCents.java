package org.atari.raster.rmt.model;

/**
 * The twelve tonic-based values of {@link Temperament#CUSTOM}, in musical
 * cents (1 cent = 1/1200 octave, 100 cents = one equal-tempered semitone).
 * {@code values[0]} is the tonic and always 0; the octave is fixed at 1200
 * (2:1) and not stored.
 *
 * <p>Replaces the former {@code TuningRatios} fractions (plan 59, decided
 * with ivop 2026-10-26): cents are the notation of the musicological
 * literature (Kirnberger III is 0, 90.225, 193.157, ...), and not every
 * temperament is expressible as a fraction - some contain square roots. A
 * multiplier {@code m} converts as {@code cents = 1200*log2(m)}; the table
 * generation applies {@code 2^(cents/1200)}. Ported in lockstep with
 * {@code TTuningCents} (src/cpp/TuningTypes.h/.cpp).
 */
public final class TuningCents {

	/** One cents value per semitone of the octave ({@link Tuning#NOTES_PER_OCTAVE}, the central definition of the 12). */
	public static final int COUNT = Tuning.NOTES_PER_OCTAVE;

	/**
	 * The ini file's key per semitone above the tonic - the interval names
	 * the former fraction lines used, so an old Tuning.ini reads naturally
	 * into the new model.
	 */
	public static final String[] NAMES = { "UNISON", "MIN_2ND", "MAJ_2ND", "MIN_3RD", "MAJ_3RD", "PERF_4TH", "TRITONE", "PERF_5TH", "MIN_6TH", "MAJ_6TH", "MIN_7TH", "MAJ_7TH" };

	/** Cents above the tonic, by semitone; {@code values[0]} stays 0. */
	public final double[] values = new double[COUNT];

	/** The default is equal temperament: 0, 100, ..., 1100. */
	public void initialize() {
		for (int i = 0; i < COUNT; i++) {
			values[i] = i * 100;
		}
	}

	public void copyFrom(TuningCents from) {
		System.arraycopy(from.values, 0, values, 0, COUNT);
	}
}
