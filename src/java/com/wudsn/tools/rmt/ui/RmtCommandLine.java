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
 * <p>{@code /SCRIPT:<file>} runs the script headless
 * ({@link com.wudsn.tools.rmt.script.ScriptRunner}). The C++ {@code /TEST}
 * switch and the developer routines behind the C++ {@code /TEST}/
 * {@code /SCRIPT} are not ported (user decision 2026-09-27: proper scripts
 * instead); {@link Result#rejection()} carries the C++-style error text for
 * {@code /TEST}, and the application shows it and exits.
 */
final class RmtCommandLine {

	/** {@code file} may be null; {@code rejection} is null when the command line is acceptable. */
	record Result(Path file, RmtCommandLineInfo info, String rejection) {
		/** The {@code /SCRIPT:<file>} path, or {@code null}. */
		Path scriptFile() {
			return info.isScriptFileSpecified() ? Path.of(info.getScriptFilePath()) : null;
		}
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
		if (info.isTestFileSpecified()) {
			rejection = "The command line switch /TEST is not supported by the Java port; use /SCRIPT:<file> instead.";
		} else if (info.isScriptFileSpecified() && info.getScriptFilePath().isEmpty()) {
			rejection = "The command line switch /SCRIPT needs a script file: /SCRIPT:<file>.";
		}
		return new Result(file, info, rejection);
	}
}
