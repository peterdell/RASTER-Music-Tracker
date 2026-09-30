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

import org.atari.raster.rmt.model.Fraction;
import org.atari.raster.rmt.model.KeyboardLayout;
import org.atari.raster.rmt.model.TrackerDriverVersion;

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
		values.trackerDriverVersion = TrackerDriverVersion.PATCH8;
		values.keyboardLayout = KeyboardLayout.AZERTY;
		values.midiTouchResponse = false;
		OptionsDialog dialog = new OptionsDialog(null, session, values);
		dialog.dataToUi();

		List<JCheckBox> boxes = find(dialog, JCheckBox.class);
		assertEquals(13, boxes.size());
		List<JComboBox> combos = find(dialog, JComboBox.class);
		assertEquals(3, combos.size());
		assertEquals(TrackerDriverVersion.PATCH8, combos.get(0).getSelectedItem()); // a value set field holds the instance; its text is what the box shows
		assertEquals("RMT 1.28 Patch 8 by Analmux", combos.get(0).getSelectedItem().toString());
		assertEquals(KeyboardLayout.AZERTY, combos.get(1).getSelectedItem()); // a value set field holds the instance
		assertEquals("AZERTY Layout", combos.get(1).getSelectedItem().toString());
		assertEquals("None", combos.get(2).getSelectedItem());
		List<JTextField> fields = find(dialog, JTextField.class);
		assertEquals(4, fields.size()); // scaling, primary, secondary, MIDI volume offset
		assertEquals("150", fields.get(0).getText());
		assertFalse(fields.get(3).isEnabled()); // OnMidiTouchResponseClicked: no touch response, no offset
		assertEquals("Paths...", button(dialog, "Paths...").getText());
		assertEquals("Tuning...", button(dialog, "Tuning...").getText());

		fields.get(0).setText("300");
		fields.get(1).setText("16");
		((JCheckBox) button(dialog, "Use NTSC System Speed (60Hz)")).setSelected(true);
		((JCheckBox) button(dialog, "Touch response")).doClick();
		assertTrue(fields.get(3).isEnabled());
		combos.get(0).setSelectedIndex(0);
		dialog.dataFromUi();
		assertEquals(300, values.scalingPercentage);
		assertEquals(16, values.trackLinePrimaryHighlight);
		assertTrue(values.ntsc);
		assertTrue(values.midiTouchResponse);
		assertEquals(TrackerDriverVersion.UNPATCHED, values.trackerDriverVersion);
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

	@Test
	void tuningDialogRoundTripsAndOnlyTestOrOKReachTheSession() {
		session.tuningSettings.basetuning = 432;
		session.tuningSettings.temperament = 29;
		session.tuningRatios.maj7th = new Fraction(243, 128);
		TuningDialog dialog = new TuningDialog(null, session);
		dialog.dataToUi();

		List<JTextField> fields = find(dialog, JTextField.class);
		assertEquals(1 + 2 * 13, fields.size());
		assertEquals("432", fields.get(0).getText());
		List<JComboBox> combos = find(dialog, JComboBox.class);
		assertEquals("A-", combos.get(0).getSelectedItem());
		assertEquals("Custom Temperament with RA/TIO", combos.get(1).getSelectedItem());
		assertEquals("243", fields.get(1 + 2 * 11).getText()); // Major 7th numerator
		assertEquals("128", fields.get(2 + 2 * 11).getText());
		assertEquals("2", fields.get(1 + 2 * 12).getText()); // Octave

		fields.get(0).setText("440");
		fields.get(1 + 2 * 12).setText("4"); // Octave 4/1
		dialog.dataFromUi();
		assertEquals(440, dialog.settings.basetuning);
		assertEquals(new Fraction(4, 1), dialog.ratios.octave);
		assertEquals(432, session.tuningSettings.basetuning); // not applied yet

		button(dialog, "Test").doClick();
		assertEquals(440, session.tuningSettings.basetuning);
		assertEquals(new Fraction(4, 1), session.tuningRatios.octave);

		button(dialog, "Reset").doClick();
		assertEquals(432, session.tuningSettings.basetuning); // the backup
		assertEquals(new Fraction(2, 1), session.tuningRatios.octave);
		assertEquals("440", fields.get(0).getText()); // the fields keep what was typed, as in C++

		fields.get(0).setText("5"); // DDV_MinMaxDouble 6.875..7040
		assertFalse(dialog.validateOK());
		fields.get(0).setText("7040");
		fields.get(2 + 2 * 12).setText("0"); // a zero denominator is refused (C++ would divide by it)
		assertFalse(dialog.validateOK());
		fields.get(2 + 2 * 12).setText("1");
		assertTrue(dialog.validateOK());
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

	@Test
	void theComboEntriesMatchTheCppResources() {
		assertEquals(12, TuningDialog.BASE_NOTES.length);
		assertEquals("A-", TuningDialog.BASE_NOTES[3]); // the default basenote
		assertEquals(30, TuningDialog.TEMPERAMENTS.length); // 0 equal + 28 presets + custom (TUNING_PRESETS = 29 is the custom index)
		assertEquals(org.atari.raster.rmt.model.Tuning.TUNING_CUSTOM, TuningDialog.TEMPERAMENTS.length - 1);
		// the driver versions are a value set now: the six the dialog offers, with their texts from ValueSets.properties
		assertEquals(6, TrackerDriverVersion.getSelectableValues().size());
		assertEquals("RMT 1.28 Unpatched by Raster", TrackerDriverVersion.getSelectableValues().get(0).getText());
	}
}
