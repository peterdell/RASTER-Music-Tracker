package org.atari.raster.rmt.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.atari.raster.rmt.script.ScriptRunner;

/**
 * The cross-program export comparison (plans/CPP_SCRIPTING_PLAN.md, section
 * 4): every script in {@code test-resources/scripts} is run through the C++
 * {@code Rmt.exe} (when it is built) and through this port's
 * {@link ScriptRunner}, and the two output folders must match byte for byte
 * - the whole pipeline from loading through tuning, driver, dump and
 * exporters, in both directions. WAV files are exempt (8-bit in C++, 16-bit
 * here); their presence is checked. Skipped when {@code Rmt.exe} is not at
 * its build location.
 */
class CrossProgramExportTest {

	static final Path RMT_EXE = Path.of("out", "Release", "output", "Rmt.exe");
	static final Path SCRIPTS = Path.of("test-resources", "scripts");

	@TempDir
	Path work;

	@Test
	void everyScriptProducesTheSameFilesInBothPrograms() throws IOException, InterruptedException {
		Assumptions.assumeTrue(Files.isRegularFile(RMT_EXE), "needs the C++ build: " + RMT_EXE.toAbsolutePath());
		List<Path> scripts;
		try (Stream<Path> files = Files.list(SCRIPTS)) {
			scripts = files.filter(p -> p.getFileName().toString().endsWith(".rmtscript")).sorted().toList();
		}
		assertTrue(!scripts.isEmpty(), "no scripts in " + SCRIPTS);

		List<String> differences = new ArrayList<>();
		List<String> infos = new ArrayList<>();
		for (Path script : scripts) {
			String name = script.getFileName().toString().replace(".rmtscript", "");
			Path cppOut = work.resolve("cpp").resolve(name);
			Path javaOut = work.resolve("java").resolve(name);

			runCpp(script, cppOut);
			runJava(script, javaOut);

			TreeSet<String> names = new TreeSet<>();
			try (Stream<Path> files = Files.list(cppOut)) {
				files.forEach(p -> names.add(p.getFileName().toString()));
			}
			try (Stream<Path> files = Files.list(javaOut)) {
				files.forEach(p -> names.add(p.getFileName().toString()));
			}
			for (String file : names) {
				Path c = cppOut.resolve(file);
				Path j = javaOut.resolve(file);
				if (!Files.exists(c) || !Files.exists(j)) {
					differences.add(name + "/" + file + ": only in " + (Files.exists(c) ? "C++" : "Java"));
					continue;
				}
				byte[] cb = Files.readAllBytes(c);
				byte[] jb = Files.readAllBytes(j);
				if (file.toLowerCase(Locale.ROOT).endsWith(".wav")) {
					infos.add(name + "/" + file + ": " + cb.length + " bytes (C++, 8-bit) / " + jb.length + " bytes (Java, 16-bit)");
					continue;
				}
				int offset = firstDifference(cb, jb);
				if (offset >= 0) {
					differences.add(name + "/" + file + ": differs at offset " + offset + " (" + cb.length + " vs " + jb.length + " bytes)" + excerpt(cb, jb, offset));
				}
			}
		}
		System.out.println("Cross-program comparison: " + String.join("; ", infos));
		if (!differences.isEmpty()) {
			fail(differences.size() + " difference(s) between Rmt.exe and the Java port:\n" + String.join("\n", differences));
		}
	}

	/** {@code Rmt.exe /SCRIPT:<script>} with {@code RMT_SCRIPT_OUTPUT} pointing at {@code out}; the C++ program reads its own rmt.ini/tuning.ini next to itself. */
	private static void runCpp(Path script, Path out) throws IOException, InterruptedException {
		Files.createDirectories(out);
		Path log = out.getParent().resolve(out.getFileName() + ".cpp.log");
		ProcessBuilder pb = new ProcessBuilder(RMT_EXE.toAbsolutePath().toString(), "/SCRIPT:" + script.toAbsolutePath());
		pb.environment().put(RmtApplication.SCRIPT_OUTPUT_VARIABLE, out.toAbsolutePath().toString());
		pb.environment().put("RMT_SCRIPT_LOG", log.toAbsolutePath().toString()); // the C++ program's console output, whatever console the test runner has
		pb.redirectErrorStream(true);
		Process process = pb.start();
		if (!process.waitFor(5, TimeUnit.MINUTES)) {
			process.destroyForcibly();
			fail("Rmt.exe did not finish " + script);
		}
		String logText = Files.exists(log) ? Files.readString(log, StandardCharsets.ISO_8859_1) : "(no log)";
		assertEquals(0, process.exitValue(), "Rmt.exe exit code for " + script.getFileName() + " (0x" + Integer.toHexString(process.exitValue()) + ")\n" + logText);
	}

	/** The same script through this port, with the same rmt.ini/tuning.ini the C++ program used (its program folder's). */
	private static void runJava(Path script, Path out) throws IOException {
		RmtSession session = new RmtSession();
		RmtConfig config = new RmtConfig(RMT_EXE.getParent());
		config.readRMTConfig(session);
		config.readTuningConfig(session);
		ScriptRunner runner = new ScriptRunner(session, new PrintStream(new java.io.ByteArrayOutputStream()), System.err);
		runner.setOutputFolder(out);
		assertEquals(ScriptRunner.EXIT_OK, runner.run(script), "Java exit code for " + script.getFileName());
	}

	private static int firstDifference(byte[] a, byte[] b) {
		int n = Math.min(a.length, b.length);
		for (int i = 0; i < n; i++) {
			if (a[i] != b[i]) {
				return i;
			}
		}
		return a.length == b.length ? -1 : n;
	}

	private static String excerpt(byte[] a, byte[] b, int offset) {
		StringBuilder sb = new StringBuilder(" C++ ");
		for (int i = offset; i < Math.min(a.length, offset + 12); i++) {
			sb.append(String.format("%02x ", a[i] & 0xFF));
		}
		sb.append("| Java ");
		for (int i = offset; i < Math.min(b.length, offset + 12); i++) {
			sb.append(String.format("%02x ", b[i] & 0xFF));
		}
		return sb.toString().trim();
	}
}
