package org.atari.raster.rmt.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Component;
import java.awt.Container;
import java.awt.GraphicsEnvironment;
import java.util.ArrayList;
import java.util.List;

import javax.swing.AbstractButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JTextField;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.atari.raster.rmt.model.IntegerFraction;
import org.atari.raster.rmt.model.KeyboardLayout;
import org.atari.raster.rmt.model.Tonic;
import org.atari.raster.rmt.model.Temperament;
import org.atari.raster.rmt.model.TrackerDriverVersion;
import org.atari.raster.rmt.model.Tuning;

/**
 * The three B6 dialogs' data binding and validation, driven through their
 * widgets without showing them (a display is still needed to construct a
 * JDialog - skipped headless). The dialogs' buttons are found by their
 * text, so the tests read like the C++ dialog resources.
 */
class OptionsDialogsTest {

	private RmtSession session;

	@BeforeEach
	void setUp() {
		Assumptions.assumeFalse(GraphicsEnvironment.isHeadless(), "needs a display to build dialogs");
		session = new RmtSession();
	}

	private static <T extends Component> List<T> find(Container root, Class<T> type) {
		List<T> result = new ArrayList<>();
		collect(root, type, result);
		return result;
	}

	private static <T extends Component> void collect(Container root, Class<T> type, List<T> into) {
		for (Component c : root.getComponents()) {
			if (type.isInstance(c)) {
				into.add(type.cast(c));
			}
			if (c instanceof Container container) {
				collect(container, type, into);
			}
		}
	}

	private static AbstractButton button(Container root, String text) {
		for (AbstractButton b : find(root, AbstractButton.class)) {
			if (text.equals(b.getText())) {
				return b;
			}
		}
		throw new AssertionError("no button " + text);
	}

	@Test
	void optionsDialogShowsTheValuesAndReadsThemBack() {
		OptionsValues values = OptionsValues.from(session);
		values.scalingPercentage = 150;
		values.keyboardLayout = KeyboardLayout.AZERTY;
		values.midiTouchResponse = false;
		OptionsDialog dialog = new OptionsDialog(null, session, values);
		dialog.dataToUi();

		List<JCheckBox> boxes = find(dialog, JCheckBox.class);
		assertEquals(12, boxes.size()); // the NTSC box is the song's, in the Module Properties dialog since plan 44
		List<JComboBox> combos = find(dialog, JComboBox.class);
		assertEquals(2, combos.size()); // the driver version combo went with it
		assertEquals(KeyboardLayout.AZERTY, combos.get(0).getSelectedItem()); // a value set field holds the instance
		assertEquals("AZERTY Layout", combos.get(0).getSelectedItem().toString());
		assertEquals("None", combos.get(1).getSelectedItem());
		List<JTextField> fields = find(dialog, JTextField.class);
		assertEquals(4, fields.size()); // scaling, primary, secondary, MIDI volume offset
		assertEquals("150", fields.get(0).getText());
		assertFalse(fields.get(3).isEnabled()); // OnMidiTouchResponseClicked: no touch response, no offset
		assertEquals("Paths...", button(dialog, "Paths...").getText());

		fields.get(0).setText("300");
		fields.get(1).setText("16");
		((JCheckBox) button(dialog, "Touch response")).doClick();
		assertTrue(fields.get(3).isEnabled());
		dialog.dataFromUi();
		assertEquals(300, values.scalingPercentage);
		assertEquals(16, values.trackLinePrimaryHighlight);
		assertTrue(values.midiTouchResponse);
		assertEquals("", values.midiDevice);
		assertTrue(dialog.validateOK());

		fields.get(0).setText("99"); // DDV_MinMaxInt 100..300
		assertFalse(dialog.validateOK());
		fields.get(0).setText("100");
		fields.get(2).setText("257"); // 2..256
		assertFalse(dialog.validateOK());
		fields.get(2).setText("x");
		assertFalse(dialog.validateOK());
	}

