package org.atari.raster.rmt.ui;

import java.awt.BorderLayout;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

import com.wudsn.tools.base.gui.ElementFactory;
import com.wudsn.tools.base.gui.ModalDialog;
import org.atari.raster.rmt.model.KeyboardLayout;
import org.atari.raster.rmt.model.TrackerDriverVersion;

/**
 * "Options" - the port of {@code COptionsDialog} ({@code IDD_OPTIONS}): the
 * four group boxes General / Module Defaults / Keyboard / MIDI, the
 * Paths... and Tuning... buttons opening {@link OptionsPathsDialog} and
 * {@link TuningDialog}, and the MFC validation ranges (interface size
 * 100-300, highlight steps 2-256, MIDI volume offset 0-15). It edits an
 * {@link OptionsValues}; {@link RmtCommands#applyOptions} applies the
 * result.
 *
 * <p>The MIDI device list is "None" plus the MIDI input devices
 * {@link RmtMidi#deviceNames()} reports ({@code midiInGetDevCaps} in C++).
 */
final class OptionsDialog extends ModalDialog {

	private static final long serialVersionUID = 1L;

	/** {@code m_keyboardLayoutComboBox}'s entries, index = {@link KeyboardLayout} constant. */
	static final String[] KEYBOARD_LAYOUTS = { "QWERTY Layout", "AZERTY Layout" };

	/** {@code m_trackerDriverVersionComboBox}'s entries, in C++'s order (not every enum value is offered). */
	static final TrackerDriverVersion[] DRIVER_VERSIONS = { TrackerDriverVersion.UNPATCHED, TrackerDriverVersion.PATCH3, TrackerDriverVersion.PATCH6, TrackerDriverVersion.PATCH8, TrackerDriverVersion.PATCH16, TrackerDriverVersion.PATCH_PRINCE_OF_PERSIA };
	static final String[] DRIVER_VERSION_NAMES = { "RMT 1.28 Unpatched by Raster", "RMT 1.25 Patch 3 by Analmux", "RMT 1.27 Patch 6 by Analmux", "RMT 1.28 Patch 8 by Analmux", "RMT 1.28 Patch 16 by VinsCool", "RMT 1.28 Patch Prince of Persia by VinsCool" };

	private final RmtSession session;
	private final OptionsValues values;

	private final JTextField scalingField = new JTextField(4);
	private final JTextField primaryHighlightField = new JTextField(3);
	private final JTextField secondaryHighlightField = new JTextField(3);
	private final JCheckBox germanNotationBox = ElementFactory.createCheckBox(DataTypes.OptionsDialog_UseGermanNotation);
	private final JCheckBox altNumberingBox = ElementFactory.createCheckBox(DataTypes.OptionsDialog_TrackLineAltNumbering);
	private final JCheckBox flatNotesBox = ElementFactory.createCheckBox(DataTypes.OptionsDialog_DisplayFlatNotes);
	private final JCheckBox smoothScrollBox = ElementFactory.createCheckBox(DataTypes.OptionsDialog_SmoothScroll);
	private final JCheckBox noHwSoundBufferBox = ElementFactory.createCheckBox(DataTypes.OptionsDialog_NoHwSoundBuffer);
	private final JCheckBox debugDisplayBox = ElementFactory.createCheckBox(DataTypes.OptionsDialog_DebugDisplay);
	private final JCheckBox ntscBox = ElementFactory.createCheckBox(DataTypes.OptionsDialog_NTSC);
	private final JComboBox<String> driverVersionCombo = new JComboBox<>(DRIVER_VERSION_NAMES);
	private final JComboBox<String> keyboardLayoutCombo = new JComboBox<>(KEYBOARD_LAYOUTS);
	private final JCheckBox upDownContinueBox = ElementFactory.createCheckBox(DataTypes.OptionsDialog_KeyboardUpDownContinue);
	private final JCheckBox rememberOctavesBox = ElementFactory.createCheckBox(DataTypes.OptionsDialog_KeyboardRememberOctavesAndVolumes);
	private final JCheckBox escResetBox = ElementFactory.createCheckBox(DataTypes.OptionsDialog_KeyboardEscResetAtariSound);
	private final JCheckBox askWhenControlSBox = ElementFactory.createCheckBox(DataTypes.OptionsDialog_KeyboardAskWhenControlS);
	private final JComboBox<String> midiDeviceCombo = new JComboBox<>();
	private final JCheckBox midiTouchResponseBox = ElementFactory.createCheckBox(DataTypes.OptionsDialog_MidiTouchResponse);
	private final JTextField midiVolumeOffsetField = new JTextField(3);
	private final JCheckBox midiNoteOffBox = ElementFactory.createCheckBox(DataTypes.OptionsDialog_MidiNoteOff);
	private boolean filled;

