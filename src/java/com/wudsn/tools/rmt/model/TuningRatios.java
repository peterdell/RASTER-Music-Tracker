package com.wudsn.tools.rmt.model;

/**
 * Ported from TTuningRatios (src/cpp/TuningTypes.h/.cpp) - the ratio used
 * for each note (NOTE_L / NOTE_R), reusing the already-ported Fraction.
 * Field names are camelCase (idiomatic Java) rather than the C++ struct's
 * SCREAMING_SNAKE_CASE, but map 1:1 to the same musical intervals in the
 * same order.
 */
public final class TuningRatios {

	public Fraction unison;
	public Fraction min2nd;
	public Fraction maj2nd;
	public Fraction min3rd;
	public Fraction maj3rd;
	public Fraction perf4th;
	public Fraction tritone;
	public Fraction perf5th;
	public Fraction min6th;
	public Fraction maj6th;
	public Fraction min7th;
	public Fraction maj7th;
	public Fraction octave;

	public void initialize() {
		unison = new Fraction(1, 1);
		min2nd = new Fraction(40, 38);
		maj2nd = new Fraction(10, 9);
		min3rd = new Fraction(20, 17);
		maj3rd = new Fraction(5, 4);
		perf4th = new Fraction(4, 3);
		tritone = new Fraction(60, 43);
		perf5th = new Fraction(3, 2);
		min6th = new Fraction(30, 19);
		maj6th = new Fraction(5, 3);
		min7th = new Fraction(30, 17);
		maj7th = new Fraction(15, 8);
		octave = new Fraction(2, 1);
	}
}