	/** The Module Properties dialog (plan 44): the two controls that were the Options dialog's "Module Defaults" group, and the Tuning... button that was in its button bar. */
	@Test
	void modulePropertiesDialogShowsTheValuesAndReadsThemBack() {
		ModulePropertiesValues values = ModulePropertiesValues.from(session);
		values.ntsc = false;
		values.trackerDriverVersion = TrackerDriverVersion.PATCH8;
		ModulePropertiesDialog dialog = new ModulePropertiesDialog(null, session, values);
		dialog.dataToUi();

		List<JCheckBox> boxes = find(dialog, JCheckBox.class);
		assertEquals(1, boxes.size());
		List<JComboBox> combos = find(dialog, JComboBox.class);
		assertEquals(1, combos.size());
		assertEquals(TrackerDriverVersion.PATCH8, combos.get(0).getSelectedItem()); // a value set field holds the instance; its text is what the box shows
		assertEquals("RMT 1.28 Patch 8 by Analmux", combos.get(0).getSelectedItem().toString());
		assertEquals("Tuning...", button(dialog, "Tuning...").getText());
		// The tuning only reaches Patch 16's tables, so the button follows the selected version
		assertFalse(button(dialog, "Tuning...").isEnabled(), "Patch 8 plays its own tables");
		combos.get(0).setSelectedItem(TrackerDriverVersion.PATCH16);
		assertTrue(button(dialog, "Tuning...").isEnabled());

		((JCheckBox) button(dialog, "Use NTSC System Speed (60Hz)")).setSelected(true);
		combos.get(0).setSelectedIndex(0);
		dialog.dataFromUi();
		assertTrue(values.ntsc);
		assertEquals(TrackerDriverVersion.UNPATCHED, values.trackerDriverVersion);
		assertFalse(session.song.isNTSC()); // not applied here: RmtCommands.applyModuleProperties does that on OK
	}

	@Test
	void tuningDialogRoundTripsAndOnlyTestOrOKReachTheSession() {
		session.song.getTuningSettings().a4Frequency = 432;
		session.song.getTuningSettings().temperament = Temperament.CUSTOM;
		session.song.getTuningSettings().cents.values[11] = 1109.775004326937; // 243/128 in cents
		TuningDialog dialog = new TuningDialog(null, session);
		dialog.dataToUi();

		List<JTextField> fields = find(dialog, JTextField.class);
		assertEquals(1 + 12, fields.size()); // the A4 frequency and the 12 cents (plan 59)
		assertEquals("432", fields.get(0).getText());
		List<JComboBox> combos = find(dialog, JComboBox.class);
		assertEquals(Tonic.A, combos.get(0).getSelectedItem());
		assertEquals(Temperament.CUSTOM, combos.get(1).getSelectedItem());
		assertEquals("Custom Temperament in Cents", combos.get(1).getSelectedItem().toString()); // a combo box shows toString()
		assertEquals("0", fields.get(1).getText()); // the tonic, fixed at 0 cents
		assertFalse(fields.get(1).isEnabled(), "the tonic stays 0 cents");
		assertTrue(fields.get(2).isEnabled(), "custom enables the other cents");
		assertEquals("1109.775004326937", fields.get(1 + 11).getText()); // Major 7th

		fields.get(0).setText("440");
		fields.get(1 + 11).setText("1100.5");
		dialog.dataFromUi();
		assertEquals(440, dialog.settings.a4Frequency);
		assertEquals(1100.5, dialog.settings.cents.values[11]);
		assertEquals(432, session.song.getTuningSettings().a4Frequency); // not applied yet

		button(dialog, "Test").doClick();
		assertEquals(440, session.song.getTuningSettings().a4Frequency);
		assertEquals(1100.5, session.song.getTuningSettings().cents.values[11]);

		button(dialog, "Reset").doClick();
		assertEquals(432, session.song.getTuningSettings().a4Frequency); // the backup
		assertEquals(1109.775004326937, session.song.getTuningSettings().cents.values[11]);
		assertEquals("440", fields.get(0).getText()); // the fields keep what was typed, as in C++

		fields.get(0).setText("5"); // DDV_MinMaxDouble 6.875..7040
		assertFalse(dialog.validateOK());
		fields.get(0).setText("7040");
		fields.get(1 + 11).setText("1300"); // cents are 0-1200
		assertFalse(dialog.validateOK());
		fields.get(1 + 11).setText("1100");
		assertTrue(dialog.validateOK());
	}