	OptionsDialog(JFrame parent, RmtSession session, OptionsValues values) {
		super(parent, Texts.OptionsDialog_Title);
		this.session = session;
		this.values = values;

		JPanel general = DialogSupport.createGroup(Texts.OptionsDialog_GroupGeneral);
		DialogSupport.add(general, ElementFactory.createLabel(DataTypes.OptionsDialog_ScalingPercentage, scalingField), 0, 0, 1, false);
		DialogSupport.add(general, scalingField, 1, 0, 1, false);
		JPanel highlight = new JPanel();
		highlight.add(ElementFactory.createLabel(DataTypes.OptionsDialog_TrackLineHighlight, primaryHighlightField));
		highlight.add(primaryHighlightField);
		highlight.add(new JLabel("/"));
		highlight.add(secondaryHighlightField);
		DialogSupport.add(general, highlight, 2, 0, 1, false);
		DialogSupport.add(general, germanNotationBox, 0, 1, 2, false);
		DialogSupport.add(general, altNumberingBox, 2, 1, 1, false);
		DialogSupport.add(general, flatNotesBox, 0, 2, 3, false);
		DialogSupport.add(general, smoothScrollBox, 0, 3, 2, false);
		DialogSupport.add(general, noHwSoundBufferBox, 2, 3, 1, false);
		DialogSupport.add(general, debugDisplayBox, 0, 4, 3, false);

		JPanel module = DialogSupport.createGroup(Texts.OptionsDialog_GroupModuleDefaults);
		DialogSupport.add(module, ntscBox, 0, 0, 2, false);
		DialogSupport.add(module, ElementFactory.createLabel(DataTypes.OptionsDialog_TrackerDriverVersion, driverVersionCombo), 0, 1, 1, false);
		DialogSupport.add(module, driverVersionCombo, 1, 1, 1, true);

		JPanel keyboard = DialogSupport.createGroup(Texts.OptionsDialog_GroupKeyboard);
		DialogSupport.add(keyboard, ElementFactory.createLabel(DataTypes.OptionsDialog_KeyboardLayout, keyboardLayoutCombo), 0, 0, 1, false);
		DialogSupport.add(keyboard, keyboardLayoutCombo, 1, 0, 1, true);
		DialogSupport.add(keyboard, upDownContinueBox, 0, 1, 2, false);
		DialogSupport.add(keyboard, rememberOctavesBox, 0, 2, 2, false);
		DialogSupport.add(keyboard, escResetBox, 0, 3, 2, false);
		DialogSupport.add(keyboard, askWhenControlSBox, 0, 4, 2, false);

		JPanel midi = DialogSupport.createGroup(Texts.OptionsDialog_GroupMidi);
		DialogSupport.add(midi, ElementFactory.createLabel(DataTypes.OptionsDialog_MidiDevice, midiDeviceCombo), 0, 0, 1, false);
		DialogSupport.add(midi, midiDeviceCombo, 1, 0, 2, true);
		DialogSupport.add(midi, midiTouchResponseBox, 0, 1, 1, false);
		JPanel offset = new JPanel();
		offset.add(ElementFactory.createLabel(DataTypes.OptionsDialog_MidiVolumeOffset, midiVolumeOffsetField));
		offset.add(midiVolumeOffsetField);
		DialogSupport.add(midi, offset, 1, 1, 2, false);
		DialogSupport.add(midi, midiNoteOffBox, 0, 2, 1, false);
		midiDeviceCombo.addItem(Texts.OptionsDialog_MidiDeviceNone); // id=0
		for (String name : session.midi.deviceNames()) {
			midiDeviceCombo.addItem(name);
		}
		midiTouchResponseBox.addActionListener(e -> onMidiTouchResponseClicked());

		JPanel content = new JPanel();
		content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
		content.add(general);
		content.add(module);
		content.add(keyboard);
		content.add(midi);
		getContentPane().add(content, BorderLayout.CENTER);

		JButton paths = ElementFactory.createButton(Actions.OptionsDialog_Paths, true);
		paths.addActionListener(e -> onClickedOptionsPaths());
		JButton tuning = ElementFactory.createButton(Actions.OptionsDialog_Tuning, true);
		tuning.addActionListener(e -> onClickedOptionsTuning());
		addButtonBarButton(tuning); // inserted at the front each time, so add in reverse order
		addButtonBarButton(paths);
	}

