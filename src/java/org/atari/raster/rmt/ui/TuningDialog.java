package org.atari.raster.rmt.ui;

import java.awt.BorderLayout;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Collator;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

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
import org.atari.raster.rmt.model.Temperament;
import org.atari.raster.rmt.model.Tuning;
import org.atari.raster.rmt.model.TuningCents;
import org.atari.raster.rmt.model.TuningSettings;

/**
 * "Tuning" - the port of {@code TuningDlg} ({@code IDD_TUNING}): the A4
 * frequency, base note and temperament, and 12 cents fields showing the
 * selected temperament's notes in musical cents (plan 59; the custom
 * values replaced the numerator/denominator fraction pairs): computed and
 * read-only for equal temperament and the presets, editable only for the
 * custom temperament, whose typed values are kept while another
 * temperament is shown. The unison is the tonic and fixed at 0, the octave
 * at 1200 (2:1). Its button semantics are C++'s: Test applies
 * the fields to the session and regenerates the frequency tables
 * ({@code g_Tuning.InitTuning()}), Reset restores the values the dialog was
 * opened with (into the session only - the fields keep what was typed, as
 * in C++), OK is Test-then-close, Cancel (and closing the window) is
 * Reset-then-close.
 *
 * <p>Validation: the base tuning must be 6.875-7040 ({@code
 * DDV_MinMaxDouble}); each cents value must be 0-1200.
 */
final class TuningDialog extends ModalDialog {

	private static final long serialVersionUID = 1L;

	/** The cents rows' labels, by semitone above the tonic. */
	static final String[] CENTS_NAMES = { "Unison", "Minor 2nd", "Major 2nd", "Minor 3rd", "Major 3rd", "Perfect 4th", "Tritone", "Perfect 5th", "Minor 6th", "Major 6th", "Minor 7th", "Major 7th" };

	private final RmtSession session;
	/** {@code m_tuningSettings}: the dialog's working copy. */
	final TuningSettings settings = new TuningSettings();
	/** {@code m_tuningSettingsBackup}: what Reset restores. */
	private final TuningSettings settingsBackup = new TuningSettings();

	private final JTextField baseTuningField = new JTextField(12);
	/** {@code IDC_BASENOTE}/{@code IDC_TEMPERAMENT}, holding the {@link BaseNote}/{@link Temperament} instances rather than combo indexes. */
	private final ValueSetField<BaseNote> baseNoteCombo = new ValueSetField<>(BaseNote.getValues());
	private final ValueSetField<Temperament> temperamentCombo = new ValueSetField<>(temperamentsInDisplayOrder());
	private final JTextField[] centsFields = new JTextField[TuningCents.COUNT];
	/** The custom temperament's cents as typed, kept while the fields show another temperament's cents. */
	private final String[] customCentsTexts = new String[TuningCents.COUNT];
	/** The temperament whose cents the fields currently show. */
	private Temperament shownTemperament;
	private boolean filled;

	TuningDialog(JFrame parent, RmtSession session) {
		super(parent, Texts.TuningDialog_Title);
		this.session = session;
		// OnClickedModuleTuning: dlg.m_tuningSettings = g_Song.GetTuningSettings()
		copy(session.song.getTuningSettings(), settings);
		// OnInitDialog: backup all current values first
		copy(session.song.getTuningSettings(), settingsBackup);

		JPanel general = DialogSupport.createGroup(Texts.TuningDialog_GroupGeneral);
		DialogSupport.add(general, ElementFactory.createLabel(DataTypes.TuningDialog_BaseTuning, baseTuningField), 0, 0, 1, false);
		DialogSupport.add(general, baseTuningField, 1, 0, 1, true);
		DialogSupport.add(general, ElementFactory.createLabel(DataTypes.TuningDialog_BaseNote, baseNoteCombo), 2, 0, 1, false);
		DialogSupport.add(general, baseNoteCombo, 3, 0, 1, false);
		JLabel examples = new JLabel(DataTypes.TuningDialog_BaseTuning.getToolTip()); // the resource's static example text
		DialogSupport.add(general, examples, 0, 1, 4, false);
		DialogSupport.add(general, ElementFactory.createLabel(DataTypes.TuningDialog_Temperament, temperamentCombo), 0, 2, 1, false);
		DialogSupport.add(general, temperamentCombo, 1, 2, 3, true);

		JPanel centsGroup = DialogSupport.createGroup(Texts.TuningDialog_GroupCents);
		for (int i = 0; i < TuningCents.COUNT; i++) {
			centsFields[i] = new JTextField(10);
			DialogSupport.add(centsGroup, new JLabel(CENTS_NAMES[i]), 0, i, 1, false);
			DialogSupport.add(centsGroup, centsFields[i], 1, i, 1, true);
		}
		centsFields[0].setEditable(false); // the tonic is always 0 cents
		temperamentCombo.addActionListener(e -> showCents());

		JPanel content = new JPanel();
		content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
		content.add(general);
		content.add(centsGroup);
		getContentPane().add(content, BorderLayout.CENTER);

		JButton test = ElementFactory.createButton(Actions.TuningDialog_Test, true);
		test.addActionListener(e -> onTuningTest());
		JButton reset = ElementFactory.createButton(Actions.TuningDialog_Reset, true);
		reset.addActionListener(e -> onTuningReset());
		addButtonBarButton(reset); // inserted at the front each time, so add in reverse order
		addButtonBarButton(test);
	}

