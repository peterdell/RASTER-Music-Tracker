package org.atari.raster.rmt.ui;

import static org.atari.raster.rmt.ui.VirtualKey.*;

import org.atari.raster.rmt.model.Atari;
import org.atari.raster.rmt.model.NoteKeys;

/**
 * The port of {@code CPokeyController} ({@code PokeyController.h/.cpp}): the
 * Pokey Explorer mode's controller. With the RMT routines switched off
 * ({@code EditMode.POKEY_EXPLORER_MODE}, see {@code AudioEngine}) the keys
 * write the driver's POKEY shadow registers directly - the tracker driver's
 * {@code SetPokey} copies them to {@code $D200} every frame - and the
 * "POKEY REGISTERS" view details one channel ({@link #getChannelIndex()})
 * with a free {@link #getDivisor() divisor} in its pitch formula.
 *
 * <p>Keys ({@link #onKeyDown}, the physical QWERTY positions - on the
 * AZERTY and QWERTZ layouts the keys at the same positions,
 * {@link NoteKeys#toQwertyPosition}): Enter/Backspace next/previous channel; +/- the divisor by 0.1,
 * with Shift by 1.0; the digits 1 3 5 7 / 2 4 6 8 increase AUDF0-3 /
 * AUDC0-3 and the row below them Q E T U / W R Y I decrease them, by 1, with
 * Shift by $10 (byte arithmetic, wrapping); C G F K J D A P toggle the
 * AUDCTL bits 0-7; M toggles the two-tone bits of SKCTL. Control is not
 * looked at. The same operations are the Pokey menu's items
 * ({@code RmtCommands}). Owned by {@link RmtSession} (C++ hangs it on
 * {@code CSong}).
 */
public final class PokeyController {

	// Memory shadow location for POKEY registers (also used by MidiInput's channel 16 knobs).
	public static final int AUDF = 0x3178; // 8 bytes
	public static final int AUDC = 0x3180; // 8 bytes
	public static final int AUDCTL = 0x3C69;
	public static final int SKCTL = 0x3CD3;

	private static final double DIVISOR_MIN = 1;
	private static final double DIVISOR_MAX = 10000;

	private final Atari atari;

	private int channelIndex;
	private double divisor = 1.0;

	public PokeyController(Atari atari) {
		this.atari = atari;
	}

	/** {@code GetChannelIndex()}: the channel (0-3) the view details. */
	public int getChannelIndex() {
		return channelIndex;
	}

	/** {@code GetDivisor()}: the free divisor of the view's pitch formula, 1.0-10000.0. */
	public double getDivisor() {
		return divisor;
	}

	/** {@code OnKeyDown(vk, shift, control)}: true for a handled key; {@code keyboardLayout} makes the keys positional (the QWERTZ Z is the QWERTY Y). */
	public boolean onKeyDown(int vk, boolean shift, boolean control, org.atari.raster.rmt.model.KeyboardLayout keyboardLayout) {
		switch (NoteKeys.toQwertyPosition(vk, keyboardLayout)) {
		// General variables manipulation
		case VK_RETURN -> nextChannel();
		case VK_BACK -> previousChannel();
		case VK_OEM_PLUS -> increaseDivisor(shift ? 1.0 : 0.1);
		case VK_OEM_MINUS -> decreaseDivisor(shift ? 1.0 : 0.1);

		// AUDF channels
		case VK_1 -> increase(AUDF + 0, shift ? 0x10 : 0x01);
		case VK_Q -> decrease(AUDF + 0, shift ? 0x10 : 0x01);
		case VK_3 -> increase(AUDF + 1, shift ? 0x10 : 0x01);
		case VK_E -> decrease(AUDF + 1, shift ? 0x10 : 0x01);
		case VK_5 -> increase(AUDF + 2, shift ? 0x10 : 0x01);
		case VK_T -> decrease(AUDF + 2, shift ? 0x10 : 0x01);
		case VK_7 -> increase(AUDF + 3, shift ? 0x10 : 0x01);
		case VK_U -> decrease(AUDF + 3, shift ? 0x10 : 0x01);

		// AUDC channels
		case VK_2 -> increase(AUDC + 0, shift ? 0x10 : 0x01);
		case VK_W -> decrease(AUDC + 0, shift ? 0x10 : 0x01);
		case VK_4 -> increase(AUDC + 1, shift ? 0x10 : 0x01);
		case VK_R -> decrease(AUDC + 1, shift ? 0x10 : 0x01);
		case VK_6 -> increase(AUDC + 2, shift ? 0x10 : 0x01);
		case VK_Y -> decrease(AUDC + 2, shift ? 0x10 : 0x01);
		case VK_8 -> increase(AUDC + 3, shift ? 0x10 : 0x01);
		case VK_I -> decrease(AUDC + 3, shift ? 0x10 : 0x01);

		// AUDCTL bits
		case VK_P -> toggleAUDCTLBit(7);
		case VK_A -> toggleAUDCTLBit(6);
		case VK_D -> toggleAUDCTLBit(5);
		case VK_J -> toggleAUDCTLBit(4);
		case VK_K -> toggleAUDCTLBit(3);
		case VK_F -> toggleAUDCTLBit(2);
		case VK_G -> toggleAUDCTLBit(1);
		case VK_C -> toggleAUDCTLBit(0);

		case VK_M -> toggleTwoTone();

		default -> {
			return false;
		}
		}
		return true;
	}

	// ---- the operations (the Pokey menu's items) ----

	/** {@code OnNextChannel()}: 0-3, wrapping. */
	public void nextChannel() {
		channelIndex++;
		if (channelIndex > 3) {
			channelIndex = 0;
		}
	}

	/** {@code OnPreviousChannel()}: 3-0, wrapping (C++ lacked the decrement until 2026-09-30 - Backspace did nothing). */
	public void previousChannel() {
		channelIndex--;
		if (channelIndex < 0) {
			channelIndex = 3;
		}
	}

	/** {@code IncreaseDivisor(step)}, at most 10000. */
	public void increaseDivisor(double step) {
		divisor += step;
		if (divisor > DIVISOR_MAX) {
			divisor = DIVISOR_MAX;
		}
	}

	/** {@code DecreaseDivisor(step)}, at least 1. */
	public void decreaseDivisor(double step) {
		divisor -= step;
		if (divisor < DIVISOR_MIN) {
			divisor = DIVISOR_MIN;
		}
	}

	/** {@code Increase(address, step)}: the shadow byte plus {@code step}, wrapping. */
	public void increase(int address, int step) {
		byte[] memory = atari.getMemory();
		memory[address] = (byte) (memory[address] + step);
	}

	/** {@code Decrease(address, step)}. */
	public void decrease(int address, int step) {
		increase(address, -step);
	}

	/** {@code Eor(address, mask)}. */
	public void eor(int address, int mask) {
		byte[] memory = atari.getMemory();
		memory[address] = (byte) (memory[address] ^ mask);
	}

	/** {@code OnToggleAUDCTLBitN()}. */
	public void toggleAUDCTLBit(int bit) {
		eor(AUDCTL, 1 << bit);
	}

	/** {@code OnToggleTwoTone()}: SKCTL bits 3 and 7. */
	public void toggleTwoTone() {
		eor(SKCTL, 0x88);
	}
}
