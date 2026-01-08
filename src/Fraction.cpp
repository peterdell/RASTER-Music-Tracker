#include "Fraction.h"

#include <stdexcept>

using std::invalid_argument;

CFraction::CFraction(int n, int d) {
    if (d == 0) throw invalid_argument("d");
    numerator = n;
    denominator = d;
    simplify();
}

CFraction::~CFraction() {}

CFraction CFraction::operator+(const CFraction& f) {
    int n = numerator * f.denominator + f.numerator * denominator;
    int d = denominator * f.denominator;

    CFraction ff(n, d);
    return ff;
}

CFraction CFraction::operator-(const CFraction& f) {
    int n = numerator * f.denominator - f.numerator * denominator;
    int d = denominator * f.denominator;

    CFraction ff(n, d);
    return ff;
}

CFraction CFraction::operator*(const CFraction& f) {
    int n = numerator * f.numerator;
    int d = denominator * f.denominator;

    CFraction ff(n, d);
    return ff;
}

CFraction CFraction::operator/(const CFraction& f) {
    int n = numerator * f.denominator;
    int d = denominator * f.numerator;

    CFraction ff(n, d);
    return ff;
}

CFraction CFraction::operator+=(const CFraction& f) {
    numerator = numerator * f.denominator + f.numerator * denominator;
    denominator = denominator * f.denominator;
    simplify();

    CFraction ff(numerator, denominator);
    return ff;
}

CFraction CFraction::operator++() {
    CFraction f(1, 1);
    numerator = numerator * f.denominator + f.numerator * denominator;
    denominator = denominator * f.denominator;
    simplify();

    CFraction ff(numerator, denominator);
    return ff;
}

CFraction CFraction::operator++(int) {
    CFraction ff(numerator, denominator);
    CFraction f(1, 1);
    numerator = numerator * f.denominator + f.numerator * denominator;
    denominator = denominator * f.denominator;
    simplify();

    return ff;
}

bool CFraction::operator>(const CFraction& f) {
    int n = numerator * f.denominator - f.numerator * denominator;
    int d = denominator * f.denominator;

    CFraction ff(n, d);
    return ff.numerator > 0;
}

bool CFraction::operator==(const CFraction& f) {
    int n = numerator * f.denominator - f.numerator * denominator;
    int d = denominator * f.denominator;

    CFraction ff(n, d);
    return ff.denominator == 0;
}

CFraction::operator double() {
    return (double)numerator / denominator;
}

void CFraction::simplify() {
    reduction();
    fix_sign();
}
void CFraction::fix_sign() {
    if (denominator < 0) {
        denominator = -denominator;
        numerator = -numerator;
    }
}
void CFraction::reduction() {
    int common = gcd(numerator, denominator);
    numerator /= common;
    denominator /= common;
}
int CFraction::gcd(int x, int y) {
    return y == 0 ? x : gcd(y, x % y);
}