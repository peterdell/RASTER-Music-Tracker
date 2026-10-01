package org.atari.raster.rmt.ui;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.MathContext;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.atari.raster.rmt.model.Fraction;
import org.atari.raster.rmt.model.RmtVersion;
import org.atari.raster.rmt.model.KeyboardLayout;
import org.atari.raster.rmt.model.TrackerDriverVersion;
import org.atari.raster.rmt.model.TuningRatios;
import org.atari.raster.rmt.model.TuningSettings;

/**
 * The two configuration files, {@code rmt.ini} and {@code tuning.ini} -
 * the port of {@code CRmtView::ReadRMTConfig()/WriteRMTConfig()/
 * ResetRMTConfig()} and {@code ReadTuningConfig()/WriteTuningConfig()}.
 * C++ keeps both next to the program ({@code GetResourceFilePath("", name)}
 * under {@code g_prgpath}); here the folder is a constructor parameter (see
 * {@link RmtApplication#getProgramFolder()}), so tests use a temporary one.
 *
 * <p>The file format is C++'s byte for byte: a {@code # RMT CONFIGURATION
 * FILE} header with the version, {@code # SECTION} comments, and
 * {@code NAME = value} lines. The parser is C++'s too - it splits every line
 * that contains {@code '='} at that character, takes the name as everything
 * before the character preceding the {@code '='} ({@code tmp[-1] = 0}, the
 * single space) and the value as everything from two characters after it
 * ({@code tmp + 2}); numbers go through C's {@code atoi}/{@code atof}
 * (leading numeric prefix, 0 for anything else). Lines without {@code '='}
 * (the comments, blank lines) are skipped.
 *
 * <p>Deviations, all documented in {@code plans/NOTES.md} (B6): the
 * {@code MAJ_7TH} ratio is read back (C++ 1.35 wrote it but never read it -
 * fixed in both languages); a tuning ratio is stored normalized by
 * {@link Fraction} (C++ writes {@code 40 / 38} where Java writes
 * {@code 20 / 19} - the same ratio); a driver version number outside the
 * enum is ignored where C++ casts it to an invalid enum value.
 */
public final class RmtConfig {

	/** General.h's {@code CONFIG_FILENAME}. */
	public static final String CONFIG_FILENAME = "rmt.ini";
	/** General.h's {@code TUNING_FILENAME}. */
	public static final String TUNING_FILENAME = "tuning.ini";

	/** C++ reads and writes the files as raw bytes in the ANSI code page; the platform's native encoding is the closest match, so both programs can share one file. */
	static final Charset CHARSET = nativeCharset();

	private static final Pattern ATOI = Pattern.compile("^\\s*([+-]?\\d+)");
	private static final Pattern ATOF = Pattern.compile("^\\s*([+-]?(?:\\d+\\.?\\d*|\\.\\d+)(?:[eE][+-]?\\d+)?)");

	private final Path folder;
	/** The keyboard layout of a first start (no rmt.ini): the system's keyboard language - German QWERTZ, French AZERTY, else QWERTY; tests inject a fixed one. */
	private java.util.function.Supplier<KeyboardLayout> defaultKeyboardLayout = RmtConfig::systemKeyboardLayout;

	public void setDefaultKeyboardLayout(java.util.function.Supplier<KeyboardLayout> defaultKeyboardLayout) {
		this.defaultKeyboardLayout = defaultKeyboardLayout;
	}

	/** {@link KeyboardLayout#forLanguage} of the input method's locale (the keyboard language), else of the default locale. */
	static KeyboardLayout systemKeyboardLayout() {
		String language = null;
		try {
			if (!java.awt.GraphicsEnvironment.isHeadless()) {
				java.util.Locale locale = java.awt.im.InputContext.getInstance().getLocale();
				if (locale != null) {
					language = locale.getLanguage();
				}
			}
		} catch (RuntimeException e) {
			// no input context: the default locale below
		}
		if (language == null) {
			language = java.util.Locale.getDefault().getLanguage();
		}
		return KeyboardLayout.forLanguage(language);
	}

	public RmtConfig(Path folder) {
		this.folder = folder;
	}

	public Path getConfigPath() {
		return folder.resolve(CONFIG_FILENAME);
	}

	public Path getTuningPath() {
		return folder.resolve(TUNING_FILENAME);
	}

	private static Charset nativeCharset() {
		try {
			String name = System.getProperty("native.encoding");
			return name != null ? Charset.forName(name) : Charset.defaultCharset();
		} catch (RuntimeException ex) {
			return Charset.defaultCharset();
		}
	}

