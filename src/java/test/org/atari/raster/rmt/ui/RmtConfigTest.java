package org.atari.raster.rmt.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.atari.raster.rmt.model.Fraction;
import org.atari.raster.rmt.model.KeyboardLayout;
import org.atari.raster.rmt.model.MessageAnswer;
import org.atari.raster.rmt.model.MessageButtons;
import org.atari.raster.rmt.model.Messages;
import org.atari.raster.rmt.model.TrackerDriverVersion;
import org.atari.raster.rmt.model.TuningRatios;
import org.atari.raster.rmt.model.TuningSettings;

/** rmt.ini / tuning.ini: the exact text C++ writes, the C++ parser's behavior, and the missing-file paths. */
class RmtConfigTest {

	/** What Rmt.exe writes for its defaults (ResetRMTConfig), with the C++ order and spacing. */
	static final String DEFAULT_RMT_INI = """
			# RMT CONFIGURATION FILE
			# RASTER Music Tracker 1.36

			# GENERAL

			SCALEPERCENTAGE = 100
			TRACKLINEPRIMARYHIGHLIGHT = 8
			TRACKLINESECONDARYHIGHLIGHT = 4
			TRACKLINEALTNUMBERING = 0
			DISPLAYFLATNOTES = 0
			USEGERMANNOTATION = 0
			NTSC_SYSTEM = 0
			NOHWSOUNDBUFFER = 0
			TRACKERDRIVERVERSION = 6

			# KEYBOARD

			KEYBOARD_LAYOUT = 0
			KEYBOARD_UPDOWNCONTINUE = 1
			KEYBOARD_REMEMBEROCTAVESANDVOLUMES = 1
			KEYBOARD_ESCRESETATARISOUND = 1
			KEYBOARD_ASKWHENCONTROL_S = 1

			# MIDI

			MIDI_IN =\s
			MIDI_TR = 0
			MIDI_VOLUMEOFFSET = 0
			MIDI_NOTEOFF = 0

			# PATHS

			PATH_DEFAULTSONGS =\s
			PATH_DEFAULTINSTRUMENTS =\s
			PATH_DEFAULTTRACKS =\s
			PATH_LASTSONGS =\s
			PATH_LASTINSTRUMENTS =\s
			PATH_LASTTRACKS =\s

			# VIEW

			VIEW_MAINTOOLBAR = 1
			VIEW_BLOCKTOOLBAR = 1
			VIEW_STATUSBAR = 1
			VIEW_PLAYTIMECOUNTER = 1
			VIEW_VOLUMEANALYZER = 1
			VIEW_POKEYCHIPREGISTERS = 1
			VIEW_INSTRUMENTACTIVEHELP = 1
			SMOOTH_SCROLL = 1
			VIEW_DEBUGDISPLAY = 1
			""";

	/** The default tuning.ini - note the ratios as C++ writes them before Fraction's normalization (40/38 etc.); Java writes them reduced. */
	static final String DEFAULT_TUNING_INI = """
			# RMT CONFIGURATION FILE
			# RASTER Music Tracker 1.36

			# TUNING

			TUNING = 440.83751645933
			BASENOTE = 3
			TEMPERAMENT = 0

			# RATIOS

			UNISON = 1 / 1
			MIN_2ND = 20 / 19
			MAJ_2ND = 10 / 9
			MIN_3RD = 20 / 17
			MAJ_3RD = 5 / 4
			PERF_4TH = 4 / 3
			TRITONE = 60 / 43
			PERF_5TH = 3 / 2
			MIN_6TH = 30 / 19
			MAJ_6TH = 5 / 3
			MIN_7TH = 30 / 17
			MAJ_7TH = 15 / 8
			OCTAVE = 2 / 1
			""";

	private static final class RecordingMessages implements Messages.Handler {
		final List<String> warnings = new ArrayList<>();

		@Override
		public void showError(String title, String message) {
			throw new AssertionError(message);
		}

		@Override
		public void showWarning(String title, String message) {
			warnings.add(title + ": " + message);
		}

		@Override
		public void showInformation(String title, String message) {
			throw new AssertionError(message);
		}

		@Override
		public MessageAnswer askQuestion(String title, String message, MessageButtons buttons) {
			throw new AssertionError(message);
		}
	}

	@Test
	void theDefaultsFormatExactlyAsRmtExeWritesThem() {
		RmtSession session = new RmtSession();
		assertEquals(DEFAULT_RMT_INI, RmtConfig.formatRMTConfig(session));
		assertEquals(DEFAULT_TUNING_INI, RmtConfig.formatTuningConfig(session.tuningSettings, session.tuningRatios));
	}

