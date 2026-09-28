package org.atari.raster.rmt.ui;

import java.awt.BorderLayout;

import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

import com.wudsn.tools.base.gui.ElementFactory;
import com.wudsn.tools.base.gui.ModalDialog;
import com.wudsn.tools.base.repository.DataType;

/**
 * "Default Paths" - the port of {@code COptionsPathsDialog}
 * ({@code IDD_OPTIONS_FILE_PATHS}): three folder fields, each with a
 * Browse... button ({@code CFolderPickerDialog} -> a directories-only
 * {@link JFileChooser}). The caller ({@link OptionsDialog}) copies the paths
 * in and, on OK, out again.
 */
final class OptionsPathsDialog extends ModalDialog {

	private static final long serialVersionUID = 1L;

	/** {@code m_path_songs}. */
	String pathSongs = "";
	/** {@code m_path_instruments}. */
	String pathInstruments = "";
	/** {@code m_path_tracks}. */
	String pathTracks = "";

	private final JTextField songsField = new JTextField(40);
	private final JTextField instrumentsField = new JTextField(40);
	private final JTextField tracksField = new JTextField(40);
	private boolean filled;

	OptionsPathsDialog(JFrame parent) {
		super(parent, Texts.OptionsPathsDialog_Title);
		JPanel grid = new JPanel(new java.awt.GridBagLayout());
		addRow(grid, 0, DataTypes.OptionsPathsDialog_ModuleFilesFolder, songsField);
		addRow(grid, 1, DataTypes.OptionsPathsDialog_InstrumentFilesFolder, instrumentsField);
		addRow(grid, 2, DataTypes.OptionsPathsDialog_TrackFilesFolder, tracksField);
		getContentPane().add(grid, BorderLayout.CENTER);
	}

	private void addRow(JPanel grid, int row, DataType label, JTextField field) {
		JLabel jLabel = ElementFactory.createLabel(label, field);
		DialogSupport.add(grid, jLabel, 0, row * 2, 2, false);
		DialogSupport.add(grid, field, 0, row * 2 + 1, 1, true);
		// The one Browse action serves all three buttons (all visible at once), so no mnemonic - as dis6502's DefaultFoldersDialog does
		JButton browse = ElementFactory.createButton(Actions.OptionsPathsDialog_Browse, false);
		browse.addActionListener(e -> browsePath(field, label.getLabelWithoutMnemonics()));
		DialogSupport.add(grid, browse, 1, row * 2 + 1, 1, false);
	}

	/** {@code COptionsPathsDialog::BrowsePath()}: a folder picker starting at the field's current path, the pick written back into the field. */
	private void browsePath(JTextField field, String title) {
		JFileChooser chooser = new JFileChooser(field.getText());
		chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
		chooser.setDialogTitle(java.text.MessageFormat.format(Texts.OptionsPathsDialog_BrowseTitle, title));
		if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
			field.setText(chooser.getSelectedFile().getPath());
		}
	}

	@Override
	protected void dataToUi() {
		if (filled) {
			return; // keep whatever the user typed across an OK round trip
		}
		filled = true;
		songsField.setText(pathSongs);
		instrumentsField.setText(pathInstruments);
		tracksField.setText(pathTracks);
	}

	@Override
	protected void dataFromUi() {
		pathSongs = songsField.getText();
		pathInstruments = instrumentsField.getText();
		pathTracks = tracksField.getText();
	}

	/** {@code DoModal() == IDOK}. */
	boolean showDialog() {
		showModal(songsField);
		return okPressed;
	}
}
