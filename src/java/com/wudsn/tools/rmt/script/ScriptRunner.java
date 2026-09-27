package com.wudsn.tools.rmt.script;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.wudsn.tools.rmt.model.AsmFileExporter;
import com.wudsn.tools.rmt.model.AssemblerFormat;
import com.wudsn.tools.rmt.model.MessageAnswer;
import com.wudsn.tools.rmt.model.MessageButtons;
import com.wudsn.tools.rmt.model.Messages;
import com.wudsn.tools.rmt.model.SapFile;
import com.wudsn.tools.rmt.model.TrackerDriverVersion;
import com.wudsn.tools.rmt.ui.ExportSettings;
import com.wudsn.tools.rmt.ui.RmtSession;
import com.wudsn.tools.rmt.ui.SongFiles;

/**
 * Runs a script (plans/JAVA_SCRIPTING_PLAN.md) against an {@link RmtSession}
 * without a window: the file commands go through {@link SongFiles} exactly
 * as the menu commands do, with a {@link SongFiles.Host} that answers the
 * file chooser and the export dialogs from the command's options (the
 * dialogs' own defaults when an option is omitted) and a
 * {@link Messages.Handler} that prints the message boxes to the console.
 *
 * <p>Commands: {@code open <file>}, {@code save <file>},
 * {@code export <format> <file> [name=value ...]}, {@code set overwrite
 * yes|no}, {@code set output <folder>}, {@code set ntsc yes|no},
 * {@code set driver <version>}, {@code echo <text>}, {@code quit}. Paths
 * are relative to the script's folder; output files to the output folder
 * once one is set. Exit codes: 0 = all commands succeeded, 1 = a command
 * failed (the script stops there), 2 = the script could not be read or
 * parsed.
 *
 * <p>Two message policies: headless (the command line), where the message
 * boxes are printed to the console; and interactive (Tools > Run script...
 * in the window), where they stay the window's boxes - either way an error
 * or warning box fails the command. The session's handler is restored
 * after the run.
 */
public final class ScriptRunner {

	public static final int EXIT_OK = 0;
	public static final int EXIT_COMMAND_FAILED = 1;
	public static final int EXIT_SCRIPT_INVALID = 2;

	/** The export formats: the Export dialog's filter list in its order (the 1-based filter index is the position + 1). */
	static final String[] EXPORT_FORMATS = { "stripped-rmt", "asm", "sapr", "lzss", "sap", "xex", "rmtplayer-asm", "wav" };

	private static final Map<String, Set<String>> EXPORT_OPTIONS = Map.of( //
			"stripped-rmt", Set.of("address", "sfx", "gvf", "nos", "asmformat"), //
			"asm", Set.of("type", "notes", "durations", "prefix"), //
			"sapr", Set.of("author", "name", "date", "subsongs"), //
			"lzss", Set.of(), //
			"sap", Set.of("author", "name", "date", "subsongs"), //
			"xex", Set.of("text", "rasterbar", "shuffle", "region-auto", "color"), //
			"rmtplayer-asm", Set.of("startlabel", "relocate", "instruments-label", "tracks-label", "songlines-label", "asmformat", "sfx", "gvf", "nos"), //
			"wav", Set.of());

	private final RmtSession session;
	private final PrintStream out;
	private final PrintStream err;
	private final ScriptHost host = new ScriptHost();
	private final SongFiles files;

	private Path baseFolder = Path.of(".");
	/** {@code set output <folder>}: where {@code save}/{@code export} write; null = the script's folder. */
	private Path outputFolder;
	/** {@link #setOutputFolder}: wins over the script's own {@code set output} (the cross-program comparison runs one script into two folders). */
	private Path outputOverride;
	private boolean overwrite;
	/** The current command, for the host's option lookups and error texts. */
	private ScriptCommand current;
	/** Set by the message handler when a command raised an error or warning box. */
	private final List<String> problems = new ArrayList<>();

	private final Messages.Handler messageBoxes;

	/** Headless: the message boxes go to the console. */
	public ScriptRunner(RmtSession session, PrintStream out, PrintStream err) {
		this(session, out, err, null);
	}

