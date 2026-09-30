package org.atari.raster.rmt.ui;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.atari.raster.rmt.model.AsmFileExporter;
import org.atari.raster.rmt.model.AssemblerFormat;
import org.atari.raster.rmt.script.ScriptRunner;

/**
 * The script runner against the dialog path: the same exports through {@link SongFiles} with the stub host answering the dialogs' defaults must produce the same bytes, and the failure cases the
 * documented exit codes. Lives in the ui test package for {@link StubSongFilesHost}.
 */
class ScriptRunnerTest {

	@TempDir
	Path dir;

	private ByteArrayOutputStream out;
	private ByteArrayOutputStream err;
	private Path delta;

	@BeforeEach
	void setUp() throws IOException {
		out = new ByteArrayOutputStream();
		err = new ByteArrayOutputStream();
		delta = dir.resolve("Delta.rmt");
		Files.copy(ReferenceScreenshot.ROOT.resolve("song1-mono").resolve("Delta.rmt"), delta);
	}

	private int run(String... lines) throws IOException {
		Path script = dir.resolve("run.rmtscript");
		Files.write(script, List.of(lines), StandardCharsets.UTF_8);
		ScriptRunner runner = new ScriptRunner(new RmtSession(), new PrintStream(out, true, StandardCharsets.UTF_8), new PrintStream(err, true, StandardCharsets.UTF_8));
		return runner.run(script);
	}

	private String err() {
		return err.toString(StandardCharsets.UTF_8);
	}

	// ---- midi ----

	/** {@code midi} records like the MIDI IN device would, with the window counted as focused; the MIDI options are script settings. */
	@Test
	void midiMessagesRecordNotesAndTheMidiSettingsApply() throws IOException {
		RmtSession session = new RmtSession();
		Path script = dir.resolve("midi.rmtscript");
		Files.write(script, List.of( //
				"open " + delta.getFileName(), //
				"midi 90 3C 64", // C-3 on channel 1 at the cursor
				"midi CA 05", // instrument 5 (channel 11)
				"set midi-touch-response yes", //
				"set midi-volume-offset 3", //
				"midi 90 3E 40", //
				"set midi-note-off yes", //
				"midi 80 3E 00"), StandardCharsets.UTF_8);
		ScriptRunner runner = new ScriptRunner(session, new PrintStream(out, true, StandardCharsets.UTF_8), new PrintStream(err, true, StandardCharsets.UTF_8));

		assertEquals(ScriptRunner.EXIT_OK, runner.run(script), err());

		int track = session.song.getSong()[0][0];
		org.atari.raster.rmt.model.Track tr = session.tracks.getTrack(track);
		assertEquals(24, tr.note[0]);
		assertEquals(5, session.song.getActiveInstr());
		assertEquals(26, tr.note[1]);
		assertEquals(5, tr.instr[1]);
		assertEquals(11, tr.volume[1]); // 3 + 64 / 8
		assertTrue(session.options.midiTouchResponse && session.options.midiNoteOff);
		assertEquals(3, session.options.midiVolumeOffset);
		assertEquals(0, tr.volume[2], "the release deleted the note at the cursor and wrote volume 0");
		assertEquals(3, session.song.getActiveLine());
	}

	@Test
	void midiRejectsBadBytes() throws IOException {
		assertEquals(ScriptRunner.EXIT_COMMAND_FAILED, run("midi 90"));
		assertTrue(err().contains("Usage: midi <status> <data1> [<data2>]"), err());
		err.reset();
		assertEquals(ScriptRunner.EXIT_COMMAND_FAILED, run("midi 90 ZZ"));
		assertTrue(err().contains("'ZZ' is not a MIDI byte"), err());
		err.reset();
		assertEquals(ScriptRunner.EXIT_COMMAND_FAILED, run("set midi-volume-offset 16"));
		assertTrue(err().contains("must be a number from 0 to 15"), err());
	}

	private String out() {
		return out.toString(StandardCharsets.UTF_8);
	}

