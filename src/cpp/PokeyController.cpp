#include "Atari.h"
#include "PokeyController.h"

#include "Keyboard.h"
#include "resource.h"

CPokeyController::CPokeyController(CAtari* atari) : m_atari(atari), m_channel_index(0), m_divisor(1.0) {
}

// Whether id is one of the Pokey menu's explorer items.
static bool OnCommandKnown(UINT id) {
    switch (id) {
    case ID_POKEY_AUDF0_INCREASE_BY_01:
    case ID_POKEY_AUDF0_INCREASE_BY_10:
    case ID_POKEY_AUDF0_DECREASE_BY_01:
    case ID_POKEY_AUDF0_DECREASE_BY_10:
    case ID_POKEY_AUDC0_INCREASE_BY_01:
    case ID_POKEY_AUDC0_INCREASE_BY_10:
    case ID_POKEY_AUDC0_DECREASE_BY_01:
    case ID_POKEY_AUDC0_DECREASE_BY_10:
    case ID_POKEY_AUDF1_INCREASE_BY_01:
    case ID_POKEY_AUDF1_INCREASE_BY_10:
    case ID_POKEY_AUDF1_DECREASE_BY_01:
    case ID_POKEY_AUDF1_DECREASE_BY_10:
    case ID_POKEY_AUDC1_INCREASE_BY_01:
    case ID_POKEY_AUDC1_INCREASE_BY_10:
    case ID_POKEY_AUDC1_DECREASE_BY_01:
    case ID_POKEY_AUDC1_DECREASE_BY_10:
    case ID_POKEY_AUDF2_INCREASE_BY_01:
    case ID_POKEY_AUDF2_INCREASE_BY_10:
    case ID_POKEY_AUDF2_DECREASE_BY_01:
    case ID_POKEY_AUDF2_DECREASE_BY_10:
    case ID_POKEY_AUDC2_INCREASE_BY_01:
    case ID_POKEY_AUDC2_INCREASE_BY_10:
    case ID_POKEY_AUDC2_DECREASE_BY_01:
    case ID_POKEY_AUDC2_DECREASE_BY_10:
    case ID_POKEY_AUDF3_INCREASE_BY_01:
    case ID_POKEY_AUDF3_INCREASE_BY_10:
    case ID_POKEY_AUDF3_DECREASE_BY_01:
    case ID_POKEY_AUDF3_DECREASE_BY_10:
    case ID_POKEY_AUDC3_INCREASE_BY_01:
    case ID_POKEY_AUDC3_INCREASE_BY_10:
    case ID_POKEY_AUDC3_DECREASE_BY_01:
    case ID_POKEY_AUDC3_DECREASE_BY_10:
    case ID_POKEY_AUDCTL_BIT0:
    case ID_POKEY_AUDCTL_BIT1:
    case ID_POKEY_AUDCTL_BIT2:
    case ID_POKEY_AUDCTL_BIT3:
    case ID_POKEY_AUDCTL_BIT4:
    case ID_POKEY_AUDCTL_BIT5:
    case ID_POKEY_AUDCTL_BIT6:
    case ID_POKEY_AUDCTL_BIT7:
    case ID_POKEY_SKCTL_TWO_TONE_MODE:
    case ID_POKEY_NEXTCHANNEL:
    case ID_POKEY_PREVIOUSCHANNEL:
    case ID_POKEY_DIVISOR_INCREASE_BY_01:
    case ID_POKEY_DIVISOR_INCREASE_BY_1:
    case ID_POKEY_DIVISOR_DECREASE_BY_01:
    case ID_POKEY_DIVISOR_DECREASE_BY_1:
        return true;
    default:
        return false;
    }
}