	/** {@code messageBoxes} non-null: the boxes stay boxes (the window's handler), only the command results go to {@code out}/{@code err}. */
	public ScriptRunner(RmtSession session, PrintStream out, PrintStream err, Messages.Handler messageBoxes) {
		this.session = session;
		this.out = out;
		this.err = err;
		this.messageBoxes = messageBoxes;
		this.files = new SongFiles(session, host);
	}

	/** The output folder for every {@code save}/{@code export}, overriding the script's {@code set output} - the {@code RMT_SCRIPT_OUTPUT} environment variable on the command line. */
	public void setOutputFolder(Path outputFolder) {
		this.outputOverride = outputFolder;
	}

	/** Reads, parses and runs the script file; returns the exit code. */
	public int run(Path scriptFile) {
		List<String> lines;
		try {
			lines = Files.readAllLines(scriptFile, StandardCharsets.UTF_8);
		} catch (IOException e) {
			err.println("The script file '" + scriptFile + "' cannot be read: " + e.getMessage());
			return EXIT_SCRIPT_INVALID;
		}
		List<ScriptCommand> commands;
		try {
			commands = ScriptParser.parse(lines);
		} catch (ScriptException e) {
			err.println(scriptFile.getFileName() + ": " + e.getLocatedMessage());
			return EXIT_SCRIPT_INVALID;
		}
		Path folder = scriptFile.toAbsolutePath().getParent();
		return run(commands, folder != null ? folder : Path.of("."));
	}

	/** Runs already parsed commands, resolving relative paths against {@code baseFolder}; returns the exit code. */
	public int run(List<ScriptCommand> commands, Path baseFolder) {
		Messages.Handler previous = session.messages.getHandler();
		session.messages.setHandler(new RecordingMessages(messageBoxes != null ? messageBoxes : new ConsoleMessages()));
		try {
			return runCommands(commands, baseFolder);
		} finally {
			session.messages.setHandler(previous);
		}
	}

	private int runCommands(List<ScriptCommand> commands, Path baseFolder) {
		this.baseFolder = baseFolder;
		for (ScriptCommand command : commands) {
			current = command;
			problems.clear();
			try {
				if (!execute(command)) {
					return EXIT_OK; // quit
				}
			} catch (ScriptException e) {
				err.println(e.getLocatedMessage());
				return EXIT_COMMAND_FAILED;
			} catch (OptionError e) {
				err.println(e.getMessage());
				return EXIT_COMMAND_FAILED;
			} catch (IOException | RuntimeException e) {
				err.println("line " + command.line() + ": " + command.name() + " failed: " + e);
				return EXIT_COMMAND_FAILED;
			} finally {
				current = null;
			}
		}
		return EXIT_OK;
	}

	/** Returns false for {@code quit}. */
	private boolean execute(ScriptCommand command) throws ScriptException, IOException {
		switch (command.name()) {
		case "open" -> open(command);
		case "save" -> save(command);
		case "export" -> export(command);
		case "set" -> set(command);
		case "echo" -> out.println(String.join(" ", command.arguments()));
		case "quit" -> {
			return false;
		}
		default -> throw new ScriptException(command.line(), "Unknown command '" + command.name() + "'.");
		}
		return true;
	}

	private void open(ScriptCommand command) throws ScriptException {
		requireArguments(command, 1, "open <file>");
		requireNoOptions(command);
		Path file = resolve(command.argument(0));
		if (!files.fileOpen(file, false) || !problems.isEmpty()) {
			throw new ScriptException(command.line(), "Cannot open '" + file + "'." + problemText());
		}
		out.println("Opened " + file);
	}

	private void save(ScriptCommand command) throws ScriptException {
		requireArguments(command, 1, "save <file>");
		requireNoOptions(command);
		Path file = resolveOutput(command.argument(0));
		int filterIndex = filterIndexOfExtension(file, SongFiles.SONG_FILTERS);
		if (filterIndex == 0) {
			throw new ScriptException(command.line(), "The file name must end in .rmt, .txt or .rmw.");
		}
		checkOverwrite(command, file);
		host.answer(file, filterIndex);
		files.fileSaveAs();
		if (!problems.isEmpty() || !Files.isRegularFile(file)) {
			throw new ScriptException(command.line(), "Saving '" + file + "' failed." + problemText());
		}
		out.println("Saved " + file);
	}

