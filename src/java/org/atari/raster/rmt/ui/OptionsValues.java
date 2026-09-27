package org.atari.raster.rmt.ui;

import org.atari.raster.rmt.model.TrackerDriverVersion;

/**
 * The Options dialog's data - {@code COptionsDialog}'s {@code m_*} members
 * as a plain holder, so that {@code CRmtView::OnToolsOptions()}'s two
 * halves stay apart: {@link #from(RmtSession)} is its "copy the globals into
 * the dialog" prologue, {@link RmtCommands#applyOptions} its "apply what
 * the dialog returned" epilogue (headless-testable), and
 * {@link OptionsDialog} edits an instance in between.
 */
public final class OptionsValues {

	// GENERAL
	public int scalingPercentage;
	public int trackLinePrimaryHighlight;
	public int trackLineSecondaryHighlight;
	public boolean trackLineAltNumbering;
	public boolean displayFlatNotes;
	public boolean useGermanNotation;
	public boolean ntsc;
	public boolean noHwSoundBuffer;
	public boolean doSmoothScrolling;
	public boolean viewDebugDisplay;

	// TODO: Module
	public TrackerDriverVersion trackerDriverVersion;

	// KEYBOARD
	public int keyboardLayout;
	public boolean keyboardEscResetAtariSound;
	public boolean keyboardUpDownContinue;
	public boolean keyboardRememberOctavesAndVolumes;
	public boolean keyboardAskWhenControlS;

	// MIDI - the device by name ("" for none); C++ passes the device index and looks the name up again afterwards
	public String midiDevice;
	public boolean midiTouchResponse;
	public int midiVolumeOffset;
	public boolean midiNoteOff;

	/** {@code OnToolsOptions()}'s prologue: the current option values. */
	public static OptionsValues from(RmtSession session) {
		RmtOptions o = session.options;
		OptionsValues v = new OptionsValues();
		v.scalingPercentage = o.scalingPercentage;
		v.trackLinePrimaryHighlight = o.trackLinePrimaryHighlight;
		v.trackLineSecondaryHighlight = o.trackLineSecondaryHighlight;
		v.trackLineAltNumbering = o.trackLineAltNumbering;
		v.displayFlatNotes = o.displayFlatNotes;
		v.useGermanNotation = o.useGermanNotation;
		v.ntsc = session.song.isNTSC();
		v.noHwSoundBuffer = o.noHwSoundBuffer;
		v.doSmoothScrolling = o.view.smoothScrolling;
		v.viewDebugDisplay = o.view.debugDisplay;

		v.trackerDriverVersion = o.trackerDriverVersion;

		v.keyboardLayout = o.keyboardLayout;
		v.keyboardEscResetAtariSound = o.keyboardEscResetAtariSound;
		v.keyboardUpDownContinue = o.keyboardUpDownContinue;
		v.keyboardRememberOctavesAndVolumes = o.keyboardRememberOctavesAndVolumes;
		v.keyboardAskWhenControlS = o.keyboardAskWhenControlS;

		v.midiDevice = o.midiDevice;
		v.midiTouchResponse = o.midiTouchResponse;
		v.midiVolumeOffset = o.midiVolumeOffset;
		v.midiNoteOff = o.midiNoteOff;
		return v;
	}
}
