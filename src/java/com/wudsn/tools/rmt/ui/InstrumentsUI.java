package com.wudsn.tools.rmt.ui;

import java.awt.Color;
import java.util.Locale;

import com.wudsn.tools.rmt.model.EnvelopeParameter;
import com.wudsn.tools.rmt.model.Instrument;
import com.wudsn.tools.rmt.model.InstrumentSection;
import com.wudsn.tools.rmt.model.Instruments;
import com.wudsn.tools.rmt.model.Part;
import com.wudsn.tools.rmt.model.Song;

/**
 * The instrument editor screen - ported from the drawing half of
 * GUI_Instruments.cpp ({@code CInstruments::DrawInstrument}/{@code DrawName}/
 * {@code DrawParameter}/{@code DrawEnv}/{@code DrawNoteTableValue}). C++
 * implements these as methods of the model class {@code CInstruments};
 * this port keeps {@link Instruments} UI-free and puts them here, taking
 * the model (the deviation recorded in {@code plans/JAVA_UI_PORT_PLAN.md}).
 * The hit-testing half ({@code CursorGoto}/{@code GetGUIArea}) comes with
 * the mouse batch (B4).
 *
 * <p>{@link #SHPAR}/{@link #SHENV} are the display columns of C++'s
 * {@code shpar[]}/{@code shenv[]} tables (InstrumentsAtaFormat.cpp) -
 * name and screen position; the value-range and TXT-field columns of the
 * same tables live in {@link Instruments} where the loaders use them, so
 * the two must be kept in step by hand.
 */
public final class InstrumentsUI {

	/** Ported from C++'s {@code InstrumentGUIPosition} (General.h). */
	public static final int X = 2 * 8;
	public static final int Y = 8 * 16 + 8;
	public static final int PARAM_X = X; // parameter X
	public static final int PARAM_Y = Y + 2 * 16; // parametry Y
	public static final int ENV_X = X + 32 * 8; // envelope X  (29)
	public static final int ENV_Y = Y + 2 * 16; // envelope Y
	public static final int TABLE_X = X + 0 * 8; // table X	(16)(37)
	public static final int TABLE_Y = Y + 18 * 16 - 8; // table Y
	public static final int HELP_X = X; // active help X
	public static final int HELP_Y = Y + 21 * 16; // active help Y

	/** {@code IconMiniXY} icon numbers (InstrumentTypes.h). */
	private static final int INSTRUMENT_TABLE_OF_NOTES = 1;
	private static final int INSTRUMENT_TABLE_OF_FREQ = 2;
	private static final int INSTRUMENT_TABLE_MODE_SET = 3;
	private static final int INSTRUMENT_TABLE_MODE_ADD = 4;

	/** The display columns of C++'s {@code Tshpar}: parameter index, screen position, name, and the two range values {@code DrawParameter} needs for its 1- or 2-digit format. */
	record ShPar(int paramIndex, int x, int y, String name, int maxParameterValue, int displayOffset) {
	}

