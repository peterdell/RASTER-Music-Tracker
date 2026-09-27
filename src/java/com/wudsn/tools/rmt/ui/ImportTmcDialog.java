package com.wudsn.tools.rmt.ui;

import java.awt.BorderLayout;

import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;

import com.wudsn.tools.base.gui.ModalDialog;

/** "Import Theta Music Composer Module" - the port of {@code CImportTmcDlg} ({@code IDD_IMPORTTMC}): the module name and three options, all on by default. */
final class ImportTmcDialog extends ModalDialog {

	private static final long serialVersionUID = 1L;

	private final JCheckBox check1 = new JCheckBox("Permit to use the instrument table also for vibrato and some special TMC effects.", true);
	private final JCheckBox check6 = new JCheckBox("Search and build wise loops in tracks.", true);
	private final JCheckBox check7 = new JCheckBox("Truncate unused parts of tracks (only if it has data saving effect).", true);

	ImportTmcDialog(JFrame parent, String info) {
		super(parent, Texts.ImportTmcDialog_Title);
		JPanel grid = new JPanel(new java.awt.GridBagLayout());
		DialogSupport.add(grid, new JLabel(info), 0, 0, 1, false);
		DialogSupport.add(grid, new JLabel("Instruments:"), 0, 1, 1, false);
		DialogSupport.add(grid, check1, 0, 2, 1, false);
		DialogSupport.add(grid, new JLabel("Size optimizations:"), 0, 3, 1, false);
		DialogSupport.add(grid, check6, 0, 4, 1, false);
		DialogSupport.add(grid, check7, 0, 5, 1, false);
		getContentPane().add(grid, BorderLayout.CENTER);
	}

	/** {@code DoModal()}: the choice, or {@code null} if cancelled. */
	SongFiles.ImportTmcChoice showDialog() {
		showModal(check1);
		if (!okPressed) {
			return null;
		}
		return new SongFiles.ImportTmcChoice(check1.isSelected(), check6.isSelected(), check7.isSelected());
	}
}
