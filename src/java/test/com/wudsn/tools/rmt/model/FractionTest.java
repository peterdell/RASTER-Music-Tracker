package com.wudsn.tools.rmt.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Mirrors src/cpp/test/FractionTests.cpp, characterizing the same behavior
 * (see Fraction's own javadoc for the one deliberate fix: the equality
 * operator's bug was corrected in both languages, not ported as-is).
 */
class FractionTest {

	@Test
	void constructorDefaultIsZero() {
		Fraction f = new Fraction();
		assertEquals(0, f.numerator);
		assertEquals(1, f.denominator);
	}

	@Test
	void constructorFromIntegerHasDenominatorOne() {
		Fraction f = new Fraction(5);
		assertEquals(5, f.numerator);
		assertEquals(1, f.denominator);
	}

	@Test
	void constructorReducesToLowestTerms() {
		Fraction f = new Fraction(4, 8);
		assertEquals(1, f.numerator);
		assertEquals(2, f.denominator);
	}

	@Test
	void constructorMovesSignToNumerator() {
		Fraction f = new Fraction(1, -2);
		assertEquals(-1, f.numerator);
		assertEquals(2, f.denominator);
	}

	@Test
	void constructorWithZeroDenominatorThrows() {
		assertThrows(IllegalArgumentException.class, () -> new Fraction(1, 0));
	}

	@Test
	void additionReducesResult() {
		Fraction f = new Fraction(1, 4).add(new Fraction(1, 4));
		assertEquals(1, f.numerator);
		assertEquals(2, f.denominator);
	}

	@Test
	void subtractionProducesNegativeNumerator() {
		Fraction f = new Fraction(1, 4).subtract(new Fraction(1, 2));
		assertEquals(-1, f.numerator);
		assertEquals(4, f.denominator);
	}

	@Test
	void multiplicationReducesResult() {
		Fraction f = new Fraction(2, 3).multiply(new Fraction(3, 4));
		assertEquals(1, f.numerator);
		assertEquals(2, f.denominator);
	}

	@Test
	void divisionReducesResult() {
		Fraction f = new Fraction(1, 2).divide(new Fraction(1, 4));
		assertEquals(2, f.numerator);
		assertEquals(1, f.denominator);
	}

	@Test
	void incrementAddsOne() {
		Fraction result = new Fraction(1, 2).increment();
		assertEquals(3, result.numerator);
		assertEquals(2, result.denominator);
	}

	@Test
	void greaterThanComparesValue() {
		assertTrue(new Fraction(1, 2).greaterThan(new Fraction(1, 3)));
		assertFalse(new Fraction(1, 3).greaterThan(new Fraction(1, 2)));
	}

	@Test
	void equalsComparesValue() {
		assertTrue(new Fraction(1, 2).equals(new Fraction(1, 2)));
		assertTrue(new Fraction(1, 2).equals(new Fraction(2, 4)));
		assertFalse(new Fraction(1, 2).equals(new Fraction(1, 3)));
	}

	@Test
	void convertsToDouble() {
		Fraction f = new Fraction(1, 4);
		assertEquals(0.25, f.doubleValue());
	}
}
