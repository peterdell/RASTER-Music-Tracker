package org.atari.raster.rmt.model;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * The note keys ("tonekeys") of both keyboard layouts as a Markdown document
 * ({@code doc/rmt_note_keys.md}, the {@code dump notekeys <file>} script
 * command) - the same text as C++'s {@code NoteKeysTable()} in
 * {@code Keyboard2NoteMapping.cpp}, from the same tables
 * ({@link Keyboard2NoteMapping}), so the cross-program comparison compares
 * the two files. Per layout: the keyboard as a picture (each key row with
 * its legends and the note every key plays underneath, the rows staggered
 * as on a keyboard), then a table by note.
 */
public final class NoteKeys {

	private NoteKeys() {
	}

	/**
	 * The QWERTY key at the position of {@code vk} on the layout's keyboard,
	 * for keys that mean a position (the Pokey Explorer's): the three letter
	 * rows, the ISO key discounted; {@code +}/{@code -} ({@code 0xBB}/{@code 0xBD})
	 * and everything else stay themselves. On QWERTY {@code vk} itself.
	 */
	public static int toQwertyPosition(int vk, KeyboardLayout layout) {
		if (layout == KeyboardLayout.QWERTY || vk == 0xBB || vk == 0xBD) {
			return vk;
		}
		List<List<Integer>> rows = layout.getKeyRows();
		for (int r = 1; r < 4; r++) {
			List<Integer> row = stripIsoKey(rows.get(r));
			List<Integer> qwertyRow = stripIsoKey(KeyboardLayout.QWERTY.getKeyRows().get(r));
			int index = row.indexOf(vk);
			if (index >= 0 && index < qwertyRow.size()) {
				return qwertyRow.get(index);
			}
		}
		return vk;
	}

	private static List<Integer> stripIsoKey(List<Integer> row) {
		return !row.isEmpty() && row.get(0) == 0xE2 ? row.subList(1, row.size()) : row;
	}

	/** The French number row's legends for VK_0..VK_9 (the digits are its Shift level). */
	private static final String[] AZERTY_DIGITS = { "à", "&", "é", "\"", "'", "(", "-", "è", "_", "ç" };

	/** Writes the document to {@code file} (UTF-8, LF). */
	public static void write(Path file) throws IOException {
		Files.writeString(file, table(), StandardCharsets.UTF_8);
	}

	public static String table() {
		StringBuilder sb = new StringBuilder();
		boolean first = true;
		for (KeyboardLayout layout : KeyboardLayout.getValues()) {
			if (!first) {
				sb.append('\n');
			}
			first = false;
			appendLayout(sb, layout);
		}
		return sb.toString();
	}

	private static void appendLayout(StringBuilder sb, KeyboardLayout layout) {
		sb.append("### ").append(layout.getId()).append("\n\n");
		appendKeyboard(sb, layout);
		sb.append("| Note | Keys |\n|---|---|\n");
		for (int note = 0; note < Notes.NOTESNUM; note++) {
			StringBuilder keys = new StringBuilder();
			for (int vk = 0; vk < 256; vk++) {
				if (Keyboard2NoteMapping.noteKey(vk, layout) == note) {
					if (keys.length() > 0) {
						keys.append(", ");
					}
					keys.append('`').append(keyLegend(vk, layout)).append('`');
				}
			}
			if (keys.length() > 0) {
				sb.append("| ").append(Notes.getNote(note)).append(" | ").append(keys).append(" |\n");
			}
		}
	}

	private static void appendKeyboard(StringBuilder sb, KeyboardLayout layout) {
		List<List<Integer>> rows = layout.getKeyRows();
		sb.append("```\n");
		for (int r = 0; r < rows.size(); r++) {
			StringBuilder keys = new StringBuilder(" ".repeat(r * 2));
			StringBuilder notes = new StringBuilder(" ".repeat(r * 2));
			for (int vk : rows.get(r)) {
				keys.append(pad(" " + keyLegend(vk, layout), 5));
				int note = Keyboard2NoteMapping.noteKey(vk, layout);
				notes.append(pad(note >= 0 ? Notes.getNote(note) : "", 5));
			}
			sb.append(keys.toString().stripTrailing()).append('\n').append(notes.toString().stripTrailing()).append('\n');
		}
		sb.append("```\n\n");
	}

	private static String pad(String s, int width) {
		int columns = s.codePointCount(0, s.length());
		return columns < width ? s + " ".repeat(width - columns) : s;
	}

	/**
	 * The legend of a virtual key as the keyboard of the layout prints it:
	 * the letters are the same physical keys in both layouts; the number row
	 * of the French AZERTY keyboard prints & é " ' ( - è _ ç à, and the OEM
	 * keys differ (the German QWERTZ keyboard: ü + ö ä # ß ´ ^ -).
	 */
	static String keyLegend(int vk, KeyboardLayout layout) {
		boolean azerty = layout == KeyboardLayout.AZERTY;
		boolean qwertz = layout == KeyboardLayout.QWERTZ;
		if (vk >= '0' && vk <= '9') {
			return azerty ? AZERTY_DIGITS[vk - '0'] : String.valueOf((char) vk);
		}
		if (vk >= 'A' && vk <= 'Z') {
			return String.valueOf((char) vk);
		}
		return switch (vk) {
		case 0xBA -> azerty ? "$" : qwertz ? "ü" : ";"; // VK_OEM_1
		case 0xBB -> qwertz ? "+" : "="; // VK_OEM_PLUS
		case 0xBC -> ","; // VK_OEM_COMMA
		case 0xBD -> "-"; // VK_OEM_MINUS
		case 0xBE -> azerty ? ";" : "."; // VK_OEM_PERIOD
		case 0xBF -> azerty ? ":" : qwertz ? "#" : "/"; // VK_OEM_2
		case 0xC0 -> azerty ? "ù" : qwertz ? "ö" : "`"; // VK_OEM_3
		case 0xDB -> azerty ? ")" : qwertz ? "ß" : "["; // VK_OEM_4
		case 0xDC -> azerty ? "*" : qwertz ? "^" : "\\"; // VK_OEM_5
		case 0xDD -> azerty ? "^" : qwertz ? "´" : "]"; // VK_OEM_6
		case 0xDE -> azerty ? "²" : qwertz ? "ä" : "'"; // VK_OEM_7
		case 0xDF -> "!"; // VK_OEM_8
		case 0xE2 -> "<"; // VK_OEM_102
		default -> String.format("VK_%02X", vk);
		};
	}
}