	@Test
	void tuningDialogEnablesTheRatioFieldsOnlyForTheCustomTemperament() {
		session.song.getTuningSettings().temperament = Temperament.EQUAL;
		TuningDialog dialog = new TuningDialog(null, session);
		dialog.dataToUi();

		List<JTextField> fields = find(dialog, JTextField.class);
		assertTrue(fields.get(0).isEnabled()); // the A4 frequency is independent of the temperament
		for (int i = 1; i < fields.size(); i++) {
			assertFalse(fields.get(i).isEnabled(), "a preset plays its own table, the cents are not used");
		}

		List<JComboBox> combos = find(dialog, JComboBox.class);
		combos.get(1).setSelectedItem(Temperament.CUSTOM);
		assertFalse(fields.get(1).isEnabled(), "the tonic stays fixed at 0 cents");
		for (int i = 2; i < fields.size(); i++) {
			assertTrue(fields.get(i).isEnabled(), "the custom temperament is played from the cents");
		}

		combos.get(1).setSelectedItem(Temperament.WERCKMEISTER_III);
		for (int i = 1; i < fields.size(); i++) {
			assertFalse(fields.get(i).isEnabled());
		}
	}

	@Test
	void tuningDialogShowsTheCentsOfTheSelectedTemperament() {
		session.song.getTuningSettings().temperament = Temperament.CUSTOM;
		session.song.getTuningSettings().cents.values[4] = 386.3137138648348; // 5/4 in cents
		TuningDialog dialog = new TuningDialog(null, session);
		dialog.dataToUi();
		List<JTextField> fields = find(dialog, JTextField.class);
		List<JComboBox> combos = find(dialog, JComboBox.class);
		assertEquals("386.3137138648348", fields.get(1 + 4).getText()); // the custom temperament shows the stored cents
		fields.get(1 + 7).setText("701.955"); // typed for the custom temperament

		combos.get(1).setSelectedItem(Temperament.EQUAL);
		for (int i = 0; i < Tuning.NOTES_PER_OCTAVE; i++) {
			assertEquals(String.valueOf(i * Tuning.CENTS_PER_OCTAVE / Tuning.NOTES_PER_OCTAVE), fields.get(1 + i).getText(), "equal temperament note " + i);
			assertFalse(fields.get(1 + i).isEnabled());
		}

		combos.get(1).setSelectedItem(Temperament.YOUNG_1799_NO1);
		assertEquals(TuningDialog.formatCents(Temperament.YOUNG_1799_NO1.getCents(1)), fields.get(2).getText());
		assertTrue(fields.get(2).getText().matches("\\d+\\.\\d{1,3}"), "rounded to 3 decimals: " + fields.get(2).getText());
		assertFalse(fields.get(2).isEnabled(), "a preset's cents are read-only");

		// OK with a preset selected: the custom cents survive, including what was typed
		dialog.dataFromUi();
		assertEquals(Temperament.YOUNG_1799_NO1, dialog.settings.temperament);
		assertEquals(386.3137138648348, dialog.settings.cents.values[4]);
		assertEquals(701.955, dialog.settings.cents.values[7]);

		combos.get(1).setSelectedItem(Temperament.CUSTOM);
		assertEquals("701.955", fields.get(1 + 7).getText()); // what was typed comes back
		assertTrue(fields.get(1 + 7).isEnabled());
	}

