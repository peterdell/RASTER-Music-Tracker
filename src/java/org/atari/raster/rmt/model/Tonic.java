package org.atari.raster.rmt.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.wudsn.tools.base.repository.ValueSet;

/**
 * The tonic: the key a song's tables are tuned to (plan 62, formerly the
 * base note), typically A-
 * or C-.
 *
 * <p>A WUDSN Base {@link ValueSet} rather than a Java {@code enum}, so its
 * display texts live in {@code ValueSets.properties} and the tuning dialog
 * can show it through a {@code ValueSetField} (see
 * plans/28_VALUE_SETS_PLAN.md and plans/48_TUNING_VALUE_SETS_PLAN.md). C++
 * has no enumeration for it - the names live only in the IDD_TUNING dialog
 * resource (Rmt.rc's DLGINIT) and the number is used raw.
 *
 * <p>{@link #getNumber()} is the stored value: the {@code TONIC} line (formerly {@code BASENOTE}) of
 * the ini file and the combo box index of the C++ dialog, descending from
 * C- (0) to C# (11); the default is {@link #A} (3). {@link #getValues()}
 * keeps that number order - these values carry a natural order of their
 * own, like {@link TrackerDriverVersion}, so the combo box is not
 * alphabetical.
 */
public final class Tonic extends ValueSet {

	public static final Tonic C;
	public static final Tonic B;
	public static final Tonic A_SHARP;
	public static final Tonic A;
	public static final Tonic G_SHARP;
	public static final Tonic G;
	public static final Tonic F_SHARP;
	public static final Tonic F;
	public static final Tonic E;
	public static final Tonic D_SHARP;
	public static final Tonic D;
	public static final Tonic C_SHARP;

	// Instances, in declaration order (= the order of getNumber())
	private static final Map<String, Tonic> values;

	// Instance attributes
	private final int number;

	static {
		values = new LinkedHashMap<String, Tonic>();
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
		initializeClass(Tonic.class, ValueSets.class);
	}

	private Tonic(String id, int number) {
		super(id, id, number);
		this.number = number;
	}

	private static Tonic add(String id, int number) {
		Tonic result = new Tonic(id, number);
		values.put(result.getId(), result);
		return result;
	}

	/** The unmodifiable list of all values, in the order of {@link #getNumber()}. */
	public static List<Tonic> getValues() {
		return Collections.unmodifiableList(new ArrayList<Tonic>(values.values()));
	}

	/** The instance of that id, or {@code null}. */
	public static Tonic getInstance(String id) {
		if (id == null) {
			throw new IllegalArgumentException("Parameter 'id' must not be null.");
		}
		return values.get(id);
	}

	/** The instance of that {@link #getNumber()}, or {@code null}. */
	public static Tonic getInstance(int number) {
		for (Tonic value : values.values()) {
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
	public static Tonic getInstanceWithFallback(int number) {
		Tonic result = getInstance(number);
		return result != null ? result : A;
	}

	/** The stored value: the ini file's {@code TONIC} line and the C++ combo index, descending from C- (0) to C# (11). */
	public int getNumber() {
		return number;
	}
}
