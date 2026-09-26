package com.wudsn.tools.rmt.ui;

/**
 * The persisted user options that C++ keeps in {@code Global.h} globals and
 * stores in {@code rmt.ini} ({@code g_scaling_percentage},
 * {@code g_trackLinePrimaryHighlight}, {@code TViewState g_view}, ...) -
 * the counterpart of {@link UiState}, which holds the transient editor
 * state. One plain mutable holder, passed explicitly (no static singleton),
 * per this port's "C++ global -> explicit parameter" idiom.
 *
 * <p>Only the options the drawing and navigation code reads exist yet;
 * reading/writing {@code rmt.ini} and the remaining options (paths,
 * keyboard layout, MIDI, ...) come with the options batch (see
 * {@code plans/JAVA_UI_PORT_PLAN.md}, B6). Defaults are those of
 * {@code CRmtView::ResetRMTConfig()} - which is also what a fresh
 * {@code Rmt.exe} without an {@code rmt.ini} runs with, so the reference
 * screenshots were taken with exactly these values.
 */
public final class RmtOptions {

	/** {@code g_scaling_percentage} - how much RMT stretches its logical canvas onto the window, 100-300. */
	public int scalingPercentage = 100;
	/** {@code g_trackLinePrimaryHighlight} - every n-th track line is drawn cyan (and PgUp/PgDn jump by it). */
	public int trackLinePrimaryHighlight = 8;
	/** {@code g_trackLineSecondaryHighlight} - every n-th track line is drawn green. */
	public int trackLineSecondaryHighlight = 4;
	/** {@code g_tracklinealtnumbering} - number track lines as "bar.beat" letters instead of hex. */
	public boolean trackLineAltNumbering;
	/** {@code g_SkipLinesAfterNoteInsert} - how many lines the cursor moves after a note is entered (also the plain Up/Down step). */
	public int skipLinesAfterNoteInsert = 1;
	/** {@code g_displayflatnotes} - flats instead of sharps. */
	public boolean displayFlatNotes;
	/** {@code g_usegermannotation} - H instead of B. */
	public boolean useGermanNotation;
	/** {@code g_notesperoctave} - 12; not in {@code rmt.ini}, but an option-like global the note display depends on. */
	public int notesPerOctave = 12;
	/** {@code g_keyboard_updowncontinue} - Up/Down past a pattern's end continue into the previous/next songline. */
	public boolean keyboardUpDownContinue = true;

	/** {@code TViewState g_view}. */
	public final ViewState view = new ViewState();

	/** Ported from the C++ struct {@code TViewState} (Global.h) - the View menu's toggles. */
	public static final class ViewState {
		public boolean mainToolbar = true;
		public boolean blockToolbar = true;
		public boolean statusBar = true;
		public boolean playTimeCounter = true;
		public boolean volumeAnalyzer = true;
		public boolean pokeyRegisters = true;
		public boolean instrumentEditHelp = true;
		public boolean smoothScrolling = true;
		public boolean debugDisplay = true;
	}

	/**
	 * The note-name table index {@code CSongUI::DrawTracks()} recomputes on
	 * every frame from the three notation options (its own {@code TODO --
	 * FIXME: set the Notation elsewhere}): 0 standard, +1 flats, +2 German,
	 * 4 for anything but 12 notes per octave.
	 */
	public int getNotation() {
		int notation = 0;
		if (displayFlatNotes) {
			notation += 1;
		}
		if (useGermanNotation) {
			notation += 2;
		}
		if (notesPerOctave != 12) {
			notation = 4; // Non-12 scales don't yet have proper display
		}
		return notation;
	}
}
