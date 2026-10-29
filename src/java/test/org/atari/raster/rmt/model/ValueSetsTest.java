package org.atari.raster.rmt.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

	/** Only Patch 16 reads its frequency tables from the $B000 page the tuning system writes, so it is the only version the tuning reaches. */
	@Test
	void onlyPatch16SupportsTuning() {
		assertTrue(TrackerDriverVersion.PATCH16.isTuningSupported());
		for (TrackerDriverVersion version : TrackerDriverVersion.getValues()) {
			if (version != TrackerDriverVersion.PATCH16) {
				assertFalse(version.isTuningSupported(), version.getId() + " keeps its tables inside the player");
			}
		}
	}

	@Test
	void theValuesAreSortedForTheComboBoxByTheirSortKey() {
		List<AssemblerFormat> values = ValueSet.getValues(AssemblerFormat.class); // what ValueSetField(Class) shows
		assertEquals(List.of(AssemblerFormat.ATASM, AssemblerFormat.XASM), values);
	}

	/**
	 * The keyboard layouts are shown QWERTY, QWERTZ, AZERTY - the sort key, not
	 * the number (QWERTZ is 2 and AZERTY is 1, the values C++ and rmt.ini use).
	 * The sort key is {@code ValueSet}'s third constructor argument; passing
	 * something else there leaves the field in the number's order, silently.
	 */
	@Test
	void theKeyboardLayoutsAreShownInTheirOwnOrderNotTheNumbers() {
		List<KeyboardLayout> shown = List.of(KeyboardLayout.QWERTY, KeyboardLayout.QWERTZ, KeyboardLayout.AZERTY);
		assertEquals(shown, ValueSet.getValues(KeyboardLayout.class), "what the Options dialog's field shows");
		assertEquals(shown, KeyboardLayout.getValues(), "and the order the note key document is written in");
		assertEquals(0, KeyboardLayout.QWERTY.getNumber());
		assertEquals(1, KeyboardLayout.AZERTY.getNumber());
		assertEquals(2, KeyboardLayout.QWERTZ.getNumber());
	}

	@Test
	void theKeyboardLayoutsHaveTheirTexts() {
		assertTextsAreLoaded(KeyboardLayout.getValues());
		assertEquals("QWERTZ Layout", KeyboardLayout.QWERTZ.getText());
	}

	/** The two assembler keywords that used to be picked by a comparison at four sites. */
	@Test
	void eachAssemblerFormatCarriesItsDirectives() {
		assertEquals("* = ", AssemblerFormat.ATASM.getOriginDirective());
		assertEquals("org ", AssemblerFormat.XASM.getOriginDirective());
		assertEquals("=", AssemblerFormat.ATASM.getEqualDirective());
		assertEquals("equ", AssemblerFormat.XASM.getEqualDirective());
	}

	/** The 30 entries of the C++ IDC_TEMPERAMENT combo (Rmt.rc's DLGINIT), number = combo index = ini value. */
	@Test
	void theTemperamentsHaveTheirTextsAndNumbers() {
		List<Temperament> all = Temperament.getValues();
		assertTextsAreLoaded(all);
		assertEquals(23, all.size()); // 30 before plan 59 retired seven presets
		for (int i = 1; i < all.size(); i++) {
			assertTrue(all.get(i - 1).getNumber() < all.get(i).getNumber(), "getValues() keeps the number order");
		}
		assertEquals("Equal Temperament (Default)", Temperament.EQUAL.getText());
		assertNull(Temperament.getInstance(5), "Egal a Quintes Justes is retired (plan 59)");
		assertEquals(Temperament.EQUAL, Temperament.getInstanceWithFallback(5));
		assertEquals("Custom Temperament in Cents", Temperament.CUSTOM.getText());
		assertSame(Temperament.WERCKMEISTER_III, Temperament.getInstance(4));
		assertSame(Temperament.BACH, Temperament.getInstance("BACH"));
		assertTrue(Temperament.CUSTOM.isCustom());
		assertFalse(Temperament.EQUAL.isCustom());
	}

	/** Plan 59 step 4: each preset carries its twelve tonic-based multipliers; the equal temperament (computed) and the custom temperament (played from the cents) carry none. */
	@Test
	void theTemperamentsCarryTheirMultipliers() {
		for (Temperament temperament : Temperament.getValues()) {
			if (temperament.isCustom()) {
				assertThrows(IllegalStateException.class, () -> temperament.getMultiplier(0));
				continue;
			}
			assertEquals(1.0, temperament.getMultiplier(0), "the tonic of " + temperament.getId());
			for (int note = 1; note < Tuning.NOTES_PER_OCTAVE; note++) {
				double multiplier = temperament.getMultiplier(note);
				assertTrue(multiplier > 1 && multiplier < 2, temperament.getId() + " note " + note + ": " + multiplier);
			}
		}
		// A golden spot check: Young No. 1's minor second, per young1.scl (plan 59 step 3)
		assertEquals(1.055709, Temperament.YOUNG_1799_NO1.getMultiplier(1));
		// Equal temperament's are computed, bit-exactly as the A4 derivation used to compute them inline
		assertEquals(Math.pow(2, 7.0 / Tuning.NOTES_PER_OCTAVE), Temperament.EQUAL.getMultiplier(7));
	}

	/** The cents the tuning dialog shows for the selected temperament, CENTS_PER_OCTAVE * log2(multiplier) - 100 per semitone for equal temperament; the custom cents are the user's own. */
	@Test
	void theTemperamentsGiveTheirCents() {
		for (int note = 0; note < Tuning.NOTES_PER_OCTAVE; note++) {
			assertEquals((double) note * Tuning.CENTS_PER_OCTAVE / Tuning.NOTES_PER_OCTAVE, Temperament.EQUAL.getCents(note), 1e-9);
		}
		assertEquals(0.0, Temperament.YOUNG_1799_NO1.getCents(0));
		assertEquals(Tuning.CENTS_PER_OCTAVE * Math.log(1.055709) / Math.log(2), Temperament.YOUNG_1799_NO1.getCents(1), 1e-12);
		assertThrows(IllegalStateException.class, () -> Temperament.CUSTOM.getCents(1));
	}

	/** A number nobody owns maps per C++'s semantic (&gt;= 29 is custom) or to a guard value. */
	@Test
	void strayTemperamentAndTonicNumbersFallBack() {
		assertSame(Temperament.CUSTOM, Temperament.getInstanceWithFallback(29));
		assertSame(Temperament.CUSTOM, Temperament.getInstanceWithFallback(35));
		assertSame(Temperament.EQUAL, Temperament.getInstanceWithFallback(-1));
		assertSame(Temperament.BAILEY, Temperament.getInstanceWithFallback(10));
		assertNull(Temperament.getInstance(30));
		assertSame(Tonic.A, Tonic.getInstanceWithFallback(99));
		assertSame(Tonic.A, Tonic.getInstanceWithFallback(-1));
		assertSame(Tonic.C_SHARP, Tonic.getInstanceWithFallback(11));
	}

	/** The 12 entries of the C++ IDC_TONIC combo, descending from C- (0) to C# (11); the default 3 is A-. */
	@Test
	void theTonicsHaveTheirTextsAndNumbers() {
		List<Tonic> all = Tonic.getValues();
		assertTextsAreLoaded(all);
		assertEquals(12, all.size());
		for (int i = 0; i < all.size(); i++) {
			assertEquals(i, all.get(i).getNumber(), "getValues() keeps the number order");
		}
		assertEquals("C-", Tonic.C.getText());
		assertEquals("A-", Tonic.A.getText());
		assertEquals("C#", Tonic.C_SHARP.getText());
		assertSame(Tonic.A, Tonic.getInstance(3));
		assertSame(Tonic.G_SHARP, Tonic.getInstance("G_SHARP"));
	}
}
