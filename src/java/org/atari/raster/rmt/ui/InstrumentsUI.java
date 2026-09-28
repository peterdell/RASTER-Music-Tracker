package org.atari.raster.rmt.ui;

import java.awt.Color;
import java.util.Locale;

import org.atari.raster.rmt.model.EnvelopeParameter;
import org.atari.raster.rmt.model.Instrument;
import org.atari.raster.rmt.model.InstrumentSection;
import org.atari.raster.rmt.model.Instruments;
import org.atari.raster.rmt.model.Part;
import org.atari.raster.rmt.model.Song;

/**
 * The instrument editor screen - ported from the drawing half of
 * GUI_Instruments.cpp ({@code CInstruments::DrawInstrument}/{@code DrawName}/
 * {@code DrawParameter}/{@code DrawEnv}/{@code DrawNoteTableValue}). C++
 * implements these as methods of the model class {@code CInstruments};
 * this port keeps {@link Instruments} UI-free and puts them here, taking
 * the model (the deviation recorded in {@code plans/18_JAVA_UI_PORT_PLAN.md}).
 * The hit-testing half ({@link #getGUIArea}/{@link #cursorGoto}) is here
 * too, used by {@link MouseInput}.
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

	/**
	 * The UI columns of C++'s {@code Tshpar}: parameter index, screen
	 * position, name, the value range ({@code maxParameterValue}/
	 * {@code displayOffset}, which {@code DrawParameter} and {@code InstrKey}
	 * both need) and the four "next parameter on cursor movement" links
	 * ({@code InstrKey}'s Up/Down/Left/Right). The TXT-file columns
	 * ({@code parameterAND}, {@code fieldName}) live in {@link Instruments}.
	 */
	record ShPar(int paramIndex, int x, int y, String name, int maxParameterValue, int displayOffset, int gotoUp, int gotoDown, int gotoLeft, int gotoRight) {
	}

	/** Same order as C++'s {@code shpar[]} - indexed by parameter number. */
	static final ShPar[] SHPAR = { //
			// TABLE: LEN GO SPD TYPE MODE
			new ShPar(Instrument.PAR_TBL_LENGTH, PARAM_X + 16 * 8, PARAM_Y + 9 * 16, "LENGTH:", 0x1f, 1, 8, 1, 15, 15), //
			new ShPar(Instrument.PAR_TBL_GOTO, PARAM_X + 18 * 8, PARAM_Y + 10 * 16, "GOTO:", 0x1f, 0, 0, 2, 16, 16), //
			new ShPar(Instrument.PAR_TBL_SPEED, PARAM_X + 17 * 8, PARAM_Y + 11 * 16, "SPEED:", 0x3f, 1, 1, 3, 17, 17), //
			new ShPar(Instrument.PAR_TBL_TYPE, PARAM_X + 18 * 8, PARAM_Y + 12 * 16, "TYPE:", 0x01, 0, 2, 4, 18, 18), //
			new ShPar(Instrument.PAR_TBL_MODE, PARAM_X + 18 * 8, PARAM_Y + 13 * 16, "MODE:", 0x01, 0, 3, 5, 19, 19), //
			// ENVELOPE: LEN GO VSLIDE VMIN
			new ShPar(Instrument.PAR_ENV_LENGTH, PARAM_X + 16 * 8, PARAM_Y + 2 * 16, "LENGTH:", 0x2f, 1, 4, 6, 9, 9), //
			new ShPar(Instrument.PAR_ENV_GOTO, PARAM_X + 18 * 8, PARAM_Y + 3 * 16, "GOTO:", 0x2f, 0, 5, 7, 10, 10), //
			new ShPar(Instrument.PAR_VOL_FADEOUT, PARAM_X + 15 * 8, PARAM_Y + 4 * 16, "FADEOUT:", 0xff, 0, 6, 8, 11, 11), //
			new ShPar(Instrument.PAR_VOL_MIN, PARAM_X + 15 * 8, PARAM_Y + 5 * 16, "VOL MIN:", 0x0f, 0, 7, 0, 11, 11), //
			// EFFECT: DELAY VIBRATO FSHIFT
			new ShPar(Instrument.PAR_DELAY, PARAM_X + 3 * 8, PARAM_Y + 2 * 16, "DELAY:", 0xff, 0, 19, 10, 5, 5), //
			new ShPar(Instrument.PAR_VIBRATO, PARAM_X + 1 * 8, PARAM_Y + 3 * 16, "VIBRATO:", 0x03, 0, 9, 11, 6, 6), //
			new ShPar(Instrument.PAR_FREQ_SHIFT, PARAM_X + -1 * 8, PARAM_Y + 4 * 16, "FREQSHIFT:", 0xff, 0, 10, 12, 7, 7), //
			// AUDCTL: 00-07
			new ShPar(Instrument.PAR_AUDCTL_15KHZ, PARAM_X + 3 * 8, PARAM_Y + 6 * 16, "15KHZ:", 0x01, 0, 11, 13, 0, 0), //
			new ShPar(Instrument.PAR_AUDCTL_HPF_CH2, PARAM_X + 1 * 8, PARAM_Y + 7 * 16, "HPF 2+4:", 0x01, 0, 12, 14, 0, 0), //
			new ShPar(Instrument.PAR_AUDCTL_HPF_CH1, PARAM_X + 1 * 8, PARAM_Y + 8 * 16, "HPF 1+3:", 0x01, 0, 13, 15, 0, 0), //
			new ShPar(Instrument.PAR_AUDCTL_JOIN_3_4, PARAM_X + 0 * 8, PARAM_Y + 9 * 16, "JOIN 3+4:", 0x01, 0, 14, 16, 0, 0), //
			new ShPar(Instrument.PAR_AUDCTL_JOIN_1_2, PARAM_X + 0 * 8, PARAM_Y + 10 * 16, "JOIN 1+2:", 0x01, 0, 15, 17, 1, 1), //
			new ShPar(Instrument.PAR_AUDCTL_179_CH3, PARAM_X + 0 * 8, PARAM_Y + 11 * 16, "1.79 CH3:", 0x01, 0, 16, 18, 2, 2), //
			new ShPar(Instrument.PAR_AUDCTL_179_CH1, PARAM_X + 0 * 8, PARAM_Y + 12 * 16, "1.79 CH1:", 0x01, 0, 17, 19, 3, 3), //
			new ShPar(Instrument.PAR_AUDCTL_POLY9, PARAM_X + 3 * 8, PARAM_Y + 13 * 16, "POLY9:", 0x01, 0, 18, 9, 4, 4) };

	/** The UI columns of C++'s {@code Tshenv}: the marker character ({@code 0} = show the hex digit instead), the value mask and the +/- steps ({@code InstrKey}), name and position. */
	record ShEnv(char ch, int pand, int padd, int psub, String name, int xpos, int ypos) {
	}

	/** Same order as C++'s {@code shenv[]} - indexed by {@link EnvelopeParameter}. */
	static final ShEnv[] SHENV = { //
			new ShEnv((char) 0, 0x0f, 1, -1, "VOLUME R:", ENV_X + 2 * 8, ENV_Y + 2 * 16), // volume right
			new ShEnv((char) 0, 0x0f, 1, -1, "VOLUME L:", ENV_X + 2 * 8, ENV_Y + 8 * 16), // volume left
			new ShEnv((char) 0, 0x0e, 2, -2, "DISTORTION:", ENV_X + 0 * 8, ENV_Y + 9 * 16), // distortion 0,2,4,6,...
			new ShEnv((char) 0, 0x07, 1, -1, "COMMAND:", ENV_X + 3 * 8, ENV_Y + 10 * 16), // command 0-7
			new ShEnv((char) 0, 0x0f, 1, -1, "X/:", ENV_X + 8 * 8, ENV_Y + 11 * 16), // X
			new ShEnv((char) 0, 0x0f, 1, -1, "Y\\:", ENV_X + 8 * 8, ENV_Y + 12 * 16), // Y
			new ShEnv((char) 9, 0x01, 1, -1, "AUTOFILTER:", ENV_X + 0 * 8, ENV_Y + 13 * 16), // filter *
			new ShEnv((char) 9, 0x01, 1, -1, "PORTAMENTO:", ENV_X + 0 * 8, ENV_Y + 14 * 16) // portamento *
	};

	/** Ported from the C++ enum class {@code InstrumentGUIZone} (General.h) - the mouse-sensitive areas of the instrument screen. */
	public enum Zone {
		ENVELOPE_LEFT_ENVELOPE, ENVELOPE_RIGHT_ENVELOPE, ENVELOPE_PARAM_TABLE, ENVELOPE_RIGHT_VOL_NUMS, NOTE_TABLE, INSTRUMENT_NAME, PARAMETERS, INSTRUMENT_NUMBER_DLG, LEN_AND_GOTO_ARROWS, NOTE_TBL_LEN_AND_GOTO
	}

	private final RmtSession session;
	private final CanvasXY canvasXY;

	/** The drawing constructor. */
	public InstrumentsUI(RmtSession session, CanvasXY canvasXY) {
		this.session = session;
		this.canvasXY = canvasXY;
	}

	/** For hit-testing only ({@link #getGUIArea}/{@link #cursorGoto} never draw). */
	public InstrumentsUI(RmtSession session) {
		this(session, null);
	}

	/**
	 * {@code CInstruments::GetGUIArea()}: "Query the bounding rectangle of the
	 * requested GUI element", or {@code null} where C++ returns FALSE (the
	 * right-channel zones of a mono song). C++'s {@code CRect(l, t, r, b)}
	 * becomes an {@link java.awt.Rectangle}; {@code PtInRect} semantics
	 * (right/bottom exclusive) are what {@link java.awt.Rectangle#contains}
	 * implements too.
	 */
	public java.awt.Rectangle getGUIArea(int instrNr, Zone zone) {
		Instruments instruments = session.instruments;
		boolean stereo = session.tracks4_8 > 4;
		final int len = (instruments.getParameter(instrNr, Instrument.PAR_ENV_LENGTH) & 0xFF) + 1;
		final int tabl = (instruments.getParameter(instrNr, Instrument.PAR_TBL_LENGTH) & 0xFF) + 1;

		switch (zone) {
		case ENVELOPE_LEFT_ENVELOPE:
			// left channel volume curve (lower)
			return rect(ENV_X + 12 * 8, ENV_Y + 3 * 16 + 4, ENV_X + 12 * 8 + len * 8, ENV_Y + 3 * 16 + 4 + 4 * 16);

		case ENVELOPE_RIGHT_ENVELOPE:
			// right channel volume curve (upper)
			if (!stereo) {
				return null;
			}
			return rect(ENV_X + 12 * 8, ENV_Y - 2 * 16 + 4, ENV_X + 12 * 8 + len * 8, ENV_Y - 2 * 16 + 4 + 4 * 16);

		case ENVELOPE_PARAM_TABLE:
			// envelope area large table
			return rect(ENV_X + 12 * 8, ENV_Y + 3 * 16 + 0 + 5 * 16, ENV_X + 12 * 8 + len * 8, ENV_Y + 3 * 16 + 0 + 5 * 16 + 7 * 16);

		case ENVELOPE_RIGHT_VOL_NUMS:
			// envelope area of volume numbers for right channel
			if (!stereo) {
				return null;
			}
			return rect(ENV_X + 12 * 8, ENV_Y - 2 * 16 + 0 + 4 * 16, ENV_X + 12 * 8 + len * 8, ENV_Y - 2 * 16 + 0 + 4 * 16 + 16);

		case NOTE_TABLE:
			// instrument table line
			return rect(TABLE_X, TABLE_Y + 8, TABLE_X + tabl * 24 - 8, TABLE_Y + 8 + 16);

		case INSTRUMENT_NAME:
			// instrument name
			return rect(PARAM_X, PARAM_Y - 16, PARAM_X + 6 * 8 + Instrument.INSTRUMENT_NAME_MAX_LEN * 8, PARAM_Y + 0);

		case PARAMETERS:
			// instrument parameters
			return rect(PARAM_X, PARAM_Y + 32, PARAM_X + 26 * 8, PARAM_Y + 32 + 12 * 16);

		case INSTRUMENT_NUMBER_DLG:
			// instrument number
			return rect(X, Y, X + 13 * 8, Y + 16);

		case LEN_AND_GOTO_ARROWS:
			// envelope area under the left (lower) volume curve
			return rect(ENV_X + 12 * 8, ENV_Y + 3 * 16 + 0 + 4 * 16, ENV_X + 12 * 8 + Instrument.ENVELOPE_MAX_COLUMNS * 8, ENV_Y + 3 * 16 + 0 + 4 * 16 + 16);

		case NOTE_TBL_LEN_AND_GOTO:
			// instrument table + 1 line below parameter table
			return rect(TABLE_X, TABLE_Y + 8 + 1 * 16, TABLE_X + Instrument.NOTE_TABLE_MAX_LEN * 24 - 8, TABLE_Y + 8 + 2 * 16);
		}
		return null;
	}

	private static java.awt.Rectangle rect(int left, int top, int right, int bottom) {
		return new java.awt.Rectangle(left, top, right - left, bottom - top);
	}

	/**
	 * {@code CInstruments::CursorGoto()}: a click at {@code (x, y)} relative
	 * to zone {@code pzone}'s rectangle moves the instrument editor's cursor
	 * (zones 0-4) or sets the envelope/table length and loop point (zones
	 * 5-8, left/right button). Returns whether something was hit.
	 */
	public boolean cursorGoto(int instrNr, int x, int y, int pzone) {
		Instruments instruments = session.instruments;
		UiState ui = session.uiState;
		if (instrNr < 0 || instrNr >= Instruments.INSTRSNUM) {
			return false;
		}
		Instrument tt = instruments.getInstrument(instrNr);
		int px;
		int py;

		ui.isEditingInstrumentName = false; // when it is not edited, it shouldn't allow playing notes

		switch (pzone) {
		case 0:
			// envelope large table
			ui.activePart = Part.PART_INSTRUMENTS;
			tt.activeEditSection = InstrumentSection.ENVELOPE; // the envelope is active
			px = x / 8;
			if (px >= 0 && px <= tt.parameters[Instrument.PAR_ENV_LENGTH]) {
				tt.editEnvelopeX = px;
			}
			py = y / 16 + 1;
			if (py >= 1 && py < Instrument.ENVROWS) {
				tt.editEnvelopeY = py;
			}
			return true;
		case 1:
			// envelope line volume number of the right channel
			ui.activePart = Part.PART_INSTRUMENTS;
			tt.activeEditSection = InstrumentSection.ENVELOPE; // the envelope is active
			px = x / 8;
			if (px >= 0 && px <= tt.parameters[Instrument.PAR_ENV_LENGTH]) {
				tt.editEnvelopeX = px;
			}
			tt.editEnvelopeY = 0;
			return true;
		case 2:
			// TABLE
			ui.activePart = Part.PART_INSTRUMENTS;
			tt.activeEditSection = InstrumentSection.NOTETABLE; // the table is active
			px = (x + 4) / (3 * 8);
			if (px >= 0 && px <= tt.parameters[Instrument.PAR_TBL_LENGTH]) {
				tt.editNoteTableCursorPos = px;
			}
			return true;
		case 3:
			// INSTRUMENT NAME
			ui.activePart = Part.PART_INSTRUMENTS;
			tt.activeEditSection = InstrumentSection.NAME; // the name is active
			ui.isEditingInstrumentName = true; // instrument name is being edited
			px = x / 8 - 6;
			if (px >= 0 && px <= Instrument.INSTRUMENT_NAME_MAX_LEN) {
				tt.editNameCursorPos = px;
			}
			if (px < 0) {
				tt.editNameCursorPos = 0;
			}
			return true;
		case 4: {
			// INSTRUMENT PARAMETERS
			px = x / 8;
			py = y / 16;
			if (px > 11 && px < 15) {
				return false; // middle empty part
			}
			if (py < 0 || py > 12) {
				return false; // just in case
			}
			final int[][] xytopar = { //
					{ Instrument.PAR_DELAY, Instrument.PAR_VIBRATO, Instrument.PAR_FREQ_SHIFT, -1, Instrument.PAR_AUDCTL_15KHZ, Instrument.PAR_AUDCTL_HPF_CH2, Instrument.PAR_AUDCTL_HPF_CH1, Instrument.PAR_AUDCTL_JOIN_3_4, Instrument.PAR_AUDCTL_JOIN_1_2, Instrument.PAR_AUDCTL_179_CH3, Instrument.PAR_AUDCTL_179_CH1, Instrument.PAR_AUDCTL_POLY9 }, //
					{ Instrument.PAR_ENV_LENGTH, Instrument.PAR_ENV_GOTO, Instrument.PAR_VOL_FADEOUT, Instrument.PAR_VOL_MIN, -1, -1, -1, Instrument.PAR_TBL_LENGTH, Instrument.PAR_TBL_GOTO, Instrument.PAR_TBL_SPEED, Instrument.PAR_TBL_TYPE, Instrument.PAR_TBL_MODE } };
			int p = xytopar[px > 11 ? 1 : 0][py];
			if (p >= 0 && p < Instrument.NUMBER_OF_PARAMS) {
				tt.editParameterNr = p;
				ui.activePart = Part.PART_INSTRUMENTS;
				tt.activeEditSection = InstrumentSection.PARAMETERS; // parameters are active
				return true;
			}
			return false;
		}

		case 5:
			// INSTRUMENT SET ENVELOPE LEN/GO PARAMETER by MOUSE
			// left mouse button
			// changes GO and moves LEN if necessary
			px = x / 8;
			if (px < 0) {
				px = 0;
			} else if (px >= Instrument.ENVELOPE_MAX_COLUMNS) {
				px = Instrument.ENVELOPE_MAX_COLUMNS - 1;
			}
			tt.parameters[Instrument.PAR_ENV_GOTO] = px;
			if (tt.parameters[Instrument.PAR_ENV_LENGTH] < px) {
				tt.parameters[Instrument.PAR_ENV_LENGTH] = px;
			}
			return instrumentParametersChanged(instrNr);

		case 6:
			// INSTRUMENT SET ENVELOPE LEN/GO PARAMETER by MOUSE
			// right mouse button
			// changes LEN and moves GO if necessary
			px = x / 8;
			if (px < 0) {
				px = 0;
			} else if (px >= Instrument.ENVELOPE_MAX_COLUMNS) {
				px = Instrument.ENVELOPE_MAX_COLUMNS - 1;
			}
			tt.parameters[Instrument.PAR_ENV_LENGTH] = px;
			if (tt.parameters[Instrument.PAR_ENV_GOTO] > px) {
				tt.parameters[Instrument.PAR_ENV_GOTO] = px;
			}
			return instrumentParametersChanged(instrNr);
		case 7:
			// TABLE SET LEN/GO PARAMETER by MOUSE
			// left mouse button
			// changes GO and moves LEN if necessary
			px = (x + 4) / (3 * 8);
			if (px < 0) {
				px = 0;
			} else if (px >= Instrument.NOTE_TABLE_MAX_LEN) {
				px = Instrument.NOTE_TABLE_MAX_LEN - 1;
			}
			tt.parameters[Instrument.PAR_TBL_GOTO] = px;
			if (tt.parameters[Instrument.PAR_TBL_LENGTH] < px) {
				tt.parameters[Instrument.PAR_TBL_LENGTH] = px;
			}
			return instrumentParametersChanged(instrNr);
		case 8:
			// TABLE SET LEN/GO PARAMETER by MOUSE
			// right mouse button
			// changes LEN and moves GO if necessary
			px = (x + 4) / (3 * 8);
			if (px < 0) {
				px = 0;
			} else if (px >= Instrument.NOTE_TABLE_MAX_LEN) {
				px = Instrument.NOTE_TABLE_MAX_LEN - 1;
			}
			tt.parameters[Instrument.PAR_TBL_LENGTH] = px;
			if (tt.parameters[Instrument.PAR_TBL_GOTO] > px) {
				tt.parameters[Instrument.PAR_TBL_GOTO] = px;
			}
			return instrumentParametersChanged(instrNr);
		default:
			break;
		}
		return false;
	}

	/** C++'s {@code CG_InstrumentParametersChanged:} label: "because there has been some change in the instrument parameter => this instrument will stop on all channels". */
	private boolean instrumentParametersChanged(int instrNr) {
		session.atariTrackerDriver.instrumentTurnOff(instrNr);
		session.instruments.checkInstrumentParameters(instrNr);
		// something changed => Save instrument "to Atari"
		session.instruments.update(instrNr);
		return true;
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
