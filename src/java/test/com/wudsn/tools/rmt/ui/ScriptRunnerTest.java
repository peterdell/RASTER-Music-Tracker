package com.wudsn.tools.rmt.ui;

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

import com.wudsn.tools.rmt.model.AsmFileExporter;
import com.wudsn.tools.rmt.model.AssemblerFormat;
import com.wudsn.tools.rmt.script.ScriptRunner;

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
	void quitEndsTheScriptSuccessfully() throws IOException {
		assertEquals(ScriptRunner.EXIT_OK, run("open Delta.rmt", "quit", "export mp3 nonsense"));
	}
}
