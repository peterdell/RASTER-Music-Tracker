package com.wudsn.tools.rmt.ui;

import java.awt.BorderLayout;

import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingConstants;

import com.wudsn.tools.base.gui.ModalDialog;
import com.wudsn.tools.rmt.model.Track;

/** "Change maximal length of tracks" - the port of {@code CChangeMaxtracklenDlg} ({@code IDD_CHANGEMAXTRACKLEN}): the current/effective values, the new length (1..256, {@code DDV_MinMaxInt}) and the warning. */
final class ChangeMaxTrackLengthDialog extends ModalDialog {

	private static final long serialVersionUID = 1L;

	private final JTextField lengthField = new JTextField(4);

	ChangeMaxTrackLengthDialog(JFrame parent, String info, int maxTrackLength) {
		super(parent, Texts.ChangeMaxTrackLengthDialog_Title);
		lengthField.setText(Integer.toString(maxTrackLength));
		JPanel grid = new JPanel(new java.awt.GridBagLayout());
		int row = 0;
		DialogSupport.add(grid, new JLabel("Change maximal length of tracks"), 0, row++, 2, false);
		for (String line : info.split("\n")) {
			DialogSupport.add(grid, new JLabel(line), 0, row++, 2, false);
		}
		DialogSupport.add(grid, new JLabel("New maximal length of tracks"), 0, row, 1, false);
		DialogSupport.add(grid, lengthField, 1, row++, 1, false);
		DialogSupport.add(grid, new JLabel("Warning: All tracks will be prolonged or truncated!", SwingConstants.CENTER), 0, row++, 2, true);
		getContentPane().add(grid, BorderLayout.CENTER);
	}

	@Override
	protected boolean validateOK() {
		return DialogSupport.validateInt(this, lengthField, 1, Track.TRACKLEN);
	}

	/** {@code DoModal()}: the new length, or -1 if cancelled. */
	int showDialog() {
		showModal(lengthField);
		return okPressed ? DialogSupport.parseInt(lengthField) : -1;
	}
}
