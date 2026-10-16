#include "gtest/gtest.h"

#include "IntegerFraction.h"

TEST(IntegerFractionTest, ConstructorDefaultIsZero) {
    CIntegerFraction f;
    EXPECT_EQ(f.numerator, 0);
    EXPECT_EQ(f.denominator, 1);
}

TEST(IntegerFractionTest, ConstructorFromIntegerHasDenominatorOne) {
    CIntegerFraction f(5);
    EXPECT_EQ(f.numerator, 5);
    EXPECT_EQ(f.denominator, 1);
}

TEST(IntegerFractionTest, ConstructorReducesToLowestTerms) {
    CIntegerFraction f(4, 8);
    EXPECT_EQ(f.numerator, 1);
    EXPECT_EQ(f.denominator, 2);
}

TEST(IntegerFractionTest, ConstructorMovesSignToNumerator) {
    CIntegerFraction f(1, -2);
    EXPECT_EQ(f.numerator, -1);
    EXPECT_EQ(f.denominator, 2);
}

TEST(IntegerFractionTest, ConstructorWithZeroDenominatorThrows) {
    EXPECT_THROW(CIntegerFraction(1, 0), std::invalid_argument);
}

TEST(IntegerFractionTest, AdditionReducesResult) {
    CIntegerFraction f = CIntegerFraction(1, 4) + CIntegerFraction(1, 4);
    EXPECT_EQ(f.numerator, 1);
    EXPECT_EQ(f.denominator, 2);
}

TEST(IntegerFractionTest, SubtractionProducesNegativeNumerator) {
    CIntegerFraction f = CIntegerFraction(1, 4) - CIntegerFraction(1, 2);
    EXPECT_EQ(f.numerator, -1);
    EXPECT_EQ(f.denominator, 4);
}

TEST(IntegerFractionTest, MultiplicationReducesResult) {
    CIntegerFraction f = CIntegerFraction(2, 3) * CIntegerFraction(3, 4);
    EXPECT_EQ(f.numerator, 1);
    EXPECT_EQ(f.denominator, 2);
}

TEST(IntegerFractionTest, DivisionReducesResult) {
    CIntegerFraction f = CIntegerFraction(1, 2) / CIntegerFraction(1, 4);
    EXPECT_EQ(f.numerator, 2);
    EXPECT_EQ(f.denominator, 1);
}

TEST(IntegerFractionTest, PlusEqualsAccumulates) {
    CIntegerFraction f(1, 4);
    f += CIntegerFraction(1, 4);
    EXPECT_EQ(f.numerator, 1);
    EXPECT_EQ(f.denominator, 2);
}

TEST(IntegerFractionTest, PreIncrementAddsOne) {
    CIntegerFraction f(1, 2);
    CIntegerFraction result = ++f;
    EXPECT_EQ(f.numerator, 3);
    EXPECT_EQ(f.denominator, 2);
    EXPECT_EQ(result.numerator, 3);
    EXPECT_EQ(result.denominator, 2);
}

TEST(IntegerFractionTest, PostIncrementReturnsOriginalValue) {
    CIntegerFraction f(1, 2);
    CIntegerFraction result = f++;
    EXPECT_EQ(result.numerator, 1);
    EXPECT_EQ(result.denominator, 2);
    EXPECT_EQ(f.numerator, 3);
    EXPECT_EQ(f.denominator, 2);
}

TEST(IntegerFractionTest, GreaterThanComparesValue) {
    EXPECT_TRUE(CIntegerFraction(1, 2) > CIntegerFraction(1, 3));
    EXPECT_FALSE(CIntegerFraction(1, 3) > CIntegerFraction(1, 2));
}

// Fixed a real bug found while characterizing this operator: it checked
// whether the reduced difference's *denominator* was zero, but CIntegerFraction's
// own constructor/simplify() always leaves a non-zero denominator (see
// gcd()), so it evaluated to false for every pair of operands, including two
// fractions that represent the same value. No production code relied on this
// operator (verified by repo-wide search) - fixed to check the *numerator*
// instead, at the same time as the Java port (org.atari.raster.rmt.model.Fraction),
// per the user's explicit decision to fix this bug in both languages.
TEST(IntegerFractionTest, EqualityOperatorComparesValue) {
    // Calling operator== explicitly (rather than via "a == b") sidesteps a
    // C++20 overload-resolution ambiguity: CIntegerFraction's implicit
    // operator double() makes "a == b" ambiguous between the member
    // operator== and the built-in double comparison once the compiler
    // synthesizes the reversed "b == a" candidate.
    EXPECT_TRUE(CIntegerFraction(1, 2).operator==(CIntegerFraction(1, 2)));
    EXPECT_TRUE(CIntegerFraction(1, 2).operator==(CIntegerFraction(2, 4)));
    EXPECT_FALSE(CIntegerFraction(1, 2).operator==(CIntegerFraction(1, 3)));
}

TEST(IntegerFractionTest, ConvertsToDouble) {
    CIntegerFraction f(1, 4);
    EXPECT_DOUBLE_EQ((double)f, 0.25);
}
