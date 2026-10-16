package org.atari.raster.rmt.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.wudsn.tools.base.repository.ValueSet;

/**
 * The base note/key from which a song's tuning is calculated, typically A-
 * or C-.
 *
 * <p>A WUDSN Base {@link ValueSet} rather than a Java {@code enum}, so its
 * display texts live in {@code ValueSets.properties} and the tuning dialog
 * can show it through a {@code ValueSetField} (see
 * plans/28_VALUE_SETS_PLAN.md and plans/48_TUNING_VALUE_SETS_PLAN.md). C++
 * has no enumeration for it - the names live only in the IDD_TUNING dialog
 * resource (Rmt.rc's DLGINIT) and the number is used raw.
 *
 * <p>{@link #getNumber()} is the stored value: the {@code BASENOTE} line of
 * the ini file and the combo box index of the C++ dialog, descending from
 * C- (0) to C# (11); the default is {@link #A} (3). {@link #getValues()}
 * keeps that number order - these values carry a natural order of their
 * own, like {@link TrackerDriverVersion}, so the combo box is not
 * alphabetical.
 */
public final class BaseNote extends ValueSet {

	public static final BaseNote C;
	public static final BaseNote B;
	public static final BaseNote A_SHARP;
	public static final BaseNote A;
	public static final BaseNote G_SHARP;
	public static final BaseNote G;
	public static final BaseNote F_SHARP;
	public static final BaseNote F;
	public static final BaseNote E;
	public static final BaseNote D_SHARP;
	public static final BaseNote D;
	public static final BaseNote C_SHARP;

	// Instances, in declaration order (= the order of getNumber())
	private static final Map<String, BaseNote> values;

	// Instance attributes
	private final int number;

	static {
		values = new LinkedHashMap<String, BaseNote>();
		C = add("C", 0);
		B = add("B", 1);
		A_SHARP = add("A_SHARP", 2);
		A = add("A", 3);
		G_SHARP = add("G_SHARP", 4);
		G = add("G", 5);
		F_SHARP = add("F_SHARP", 6);
		F = add("F", 7);
		E = add("E", 8);
		D_SHARP = add("D_SHARP", 9);
		D = add("D", 10);
		C_SHARP = add("C_SHARP", 11);
		initializeClass(BaseNote.class, ValueSets.class);
	}

	private BaseNote(String id, int number) {
		super(id, id, number);
		this.number = number;
	}

	private static BaseNote add(String id, int number) {
		BaseNote result = new BaseNote(id, number);
		values.put(result.getId(), result);
		return result;
	}

	/** The unmodifiable list of all values, in the order of {@link #getNumber()}. */
	public static List<BaseNote> getValues() {
		return Collections.unmodifiableList(new ArrayList<BaseNote>(values.values()));
	}

	/** The instance of that id, or {@code null}. */
	public static BaseNote getInstance(String id) {
		if (id == null) {
			throw new IllegalArgumentException("Parameter 'id' must not be null.");
		}
		return values.get(id);
	}

	/** The instance of that {@link #getNumber()}, or {@code null}. */
	public static BaseNote getInstance(int number) {
		for (BaseNote value : values.values()) {
			if (value.number == number) {
				return value;
			}
		}
		return null;
	}

	/**
	 * The instance of that number, taking any number an ini file can carry:
	 * an unknown number is the default {@link #A}, a deliberate guard where
	 * C++ reads out of bounds (the old dialog clamped the index instead).
	 */
	public static BaseNote getInstanceWithFallback(int number) {
		BaseNote result = getInstance(number);
		return result != null ? result : A;
	}

	/** The stored value: the ini file's {@code BASENOTE} line and the C++ combo index, descending from C- (0) to C# (11). */
	public int getNumber() {
		return number;
	}
}
