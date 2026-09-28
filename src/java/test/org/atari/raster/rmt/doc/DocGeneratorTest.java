package org.atari.raster.rmt.doc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The documentation generator over the repository's real {@code doc/} folder, and its rules on synthetic folders. */
class DocGeneratorTest {

	static final Path DOC = Path.of("doc");

	@TempDir
	Path work;

	@Test
	void generatesEveryShippedDocumentFromTheRepositoryDocFolder() throws IOException {
		Path out = work.resolve("docs");
		List<Path> written = new DocGenerator().generate(DOC, out);
		assertFalse(written.isEmpty());

		List<String> markdownNames;
		try (Stream<Path> files = Files.list(DOC)) {
			markdownNames = files.map(p -> p.getFileName().toString()).filter(n -> n.endsWith(".md")).toList();
		}
		assertFalse(markdownNames.isEmpty());
		for (String md : markdownNames) {
			assertTrue(Files.isRegularFile(out.resolve(md.replace(".md", ".html"))), md + " has no generated page");
		}
		// The manual is generated from rmt_en.md and includes the generated command table; the 1.28 manual (no .md source) and the images travel as they are
		String manual = Files.readString(out.resolve("rmt_en.html"), StandardCharsets.UTF_8);
		assertTrue(manual.contains("<h1>RASTER Music Tracker (RMT) - Manual</h1>"), manual.substring(0, 400));
		assertTrue(manual.contains("<td>Menu Tools</td>") && manual.contains("<td>Run Script...</td>"), "rmt_action_infos.md included");
		assertTrue(manual.contains("<h3>AZERTY</h3>"), "rmt_note_keys.md included");
		assertTrue(Files.isRegularFile(out.resolve("rmt_en_128.html")));
		assertTrue(Files.isRegularFile(out.resolve("rmt.gif")));
		assertTrue(Files.isRegularFile(out.resolve("img").resolve("song-go-to-line.png")));
		// No Markdown and no developer text files in the distribution
		try (Stream<Path> files = Files.list(out)) {
			assertTrue(files.map(p -> p.getFileName().toString()).noneMatch(n -> n.endsWith(".md") || n.endsWith(".txt")));
		}

		String scripting = Files.readString(out.resolve("rmt_scripting.html"), StandardCharsets.UTF_8);
		assertTrue(scripting.contains("<title>RMT scripting</title>"), "title from the first heading");
		assertTrue(scripting.contains("<table>"), "GFM tables are rendered");
		assertTrue(scripting.contains("<pre><code>"), "code fences are rendered");

		String versions = Files.readString(out.resolve("rmt_versions.html"), StandardCharsets.UTF_8);
		assertTrue(versions.contains("href=\"rmt_changes.html\""), "links between pages point at the generated pages: " + versions);
		String format = Files.readString(out.resolve("rmt_format.html"), StandardCharsets.UTF_8);
		assertTrue(format.contains("href=\"./rmt_tracker.html\""), format);
		assertTrue(format.contains("blob/dev/doc/rmt_format.md"), "links with a scheme (GitHub) are left alone");

		String changes = Files.readString(out.resolve("rmt_changes.html"), StandardCharsets.UTF_8);
		assertTrue(changes.contains("/SCRIPT:&lt;file&gt;"), "the <file> placeholder survives as text, not as a tag");
	}

	@Test
	void includeMarkerInlinesTheFileAndAMissingOneFails() throws IOException {
		Path source = work.resolve("src");
		Files.createDirectories(source);
		Files.writeString(source.resolve("page.md"), "# Page\n\nBefore.\n\n<!-- include: table.md -->\n\nAfter.\n");
		Files.writeString(source.resolve("table.md"), "| A | B |\n|---|---|\n| 1 | 2 |\n");
		Path out = work.resolve("out");

		new DocGenerator().generate(source, out);

		String page = Files.readString(out.resolve("page.html"), StandardCharsets.UTF_8);
		assertTrue(page.contains("<table>") && page.contains("<td>2</td>"), page);
		assertTrue(page.indexOf("Before.") < page.indexOf("<table>") && page.indexOf("<table>") < page.indexOf("After."), page);

		Files.writeString(source.resolve("broken.md"), "# Broken\n<!-- include: missing.md -->\n");
		IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> new DocGenerator().generate(source, work.resolve("out2")));
		assertTrue(ex.getMessage().contains("missing.md"), ex.getMessage());
	}

	@Test
	void markdownSourceWinsOverAHandWrittenPageOfTheSameName() throws IOException {
		Path source = work.resolve("src");
		Files.createDirectories(source);
		Files.writeString(source.resolve("same.md"), "# From Markdown\n");
		Files.writeString(source.resolve("same.html"), "<html>hand-written</html>");
		Files.writeString(source.resolve("alone.html"), "<html>alone</html>");
		Files.writeString(source.resolve("notes.txt"), "not shipped");
		Path out = work.resolve("out");

		List<Path> written = new DocGenerator().generate(source, out);

		assertEquals(2, written.size(), written.toString());
		assertTrue(Files.readString(out.resolve("same.html")).contains("<h1>From Markdown</h1>"));
		assertEquals("<html>alone</html>", Files.readString(out.resolve("alone.html")));
		assertFalse(Files.exists(out.resolve("notes.txt")));
	}

	@Test
	void titleFallsBackToTheFileNameWithoutAHeading() throws IOException {
		Path source = work.resolve("src");
		Files.createDirectories(source);
		Files.writeString(source.resolve("plain.md"), "Just a paragraph with <b>inline html</b>.\n");

		new DocGenerator().generate(source, work.resolve("out"));

		String page = Files.readString(work.resolve("out").resolve("plain.html"), StandardCharsets.UTF_8);
		assertTrue(page.contains("<title>plain</title>"), page);
		assertTrue(page.contains("<b>inline html</b>"), "inline HTML passes through: " + page);
	}
}
