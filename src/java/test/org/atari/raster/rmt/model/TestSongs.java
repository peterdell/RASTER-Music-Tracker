package org.atari.raster.rmt.model;

import java.nio.file.Path;
import java.util.List;

/**
 * The test songs in {@code test-resources/songs} (plan 35): the shared inputs
 * the session, audio, playability, script and UI tests open. Each song folder
 * holds the module, its README metadata sheet and any committed reference
 * exports; the golden screenshots stay in {@code test-resources/ui-reference}
 * ({@code ReferenceScreenshot}). In the model package, since the songs are
 * the model's data; the ui tests import it (plan 60 moved it here).
 */
public final class TestSongs {

	public static final Path ROOT = Path.of("test-resources", "songs");

	/** Delta by Radek Sterba (Raster), 01/2003, mono, RMT 1.28 compatible. */
	public static final Path DELTA = ROOT.resolve("rmt128").resolve("delta").resolve("Delta.rmt");

	/** RebbStars by Teemu Pohjanlehto (Rebb), 20/04/2014, mono, RMT 1.28 compatible,
	 * instrument speed 3 - the multi-speed counterpart to {@link #DELTA}. */
	public static final Path REBB_STARS = ROOT.resolve("rmt128").resolve("rebbstars").resolve("RebbStars.rmt");

	/** Why Do You Dance With Me? by Zdenek Eisenhammer (PG), 2021, stereo. */
	public static final Path WHY_DO_YOU_DANCE_WITH_ME = ROOT.resolve("rmt134").resolve("why_do_you_dance_with_me")
			.resolve("Why_Do_You_Dance_With_Me.rmt");

	/** Captain Future Theme by Christian Duesterhoeft (Buddy), 13/08/2024, stereo, Patch16
	 * - the Simple RMT Player's master tune; see its README.md for the copies and the .sapr reference. */
	public static final Path CAPTAIN_FUTURE_THEME = ROOT.resolve("rmt134").resolve("captain_future_theme")
			.resolve("Captain_Future_Theme.rmt");

	/** Makary's Two-Tone filter example (github issue #30), mono, instrument speed 2 - the
	 * envelope editor's $7FE command drives SKCTL to $8B; see its README.md for the added pause. */
	public static final Path ISSUE_30_TWO_TONE_FILTER = ROOT.resolve("rmt134").resolve("issue_30_two_tone_filter")
			.resolve("Makary_Two_Tone_Filter.rmt");

	/** rra's SFX-only module (github issue #5): 10 instruments, no tracks or song lines, stripped,
	 * at $6800 - exercises "Export as Stripped RMT" with SFX support; see its README.md. */
	public static final Path ISSUE_5_STRIPPED_SFX_EXPORT = ROOT.resolve("rmt134").resolve("issue_5_stripped_sfx_export")
			.resolve("testsfx.rmt");

	/** Every committed module, in path order. */
	public static final List<Path> ALL = List.of(DELTA, REBB_STARS, CAPTAIN_FUTURE_THEME, ISSUE_30_TWO_TONE_FILTER, ISSUE_5_STRIPPED_SFX_EXPORT, WHY_DO_YOU_DANCE_WITH_ME);

	/**
	 * The modules without the names segment - "stripped", in RMT's words. Party
	 * releases and SFX banks commonly are: only {@link #CAPTAIN_FUTURE_THEME}
	 * and {@link #ISSUE_30_TWO_TONE_FILTER} carry their song and instrument
	 * names (plan 60 read every file).
	 */
	public static final List<Path> STRIPPED = List.of(DELTA, REBB_STARS, ISSUE_5_STRIPPED_SFX_EXPORT, WHY_DO_YOU_DANCE_WITH_ME);

	private TestSongs() {
	}
}