	/** {@code OnMidiTouchResponseClicked()}: the volume offset is only editable with touch response on. */
	private void onMidiTouchResponseClicked() {
		midiVolumeOffsetField.setEnabled(midiTouchResponseBox.isSelected());
	}

	/** {@code OnClickedOptionsPaths()}: the default paths straight into the options (not part of the OK/Cancel round trip in C++ either), the last-used paths cleared. */
	private void onClickedOptionsPaths() {
		RmtOptions o = session.options;
		OptionsPathsDialog dlg = new OptionsPathsDialog((JFrame) getOwner());
		dlg.pathSongs = o.defaultSongsPath;
		dlg.pathInstruments = o.defaultInstrumentsPath;
		dlg.pathTracks = o.defaultTracksPath;
		if (dlg.showDialog()) {
			o.defaultSongsPath = dlg.pathSongs;
			o.defaultInstrumentsPath = dlg.pathInstruments;
			o.defaultTracksPath = dlg.pathTracks;
			o.lastSongsPath = "";
			o.lastInstrumentsPath = "";
			o.lastTracksPath = "";
		}
	}

	/** {@code OnClickedOptionsTuning()}. */
	private void onClickedOptionsTuning() {
		new TuningDialog((JFrame) getOwner(), session).showDialog();
	}

	private static int indexOf(TrackerDriverVersion version) {
		for (int i = 0; i < DRIVER_VERSIONS.length; i++) {
			if (DRIVER_VERSIONS[i] == version) {
				return i;
			}
		}
		return -1;
	}

	@Override
	protected void dataToUi() {
		if (filled) {
			return; // keep whatever the user typed across an OK round trip
		}
		filled = true;
		scalingField.setText(Integer.toString(values.scalingPercentage));
		primaryHighlightField.setText(Integer.toString(values.trackLinePrimaryHighlight));
		secondaryHighlightField.setText(Integer.toString(values.trackLineSecondaryHighlight));
		germanNotationBox.setSelected(values.useGermanNotation);
		altNumberingBox.setSelected(values.trackLineAltNumbering);
		flatNotesBox.setSelected(values.displayFlatNotes);
		smoothScrollBox.setSelected(values.doSmoothScrolling);
		noHwSoundBufferBox.setSelected(values.noHwSoundBuffer);
		debugDisplayBox.setSelected(values.viewDebugDisplay);
		ntscBox.setSelected(values.ntsc);
		driverVersionCombo.setSelectedIndex(indexOf(values.trackerDriverVersion)); // -1 = nothing selected, like CComboBox for an unlisted item
		keyboardLayoutCombo.setSelectedIndex(values.keyboardLayout >= 0 && values.keyboardLayout < KEYBOARD_LAYOUTS.length ? values.keyboardLayout : -1);
		upDownContinueBox.setSelected(values.keyboardUpDownContinue);
		rememberOctavesBox.setSelected(values.keyboardRememberOctavesAndVolumes);
		escResetBox.setSelected(values.keyboardEscResetAtariSound);
		askWhenControlSBox.setSelected(values.keyboardAskWhenControlS);
		int midiIndex = 0;
		for (int i = 1; i < midiDeviceCombo.getItemCount(); i++) {
			if (midiDeviceCombo.getItemAt(i).equals(values.midiDevice)) {
				midiIndex = i;
			}
		}
		midiDeviceCombo.setSelectedIndex(midiIndex); // +1 because -1 == --- none ---
		midiTouchResponseBox.setSelected(values.midiTouchResponse);
		midiVolumeOffsetField.setText(Integer.toString(values.midiVolumeOffset));
		midiNoteOffBox.setSelected(values.midiNoteOff);
		onMidiTouchResponseClicked();
	}

