#pragma once

class CFraction {

public:
    int numerator;
    int denominator;

public:
    CFraction();
    CFraction(int n);
    CFraction(int n, int d);
    ~CFraction();
    CFraction operator+(const CFraction& f);
    CFraction operator-(const CFraction& f);
    CFraction operator*(const CFraction& f);
    CFraction operator/(const CFraction& f);
    CFraction operator+=(const CFraction& f);
    CFraction operator++();
    CFraction operator++(int);
    bool operator>(const CFraction& f);
    bool operator==(const CFraction& f);
    operator double();

private:
    void simplify();
    void fix_sign();
    void reduction();
    int gcd(int x, int y);
};
