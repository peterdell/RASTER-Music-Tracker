package org.atari.raster.rmt.ui;

import java.awt.BorderLayout;

import javax.swing.ButtonGroup;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JTextField;

import com.wudsn.tools.base.gui.ModalDialog;
import org.atari.raster.rmt.model.Notes;

/**
 * "Export ASM simple notation source file" - the port of {@code CExportAsmDlg}
 * ({@code IDD_EXPORT_ASM}): three radio groups (export type, note values,
 * note durations) and the label prefix (at most 32 characters,
 * {@code DDV_MaxChars}).
 */
final class ExportAsmDialog extends ModalDialog {

	private static final long serialVersionUID = 1L;

	private final JRadioButton radio1 = new JRadioButton("Tracks", true);
	private final JRadioButton radio2 = new JRadioButton("Whole song by song columns");
	private final JRadioButton radio3 = new JRadioButton(String.format("Note indexes $00-$%02X", Notes.NOTESNUM - 1), true); // TODO (C++): Why -1? The note indexes are inclusive!?
	private final JRadioButton radio4 = new JRadioButton("Note frequencies according to distortion in first envelope column");
	private final JRadioButton radio5 = new JRadioButton("Notes only (special value XXX in empty beats)", true);
	private final JRadioButton radio6 = new JRadioButton("Pairs of note,duration");
	private final JRadioButton radio7 = new JRadioButton("Pairs of duration,note");
	private final JTextField prefixField = new JTextField(20);

	ExportAsmDialog(JFrame parent, String prefixForAllAsmLabels) {
		super(parent, Texts.ExportAsmDialog_Title);
		group(radio1, radio2);
		group(radio3, radio4);
		group(radio5, radio6, radio7);
		prefixField.setText(prefixForAllAsmLabels);

		JPanel grid = new JPanel(new java.awt.GridBagLayout());
		int row = 0;
		DialogSupport.add(grid, new JLabel("Export type"), 0, row++, 1, false);
		DialogSupport.add(grid, radio1, 0, row++, 1, false);
		DialogSupport.add(grid, radio2, 0, row++, 1, false);
		DialogSupport.add(grid, new JLabel("Note values"), 0, row++, 1, false);
		DialogSupport.add(grid, radio3, 0, row++, 1, false);
		DialogSupport.add(grid, radio4, 0, row++, 1, false);
		DialogSupport.add(grid, new JLabel("Note durations"), 0, row++, 1, false);
		DialogSupport.add(grid, radio5, 0, row++, 1, false);
		DialogSupport.add(grid, radio6, 0, row++, 1, false);
		DialogSupport.add(grid, radio7, 0, row++, 1, false);
		DialogSupport.add(grid, new JLabel("Generate labels with prefix (empty prefix => no labels)"), 0, row++, 1, false);
		DialogSupport.add(grid, prefixField, 0, row++, 1, false);
		getContentPane().add(grid, BorderLayout.CENTER);
	}

	private static void group(JRadioButton... buttons) {
		ButtonGroup group = new ButtonGroup();
		for (JRadioButton b : buttons) {
			group.add(b);
		}
	}

	/** {@code DDV_MaxChars(..., 32)}. */
	@Override
	protected boolean validateOK() {
		if (prefixField.getText().length() > 32) {
			javax.swing.JOptionPane.showMessageDialog(this, "Please enter no more than 32 characters.", null, javax.swing.JOptionPane.WARNING_MESSAGE);
			prefixField.requestFocusInWindow();
			return false;
		}
		return true;
	}

	/** {@code OnOK()}'s radio arithmetic: type 1/2, notes 1/2, durations 1/2/3. */
	SongFiles.AsmChoice showDialog() {
		showModal(radio1);
		if (!okPressed) {
			return null;
		}
		int exportType = radio1.isSelected() ? 1 : 2;
		int notesIndexOrFreq = radio3.isSelected() ? 1 : 2;
		int durationsType = radio5.isSelected() ? 1 : radio6.isSelected() ? 2 : 3;
		return new SongFiles.AsmChoice(exportType, notesIndexOrFreq, durationsType, prefixField.getText());
	}
}
