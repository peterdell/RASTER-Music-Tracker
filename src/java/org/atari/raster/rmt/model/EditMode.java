package org.atari.raster.rmt.model;

/**
 * Ported from the C++ enum class EditMode (General.h) - the GUI edit/jam
 * modes held in C++'s {@code g_prove} global. Placed in {@code model} like
 * its {@code General.h} siblings ({@link Part}, {@link PlayMode},
 * {@link EditArea}, {@link KeyboardLayout}).
 *
 * <p>C++'s free functions {@code IsProveMode()}/{@code IsSpecialProveMode()}
 * (Global.cpp), which read {@code g_prove}, become the instance methods
 * {@link #isProveMode()}/{@link #isSpecialProveMode()} on the value itself -
 * an idiomatic substitution with identical results.
 */
public enum EditMode {
	EDIT_MODE, // Hit the Jam mode button to switch between
	JAM_MONO_MODE, // the first three modes
	JAM_STEREO_MODE, // Can only get here in stereo mode
	MIDI_CH15_MODE, // Hit RECORD key in Midi channel 15 to cycle to this mode
	POKEY_EXPLORER_MODE;

	/** MIDI or Pokey Explorer mode? Mirrors C++'s {@code IsSpecialProveMode()}. */
	public boolean isSpecialProveMode() {
		return this == MIDI_CH15_MODE || this == POKEY_EXPLORER_MODE;
	}

	/** Any mode that plays notes instead of editing them. Mirrors C++'s {@code IsProveMode()}. */
	public boolean isProveMode() {
		return this == JAM_MONO_MODE || this == JAM_STEREO_MODE || isSpecialProveMode();
	}
}