	/** Same order as C++'s {@code shpar[]} - indexed by parameter number. */
	static final ShPar[] SHPAR = { //
			// TABLE: LEN GO SPD TYPE MODE
			new ShPar(Instrument.PAR_TBL_LENGTH, PARAM_X + 16 * 8, PARAM_Y + 9 * 16, "LENGTH:", 0x1f, 1), //
			new ShPar(Instrument.PAR_TBL_GOTO, PARAM_X + 18 * 8, PARAM_Y + 10 * 16, "GOTO:", 0x1f, 0), //
			new ShPar(Instrument.PAR_TBL_SPEED, PARAM_X + 17 * 8, PARAM_Y + 11 * 16, "SPEED:", 0x3f, 1), //
			new ShPar(Instrument.PAR_TBL_TYPE, PARAM_X + 18 * 8, PARAM_Y + 12 * 16, "TYPE:", 0x01, 0), //
			new ShPar(Instrument.PAR_TBL_MODE, PARAM_X + 18 * 8, PARAM_Y + 13 * 16, "MODE:", 0x01, 0), //
			// ENVELOPE: LEN GO VSLIDE VMIN
			new ShPar(Instrument.PAR_ENV_LENGTH, PARAM_X + 16 * 8, PARAM_Y + 2 * 16, "LENGTH:", 0x2f, 1), //
			new ShPar(Instrument.PAR_ENV_GOTO, PARAM_X + 18 * 8, PARAM_Y + 3 * 16, "GOTO:", 0x2f, 0), //
			new ShPar(Instrument.PAR_VOL_FADEOUT, PARAM_X + 15 * 8, PARAM_Y + 4 * 16, "FADEOUT:", 0xff, 0), //
			new ShPar(Instrument.PAR_VOL_MIN, PARAM_X + 15 * 8, PARAM_Y + 5 * 16, "VOL MIN:", 0x0f, 0), //
			// EFFECT: DELAY VIBRATO FSHIFT
			new ShPar(Instrument.PAR_DELAY, PARAM_X + 3 * 8, PARAM_Y + 2 * 16, "DELAY:", 0xff, 0), //
			new ShPar(Instrument.PAR_VIBRATO, PARAM_X + 1 * 8, PARAM_Y + 3 * 16, "VIBRATO:", 0x03, 0), //
			new ShPar(Instrument.PAR_FREQ_SHIFT, PARAM_X + -1 * 8, PARAM_Y + 4 * 16, "FREQSHIFT:", 0xff, 0), //
			// AUDCTL: 00-07
			new ShPar(Instrument.PAR_AUDCTL_15KHZ, PARAM_X + 3 * 8, PARAM_Y + 6 * 16, "15KHZ:", 0x01, 0), //
			new ShPar(Instrument.PAR_AUDCTL_HPF_CH2, PARAM_X + 1 * 8, PARAM_Y + 7 * 16, "HPF 2+4:", 0x01, 0), //
			new ShPar(Instrument.PAR_AUDCTL_HPF_CH1, PARAM_X + 1 * 8, PARAM_Y + 8 * 16, "HPF 1+3:", 0x01, 0), //
			new ShPar(Instrument.PAR_AUDCTL_JOIN_3_4, PARAM_X + 0 * 8, PARAM_Y + 9 * 16, "JOIN 3+4:", 0x01, 0), //
			new ShPar(Instrument.PAR_AUDCTL_JOIN_1_2, PARAM_X + 0 * 8, PARAM_Y + 10 * 16, "JOIN 1+2:", 0x01, 0), //
			new ShPar(Instrument.PAR_AUDCTL_179_CH3, PARAM_X + 0 * 8, PARAM_Y + 11 * 16, "1.79 CH3:", 0x01, 0), //
			new ShPar(Instrument.PAR_AUDCTL_179_CH1, PARAM_X + 0 * 8, PARAM_Y + 12 * 16, "1.79 CH1:", 0x01, 0), //
			new ShPar(Instrument.PAR_AUDCTL_POLY9, PARAM_X + 3 * 8, PARAM_Y + 13 * 16, "POLY9:", 0x01, 0) };

	/** The display columns of C++'s {@code Tshenv}: the marker character ({@code 0} = show the hex digit instead), name and position. */
	record ShEnv(char ch, String name, int xpos, int ypos) {
	}

	/** Same order as C++'s {@code shenv[]} - indexed by {@link EnvelopeParameter}. */
	static final ShEnv[] SHENV = { //
			new ShEnv((char) 0, "VOLUME R:", ENV_X + 2 * 8, ENV_Y + 2 * 16), // volume right
			new ShEnv((char) 0, "VOLUME L:", ENV_X + 2 * 8, ENV_Y + 8 * 16), // volume left
			new ShEnv((char) 0, "DISTORTION:", ENV_X + 0 * 8, ENV_Y + 9 * 16), // distortion 0,2,4,6,...
			new ShEnv((char) 0, "COMMAND:", ENV_X + 3 * 8, ENV_Y + 10 * 16), // command 0-7
			new ShEnv((char) 0, "X/:", ENV_X + 8 * 8, ENV_Y + 11 * 16), // X
			new ShEnv((char) 0, "Y\\:", ENV_X + 8 * 8, ENV_Y + 12 * 16), // Y
			new ShEnv((char) 9, "AUTOFILTER:", ENV_X + 0 * 8, ENV_Y + 13 * 16), // filter *
			new ShEnv((char) 9, "PORTAMENTO:", ENV_X + 0 * 8, ENV_Y + 14 * 16) // portamento *
	};

	private final RmtSession session;
	private final CanvasXY canvasXY;