	// ---- rmt.ini ----

	/** {@code CRmtView::ReadRMTConfig()}: a missing file is reported, then {@link #resetRMTConfig} writes the defaults. */
	public void readRMTConfig(RmtSession session) {
		Path path = getConfigPath();
		List<String> lines;
		try {
			lines = Files.readAllLines(path, CHARSET);
		} catch (IOException ex) {
			session.messages.sendWarningMessage("RMT", "Could not find: '" + path + "'\n\nRMT will use the default configuration.\n");
			resetRMTConfig(session); // In order to save the default configuration file
			return;
		}
		parseRMTConfig(lines, session);
	}

	/** The parsing half of {@code ReadRMTConfig()}, on lines already read. */
	static void parseRMTConfig(List<String> lines, RmtSession session) {
		RmtOptions o = session.options;
		RmtOptions.ViewState view = o.view;
		for (String line : lines) {
			int eq = line.indexOf('=');
			if (eq < 0) {
				continue;
			}
			String name = nameOf(line, eq);
			String value = valueFrom(line, eq + 2);

			switch (name) {
			// GENERAL
			case "SCALEPERCENTAGE" -> o.scalingPercentage = atoi(value);
			case "TRACKLINEPRIMARYHIGHLIGHT" -> o.trackLinePrimaryHighlight = atoi(value);
			case "TRACKLINESECONDARYHIGHLIGHT" -> o.trackLineSecondaryHighlight = atoi(value);
			case "TRACKLINEALTNUMBERING" -> o.trackLineAltNumbering = atoi(value) != 0;
			case "DISPLAYFLATNOTES" -> o.displayFlatNotes = atoi(value) != 0;
			case "USEGERMANNOTATION" -> o.useGermanNotation = atoi(value) != 0;
			case "NOHWSOUNDBUFFER" -> o.noHwSoundBuffer = atoi(value) != 0;
			// TODO: Tracker must be in the module instead
			case "NTSC_SYSTEM" -> session.song.setNTSC(atoi(value) != 0);
			case "TRACKERDRIVERVERSION" -> {
				TrackerDriverVersion version = TrackerDriverVersion.getInstance(atoi(value));
				if (version != null) { // a number outside the range keeps the current value
					o.trackerDriverVersion = version;
				}
			}
			// KEYBOARD
			case "KEYBOARD_LAYOUT" -> o.keyboardLayout = KeyboardLayout.getInstance(atoi(value)); // an unknown number falls back to QWERTY
			case "KEYBOARD_UPDOWNCONTINUE" -> o.keyboardUpDownContinue = atoi(value) != 0;
			case "KEYBOARD_REMEMBEROCTAVESANDVOLUMES" -> o.keyboardRememberOctavesAndVolumes = atoi(value) != 0;
			case "KEYBOARD_ESCRESETATARISOUND" -> o.keyboardEscResetAtariSound = atoi(value) != 0;
			case "KEYBOARD_ASKWHENCONTROL_S" -> o.keyboardAskWhenControlS = atoi(value) != 0;
			// MIDI
			case "MIDI_IN" -> o.midiDevice = value;
			case "MIDI_TR" -> o.midiTouchResponse = atoi(value) != 0;
			case "MIDI_VOLUMEOFFSET" -> o.midiVolumeOffset = atoi(value);
			case "MIDI_NOTEOFF" -> o.midiNoteOff = atoi(value) != 0;
			// PATHS
			case "PATH_DEFAULTSONGS" -> o.defaultSongsPath = value;
			case "PATH_DEFAULTINSTRUMENTS" -> o.defaultInstrumentsPath = value;
			case "PATH_DEFAULTTRACKS" -> o.defaultTracksPath = value;
			case "PATH_LASTSONGS" -> o.lastSongsPath = value;
			case "PATH_LASTINSTRUMENTS" -> o.lastInstrumentsPath = value;
			case "PATH_LASTTRACKS" -> o.lastTracksPath = value;
			// VIEW
			case "VIEW_MAINTOOLBAR" -> view.mainToolbar = atoi(value) != 0;
			case "VIEW_BLOCKTOOLBAR" -> view.blockToolbar = atoi(value) != 0;
			case "VIEW_STATUSBAR" -> view.statusBar = atoi(value) != 0;
			case "VIEW_PLAYTIMECOUNTER" -> view.playTimeCounter = atoi(value) != 0;
			case "VIEW_VOLUMEANALYZER" -> view.volumeAnalyzer = atoi(value) != 0;
			case "VIEW_POKEYCHIPREGISTERS" -> view.pokeyRegisters = atoi(value) != 0;
			case "VIEW_INSTRUMENTACTIVEHELP" -> view.instrumentEditHelp = atoi(value) != 0;
			case "SMOOTH_SCROLL" -> view.smoothScrolling = atoi(value) != 0;
			case "VIEW_DEBUGDISPLAY" -> view.debugDisplay = atoi(value) != 0;
			default -> {
				// unknown names are skipped, as in C++
			}
			}
		}
	}

