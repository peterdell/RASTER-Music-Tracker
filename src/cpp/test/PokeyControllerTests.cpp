#include "gtest/gtest.h"

#include "Atari.h"
#include "Keyboard.h"
#include "PokeyController.h"
#include "resource.h"

extern CAtari g_Atari;

// The Pokey Explorer's controller: the same key table as the Java port's PokeyControllerTest.
class PokeyControllerTest : public ::testing::Test {
  protected:
    static constexpr int AUDF = 0x3178;
    static constexpr int AUDC = 0x3180;
    static constexpr int AUDCTL = 0x3C69;
    static constexpr int SKCTL = 0x3CD3;

    byte* memory = nullptr;
    CPokeyController controller{ &g_Atari };

    void SetUp() override {
        memory = g_Atari.GetMemoryAt(0);
        memset(memory + AUDF, 0, 16);
        memory[AUDCTL] = 0;
        memory[SKCTL] = 0;
    }
};

TEST_F(PokeyControllerTest, TheDigitsIncreaseAndTheRowBelowDecreasesTheRegistersBy1OrWithShiftBy10) {
    const int increaseKeys[8] = { VK_1, VK_2, VK_3, VK_4, VK_5, VK_6, VK_7, VK_8 };
    const int decreaseKeys[8] = { VK_Q, VK_W, VK_E, VK_R, VK_T, VK_Y, VK_U, VK_I };
    for (int i = 0; i < 8; i++) {
        const int address = ((i % 2 == 0) ? AUDF : AUDC) + i / 2; // 1 3 5 7 AUDF0-3, 2 4 6 8 AUDC0-3
        memory[address] = 0x20;
        EXPECT_TRUE(controller.OnKeyDown(increaseKeys[i], 0, 0));
        EXPECT_EQ(memory[address], 0x21) << "key " << i;
        EXPECT_TRUE(controller.OnKeyDown(increaseKeys[i], 1, 0));
        EXPECT_EQ(memory[address], 0x31) << "key " << i;
        EXPECT_TRUE(controller.OnKeyDown(decreaseKeys[i], 1, 0));
        EXPECT_EQ(memory[address], 0x21) << "key " << i;
        EXPECT_TRUE(controller.OnKeyDown(decreaseKeys[i], 0, 0));
        EXPECT_EQ(memory[address], 0x20) << "key " << i;
    }
}

TEST_F(PokeyControllerTest, TheRegisterBytesWrap) {
    memory[AUDF] = 0xFF;
    controller.OnKeyDown(VK_1, 0, 0);
    EXPECT_EQ(memory[AUDF], 0x00);
    controller.OnKeyDown(VK_Q, 0, 0);
    EXPECT_EQ(memory[AUDF], 0xFF);
    memory[AUDC + 3] = 0xF8;
    controller.OnKeyDown(VK_8, 1, 0);
    EXPECT_EQ(memory[AUDC + 3], 0x08);
}

TEST_F(PokeyControllerTest, TheLettersToggleTheAudctlBitsAndMTheTwoTone) {
    const int keys[8] = { VK_C, VK_G, VK_F, VK_K, VK_J, VK_D, VK_A, VK_P }; // bits 0..7
    for (int bit = 0; bit < 8; bit++) {
        EXPECT_TRUE(controller.OnKeyDown(keys[bit], 0, 0));
        EXPECT_EQ(memory[AUDCTL] & (1 << bit), 1 << bit) << "bit " << bit;
        EXPECT_TRUE(controller.OnKeyDown(keys[bit], 1, 1)); // Shift and Control change nothing here
        EXPECT_EQ(memory[AUDCTL] & (1 << bit), 0) << "bit " << bit;
    }
    memory[SKCTL] = 0x03;
    EXPECT_TRUE(controller.OnKeyDown(VK_M, 0, 0));
    EXPECT_EQ(memory[SKCTL], 0x8B);
    controller.OnKeyDown(VK_M, 0, 0);
    EXPECT_EQ(memory[SKCTL], 0x03);
}