	public InstrumentsUI(RmtSession session, CanvasXY canvasXY) {
		this.session = session;
		this.canvasXY = canvasXY;
	}

	private boolean isProveMode() {
		return session.uiState.editMode.isProveMode();
	}

	private TextColor getSelectedColor() {
		return isProveMode() ? TextColor.SELECTED_PROVE : TextColor.SELECTED;
	}

	/**
	 * Draw instrument:
	 * 4 general areas:
	 * - Name
	 * - Parameters
	 * - Envelope
	 * - Note table
	 */
	public void drawInstrument(int instrNr) {
		Instruments instruments = session.instruments;
		UiState ui = session.uiState;
		boolean stereo = session.tracks4_8 > 4;
		int i;

		Instrument t = instruments.getInstrument(instrNr);
		if (t == null) {
			return;
		}

		// Line 8.5: Instrument XX (size xx bytes)
		canvasXY.textXY(String.format("INSTRUMENT %02X", instrNr), X, Y, TextColor.WHITE);

		int size = (t.parameters[Instrument.PAR_ENV_LENGTH] + 1) * 3 + (t.parameters[Instrument.PAR_TBL_LENGTH] + 1) + 12;
		canvasXY.textMiniXY(String.format("(SIZE %d BYTES)", size), X + 14 * 8, Y + 5, TextMiniColor.GRAY);

		drawName(instrNr);

		// Draw some headings
		canvasXY.textMiniXY("EFFECT", PARAM_X, PARAM_Y + 1 * 16 + 8, TextMiniColor.GRAY);
		canvasXY.textMiniXY("AUDCTL", PARAM_X + 0 * 8, PARAM_Y + 5 * 16 + 8, TextMiniColor.GRAY);
		canvasXY.textMiniXY("ENVELOPE", PARAM_X + 15 * 8, PARAM_Y + 16 + 8, TextMiniColor.GRAY);
		canvasXY.textMiniXY("TABLE", PARAM_X + 15 * 8, PARAM_Y + 8 * 16 + 8, TextMiniColor.GRAY);

		// Draw envelope volume markers
		canvasXY.textDownXY("\u000e\u000e\u000e\u000e", ENV_X + 11 * 8 - 1, ENV_Y + 3 * 16, TextColor.GRAY);
		// delimitation of space for Envelope VOLUME
		canvasXY.moveTo(ENV_X + 12 * 8 - 1, ENV_Y + 7 * 16 - 1);
		canvasXY.lineTo(ENV_X + 12 * 8 + Instrument.ENVELOPE_MAX_COLUMNS * 8, ENV_Y + 7 * 16 - 1);

		if (t.activeEditSection == InstrumentSection.ENVELOPE) {
			// Only when the cursor is on the envelope editor, draw the x position of the envelop index being edited
			canvasXY.textXY(String.format("POS %02X", t.editEnvelopeX), ENV_X + 2 * 8, ENV_Y + 5 * 16, TextColor.GRAY);
		}

		// Draw the headers of the envelop table parameters
		// Skip "VOLUME R:", hence start at 1
		for (i = 1; i < Instrument.ENVROWS; i++) {
			canvasXY.textXY(SHENV[i].name(), SHENV[i].xpos(), SHENV[i].ypos(), TextColor.WHITE);
		}

		// For 8 channels draw the "VOLUME R:" header and volume markers
		if (stereo) {
			canvasXY.textXY(SHENV[0].name(), SHENV[0].xpos(), SHENV[0].ypos(), TextColor.WHITE); // "VOLUME R:"
			canvasXY.textDownXY("\u000e\u000e\u000e\u000e", ENV_X + 11 * 8 - 1, ENV_Y - 2 * 16, TextColor.GRAY);
			canvasXY.moveTo(ENV_X + 12 * 8 - 1, ENV_Y + 2 * 16 - 1);
			canvasXY.lineTo(ENV_X + 12 * 8 + Instrument.ENVELOPE_MAX_COLUMNS * 8, ENV_Y + 2 * 16 - 1);
		}

		for (i = 0; i < Instrument.NUMBER_OF_PARAMS; i++) {
			drawParameter(i, instrNr);
		}

		// TABLE TYPE icon (Notes or Freq)
		i = (t.parameters[Instrument.PAR_TBL_TYPE] == 0) ? INSTRUMENT_TABLE_OF_NOTES : INSTRUMENT_TABLE_OF_FREQ;
		canvasXY.iconMiniXY(i, SHPAR[Instrument.PAR_TBL_TYPE].x() + 8 * 8 + 2, SHPAR[Instrument.PAR_TBL_TYPE].y() + 7);

		// inscription at the bottom of TABLE
		canvasXY.textMiniXY((i == INSTRUMENT_TABLE_OF_NOTES) ? "TABLE OF NOTES" : "TABLE OF FREQS", TABLE_X, TABLE_Y - 8, TextMiniColor.GRAY);

		// TABLE MODE icon (Set or Add)
		i = (t.parameters[Instrument.PAR_TBL_MODE] == 0) ? INSTRUMENT_TABLE_MODE_SET : INSTRUMENT_TABLE_MODE_ADD;
		canvasXY.iconMiniXY(i, SHPAR[Instrument.PAR_TBL_MODE].x() + 8 * 8 + 2, SHPAR[Instrument.PAR_TBL_MODE].y() + 7);

		// ENVELOPE
		int len = t.parameters[Instrument.PAR_ENV_LENGTH]; // par 5 is the length of the envelope
		for (i = 0; i <= len; i++) {
			drawEnv(i, instrNr);
		}

		// ENVELOPE LOOP ARROWS
		String arrow;
		int go = t.parameters[Instrument.PAR_ENV_GOTO]; // par 14 is the GO loop envelope
		if (go < len) {
			canvasXY.textXY("\u0007", ENV_X + 12 * 8 + len * 8, ENV_Y + 7 * 16, TextColor.WHITE); // Go from here
			arrow = "\u0006"; // Go here

			int lengo = len - go;
			if (lengo > 3) {
				canvasXY.numberMiniXY(lengo + 1, ENV_X + 11 * 8 + 4 + go * 8 + lengo * 4, ENV_Y + 7 * 16 + 4, TextMiniColor.GRAY); // len-go number
			}
		} else {
			arrow = "\u0016"; // GO from here to here
		}
		canvasXY.textXY(arrow, ENV_X + 12 * 8 + go * 8, ENV_Y + 7 * 16, TextColor.WHITE);
		if (go > 2) {
			canvasXY.numberMiniXY(go, ENV_X + 11 * 8 + go * 4, ENV_Y + 7 * 16 + 4, TextMiniColor.GRAY); // GO number
		}

		// TABLE
		len = t.parameters[Instrument.PAR_TBL_LENGTH]; // length table
		for (i = 0; i <= len; i++) {
			drawNoteTableValue(i, instrNr);
		}

		// TABLE LOOP ARROWS
		go = t.parameters[Instrument.PAR_TBL_GOTO]; // table GO loop
		if (len == 0) {
			canvasXY.textXY("\u0018", TABLE_X + 4, TABLE_Y + 8 + 16, TextColor.WHITE);
		} else {
			canvasXY.textXY("\u0019", TABLE_X + go * 8 * 3, TABLE_Y + 8 + 16, TextColor.WHITE);
			canvasXY.textXY("\u001a", TABLE_X + 8 + len * 8 * 3, TABLE_Y + 8 + 16, TextColor.WHITE);
		}

		if (!session.options.view.instrumentEditHelp) {
			return; // does not want help => end
		}
		// want help => continue

		if (t.activeEditSection == InstrumentSection.NAME) { // is the cursor on the instrument name?
			ui.isEditingInstrumentName = true;
		}

		if (t.activeEditSection == InstrumentSection.ENVELOPE) { // is the cursor on the envelope?
			ui.isEditingInstrumentName = false;
			switch (t.editEnvelopeY) {
			case EnvelopeParameter.DISTORTION -> {
				int d = t.envelope[t.editEnvelopeX][EnvelopeParameter.DISTORTION];
				final String[] distorHelp = { //
						"Distortion 0, white noise. (AUDC $0v, Poly5+17/9)", //
						"Distortion 2, square-ish tones. (AUDC $2v, Poly5)", //
						"Distortion 4, no note table yet, Pure Table by default. (AUDC $4v, Poly4+5)", //
						"16-Bit tones in valid channels, use command 6 to set the Distortion. (Distortion A by default)", //
						"Distortion 8, white noise. (AUDC $8v, Poly17/9)", //
						"Distortion A, pure tones. Special mode: CH1+CH3 1.79mhz + AUTOFILTER = Sawtooth (AUDC $Av)", //
						"Distortion C, buzzy bass tones. (AUDC $Cv, Poly4)", //
						"Distortion C, gritty bass tones. (AUDC $Cv, Poly4)" };
				canvasXY.textXY(distorHelp[(d >> 1) & 0x07], HELP_X, HELP_Y, TextColor.GRAY);
			}
			case EnvelopeParameter.COMMAND -> {
				int c = t.envelope[t.editEnvelopeX][EnvelopeParameter.COMMAND];
				final String[] commHelp = { //
						"Play BASE_NOTE + $XY semitones.", //
						"Play frequency $XY.", //
						"Play BASE_NOTE + frequency $XY.", //
						"Set BASE_NOTE += $XY semitones. Play BASE_NOTE.", //
						"Set FSHIFT += frequency $XY. Play BASE_NOTE.", //
						"Set portamento speed $X, step $Y. Play BASE_NOTE.", //
						"Set FILTER_SHFRQ += $XY. $0Y = BASS16 Distortion. $FF/$01 = Sawtooth inversion (Distortion A).", //
						"Set instrument AUDCTL. $FF = VOLUME ONLY mode. $FE/$FD = enable/disable Two-Tone Filter." };
				canvasXY.textXY(commHelp[c & 0x07], HELP_X, HELP_Y, TextColor.GRAY);
			}
			case EnvelopeParameter.X, EnvelopeParameter.Y -> {
				int unsigned = ((t.envelope[t.editEnvelopeX][EnvelopeParameter.X] << 4) | t.envelope[t.editEnvelopeX][EnvelopeParameter.Y]) & 0xFF;
				int signed = (byte) unsigned; // C++'s "char i"
				canvasXY.textXY(String.format("XY: $%02X = %d = %+d", unsigned, unsigned, signed), HELP_X, HELP_Y, TextColor.GRAY);
			}
			default -> {
			}
			}
		} else if (t.activeEditSection == InstrumentSection.PARAMETERS) {
			// The cursor is on the main parameters
			ui.isEditingInstrumentName = false;
			switch (t.editParameterNr) {
			case Instrument.PAR_DELAY -> {
				int v = t.parameters[t.editParameterNr] & 0xFF;
				String s = v > 0 ? String.format("$%02X = %d", v, v) : "$00 = no effects.";
				canvasXY.textXY(s, HELP_X, HELP_Y, TextColor.GRAY);
			}
			case Instrument.PAR_VOL_FADEOUT -> {
				int v = t.parameters[t.editParameterNr] & 0xFF;
				double f;
				if (v == 0) {
					f = 0;
				} else if (v == 0xff) {
					f = 1;
				} else {
					f = (double) v / 256 + 0.0005;
				}
				canvasXY.textXY(String.format(Locale.ROOT, "$%02X = -%.3f / vbi", v, f), HELP_X, HELP_Y, TextColor.GRAY);
			}
			default -> {
			}
			}
		}
		if (t.activeEditSection == InstrumentSection.NOTETABLE) {
			// The cursor is on the table
			ui.isEditingInstrumentName = false;
			int unsigned = t.noteTable[t.editNoteTableCursorPos] & 0xFF;
			int signed = (byte) unsigned; // C++'s "char i"
			canvasXY.textXY(String.format("$%02X = %+d", unsigned, signed), HELP_X, HELP_Y, TextColor.GRAY);
		}
	}

