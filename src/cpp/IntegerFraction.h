#pragma once

class CIntegerFraction {

public:
    int numerator;
    int denominator;

public:
    CIntegerFraction();
    CIntegerFraction(int n);
    CIntegerFraction(int n, int d);
    ~CIntegerFraction();
    CIntegerFraction operator+(const CIntegerFraction& f);
    CIntegerFraction operator-(const CIntegerFraction& f);
    CIntegerFraction operator*(const CIntegerFraction& f);
    CIntegerFraction operator/(const CIntegerFraction& f);
    CIntegerFraction operator+=(const CIntegerFraction& f);
    CIntegerFraction operator++();
    CIntegerFraction operator++(int);
    bool operator>(const CIntegerFraction& f);
    bool operator==(const CIntegerFraction& f);
    operator double() const;

private:
    void simplify();
    void fix_sign();
    void reduction();
    int gcd(int x, int y);
};
