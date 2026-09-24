package com.wudsn.tools.rmt.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Mirrors src/cpp/test/RmtCommandLineInfoTests.cpp. */
class RmtCommandLineInfoTest {

	@Test
	void noParamsLeavesNeitherFileSpecified() {
		RmtCommandLineInfo info = new RmtCommandLineInfo();
		assertFalse(info.isScriptFileSpecified());
		assertFalse(info.isTestFileSpecified());
	}

	@Test
	void scriptSwitchSetsScriptFilePath() {
		RmtCommandLineInfo info = new RmtCommandLineInfo();
		info.parseParam("SCRIPT:test-script.txt", true, true);

		assertTrue(info.isScriptFileSpecified());
		assertEquals("test-script.txt", info.getScriptFilePath());
		assertFalse(info.isTestFileSpecified());
	}

	@Test
	void testSwitchSetsTestFilePath() {
		RmtCommandLineInfo info = new RmtCommandLineInfo();
		info.parseParam("TEST:song.rmt", true, true);

		assertTrue(info.isTestFileSpecified());
		assertEquals("song.rmt", info.getTestFilePath());
		assertFalse(info.isScriptFileSpecified());
	}

	@Test
	void switchNameIsCaseInsensitive() {
		RmtCommandLineInfo info = new RmtCommandLineInfo();
		info.parseParam("script:lower.txt", true, true);

		assertTrue(info.isScriptFileSpecified());
		assertEquals("lower.txt", info.getScriptFilePath());
	}

	@Test
	void switchWithoutColonHasEmptyValue() {
		RmtCommandLineInfo info = new RmtCommandLineInfo();
		info.parseParam("SCRIPT", true, true);

		assertTrue(info.isScriptFileSpecified());
		assertEquals("", info.getScriptFilePath());
	}

	@Test
	void unrecognizedFlagLeavesBothFilesUnspecified() {
		RmtCommandLineInfo info = new RmtCommandLineInfo();
		info.parseParam("SOMETHINGELSE:value", true, true);

		assertFalse(info.isScriptFileSpecified());
		assertFalse(info.isTestFileSpecified());
	}

	@Test
	void plainFilenameParamDoesNotSetCustomFlags() {
		RmtCommandLineInfo info = new RmtCommandLineInfo();
		info.parseParam("song.rmt", false, true);

		assertFalse(info.isScriptFileSpecified());
		assertFalse(info.isTestFileSpecified());
	}
}
