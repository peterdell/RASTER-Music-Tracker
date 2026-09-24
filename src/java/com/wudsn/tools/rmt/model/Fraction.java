package com.wudsn.tools.rmt.model;

/**
 * Ported from CFraction (src/cpp/Fraction.h/.cpp). C++ operator overloads
 * become named methods since Java has no user-defined operator overloading;
 * the mutating operators (operator+=, operator++) become methods returning a
 * new Fraction, leaving this class immutable rather than replicating C++'s
 * in-place mutation - simpler and safer, with no behavioral difference for
 * any caller that uses the returned value (as every one already does).
 */
public final class Fraction {

	public final int numerator;
	public final int denominator;

	public Fraction() {
		this(0, 1);
	}

	public Fraction(int n) {
		this(n, 1);
	}

	public Fraction(int n, int d) {
		if (d == 0) {
			throw new IllegalArgumentException("d");
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

	public Fraction add(Fraction f) {
		return new Fraction(numerator * f.denominator + f.numerator * denominator, denominator * f.denominator);
	}

	public Fraction subtract(Fraction f) {
		return new Fraction(numerator * f.denominator - f.numerator * denominator, denominator * f.denominator);
	}

	public Fraction multiply(Fraction f) {
		return new Fraction(numerator * f.numerator, denominator * f.denominator);
	}

	public Fraction divide(Fraction f) {
		return new Fraction(numerator * f.denominator, denominator * f.numerator);
	}

	/** C++'s operator++ (both pre- and post-increment become this, since Fraction is immutable - see the caller for which value it keeps). */
	public Fraction increment() {
		return add(new Fraction(1, 1));
	}

	public boolean greaterThan(Fraction f) {
		return numerator * f.denominator - f.numerator * denominator > 0;
	}

	/**
	 * Fixed a real bug found while porting: the C++ operator== checked
	 * whether the reduced difference's *denominator* was zero, but the
	 * constructor's own reduction always leaves a non-zero denominator, so
	 * it evaluated to false for every pair of operands, including two
	 * fractions representing the same value. No production code relied on
	 * it (verified by repo-wide search) - fixed here and in Fraction.cpp's
	 * operator==, per the user's explicit decision to fix this bug in both
	 * languages rather than port it faithfully.
	 */
	@Override
	public boolean equals(Object obj) {
		if (!(obj instanceof Fraction)) {
			return false;
		}
		Fraction f = (Fraction) obj;
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
