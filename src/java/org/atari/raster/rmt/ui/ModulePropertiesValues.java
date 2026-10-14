package org.atari.raster.rmt.ui;

import org.atari.raster.rmt.model.TrackerDriverVersion;

/**
 * The Module Properties dialog's data, the same plain-holder pattern
 * {@link OptionsValues} uses for the Options dialog: {@link #from(RmtSession)}
 * reads the song's current properties, {@link ModulePropertiesDialog} edits an
 * instance, and {@link RmtCommands#applyModuleProperties} applies what the
 * dialog returned (headless-testable).
 *
 * <p>Both values were in the Options dialog's "Module Defaults" group until
 * plan 44 moved them here, where they belong: they are the song's, not the
 * program's. Where they are stored has not changed - {@code rmt.ini} keeps
 * them, so opening a module does not change them.
 */
public final class ModulePropertiesValues {

	/** {@code CSong::IsNTSC()}. */
	public boolean ntsc;
	/** {@code CSong::GetTrackerDriverVersion()}. */
	public TrackerDriverVersion trackerDriverVersion;

	/** The song's current properties. */
	public static ModulePropertiesValues from(RmtSession session) {
		ModulePropertiesValues v = new ModulePropertiesValues();
		v.ntsc = session.song.isNTSC();
		v.trackerDriverVersion = session.song.getTrackerDriverVersion();
		return v;
	}
}