	private void export(ScriptCommand command) throws ScriptException {
		requireArguments(command, 2, "export <format> <file> [name=value ...]");
		String format = command.argument(0).toLowerCase(Locale.ROOT);
		int filterIndex = List.of(EXPORT_FORMATS).indexOf(format) + 1;
		if (filterIndex == 0) {
			throw new ScriptException(command.line(), "Unknown export format '" + command.argument(0) + "'; one of " + String.join(", ", EXPORT_FORMATS) + ".");
		}
		Set<String> allowed = EXPORT_OPTIONS.get(format);
		for (String option : command.options().keySet()) {
			if (!allowed.contains(option)) {
				throw new ScriptException(command.line(), "Unknown option '" + option + "' for the format '" + format + "'" + (allowed.isEmpty() ? " (it has none)." : "; one of " + String.join(", ", allowed.stream().sorted().toList()) + "."));
			}
		}
		Path file = SongFiles.ensureFileExtension(resolveOutput(command.argument(1)), SongFiles.EXPORT_FILTERS, filterIndex);
		checkOverwrite(command, file);
		host.answer(file, filterIndex);
		files.fileExportAs();
		if (!problems.isEmpty() || !Files.isRegularFile(file)) {
			throw new ScriptException(command.line(), "Exporting '" + file + "' as " + format + " failed." + problemText());
		}
		out.println("Exported " + file);
	}

	private void set(ScriptCommand command) throws ScriptException {
		requireArguments(command, 2, "set <name> <value>");
		requireNoOptions(command);
		String name = command.argument(0).toLowerCase(Locale.ROOT);
		String value = command.argument(1);
		switch (name) {
		case "overwrite" -> overwrite = parseBoolean(command, "overwrite", value);
		case "output" -> outputFolder = resolve(value);
		case "ntsc" -> { // the Options dialog's NTSC box (OnToolsOptions -> SetNTSC)
			boolean ntsc = parseBoolean(command, "ntsc", value);
			if (session.song.isNTSC() != ntsc) {
				session.setNTSC(ntsc);
			}
		}
		case "driver" -> { // the Options dialog's tracker driver version
			TrackerDriverVersion version = parseDriverVersion(command, value);
			if (session.options.trackerDriverVersion != version) {
				session.setTrackerDriverVersion(version);
			}
		}
		default -> throw new ScriptException(command.line(), "Unknown setting '" + command.argument(0) + "'; one of overwrite, output, ntsc, driver.");
		}
	}

	/** An output file: relative to the output folder (created on demand), else the script's folder. */
	private Path resolveOutput(String path) throws ScriptException {
		Path base = outputOverride != null ? outputOverride : outputFolder != null ? outputFolder : baseFolder;
		Path file = base.resolve(path).normalize();
		try {
			if (file.getParent() != null) {
				Files.createDirectories(file.getParent());
			}
		} catch (IOException e) {
			throw new ScriptException(current != null ? current.line() : 0, "Cannot create the folder '" + file.getParent() + "': " + e.getMessage());
		}
		return file;
	}

	/** {@code unpatched}, {@code unpatched-with-tuning}, {@code patch3}, {@code patch6}, {@code patch8}, {@code patch16}, {@code patch-prince-of-persia} (the enum names, case-insensitive, {@code -} or {@code _}). */
	private static TrackerDriverVersion parseDriverVersion(ScriptCommand command, String value) throws ScriptException {
		String wanted = value.trim().toUpperCase(Locale.ROOT).replace('-', '_');
		List<String> names = new ArrayList<>();
		for (TrackerDriverVersion v : TrackerDriverVersion.values()) {
			if (v == TrackerDriverVersion.NONE) {
				continue;
			}
			if (v.name().equals(wanted)) {
				return v;
			}
			names.add(v.name().toLowerCase(Locale.ROOT).replace('_', '-'));
		}
		throw new ScriptException(command.line(), "'driver' must be one of " + String.join(", ", names) + ", not '" + value + "'.");
	}

	// ---- helpers ----

	private Path resolve(String path) {
		return baseFolder.resolve(path).normalize();
	}

	private void checkOverwrite(ScriptCommand command, Path file) throws ScriptException {
		if (!overwrite && Files.exists(file)) {
			throw new ScriptException(command.line(), "'" + file + "' exists already (use 'set overwrite yes' to replace files).");
		}
	}

