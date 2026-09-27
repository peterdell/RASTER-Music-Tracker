package org.atari.raster.rmt.doc;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.commonmark.Extension;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.node.Heading;
import org.commonmark.node.Link;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.commonmark.renderer.text.TextContentRenderer;

/**
 * The documentation generator (plans/DOC_GENERATION_PLAN.md, section 2.1):
 * the Markdown files in {@code doc/} are the documentation's source, kept
 * as Markdown because that is what is maintained and what GitHub shows; the
 * distributions ship HTML, because their users have no Markdown reader.
 * This class converts every {@code *.md} of the source folder to
 * {@code <name>.html} in the target folder (CommonMark with GitHub-flavoured
 * tables, one page template, links between the pages rewritten from
 * {@code .md} to {@code .html}), copies the images ({@code *.gif},
 * {@code *.png}, the {@code img/} folder) and the HTML files that have no
 * Markdown source (the manuals, until they are converted), and inlines
 * generated tables where a page says {@code <!-- include: file.md -->}.
 *
 * <p>Run by {@code build/stage_java_release.sh} and
 * {@code build/build_rmt_pre.bat} from the built {@code rmt.jar}:
 *
 * <pre>
 * java -cp rmt.jar org.atari.raster.rmt.doc.DocGenerator doc target/stage/docs
 * </pre>
 */
public final class DocGenerator {

	/** {@code <!-- include: rmt_action_infos.md -->} on a line of its own. */
	static final Pattern INCLUDE = Pattern.compile("^[ \\t]*<!--[ \\t]*include:[ \\t]*(\\S+)[ \\t]*-->[ \\t]*$", Pattern.MULTILINE);

	private static final Set<String> IMAGE_EXTENSIONS = Set.of("gif", "png", "jpg");
	private static final String IMAGE_FOLDER = "img";

	private final Parser parser;
	private final HtmlRenderer renderer;
	private final TextContentRenderer textRenderer;

	public DocGenerator() {
		List<Extension> extensions = List.of(TablesExtension.create());
		parser = Parser.builder().extensions(extensions).build();
		renderer = HtmlRenderer.builder().extensions(extensions).attributeProviderFactory(context -> DocGenerator::rewriteMarkdownLinks).build();
		textRenderer = TextContentRenderer.builder().build();
	}

	public static void main(String[] args) {
		if (args.length != 2) {
			System.err.println("Usage: java -cp rmt.jar " + DocGenerator.class.getName() + " <source folder with *.md> <target folder for *.html>");
			System.exit(2);
		}
		try {
			List<Path> written = new DocGenerator().generate(Path.of(args[0]), Path.of(args[1]));
			System.out.println("Generated " + written.size() + " documentation file(s) in " + Path.of(args[1]).toAbsolutePath());
		} catch (IOException | UncheckedIOException | IllegalArgumentException ex) {
			System.err.println("ERROR: " + ex.getMessage());
			System.exit(1);
		}
	}

	/**
	 * Fills {@code target} from {@code source}; returns the files written, in
	 * source order. An include of a missing file is an
	 * {@link IllegalArgumentException} - the build must not ship a page with a
	 * hole in it.
	 */
	public List<Path> generate(Path source, Path target) throws IOException {
		if (!Files.isDirectory(source)) {
			throw new IllegalArgumentException("Source folder not found: " + source.toAbsolutePath());
		}
		Files.createDirectories(target);
		List<Path> written = new ArrayList<>();
		List<Path> entries;
		try (Stream<Path> stream = Files.list(source)) {
			entries = stream.sorted().toList();
		}
		for (Path entry : entries) {
			String name = entry.getFileName().toString();
			if (Files.isDirectory(entry)) {
				if (name.equals(IMAGE_FOLDER)) {
					copyTree(entry, target.resolve(name), written);
				}
				continue;
			}
			String extension = extension(name);
			switch (extension) {
			case "md" -> {
				Path out = target.resolve(stem(name) + ".html");
				Files.writeString(out, convert(source, entry), StandardCharsets.UTF_8);
				written.add(out);
			}
			case "html" -> {
				if (!Files.exists(source.resolve(stem(name) + ".md"))) { // a Markdown source wins over a hand-written page of the same name
					written.add(copy(entry, target.resolve(name)));
				}
			}
			default -> {
				if (IMAGE_EXTENSIONS.contains(extension)) {
					written.add(copy(entry, target.resolve(name)));
				}
			}
			}
		}
		return written;
	}

