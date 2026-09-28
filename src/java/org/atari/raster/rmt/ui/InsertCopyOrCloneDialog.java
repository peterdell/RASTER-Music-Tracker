package org.atari.raster.rmt.ui;

import java.awt.BorderLayout;

import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import com.wudsn.tools.base.gui.ModalDialog;
import org.atari.raster.rmt.model.Song;

/**
 * "Insert copy or clone of song line(s) into song" - the port of
 * {@code CInsertCopyOrCloneOfSongLinesDlg}
 * ({@code IDD_SONGINSERTCOPYORCLONEOFSONGLINES}): the source line range
 * (hex), the clone option with its tuning and volume fields, and the
 * "n lines will be inserted into $xx song line." info. {@code ValuesTest()}
 * clamps the fields and refuses OK with a warning if anything had to be
 * corrected.
 */
final class InsertCopyOrCloneDialog extends ModalDialog {

	private static final long serialVersionUID = 1L;

	private final int lineInto;
	private final JTextField lineFromField = new JTextField(3);
	private final JTextField lineToField = new JTextField(3);
	private final JCheckBox cloneBox = new JCheckBox("Clone tracks");
	private final JLabel tuningLabel = new JLabel("Tuning (+/- halftones):", SwingConstants.RIGHT);
	private final JTextField tuningField = new JTextField(3);
	private final JLabel volumeLabel = new JLabel("Volume (%):", SwingConstants.RIGHT);
	private final JTextField volumeField = new JTextField(4);
	private final JLabel infoLabel = new JLabel("...", SwingConstants.CENTER);

	/** {@code m_linefrom/m_lineto/m_clone/m_tuning/m_volumep} after {@code ValuesTest()}. */
	private int lineFrom;
	private int lineTo;
	private boolean clone;
	private int tuning;
	private int volumep;
	private boolean updating;

	InsertCopyOrCloneDialog(JFrame parent, int lineFrom, int lineTo, int lineInto) {
		super(parent, Texts.InsertCopyOrCloneDialog_Title);
		this.lineInto = lineInto;
		lineFromField.setText(String.format("%02X", lineFrom));
		lineToField.setText(String.format("%02X", lineTo));
		cloneBox.setSelected(false);
		tuningField.setText("0");
		volumeField.setText("100"); // 100%

		JPanel grid = new JPanel(new java.awt.GridBagLayout());
		JPanel range = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 4, 0));
		range.add(new JLabel("Source songline(s): From line $"));
		range.add(lineFromField);
		range.add(new JLabel("to $"));
		range.add(lineToField);
		DialogSupport.add(grid, range, 0, 0, 4, true);
		DialogSupport.add(grid, infoLabel, 0, 1, 4, true);
		DialogSupport.add(grid, cloneBox, 0, 2, 4, false);
		DialogSupport.add(grid, tuningLabel, 0, 3, 1, false);
		DialogSupport.add(grid, tuningField, 1, 3, 1, false);
		DialogSupport.add(grid, volumeLabel, 2, 3, 1, false);
		DialogSupport.add(grid, volumeField, 3, 3, 1, false);
		getContentPane().add(grid, BorderLayout.CENTER);

		cloneBox.addActionListener(e -> valuesTest());
		DocumentListener onRange = new DocumentListener() {
			@Override
			public void insertUpdate(DocumentEvent e) {
				onChangeSonglineRange();
			}

			@Override
			public void removeUpdate(DocumentEvent e) {
				onChangeSonglineRange();
			}

			@Override
			public void changedUpdate(DocumentEvent e) {
				onChangeSonglineRange();
			}
		};
		lineFromField.getDocument().addDocumentListener(onRange);
		lineToField.getDocument().addDocumentListener(onRange);
		valuesTest();
	}

	/** {@code Hexstr(s, 4)}: -1 if the text doesn't start with a hex digit. */
	private static int hex(JTextField field) {
		return Song.hexstr(field.getText().trim(), 0, 4);
	}

	private static int atoi(JTextField field) {
		return RmtConfig.atoi(field.getText());
	}

	private void setLater(JTextField field, String text) {
		if (!field.getText().equals(text)) {
			javax.swing.SwingUtilities.invokeLater(() -> {
				updating = true;
				field.setText(text);
				updating = false;
			});
		}
	}

	/** {@code ValuesTest()}: enable the clone fields per the box, clamp both hex lines into the song and the volume into 0..1600, and report whether nothing had to be changed. */
	boolean valuesTest() {
		boolean r = true;
		clone = cloneBox.isSelected();
		tuningLabel.setEnabled(clone);
		volumeLabel.setEnabled(clone);
		tuningField.setEnabled(clone);
		volumeField.setEnabled(clone);

		int c = hex(lineFromField);
		if (c < 0) {
			c = 0;
			r = false;
		} else if (c >= Song.SONGLEN) {
			c = Song.SONGLEN - 1;
			r = false;
		}
		lineFrom = c;
		setLater(lineFromField, String.format("%02X", c));

		c = hex(lineToField);
		if (c < 0) {
			c = 0;
			r = false;
		} else if (c >= Song.SONGLEN) {
			c = Song.SONGLEN - 1;
			r = false;
		}
		if (c < lineFrom) {
			c = lineFrom; // it can't be smaller
			r = false;
		}
		lineTo = c;
		setLater(lineToField, String.format("%02X", c));

		onChangeSonglineRange();

		tuning = atoi(tuningField);

		c = atoi(volumeField);
		if (c < 0) {
			c = 0;
			r = false;
		} else if (c >= 1600) {
			c = 1600;
			r = false;
		}
		volumep = c;
		setLater(volumeField, Integer.toString(c));
		return r;
	}

	/** {@code OnChangeSonglinerange()}: the info line. */
	private void onChangeSonglineRange() {
		if (updating) {
			return;
		}
		int f = hex(lineFromField);
		int t = hex(lineToField);
		if (t < f) {
			t = f;
		}
		int n = t - f + 1;
		infoLabel.setText(n > 1 ? String.format("%d lines will be inserted into $%02X song line.", n, lineInto) : String.format("1 line will be inserted into $%02X song line.", lineInto));
	}

	/** {@code OnOK()}: a corrected value keeps the dialog open. */
	@Override
	protected boolean validateOK() {
		if (!valuesTest()) {
			JOptionPane.showMessageDialog(this, "Some parameters need to be corrected.\nPlease re-verify their values.", "Warning", JOptionPane.WARNING_MESSAGE);
			return false;
		}
		return true;
	}

	/** {@code DoModal()}: the choice, or {@code null} if cancelled. */
	SongDialogs.InsertCopyChoice showDialog() {
		showModal(lineFromField);
		if (!okPressed) {
			return null;
		}
		return new SongDialogs.InsertCopyChoice(lineFrom, lineTo, clone, tuning, volumep);
	}
}