	/**
	 * Draw the instrument's name.
	 * Show edit state with cursor position.
	 * Drawn in line 9
	 */
	void drawName(int instrNr) {
		Instruments instruments = session.instruments;
		UiState ui = session.uiState;
		String name = SongUI.nameToString(instruments.getName(instrNr));
		int cursorPos = -1;
		TextColor color = TextColor.TURQUOISE;

		if (ui.activePart == Part.PART_INSTRUMENTS && instruments.getActiveEditSection(instrNr) == InstrumentSection.NAME) { // is an active change of instrument name
			cursorPos = instruments.getNameCursorPosition(instrNr);
			color = isProveMode() ? TextColor.BLUE : TextColor.RED;
			ui.isEditingInstrumentName = true;
		}

		canvasXY.textXY("NAME:", X, Y + 16, TextColor.WHITE); // Draw the title
		canvasXY.textXYSelN(name, cursorPos, X + 6 * 8, Y + 16, color); // Draw the name and highlight the cursor position
	}

	/** Draw an instruments parameter: */
	void drawParameter(int p, int instrNr) {
		Instruments instruments = session.instruments;
		ShPar par = SHPAR[p];
		String s = par.name();
		int x = par.x();
		int y = par.y();
		int showpar = (instruments.getParameter(instrNr, p) & 0xFF) + par.displayOffset();
		TextColor color = TextColor.WHITE;

		canvasXY.textXY(s, x, y, color);

		// Offset x to the end of parameter name after it was drawn
		x += 8 * (s.length() + 1);

		// If the cursor is on the main parameters
		if (session.uiState.activePart == Part.PART_INSTRUMENTS && instruments.getActiveEditSection(instrNr) == InstrumentSection.PARAMETERS && instruments.getParameterNumber(instrNr) == p) {
			color = getSelectedColor();
		}

		// Some parameters are 0..x but 1..x + 1 is displayed
		s = String.format(par.maxParameterValue() + par.displayOffset() > 0x0F ? "%02X" : " %01X", showpar);
		canvasXY.textXYSelN(s, -1, x, y, color);
	}

