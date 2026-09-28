package org.atari.raster.rmt.ui;

import java.awt.BorderLayout;

import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;

import com.wudsn.tools.base.gui.ElementFactory;
import com.wudsn.tools.base.gui.ModalDialog;
import org.atari.raster.rmt.model.Track;

/**
 * "New Module" - the port of {@code CFileNewDlg} ({@code IDD_FILE_NEW}): the
 * mono/stereo choice and the notes per track (1-256; above 64 a warning
 * asks for confirmation, as {@code OnOK()} does).
 */
final class FileNewDialog extends ModalDialog {

	private static final long serialVersionUID = 1L;

	/** {@code IDC_COMBOTYPE}'s entries (Rmt.rc's DLGINIT): index 0 = mono 4 tracks, 1 = stereo 8 tracks. */
	static final String[] TYPES = { "Mono - 4 Tracks", "Stereo - 8 Tracks" };

	/** {@code m_maxTrackLength}, default 64. */
	int maxTrackLength = 64;
	/** {@code m_comboMonoOrStereo}, default 1 = stereo. */
	int comboMonoOrStereo = 1;

	private final JComboBox<String> typeCombo = new JComboBox<>(TYPES);
	private final JTextField maxTrackLengthField = new JTextField(8);
	private boolean filled;

	FileNewDialog(JFrame parent) {
		super(parent, Texts.FileNewDialog_Title);
		JPanel grid = new JPanel(new java.awt.GridBagLayout());
		DialogSupport.add(grid, ElementFactory.createLabel(DataTypes.FileNewDialog_Tracks, typeCombo), 0, 0, 1, false);
		DialogSupport.add(grid, typeCombo, 1, 0, 1, true);
		DialogSupport.add(grid, ElementFactory.createLabel(DataTypes.FileNewDialog_NotesPerTrack, maxTrackLengthField), 0, 1, 1, false);
		DialogSupport.add(grid, maxTrackLengthField, 1, 1, 1, true);
		getContentPane().add(grid, BorderLayout.CENTER);
	}

	@Override
	protected void dataToUi() {
		if (filled) {
			return;
		}
		filled = true;
		typeCombo.setSelectedIndex(comboMonoOrStereo);
		maxTrackLengthField.setText(Integer.toString(maxTrackLength));
	}

	@Override
	protected void dataFromUi() {
		if (!DialogSupport.validateInt(this, maxTrackLengthField, 1, 256)) {
			return;
		}
		maxTrackLength = DialogSupport.parseInt(maxTrackLengthField);
		comboMonoOrStereo = typeCombo.getSelectedIndex();
	}

	/** {@code CFileNewDlg::OnOK()}: the DDV range, then the "greater than 64" warning the user must accept. */
	@Override
	protected boolean validateOK() {
		Integer mtl = DialogSupport.parseInt(maxTrackLengthField);
		if (mtl == null || mtl < 1 || mtl > 256) {
			return false;
		}
		if (mtl > 64 && mtl <= Track.TRACKLEN) {
			int r = JOptionPane.showConfirmDialog(this,
					"Warning:\nLength of tracks is greater than 64.\nRMT's internal module format allows for a maximum of\n256 bytes for each track. It is not recommended to use\na large number of events in long tracks.\nEach track event (note or speed command) uses about 2 bytes.\n\nWhen saving the RMT file it will report any problems with it.\n\nOk?",
					"New RMT module - Warning", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
			return r == JOptionPane.YES_OPTION;
		}
		return true;
	}

	/** {@code DoModal() == IDOK}. */
	boolean showDialog() {
		showModal(maxTrackLengthField);
		return okPressed;
	}
}
