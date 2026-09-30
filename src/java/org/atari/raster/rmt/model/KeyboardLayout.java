package org.atari.raster.rmt.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.wudsn.tools.base.repository.ValueSet;

/**
 * Ported from the C++ enum class KeyboardLayout (General.h): which physical
 * keyboard the note keys ("tonekeys") are laid out for.
 *
 * <p>
 * A WUDSN Base {@link ValueSet} rather than a Java {@code enum}, so its display
 * texts live in {@code ValueSets.properties} and the Options dialog can show it
 * through a {@code ValueSetField} (see plans/28_VALUE_SETS_PLAN.md). The
 * instances are singletons, so {@code ==} comparisons work.
 *
 * <p>
 * {@link #getNumber()} is C++'s enum value, the {@code rmt.ini} setting
 * {@code KEYBOARD_LAYOUT} and the RMW file's UI parameter - shared with
 * {@code Rmt.exe}, so the numbers are fixed. A number the file does not know
 * falls back to {@link #QWERTY} (the user's decision of 2026-10-01); C++ casts
 * it to an invalid enum value there, where no key plays a note at all.
 *
 * <p>
 * Each instance carries the two tables that make up its layout: the note every
 * key plays ({@link #getNoteKeys()}, indexed by virtual key) and the four key
 * rows of its keyboard ({@link #getKeyRows()}), which the keyboard picture of
 * {@code dump notekeys} and the Pokey Explorer's positional keys read. Adding a
 * layout therefore cannot forget one of them.
 */
public final class KeyboardLayout extends ValueSet {

	public static final KeyboardLayout QWERTY;
	/**
	 * The German keyboard (since 2026-09-30, plan 27): the QWERTY piano by key
	 * position, Y and Z exchanged, the OEM keys moved.
	 */
	public static final KeyboardLayout QWERTZ;
	public static final KeyboardLayout AZERTY;

	// Instances, in declaration order (= the display order, which is the sort key - not the order of getNumber())
	private static final Map<String, KeyboardLayout> values;

	// Instance attributes
	private final int number;
	private final byte[] noteKeys;
	private final List<List<Integer>> keyRows;

	static {
		values = new LinkedHashMap<String, KeyboardLayout>();
		QWERTY = add("QWERTY", 0, 0, Keyboard2NoteMapping.KEYNOTES_QWERTY, List.of( //
				List.of((int) '1', (int) '2', (int) '3', (int) '4', (int) '5', (int) '6', (int) '7', (int) '8',
						(int) '9', (int) '0', 0xBD, 0xBB), //
				List.of((int) 'Q', (int) 'W', (int) 'E', (int) 'R', (int) 'T', (int) 'Y', (int) 'U', (int) 'I',
						(int) 'O', (int) 'P', 0xDB, 0xDD), //
				List.of((int) 'A', (int) 'S', (int) 'D', (int) 'F', (int) 'G', (int) 'H', (int) 'J', (int) 'K',
						(int) 'L', 0xBA, 0xDE), //
				List.of((int) 'Z', (int) 'X', (int) 'C', (int) 'V', (int) 'B', (int) 'N', (int) 'M', 0xBC, 0xBE,
						0xBF)));
		QWERTZ = add("QWERTZ", 1, 2, Keyboard2NoteMapping.KEYNOTES_QWERTZ, List.of( //
				List.of((int) '1', (int) '2', (int) '3', (int) '4', (int) '5', (int) '6', (int) '7', (int) '8',
						(int) '9', (int) '0', 0xDB, 0xDD), //
				List.of((int) 'Q', (int) 'W', (int) 'E', (int) 'R', (int) 'T', (int) 'Z', (int) 'U', (int) 'I',
						(int) 'O', (int) 'P', 0xBA, 0xBB), //
				List.of((int) 'A', (int) 'S', (int) 'D', (int) 'F', (int) 'G', (int) 'H', (int) 'J', (int) 'K',
						(int) 'L', 0xC0, 0xDE, 0xBF), //
				List.of(0xE2, (int) 'Y', (int) 'X', (int) 'C', (int) 'V', (int) 'B', (int) 'N', (int) 'M', 0xBC, 0xBE,
						0xBD)));
		AZERTY = add("AZERTY", 2, 1, Keyboard2NoteMapping.KEYNOTES_AZERTY, List.of( //
				List.of((int) '1', (int) '2', (int) '3', (int) '4', (int) '5', (int) '6', (int) '7', (int) '8',
						(int) '9', (int) '0', 0xDB, 0xBB), //
				List.of((int) 'A', (int) 'Z', (int) 'E', (int) 'R', (int) 'T', (int) 'Y', (int) 'U', (int) 'I',
						(int) 'O', (int) 'P', 0xDD, 0xBA), //
				List.of((int) 'Q', (int) 'S', (int) 'D', (int) 'F', (int) 'G', (int) 'H', (int) 'J', (int) 'K',
						(int) 'L', (int) 'M', 0xC0, 0xDC), //
				List.of(0xE2, (int) 'W', (int) 'X', (int) 'C', (int) 'V', (int) 'B', (int) 'N', 0xBC, 0xBE, 0xBF,
						0xDF)));
		initializeClass(KeyboardLayout.class, ValueSets.class);
	}

