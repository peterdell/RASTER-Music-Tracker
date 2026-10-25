package org.atari.raster.rmt.ui;

import java.nio.file.Path;

/**
 * The test songs in {@code test-resources/songs} (plan 35): the shared inputs
 * the session, audio, playability, script and UI tests open. Each song folder
 * holds the module, its README metadata sheet and any committed reference
 * exports; the golden screenshots stay in {@code test-resources/ui-reference}
 * ({@link ReferenceScreenshot}).
 */
final class TestSongs {

	static final Path ROOT = Path.of("test-resources", "songs");

	/** Delta by Radek Sterba (Raster), 01/2003, mono, RMT 1.28 compatible. */
	static final Path DELTA = ROOT.resolve("rmt128").resolve("delta").resolve("Delta.rmt");

	/** RebbStars by Teemu Pohjanlehto (Rebb), 20/04/2014, mono, RMT 1.28 compatible,
	 * instrument speed 3 - the multi-speed counterpart to {@link #DELTA}. */
	static final Path REBB_STARS = ROOT.resolve("rmt128").resolve("rebbstars").resolve("RebbStars.rmt");

	/** Why Do You Dance With Me? by Zdenek Eisenhammer (PG), 2021, stereo. */
	static final Path WHY_DO_YOU_DANCE_WITH_ME = ROOT.resolve("rmt134").resolve("why_do_you_dance_with_me")
			.resolve("Why_Do_You_Dance_With_Me.rmt");

	/** Captain Future Theme by Christian Duesterhoeft (Buddy), 13/08/2024, stereo, Patch16
	 * - the Simple RMT Player's master tune; see its README.md for the copies and the .sapr reference. */
	static final Path CAPTAIN_FUTURE_THEME = ROOT.resolve("rmt134").resolve("captain_future_theme")
			.resolve("Captain_Future_Theme.rmt");

	/** Makary's Two-Tone filter example (github issue #30), mono, instrument speed 2 - the
	 * envelope editor's $7FE command drives SKCTL to $8B; see its README.md for the added pause. */
	static final Path MAKARY_TWO_TONE_FILTER = ROOT.resolve("rmt134").resolve("makary_two_tone_filter")
			.resolve("Makary_Two_Tone_Filter.rmt");

	/** rra's SFX-only module (github issue #5): 9 instruments, no tracks or song lines, stripped,
	 * at $6800 - exercises "Export as Stripped RMT" with SFX support; see its README.md. */
	static final Path ISSUE5_SFX_ONLY = ROOT.resolve("issues").resolve("issue5_sfx_only").resolve("testsfx.rmt");

	private TestSongs() {
	}
}