	/** {@code CRmtView::WriteRMTConfig()}. */
	public void writeRMTConfig(RmtSession session) {
		write(getConfigPath(), formatRMTConfig(session), session, "The RMT configuration won't be saved.");
	}

	/** The text {@code WriteRMTConfig()} produces, with {@code \n} line ends. */
	static String formatRMTConfig(RmtSession session) {
		RmtOptions o = session.options;
		RmtOptions.ViewState view = o.view;
		StringBuilder ou = new StringBuilder();
		ou.append("# RMT CONFIGURATION FILE\n");
		ou.append("# ").append(RmtVersion.RMT_VERSION_STRING).append('\n');

		ou.append("\n# GENERAL\n\n");
		line(ou, "SCALEPERCENTAGE", o.scalingPercentage);
		line(ou, "TRACKLINEPRIMARYHIGHLIGHT", o.trackLinePrimaryHighlight);
		line(ou, "TRACKLINESECONDARYHIGHLIGHT", o.trackLineSecondaryHighlight);
		line(ou, "TRACKLINEALTNUMBERING", o.trackLineAltNumbering);
		line(ou, "DISPLAYFLATNOTES", o.displayFlatNotes);
		line(ou, "USEGERMANNOTATION", o.useGermanNotation);
		line(ou, "NTSC_SYSTEM", session.song.isNTSC());
		line(ou, "NOHWSOUNDBUFFER", o.noHwSoundBuffer);
		line(ou, "TRACKERDRIVERVERSION", o.trackerDriverVersion.getNumber());

		ou.append("\n# KEYBOARD\n\n");
		line(ou, "KEYBOARD_LAYOUT", o.keyboardLayout.getNumber());
		line(ou, "KEYBOARD_UPDOWNCONTINUE", o.keyboardUpDownContinue);
		line(ou, "KEYBOARD_REMEMBEROCTAVESANDVOLUMES", o.keyboardRememberOctavesAndVolumes);
		line(ou, "KEYBOARD_ESCRESETATARISOUND", o.keyboardEscResetAtariSound);
		line(ou, "KEYBOARD_ASKWHENCONTROL_S", o.keyboardAskWhenControlS);

		ou.append("\n# MIDI\n\n");
		line(ou, "MIDI_IN", o.midiDevice);
		line(ou, "MIDI_TR", o.midiTouchResponse);
		line(ou, "MIDI_VOLUMEOFFSET", o.midiVolumeOffset);
		line(ou, "MIDI_NOTEOFF", o.midiNoteOff);

		ou.append("\n# PATHS\n\n");
		line(ou, "PATH_DEFAULTSONGS", o.defaultSongsPath);
		line(ou, "PATH_DEFAULTINSTRUMENTS", o.defaultInstrumentsPath);
		line(ou, "PATH_DEFAULTTRACKS", o.defaultTracksPath);
		line(ou, "PATH_LASTSONGS", o.lastSongsPath);
		line(ou, "PATH_LASTINSTRUMENTS", o.lastInstrumentsPath);
		line(ou, "PATH_LASTTRACKS", o.lastTracksPath);

		ou.append("\n# VIEW\n\n");
		line(ou, "VIEW_MAINTOOLBAR", view.mainToolbar);
		line(ou, "VIEW_BLOCKTOOLBAR", view.blockToolbar);
		line(ou, "VIEW_STATUSBAR", view.statusBar);
		line(ou, "VIEW_PLAYTIMECOUNTER", view.playTimeCounter);
		line(ou, "VIEW_VOLUMEANALYZER", view.volumeAnalyzer);
		line(ou, "VIEW_POKEYCHIPREGISTERS", view.pokeyRegisters);
		line(ou, "VIEW_INSTRUMENTACTIVEHELP", view.instrumentEditHelp);
		line(ou, "SMOOTH_SCROLL", view.smoothScrolling);
		line(ou, "VIEW_DEBUGDISPLAY", view.debugDisplay);
		return ou.toString();
	}