	/** One Markdown file as a complete HTML page. */
	String convert(Path source, Path markdownFile) throws IOException {
		String markdown = expandIncludes(source, read(markdownFile));
		Node document = parser.parse(markdown);
		String title = firstHeading(document);
		if (title == null) {
			title = stem(markdownFile.getFileName().toString());
		}
		return page(title, renderer.render(document));
	}

	private String expandIncludes(Path source, String markdown) throws IOException {
		Matcher m = INCLUDE.matcher(markdown);
		StringBuilder sb = new StringBuilder();
		while (m.find()) {
			Path included = source.resolve(m.group(1));
			if (!Files.isRegularFile(included)) {
				throw new IllegalArgumentException("Included file not found: " + included.toAbsolutePath());
			}
			m.appendReplacement(sb, Matcher.quoteReplacement(read(included)));
		}
		m.appendTail(sb);
		return sb.toString();
	}

	private String firstHeading(Node document) {
		for (Node node = document.getFirstChild(); node != null; node = node.getNext()) {
			if (node instanceof Heading) {
				return textRenderer.render(node).trim();
			}
		}
		return null;
	}

	/** A relative link to a Markdown page becomes a link to its generated HTML page; anything with a scheme (GitHub, the web) stays. */
	private static void rewriteMarkdownLinks(Node node, String tagName, Map<String, String> attributes) {
		if (!(node instanceof Link)) {
			return;
		}
		String href = attributes.get("href");
		if (href == null || href.contains(":")) {
			return;
		}
		int hash = href.indexOf('#');
		String path = hash >= 0 ? href.substring(0, hash) : href;
		String anchor = hash >= 0 ? href.substring(hash) : "";
		if (path.toLowerCase(Locale.ROOT).endsWith(".md")) {
			attributes.put("href", path.substring(0, path.length() - 3) + ".html" + anchor);
		}
	}

	/** The page template: one small embedded stylesheet, in the spirit of {@code rmt_en.html}'s. */
	static String page(String title, String body) {
		return """
				<!DOCTYPE html>
				<html>
				<head>
				<meta charset="utf-8">
				<title>%s</title>
				<style>
				body { font-family: sans-serif; max-width: 60em; margin: 1em auto; padding: 0 1em; line-height: 1.4; background-color: #FFFFFF; }
				code, pre { background-color: #E0E0E0; }
				code { padding: 0 0.2em; white-space: nowrap; }
				pre { padding: 0.5em; overflow-x: auto; }
				pre code { padding: 0; white-space: pre; }
				table { border-collapse: collapse; }
				th, td { border: 1px solid #A0A0A0; padding: 0.2em 0.5em; text-align: left; vertical-align: top; }
				th { background-color: #F0F0F0; }
				img { max-width: 100%%; }
				</style>
				</head>
				<body>
				%s</body>
				</html>
				""".formatted(escape(title), body);
	}

	private static String read(Path file) throws IOException {
		String text = Files.readString(file, StandardCharsets.UTF_8);
		if (!text.isEmpty() && text.charAt(0) == '﻿') {
			text = text.substring(1); // some of the files carry a byte order mark
		}
		return text;
	}

	private static Path copy(Path from, Path to) throws IOException {
		Files.copy(from, to, StandardCopyOption.REPLACE_EXISTING);
		return to;
	}

	private static void copyTree(Path from, Path to, List<Path> written) throws IOException {
		Files.createDirectories(to);
		List<Path> entries;
		try (Stream<Path> stream = Files.list(from)) {
			entries = stream.sorted().toList();
		}
		for (Path entry : entries) {
			Path out = to.resolve(entry.getFileName().toString());
			if (Files.isDirectory(entry)) {
				copyTree(entry, out, written);
			} else {
				written.add(copy(entry, out));
			}
		}
	}

	private static String extension(String name) {
		int dot = name.lastIndexOf('.');
		return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
	}

	private static String stem(String name) {
		int dot = name.lastIndexOf('.');
		return dot < 0 ? name : name.substring(0, dot);
	}

	private static String escape(String text) {
		return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}
}