	@Test
	void tuningDialogRejectsAnInvalidCustomValueHiddenBehindAPreset() {
		session.song.getTuningSettings().temperament = Temperament.CUSTOM;
		TuningDialog dialog = new TuningDialog(null, session);
		dialog.dataToUi();
		List<JTextField> fields = find(dialog, JTextField.class);
		fields.get(2).setText("abc");
		find(dialog, JComboBox.class).get(1).setSelectedItem(Temperament.EQUAL);
		assertEquals("100", fields.get(2).getText());
		assertFalse(dialog.validateOK(), "the hidden custom value is still checked");
	}

	@Test
	void pathsDialogRoundTrips() {
		OptionsPathsDialog dialog = new OptionsPathsDialog(null);
		dialog.pathSongs = "C:\\songs";
		dialog.pathTracks = "C:\\tracks";
		dialog.dataToUi();
		List<JTextField> fields = find(dialog, JTextField.class);
		assertEquals(3, fields.size());
		assertEquals("C:\\songs", fields.get(0).getText());
		assertEquals("", fields.get(1).getText());
		assertEquals(3, find(dialog, AbstractButton.class).stream().filter(b -> "Browse...".equals(b.getText())).count());
		fields.get(1).setText("C:\\instruments");
		dialog.dataFromUi();
		assertEquals("C:\\instruments", dialog.pathInstruments);
		assertEquals("C:\\tracks", dialog.pathTracks);
	}

	/** Plan 59 step 3: Equal pinned first, Custom pinned last, the 21 presets alphabetical - and the exact order C++'s TEMPERAMENT_NUMBERS/TEMPERAMENT_NAMES hard-code. */
	@Test
	void theTemperamentComboOrderIsPinnedAndMatchesTheCpp() {
		java.util.List<Temperament> order = TuningDialog.temperamentsInDisplayOrder();
		assertEquals(23, order.size());
		int[] numbers = { 0, 7, 8, 21, 10, 11, 12, 13, 6, 9, 20, 17, 18, 16, 22, 23, 15, 4, 3, 1, 2, 19, 29 };
		for (int i = 0; i < numbers.length; i++) {
			assertEquals(numbers[i], order.get(i).getNumber(), "TEMPERAMENT_NUMBERS[" + i + "] (" + order.get(i).getText() + ")");
		}
		// Spot checks of the renamed texts (plan 59 step 3, grounded in the Scala archive)
		assertEquals("Béthizy Tempérament Ordinaire (1754)", Temperament.getInstance(12).getText());
		assertEquals("Bach Temperament by Sparschuh (A4 = 400 Hz)", Temperament.BACH.getText());
		assertEquals("Zarlino Tempérament Extraordinaire (Schulter)", Temperament.ZARLINO.getText());
		assertEquals("Young Well Temperament No. 1 (1800)", Temperament.getInstance(1).getText());
		assertEquals("Werckmeister III Well Temperament (1691)", Temperament.WERCKMEISTER_III.getText());
	}

	@Test
	void theComboEntriesMatchTheCppResources() {
		assertEquals(12, Tonic.getValues().size());
		assertEquals("A-", Tonic.getInstance(3).getText()); // the default tonic
		assertEquals(23, Temperament.getValues().size()); // 0 equal + 21 presets + custom; plan 59 retired seven presets (TUNING_PRESETS = 29 stays the custom index)
		assertEquals(org.atari.raster.rmt.model.Tuning.TUNING_CUSTOM, Temperament.CUSTOM.getNumber());
		// the driver versions are a value set now: the six the dialog offers, with their texts from ValueSets.properties
		assertEquals(6, TrackerDriverVersion.getSelectableValues().size());
		assertEquals("RMT 1.28 Unpatched by Raster", TrackerDriverVersion.getSelectableValues().get(0).getText());
	}
}