TEST_F(PokeyControllerTest, TheDivisorStepsBy01OrWithShiftBy1AndStaysBetween1And10000) {
    EXPECT_DOUBLE_EQ(controller.GetDivisor(), 1.0);
    controller.OnKeyDown(VK_OEM_MINUS, 0, 0);
    EXPECT_DOUBLE_EQ(controller.GetDivisor(), 1.0); // not below 1
    controller.OnKeyDown(VK_OEM_PLUS, 0, 0);
    EXPECT_NEAR(controller.GetDivisor(), 1.1, 1e-9);
    controller.OnKeyDown(VK_OEM_PLUS, 1, 0);
    EXPECT_NEAR(controller.GetDivisor(), 2.1, 1e-9);
    controller.OnKeyDown(VK_OEM_MINUS, 1, 0);
    EXPECT_NEAR(controller.GetDivisor(), 1.1, 1e-9);
    for (int i = 0; i < 11000; i++) {
        controller.OnKeyDown(VK_OEM_PLUS, 1, 0);
    }
    EXPECT_DOUBLE_EQ(controller.GetDivisor(), 10000.0); // not above 10000
}

TEST_F(PokeyControllerTest, EnterAndBackspaceCycleTheChannel) {
    EXPECT_EQ(controller.GetChannelIndex(), 0);
    controller.OnKeyDown(VK_RETURN, 0, 0);
    EXPECT_EQ(controller.GetChannelIndex(), 1);
    controller.OnKeyDown(VK_BACK, 0, 0); // had no decrement until 2026-09-30
    EXPECT_EQ(controller.GetChannelIndex(), 0);
    controller.OnKeyDown(VK_BACK, 0, 0);
    EXPECT_EQ(controller.GetChannelIndex(), 3); // wraps backwards
    controller.OnKeyDown(VK_RETURN, 0, 0);
    EXPECT_EQ(controller.GetChannelIndex(), 0); // wraps forwards
}

TEST_F(PokeyControllerTest, AnUnknownKeyIsNotHandledAndChangesNothing) {
    memory[AUDF] = 0x11;
    EXPECT_FALSE(controller.OnKeyDown(VK_Z, 0, 0)); // Z is not the explorer's key, Y is (the QWERTY row under the digits)
    EXPECT_FALSE(controller.OnKeyDown(VK_SPACE, 0, 0));
    EXPECT_EQ(memory[AUDF], 0x11);
    EXPECT_EQ(memory[AUDCTL], 0);
}

// The Pokey menu's items (CRmtView::OnCmdMsg -> OnCommand) do what the keys do.
TEST_F(PokeyControllerTest, TheMenuCommandsAreTheKeysOperations) {
    EXPECT_TRUE(CPokeyController::IsCommand(ID_POKEY_AUDF2_INCREASE_BY_10));
    EXPECT_TRUE(CPokeyController::IsCommand(ID_POKEY_DIVISOR_DECREASE_BY_1));
    EXPECT_FALSE(CPokeyController::IsCommand(ID_EDIT_ACTIVATE_POKEY_EXPLORER_MODE));

    memory[AUDF + 2] = 0x40;
    EXPECT_TRUE(controller.OnCommand(ID_POKEY_AUDF2_INCREASE_BY_10));
    EXPECT_EQ(memory[AUDF + 2], 0x50);
    EXPECT_TRUE(controller.OnCommand(ID_POKEY_AUDC1_DECREASE_BY_01));
    EXPECT_EQ(memory[AUDC + 1], 0xFF);
    EXPECT_TRUE(controller.OnCommand(ID_POKEY_AUDCTL_BIT4));
    EXPECT_EQ(memory[AUDCTL], 0x10);
    EXPECT_TRUE(controller.OnCommand(ID_POKEY_SKCTL_TWO_TONE_MODE));
    EXPECT_EQ(memory[SKCTL], 0x88);
    EXPECT_TRUE(controller.OnCommand(ID_POKEY_PREVIOUSCHANNEL));
    EXPECT_EQ(controller.GetChannelIndex(), 3);
    EXPECT_TRUE(controller.OnCommand(ID_POKEY_DIVISOR_INCREASE_BY_1));
    EXPECT_NEAR(controller.GetDivisor(), 2.0, 1e-9);
    EXPECT_FALSE(controller.OnCommand(ID_EDIT_ACTIVATE_POKEY_EXPLORER_MODE));
}
