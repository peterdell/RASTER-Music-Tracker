package org.atari.raster.rmt.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.wudsn.tools.base.repository.ValueSet;

/**
 * The temperament a song's notes are tuned in: equal temperament, one of the
 * 28 presets of {@link Tuning}, or the custom ratios of
 * {@link TuningRatios}.
 *
 * <p>A WUDSN Base {@link ValueSet} rather than a Java {@code enum}, so its
 * display texts live in {@code ValueSets.properties} and the tuning dialog
 * can show it through a {@code ValueSetField} (see
 * plans/28_VALUE_SETS_PLAN.md and plans/48_TUNING_VALUE_SETS_PLAN.md). C++
 * has no enumeration for it at all - the names live only in the IDD_TUNING
 * dialog resource (Rmt.rc's DLGINIT) and the number is used raw.
 *
 * <p>{@link #getNumber()} is the stored value: the {@code TEMPERAMENT} line
 * of the ini file, the combo box index of the C++ dialog, and the row of
 * {@code Tuning.TEMPERAMENT_PRESETS} for 1-28. {@link #getValues()} keeps
 * the number order - these values carry a natural order of their own, like
 * {@link TrackerDriverVersion}, so the combo box is not alphabetical.
 */
public final class Temperament extends ValueSet {

	public static final Temperament EQUAL;
	public static final Temperament YOUNG_1799_NO1;
	public static final Temperament YOUNG_1799_NO2;
	public static final Temperament YOUNG_1807;
	public static final Temperament WERCKMEISTER_III;
	public static final Temperament ALEMBERT_ROUSSEAU;
	public static final Temperament ARON_NEIDHARDT;
	public static final Temperament ATOM_SCHISMA;
	public static final Temperament TET12_MINIMAL_ORDER_17;
	public static final Temperament BAILEY;
	public static final Temperament BARNES;
	public static final Temperament BETHISY;
	public static final Temperament BIG_GULP;
	public static final Temperament WEDDING_CAKE;
	public static final Temperament UPSIDE_DOWN_WEDDING_CAKE;
	public static final Temperament PYTHAGOREAN;
	public static final Temperament SCHNEIDER;
	public static final Temperament ZARLINO;
	public static final Temperament FOKKER;
	public static final Temperament BACH;
	public static final Temperament VALLOTTI_YOUNG;
	public static final Temperament VALLOTTI_YOUNG_WERCKMEISTER_III;
	public static final Temperament CUSTOM;

	// Instances, in declaration order (= the order of getNumber())
	private static final Map<String, Temperament> values;

	// Instance attributes
	private final int number;

	static {
		values = new LinkedHashMap<String, Temperament>();
		EQUAL = add("EQUAL", Tuning.NO_TEMPERAMENT);
		YOUNG_1799_NO1 = add("YOUNG_1799_NO1", 1);
		YOUNG_1799_NO2 = add("YOUNG_1799_NO2", 2);
		YOUNG_1807 = add("YOUNG_1807", 3);
		WERCKMEISTER_III = add("WERCKMEISTER_III", 4);
		// 5, 14 and 24-28 are retired (plan 59): Egal a Quintes Justes,
		// Bohlen, pentatonic, Aeolic, Bapare, Yaswa and 19-EDO do not fit the
		// 12-note, 2:1-octave model (the last five are scales or
		// stretched-octave sets, not temperaments). Their numbers stay
		// reserved; an ini carrying one falls back to EQUAL via
		// getInstanceWithFallback().
		ALEMBERT_ROUSSEAU = add("ALEMBERT_ROUSSEAU", 6);
		ARON_NEIDHARDT = add("ARON_NEIDHARDT", 7);
		ATOM_SCHISMA = add("ATOM_SCHISMA", 8);
		TET12_MINIMAL_ORDER_17 = add("TET12_MINIMAL_ORDER_17", 9);
		BAILEY = add("BAILEY", 10);
		BARNES = add("BARNES", 11);
		BETHISY = add("BETHISY", 12);
		BIG_GULP = add("BIG_GULP", 13);
		WEDDING_CAKE = add("WEDDING_CAKE", 15);
		UPSIDE_DOWN_WEDDING_CAKE = add("UPSIDE_DOWN_WEDDING_CAKE", 16);
		PYTHAGOREAN = add("PYTHAGOREAN", 17);
		SCHNEIDER = add("SCHNEIDER", 18);
		ZARLINO = add("ZARLINO", 19);
		FOKKER = add("FOKKER", 20);
		BACH = add("BACH", 21);
		VALLOTTI_YOUNG = add("VALLOTTI_YOUNG", 22);
		VALLOTTI_YOUNG_WERCKMEISTER_III = add("VALLOTTI_YOUNG_WERCKMEISTER_III", 23);
		CUSTOM = add("CUSTOM", Tuning.TUNING_CUSTOM);
		initializeClass(Temperament.class, ValueSets.class);
	}

	private Temperament(String id, int number) {
		super(id, id, number);
		this.number = number;
	}

	private static Temperament add(String id, int number) {
		Temperament result = new Temperament(id, number);
		values.put(result.getId(), result);
		return result;
	}

	/** The unmodifiable list of all values, in the order of {@link #getNumber()}. */
	public static List<Temperament> getValues() {
		return Collections.unmodifiableList(new ArrayList<Temperament>(values.values()));
	}

	/** The instance of that id, or {@code null}. */
	public static Temperament getInstance(String id) {
		if (id == null) {
			throw new IllegalArgumentException("Parameter 'id' must not be null.");
		}
		return values.get(id);
	}

	/** The instance of that {@link #getNumber()}, or {@code null}. */
	public static Temperament getInstance(int number) {
		for (Temperament value : values.values()) {
			if (value.number == number) {
				return value;
			}
		}
		return null;
	}

	/**
	 * The instance of that number, taking any number an ini file can carry:
	 * a number &gt;= {@link Tuning#TUNING_CUSTOM} is {@link #CUSTOM} - C++'s
	 * own semantic ("any value that is not assigned defaults to custom") -
	 * and a negative number is {@link #EQUAL}, a deliberate guard where C++
	 * reads its preset table out of bounds. A retired preset number
	 * (plan 59) falls back to {@link #EQUAL} too.
	 */
	public static Temperament getInstanceWithFallback(int number) {
		if (number >= Tuning.TUNING_CUSTOM) {
			return CUSTOM;
		}
		if (number < 0) {
			return EQUAL;
		}
		Temperament result = getInstance(number);
		return result != null ? result : EQUAL; // a retired preset (plan 59)
	}

	/** The stored value: the ini file's {@code TEMPERAMENT} line, the C++ combo index, the {@code Tuning.TEMPERAMENT_PRESETS} row for 1-28. */
	public int getNumber() {
		return number;
	}

	/** Whether this is {@link #CUSTOM}, the only temperament played from the {@link TuningCents}. */
	public boolean isCustom() {
		return this == CUSTOM;
	}
}
