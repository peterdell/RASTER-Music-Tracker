package com.wudsn.tools.rmt.ui;

import java.awt.BorderLayout;
import java.awt.Component;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.SwingConstants;
import javax.swing.UIManager;

import com.wudsn.tools.base.gui.ModalDialog;

/**
 * "Import of module finished." - the port of {@code CImportModFinishedDlg}
 * and {@code CImportTmcFinishedDlg} ({@code IDD_IMPORTMODFINISHED}/
 * {@code IDD_IMPORTTMCFINISHED}, which differ only in title and warning
 * text): the result summary, the warning about what still needs manual
 * work, and the "Yes... OK, OK... I understand." box that has to be
 * checked before OK is enabled - a check remembered for the rest of the
 * session ({@code g_importmodyesokok}/{@code g_importtmcyesokok}).
 */
final class ImportFinishedDialog extends ModalDialog {

	private static final long serialVersionUID = 1L;

	static final String MOD_WARNING = "Please, now you have to look over all the instuments and set up proper envelopes distortions (drums, basses, etc.), also you must correct instruments tunings according to the tuning of original samples and improve them (chords, effects, noises, etc.).";
	static final String TMC_WARNING = "Please, now you have to look over all the instuments and whole song and check if it's all right, otherwise you must correct it manually (some special instrument effects aren't converted automatically). Also some stereo and AUDCTL events may be wrong, because of different stereo and AUDCTL conception in TMC and RMT.";

	private final JCheckBox check1;
	private final JButton okButton;

	ImportFinishedDialog(JFrame parent, boolean mod, String info, boolean understood) {
		super(parent, mod ? Texts.ImportModDialog_Title : Texts.ImportTmcFinishedDialog_Title);
		check1 = new JCheckBox(mod ? "Yes... OK, OK... I understand." : "Yes... Ok, ok... I understand.", understood);
		okButton = findOkButton(getContentPane());

		JPanel grid = new JPanel(new java.awt.GridBagLayout());
		JLabel title = new JLabel("Import of module finished.", SwingConstants.CENTER);
		DialogSupport.add(grid, title, 0, 0, 2, true);
		JTextArea infoArea = new JTextArea(info);
		infoArea.setEditable(false);
		infoArea.setOpaque(false);
		infoArea.setFont(title.getFont());
		DialogSupport.add(grid, infoArea, 0, 1, 2, true);
		JTextArea warning = new JTextArea(mod ? MOD_WARNING : TMC_WARNING, 4, 50);
		warning.setLineWrap(true);
		warning.setWrapStyleWord(true);
		warning.setEditable(false);
		warning.setOpaque(false);
		warning.setFont(title.getFont());
		DialogSupport.add(grid, warning, 0, 2, 2, true);
		DialogSupport.add(grid, new JLabel(UIManager.getIcon("OptionPane.warningIcon")), 0, 3, 1, false); // IDI_ICON_EXCLAMATION
		DialogSupport.add(grid, check1, 1, 3, 1, false);
		getContentPane().add(grid, BorderLayout.CENTER);

		check1.addActionListener(e -> onCheck1());
		onCheck1();
	}

	/** {@code m_okbutt.EnableWindow(g_importmodyesokok)}. */
	private void onCheck1() {
		if (okButton != null) {
			okButton.setEnabled(check1.isSelected());
		}
	}

	/** {@code ModalDialog} keeps its OK button private; it is the first button of the button bar. */
	private static JButton findOkButton(java.awt.Container root) {
		for (Component c : root.getComponents()) {
			if (c instanceof JButton button) {
				return button;
			}
			if (c instanceof java.awt.Container container) {
				JButton found = findOkButton(container);
				if (found != null) {
					return found;
				}
			}
		}
		return null;
	}

	boolean isUnderstood() {
		return check1.isSelected();
	}

	/** {@code DoModal() == IDOK}. */
	boolean showDialog() {
		showModal(check1);
		return okPressed;
	}
}
