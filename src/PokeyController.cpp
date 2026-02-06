#include "Atari.h"
#include "PokeyController.h"

CPokeyController::CPokeyController(CAtari* atari) : m_atari(atari), m_channel_index(0), m_divisor(1.0) {

}


int CPokeyController::GetChannelIndex() const {
    return m_channel_index;
}
double CPokeyController::GetDivisor() const {
    return m_divisor;
}


void CPokeyController::Decrease(const int address, const int step) {
    Increase(address, -step);
}

void CPokeyController::Increase(const int address, const int step) {
    const auto memory = m_atari->GetMemoryAt(0);
    memory[address] += step;
}

void CPokeyController::Eor(const int address, const byte mask) {
    const auto memory = m_atari->GetMemoryAt(0);
    memory[address] ^= mask;
}

void CPokeyController::OnNextChannel() {
    m_channel_index++;
    if (m_channel_index > 3) {
        m_channel_index = 0;
    }
}

void  CPokeyController::OnPreviousChannel() {
    if (m_channel_index < 0) {
        m_channel_index = 3;
    }
}

void  CPokeyController::OnIncreaseDivisorBy01() {
    IncreaseDivisor(0.1);
}
void  CPokeyController::OnIncreaseDivisorBy10() {
    IncreaseDivisor(1.0);

}
void  CPokeyController::OnDecreaseDivisorBy01() {
    DecreaseDivisor(0.1);

}
void  CPokeyController::OnDecreaseDivisorBy10() {
    DecreaseDivisor(1.0);
}

void CPokeyController::IncreaseDivisor(const double step) {
    m_divisor += step;

    if (m_divisor > 10000) {
        m_divisor = 10000;
    }
}

void CPokeyController::DecreaseDivisor(const double step) {
    m_divisor -= step;

    if (m_divisor < 1) {
        m_divisor = 1;
    }
}



void CPokeyController::OnIncreaseAUDF0By01() {
    Increase(AUDF + 0, 0x01);
}
void CPokeyController::OnIncreaseAUDF0By10() {
    Increase(AUDF + 0, 0x10);
}
void CPokeyController::OnDecreaseAUDF0By01() {
    Decrease(AUDF + 0, 0x01);
}
void CPokeyController::OnDecreaseAUDF0By10() {
    Decrease(AUDF + 0, 0x10);
}

void CPokeyController::OnDecreaseAUDC0By01() {
    Decrease(AUDC + 0, 0x01);
}
void CPokeyController::OnDecreaseAUDC0By10() {
    Decrease(AUDC + 0, 0x10);
}
void CPokeyController::OnIncreaseAUDC0By01() {
    Increase(AUDC + 0, 0x01);
}
void CPokeyController::OnIncreaseAUDC0By10() {
    Increase(AUDC + 0, 0x10);
}


void CPokeyController::OnIncreaseAUDF1By01() {
    Increase(AUDF + 1, 0x01);
}
void CPokeyController::OnIncreaseAUDF1By10() {
    Increase(AUDF + 1, 0x10);
}
void CPokeyController::OnDecreaseAUDF1By01() {
    Decrease(AUDF + 1, 0x01);
}
void CPokeyController::OnDecreaseAUDF1By10() {
    Decrease(AUDF + 1, 0x10);
}

void CPokeyController::OnDecreaseAUDC1By01() {
    Decrease(AUDC + 1, 0x01);
}
void CPokeyController::OnDecreaseAUDC1By10() {
    Decrease(AUDC + 1, 0x10);
}
void CPokeyController::OnIncreaseAUDC1By01() {
    Increase(AUDC + 1, 0x01);
}
void CPokeyController::OnIncreaseAUDC1By10() {
    Increase(AUDC + 1, 0x10);
}

void CPokeyController::OnIncreaseAUDF2By01() {
    Increase(AUDF + 2, 0x01);
}
void CPokeyController::OnIncreaseAUDF2By10() {
    Increase(AUDF + 2, 0x10);
}
void CPokeyController::OnDecreaseAUDF2By01() {
    Decrease(AUDF + 2, 0x01);
}
void CPokeyController::OnDecreaseAUDF2By10() {
    Decrease(AUDF + 2, 0x10);
}

void CPokeyController::OnDecreaseAUDC2By01() {
    Decrease(AUDC + 2, 0x01);
}
void CPokeyController::OnDecreaseAUDC2By10() {
    Decrease(AUDC + 2, 0x10);
}
void CPokeyController::OnIncreaseAUDC2By01() {
    Increase(AUDC + 2, 0x01);
}
void CPokeyController::OnIncreaseAUDC2By10() {
    Increase(AUDC + 2, 0x10);
}


void CPokeyController::OnIncreaseAUDF3By01() {
    Increase(AUDF + 3, 0x01);
}
void CPokeyController::OnIncreaseAUDF3By10() {
    Increase(AUDF + 3, 0x10);
}
void CPokeyController::OnDecreaseAUDF3By01() {
    Decrease(AUDF + 3, 0x01);
}
void CPokeyController::OnDecreaseAUDF3By10() {
    Decrease(AUDF + 3, 0x10);
}

void CPokeyController::OnDecreaseAUDC3By01() {
    Decrease(AUDC + 3, 0x01);
}
void CPokeyController::OnDecreaseAUDC3By10() {
    Decrease(AUDC + 3, 0x10);
}
void CPokeyController::OnIncreaseAUDC3By01() {
    Increase(AUDC + 3, 0x01);
}
void CPokeyController::OnIncreaseAUDC3By10() {
    Increase(AUDC + 3, 0x10);
}

void CPokeyController::OnToggleAUDCTLBit0() {
    Eor(AUDCTL, 0x01);
}
void CPokeyController::OnToggleAUDCTLBit1() {
    Eor(AUDCTL, 0x02);
}
void CPokeyController::OnToggleAUDCTLBit2() {
    Eor(AUDCTL, 0x04);
}
void CPokeyController::OnToggleAUDCTLBit3() {
    Eor(AUDCTL, 0x08);
}
void CPokeyController::OnToggleAUDCTLBit4() {
    Eor(AUDCTL, 0x10);
}
void CPokeyController::OnToggleAUDCTLBit5() {
    Eor(AUDCTL, 0x20);
}
void CPokeyController::OnToggleAUDCTLBit6() {
    Eor(AUDCTL, 0x40);
}
void CPokeyController::OnToggleAUDCTLBit7() {
    Eor(AUDCTL, 0x80);
}

void CPokeyController::OnToggleTwoTone() {
    Eor(SKCTL, 0x88);
}