	/**
	 * {@code CRmtView::ResetRMTConfig()}: the defaults ({@link RmtOptions#reset()}),
	 * {@code SetNTSC(false)} (the view's, which rescales the base tuning even
	 * when the song already is PAL - harmless at start-up, where
	 * {@link #readTuningConfig} follows and overwrites it), and the file
	 * written so it exists next time, and {@code g_Midi.MidiInit()} (no
	 * device after the reset, so MIDI goes off).
	 */
	public void resetRMTConfig(RmtSession session) {
		session.options.reset();
		session.options.keyboardLayout = defaultKeyboardLayout.get(); // the first start: the system's keyboard language (since 2026-09-30)
		session.setNTSC(false); // NTSC (60Hz)
		session.midi.midiInit(); // MIDI must be initialised just in case
		writeRMTConfig(session); // Write the default configuration file
	}

	// ---- tuning.ini ----

	/** {@code CRmtView::ReadTuningConfig()}: a missing file is reported, the tuning variables reset and the file written. */
	public void readTuningConfig(RmtSession session) {
		Path path = getTuningPath();
		List<String> lines;
		try {
			lines = Files.readAllLines(path, CHARSET);
		} catch (IOException ex) {
			session.messages.sendWarningMessage("RMT", "Could not find: '" + path + "'\n\nRMT will use the default Tuning parameters.\n");
			session.song.resetTuningVariables(session.tuningSettings, session.tuningRatios);
			writeTuningConfig(session); // In order to save the default Tuning configuration file
			return;
		}
		parseTuningConfig(lines, session.tuningSettings, session.tuningRatios);
	}

	/** The parsing half of {@code ReadTuningConfig()}: {@code NAME = value} for the settings, {@code NAME = numerator / denominator} for the ratios. */
	static void parseTuningConfig(List<String> lines, TuningSettings tuning, TuningRatios ratios) {
		for (String line : lines) {
			int eq = line.indexOf('=');
			if (eq < 0) {
				continue;
			}
			int div = line.indexOf('/'); // The div pointer is used to get the 2nd Ratio value
			String name = nameOf(line, eq);
			String value;
			String value2;
			if (div >= 0) {
				value = line.substring(Math.min(eq + 2, line.length()), Math.max(Math.min(eq + 2, line.length()), div - 1));
				value2 = valueFrom(line, div + 2);
			} else {
				value = valueFrom(line, eq + 2);
				value2 = ""; // C++ leaves value2 unset here; only the ratio lines have a '/'
			}

			switch (name) {
			// TUNING
			case "TUNING" -> tuning.basetuning = atof(value);
			case "BASENOTE" -> tuning.basenote = atoi(value);
			case "TEMPERAMENT" -> tuning.temperament = atoi(value);
			// RATIOS
			case "UNISON" -> ratios.unison = readFraction(value, value2);
			case "MIN_2ND" -> ratios.min2nd = readFraction(value, value2);
			case "MAJ_2ND" -> ratios.maj2nd = readFraction(value, value2);
			case "MIN_3RD" -> ratios.min3rd = readFraction(value, value2);
			case "MAJ_3RD" -> ratios.maj3rd = readFraction(value, value2);
			case "PERF_4TH" -> ratios.perf4th = readFraction(value, value2);
			case "TRITONE" -> ratios.tritone = readFraction(value, value2);
			case "PERF_5TH" -> ratios.perf5th = readFraction(value, value2);
			case "MIN_6TH" -> ratios.min6th = readFraction(value, value2);
			case "MAJ_6TH" -> ratios.maj6th = readFraction(value, value2);
			case "MIN_7TH" -> ratios.min7th = readFraction(value, value2);
			case "MAJ_7TH" -> ratios.maj7th = readFraction(value, value2); // missing from C++ 1.35's read list although written - fixed in both
			case "OCTAVE" -> ratios.octave = readFraction(value, value2);
			default -> {
				// unknown names are skipped, as in C++
			}
			}
		}
	}

	/** {@code ReadFraction()}: a zero denominator becomes 1/1. */
	private static Fraction readFraction(String value, String value2) {
		int numerator = atoi(value);
		int denominator = atoi(value2);
		if (denominator == 0) {
			return new Fraction(1, 1);
		}
		return new Fraction(numerator, denominator);
	}

