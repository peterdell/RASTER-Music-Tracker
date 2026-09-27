package com.wudsn.tools.rmt.ui;

import java.awt.BorderLayout;

import javax.swing.ButtonGroup;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;

import com.wudsn.tools.base.gui.ModalDialog;

/**
 * "Renumber All Tracks" / "Renumber All Instruments" - the ports of
 * {@code CRenumberTracksDlg} ({@code IDD_RENUMBERTRACKS}, two orders) and
 * {@code CRenumberInstrumentsDlg} ({@code IDD_RENUMBERINSTRUMENTS}, three
 * orders): a question, the radios (first one preselected) and "Are you
 * sure?". {@link #showDialog} returns the 1-based radio, 0 if cancelled.
 */
final class RenumberDialogs extends ModalDialog {

	private static final long serialVersionUID = 1L;

	static final String[] TRACK_ORDERS = { "Order by songcolumns at first.", "Order by songlines at first." };
	static final String[] INSTRUMENT_ORDERS = { "No order change. Remove gaps between instrument slots.", "Order by use in tracks.", "Order by instrument names (alphabetical)." };

	private final JRadioButton[] radios;

	private RenumberDialogs(JFrame parent, String title, String question, String[] orders) {
		super(parent, title);
		radios = new JRadioButton[orders.length];
		ButtonGroup group = new ButtonGroup();
		JPanel grid = new JPanel(new java.awt.GridBagLayout());
		int row = 0;
		DialogSupport.add(grid, new JLabel(question), 0, row++, 1, false);
		for (int i = 0; i < orders.length; i++) {
			radios[i] = new JRadioButton(orders[i], i == 0);
			group.add(radios[i]);
			DialogSupport.add(grid, radios[i], 0, row++, 1, false);
		}
		DialogSupport.add(grid, new JLabel("Are you sure?"), 0, row++, 1, false);
		getContentPane().add(grid, BorderLayout.CENTER);
	}

	static RenumberDialogs tracks(JFrame parent) {
		return new RenumberDialogs(parent, Texts.RenumberTracksDialog_Title, "Renumber all tracks:", TRACK_ORDERS);
	}

	static RenumberDialogs instruments(JFrame parent) {
		return new RenumberDialogs(parent, Texts.RenumberInstrumentsDialog_Title, "Renumber all instruments:", INSTRUMENT_ORDERS);
	}

	/** {@code DoModal()} + {@code OnOK()}'s {@code m_radio}: 1..n, or 0 if cancelled. */
	int showDialog() {
		showModal(radios[0]);
		if (!okPressed) {
			return 0;
		}
		for (int i = 0; i < radios.length; i++) {
			if (radios[i].isSelected()) {
				return i + 1;
			}
		}
		return 0;
	}
}
