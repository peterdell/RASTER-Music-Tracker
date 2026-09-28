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

	/** The four key rows of the layout's keyboard, number row first, as virtual keys. */
	private static final List<List<Integer>> QWERTY_ROWS = List.of( //
			List.of((int) '1', (int) '2', (int) '3', (int) '4', (int) '5', (int) '6', (int) '7', (int) '8', (int) '9', (int) '0', 0xBD, 0xBB), //
			List.of((int) 'Q', (int) 'W', (int) 'E', (int) 'R', (int) 'T', (int) 'Y', (int) 'U', (int) 'I', (int) 'O', (int) 'P', 0xDB, 0xDD), //
			List.of((int) 'A', (int) 'S', (int) 'D', (int) 'F', (int) 'G', (int) 'H', (int) 'J', (int) 'K', (int) 'L', 0xBA, 0xDE), //
			List.of((int) 'Z', (int) 'X', (int) 'C', (int) 'V', (int) 'B', (int) 'N', (int) 'M', 0xBC, 0xBE, 0xBF));
	private static final List<List<Integer>> AZERTY_ROWS = List.of( //
			List.of((int) '1', (int) '2', (int) '3', (int) '4', (int) '5', (int) '6', (int) '7', (int) '8', (int) '9', (int) '0', 0xDB, 0xBB), //
			List.of((int) 'A', (int) 'Z', (int) 'E', (int) 'R', (int) 'T', (int) 'Y', (int) 'U', (int) 'I', (int) 'O', (int) 'P', 0xDD, 0xBA), //
			List.of((int) 'Q', (int) 'S', (int) 'D', (int) 'F', (int) 'G', (int) 'H', (int) 'J', (int) 'K', (int) 'L', (int) 'M', 0xC0, 0xDC), //
			List.of(0xE2, (int) 'W', (int) 'X', (int) 'C', (int) 'V', (int) 'B', (int) 'N', 0xBC, 0xBE, 0xBF, 0xDF));

	/** The French number row's legends for VK_0..VK_9 (the digits are its Shift level). */
	private static final String[] AZERTY_DIGITS = { "à", "&", "é", "\"", "'", "(", "-", "è", "_", "ç" };

	/** Writes the document to {@code file} (UTF-8, LF). */
	public static void write(Path file) throws IOException {
		Files.writeString(file, table(), StandardCharsets.UTF_8);
	}

	public static String table() {
		StringBuilder sb = new StringBuilder();
		appendLayout(sb, "QWERTY", KeyboardLayout.QWERTY);
		sb.append('\n');
		appendLayout(sb, "AZERTY", KeyboardLayout.AZERTY);
		return sb.toString();
	}

	private static void appendLayout(StringBuilder sb, String name, int layout) {
		sb.append("### ").append(name).append("\n\n");
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

	private static void appendKeyboard(StringBuilder sb, int layout) {
		List<List<Integer>> rows = layout == KeyboardLayout.AZERTY ? AZERTY_ROWS : QWERTY_ROWS;
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
	 * keys differ.
	 */
	static String keyLegend(int vk, int layout) {
		boolean azerty = layout == KeyboardLayout.AZERTY;
		if (vk >= '0' && vk <= '9') {
			return azerty ? AZERTY_DIGITS[vk - '0'] : String.valueOf((char) vk);
		}
		if (vk >= 'A' && vk <= 'Z') {
			return String.valueOf((char) vk);
		}
		return switch (vk) {
		case 0xBA -> azerty ? "$" : ";"; // VK_OEM_1
		case 0xBB -> "="; // VK_OEM_PLUS
		case 0xBC -> ","; // VK_OEM_COMMA
		case 0xBD -> "-"; // VK_OEM_MINUS
		case 0xBE -> azerty ? ";" : "."; // VK_OEM_PERIOD
		case 0xBF -> azerty ? ":" : "/"; // VK_OEM_2
		case 0xC0 -> azerty ? "ù" : "`"; // VK_OEM_3
		case 0xDB -> azerty ? ")" : "["; // VK_OEM_4
		case 0xDC -> azerty ? "*" : "\\"; // VK_OEM_5
		case 0xDD -> azerty ? "^" : "]"; // VK_OEM_6
		case 0xDE -> azerty ? "²" : "'"; // VK_OEM_7
		case 0xDF -> "!"; // VK_OEM_8
		case 0xE2 -> "<"; // VK_OEM_102
		default -> String.format("VK_%02X", vk);
		};
	}
}