	/** {@code CRmtView::WriteTuningConfig()}. */
	public void writeTuningConfig(RmtSession session) {
		write(getTuningPath(), formatTuningConfig(session.tuningSettings, session.tuningRatios), session, "The Tuning parameters won't be saved.");
	}

	/** The text {@code WriteTuningConfig()} produces, with {@code \n} line ends. */
	static String formatTuningConfig(TuningSettings tuning, TuningRatios ratios) {
		StringBuilder os = new StringBuilder();
		os.append("# RMT CONFIGURATION FILE\n");
		os.append("# ").append(RmtVersion.RMT_VERSION_STRING).append('\n');

		os.append("\n# TUNING\n\n");
		os.append("TUNING = ").append(formatDouble(tuning.basetuning)).append('\n');
		line(os, "BASENOTE", tuning.basenote);
		line(os, "TEMPERAMENT", tuning.temperament);

		os.append("\n# RATIOS\n\n");
		fraction(os, "UNISON", ratios.unison);
		fraction(os, "MIN_2ND", ratios.min2nd);
		fraction(os, "MAJ_2ND", ratios.maj2nd);
		fraction(os, "MIN_3RD", ratios.min3rd);
		fraction(os, "MAJ_3RD", ratios.maj3rd);
		fraction(os, "PERF_4TH", ratios.perf4th);
		fraction(os, "TRITONE", ratios.tritone);
		fraction(os, "PERF_5TH", ratios.perf5th);
		fraction(os, "MIN_6TH", ratios.min6th);
		fraction(os, "MAJ_6TH", ratios.maj6th);
		fraction(os, "MIN_7TH", ratios.min7th);
		fraction(os, "MAJ_7TH", ratios.maj7th);
		fraction(os, "OCTAVE", ratios.octave);
		return os.toString();
	}

	// ---- helpers ----

	private void write(Path path, String text, RmtSession session, String consequence) {
		try {
			Path parent = path.getParent();
			if (parent != null) {
				Files.createDirectories(parent); // the per-user configuration folder does not exist on a first start
			}
			Files.writeString(path, text.replace("\n", System.lineSeparator()), CHARSET);
		} catch (IOException ex) {
			session.messages.sendWarningMessage("RMT", "Could not create: '" + path + "'\n\n" + consequence + "\n");
		}
	}

	/** {@code tmp[-1] = 0; name = line}: the text before the character preceding the {@code '='}. */
	private static String nameOf(String line, int eq) {
		return line.substring(0, Math.max(0, eq - 1));
	}

	/** {@code value = tmp + 2}: the text from two characters after a separator, "" if the line ends before that. */
	private static String valueFrom(String line, int from) {
		return from <= line.length() ? line.substring(from) : "";
	}

	private static void line(StringBuilder out, String name, int value) {
		out.append(name).append(" = ").append(value).append('\n');
	}

	private static void line(StringBuilder out, String name, boolean value) {
		line(out, name, value ? 1 : 0); // a BOOL streams as 0/1
	}

	private static void line(StringBuilder out, String name, String value) {
		out.append(name).append(" = ").append(value).append('\n');
	}

	/** {@code WriteFraction()}. */
	private static void fraction(StringBuilder out, String name, Fraction f) {
		out.append(name).append(" = ").append(f.numerator).append(" / ").append(f.denominator).append('\n');
	}

	/** C's {@code atoi}: the leading integer, 0 if there is none. */
	static int atoi(String s) {
		Matcher m = ATOI.matcher(s);
		if (!m.find()) {
			return 0;
		}
		try {
			return Integer.parseInt(m.group(1));
		} catch (NumberFormatException ex) {
			return m.group(1).startsWith("-") ? Integer.MIN_VALUE : Integer.MAX_VALUE; // C's overflow is undefined; saturate
		}
	}

	/** C's {@code atof}: the leading decimal number, 0 if there is none. */
	static double atof(String s) {
		Matcher m = ATOF.matcher(s);
		return m.find() ? Double.parseDouble(m.group(1)) : 0;
	}

	/** {@code os << std::setprecision(16) << double}: up to 16 significant digits, no trailing zeros, plain notation (the base tuning is 6.875-7040). */
	static String formatDouble(double value) {
		if (Double.isNaN(value) || Double.isInfinite(value)) {
			return Double.toString(value);
		}
		BigDecimal d = new BigDecimal(value).round(new MathContext(16)).stripTrailingZeros();
		return d.scale() < 0 ? d.setScale(0).toPlainString() : d.toPlainString();
	}
}
