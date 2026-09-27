package org.atari.raster.rmt.ui;

import java.awt.BorderLayout;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

import com.wudsn.tools.base.gui.ElementFactory;
import com.wudsn.tools.base.gui.ModalDialog;
import org.atari.raster.rmt.model.Fraction;
import org.atari.raster.rmt.model.TuningRatios;
import org.atari.raster.rmt.model.TuningSettings;

/**
 * "Tuning" - the port of {@code TuningDlg} ({@code IDD_TUNING}): the base
 * tuning, base note and temperament, and the 13 custom-temperament ratios
 * as numerator/denominator pairs. Its button semantics are C++'s: Test
 * applies the fields to the session and regenerates the frequency tables
 * ({@code g_Tuning.InitTuning()}), Reset restores the values the dialog was
 * opened with (into the session only - the fields keep what was typed, as
 * in C++), OK is Test-then-close, Cancel (and closing the window) is
 * Reset-then-close.
 *
 * <p>Validation: the base tuning must be 6.875-7040 ({@code
 * DDV_MinMaxDouble}); a denominator of 0 is rejected here where C++
 * accepted it and divided by zero later ({@link Fraction} refuses to be
 * built with one).
 */
final class TuningDialog extends ModalDialog {

	private static final long serialVersionUID = 1L;

	/** {@code IDC_BASENOTE}'s entries (Rmt.rc's DLGINIT), index = {@code basenote}; the default 3 is A. */
	static final String[] BASE_NOTES = { "C-", "B-", "A#", "A-", "G#", "G-", "F#", "F-", "E-", "D#", "D-", "C#" };

	/** {@code IDC_TEMPERAMENT}'s entries, index = {@code temperament}: 0 equal, 1-28 the presets of {@code CTuning}, 29 the custom ratios below. */
	static final String[] TEMPERAMENTS = { "Equal Temperament (Default)", "Thomas Young 1799's Well Temperament no.1", "Thomas Young 1799's Well Temperament no.2", "Thomas Young 1807's Well Temperament", "Andreas Werckmeister's Temperament III (1681)", "Tempérament Égal a Quintes Justes",
			"d'Alembert and Rousseau Tempérament Ordinaire (1752/1767)", "Aron - Neidhardt Equal Beating Well Temperament", "Atom Schisma Scale", "12-TET Approximation with Minimal Order 17 Beats", "Paul Bailey's Modern Well Temperament (2002)", "John Barnes' Temperament (1977) Made After Analysis of Wohltemperierte Klavier",
			"Bethisy Tempérament Ordinaire", "Big Gulp", "12 Tone Scale by Bohlen Generated from the 4:7 : 10 Triad, Acustica 39/2/1978", "This Scale May Also be Called the \"Wedding Cake\"", "Upside Down Wedding Cake (Divorce Cake)", "12 Tone Pythagorean Scale", "Robert Schneider, Scale of Log(4) ..Log(16)",
			"Zarlino Tempérament Extraordinaire", "Fokker's 7-Limit 12-Tone Just Scale", "Bach Temperament, A- = 400hz", "Vallotti & Young Scale (Vallotti Version), Also Known as Tartini-Vallotti (1754)", "Vallotti-Young and Werckmeister III, 10 Cents 5-Limit Lesfip Scale", "Optimally Consonant Major Pentatonic, John deLaubenfels (2001)",
			"Ancient Greek Aeolic, Also Tritriaic Scale of the 54:64 : 81 Triad", "African Bapare Xylophone (Idiophone, Loose Log)", "African Yaswa Xylophone (Idiophone, Calbas Resonators with Membrane)", "19-EDO Generated Using Scale Workshop", "Custom Temperament with RA/TIO" };

	/** The ratio rows' labels, in {@link TuningRatios}' field order. */
	static final String[] RATIO_NAMES = { "Unison", "Minor 2nd", "Major 2nd", "Minor 3rd", "Major 3rd", "Perfect 4th", "Tritone", "Perfect 5th", "Minor 6th", "Major 6th", "Minor 7th", "Major 7th", "Octave" };

	static final double BASE_TUNING_MIN = 6.875;
	static final double BASE_TUNING_MAX = 7040;

	private final RmtSession session;
	/** {@code m_tuningSettings}/{@code m_tuningRatios}: the dialog's working copy. */
	final TuningSettings settings = new TuningSettings();
	final TuningRatios ratios = new TuningRatios();
	/** {@code m_tuningSettingsBackup}/{@code m_tuningRatiosBackup}: what Reset restores. */
	private final TuningSettings settingsBackup = new TuningSettings();
	private final TuningRatios ratiosBackup = new TuningRatios();

