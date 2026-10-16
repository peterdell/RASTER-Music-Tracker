package org.atari.raster.rmt.model;

/**
 * Ported from TTuningRatios (src/cpp/TuningTypes.h/.cpp) - the ratio used
 * for each note (NOTE_L / NOTE_R), reusing the already-ported IntegerFraction.
 * Field names are camelCase (idiomatic Java) rather than the C++ struct's
 * SCREAMING_SNAKE_CASE, but map 1:1 to the same musical intervals in the
 * same order.
 */
public final class TuningRatios {

	public IntegerFraction unison;
	public IntegerFraction min2nd;
	public IntegerFraction maj2nd;
	public IntegerFraction min3rd;
	public IntegerFraction maj3rd;
	public IntegerFraction perf4th;
	public IntegerFraction tritone;
	public IntegerFraction perf5th;
	public IntegerFraction min6th;
	public IntegerFraction maj6th;
	public IntegerFraction min7th;
	public IntegerFraction maj7th;
	public IntegerFraction octave;

	public void initialize() {
		unison = new IntegerFraction(1, 1);
		min2nd = new IntegerFraction(40, 38);
		maj2nd = new IntegerFraction(10, 9);
		min3rd = new IntegerFraction(20, 17);
		maj3rd = new IntegerFraction(5, 4);
		perf4th = new IntegerFraction(4, 3);
		tritone = new IntegerFraction(60, 43);
		perf5th = new IntegerFraction(3, 2);
		min6th = new IntegerFraction(30, 19);
		maj6th = new IntegerFraction(5, 3);
		min7th = new IntegerFraction(30, 17);
		maj7th = new IntegerFraction(15, 8);
		octave = new IntegerFraction(2, 1);
	}
}
