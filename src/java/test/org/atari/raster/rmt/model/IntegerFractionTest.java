package org.atari.raster.rmt.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Mirrors src/cpp/test/FractionTests.cpp, characterizing the same behavior
 * (see IntegerFraction's own javadoc for the one deliberate fix: the equality
 * operator's bug was corrected in both languages, not ported as-is).
 */
class IntegerFractionTest {

	@Test
	void constructorDefaultIsZero() {
		IntegerFraction f = new IntegerFraction();
		assertEquals(0, f.numerator);
		assertEquals(1, f.denominator);
	}

	@Test
	void constructorFromIntegerHasDenominatorOne() {
		IntegerFraction f = new IntegerFraction(5);
		assertEquals(5, f.numerator);
		assertEquals(1, f.denominator);
	}

	@Test
	void constructorReducesToLowestTerms() {
		IntegerFraction f = new IntegerFraction(4, 8);
		assertEquals(1, f.numerator);
		assertEquals(2, f.denominator);
	}

	@Test
	void constructorMovesSignToNumerator() {
		IntegerFraction f = new IntegerFraction(1, -2);
		assertEquals(-1, f.numerator);
		assertEquals(2, f.denominator);
	}

	@Test
	void constructorWithZeroDenominatorThrows() {
		assertThrows(IllegalArgumentException.class, () -> new IntegerFraction(1, 0));
	}

	@Test
	void additionReducesResult() {
		IntegerFraction f = new IntegerFraction(1, 4).add(new IntegerFraction(1, 4));
		assertEquals(1, f.numerator);
		assertEquals(2, f.denominator);
	}

	@Test
	void subtractionProducesNegativeNumerator() {
		IntegerFraction f = new IntegerFraction(1, 4).subtract(new IntegerFraction(1, 2));
		assertEquals(-1, f.numerator);
		assertEquals(4, f.denominator);
	}

	@Test
	void multiplicationReducesResult() {
		IntegerFraction f = new IntegerFraction(2, 3).multiply(new IntegerFraction(3, 4));
		assertEquals(1, f.numerator);
		assertEquals(2, f.denominator);
	}

	@Test
	void divisionReducesResult() {
		IntegerFraction f = new IntegerFraction(1, 2).divide(new IntegerFraction(1, 4));
		assertEquals(2, f.numerator);
		assertEquals(1, f.denominator);
	}

	@Test
	void incrementAddsOne() {
		IntegerFraction result = new IntegerFraction(1, 2).increment();
		assertEquals(3, result.numerator);
		assertEquals(2, result.denominator);
	}

	@Test
	void greaterThanComparesValue() {
		assertTrue(new IntegerFraction(1, 2).greaterThan(new IntegerFraction(1, 3)));
		assertFalse(new IntegerFraction(1, 3).greaterThan(new IntegerFraction(1, 2)));
	}

	@Test
	void equalsComparesValue() {
		assertTrue(new IntegerFraction(1, 2).equals(new IntegerFraction(1, 2)));
		assertTrue(new IntegerFraction(1, 2).equals(new IntegerFraction(2, 4)));
		assertFalse(new IntegerFraction(1, 2).equals(new IntegerFraction(1, 3)));
	}

	@Test
	void convertsToDouble() {
		IntegerFraction f = new IntegerFraction(1, 4);
		assertEquals(0.25, f.doubleValue());
	}
}
