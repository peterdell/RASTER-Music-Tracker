package com.wudsn.tools.rmt.model;

/**
 * Ported from the C++ class EnvelopeParameter (General.h) - plain
 * {@code static constexpr int} row indices into {@link Instrument#envelope},
 * kept as plain constants here too rather than an enum, matching how they're
 * actually used as raw array indices throughout the C++ source.
 */
public final class EnvelopeParameter {

	public static final int VOLUMER = 0;
	public static final int VOLUMEL = 1;
	public static final int DISTORTION = 2;
	public static final int COMMAND = 3;
	public static final int X = 4;
	public static final int Y = 5;
	public static final int FILTER = 6;
	public static final int PORTAMENTO = 7;

	private EnvelopeParameter() {
	}
}