	private static void requireArguments(ScriptCommand command, int count, String usage) throws ScriptException {
		if (command.arguments().size() < count) {
			throw new ScriptException(command.line(), "Usage: " + usage);
		}
		if (command.arguments().size() > count) {
			throw new ScriptException(command.line(), "Unexpected argument '" + command.arguments().get(count) + "' (a file name with blanks needs quotes). Usage: " + usage);
		}
	}

	private static void requireNoOptions(ScriptCommand command) throws ScriptException {
		if (!command.options().isEmpty()) {
			throw new ScriptException(command.line(), "The command '" + command.name() + "' takes no options.");
		}
	}

	private String problemText() {
		return problems.isEmpty() ? "" : " " + String.join(" ", problems);
	}

	/** The 1-based filter whose extension the file name ends in, 0 for none. */
	private static int filterIndexOfExtension(Path file, List<SongFiles.FileFilter> filters) {
		String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
		for (int i = 0; i < filters.size(); i++) {
			for (String extension : filters.get(i).extensions()) {
				if (name.endsWith(extension)) {
					return i + 1;
				}
			}
		}
		return 0;
	}

	private static boolean parseBoolean(ScriptCommand command, String name, String value) throws ScriptException {
		switch (value.toLowerCase(Locale.ROOT)) {
		case "yes", "true", "on", "1":
			return true;
		case "no", "false", "off", "0":
			return false;
		default:
			throw new ScriptException(command.line(), "'" + name + "' must be yes or no, not '" + value + "'.");
		}
	}

	private static int parseAddress(ScriptCommand command, String name, String value) throws ScriptException {
		try {
			String v = value.trim();
			int result;
			if (v.startsWith("$")) {
				result = Integer.parseInt(v.substring(1), 16);
			} else if (v.toLowerCase(Locale.ROOT).startsWith("0x")) {
				result = Integer.parseInt(v.substring(2), 16);
			} else {
				result = Integer.parseInt(v);
			}
			if (result < 0 || result > 0xFFFF) {
				throw new NumberFormatException();
			}
			return result;
		} catch (NumberFormatException e) {
			throw new ScriptException(command.line(), "'" + name + "' must be an address like $4000, not '" + value + "'.");
		}
	}

	private static int parseInt(ScriptCommand command, String name, String value, int min, int max) throws ScriptException {
		try {
			int result = Integer.parseInt(value.trim());
			if (result < min || result > max) {
				throw new NumberFormatException();
			}
			return result;
		} catch (NumberFormatException e) {
			throw new ScriptException(command.line(), "'" + name + "' must be a number from " + min + " to " + max + ", not '" + value + "'.");
		}
	}

	private static int parseChoice(ScriptCommand command, String name, String value, String... choices) throws ScriptException {
		for (int i = 0; i < choices.length; i++) {
			if (choices[i].equalsIgnoreCase(value.trim())) {
				return i + 1;
			}
		}
		throw new ScriptException(command.line(), "'" + name + "' must be one of " + String.join(", ", choices) + ", not '" + value + "'.");
	}

	private static AssemblerFormat parseAssemblerFormat(ScriptCommand command, String value) throws ScriptException {
		return parseChoice(command, "asmformat", value, "xasm", "atasm") == 1 ? AssemblerFormat.XASM : AssemblerFormat.ATASM;
	}

	/** An option value as a text with {@code \n} (the XEX screen text has up to 5 lines). */
	private static String unescapeLines(String value) {
		return value.replace("\\n", "\n");
	}

	/** The host's option errors are checked exceptions in the command flow; the host interface can't throw them, so they travel wrapped and are unwrapped in {@link #run(List, Path)}. */
	private static final class OptionError extends RuntimeException {
		private static final long serialVersionUID = 1L;

		OptionError(ScriptException cause) {
			super(cause.getLocatedMessage(), cause);
		}
	}

	private interface OptionParser<T> {
		T parse() throws ScriptException;
	}

	private static <T> T option(OptionParser<T> parser) {
		try {
			return parser.parse();
		} catch (ScriptException e) {
			throw new OptionError(e);
		}
	}

