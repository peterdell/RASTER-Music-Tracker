package org.atari.raster.rmt.ui;

import java.awt.BorderLayout;

import javax.swing.ButtonGroup;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;

import com.wudsn.tools.base.gui.ModalDialog;

/**
 * "Tracks loading" - the port of {@code CTracksLoadDlg}
 * ({@code IDD_TRACKSLOAD}): a TXT file holds several tracks - load them
 * consecutively from the active track on, or to the places stored in the
 * file.
 */
final class TracksLoadDialog extends ModalDialog {

	private static final long serialVersionUID = 1L;

	/** {@code m_radio}: 0 = from the active track on, 1 = original places. */
	int radio;

	private final JRadioButton consecutive = new JRadioButton();
	private final JRadioButton originalPlaces = new JRadioButton("Load tracks to their original places stored in TXT file.");

	TracksLoadDialog(JFrame parent, int trackFrom, int trackNum) {
		super(parent, Texts.TracksLoadDialog_Title);
		ButtonGroup group = new ButtonGroup();
		group.add(consecutive);
		group.add(originalPlaces);
		consecutive.setText(String.format("Load tracks to $%02X-$%02X.", trackFrom, trackFrom + trackNum - 1));
		consecutive.setSelected(true);

		JPanel grid = new JPanel(new java.awt.GridBagLayout());
		DialogSupport.add(grid, new JLabel(String.format("There are %d tracks in TXT file.", trackNum)), 0, 0, 1, false);
		DialogSupport.add(grid, consecutive, 0, 1, 1, false);
		DialogSupport.add(grid, originalPlaces, 0, 2, 1, false);
		DialogSupport.add(grid, new JLabel("Are you sure?"), 0, 3, 1, false);
		getContentPane().add(grid, BorderLayout.CENTER);
	}

	@Override
	protected void dataFromUi() {
		radio = originalPlaces.isSelected() ? 1 : 0;
	}

	/** {@code DoModal()}: the chosen mode, or -1 if cancelled. */
	int showDialog() {
		showModal(consecutive);
		return okPressed ? radio : -1;
	}
}