	/** The dialog path with the dialogs' untouched defaults, as the UI's OK would answer them. */
	private static Path dialogExport(Path dir, String name, int filterIndex) throws IOException {
		RmtSession session = new RmtSession();
		StubSongFilesHost host = new StubSongFilesHost();
		SongFiles files = new SongFiles(session, host);
		assertTrue(files.fileOpen(dir.resolve("Delta.rmt"), false));
		host.answerDialogDefaults = true;
		host.nextStrippedRmt = new SongFiles.StrippedRmtChoice(0x4000, AssemblerFormat.XASM, false, false, false);
		host.nextAsm = new SongFiles.AsmChoice(1, 1, 1, "MUSIC");
		host.nextRelocatableAsm = new AsmFileExporter.RelocatableAsmExportParams("RMT_SONG_DATA", false, false, false, "RMT_INSTRUMENT_DATA", "RMT_SONG_TRACKS", "RMT_SONG_LINES", AssemblerFormat.XASM, false, false, false);
		host.answer(dir.resolve(name), filterIndex);
		files.fileExportAs();
		return SongFiles.ensureFileExtension(dir.resolve(name), SongFiles.EXPORT_FILTERS, filterIndex);
	}

	@Test
	void everyExportFormatWithDefaultsEqualsTheDialogPathByteForByte() throws IOException {
		int code = run( //
				"open Delta.rmt", //
				"export stripped-rmt s.rmt", //
				"export asm s.asm", //
				"export sapr s.sapr", //
				"export lzss s.lzss", //
				"export sap s.sap", //
				"export xex s.xex", //
				"export rmtplayer-asm s_player.asm", //
				"export wav s.wav", //
				"echo all done");
		assertEquals(ScriptRunner.EXIT_OK, code, err());
		assertTrue(out().contains("all done"), out());
		assertEquals("", err());

		String[] names = { "s.rmt", "s.asm", "s.sapr", "s.lzss", "s.sap", "s.xex", "s_player.asm", "s.wav" };
		Path reference = Files.createDirectory(dir.resolve("dialog"));
		Files.copy(delta, reference.resolve("Delta.rmt"));
		for (int i = 0; i < names.length; i++) {
			Path expected = dialogExport(reference, names[i], i + 1);
			assertArrayEquals(Files.readAllBytes(expected), Files.readAllBytes(dir.resolve(names[i])), names[i]);
		}
		assertTrue(Files.exists(dir.resolve("s_LOOP.lzss")), "the LZSS export's loop sibling");
	}

	@Test
	void optionsReachTheExportersAndTheirValuesAreChecked() throws IOException {
		assertEquals(ScriptRunner.EXIT_OK, run("open Delta.rmt", "export asm p.asm prefix=TUNE", "export sapr p.sapr author=\"Me\" name=\"P\"", "export xex p.xex text=\"LINE1\\nLINE2\" rasterbar=no color=20"), err());
		String asm = Files.readString(dir.resolve("p.asm"), SongFiles.TEXT_CHARSET);
		assertTrue(asm.contains("TUNE"), asm.substring(0, Math.min(200, asm.length())));
		String sapr = new String(Files.readAllBytes(dir.resolve("p.sapr")), 0, 80, SongFiles.TEXT_CHARSET);
		assertTrue(sapr.contains("AUTHOR \"Me\"") && sapr.contains("NAME \"P\""), sapr);

		err.reset();
		assertEquals(ScriptRunner.EXIT_COMMAND_FAILED, run("open Delta.rmt", "export stripped-rmt q.rmt address=nope"));
		assertTrue(err().contains("line 2") && err().contains("address"), err());
		err.reset();
		assertEquals(ScriptRunner.EXIT_COMMAND_FAILED, run("open Delta.rmt", "export wav q.wav volume=11"));
		assertTrue(err().contains("Unknown option 'volume'"), err());
		err.reset();
		assertEquals(ScriptRunner.EXIT_COMMAND_FAILED, run("open Delta.rmt", "export mp3 q.mp3"));
		assertTrue(err().contains("Unknown export format 'mp3'"), err());
	}

	@Test
	void saveWritesTheSongInTheFormatOfTheExtension() throws IOException {
		assertEquals(ScriptRunner.EXIT_OK, run("open Delta.rmt", "save copy.rmt", "save copy.txt", "save copy.rmw", "open copy.rmt", "save copy2.rmt"), err());
		// a re-save of Delta.rmt is not byte-identical to the 2003 original (a different "save all" rule, see SongFilesTest), but it is idempotent
		byte[] copy = Files.readAllBytes(dir.resolve("copy.rmt"));
		assertEquals("RMT4", new String(copy, 6, 4, SongFiles.TEXT_CHARSET));
		assertArrayEquals(copy, Files.readAllBytes(dir.resolve("copy2.rmt")));
		assertTrue(Files.readString(dir.resolve("copy.txt"), SongFiles.TEXT_CHARSET).startsWith("[MODULE]"));
		assertTrue(Files.size(dir.resolve("copy.rmw")) > 1000);
		assertEquals(ScriptRunner.EXIT_COMMAND_FAILED, run("open Delta.rmt", "save copy.mid"));
		assertTrue(err().contains(".rmt, .txt or .rmw"), err());
	}

