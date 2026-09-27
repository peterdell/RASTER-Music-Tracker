package org.atari.raster.rmt.model;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The note keys ("tonekeys") of both keyboard layouts as a Markdown document
 * ({@code doc/rmt_note_keys.md}, the {@code dump notekeys <file>} script
 * command) - the same text as C++'s {@code NoteKeysTable()} in
 * {@code Keyboard2NoteMapping.cpp}, from the same tables
 * ({@link Keyboard2NoteMapping}), so the cross-program comparison compares
 * the two files.
 */
public final class NoteKeys {

	private NoteKeys() {
	}

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
		sb.append("### ").append(name).append("\n\n| Note | Keys |\n|---|---|\n");
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

	/**
	 * The legend of a virtual key as the keyboard of the layout prints it:
	 * the letters and digits are the same physical keys in both layouts (the
	 * AZERTY number row is Shift-ed for the digits), the OEM keys differ.
	 */
	static String keyLegend(int vk, int layout) {
		if ((vk >= '0' && vk <= '9') || (vk >= 'A' && vk <= 'Z')) {
			return String.valueOf((char) vk);
		}
		boolean azerty = layout == KeyboardLayout.AZERTY;
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
