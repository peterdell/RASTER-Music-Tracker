package com.wudsn.tools.rmt.model;

/**
 * Ported from the C++ Timbre enum (src/cpp/Tuning.h). The values also define
 * which Distortion (AUDC) is appropriate: "audc = value &amp; 0xF0". Example:
 * BUZZY_C's value is 0xC1, so its distortion nibble is 0xC0 (Distortion C).
 */
public enum Timbre {
	PINK_NOISE(0x00), // Distortion 0, by default
	BROWNIAN_NOISE(0x01), // (MOD7 && POLY9), Distortion 0
	FUZZY_NOISE(0x02), // (!MOD7 && POLY9), Distortion 0
	BELL(0x20), // (!MOD31), Distortion 2, by default
	BUZZY_4(0x40), // (!MOD3 && !MOD5), used by Distortion 4, identical to Distortion C (Gritty)
	SMOOTH_4(0x41), // (MOD3 && !MOD5), used by Distortion 4, identical to Distortion C (Buzzy)
	WHITE_NOISE(0x80), // Distortion 8, by default
	METALLIC_NOISE(0x81), // (MOD7 && POLY9), Distortion 8
	BUZZY_NOISE(0x82), // (!MOD7 && POLY9), Distortion 8
	PURE_A(0xA0), // Distortion A, by default
	GRITTY_C(0xC0), // (!MOD3 && !MOD5), also known as RMT Distortion E
	BUZZY_C(0xC1), // (MOD3 && !MOD5), also known as RMT Distortion C
	UNSTABLE_C(0xC2); // (!MOD3 && MOD5), must be avoided unless there is a purpose for it

	public final int value;

	Timbre(int value) {
		this.value = value;
	}
}
