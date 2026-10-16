package org.atari.raster.rmt.ui;

import java.awt.BorderLayout;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

import com.wudsn.tools.base.gui.ElementFactory;
import com.wudsn.tools.base.gui.ModalDialog;
import com.wudsn.tools.base.gui.ValueSetField;
import org.atari.raster.rmt.model.BaseNote;
import org.atari.raster.rmt.model.IntegerFraction;
import org.atari.raster.rmt.model.Temperament;
import org.atari.raster.rmt.model.TuningRatios;
import org.atari.raster.rmt.model.TuningSettings;

/**
 * "Tuning" - the port of {@code TuningDlg} ({@code IDD_TUNING}): the base
 * tuning, base note and temperament, and the 13 custom-temperament ratios
 * as numerator/denominator pairs, enabled only while the custom
 * temperament is selected (a preset plays its own table). Its button semantics are C++'s: Test
 * applies the fields to the session and regenerates the frequency tables
 * ({@code g_Tuning.InitTuning()}), Reset restores the values the dialog was
 * opened with (into the session only - the fields keep what was typed, as
 * in C++), OK is Test-then-close, Cancel (and closing the window) is
 * Reset-then-close.
 *
 * <p>Validation: the base tuning must be 6.875-7040 ({@code
 * DDV_MinMaxDouble}); a denominator of 0 is rejected here where C++
 * accepted it and divided by zero later ({@link IntegerFraction} refuses to be
 * built with one).
 */
final class TuningDialog extends ModalDialog {

	private static final long serialVersionUID = 1L;

	/** The ratio rows' labels, in {@link TuningRatios}' field order. */
	static final String[] RATIO_NAMES = { "Unison", "Minor 2nd", "Major 2nd", "Minor 3rd", "Major 3rd", "Perfect 4th", "Tritone", "Perfect 5th", "Minor 6th", "Major 6th", "Minor 7th", "Major 7th", "Octave" };

	private final RmtSession session;
	/** {@code m_tuningSettings}/{@code m_tuningRatios}: the dialog's working copy. */
	final TuningSettings settings = new TuningSettings();
	final TuningRatios ratios = new TuningRatios();
	/** {@code m_tuningSettingsBackup}/{@code m_tuningRatiosBackup}: what Reset restores. */
	private final TuningSettings settingsBackup = new TuningSettings();
	private final TuningRatios ratiosBackup = new TuningRatios();

	private final JTextField baseTuningField = new JTextField(12);
	/** {@code IDC_BASENOTE}/{@code IDC_TEMPERAMENT}, holding the {@link BaseNote}/{@link Temperament} instances rather than combo indexes. */
	private final ValueSetField<BaseNote> baseNoteCombo = new ValueSetField<>(BaseNote.getValues());
	private final ValueSetField<Temperament> temperamentCombo = new ValueSetField<>(Temperament.getValues());
	private final JTextField[] numeratorFields = new JTextField[RATIO_NAMES.length];
	private final JTextField[] denominatorFields = new JTextField[RATIO_NAMES.length];
	private boolean filled;

	TuningDialog(JFrame parent, RmtSession session) {
		super(parent, Texts.TuningDialog_Title);
		this.session = session;
		// OnClickedOptionsTuning: dlg.m_tuningSettings = g_Song.GetTuning(); dlg.m_tuningRatios = g_Song.GetTuningRatios()
		copy(session.song.getTuning(), settings);
		copy(session.song.getTuningRatios(), ratios);
		// OnInitDialog: backup all current values first
		copy(session.song.getTuning(), settingsBackup);
		copy(session.song.getTuningRatios(), ratiosBackup);

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
		temperamentCombo.addActionListener(e -> updateRatioFieldsEnabled());
		updateRatioFieldsEnabled();

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

	private static IntegerFraction[] toArray(TuningRatios r) {
		return new IntegerFraction[] { r.unison, r.min2nd, r.maj2nd, r.min3rd, r.maj3rd, r.perf4th, r.tritone, r.perf5th, r.min6th, r.maj6th, r.min7th, r.maj7th, r.octave };
	}

	private static void fromArray(IntegerFraction[] f, TuningRatios r) {
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
		baseNoteCombo.setValue(settings.basenote); // always a valid instance - the config read already mapped stray numbers
		temperamentCombo.setValue(settings.temperament);
		IntegerFraction[] f = toArray(ratios);
		for (int i = 0; i < f.length; i++) {
			numeratorFields[i].setText(Integer.toString(f[i].numerator));
			denominatorFields[i].setText(Integer.toString(f[i].denominator));
		}
		updateRatioFieldsEnabled(); // setSelectedIndex above fires no event when the selection did not change
	}

	/** Only the custom temperament plays the ratio fields, so they are only enabled there. */
	private void updateRatioFieldsEnabled() {
		Temperament temperament = temperamentCombo.getValue();
		boolean enabled = temperament != null && temperament.isCustom();
		for (int i = 0; i < numeratorFields.length; i++) {
			numeratorFields[i].setEnabled(enabled);
			denominatorFields[i].setEnabled(enabled);
		}
	}

	/** {@code UpdateData(TRUE)}: the fields into the working copy - only after {@link #validateOK()} said they parse. */
	@Override
	protected void dataFromUi() {
		if (!validateFields()) {
			return;
		}
		settings.basetuning = DialogSupport.parseDouble(baseTuningField);
		settings.basenote = baseNoteCombo.getValue();
		settings.temperament = temperamentCombo.getValue();
		IntegerFraction[] f = new IntegerFraction[RATIO_NAMES.length];
		for (int i = 0; i < f.length; i++) {
			f[i] = new IntegerFraction(DialogSupport.parseInt(numeratorFields[i]), DialogSupport.parseInt(denominatorFields[i]));
		}
		fromArray(f, ratios);
	}

	/** The DDV checks, silently. */
	private boolean fieldsValid() {
		Double tuning = DialogSupport.parseDouble(baseTuningField);
		if (tuning == null || tuning.isNaN() || tuning < TuningSettings.BASETUNING_MIN || tuning > TuningSettings.BASETUNING_MAX) {
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
		if (!DialogSupport.validateDouble(this, baseTuningField, TuningSettings.BASETUNING_MIN, TuningSettings.BASETUNING_MAX)) {
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
			copy(settings, session.song.getTuning());
			copy(ratios, session.song.getTuningRatios());
			session.initTuning();
		});
	}

	/** {@code OnTuningReset()}: the backup into the session and {@code InitTuning()} - the fields are left alone, as in C++. */
	private void onTuningReset() {
		session.locked(() -> {
			copy(settingsBackup, session.song.getTuning());
			copy(ratiosBackup, session.song.getTuningRatios());
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
