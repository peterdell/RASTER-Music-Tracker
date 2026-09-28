package org.atari.raster.rmt.ui;

import java.awt.BorderLayout;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JTextField;

import com.wudsn.tools.base.gui.ElementFactory;
import com.wudsn.tools.base.gui.ModalDialog;
import org.atari.raster.rmt.model.SapFile;

/** "Export as SAP File of Type 'R'/'B'" - the port of {@code CSAPFileExportDialog} ({@code IDD_EXPORT_SAP_TYPE_R}): title, author, date and the subsong list. */
final class ExportSapDialog extends ModalDialog {

	private static final long serialVersionUID = 1L;

	private final JTextField nameField = new JTextField(40);
	private final JTextField authorField = new JTextField(40);
	private final JTextField dateField = new JTextField(10);
	private final JTextField subsongsField = new JTextField(40);

	ExportSapDialog(JFrame parent, SapFile sapFile, String subsongs) {
		super(parent, "Export as SAP File of Type '" + sapFile.getType() + "'");
		nameField.setText(sapFile.getName());
		authorField.setText(sapFile.getAuthor());
		dateField.setText(sapFile.getDate());
		subsongsField.setText(subsongs);

		JPanel grid = new JPanel(new java.awt.GridBagLayout());
		DialogSupport.add(grid, ElementFactory.createLabel(DataTypes.ExportSapDialog_Title, nameField), 0, 0, 1, false);
		DialogSupport.add(grid, nameField, 0, 1, 1, true);
		DialogSupport.add(grid, ElementFactory.createLabel(DataTypes.ExportSapDialog_Author, authorField), 0, 2, 1, false);
		DialogSupport.add(grid, authorField, 0, 3, 1, true);
		DialogSupport.add(grid, ElementFactory.createLabel(DataTypes.ExportSapDialog_Date, dateField), 0, 4, 1, false);
		DialogSupport.add(grid, dateField, 0, 5, 1, false);
		DialogSupport.add(grid, ElementFactory.createLabel(DataTypes.ExportSapDialog_Subsongs, subsongsField), 0, 6, 1, false);
		DialogSupport.add(grid, subsongsField, 0, 7, 1, true);
		getContentPane().add(grid, BorderLayout.CENTER);
	}

	/** {@code DoModal()}: the choice, or {@code null} if cancelled. */
	SongFiles.SapChoice showDialog() {
		showModal(nameField);
		if (!okPressed) {
			return null;
		}
		return new SongFiles.SapChoice(authorField.getText(), nameField.getText(), dateField.getText(), subsongsField.getText());
	}
}
