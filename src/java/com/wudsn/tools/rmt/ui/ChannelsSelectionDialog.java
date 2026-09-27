package com.wudsn.tools.rmt.ui;

import java.awt.BorderLayout;

import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JPanel;

import com.wudsn.tools.base.gui.ModalDialog;

/** "Channels selection" - the port of {@code CChannelsSelectionDlg} ({@code IDD_CHANNELSSELECT}): L1-L4 / R1-R4 boxes (the right ones disabled for a mono song), the result a bit mask. */
final class ChannelsSelectionDialog extends ModalDialog {

	private static final long serialVersionUID = 1L;

	static final String[] NAMES = { "L1", "L2", "L3", "L4", "R1", "R2", "R3", "R4" };

	private final JCheckBox[] boxes = new JCheckBox[8];

	ChannelsSelectionDialog(JFrame parent, int tracks4_8) {
		super(parent, Texts.ChannelsSelectionDialog_Title);
		JPanel left = DialogSupport.createGroup("Left");
		JPanel right = DialogSupport.createGroup("Right");
		for (int i = 0; i < 8; i++) {
			boxes[i] = new JCheckBox(NAMES[i]);
			DialogSupport.add(i < 4 ? left : right, boxes[i], i % 4, 0, 1, false);
			if (i >= 4 && tracks4_8 <= 4) {
				boxes[i].setEnabled(false);
			}
		}
		JPanel row = new JPanel(new java.awt.GridLayout(1, 2, 8, 0));
		row.add(left);
		row.add(right);
		getContentPane().add(row, BorderLayout.CENTER);
	}

	/** {@code DoModal()}: {@code m_channelyes} (bit i = channel i), or -1 if cancelled. */
	int showDialog() {
		showModal(boxes[0]);
		if (!okPressed) {
			return -1;
		}
		int channelyes = 0;
		for (int i = 0; i < 8; i++) {
			if (boxes[i].isSelected()) {
				channelyes |= 1 << i;
			}
		}
		return channelyes;
	}
}
