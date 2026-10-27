package org.atari.raster.rmt.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.wudsn.tools.base.repository.ValueSet;

/**
 * The temperament a song's notes are tuned in: equal temperament, one of the
 * 21 presets - whose twelve tonic-based frequency multipliers each instance
 * carries itself (plan 59 step 4; C++ keeps them as the
 * {@code temperament_preset} table in Tuning.h, indexed by the raw number) -
 * or the custom temperament played from the {@link TuningCents}.
 *
 * <p>A WUDSN Base {@link ValueSet} rather than a Java {@code enum}, so its
 * display texts live in {@code ValueSets.properties} and the tuning dialog
 * can show it through a {@code ValueSetField} (see
 * plans/28_VALUE_SETS_PLAN.md and plans/48_TUNING_VALUE_SETS_PLAN.md). C++
 * has no enumeration for it at all - the number is used raw, and the dialog
 * names live in TuningDialog.cpp.
 *
 * <p>{@link #getNumber()} is the stored value: the {@code TEMPERAMENT} line
 * of the ini file and the row of C++'s preset table for 1-28.
 * {@link #getValues()} keeps the number order; the dialog sorts for display
 * (equal pinned first, custom last, the presets alphabetical).
 *
 * <p>The comment above each preset is the verbatim description line of its
 * Scala archive source file (huygens-fokker.org), against which the
 * multipliers were verified numerically (max difference below 1e-6, the
 * 6-decimal rounding; plan 59 step 3): the dialog shows the proper names of
 * {@code ValueSets.properties} instead.
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
	/** The preset's twelve tonic-based frequency multipliers ({@code multipliers[0]} = 1, the unison), or {@code null} for {@link #EQUAL} and {@link #CUSTOM}. */
	private final double[] multipliers;

	static {
		values = new LinkedHashMap<String, Temperament>();
		// Equal temperament is computed (2^(n/12)), not tabulated, so the
		// generated tables stay bit-exact; the custom temperament is played
		// from the runtime TuningCents. Neither carries multipliers.
		EQUAL = add("EQUAL", Tuning.NO_TEMPERAMENT);
		// young1.scl: Thomas Young well temperament no.1 (1800), 1/12 and 3/16 synt. comma
		YOUNG_1799_NO1 = add("YOUNG_1799_NO1", 1, 1, 1.055709, 1.119770, 1.187690, 1.253887, 1.334739, 1.407637, 1.496513, 1.583581, 1.675715, 1.781546, 1.878851);
		// young2.scl: Thomas Young well temperament no.2 (1799)
		YOUNG_1799_NO2 = add("YOUNG_1799_NO2", 2, 1, 1.055880, 1.119929, 1.187865, 1.254242, 1.334839, 1.407840, 1.496616, 1.583819, 1.676105, 1.781797, 1.879240);
		// young.scl: Thomas Young well temperament (1807), also Luigi Malerbi nr.2 (1794)
		YOUNG_1807 = add("YOUNG_1807", 3, 1, 1.053497, 1.119929, 1.185185, 1.254242, 1.333333, 1.404663, 1.496616, 1.580246, 1.676105, 1.777777, 1.877119);
		// werck3.scl: Andreas Werckmeister's temperament III (the most famous one, 1681)
		WERCKMEISTER_III = add("WERCKMEISTER_III", 4, 1, 1.053497, 1.117403, 1.185185, 1.252827, 1.333333, 1.404663, 1.494927, 1.580246, 1.670436, 1.777777, 1.879240);
		// 5, 14 and 24-28 are retired (plan 59): Egal a Quintes Justes,
		// Bohlen, pentatonic, Aeolic, Bapare, Yaswa and 19-EDO do not fit the
		// 12-note, 2:1-octave model (the last five are scales or
		// stretched-octave sets, not temperaments). Their numbers stay
		// reserved; an ini carrying one falls back to EQUAL via
		// getInstanceWithFallback(). Their rows live on only in C++'s table.
		// alembert-rousseau.scl: d'Alembert and Rousseau tempérament ordinaire (1752/1767)
		ALEMBERT_ROUSSEAU = add("ALEMBERT_ROUSSEAU", 6, 1, 1.051120, 1.118034, 1.181176, 1.250000, 1.331828, 1.403077, 1.495348, 1.574901, 1.671850, 1.773766, 1.872883);
		// aron-neidhardt.scl: Aron-Neidhardt equal beating well temperament
		ARON_NEIDHARDT = add("ARON_NEIDHARDT", 7, 1, 1.053497, 1.118144, 1.185185, 1.250031, 1.333333, 1.404663, 1.496112, 1.580246, 1.672002, 1.777777, 1.872885);
		// atomschis.scl: Atom Schisma Scale
		ATOM_SCHISMA = add("ATOM_SCHISMA", 8, 1, 1.059459, 1.122463, 1.189204, 1.259924, 1.334838, 1.414207, 1.498308, 1.587396, 1.681796, 1.781794, 1.887755);
		// b12_17.scl: 12-tET approximation with minimal order 17 beats
		TET12_MINIMAL_ORDER_17 = add("TET12_MINIMAL_ORDER_17", 9, 1, 1.058823, 1.125000, 1.187500, 1.250000, 1.333333, 1.416666, 1.500000, 1.588235, 1.666666, 1.777777, 1.888888);
		// bailey_well2.scl: Paul Bailey's modern well temperament (2002)
		BAILEY = add("BAILEY", 10, 1, 1.054892, 1.119671, 1.186774, 1.254242, 1.335183, 1.406531, 1.498302, 1.582329, 1.676250, 1.780202, 1.881407);
		// barnes.scl: John Barnes' temperament (1977) made after analysis of Wohltemperierte Klavier, 1/6 P
		BARNES = add("BARNES", 11, 1, 1.055880, 1.119929, 1.187865, 1.254242, 1.336348, 1.407840, 1.496616, 1.583819, 1.676105, 1.781797, 1.881364);
		// bethizy.scl: Jean Laurent de Béthizy temperament ordinaire (1754). See Pierre-Yves Asselin: Musique et temperament
		BETHISY = add("BETHISY", 12, 1, 1.051418, 1.118034, 1.181509, 1.250000, 1.331953, 1.403475, 1.495348, 1.575346, 1.671850, 1.774101, 1.872884);
		// biggulp.scl: Big Gulp
		BIG_GULP = add("BIG_GULP", 13, 1, 1.031250, 1.125000, 1.166666, 1.250000, 1.312500, 1.375000, 1.500000, 1.546875, 1.687500, 1.750000, 1.875000);
		// pyramid.scl: This scale may also be called the "Wedding Cake"
		WEDDING_CAKE = add("WEDDING_CAKE", 15, 1, 1.125000, 1.171875, 1.250000, 1.333333, 1.406250, 1.500000, 1.562500, 1.666666, 1.687500, 1.777777, 1.875000);
		// pyramid_down.scl: Upside-Down Wedding Cake (divorce cake)
		UPSIDE_DOWN_WEDDING_CAKE = add("UPSIDE_DOWN_WEDDING_CAKE", 16, 1, 1.066666, 1.125000, 1.200000, 1.280000, 1.333333, 1.500000, 1.600000, 1.687500, 1.777777, 1.800000, 1.920000);
		// pyth_12.scl: 12-tone Pythagorean scale
		PYTHAGOREAN = add("PYTHAGOREAN", 17, 1, 1.067871, 1.125000, 1.185185, 1.265625, 1.333333, 1.423828, 1.500000, 1.601806, 1.687500, 1.777777, 1.898437);
		// schneider_log.scl: Robert Schneider, scale of log(4) .. log(16), 1/1=264Hz
		SCHNEIDER = add("SCHNEIDER", 18, 1, 1.160964, 1.292481, 1.403677, 1.500000, 1.584962, 1.660964, 1.729715, 1.792481, 1.850219, 1.903677, 1.953445);
		// schulter_zarte84n.scl: Zarlino temperament extraordinaire, 1024-tET mapping
		ZARLINO = add("ZARLINO", 19, 1, 1.041450, 1.116652, 1.180385, 1.247756, 1.337855, 1.393309, 1.494930, 1.567469, 1.669316, 1.777781, 1.865308);
		// fokker_12.scl: Fokker's 7-limit 12-tone just scale
		FOKKER = add("FOKKER", 20, 1, 1.071428, 1.125000, 1.166666, 1.250000, 1.333333, 1.406250, 1.500000, 1.607142, 1.666666, 1.750000, 1.875000);
		// sparschuh-squiggle_clavichord.scl: Bach temperament, a'=400 Hz
		BACH = add("BACH", 21, 1, 1.052192, 1.118997, 1.183716, 1.250521, 1.334029, 1.402922, 1.498956, 1.578288, 1.670146, 1.776618, 1.874739);
		// vallotti.scl: Vallotti & Young scale (Vallotti version) also known as Tartini-Vallotti (1754)
		VALLOTTI_YOUNG = add("VALLOTTI_YOUNG", 22, 1, 1.055880, 1.119929, 1.187865, 1.254242, 1.336348, 1.407840, 1.496616, 1.583819, 1.676105, 1.781797, 1.877119);
		// val-werck.scl: Vallotti-Young and Werckmeister III, 10 cents 5-limit lesfip scale
		VALLOTTI_YOUNG_WERCKMEISTER_III = add("VALLOTTI_YOUNG_WERCKMEISTER_III", 23, 1, 1.051637, 1.117298, 1.187060, 1.248356, 1.336846, 1.399836, 1.495457, 1.580099, 1.669531, 1.783574, 1.867613);
		CUSTOM = add("CUSTOM", Tuning.TUNING_CUSTOM);
		initializeClass(Temperament.class, ValueSets.class);
	}

	private Temperament(String id, int number, double[] multipliers) {
		super(id, id, number);
		this.number = number;
		this.multipliers = multipliers;
	}

	/** A value without multipliers: the equal temperament (computed) and the custom temperament (played from the cents). */
	private static Temperament add(String id, int number) {
		Temperament result = new Temperament(id, number, null);
		values.put(result.getId(), result);
		return result;
	}

	/** A preset: the twelve tonic-based frequency multipliers, the unison 1 first; the octave is fixed at 2:1 and not stored. */
	private static Temperament add(String id, int number, double... multipliers) {
		if (multipliers.length != TuningCents.COUNT || multipliers[0] != 1) {
			throw new IllegalArgumentException("Parameter 'multipliers' must hold the 12 tonic-based values starting with the unison 1.");
		}
		Temperament result = new Temperament(id, number, multipliers);
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

	/**
	 * The preset's frequency multiplier for the note {@code note} (0-11)
	 * semitones above the tonic; 1 for the unison, below 2 otherwise - the
	 * octave is fixed at 2:1. Only the 21 presets carry multipliers:
	 * {@link #EQUAL} is computed and {@link #CUSTOM} is played from the
	 * cents, so asking either is a caller error.
	 */
	public double getMultiplier(int note) {
		if (multipliers == null) {
			throw new IllegalStateException("The temperament '" + getId() + "' has no multipliers.");
		}
		return multipliers[note];
	}
}
