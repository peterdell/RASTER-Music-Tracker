package org.atari.raster.rmt.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.wudsn.tools.base.repository.ValueSet;

/**
 * Ported from the C++ enum AssemblerFormat (AssemblerTypes.h): the assembler
 * the .asm exports are written for.
 *
 * <p>A WUDSN Base {@link ValueSet} rather than a Java {@code enum}, so its
 * display texts live in {@code ValueSets.properties} and the export dialogs
 * can show it through a {@code ValueSetField} (see
 * plans/28_VALUE_SETS_PLAN.md). The instances are singletons, so
 * {@code ==} comparisons keep working as they did for the enum.
 *
 * <p>Each instance carries the two assembler keywords that differ between
 * the two ("directives"): {@link #getOriginDirective()} sets the assembly
 * address and {@link #getEqualDirective()} defines a symbol. The four
 * places that used to ask "which format is this?" only to pick one of those
 * two spellings now read them from the instance.
 */
public final class AssemblerFormat extends ValueSet {

	public static final AssemblerFormat ATASM;
	public static final AssemblerFormat XASM;

	// Instances
	private static final Map<String, AssemblerFormat> values;

	// Instance attributes
	private final String originDirective;
	private final String equalDirective;

	static {
		values = new TreeMap<String, AssemblerFormat>();
		ATASM = add("ATASM", 0, "* = ", "=");
		XASM = add("XASM", 1, "org ", "equ");
		initializeClass(AssemblerFormat.class, ValueSets.class);
	}

	private AssemblerFormat(String id, int sortKey, String originDirective, String equalDirective) {
		super(id, id, sortKey);
		this.originDirective = originDirective;
		this.equalDirective = equalDirective;
	}

	private static AssemblerFormat add(String id, int sortKey, String originDirective, String equalDirective) {
		AssemblerFormat result = new AssemblerFormat(id, sortKey, originDirective, equalDirective);
		values.put(result.getId(), result);
		return result;
	}

	/** The unmodifiable list of all values; the display order is the sort key ({@code ValueSetField} sorts by it). */
	public static List<AssemblerFormat> getValues() {
		return Collections.unmodifiableList(new ArrayList<AssemblerFormat>(values.values()));
	}

	/** The instance of that id ({@code "ATASM"}/{@code "XASM"}), or {@code null}. */
	public static AssemblerFormat getInstance(String id) {
		if (id == null) {
			throw new IllegalArgumentException("Parameter 'id' must not be null.");
		}
		return values.get(id);
	}

	/** The directive that sets the assembly address, including its trailing blank: {@code "* = "} (Atasm) or {@code "org "} (Xasm). */
	public String getOriginDirective() {
		return originDirective;
	}

	/** The directive that defines a symbol: {@code "="} (Atasm) or {@code "equ"} (Xasm). */
	public String getEqualDirective() {
		return equalDirective;
	}
}