	/** {@code DoDataExchange} (save direction) + {@code OnOK()}'s combo boxes. */
	@Override
	protected void dataFromUi() {
		if (!validateFields()) {
			return;
		}
		values.scalingPercentage = DialogSupport.parseInt(scalingField);
		values.trackLinePrimaryHighlight = DialogSupport.parseInt(primaryHighlightField);
		values.trackLineSecondaryHighlight = DialogSupport.parseInt(secondaryHighlightField);
		values.useGermanNotation = germanNotationBox.isSelected();
		values.trackLineAltNumbering = altNumberingBox.isSelected();
		values.displayFlatNotes = flatNotesBox.isSelected();
		values.doSmoothScrolling = smoothScrollBox.isSelected();
		values.noHwSoundBuffer = noHwSoundBufferBox.isSelected();
		values.viewDebugDisplay = debugDisplayBox.isSelected();
		values.ntsc = ntscBox.isSelected();
		int driver = driverVersionCombo.getSelectedIndex();
		values.trackerDriverVersion = driver >= 0 ? DRIVER_VERSIONS[driver] : TrackerDriverVersion.NONE; // GetSelectedItem(TrackerDriverVersion::NONE)
		int layout = keyboardLayoutCombo.getSelectedIndex();
		values.keyboardLayout = layout >= 0 ? layout : KeyboardLayout.QWERTY; // GetSelectedItem(KeyboardLayout::QWERTY)
		values.keyboardUpDownContinue = upDownContinueBox.isSelected();
		values.keyboardRememberOctavesAndVolumes = rememberOctavesBox.isSelected();
		values.keyboardEscResetAtariSound = escResetBox.isSelected();
		values.keyboardAskWhenControlS = askWhenControlSBox.isSelected();
		values.midiDevice = midiDeviceCombo.getSelectedIndex() > 0 ? (String) midiDeviceCombo.getSelectedItem() : "";
		values.midiTouchResponse = midiTouchResponseBox.isSelected();
		values.midiVolumeOffset = DialogSupport.parseInt(midiVolumeOffsetField);
		values.midiNoteOff = midiNoteOffBox.isSelected();
	}

	/** The {@code DDV_MinMaxInt} checks with MFC's prompts, in {@code DoDataExchange}'s order. */
	private boolean validateFields() {
		return DialogSupport.validateInt(this, midiVolumeOffsetField, 0, 15) && DialogSupport.validateInt(this, primaryHighlightField, 2, 256) && DialogSupport.validateInt(this, secondaryHighlightField, 2, 256) && DialogSupport.validateInt(this, scalingField, 100, 300);
	}

	private boolean fieldsValid() {
		Integer offset = DialogSupport.parseInt(midiVolumeOffsetField);
		Integer primary = DialogSupport.parseInt(primaryHighlightField);
		Integer secondary = DialogSupport.parseInt(secondaryHighlightField);
		Integer scaling = DialogSupport.parseInt(scalingField);
		return offset != null && offset >= 0 && offset <= 15 && primary != null && primary >= 2 && primary <= 256 && secondary != null && secondary >= 2 && secondary <= 256 && scaling != null && scaling >= 100 && scaling <= 300;
	}

	@Override
	protected boolean validateOK() {
		return fieldsValid(); // dataFromUi() has already shown the prompt for an invalid field
	}

	/** {@code DoModal() == IDOK}. */
	boolean showDialog() {
		showModal(scalingField);
		return okPressed;
	}
}
