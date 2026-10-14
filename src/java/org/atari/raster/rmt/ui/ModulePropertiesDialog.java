package org.atari.raster.rmt.ui;

import java.awt.BorderLayout;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JPanel;

import com.wudsn.tools.base.gui.ElementFactory;
import com.wudsn.tools.base.gui.ModalDialog;
import com.wudsn.tools.base.gui.ValueSetField;
import org.atari.raster.rmt.model.TrackerDriverVersion;

/**
 * "Module Properties" - the properties of the module being edited, behind
 * File &gt; Properties... and Alt+Enter: the video standard, the tracker
 * driver version and, through the Tuning... button, how its notes are tuned.
 *
 * <p>All three were in the Options dialog until plan 44, which is a dialog
 * for the program's settings; these belong to the song
 * ({@code CSong::IsNTSC()}, {@code GetTrackerDriverVersion()},
 * {@code GetTuning()}). They left Options as they arrived here, so there is
 * still exactly one control for each. Where they are persisted is unchanged
 * ({@code rmt.ini} and {@code tuning.ini}).
 *
 * <p>It edits a {@link ModulePropertiesValues};
 * {@link RmtCommands#applyModuleProperties} applies the result. The Tuning...
 * button opens {@link TuningDialog}, which writes the song's tuning itself -
 * as it did from the Options dialog, outside the OK/Cancel round trip.
 */
final class ModulePropertiesDialog extends ModalDialog {

	private static final long serialVersionUID = 1L;

	private final RmtSession session;
	private final ModulePropertiesValues values;

	private final JCheckBox ntscBox = ElementFactory.createCheckBox(DataTypes.ModulePropertiesDialog_NTSC);
	/** {@code m_trackerDriverVersionComboBox}: the versions C++ offers, in its order (not every value is offered). */
	private final ValueSetField<TrackerDriverVersion> driverVersionCombo = new ValueSetField<>(TrackerDriverVersion.getSelectableValues());
	private final JButton tuningButton = ElementFactory.createButton(Actions.ModulePropertiesDialog_Tuning, true);
	private boolean filled;

	ModulePropertiesDialog(JFrame parent, RmtSession session, ModulePropertiesValues values) {
		super(parent, Texts.ModulePropertiesDialog_Title);
		this.session = session;
		this.values = values;

		JPanel module = DialogSupport.createGroup(Texts.ModulePropertiesDialog_GroupModule);
		DialogSupport.add(module, ntscBox, 0, 0, 2, false);
		DialogSupport.add(module, ElementFactory.createLabel(DataTypes.ModulePropertiesDialog_TrackerDriverVersion, driverVersionCombo), 0, 1, 1, false);
		DialogSupport.add(module, driverVersionCombo, 1, 1, 1, true);
		getContentPane().add(module, BorderLayout.CENTER);

		tuningButton.addActionListener(e -> onClickedTuning());
		addButtonBarButton(tuningButton);
		driverVersionCombo.addActionListener(e -> updateTuningButton());
	}

	/**
	 * The tuning only reaches the Patch 16 driver's tables
	 * ({@link TrackerDriverVersion#isTuningSupported()}), so the button follows
	 * the version that is selected right now, not the one the song carried when
	 * the dialog opened.
	 */
	private void updateTuningButton() {
		TrackerDriverVersion selected = driverVersionCombo.getValue();
		tuningButton.setEnabled(selected != null && selected.isTuningSupported());
	}

	/** {@code OnClickedOptionsTuning()}, from here now. */
	private void onClickedTuning() {
		new TuningDialog((JFrame) getOwner(), session).showDialog();
	}

	@Override
	protected void dataToUi() {
		if (filled) {
			return; // keep what the user chose across an OK round trip
		}
		filled = true;
		ntscBox.setSelected(values.ntsc);
		if (values.trackerDriverVersion.isSelectable()) {
			driverVersionCombo.setValue(values.trackerDriverVersion);
		} else {
			driverVersionCombo.setSelectedIndex(-1); // nothing selected, like CComboBox for an unlisted item
		}
		updateTuningButton();
	}

	@Override
	protected void dataFromUi() {
		values.ntsc = ntscBox.isSelected();
		TrackerDriverVersion driver = driverVersionCombo.getValue();
		values.trackerDriverVersion = driver != null ? driver : TrackerDriverVersion.NONE; // GetSelectedItem(TrackerDriverVersion::NONE)
	}

	/** {@code DoModal() == IDOK}. */
	boolean showDialog() {
		showModal(ntscBox);
		return okPressed;
	}
}