	/**
	 * The temperament combo's display order (plan 59 step 3): the equal
	 * temperament - the default - pinned first, the custom temperament pinned
	 * last, and the presets in between alphabetical by display text, the
	 * standing order for RMT's value lists. Display only - the stored
	 * numbers ({@code Temperament.getNumber()}) are untouched, and C++'s
	 * {@code TEMPERAMENT_NAMES} is hard-coded to this same order.
	 */
	static List<Temperament> temperamentsInDisplayOrder() {
		List<Temperament> result = new ArrayList<>(Temperament.getValues());
		result.remove(Temperament.EQUAL);
		result.remove(Temperament.CUSTOM);
		Collator collator = Collator.getInstance(Locale.ENGLISH); // é sorts as e (Béthizy)
		result.sort((temperament1, temperament2) -> collator.compare(temperament1.getText(), temperament2.getText()));
		result.add(0, Temperament.EQUAL);
		result.add(Temperament.CUSTOM);
		return result;
	}

	static void copy(TuningSettings from, TuningSettings to) {
		to.basetuning = from.basetuning;
		to.basenote = from.basenote;
		to.temperament = from.temperament;
		to.cents.copyFrom(from.cents);
	}

	@Override
	protected void dataToUi() {
		if (filled) {
			return; // keep whatever the user typed across a Test/OK round trip
		}
		filled = true;
		baseTuningField.setText(RmtConfig.formatDouble(settings.basetuning));
		baseNoteCombo.setValue(settings.basenote); // always a valid instance - the config read already mapped stray numbers
		for (int i = 0; i < TuningCents.COUNT; i++) {
			customCentsTexts[i] = RmtConfig.formatDouble(settings.cents.values[i]);
		}
		shownTemperament = null;
		temperamentCombo.setValue(settings.temperament);
		showCents(); // setSelectedIndex above fires no event when the selection did not change
	}

	/**
	 * The fields show the cents of the selected temperament: computed and
	 * read-only for equal temperament and the presets, the user's own and
	 * editable for the custom temperament (the tonic stays fixed at 0). What
	 * was typed for the custom temperament is kept while another
	 * temperament is shown, and comes back when it is selected again.
	 */
	private void showCents() {
		Temperament selected = temperamentCombo.getValue();
		if (selected == null || selected == shownTemperament) {
			return;
		}
		if (isCustomShown()) {
			for (int i = 0; i < TuningCents.COUNT; i++) {
				customCentsTexts[i] = centsFields[i].getText();
			}
		}
		for (int i = 0; i < TuningCents.COUNT; i++) {
			centsFields[i].setText(selected.isCustom() ? customCentsTexts[i] : formatCents(selected.getCents(i)));
			centsFields[i].setEnabled(selected.isCustom() && i > 0);
		}
		shownTemperament = selected;
	}

	private boolean isCustomShown() {
		return shownTemperament != null && shownTemperament.isCustom();
	}

	/** The custom temperament's cents text of note {@code i}: the field while the custom temperament is shown, the kept text otherwise. */
	private String customCentsText(int i) {
		return isCustomShown() ? centsFields[i].getText() : customCentsTexts[i];
	}

	/**
	 * A computed cents value for display, rounded to 3 decimals: the presets'
	 * multipliers carry 6 decimals (their Scala sources' rounding), which is
	 * worth about 0.001 cents - more digits would be noise.
	 */
	static String formatCents(double cents) {
		return BigDecimal.valueOf(cents).setScale(3, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
	}

	private static Double parseCents(String text) {
		try {
			return Double.valueOf(text.trim());
		} catch (NumberFormatException | NullPointerException ex) {
			return null;
		}
	}

	private boolean customCentsTextsValid() {
		for (int i = 1; i < TuningCents.COUNT; i++) {
			Double cents = parseCents(customCentsText(i));
			if (cents == null || cents.isNaN() || cents < 0 || cents > Tuning.CENTS_PER_OCTAVE) {
				return false;
			}
		}
		return true;
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
		settings.cents.values[0] = 0;
		for (int i = 1; i < TuningCents.COUNT; i++) {
			settings.cents.values[i] = parseCents(customCentsText(i)); // the custom cents, whichever temperament is shown
		}
	}

	/** The DDV checks, silently. */
	private boolean fieldsValid() {
		Double tuning = DialogSupport.parseDouble(baseTuningField);
		if (tuning == null || tuning.isNaN() || tuning < TuningSettings.BASETUNING_MIN || tuning > TuningSettings.BASETUNING_MAX) {
			return false;
		}
		return customCentsTextsValid();
	}

	/** The DDV checks with MFC's prompts. */
	private boolean validateFields() {
		if (!DialogSupport.validateDouble(this, baseTuningField, TuningSettings.BASETUNING_MIN, TuningSettings.BASETUNING_MAX)) {
			return false;
		}
		if (customCentsTextsValid()) {
			return true;
		}
		// An invalid custom value is hidden while another temperament is
		// shown: switch back to the custom temperament, so the prompt can
		// point at the offending field.
		temperamentCombo.setValue(Temperament.CUSTOM);
		showCents();
		for (int i = 1; i < TuningCents.COUNT; i++) {
			if (!DialogSupport.validateDouble(this, centsFields[i], 0, Tuning.CENTS_PER_OCTAVE)) {
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
			copy(settings, session.song.getTuningSettings());
			session.initTuning();
		});
	}

	/** {@code OnTuningReset()}: the backup into the session and {@code InitTuning()} - the fields are left alone, as in C++. */
	private void onTuningReset() {
		session.locked(() -> {
			copy(settingsBackup, session.song.getTuningSettings());
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
