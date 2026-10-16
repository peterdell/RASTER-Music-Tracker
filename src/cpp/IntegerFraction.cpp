#include "IntegerFraction.h"

#include <stdexcept>

using std::invalid_argument;

CIntegerFraction::CIntegerFraction() : CIntegerFraction(0, 1) {
}

CIntegerFraction::CIntegerFraction(int n) : CIntegerFraction(n, 1) {
}

CIntegerFraction::CIntegerFraction(int n, int d) {
    if (d == 0) {
        throw invalid_argument("d");
    }
    numerator = n;
    denominator = d;
    simplify();
}

CIntegerFraction::~CIntegerFraction() {
}

CIntegerFraction CIntegerFraction::operator+(const CIntegerFraction& f) {
    int n = numerator * f.denominator + f.numerator * denominator;
    int d = denominator * f.denominator;

    CIntegerFraction ff(n, d);
    return ff;
}

CIntegerFraction CIntegerFraction::operator-(const CIntegerFraction& f) {
    int n = numerator * f.denominator - f.numerator * denominator;
    int d = denominator * f.denominator;

    CIntegerFraction ff(n, d);
    return ff;
}

CIntegerFraction CIntegerFraction::operator*(const CIntegerFraction& f) {
    int n = numerator * f.numerator;
    int d = denominator * f.denominator;

    CIntegerFraction ff(n, d);
    return ff;
}

CIntegerFraction CIntegerFraction::operator/(const CIntegerFraction& f) {
    int n = numerator * f.denominator;
    int d = denominator * f.numerator;

    CIntegerFraction ff(n, d);
    return ff;
}

CIntegerFraction CIntegerFraction::operator+=(const CIntegerFraction& f) {
    numerator = numerator * f.denominator + f.numerator * denominator;
    denominator = denominator * f.denominator;
    simplify();

    CIntegerFraction ff(numerator, denominator);
    return ff;
}

CIntegerFraction CIntegerFraction::operator++() {
    CIntegerFraction f(1, 1);
    numerator = numerator * f.denominator + f.numerator * denominator;
    denominator = denominator * f.denominator;
    simplify();

    CIntegerFraction ff(numerator, denominator);
    return ff;
}

CIntegerFraction CIntegerFraction::operator++(int) {
    CIntegerFraction ff(numerator, denominator);
    CIntegerFraction f(1, 1);
    numerator = numerator * f.denominator + f.numerator * denominator;
    denominator = denominator * f.denominator;
    simplify();

    return ff;
}

bool CIntegerFraction::operator>(const CIntegerFraction& f) {
    int n = numerator * f.denominator - f.numerator * denominator;
    int d = denominator * f.denominator;

    CIntegerFraction ff(n, d);
    return ff.numerator > 0;
}

bool CIntegerFraction::operator==(const CIntegerFraction& f) {
    int n = numerator * f.denominator - f.numerator * denominator;
    int d = denominator * f.denominator;

    CIntegerFraction ff(n, d);
    return ff.numerator == 0;
}

CIntegerFraction::operator double() {
    return (double)numerator / denominator;
}

void CIntegerFraction::simplify() {
    reduction();
    fix_sign();
}
void CIntegerFraction::fix_sign() {
    if (denominator < 0) {
        denominator = -denominator;
        numerator = -numerator;
    }
}
void CIntegerFraction::reduction() {
    int common = gcd(numerator, denominator);
    numerator /= common;
    denominator /= common;
}
int CIntegerFraction::gcd(int x, int y) {
    return y == 0 ? x : gcd(y, x % y);
}