BOOL CPokeyController::OnCommand(UINT id) {
    switch (id) {
    case ID_POKEY_AUDF0_INCREASE_BY_01:
        OnIncreaseAUDF0By01();
        return TRUE;
    case ID_POKEY_AUDF0_INCREASE_BY_10:
        OnIncreaseAUDF0By10();
        return TRUE;
    case ID_POKEY_AUDF0_DECREASE_BY_01:
        OnDecreaseAUDF0By01();
        return TRUE;
    case ID_POKEY_AUDF0_DECREASE_BY_10:
        OnDecreaseAUDF0By10();
        return TRUE;
    case ID_POKEY_AUDC0_INCREASE_BY_01:
        OnIncreaseAUDC0By01();
        return TRUE;
    case ID_POKEY_AUDC0_INCREASE_BY_10:
        OnIncreaseAUDC0By10();
        return TRUE;
    case ID_POKEY_AUDC0_DECREASE_BY_01:
        OnDecreaseAUDC0By01();
        return TRUE;
    case ID_POKEY_AUDC0_DECREASE_BY_10:
        OnDecreaseAUDC0By10();
        return TRUE;
    case ID_POKEY_AUDF1_INCREASE_BY_01:
        OnIncreaseAUDF1By01();
        return TRUE;
    case ID_POKEY_AUDF1_INCREASE_BY_10:
        OnIncreaseAUDF1By10();
        return TRUE;
    case ID_POKEY_AUDF1_DECREASE_BY_01:
        OnDecreaseAUDF1By01();
        return TRUE;
    case ID_POKEY_AUDF1_DECREASE_BY_10:
        OnDecreaseAUDF1By10();
        return TRUE;
    case ID_POKEY_AUDC1_INCREASE_BY_01:
        OnIncreaseAUDC1By01();
        return TRUE;
    case ID_POKEY_AUDC1_INCREASE_BY_10:
        OnIncreaseAUDC1By10();
        return TRUE;
    case ID_POKEY_AUDC1_DECREASE_BY_01:
        OnDecreaseAUDC1By01();
        return TRUE;
    case ID_POKEY_AUDC1_DECREASE_BY_10:
        OnDecreaseAUDC1By10();
        return TRUE;
    case ID_POKEY_AUDF2_INCREASE_BY_01:
        OnIncreaseAUDF2By01();
        return TRUE;
    case ID_POKEY_AUDF2_INCREASE_BY_10:
        OnIncreaseAUDF2By10();
        return TRUE;
    case ID_POKEY_AUDF2_DECREASE_BY_01:
        OnDecreaseAUDF2By01();
        return TRUE;
    case ID_POKEY_AUDF2_DECREASE_BY_10:
        OnDecreaseAUDF2By10();
        return TRUE;
    case ID_POKEY_AUDC2_INCREASE_BY_01:
        OnIncreaseAUDC2By01();
        return TRUE;
    case ID_POKEY_AUDC2_INCREASE_BY_10:
        OnIncreaseAUDC2By10();
        return TRUE;
    case ID_POKEY_AUDC2_DECREASE_BY_01:
        OnDecreaseAUDC2By01();
        return TRUE;
    case ID_POKEY_AUDC2_DECREASE_BY_10:
        OnDecreaseAUDC2By10();
        return TRUE;
    case ID_POKEY_AUDF3_INCREASE_BY_01:
        OnIncreaseAUDF3By01();
        return TRUE;
    case ID_POKEY_AUDF3_INCREASE_BY_10:
        OnIncreaseAUDF3By10();
        return TRUE;
    case ID_POKEY_AUDF3_DECREASE_BY_01:
        OnDecreaseAUDF3By01();
        return TRUE;
    case ID_POKEY_AUDF3_DECREASE_BY_10:
        OnDecreaseAUDF3By10();
        return TRUE;
    case ID_POKEY_AUDC3_INCREASE_BY_01:
        OnIncreaseAUDC3By01();
        return TRUE;
    case ID_POKEY_AUDC3_INCREASE_BY_10:
        OnIncreaseAUDC3By10();
        return TRUE;
    case ID_POKEY_AUDC3_DECREASE_BY_01:
        OnDecreaseAUDC3By01();
        return TRUE;
    case ID_POKEY_AUDC3_DECREASE_BY_10:
        OnDecreaseAUDC3By10();
        return TRUE;
    case ID_POKEY_AUDCTL_BIT0:
        OnToggleAUDCTLBit0();
        return TRUE;
    case ID_POKEY_AUDCTL_BIT1:
        OnToggleAUDCTLBit1();
        return TRUE;
    case ID_POKEY_AUDCTL_BIT2:
        OnToggleAUDCTLBit2();
        return TRUE;
    case ID_POKEY_AUDCTL_BIT3:
        OnToggleAUDCTLBit3();
        return TRUE;
    case ID_POKEY_AUDCTL_BIT4:
        OnToggleAUDCTLBit4();
        return TRUE;
    case ID_POKEY_AUDCTL_BIT5:
        OnToggleAUDCTLBit5();
        return TRUE;
    case ID_POKEY_AUDCTL_BIT6:
        OnToggleAUDCTLBit6();
        return TRUE;
    case ID_POKEY_AUDCTL_BIT7:
        OnToggleAUDCTLBit7();
        return TRUE;
    case ID_POKEY_SKCTL_TWO_TONE_MODE:
        OnToggleTwoTone();
        return TRUE;
    case ID_POKEY_NEXTCHANNEL:
        OnNextChannel();
        return TRUE;
    case ID_POKEY_PREVIOUSCHANNEL:
        OnPreviousChannel();
        return TRUE;
    case ID_POKEY_DIVISOR_INCREASE_BY_01:
        OnIncreaseDivisorBy01();
        return TRUE;
    case ID_POKEY_DIVISOR_INCREASE_BY_1:
        OnIncreaseDivisorBy10();
        return TRUE;
    case ID_POKEY_DIVISOR_DECREASE_BY_01:
        OnDecreaseDivisorBy01();
        return TRUE;
    case ID_POKEY_DIVISOR_DECREASE_BY_1:
        OnDecreaseDivisorBy10();
        return TRUE;
    default:
        return FALSE;
    }
}