	@Test
	void existingFilesAreNotReplacedUnlessTheScriptSaysSo() throws IOException {
		Files.write(dir.resolve("s.wav"), new byte[] { 1 });
		assertEquals(ScriptRunner.EXIT_COMMAND_FAILED, run("open Delta.rmt", "export wav s.wav", "echo not reached"));
		assertTrue(err().contains("exists already"), err());
		assertFalse(out().contains("not reached"));
		assertEquals(1, Files.size(dir.resolve("s.wav")));

		assertEquals(ScriptRunner.EXIT_OK, run("set overwrite yes", "open Delta.rmt", "export wav s.wav"), err());
		assertTrue(Files.size(dir.resolve("s.wav")) > 44);
	}

	@Test
	void failuresStopTheScriptWithTheLineNumberAndTheExitCode() throws IOException {
		assertEquals(ScriptRunner.EXIT_COMMAND_FAILED, run("echo first", "open missing.rmt", "echo not reached"));
		assertTrue(out().contains("first") && !out().contains("not reached"), out());
		assertTrue(err().contains("Open error: Can't open this file"), err()); // the model's message box, on the console
		assertTrue(err().contains("line 2: Cannot open"), err());

		err.reset();
		assertEquals(ScriptRunner.EXIT_COMMAND_FAILED, run("frobnicate"));
		assertTrue(err().contains("line 1: Unknown command 'frobnicate'"), err());

		err.reset();
		assertEquals(ScriptRunner.EXIT_SCRIPT_INVALID, run("open \"Delta.rmt"));
		assertTrue(err().contains("line 1") && err().contains("quote"), err());

		err.reset();
		ScriptRunner runner = new ScriptRunner(new RmtSession(), new PrintStream(out), new PrintStream(err));
		assertEquals(ScriptRunner.EXIT_SCRIPT_INVALID, runner.run(dir.resolve("nowhere.rmtscript")));
	}

	@Test
	void setNtscAndDriverChangeTheSessionAsTheOptionsDialogWould() throws IOException {
		RmtSession session = new RmtSession();
		Path script = dir.resolve("set.rmtscript");
		Files.write(script, List.of("open Delta.rmt", "set ntsc yes", "set driver unpatched-with-tuning", "export wav ntsc.wav"), StandardCharsets.UTF_8);
		assertEquals(ScriptRunner.EXIT_OK, new ScriptRunner(session, new PrintStream(out), new PrintStream(err)).run(script), err());
		assertTrue(session.song.isNTSC());
		assertTrue(session.atari.getCpu().isNTSC(), "ReInitSound() followed");
		assertEquals(org.atari.raster.rmt.model.TrackerDriverVersion.UNPATCHED_WITH_TUNING, session.options.trackerDriverVersion);
		long ntscSize = Files.size(dir.resolve("ntsc.wav"));

		assertEquals(ScriptRunner.EXIT_OK, run("open Delta.rmt", "export wav pal.wav"), err());
		assertTrue(ntscSize < Files.size(dir.resolve("pal.wav")), "60 frames/s play the same frames in less time");

		err.reset();
		assertEquals(ScriptRunner.EXIT_COMMAND_FAILED, run("set driver patch99"));
		assertTrue(err().contains("'driver' must be one of unpatched, unpatched-with-tuning, patch3"), err());
		err.reset();
		assertEquals(ScriptRunner.EXIT_COMMAND_FAILED, run("set volume 11"));
		assertTrue(err().contains("Unknown setting 'volume'"), err());
	}

	@Test
	void theWindowsMessageBoxesStayInPlaceInInteractiveMode() throws IOException {
		RmtSession session = new RmtSession();
		List<String> boxes = new java.util.ArrayList<>();
		org.atari.raster.rmt.model.Messages.Handler windowBoxes = new org.atari.raster.rmt.model.Messages.Handler() {
			@Override
			public void showError(String title, String message) {
				boxes.add(title + ": " + message);
			}

			@Override
			public void showWarning(String title, String message) {
				boxes.add(title + ": " + message);
			}

			@Override
			public void showInformation(String title, String message) {
				boxes.add(title + ": " + message);
			}

			@Override
			public org.atari.raster.rmt.model.MessageAnswer askQuestion(String title, String message, org.atari.raster.rmt.model.MessageButtons buttons) {
				return org.atari.raster.rmt.model.MessageAnswer.CANCEL;
			}
		};
		session.messages.setHandler(windowBoxes);
		Path script = dir.resolve("ui.rmtscript");
		Files.write(script, List.of("open missing.rmt"), StandardCharsets.UTF_8);
		int code = new ScriptRunner(session, new PrintStream(out), new PrintStream(err), windowBoxes).run(script);
		assertEquals(ScriptRunner.EXIT_COMMAND_FAILED, code);
		assertEquals(1, boxes.size(), boxes.toString()); // the "Open error" box went to the window
		assertTrue(boxes.get(0).startsWith("Open error"), boxes.get(0));
		assertTrue(err().contains("line 1: Cannot open"), err()); // the command's failure (which repeats the box text as its cause)
		assertTrue(session.messages.getHandler() == windowBoxes, "the handler is restored");
	}

