package org.atari.raster.rmt.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.wudsn.tools.base.repository.ValueSet;

/**
 * Ported from the C++ enum class TrackerDriverVersion
 * (TrackerDriverVersion.h): which build of the RMT player routines the
 * emulated Atari runs.
 *
 * <p>A WUDSN Base {@link ValueSet} rather than a Java {@code enum}, so its
 * display texts live in {@code ValueSets.properties} and the Options dialog
 * can show it through a {@code ValueSetField} (see
 * plans/28_VALUE_SETS_PLAN.md). The instances are singletons, so {@code ==}
 * comparisons keep working as they did for the enum.
 *
 * <p>{@link #getNumber()} is C++'s explicit backing value (NONE = 0 ..
 * PATCH_PRINCE_OF_PERSIA = 7), which the enum's {@code ordinal()} used to
 * provide. It is both the {@code rmt.ini} value {@code TRACKERDRIVERVERSION}
 * and the number in the binary's file name
 * ({@code resources/drivers/rmt_driver_v<n>.obx}, which exist for 1 to 7),
 * so these numbers are fixed by the files on disk and by the shared
 * configuration file.
 *
 * <p>{@link #getValues()} keeps the declaration order, i.e. the order of
 * {@link #getNumber()}; {@link #getSelectableValues()} is the subset the
 * Options dialog offers, in the order the C++ combo box adds them - NONE
 * and UNPATCHED_WITH_TUNING are reachable through {@code rmt.ini} and the
 * script's {@code set driver}, but were never offered in the dialog.
 */
public final class TrackerDriverVersion extends ValueSet {

	public static final TrackerDriverVersion NONE;
	public static final TrackerDriverVersion UNPATCHED;
	public static final TrackerDriverVersion UNPATCHED_WITH_TUNING;
	public static final TrackerDriverVersion PATCH3;
	public static final TrackerDriverVersion PATCH6;
	public static final TrackerDriverVersion PATCH8;
	public static final TrackerDriverVersion PATCH16;
	public static final TrackerDriverVersion PATCH_PRINCE_OF_PERSIA;

	// Instances, in declaration order (= the order of getNumber())
	private static final Map<String, TrackerDriverVersion> values;

	// Instance attributes
	private final int number;
	private final boolean selectable;

	static {
		values = new LinkedHashMap<String, TrackerDriverVersion>();
		NONE = add("NONE", 0, false);
		UNPATCHED = add("UNPATCHED", 1, true);
		UNPATCHED_WITH_TUNING = add("UNPATCHED_WITH_TUNING", 2, false);
		PATCH3 = add("PATCH3", 3, true);
		PATCH6 = add("PATCH6", 4, true);
		PATCH8 = add("PATCH8", 5, true);
		PATCH16 = add("PATCH16", 6, true);
		PATCH_PRINCE_OF_PERSIA = add("PATCH_PRINCE_OF_PERSIA", 7, true);
		initializeClass(TrackerDriverVersion.class, ValueSets.class);
	}

	private TrackerDriverVersion(String id, int number, boolean selectable) {
		super(id, id, number);
		this.number = number;
		this.selectable = selectable;
	}

	private static TrackerDriverVersion add(String id, int number, boolean selectable) {
		TrackerDriverVersion result = new TrackerDriverVersion(id, number, selectable);
		values.put(result.getId(), result);
		return result;
	}

	/** The unmodifiable list of all values, in the order of {@link #getNumber()}. */
	public static List<TrackerDriverVersion> getValues() {
		return Collections.unmodifiableList(new ArrayList<TrackerDriverVersion>(values.values()));
	}

	/** The values the Options dialog offers, in the C++ combo box's order. */
	public static List<TrackerDriverVersion> getSelectableValues() {
		List<TrackerDriverVersion> result = new ArrayList<TrackerDriverVersion>();
		for (TrackerDriverVersion value : values.values()) {
			if (value.selectable) {
				result.add(value);
			}
		}
		return Collections.unmodifiableList(result);
	}

	/** The instance of that id, or {@code null}. */
	public static TrackerDriverVersion getInstance(String id) {
		if (id == null) {
			throw new IllegalArgumentException("Parameter 'id' must not be null.");
		}
		return values.get(id);
	}

	/** The instance of that {@link #getNumber()}, or {@code null} - a number outside the range is ignored by the caller, as C++ casts it to an invalid enum value. */
	public static TrackerDriverVersion getInstance(int number) {
		for (TrackerDriverVersion value : values.values()) {
			if (value.number == number) {
				return value;
			}
		}
		return null;
	}

	/** C++'s backing value: the {@code rmt.ini} number and the number in {@code rmt_driver_v<n>.obx}. */
	public int getNumber() {
		return number;
	}

	/** Whether the Options dialog offers this version. */
	public boolean isSelectable() {
		return selectable;
	}
}