	void drawEnv(int e, int it) {
		Instrument in = session.instruments.getInstrument(it);
		boolean stereo = session.tracks4_8 > 4;
		int volR = in.envelope[e][EnvelopeParameter.VOLUMER] & 0x0f; // Volume Right
		int volL = in.envelope[e][EnvelopeParameter.VOLUMEL] & 0x0f; // Volume Left/Mono
		TextColor color;
		int x = ENV_X + 12 * 8 + e * 8;
		int ay = (in.activeEditSection == InstrumentSection.ENVELOPE && in.editEnvelopeX == e) ? in.editEnvelopeY : -1;

		// Volume Only mode uses Command 7 with $XY == $FF
		Color fillColor = (in.envelope[e][EnvelopeParameter.COMMAND] == 0x07 && in.envelope[e][EnvelopeParameter.X] == 0x0f && in.envelope[e][EnvelopeParameter.Y] == 0x0f) ? new Color(128, 255, 255) : new Color(255, 255, 255);

		// Volume column
		if (volL != 0) {
			canvasXY.fillSolidRect(x, ENV_Y + 3 * 16 + 4 + 4 * (15 - volL), 8, volL * 4, fillColor);
		}

		if (stereo && volR != 0) {
			canvasXY.fillSolidRect(x, ENV_Y - 2 * 16 + 4 + 4 * (15 - volR), 8, volR * 4, fillColor);
		}

		for (int j = 0; j < 8; j++) {
			char a = SHENV[j].ch();
			char c;
			if (a != 0) {
				if (in.envelope[e][j] != 0) {
					c = a;
				} else {
					c = 8; // Character in the envelope
				}
			} else {
				c = Song.charL4(in.envelope[e][j]);
			}

			if (j == ay && session.uiState.activePart == Part.PART_INSTRUMENTS) {
				color = getSelectedColor();
			} else {
				color = TextColor.WHITE;
			}

			String s = String.valueOf(c);
			if (j == 0) {
				if (stereo) {
					canvasXY.textXY(s, x, ENV_Y + 2 * 16, color); // Volume R is out of the box
				}
			} else {
				canvasXY.textXY(s, x, ENV_Y + 7 * 16 + j * 16, color);
			}
		}
	}

	/**
	 * Draw a note table value. Two numbers. Top is the table index, below is the note value
	 * Each entry is 24 pixels wide.
	 */
	void drawNoteTableValue(int noteIdx, int instrNr) {
		Instrument data = session.instruments.getInstrument(instrNr);

		// Draw the position #
		canvasXY.textMiniXY(String.format("%02X", noteIdx), TABLE_X + noteIdx * 24, TABLE_Y, TextMiniColor.GRAY);

		// Note Table parameter
		String s = String.format("%02X", data.noteTable[noteIdx] & 0xFF);

		TextColor color = TextColor.WHITE;
		if (data.activeEditSection == InstrumentSection.NOTETABLE && data.editNoteTableCursorPos == noteIdx && session.uiState.activePart == Part.PART_INSTRUMENTS) {
			color = getSelectedColor();
		}

		canvasXY.textXY(s, TABLE_X + noteIdx * 24, TABLE_Y + 8, color);
	}
}
