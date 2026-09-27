package com.wudsn.tools.rmt.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class RmtCommandLineTest {

	@Test
	void theFirstPlainParameterIsTheFileToOpen() {
		RmtCommandLine.Result r = RmtCommandLine.parse(new String[] { "songs\\Delta.rmt", "ignored.rmt" });
		assertEquals(Path.of("songs", "Delta.rmt"), r.file());
		assertNull(r.rejection());
	}

	@Test
	void noParametersMeansNoFile() {
		RmtCommandLine.Result r = RmtCommandLine.parse(new String[0]);
		assertNull(r.file());
		assertNull(r.rejection());
	}

	@Test
	void unknownSwitchesAreIgnoredAsMfcIgnoresThem() {
		RmtCommandLine.Result r = RmtCommandLine.parse(new String[] { "/nologo", "-x:1", "song.rmt" });
		assertEquals(Path.of("song.rmt"), r.file());
		assertNull(r.rejection());
	}

	@Test
	void theScriptSwitchNamesTheScriptToRun() {
		RmtCommandLine.Result script = RmtCommandLine.parse(new String[] { "/SCRIPT:run.txt" });
		assertEquals(Path.of("run.txt"), script.scriptFile());
		assertNull(script.rejection());
		assertNull(RmtCommandLine.parse(new String[] { "song.rmt" }).scriptFile());

		RmtCommandLine.Result noFile = RmtCommandLine.parse(new String[] { "/SCRIPT" });
		assertNotNull(noFile.rejection());
		assertTrue(noFile.rejection().contains("/SCRIPT:<file>"));
	}

	@Test
	void theTestSwitchIsRejectedWithAnExplanation() {
		RmtCommandLine.Result test = RmtCommandLine.parse(new String[] { "-test:x.sapr" }); // case-insensitive, '-' prefix as MFC
		assertTrue(test.info().isTestFileSpecified());
		assertTrue(test.rejection().contains("/TEST"));
		assertTrue(test.rejection().contains("/SCRIPT"));
	}
}
