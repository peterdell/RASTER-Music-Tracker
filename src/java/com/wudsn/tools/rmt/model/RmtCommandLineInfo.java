package com.wudsn.tools.rmt.model;

/**
 * Ported from {@code CRmtCommandLineInfo} (src/cpp/RmtCommandLineInfo.h/.cpp)
 * - the {@code SCRIPT:}/{@code TEST:} switch parsing, which is all
 * {@code RmtCommandLineInfoTests.cpp} exercises. C++'s fallback to
 * {@code CCommandLineInfo::ParseParam()} (MFC's own default handling of
 * plain filenames and unrecognized flags) has no Java equivalent and no
 * observable effect on any existing test, so it's simply omitted here
 * rather than reintroducing an MFC dependency for untested behavior.
 *
 * <p>C++ recomputes {@code GetSwitchName(switchString)} a second time right
 * before checking the {@code TEST} switch, even though it's already been
 * computed identically just above for the {@code SCRIPT} check - a harmless
 * redundant call, not a behavior-affecting quirk, so {@link #parseParam}
 * simply computes it once.
 */
public final class RmtCommandLineInfo {

	private boolean scriptFileSpecified;
	private String scriptFilePath = "";

	private boolean testFileSpecified;
	private String testFilePath = "";

	public boolean isScriptFileSpecified() {
		return scriptFileSpecified;
	}

	public String getScriptFilePath() {
		return scriptFilePath;
	}

	public boolean isTestFileSpecified() {
		return testFileSpecified;
	}

	public String getTestFilePath() {
		return testFilePath;
	}

	// bLast is part of C++'s ParseParam signature but unused in its body -
	// kept here only for parity with the original method's shape.
	public void parseParam(String param, boolean flag, boolean bLast) {
		if (flag) {
			String switchName = getSwitchName(param).toUpperCase();
			if (switchName.equals("SCRIPT")) {
				scriptFileSpecified = true;
				scriptFilePath = getSwitchValue(param);
			}
			if (switchName.equals("TEST")) {
				testFileSpecified = true;
				testFilePath = getSwitchValue(param);
			}
		}
	}

	private static String getSwitchName(String switchString) {
		int pos = switchString.indexOf(':');
		if (pos != -1) {
			return switchString.substring(0, pos);
		}
		return switchString;
	}

	private static String getSwitchValue(String switchString) {
		int pos = switchString.indexOf(':');
		if (pos != -1) {
			return switchString.substring(pos + 1);
		}
		return "";
	}
}
