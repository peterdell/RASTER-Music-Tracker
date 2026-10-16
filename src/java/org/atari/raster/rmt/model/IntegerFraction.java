package org.atari.raster.rmt.model;

/**
 * Ported from CIntegerFraction (src/cpp/IntegerFraction.h/.cpp). C++ operator overloads
 * become named methods since Java has no user-defined operator overloading;
 * the mutating operators (operator+=, operator++) become methods returning a
 * new IntegerFraction, leaving this class immutable rather than replicating C++'s
 * in-place mutation - simpler and safer, with no behavioral difference for
 * any caller that uses the returned value (as every one already does).
 */
public final class IntegerFraction {

	public final int numerator;
	public final int denominator;

	public IntegerFraction() {
		this(0, 1);
	}

	public IntegerFraction(int n) {
		this(n, 1);
	}

	public IntegerFraction(int n, int d) {
		if (d == 0) {
			throw new IllegalArgumentException("The denominator must not be 0.");
		}
		int common = gcd(n, d);
		n /= common;
		d /= common;
		if (d < 0) {
			d = -d;
			n = -n;
		}
		numerator = n;
		denominator = d;
	}

	public IntegerFraction add(IntegerFraction f) {
		return new IntegerFraction(numerator * f.denominator + f.numerator * denominator, denominator * f.denominator);
	}

	public IntegerFraction subtract(IntegerFraction f) {
		return new IntegerFraction(numerator * f.denominator - f.numerator * denominator, denominator * f.denominator);
	}

	public IntegerFraction multiply(IntegerFraction f) {
		return new IntegerFraction(numerator * f.numerator, denominator * f.denominator);
	}

	public IntegerFraction divide(IntegerFraction f) {
		return new IntegerFraction(numerator * f.denominator, denominator * f.numerator);
	}

	/** C++'s operator++ (both pre- and post-increment become this, since IntegerFraction is immutable - see the caller for which value it keeps). */
	public IntegerFraction increment() {
		return add(new IntegerFraction(1, 1));
	}

	public boolean greaterThan(IntegerFraction f) {
		return numerator * f.denominator - f.numerator * denominator > 0;
	}

	/**
	 * Fixed a real bug found while porting: the C++ operator== checked
	 * whether the reduced difference's *denominator* was zero, but the
	 * constructor's own reduction always leaves a non-zero denominator, so
	 * it evaluated to false for every pair of operands, including two
	 * fractions representing the same value. No production code relied on
	 * it (verified by repo-wide search) - fixed here and in IntegerFraction.cpp's
	 * operator==, per the user's explicit decision to fix this bug in both
	 * languages rather than port it faithfully.
	 */
	@Override
	public boolean equals(Object obj) {
		if (!(obj instanceof IntegerFraction)) {
			return false;
		}
		IntegerFraction f = (IntegerFraction) obj;
		return numerator * f.denominator - f.numerator * denominator == 0;
	}

	@Override
	public int hashCode() {
		return Double.hashCode(doubleValue());
	}

	public double doubleValue() {
		return (double) numerator / denominator;
	}

	private static int gcd(int x, int y) {
		return y == 0 ? x : gcd(y, x % y);
	}
}