	/** Notes every error and warning box (they fail the current command) and forwards all boxes to the real handler. */
	private final class RecordingMessages implements Messages.Handler {
		private final Messages.Handler delegate;

		RecordingMessages(Messages.Handler delegate) {
			this.delegate = delegate;
		}

		@Override
		public void showError(String title, String message) {
			problems.add(oneLine(title, message));
			delegate.showError(title, message);
		}

		@Override
		public void showWarning(String title, String message) {
			problems.add(oneLine(title, message));
			delegate.showWarning(title, message);
		}

		@Override
		public void showInformation(String title, String message) {
			delegate.showInformation(title, message);
		}

		@Override
		public MessageAnswer askQuestion(String title, String message, MessageButtons buttons) {
			return delegate.askQuestion(title, message, buttons);
		}
	}

	private static String oneLine(String title, String message) {
		String text = message.replace("\r", "").replace("\n", " ").trim();
		return (title != null && !title.isEmpty() ? title + ": " : "") + text;
	}

	/** The console as the message boxes: errors and warnings to stderr, information to stdout, questions declined. */
	private final class ConsoleMessages implements Messages.Handler {
		@Override
		public void showError(String title, String message) {
			err.println(oneLine(title, message));
		}

		@Override
		public void showWarning(String title, String message) {
			err.println(oneLine(title, message));
		}

		@Override
		public void showInformation(String title, String message) {
			out.println(oneLine(title, message));
		}

		@Override
		public MessageAnswer askQuestion(String title, String message, MessageButtons buttons) {
			err.println(oneLine(title, message) + " (a script answers No)");
			return buttons == MessageButtons.OK_CANCEL ? MessageAnswer.CANCEL : MessageAnswer.NO;
		}
	}

	/** The file chooser and the export dialogs, answered from the current command. */
	private final class ScriptHost implements SongFiles.Host {
		private SongFiles.FileChoice nextChoice;

		void answer(Path file, int filterIndex) {
			nextChoice = new SongFiles.FileChoice(file, filterIndex);
		}

		private ScriptCommand command() {
			return current;
		}

		@Override
		public SongFiles.FileChoice chooseOpenFile(String title, List<SongFiles.FileFilter> filters, String initialDir, int initialFilterIndex) {
			throw new IllegalStateException("A script names its files; the open dialog is never shown.");
		}

		@Override
		public SongFiles.FileChoice chooseSaveFile(String title, List<SongFiles.FileFilter> filters, String initialDir, int initialFilterIndex, String suggestedFileName) {
			SongFiles.FileChoice choice = nextChoice;
			nextChoice = null;
			return choice; // the overwrite question was settled by checkOverwrite()
		}

		@Override
		public SongFiles.FileNewChoice showFileNew() {
			throw new IllegalStateException("'new' is not a script command.");
		}

		@Override
		public int showTracksLoad(int trackFrom, int trackNum) {
			return -1;
		}

		@Override
		public void songChanged() {
			// no window title to update
		}

		@Override
		public void songImported(String fileName) {
			// no window title to update
		}

		@Override
		public SongFiles.ImportModChoice showImportMod(String info, String radio1, String radio2) {
			return null;
		}

		@Override
		public SongFiles.ImportTmcChoice showImportTmc(String info) {
			return null;
		}

		@Override
		public boolean showImportFinished(boolean mod, String info) {
			return false;
		}

		@Override
		public SongFiles.StrippedRmtChoice showExportStrippedRmt(SongFiles.ModuleDescription stripped, SongFiles.ModuleDescription withSfx, String filename) {
			ScriptCommand c = command();
			ExportSettings es = session.exportSettings;
			return option(() -> new SongFiles.StrippedRmtChoice( //
					c.options().containsKey("address") ? parseAddress(c, "address", c.option("address", null)) : es.rmtStrippedAddress, //
					c.options().containsKey("asmformat") ? parseAssemblerFormat(c, c.option("asmformat", null)) : es.asmFormat, //
					c.options().containsKey("sfx") ? parseBoolean(c, "sfx", c.option("sfx", null)) : es.rmtStrippedSfx, //
					c.options().containsKey("gvf") ? parseBoolean(c, "gvf", c.option("gvf", null)) : es.rmtStrippedGlobalVolumeFade, //
					c.options().containsKey("nos") ? parseBoolean(c, "nos", c.option("nos", null)) : es.rmtStrippedNoStartingSongLine));
		}

