package com.wudsn.tools.rmt.model;

import net.sf.asap.ASAP;

/**
 * The emulated 6502 + POKEY pair behind live playback - what
 * {@code sa_c6502.dll}/{@code sa_pokey.dll} are to the C++ program: ASAP's
 * CPU and POKEY emulation in its "RMT mode" (see the additions at the end
 * of {@code net.sf.asap.ASAP}). Its 64K is the {@link Atari}'s memory; the
 * RMT tracker driver loaded there is run through {@link #jsr}; the POKEY
 * pair is fed by {@link #pokeRegister} and rendered by {@link #render}.
 * (The WAV export is the one path that still plays a whole exported module
 * through ASAP's own player instead - see {@link WaveFileExporter}.)
 */
public final class AtariCpu {

	/** The output sample rate, C++'s 44.1 kHz. */
	public static final int SAMPLE_RATE = 44100;

	/** {@link #jsr}'s result: the registers after the routine returned. */
	public record Registers(int a, int x, int y, boolean returned) {
	}

	private final ASAP asap = new ASAP();
	private boolean ntsc;
	private boolean stereo;

	public AtariCpu(boolean ntsc, boolean stereo) {
		initialize(ntsc, stereo);
	}

	/** {@code CXPokey::(Re)InitSound(ntsc, stereo)}: (re)configures the POKEY pair; the CPU's memory is kept. */
	public void initialize(boolean ntsc, boolean stereo) {
		this.ntsc = ntsc;
		this.stereo = stereo;
		asap.rmtInitialize(ntsc, stereo, SAMPLE_RATE);
	}

	public boolean isNTSC() {
		return ntsc;
	}

	public boolean isStereo() {
		return stereo;
	}

	public byte[] getMemory() {
		return asap.rmtMemory();
	}

	/** {@code C6502::JSR}: runs the routine at {@code addr} with A/X/Y, limited to one video frame of cycles as every C++ call site is ({@code GetFrameCycleCount()}). */
	public Registers jsr(int addr, int a, int x, int y) {
		int r = asap.rmtJsr(addr, a, x, y, Atari.getFrameCycleCount(ntsc));
		return new Registers(r & 0xFF, (r >> 8) & 0xFF, (r >> 16) & 0xFF, (r & (1 << 24)) == 0);
	}

	/** {@code CPokey::PutByte(offset, value)}: 0-8 = AUDF1..AUDCTL of the base POKEY, 16-24 the second one's (stereo only). */
	public void pokeRegister(int offset, int data) {
		asap.rmtPokeRegister(offset, data);
	}

	/** The volume (AUDC & 15) the POKEY pair currently holds for channel 0-7 - what {@link #pokeRegister} fed it, after any channel muting. */
	public int getChannelVolume(int channel) {
		return asap.getPokeyChannelVolume(channel);
	}

	/** Bytes per rendered block: one 16-bit sample per POKEY. */
	public int getBlockSize() {
		return stereo ? 4 : 2;
	}

	/** Renders {@code cycles} CPU cycles of POKEY output into {@code buffer} at {@code offset} (16-bit little-endian, {@link #getBlockSize()} bytes per block); returns the number of blocks. */
	public int render(int cycles, byte[] buffer, int offset) {
		return asap.rmtRender(cycles, buffer, offset);
	}
}