	@Test
	void everyValueSurvivesAWriteReadRoundTrip(@TempDir Path dir) {
		RmtSession written = new RmtSession();
		RmtOptions o = written.options;
		o.scalingPercentage = 200;
		o.trackLinePrimaryHighlight = 16;
		o.trackLineSecondaryHighlight = 3;
		o.trackLineAltNumbering = true;
		o.displayFlatNotes = true;
		o.useGermanNotation = true;
		o.noHwSoundBuffer = true;
		o.trackerDriverVersion = TrackerDriverVersion.PATCH3;
		o.keyboardLayout = KeyboardLayout.AZERTY;
		o.keyboardUpDownContinue = false;
		o.keyboardRememberOctavesAndVolumes = false;
		o.keyboardEscResetAtariSound = false;
		o.keyboardAskWhenControlS = false;
		o.midiDevice = "USB MIDI Interface";
		o.midiTouchResponse = true;
		o.midiVolumeOffset = 5;
		o.midiNoteOff = true;
		o.defaultSongsPath = "C:\\Music\\Songs = mine";
		o.defaultInstrumentsPath = "C:\\Music\\Instruments";
		o.defaultTracksPath = "C:\\Music\\Tracks";
		o.lastSongsPath = "D:\\last\\songs";
		o.lastInstrumentsPath = "D:\\last\\instruments";
		o.lastTracksPath = "D:\\last\\tracks";
		o.view.mainToolbar = false;
		o.view.blockToolbar = false;
		o.view.statusBar = false;
		o.view.playTimeCounter = false;
		o.view.volumeAnalyzer = false;
		o.view.pokeyRegisters = false;
		o.view.instrumentEditHelp = false;
		o.view.smoothScrolling = false;
		o.view.debugDisplay = false;
		written.song.setNTSC(true);
		written.tuningSettings.basetuning = 432.5;
		written.tuningSettings.basenote = 7;
		written.tuningSettings.temperament = 29;
		written.tuningRatios.maj7th = new Fraction(243, 128);
		written.tuningRatios.octave = new Fraction(4, 2);

		RmtConfig config = new RmtConfig(dir);
		config.writeRMTConfig(written);
		config.writeTuningConfig(written);
		assertTrue(Files.exists(dir.resolve("rmt.ini")));
		assertTrue(Files.exists(dir.resolve("tuning.ini")));

		RmtSession read = new RmtSession();
		RecordingMessages messages = new RecordingMessages();
		read.messages.setHandler(messages);
		config.readRMTConfig(read);
		config.readTuningConfig(read);
		assertTrue(messages.warnings.isEmpty());

		assertEquals(RmtConfig.formatRMTConfig(written), RmtConfig.formatRMTConfig(read));
		assertEquals(RmtConfig.formatTuningConfig(written.tuningSettings, written.tuningRatios), RmtConfig.formatTuningConfig(read.tuningSettings, read.tuningRatios));
		assertTrue(read.song.isNTSC());
		assertEquals("C:\\Music\\Songs = mine", read.options.defaultSongsPath); // only the first '=' splits
		assertEquals(new Fraction(243, 128), read.tuningRatios.maj7th); // MAJ_7TH is read back (C++ 1.35 only wrote it)
		assertEquals(2, read.tuningRatios.octave.numerator); // normalized by Fraction
	}

	@Test
	void aMissingRmtIniIsReportedAndTheDefaultsWritten(@TempDir Path dir) throws IOException {
		RmtSession session = new RmtSession();
		session.options.scalingPercentage = 250;
		session.options.view.debugDisplay = false;
		session.song.setNTSC(true);
		RecordingMessages messages = new RecordingMessages();
		session.messages.setHandler(messages);

		new RmtConfig(dir).readRMTConfig(session);

		assertEquals(1, messages.warnings.size());
		assertTrue(messages.warnings.get(0).startsWith("RMT: Could not find: '" + dir.resolve("rmt.ini") + "'\n\nRMT will use the default configuration.\n"));
		assertEquals(100, session.options.scalingPercentage);
		assertTrue(session.options.view.debugDisplay);
		assertFalse(session.song.isNTSC());
		assertEquals(DEFAULT_RMT_INI, Files.readString(dir.resolve("rmt.ini"), RmtConfig.CHARSET).replace(System.lineSeparator(), "\n"));
	}