BOOL CPokeyController::OnKeyDown(int vk, int shift, int control) {
    switch (vk) {
        //General variables manipulation

    case VK_RETURN:
        OnNextChannel();
        break;

    case VK_BACK:
        OnPreviousChannel();
        break;

    case VK_OEM_PLUS:
        if (shift) {
            OnIncreaseDivisorBy10();
        } else {
            OnIncreaseDivisorBy01();
        }
        break;

    case VK_OEM_MINUS:
        if (shift) {
            OnDecreaseDivisorBy10();
        } else {
            OnDecreaseDivisorBy01();
        }
        break;

        //AUDF channels

    case VK_1:
        if (shift) {
            OnIncreaseAUDF0By10();
        } else {
            OnIncreaseAUDF0By01();
        }
        break;

    case VK_Q:
        if (shift) {
            OnDecreaseAUDF0By10();
        } else {
            OnDecreaseAUDF0By01();
        }
        break;

    case VK_3:
        if (shift) {
            OnIncreaseAUDF1By10();
        } else {
            OnIncreaseAUDF1By01();
        }
        break;

    case VK_E:
        if (shift) {
            OnDecreaseAUDF1By10();
        } else {
            OnDecreaseAUDF1By01();
        }
        break;

    case VK_5:
        if (shift) {
            OnIncreaseAUDF2By10();
        } else {
            OnIncreaseAUDF2By01();
        }
        break;

    case VK_T:
        if (shift) {
            OnDecreaseAUDF2By10();
        } else {
            OnDecreaseAUDF2By01();
        }
        break;

    case VK_7:
        if (shift) {
            OnIncreaseAUDF3By10();
        } else {
            OnIncreaseAUDF3By01();
        }
        break;

    case VK_U:
        if (shift) {
            OnDecreaseAUDF3By10();
        } else {
            OnDecreaseAUDF3By01();
        }
        break;

        //AUDC channels

    case VK_2:
        if (shift) {
            OnIncreaseAUDC0By10();
        } else {
            OnIncreaseAUDC0By01();
        }
        break;

    case VK_W:
        if (shift) {
            OnDecreaseAUDC0By10();
        } else {
            OnDecreaseAUDC0By01();
        }
        break;

    case VK_4:
        if (shift) {
            OnIncreaseAUDC1By10();
        } else {
            OnIncreaseAUDC1By01();
        }
        break;

    case VK_R:
        if (shift) {
            OnDecreaseAUDC1By10();
        } else {
            OnDecreaseAUDC1By01();
        }
        break;

    case VK_6:
        if (shift) {
            OnIncreaseAUDC2By10();
        } else {
            OnIncreaseAUDC2By01();
        }
        break;

    case VK_Y:
        if (shift) {
            OnDecreaseAUDC2By10();
        } else {
            OnDecreaseAUDC2By01();
        }
        break;

    case VK_8:
        if (shift) {
            OnIncreaseAUDC3By10();
        } else {
            OnIncreaseAUDC3By01();
        }
        break;

    case VK_I:
        if (shift) {
            OnDecreaseAUDC3By10();
        } else {
            OnDecreaseAUDC3By01();
        }
        break;

        //AUDCTL bits

    case VK_P:
        OnToggleAUDCTLBit7();
        break;

    case VK_A:
        OnToggleAUDCTLBit6();
        break;

    case VK_D:
        OnToggleAUDCTLBit5();
        break;

    case VK_J:
        OnToggleAUDCTLBit4();
        break;

    case VK_K:
        OnToggleAUDCTLBit3();
        break;

    case VK_F:
        OnToggleAUDCTLBit2();
        break;

    case VK_G:
        OnToggleAUDCTLBit1();
        break;

    case VK_C:
        OnToggleAUDCTLBit0();
        break;

    case VK_M:
        OnToggleTwoTone();
        break;

    default:
        return FALSE;
    }
    return TRUE;
}

bool CPokeyController::IsCommand(UINT id) {
    return OnCommandKnown(id);
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

void CPokeyController::OnPreviousChannel() {
    m_channel_index--; // missing until 2026-09-30: Backspace did nothing
    if (m_channel_index < 0) {
        m_channel_index = 3;
    }
}

void CPokeyController::OnIncreaseDivisorBy01() {
    IncreaseDivisor(0.1);
}
void CPokeyController::OnIncreaseDivisorBy10() {
    IncreaseDivisor(1.0);
}
void CPokeyController::OnDecreaseDivisorBy01() {
    DecreaseDivisor(0.1);
}
void CPokeyController::OnDecreaseDivisorBy10() {
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