	private final JTextField baseTuningField = new JTextField(12);
	private final JComboBox<String> baseNoteCombo = new JComboBox<>(BASE_NOTES);
	private final JComboBox<String> temperamentCombo = new JComboBox<>(TEMPERAMENTS);
	private final JTextField[] numeratorFields = new JTextField[RATIO_NAMES.length];
	private final JTextField[] denominatorFields = new JTextField[RATIO_NAMES.length];
	private boolean filled;

	TuningDialog(JFrame parent, RmtSession session) {
		super(parent, Texts.TuningDialog_Title);
		this.session = session;
		// OnClickedOptionsTuning: dlg.m_tuningSettings = g_tuning; dlg.m_tuningRatios = g_tuningRatios
		copy(session.tuningSettings, settings);
		copy(session.tuningRatios, ratios);
		// OnInitDialog: backup all current values first
		copy(session.tuningSettings, settingsBackup);
		copy(session.tuningRatios, ratiosBackup);

		JPanel general = DialogSupport.createGroup(Texts.TuningDialog_GroupGeneral);
		DialogSupport.add(general, ElementFactory.createLabel(DataTypes.TuningDialog_BaseTuning, baseTuningField), 0, 0, 1, false);
		DialogSupport.add(general, baseTuningField, 1, 0, 1, true);
		DialogSupport.add(general, ElementFactory.createLabel(DataTypes.TuningDialog_BaseNote, baseNoteCombo), 2, 0, 1, false);
		DialogSupport.add(general, baseNoteCombo, 3, 0, 1, false);
		JLabel examples = new JLabel(DataTypes.TuningDialog_BaseTuning.getToolTip()); // the resource's static example text
		DialogSupport.add(general, examples, 0, 1, 4, false);
		DialogSupport.add(general, ElementFactory.createLabel(DataTypes.TuningDialog_Temperament, temperamentCombo), 0, 2, 1, false);
		DialogSupport.add(general, temperamentCombo, 1, 2, 3, true);

		JPanel ratioGroup = DialogSupport.createGroup(Texts.TuningDialog_GroupRatios);
		for (int i = 0; i < RATIO_NAMES.length; i++) {
			numeratorFields[i] = new JTextField(6);
			denominatorFields[i] = new JTextField(6);
			DialogSupport.add(ratioGroup, new JLabel(RATIO_NAMES[i]), 0, i, 1, true);
			DialogSupport.add(ratioGroup, numeratorFields[i], 1, i, 1, false);
			DialogSupport.add(ratioGroup, new JLabel("/"), 2, i, 1, false);
			DialogSupport.add(ratioGroup, denominatorFields[i], 3, i, 1, false);
		}

		JPanel content = new JPanel();
		content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
		content.add(general);
		content.add(ratioGroup);
		getContentPane().add(content, BorderLayout.CENTER);

		JButton test = ElementFactory.createButton(Actions.TuningDialog_Test, true);
		test.addActionListener(e -> onTuningTest());
		JButton reset = ElementFactory.createButton(Actions.TuningDialog_Reset, true);
		reset.addActionListener(e -> onTuningReset());
		addButtonBarButton(reset); // inserted at the front each time, so add in reverse order
		addButtonBarButton(test);
	}

	static void copy(TuningSettings from, TuningSettings to) {
		to.basetuning = from.basetuning;
		to.basenote = from.basenote;
		to.temperament = from.temperament;
	}

	static void copy(TuningRatios from, TuningRatios to) {
		to.unison = from.unison;
		to.min2nd = from.min2nd;
		to.maj2nd = from.maj2nd;
		to.min3rd = from.min3rd;
		to.maj3rd = from.maj3rd;
		to.perf4th = from.perf4th;
		to.tritone = from.tritone;
		to.perf5th = from.perf5th;
		to.min6th = from.min6th;
		to.maj6th = from.maj6th;
		to.min7th = from.min7th;
		to.maj7th = from.maj7th;
		to.octave = from.octave;
	}

	private static Fraction[] toArray(TuningRatios r) {
		return new Fraction[] { r.unison, r.min2nd, r.maj2nd, r.min3rd, r.maj3rd, r.perf4th, r.tritone, r.perf5th, r.min6th, r.maj6th, r.min7th, r.maj7th, r.octave };
	}

