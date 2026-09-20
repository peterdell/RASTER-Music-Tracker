#pragma once

class CAtari;

// Controller class for the Pokey Explorer mode.

class CPokeyController {

public:


    CPokeyController(CAtari* atari);


    int GetChannelIndex() const;
    double GetDivisor() const;

    BOOL OnKeyDown(int vk, int shift, int control);

private:

    void OnNextChannel();
    void OnPreviousChannel();

    void OnIncreaseDivisorBy01();
    void OnIncreaseDivisorBy10();
    void OnDecreaseDivisorBy01();
    void OnDecreaseDivisorBy10();

    void OnIncreaseAUDF0By01();
    void OnIncreaseAUDF0By10();
    void OnDecreaseAUDF0By01();
    void OnDecreaseAUDF0By10();

    void OnIncreaseAUDC0By01();
    void OnIncreaseAUDC0By10();
    void OnDecreaseAUDC0By01();
    void OnDecreaseAUDC0By10();

    void OnIncreaseAUDF1By01();
    void OnIncreaseAUDF1By10();
    void OnDecreaseAUDF1By01();
    void OnDecreaseAUDF1By10();

    void OnIncreaseAUDC1By01();
    void OnIncreaseAUDC1By10();
    void OnDecreaseAUDC1By01();
    void OnDecreaseAUDC1By10();

    void OnIncreaseAUDF2By01();
    void OnIncreaseAUDF2By10();
    void OnDecreaseAUDF2By01();
    void OnDecreaseAUDF2By10();

    void OnIncreaseAUDC2By01();
    void OnIncreaseAUDC2By10();
    void OnDecreaseAUDC2By01();
    void OnDecreaseAUDC2By10();

    void OnIncreaseAUDF3By01();
    void OnIncreaseAUDF3By10();
    void OnDecreaseAUDF3By01();
    void OnDecreaseAUDF3By10();

    void OnIncreaseAUDC3By01();
    void OnIncreaseAUDC3By10();
    void OnDecreaseAUDC3By01();
    void OnDecreaseAUDC3By10();

    void OnToggleAUDCTLBit0();
    void OnToggleAUDCTLBit1();
    void OnToggleAUDCTLBit2();
    void OnToggleAUDCTLBit3();
    void OnToggleAUDCTLBit4();
    void OnToggleAUDCTLBit5();
    void OnToggleAUDCTLBit6();
    void OnToggleAUDCTLBit7();

    void OnToggleTwoTone();

private:
    // Memory shadow location for POKEY registers.
    static constexpr MemoryAddress AUDF = 0x3178; // 8 bytes
    static constexpr MemoryAddress AUDC = 0x3180; // 8 bytes
    static constexpr MemoryAddress AUDCTL = 0x3C69;
    static constexpr MemoryAddress SKCTL = 0x3CD3;

    CAtari* m_atari;

    int m_channel_index;
    double m_divisor;

    void DecreaseDivisor(const double step);
    void IncreaseDivisor(const double step);

    void Decrease(const int address, const int step);
    void Increase(const int address, const int step);
    void Eor(const int address, const byte mask);

};

