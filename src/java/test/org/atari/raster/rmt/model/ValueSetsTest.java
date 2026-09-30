package org.atari.raster.rmt.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.wudsn.tools.base.repository.ValueSet;

/**
 * The package's value sets and their texts from {@code ValueSets.properties}
 * (see {@link ValueSets} and plans/28_VALUE_SETS_PLAN.md). A missing
 * properties key is only logged by {@code NLS}, never thrown, so the texts
 * are asserted here - that is what makes the properties file a checked
 * part of the build.
 */
class ValueSetsTest {

	/** Every instance has a text of its own from the properties file, not the id it starts with. */
	private static <T extends ValueSet> void assertTextsAreLoaded(List<T> values) {
		assertFalse(values.isEmpty());
		for (T value : values) {
			assertFalse(value.getText().isBlank(), value.getId() + " has no text");
			assertNotEquals(value.getId(), value.getText(), value.getId() + " still shows its id - is the key missing in ValueSets.properties?");
			assertEquals(value.getText(), value.toString(), "a combo box shows toString()");
		}
	}

	@Test
	void theAssemblerFormatsHaveTheirTexts() {
		assertTextsAreLoaded(AssemblerFormat.getValues());
		assertEquals("Atasm", AssemblerFormat.ATASM.getText());
		assertEquals("Xasm", AssemblerFormat.XASM.getText());
	}

	@Test
	void theAssemblerFormatsAreSingletonsFoundByTheirId() {
		assertSame(AssemblerFormat.ATASM, AssemblerFormat.getInstance("ATASM"));
		assertSame(AssemblerFormat.XASM, AssemblerFormat.getInstance("XASM"));
		assertNull(AssemblerFormat.getInstance("MADS"));
		// the == comparisons all over AsmFileBuilder/AsmFileExporter rely on this
		assertTrue(AssemblerFormat.getInstance("ATASM") == AssemblerFormat.ATASM);
	}

	@Test
	void theTrackerDriverVersionsHaveTheirTexts() {
		assertTextsAreLoaded(TrackerDriverVersion.getValues());
		assertEquals("RMT 1.28 Patch 16 by VinsCool", TrackerDriverVersion.PATCH16.getText());
		assertEquals("None", TrackerDriverVersion.NONE.getText());
	}

	/** The number is C++'s enum value: the rmt.ini value and the number in rmt_driver_v&lt;n&gt;.obx. */
	@Test
	void everyTrackerDriverVersionKeepsItsNumber() {
		assertEquals(0, TrackerDriverVersion.NONE.getNumber());
		assertEquals(1, TrackerDriverVersion.UNPATCHED.getNumber());
		assertEquals(2, TrackerDriverVersion.UNPATCHED_WITH_TUNING.getNumber());
		assertEquals(6, TrackerDriverVersion.PATCH16.getNumber());
		assertEquals(7, TrackerDriverVersion.PATCH_PRINCE_OF_PERSIA.getNumber());
		assertSame(TrackerDriverVersion.PATCH16, TrackerDriverVersion.getInstance(6));
		assertNull(TrackerDriverVersion.getInstance(99), "an unknown number is ignored by RmtConfig");
		assertSame(TrackerDriverVersion.PATCH3, TrackerDriverVersion.getInstance("PATCH3"));
	}

	/** getValues() keeps the declaration order - the script's error message lists the names in it. */
	@Test
	void theTrackerDriverVersionsAreListedByNumberAndTheDialogShowsSixOfThem() {
		List<TrackerDriverVersion> all = TrackerDriverVersion.getValues();
		assertEquals(8, all.size());
		for (int i = 0; i < all.size(); i++) {
			assertEquals(i, all.get(i).getNumber());
		}
		assertEquals(List.of(TrackerDriverVersion.UNPATCHED, TrackerDriverVersion.PATCH3, TrackerDriverVersion.PATCH6, TrackerDriverVersion.PATCH8, TrackerDriverVersion.PATCH16,
				TrackerDriverVersion.PATCH_PRINCE_OF_PERSIA), TrackerDriverVersion.getSelectableValues());
		assertFalse(TrackerDriverVersion.NONE.isSelectable());
		assertFalse(TrackerDriverVersion.UNPATCHED_WITH_TUNING.isSelectable());
	}

	@Test
	void theValuesAreSortedForTheComboBoxByTheirSortKey() {
		List<AssemblerFormat> values = ValueSet.getValues(AssemblerFormat.class); // what ValueSetField(Class) shows
		assertEquals(List.of(AssemblerFormat.ATASM, AssemblerFormat.XASM), values);
	}

	/** The two assembler keywords that used to be picked by a comparison at four sites. */
	@Test
	void eachAssemblerFormatCarriesItsDirectives() {
		assertEquals("* = ", AssemblerFormat.ATASM.getOriginDirective());
		assertEquals("org ", AssemblerFormat.XASM.getOriginDirective());
		assertEquals("=", AssemblerFormat.ATASM.getEqualDirective());
		assertEquals("equ", AssemblerFormat.XASM.getEqualDirective());
	}
}
