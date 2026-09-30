package org.atari.raster.rmt.ui;

import static org.atari.raster.rmt.ui.VirtualKey.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.atari.raster.rmt.model.Atari;

/** {@link PokeyController} against {@code CPokeyController}: the same key table as {@code PokeyControllerTests.cpp}. */
class PokeyControllerTest {

	private Atari atari;
	private byte[] memory;
	private PokeyController controller;

	@BeforeEach
	void setUp() {
		atari = new Atari();
		memory = atari.getMemory();
		controller = new PokeyController(atari);
	}

	private int at(int address) {
		return memory[address] & 0xFF;
	}

	@Test
	void theDigitsIncreaseAndTheRowBelowDecreasesTheRegistersBy1OrWithShiftBy10() {
		int[] increaseKeys = { VK_1, VK_2, VK_3, VK_4, VK_5, VK_6, VK_7, VK_8 };
		int[] decreaseKeys = { VK_Q, VK_W, VK_E, VK_R, VK_T, VK_Y, VK_U, VK_I };
		for (int i = 0; i < 8; i++) {
			int address = (i % 2 == 0 ? PokeyController.AUDF : PokeyController.AUDC) + i / 2; // 1 3 5 7 AUDF0-3, 2 4 6 8 AUDC0-3
			memory[address] = 0x20;
			assertTrue(controller.onKeyDown(increaseKeys[i], false, false));
			assertEquals(0x21, at(address), "key " + i + " +1");
			assertTrue(controller.onKeyDown(increaseKeys[i], true, false));
			assertEquals(0x31, at(address), "key " + i + " +$10");
			assertTrue(controller.onKeyDown(decreaseKeys[i], true, false));
			assertEquals(0x21, at(address), "key " + i + " -$10");
			assertTrue(controller.onKeyDown(decreaseKeys[i], false, false));
			assertEquals(0x20, at(address), "key " + i + " -1");
		}
	}

	@Test
	void theRegisterBytesWrap() {
		memory[PokeyController.AUDF] = (byte) 0xFF;
		controller.onKeyDown(VK_1, false, false);
		assertEquals(0x00, at(PokeyController.AUDF));
		controller.onKeyDown(VK_Q, false, false);
		assertEquals(0xFF, at(PokeyController.AUDF));
		memory[PokeyController.AUDC + 3] = (byte) 0xF8;
		controller.onKeyDown(VK_8, true, false);
		assertEquals(0x08, at(PokeyController.AUDC + 3));
	}

	@Test
	void theLettersToggleTheAudctlBitsAndMTheTwoTone() {
		int[] keys = { VK_C, VK_G, VK_F, VK_K, VK_J, VK_D, VK_A, VK_P }; // bits 0..7
		for (int bit = 0; bit < 8; bit++) {
			assertTrue(controller.onKeyDown(keys[bit], false, false));
			assertEquals(1 << bit, at(PokeyController.AUDCTL) & (1 << bit), "bit " + bit + " set");
			assertTrue(controller.onKeyDown(keys[bit], true, true)); // Shift and Control change nothing here
			assertEquals(0, at(PokeyController.AUDCTL) & (1 << bit), "bit " + bit + " cleared");
		}
		memory[PokeyController.SKCTL] = 0x03;
		assertTrue(controller.onKeyDown(VK_M, false, false));
		assertEquals(0x8B, at(PokeyController.SKCTL));
		controller.onKeyDown(VK_M, false, false);
		assertEquals(0x03, at(PokeyController.SKCTL));
	}

	@Test
	void theDivisorStepsBy01OrWithShiftBy1AndStaysBetween1And10000() {
		assertEquals(1.0, controller.getDivisor());
		controller.onKeyDown(VK_OEM_MINUS, false, false);
		assertEquals(1.0, controller.getDivisor(), "not below 1");
		controller.onKeyDown(VK_OEM_PLUS, false, false);
		assertEquals(1.1, controller.getDivisor(), 1e-9);
		controller.onKeyDown(VK_OEM_PLUS, true, false);
		assertEquals(2.1, controller.getDivisor(), 1e-9);
		controller.onKeyDown(VK_OEM_MINUS, true, false);
		assertEquals(1.1, controller.getDivisor(), 1e-9);
		for (int i = 0; i < 11000; i++) {
			controller.onKeyDown(VK_OEM_PLUS, true, false);
		}
		assertEquals(10000.0, controller.getDivisor(), "not above 10000");
	}

	@Test
	void enterAndBackspaceCycleTheChannel() {
		assertEquals(0, controller.getChannelIndex());
		controller.onKeyDown(VK_RETURN, false, false);
		assertEquals(1, controller.getChannelIndex());
		controller.onKeyDown(VK_BACK, false, false); // C++ had no decrement here until 2026-09-30
		assertEquals(0, controller.getChannelIndex());
		controller.onKeyDown(VK_BACK, false, false);
		assertEquals(3, controller.getChannelIndex(), "wraps backwards");
		controller.onKeyDown(VK_RETURN, false, false);
		assertEquals(0, controller.getChannelIndex(), "wraps forwards");
	}

	@Test
	void anUnknownKeyIsNotHandledAndChangesNothing() {
		memory[PokeyController.AUDF] = 0x11;
		assertFalse(controller.onKeyDown(VK_Z, false, false)); // Z is not the explorer's key, Y is (the QWERTY row under the digits)
		assertFalse(controller.onKeyDown(VK_SPACE, false, false));
		assertEquals(0x11, at(PokeyController.AUDF));
		assertEquals(0, at(PokeyController.AUDCTL));
	}
}
