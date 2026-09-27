package org.atari.raster.rmt.ui;

import java.awt.BorderLayout;

import javax.swing.ButtonGroup;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;

import com.wudsn.tools.base.gui.ModalDialog;

/**
 * "Import ProTracker Module" - the port of {@code CImportModDlg}
 * ({@code IDD_IMPORTMOD}): the detected-module text, the two RMT-type
 * radios (texts chosen by the caller per channel count), and the option
 * boxes - all checked by default except the disabled Fourier one; the
 * "decrease envelopes" box follows the "increase volume entries" box
 * ({@code OnCheck2}).
 */
final class ImportModDialog extends ModalDialog {

	private static final long serialVersionUID = 1L;

	private final JRadioButton radio1 = new JRadioButton();
	private final JRadioButton radio2 = new JRadioButton();
	private final JCheckBox check1 = new JCheckBox("Shift down octave of all instruments if song tuning is too high and if it is possible.", true);
	private final JCheckBox check5 = new JCheckBox("Substitute all portamento effects by inserting of calculated notes.", true);
	private final JCheckBox check2 = new JCheckBox("Increase the volume entries in tracks to spread full volume range.", true);
	private final JCheckBox check3 = new JCheckBox("Decrease the instruments' volume envelopes in accordance with tracks entries increasing.", true);
	private final JCheckBox check4 = new JCheckBox("Decrease the instruments' volume envelopes according to sample volume entry.", true);
	private final JCheckBox check8 = new JCheckBox("Use the Fourier transformation for detection of samples' tunings.", false);
	private final JCheckBox check6 = new JCheckBox("Search and build wise loops in tracks.", true);
	private final JCheckBox check7 = new JCheckBox("Truncate unused parts of tracks (only if it has data saving effect).", true);

	ImportModDialog(JFrame parent, String info, String txtRadio1, String txtRadio2) {
		super(parent, Texts.ImportModDialog_Title);
		radio1.setText(txtRadio1);
		radio2.setText(txtRadio2);
		ButtonGroup group = new ButtonGroup();
		group.add(radio1);
		group.add(radio2);
		radio1.setSelected(true);
		check8.setEnabled(false); // WS_DISABLED
		check2.addActionListener(e -> onCheck2());

		JPanel grid = new JPanel(new java.awt.GridBagLayout());
		int row = 0;
		for (String line : info.split("\n")) {
			DialogSupport.add(grid, new JLabel(line), 0, row++, 1, false);
		}
		JPanel type = DialogSupport.createGroup(Texts.ImportModDialog_GroupType);
		DialogSupport.add(type, radio1, 0, 0, 1, false);
		DialogSupport.add(type, radio2, 0, 1, 1, false);
		DialogSupport.add(grid, type, 0, row++, 1, true);
		DialogSupport.add(grid, new JLabel("Note events:"), 0, row++, 1, false);
		DialogSupport.add(grid, check1, 0, row++, 1, false);
		DialogSupport.add(grid, check5, 0, row++, 1, false);
		DialogSupport.add(grid, new JLabel("Volume events:"), 0, row++, 1, false);
		DialogSupport.add(grid, check2, 0, row++, 1, false);
		JPanel indented = new JPanel(new BorderLayout());
		indented.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 24, 0, 0));
		indented.add(check3, BorderLayout.CENTER);
		DialogSupport.add(grid, indented, 0, row++, 1, false);
		DialogSupport.add(grid, check4, 0, row++, 1, false);
		DialogSupport.add(grid, new JLabel("Special:"), 0, row++, 1, false);
		DialogSupport.add(grid, check8, 0, row++, 1, false);
		DialogSupport.add(grid, new JLabel("Size optimizations:"), 0, row++, 1, false);
		DialogSupport.add(grid, check6, 0, row++, 1, false);
		DialogSupport.add(grid, check7, 0, row++, 1, false);
		getContentPane().add(grid, BorderLayout.CENTER);
	}

	/** {@code OnCheck2()}: without the volume increase there is nothing to compensate in the envelopes. */
	private void onCheck2() {
		if (!check2.isSelected()) {
			check3.setSelected(false);
			check3.setEnabled(false);
		} else {
			check3.setEnabled(true);
			check3.setSelected(true);
		}
	}

	/** {@code DoModal()}: the choice, or {@code null} if cancelled. */
	SongFiles.ImportModChoice showDialog() {
		showModal(radio1);
		if (!okPressed) {
			return null;
		}
		return new SongFiles.ImportModChoice(radio1.isSelected(), check1.isSelected(), check5.isSelected(), check2.isSelected(), check3.isSelected(), check4.isSelected(), check6.isSelected(), check7.isSelected());
	}
}
