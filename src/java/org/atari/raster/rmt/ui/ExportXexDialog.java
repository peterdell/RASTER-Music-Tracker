package org.atari.raster.rmt.ui;

import java.awt.BorderLayout;
import java.awt.Font;

import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JToggleButton;
import javax.swing.SwingConstants;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import com.wudsn.tools.base.gui.ModalDialog;

/**
 * "Export as Atari Executable File" - the port of {@code CExpMSXDlg}
 * ({@code IDD_EXPMSX}): the 4+1 lines of on-screen text with the live
 * 40-column preview (line 5 replacing line 4 while the "Atari SHIFTkey
 * test" toggle is pressed), the rasterbar option with its color scrollbar
 * (1..127, color = 2x, with the PAL color name), the shuffle option, the
 * speed information and the automatic region adjustment.
 */
final class ExportXexDialog extends ModalDialog {

	private static final long serialVersionUID = 1L;

	/** {@code bar[]}: the 16 PAL hue names (NTSC names "may or may not be correct yet" in C++, left out there too). */
	static final String[] COLOR_NAMES = { "Gray", "Rust", "Orange", "Red-orange", "Pink", "Purple", "Cobalt blue", "Blue", "Medium blue", "Dark blue", "Blue-grey", "Olive green", "Medium green", "Dark green", "Orange-green", "Brown" };

	private final JTextArea editArea = new JTextArea(5, 44);
	private final JTextArea previewArea = new JTextArea(4, 40);
	private final JToggleButton shiftTest = new JToggleButton("Atari SHIFTkey test");
	private final JCheckBox rasterbarBox = new JCheckBox("Display rasterbar for CPU usage");
	private final JCheckBox shuffleBox = new JCheckBox("Shuffle the rasterbar colors");
	private final JScrollBar colorBar = new JScrollBar(JScrollBar.HORIZONTAL, 3, 1, 1, 128);
	private final JLabel colorLabel = new JLabel();
	private final JCheckBox regionAutoBox = new JCheckBox("Automatically adjust playback speed");
	/** {@code m_txt} after {@code ChangeParams()}: the text cut to 5 lines of 40. */
	private String txt = "";
	private int color;

	ExportXexDialog(JFrame parent, ExportSettings es, String text, String speedInfo) {
		super(parent, Texts.ExportXexDialog_Title);
		previewArea.setEditable(false);
		previewArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, previewArea.getFont().getSize()));
		rasterbarBox.setSelected(es.msxRasterbar);
		shuffleBox.setSelected(es.msxShuffle);
		regionAutoBox.setSelected(es.msxRegionAuto);
		colorBar.setValue(Math.max(1, Math.min(127, es.msxColor / 2)));
		setColor(colorBar.getValue());
		editArea.setText(text);

		JPanel grid = new JPanel(new java.awt.GridBagLayout());
		int row = 0;
		DialogSupport.add(grid, new JLabel("<html>Text displayed on screen during music playback. 4+1 lines, 40 characters per line.<br>The 5th line of text is shown instead of 4th line when the Shift key is held down.</html>"), 0, row++, 4, true);
		DialogSupport.add(grid, new JScrollPane(editArea), 0, row++, 4, true);
		DialogSupport.add(grid, new JLabel("MSX screen preview", SwingConstants.CENTER), 0, row++, 4, true);
		DialogSupport.add(grid, new JScrollPane(previewArea), 0, row++, 4, true);
		DialogSupport.add(grid, shiftTest, 0, row++, 4, false);
		DialogSupport.add(grid, rasterbarBox, 0, row, 2, false);
		DialogSupport.add(grid, shuffleBox, 2, row++, 2, false);
		DialogSupport.add(grid, new JLabel("Color:"), 0, row, 1, false);
		DialogSupport.add(grid, colorBar, 1, row, 2, true);
		DialogSupport.add(grid, colorLabel, 3, row++, 1, false);
		DialogSupport.add(grid, new JLabel(speedInfo), 0, row++, 4, true);
		DialogSupport.add(grid, regionAutoBox, 0, row++, 4, false);
		getContentPane().add(grid, BorderLayout.CENTER);

		editArea.getDocument().addDocumentListener(new DocumentListener() {
			@Override
			public void insertUpdate(DocumentEvent e) {
				changeParams();
			}

			@Override
			public void removeUpdate(DocumentEvent e) {
				changeParams();
			}

			@Override
			public void changedUpdate(DocumentEvent e) {
				changeParams();
			}
		});
		shiftTest.addActionListener(e -> changeParams());
		rasterbarBox.addActionListener(e -> changeParams());
		shuffleBox.addActionListener(e -> changeParams());
		colorBar.addAdjustmentListener(e -> setColor(colorBar.getValue()));
		changeParams();
	}

	/** {@code OnHScroll}: {@code g_msxcol = c * 2} and the "6 = Gray 6" text. */
	private void setColor(int c) {
		color = c * 2;
		colorLabel.setText(color + " = " + COLOR_NAMES[color / 16] + " " + color % 16);
	}

	/** {@code ChangeParams()}: cut the text to 5 lines of 40 characters, show lines 1-4 (or 1-3 and 5) in the preview, enable the rasterbar controls. */
	void changeParams() {
		String s = editArea.getText();
		StringBuilder d = new StringBuilder();
		StringBuilder d4th = new StringBuilder();
		StringBuilder d5th = new StringBuilder();
		int from = 0;
		int line = 0;
		while (from < s.length() && line < 5) {
			int i = s.indexOf('\n', from);
			String l;
			if (i >= 0) {
				int len = Math.min(i - from, 40);
				l = s.substring(from, from + len) + "\n";
				from = i + 1;
			} else {
				l = s.substring(from, Math.min(s.length(), from + 40));
				from = s.length();
			}
			d.append(l);
			if (line != 4) {
				d4th.append(l); // without line 5
			}
			if (line != 3) {
				d5th.append(l); // without line 4
			}
			line++;
		}
		previewArea.setText(shiftTest.isSelected() ? d5th.toString() : d4th.toString());
		txt = d.toString();
		boolean meter = rasterbarBox.isSelected();
		colorBar.setEnabled(meter);
		colorLabel.setEnabled(meter);
		shuffleBox.setEnabled(meter);
	}

	/** {@code DoModal()}: the choice, or {@code null} if cancelled. */
	SongFiles.XexChoice showDialog() {
		showModal(editArea);
		if (!okPressed) {
			return null;
		}
		changeParams();
		return new SongFiles.XexChoice(txt, rasterbarBox.isSelected(), shuffleBox.isSelected(), regionAutoBox.isSelected(), color);
	}
}