		@Override
		public SongFiles.AsmChoice showExportAsm() {
			ScriptCommand c = command();
			ExportSettings es = session.exportSettings;
			return option(() -> new SongFiles.AsmChoice( //
					parseChoice(c, "type", c.option("type", "tracks"), "tracks", "song"), //
					parseChoice(c, "notes", c.option("notes", "index"), "index", "freq"), //
					parseChoice(c, "durations", c.option("durations", "notes"), "notes", "note-duration", "duration-note"), //
					c.option("prefix", es.prefixForAllAsmLabels)));
		}

		@Override
		public AsmFileExporter.RelocatableAsmExportParams showExportRelocatableAsm(SongFiles.ModuleDescription stripped, SongFiles.ModuleDescription withSfx) {
			ScriptCommand c = command();
			ExportSettings es = session.exportSettings;
			return option(() -> {
				boolean instruments = es.asmWantRelocatableInstruments;
				boolean tracks = es.asmWantRelocatableTracks;
				boolean songLines = es.asmWantRelocatableSongLines;
				if (c.options().containsKey("relocate")) {
					instruments = tracks = songLines = false;
					for (String part : c.option("relocate", "").split(",")) {
						switch (part.trim().toLowerCase(Locale.ROOT)) {
						case "instruments" -> instruments = true;
						case "tracks" -> tracks = true;
						case "songlines" -> songLines = true;
						case "", "none" -> {
							// nothing relocated
						}
						default -> throw new ScriptException(c.line(), "'relocate' lists instruments, tracks and/or songlines, not '" + part + "'.");
						}
					}
				}
				return new AsmFileExporter.RelocatableAsmExportParams( //
						c.option("startlabel", es.asmLabelForStartOfSong.isEmpty() ? "RMT_SONG_DATA" : es.asmLabelForStartOfSong), //
						instruments, tracks, songLines, //
						c.option("instruments-label", es.asmInstrumentsLabel.isEmpty() ? "RMT_INSTRUMENT_DATA" : es.asmInstrumentsLabel), //
						c.option("tracks-label", es.asmTracksLabel.isEmpty() ? "RMT_SONG_TRACKS" : es.asmTracksLabel), //
						c.option("songlines-label", es.asmSongLinesLabel.isEmpty() ? "RMT_SONG_LINES" : es.asmSongLinesLabel), //
						c.options().containsKey("asmformat") ? parseAssemblerFormat(c, c.option("asmformat", null)) : es.asmFormat, //
						c.options().containsKey("sfx") ? parseBoolean(c, "sfx", c.option("sfx", null)) : es.rmtStrippedSfx, //
						c.options().containsKey("gvf") ? parseBoolean(c, "gvf", c.option("gvf", null)) : es.rmtStrippedGlobalVolumeFade, //
						c.options().containsKey("nos") ? parseBoolean(c, "nos", c.option("nos", null)) : es.rmtStrippedNoStartingSongLine);
			});
		}

		@Override
		public SongFiles.SapChoice showExportSap(SapFile sapFile, String subsongs) {
			ScriptCommand c = command();
			return new SongFiles.SapChoice(c.option("author", sapFile.getAuthor()), c.option("name", sapFile.getName()), c.option("date", sapFile.getDate()), c.option("subsongs", subsongs));
		}

		@Override
		public SongFiles.XexChoice showExportXex(String text, String speedInfo) {
			ScriptCommand c = command();
			ExportSettings es = session.exportSettings;
			return option(() -> new SongFiles.XexChoice( //
					c.options().containsKey("text") ? unescapeLines(c.option("text", null)) : text, //
					c.options().containsKey("rasterbar") ? parseBoolean(c, "rasterbar", c.option("rasterbar", null)) : es.msxRasterbar, //
					c.options().containsKey("shuffle") ? parseBoolean(c, "shuffle", c.option("shuffle", null)) : es.msxShuffle, //
					c.options().containsKey("region-auto") ? parseBoolean(c, "region-auto", c.option("region-auto", null)) : es.msxRegionAuto, //
					c.options().containsKey("color") ? parseInt(c, "color", c.option("color", null), 0, 255) : es.msxColor));
		}
	}
}
