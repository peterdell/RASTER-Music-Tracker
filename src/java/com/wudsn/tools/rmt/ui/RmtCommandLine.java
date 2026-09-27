package com.wudsn.tools.rmt.ui;

import java.nio.file.Path;

import com.wudsn.tools.rmt.model.RmtCommandLineInfo;

/**
 * The command line as MFC's {@code CCommandLineInfo} + the ported
 * {@code CRmtCommandLineInfo} see it: a parameter starting with {@code /}
 * or {@code -} is a switch ({@code /NAME} or {@code /NAME:value}), the
 * first other parameter is the file to open ({@code m_strFileName},
 * {@code FileOpen}); further plain parameters are ignored, as MFC ignores
 * them.
 *
 * <p>The C++ {@code /TEST} switch and the developer routines behind
 * {@code /TEST}/{@code /SCRIPT} are not ported (user decision 2026-09-27:
 * proper scripts instead); {@code /SCRIPT} is reserved for the scripting
 * feature. {@link Result#rejection()} carries the C++-style error text for
 * either switch, and the application shows it and exits.
 */
final class RmtCommandLine {

	/** {@code file} may be null; {@code rejection} is null when the command line is acceptable. */
	record Result(Path file, RmtCommandLineInfo info, String rejection) {
	}

	static final String INVALID_PARAMETER_TITLE = "Invalid Command Line Parameter";

	private RmtCommandLine() {
	}

	static Result parse(String[] args) {
		RmtCommandLineInfo info = new RmtCommandLineInfo();
		Path file = null;
		for (int i = 0; i < args.length; i++) {
			String arg = args[i];
			boolean last = i == args.length - 1;
			if (arg.length() > 1 && (arg.charAt(0) == '/' || arg.charAt(0) == '-')) {
				info.parseParam(arg.substring(1), true, last);
			} else if (file == null && !arg.isEmpty()) {
				file = Path.of(arg);
			}
		}
		String rejection = null;
		if (info.isScriptFileSpecified()) {
			rejection = "The command line switch /SCRIPT is reserved for the scripting feature, which is not available in this version.";
		} else if (info.isTestFileSpecified()) {
			rejection = "The command line switch /TEST is not supported by the Java port; scripting will take its place.";
		}
		return new Result(file, info, rejection);
	}
}