	private static void fromArray(Fraction[] f, TuningRatios r) {
		r.unison = f[0];
		r.min2nd = f[1];
		r.maj2nd = f[2];
		r.min3rd = f[3];
		r.maj3rd = f[4];
		r.perf4th = f[5];
		r.tritone = f[6];
		r.perf5th = f[7];
		r.min6th = f[8];
		r.maj6th = f[9];
		r.min7th = f[10];
		r.maj7th = f[11];
		r.octave = f[12];
	}

	@Override
	protected void dataToUi() {
		if (filled) {
			return; // keep whatever the user typed across a Test/OK round trip
		}
		filled = true;
		baseTuningField.setText(RmtConfig.formatDouble(settings.basetuning));
		baseNoteCombo.setSelectedIndex(Math.max(0, Math.min(BASE_NOTES.length - 1, settings.basenote)));
		temperamentCombo.setSelectedIndex(Math.max(0, Math.min(TEMPERAMENTS.length - 1, settings.temperament)));
		Fraction[] f = toArray(ratios);
		for (int i = 0; i < f.length; i++) {
			numeratorFields[i].setText(Integer.toString(f[i].numerator));
			denominatorFields[i].setText(Integer.toString(f[i].denominator));
		}
	}

	/** {@code UpdateData(TRUE)}: the fields into the working copy - only after {@link #validateOK()} said they parse. */
	@Override
	protected void dataFromUi() {
		if (!validateFields()) {
			return;
		}
		settings.basetuning = DialogSupport.parseDouble(baseTuningField);
		settings.basenote = baseNoteCombo.getSelectedIndex();
		settings.temperament = temperamentCombo.getSelectedIndex();
		Fraction[] f = new Fraction[RATIO_NAMES.length];
		for (int i = 0; i < f.length; i++) {
			f[i] = new Fraction(DialogSupport.parseInt(numeratorFields[i]), DialogSupport.parseInt(denominatorFields[i]));
		}
		fromArray(f, ratios);
	}

	/** The DDV checks, silently. */
	private boolean fieldsValid() {
		Double tuning = DialogSupport.parseDouble(baseTuningField);
		if (tuning == null || tuning.isNaN() || tuning < BASE_TUNING_MIN || tuning > BASE_TUNING_MAX) {
			return false;
		}
		for (int i = 0; i < RATIO_NAMES.length; i++) {
			if (DialogSupport.parseInt(numeratorFields[i]) == null) {
				return false;
			}
			Integer d = DialogSupport.parseInt(denominatorFields[i]);
			if (d == null || d < 1) {
				return false;
			}
		}
		return true;
	}

	/** The DDV checks with MFC's prompts. */
	private boolean validateFields() {
		if (!DialogSupport.validateDouble(this, baseTuningField, BASE_TUNING_MIN, BASE_TUNING_MAX)) {
			return false;
		}
		for (int i = 0; i < RATIO_NAMES.length; i++) {
			if (!DialogSupport.validateInt(this, numeratorFields[i], Integer.MIN_VALUE, Integer.MAX_VALUE)) {
				return false;
			}
			if (!DialogSupport.validateInt(this, denominatorFields[i], 1, Integer.MAX_VALUE)) {
				return false;
			}
		}
		return true;
	}

	@Override
	protected boolean validateOK() {
		return fieldsValid(); // dataFromUi() has already shown the prompt for an invalid field
	}

	/** {@code OnTuningTest()}: the fields into the session and {@code InitTuning()}. */
	private void onTuningTest() {
		dataFromUi();
		if (!fieldsValid()) {
			return; // C++ applies the unchanged m_ values anyway; nothing to apply here
		}
		apply();
	}

	private void apply() {
		session.locked(() -> {
			copy(settings, session.tuningSettings);
			copy(ratios, session.tuningRatios);
			session.initTuning();
		});
	}

	/** {@code OnTuningReset()}: the backup into the session and {@code InitTuning()} - the fields are left alone, as in C++. */
	private void onTuningReset() {
		session.locked(() -> {
			copy(settingsBackup, session.tuningSettings);
			copy(ratiosBackup, session.tuningRatios);
			session.initTuning();
		});
	}

	/** {@code DoModal()}: OK is {@code OnOK() = OnTuningTest() + close}, anything else is {@code OnCancel() = OnTuningReset() + close}. */
	void showDialog() {
		showModal(baseTuningField);
		if (okPressed) {
			apply();
		} else {
			onTuningReset();
		}
	}
}
