package org.atari.raster.rmt.ui;

import org.atari.raster.rmt.model.KeyboardLayout;
import org.atari.raster.rmt.model.TrackerDriverVersion;

/**
 * The persisted user options that C++ keeps in {@code Global.h} globals and
 * stores in {@code rmt.ini} ({@code g_scaling_percentage},
 * {@code g_trackLinePrimaryHighlight}, {@code TViewState g_view}, ...) -
 * the counterpart of {@link UiState}, which holds the transient editor
 * state. One plain mutable holder, passed explicitly (no static singleton),
 * per this port's "C++ global -> explicit parameter" idiom.
 *
 * <p>{@link RmtConfig} reads and writes these as {@code rmt.ini}. Defaults
 * are those of {@code CRmtView::ResetRMTConfig()} (see {@link #reset()}) -
 * which is also what a fresh {@code Rmt.exe} without an {@code rmt.ini}
 * runs with, so the reference screenshots were taken with exactly these
 * values. The NTSC flag is not here: C++ stores it in {@code rmt.ini} but
 * keeps it in {@code CSong} ("TODO: Tracker must be in the module instead").
 */
public final class RmtOptions {

	// GENERAL
	/** {@code g_scaling_percentage} - how much RMT stretches its logical canvas onto the window, 100-300. */
	public int scalingPercentage = 100;
	/** {@code g_trackLinePrimaryHighlight} - every n-th track line is drawn cyan (and PgUp/PgDn jump by it). */
	public int trackLinePrimaryHighlight = 8;
	/** {@code g_trackLineSecondaryHighlight} - every n-th track line is drawn green. */
	public int trackLineSecondaryHighlight = 4;
	/** {@code g_tracklinealtnumbering} - number track lines as "bar.beat" letters instead of hex. */
	public boolean trackLineAltNumbering;
	/** {@code g_SkipLinesAfterNoteInsert} - how many lines the cursor moves after a note is entered (also the plain Up/Down step). Reset by {@code ResetRMTConfig()} but not stored in {@code rmt.ini}. */
	public int skipLinesAfterNoteInsert = 1;
	/** {@code g_displayflatnotes} - flats instead of sharps. */
	public boolean displayFlatNotes;
	/** {@code g_usegermannotation} - H instead of B. */
	public boolean useGermanNotation;
	/** {@code g_nohwsoundbuffer} - "Disable hardware soundbuffer"; only the POKEY renderer reads it (B8). */
	public boolean noHwSoundBuffer;
	/** {@code g_trackerDriverVersion} - which RMT player routine binary is loaded into the emulated Atari. */
	public TrackerDriverVersion trackerDriverVersion = TrackerDriverVersion.PATCH16;
	/** {@code g_notesperoctave} - 12; not in {@code rmt.ini}, but an option-like global the note display depends on. */
	public int notesPerOctave = 12;

	// KEYBOARD
	/** {@code g_keyboard_layout} - which key-to-note table {@code NoteKey()} uses ({@link KeyboardLayout}). */
	public int keyboardLayout = KeyboardLayout.QWERTY;
	/** {@code g_keyboard_updowncontinue} - Up/Down past a pattern's end continue into the previous/next songline. */
	public boolean keyboardUpDownContinue = true;
	/** {@code g_keyboard_swapenter} - swap the roles of Enter and Ctrl+Enter in the tracks ("probably not needed anymore but will be kept for now"); not in {@code rmt.ini}. */
	public boolean keyboardSwapEnter;
	/** {@code g_keyboard_RememberOctavesAndVolumes} - the last used octave and volume are stored per instrument. */
	public boolean keyboardRememberOctavesAndVolumes = true;
	/** {@code g_keyboard_escresetatarisound} - Esc resets the Atari sound routines. */
	public boolean keyboardEscResetAtariSound = true;
	/** {@code g_keyboard_askwhencontrol_s} - Ctrl+S asks before overwriting the file. */
	public boolean keyboardAskWhenControlS = true;

	// MIDI ({@code CRmtMidi}'s persisted members; the device is {@link RmtMidi}, the input {@link MidiInput})
	/** {@code CMidi::GetMidiDevName()} - the MIDI IN device's name, "" for none. */
	public String midiDevice = "";
	/** {@code CMidi::m_TouchResponse}. */
	public boolean midiTouchResponse;
	/** {@code CMidi::m_VolumeOffset}, 0-15. */
	public int midiVolumeOffset;
	/** {@code CMidi::m_NoteOff}. */
	public boolean midiNoteOff;

	// PATHS
	/** {@code g_defaultSongsPath}. */
	public String defaultSongsPath = "";
	/** {@code g_defaultInstrumentsPath}. */
	public String defaultInstrumentsPath = "";
	/** {@code g_defaultTracksPath}. */
	public String defaultTracksPath = "";
	/** {@code g_lastLoadPath_Songs}. */
	public String lastSongsPath = "";
	/** {@code g_lastLoadPath_Instruments}. */
	public String lastInstrumentsPath = "";
	/** {@code g_lastLoadPath_Tracks}. */
	public String lastTracksPath = "";

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
	 * The option half of {@code CRmtView::ResetRMTConfig()}: every value it
	 * assigns, in its order. Its side effects - {@code SetNTSC(false)},
	 * {@code g_Midi.MidiInit()} and writing the file - are the caller's
	 * ({@link RmtConfig#resetRMTConfig}). {@link #notesPerOctave} and
	 * {@link #keyboardSwapEnter} are untouched, as in C++.
	 */
	public void reset() {
		scalingPercentage = 100; // RMT interface scaling (in percentage)
		trackLinePrimaryHighlight = 8; // Primary line highlighted every x lines
		trackLineSecondaryHighlight = 4; // Secondary line highlighted every x lines
		trackLineAltNumbering = false; // Alternative way of line numbering in tracks
		skipLinesAfterNoteInsert = 1; // Number of lines to scroll after inserting a note
		noHwSoundBuffer = false; // Don't use hardware soundbuffer
		trackerDriverVersion = TrackerDriverVersion.PATCH16; // Tracker driver version
		displayFlatNotes = false; // Display accidentals as Flats instead of Sharps
		useGermanNotation = false; // Display H notes instead of B

		view.mainToolbar = true;
		view.blockToolbar = true;
		view.statusBar = true;
		view.playTimeCounter = true;
		view.volumeAnalyzer = true;
		view.pokeyRegisters = true;
		view.instrumentEditHelp = true;
		view.smoothScrolling = true;
		view.debugDisplay = true;

		lastSongsPath = "";
		lastInstrumentsPath = "";
		lastTracksPath = "";
		defaultSongsPath = "";
		defaultInstrumentsPath = "";
		defaultTracksPath = "";

		keyboardLayout = KeyboardLayout.QWERTY;
		keyboardUpDownContinue = true;
		keyboardRememberOctavesAndVolumes = true;
		keyboardEscResetAtariSound = true;
		keyboardAskWhenControlS = true;
		midiDevice = "";
		midiTouchResponse = false;
		midiVolumeOffset = 0;
		midiNoteOff = false;
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