	private KeyboardLayout(String id, int sortKey, int number, byte[] noteKeys, List<List<Integer>> keyRows) {
		super(id, id, sortKey); // the text comes from ValueSets.properties (initializeClass below); the sort key is the display order, the number is C++'s value
		this.number = number;
		this.noteKeys = noteKeys;
		this.keyRows = keyRows;
	}

	private static KeyboardLayout add(String id, int sortKey, int number, byte[] noteKeys,
			List<List<Integer>> keyRows) {
		KeyboardLayout result = new KeyboardLayout(id, sortKey, number, noteKeys, keyRows);
		values.put(result.getId(), result);
		return result;
	}

	/**
	 * The unmodifiable list of all values, in the display order (QWERTY,
	 * QWERTZ, AZERTY) - the order of the sort key, which the Options dialog's
	 * field sorts by and the note key document is written in, not the order of
	 * {@link #getNumber()}.
	 */
	public static List<KeyboardLayout> getValues() {
		return Collections.unmodifiableList(new ArrayList<KeyboardLayout>(values.values()));
	}

	/** The instance of that id, or {@code null}. */
	public static KeyboardLayout getInstance(String id) {
		if (id == null) {
			throw new IllegalArgumentException("Parameter 'id' must not be null.");
		}
		return values.get(id);
	}

	/**
	 * The instance of that {@link #getNumber()} - {@link #QWERTY} for a number no
	 * layout has, so a stray {@code rmt.ini} or RMW value still plays notes.
	 */
	public static KeyboardLayout getInstance(int number) {
		for (KeyboardLayout value : values.values()) {
			if (value.number == number) {
				return value;
			}
		}
		return QWERTY;
	}

	/**
	 * The layout of a keyboard language ({@link java.util.Locale#getLanguage()}):
	 * German QWERTZ, French AZERTY, else QWERTY - the first-start default.
	 */
	public static KeyboardLayout forLanguage(String language) {
		if ("de".equals(language)) {
			return QWERTZ;
		}
		if ("fr".equals(language)) {
			return AZERTY;
		}
		return QWERTY;
	}

	/**
	 * C++'s enum value: the {@code rmt.ini} setting {@code KEYBOARD_LAYOUT} and the
	 * RMW file's UI parameter.
	 */
	public int getNumber() {
		return number;
	}

	/**
	 * {@code keynotes_<layout>}: the note each virtual key plays, {@code -1} where
	 * the key plays none.
	 */
	byte[] getNoteKeys() {
		return noteKeys;
	}

	/**
	 * The four key rows of this keyboard, number row first, as virtual keys (the
	 * ISO {@code <} key, {@code 0xE2}, leads the bottom row where the keyboard has
	 * it).
	 */
	public List<List<Integer>> getKeyRows() {
		return keyRows;
	}
}
