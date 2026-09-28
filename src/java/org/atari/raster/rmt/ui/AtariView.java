package org.atari.raster.rmt.ui;

import org.atari.raster.rmt.model.Atari;

/**
 * Ported from CAtariView (src/cpp/AtariView.h/.cpp) - a mini-font hex dump
 * of 1 KB of the emulated Atari's memory from {@code $3000}. Only reachable
 * through {@code CSongUI::DrawVolumeAnalyzer()}'s {@code DEBUG_MEMORY}
 * flag, which is a compile-time {@code FALSE} in C++ - kept here for
 * completeness, exactly as unreachable.
 */
public final class AtariView {

	private final Canvas canvas;

	public AtariView(Canvas canvas) {
		this.canvas = canvas;
	}

	static String getAtariMemoryHexString(byte[] memory, int address, int length) {
		StringBuilder s = new StringBuilder();
		s.append(String.format("$%04X ", address));
		for (int i = 0; i < length; i++) {
			s.append(String.format("$%02X ", memory[address + i] & 0xFF));
		}
		return s.toString();
	}

	public void draw(Atari atari) {
		final int ADDRESS = 0x3000; // RMTPLAYR_TABLES;
		final int BPL = 32;
		final int BLOCK = 8;

		final byte[] memory = atari.getMemory();

		canvas.colorMini(TextMiniColor.GRAY).printMini("MEMORY").nextRow().nextRow();
		canvas.colorMini(TextMiniColor.WHITE);
		for (int d = 0; d < 32; d++) {
			canvas.printMini(getAtariMemoryHexString(memory, ADDRESS + BPL * d, BPL)).nextRow();
			if (d % BLOCK == BLOCK - 1) {
				canvas.nextRow();
			}
		}
	}
}