	@Test
	void setOutputRedirectsSavesAndExportsAndTheOverrideWins() throws IOException {
		assertEquals(ScriptRunner.EXIT_OK, run("set output sub/folder", "open Delta.rmt", "save copy.rmt", "export sapr o.sapr", "set output sub/../elsewhere", "export lzss o.lzss"), err());
		assertTrue(Files.isRegularFile(dir.resolve("sub").resolve("folder").resolve("copy.rmt")));
		assertTrue(Files.isRegularFile(dir.resolve("sub").resolve("folder").resolve("o.sapr")));
		assertTrue(Files.isRegularFile(dir.resolve("elsewhere").resolve("o.lzss"))); // stays inside the temp folder (an earlier "../elsewhere" left files behind between runs)

		RmtSession session = new RmtSession();
		Path script = dir.resolve("override.rmtscript");
		Files.write(script, List.of("set output ignored", "open Delta.rmt", "export sapr p.sapr"), StandardCharsets.UTF_8);
		ScriptRunner runner = new ScriptRunner(session, new PrintStream(out), new PrintStream(err));
		runner.setOutputFolder(dir.resolve("forced"));
		assertEquals(ScriptRunner.EXIT_OK, runner.run(script), err());
		assertTrue(Files.isRegularFile(dir.resolve("forced").resolve("p.sapr")));
		assertFalse(Files.exists(dir.resolve("ignored")));
	}

	@Test
	void dumpActionsWritesTheCommandTable() throws IOException {
		assertEquals(ScriptRunner.EXIT_OK, run("dump actions a.md"), err());
		String text = Files.readString(dir.resolve("a.md"), StandardCharsets.UTF_8);
		assertTrue(text.startsWith(ActionInfos.HEADER), text);
		assertTrue(text.contains("| Menu File<br>Tool Bar Main | New | `Ctrl+N` | Create a new module |\n"), text);
		assertTrue(text.contains("| Menu Edit<br>Tool Bar Main | Edit Info | `Shift+F4` | "), text);
		assertTrue(text.contains("| Menu Edit | Clear Undo & Redo History |  |"), "mnemonic markers stripped, && kept as &: " + text);
		assertTrue(text.contains("| Tool Bar Main | Toggle MIDI on/off |  | Toggle MIDI on/off |\n"), "a toolbar-only command: " + text);
		assertFalse(text.contains("ERROR"), text);

		assertEquals(ScriptRunner.EXIT_OK, run("dump notekeys k.md"), err());
		String keys = Files.readString(dir.resolve("k.md"), StandardCharsets.UTF_8);
		assertTrue(keys.startsWith("### QWERTY\n\n```\n 1    2    3    4    5    6    7    8    9    0    -    =\n     C#2  D#2       F#2  G#2  A#2       C#3  D#3       F#3\n   Q    W    E"), keys);
		assertTrue(keys.contains("       Z    X    C    V    B    N    M    ,    .    /\n      C-1  D-1  E-1  F-1  G-1  A-1  B-1  C-2  D-2  E-2\n```\n\n| Note | Keys |\n|---|---|\n| C-1 | `Z` |\n"), keys);
		assertTrue(keys.contains("| C-2 | `Q`, `,` |\n") && keys.contains("\n### AZERTY\n\n```\n &    é    \"    '    (    -    è    _    ç    à    )    =\n"), keys);

		assertEquals(ScriptRunner.EXIT_COMMAND_FAILED, run("dump songs b.md"));
		assertTrue(err().contains("Unknown dump 'songs'"), err());
		assertEquals(ScriptRunner.EXIT_COMMAND_FAILED, run("dump actions a.md"), "overwrite refused");
	}

	@Test
	void quitEndsTheScriptSuccessfully() throws IOException {
		assertEquals(ScriptRunner.EXIT_OK, run("open Delta.rmt", "quit", "export mp3 nonsense"));
	}
}