	@Test
	void aMissingTuningIniResetsTheTuningAndWritesTheDefaults(@TempDir Path dir) throws IOException {
		RmtSession session = new RmtSession();
		session.tuningSettings.basetuning = 999;
		session.tuningRatios.octave = new Fraction(3, 1);
		RecordingMessages messages = new RecordingMessages();
		session.messages.setHandler(messages);

		new RmtConfig(dir).readTuningConfig(session);

		assertEquals(1, messages.warnings.size());
		assertTrue(messages.warnings.get(0).contains("RMT will use the default Tuning parameters."));
		assertEquals(440.83751645933, session.tuningSettings.basetuning);
		assertEquals(new Fraction(2, 1), session.tuningRatios.octave);
		assertEquals(DEFAULT_TUNING_INI, Files.readString(dir.resolve("tuning.ini"), RmtConfig.CHARSET).replace(System.lineSeparator(), "\n"));
	}

	@Test
	void theParserBehavesLikeTheCppOne() {
		RmtSession session = new RmtSession();
		RmtConfig.parseRMTConfig(List.of(
				"# a comment without an equals sign is skipped", //
				"", //
				"SCALEPERCENTAGE = 150", //
				"TRACKLINEPRIMARYHIGHLIGHT = 12abc", // atoi: the leading digits
				"TRACKLINESECONDARYHIGHLIGHT = abc", // atoi: 0
				"TRACKERDRIVERVERSION = 99", // not an enum value: ignored (C++ would cast it)
				"KEYBOARD_LAYOUT=1", // no space before '=': the name loses its last character and doesn't match
				"UNKNOWN = 1", //
				"VIEW_STATUSBAR = 0", //
				"MIDI_IN = My Device = 2"), session); // the value runs to the end of the line
		RmtOptions o = session.options;
		assertEquals(150, o.scalingPercentage);
		assertEquals(12, o.trackLinePrimaryHighlight);
		assertEquals(0, o.trackLineSecondaryHighlight);
		assertEquals(TrackerDriverVersion.PATCH16, o.trackerDriverVersion);
		assertEquals(KeyboardLayout.QWERTY, o.keyboardLayout);
		assertFalse(o.view.statusBar);
		assertTrue(o.view.mainToolbar);
		assertEquals("My Device = 2", o.midiDevice);

		TuningSettings tuning = new TuningSettings();
		TuningRatios ratios = new TuningRatios();
		ratios.initialize();
		RmtConfig.parseTuningConfig(List.of(
				"TUNING = 432.5Hz", // atof: the leading number
				"BASENOTE = 9", //
				"TEMPERAMENT = 29", //
				"PERF_5TH = 3 / 2", //
				"OCTAVE = 4 / 0", // a zero denominator becomes 1/1
				"MAJ_3RD = 5", // no '/': C++'s value2 is unset; treated as 0 -> 1/1
				"MIN_2ND = 40 / 38"), tuning, ratios);
		assertEquals(432.5, tuning.basetuning);
		assertEquals(9, tuning.basenote);
		assertEquals(29, tuning.temperament);
		assertEquals(new Fraction(3, 2), ratios.perf5th);
		assertEquals(new Fraction(1, 1), ratios.octave);
		assertEquals(new Fraction(1, 1), ratios.maj3rd);
		assertEquals(new Fraction(20, 19), ratios.min2nd);
		assertEquals(new Fraction(10, 9), ratios.maj2nd); // untouched
	}

	@Test
	void cNumberHelpers() {
		assertEquals(0, RmtConfig.atoi(""));
		assertEquals(0, RmtConfig.atoi(" - "));
		assertEquals(-42, RmtConfig.atoi("  -42x"));
		assertEquals(7, RmtConfig.atoi("+7.9"));
		assertEquals(Integer.MAX_VALUE, RmtConfig.atoi("99999999999"));
		assertEquals(0.0, RmtConfig.atof("abc"));
		assertEquals(440.83751645933, RmtConfig.atof("440.83751645933"));
		assertEquals(1.5e3, RmtConfig.atof(" 1.5e3 "));
		assertEquals(0.5, RmtConfig.atof(".5"));
		// std::setprecision(16) on the default float format
		assertEquals("440.83751645933", RmtConfig.formatDouble(440.83751645933));
		assertEquals("444.895778867913", RmtConfig.formatDouble(444.895778867913));
		assertEquals("440", RmtConfig.formatDouble(440));
		assertEquals("6.875", RmtConfig.formatDouble(6.875));
		assertEquals("7040", RmtConfig.formatDouble(7040));
		assertEquals("0.1", RmtConfig.formatDouble(0.1));
		assertEquals("0.3333333333333333", RmtConfig.formatDouble(1.0 / 3)); // 16 significant digits
	}
}
