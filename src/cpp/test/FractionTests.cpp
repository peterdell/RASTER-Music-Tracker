#include "gtest/gtest.h"

#include "Fraction.h"

TEST(FractionTest, ConstructorDefaultIsZero) {
    CFraction f;
    EXPECT_EQ(f.numerator, 0);
    EXPECT_EQ(f.denominator, 1);
}

TEST(FractionTest, ConstructorFromIntegerHasDenominatorOne) {
    CFraction f(5);
    EXPECT_EQ(f.numerator, 5);
    EXPECT_EQ(f.denominator, 1);
}

TEST(FractionTest, ConstructorReducesToLowestTerms) {
    CFraction f(4, 8);
    EXPECT_EQ(f.numerator, 1);
    EXPECT_EQ(f.denominator, 2);
}

TEST(FractionTest, ConstructorMovesSignToNumerator) {
    CFraction f(1, -2);
    EXPECT_EQ(f.numerator, -1);
    EXPECT_EQ(f.denominator, 2);
}

TEST(FractionTest, ConstructorWithZeroDenominatorThrows) {
    EXPECT_THROW(CFraction(1, 0), std::invalid_argument);
}

TEST(FractionTest, AdditionReducesResult) {
    CFraction f = CFraction(1, 4) + CFraction(1, 4);
    EXPECT_EQ(f.numerator, 1);
    EXPECT_EQ(f.denominator, 2);
}

TEST(FractionTest, SubtractionProducesNegativeNumerator) {
    CFraction f = CFraction(1, 4) - CFraction(1, 2);
    EXPECT_EQ(f.numerator, -1);
    EXPECT_EQ(f.denominator, 4);
}

TEST(FractionTest, MultiplicationReducesResult) {
    CFraction f = CFraction(2, 3) * CFraction(3, 4);
    EXPECT_EQ(f.numerator, 1);
    EXPECT_EQ(f.denominator, 2);
}

TEST(FractionTest, DivisionReducesResult) {
    CFraction f = CFraction(1, 2) / CFraction(1, 4);
    EXPECT_EQ(f.numerator, 2);
    EXPECT_EQ(f.denominator, 1);
}

TEST(FractionTest, PlusEqualsAccumulates) {
    CFraction f(1, 4);
    f += CFraction(1, 4);
    EXPECT_EQ(f.numerator, 1);
    EXPECT_EQ(f.denominator, 2);
}

TEST(FractionTest, PreIncrementAddsOne) {
    CFraction f(1, 2);
    CFraction result = ++f;
    EXPECT_EQ(f.numerator, 3);
    EXPECT_EQ(f.denominator, 2);
    EXPECT_EQ(result.numerator, 3);
    EXPECT_EQ(result.denominator, 2);
}

TEST(FractionTest, PostIncrementReturnsOriginalValue) {
    CFraction f(1, 2);
    CFraction result = f++;
    EXPECT_EQ(result.numerator, 1);
    EXPECT_EQ(result.denominator, 2);
    EXPECT_EQ(f.numerator, 3);
    EXPECT_EQ(f.denominator, 2);
}

TEST(FractionTest, GreaterThanComparesValue) {
    EXPECT_TRUE(CFraction(1, 2) > CFraction(1, 3));
    EXPECT_FALSE(CFraction(1, 3) > CFraction(1, 2));
}

// Characterization test for existing (surprising) behavior: operator== checks
// whether the reduced difference's *denominator* is zero, but CFraction's own
// constructor/simplify() always leaves a non-zero denominator (see gcd()), so
// this operator currently evaluates to false for every pair of operands,
// including two fractions that represent the same value. No production code
// relies on this operator today (verified by repo-wide search), but a future
// cleanup/port should decide deliberately whether to fix or intentionally
// preserve this before anyone starts using it.
TEST(FractionTest, EqualityOperatorIsCurrentlyAlwaysFalse) {
    // Calling operator== explicitly (rather than via "a == b") sidesteps a
    // C++20 overload-resolution ambiguity: CFraction's implicit
    // operator double() makes "a == b" ambiguous between the member
    // operator== and the built-in double comparison once the compiler
    // synthesizes the reversed "b == a" candidate.
    EXPECT_FALSE(CFraction(1, 2).operator==(CFraction(1, 2)));
    EXPECT_FALSE(CFraction(1, 2).operator==(CFraction(2, 4)));
    EXPECT_FALSE(CFraction(1, 2).operator==(CFraction(1, 3)));
}

TEST(FractionTest, ConvertsToDouble) {
    CFraction f(1, 4);
    EXPECT_DOUBLE_EQ((double)f, 0.25);
}
