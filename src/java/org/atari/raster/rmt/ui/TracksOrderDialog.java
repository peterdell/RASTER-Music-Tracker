package org.atari.raster.rmt.ui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Rectangle;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingConstants;

import com.wudsn.tools.base.gui.ModalDialog;

/**
 * "Song columns' order change/copy/clear" - the port of
 * {@code CSongTracksOrderDlg} ({@code IDD_SONGTRACKSORDER}): a "From" row
 * of column buttons (plus "Nothing"), a "To" row, lines drawn between them
 * for the current assignment ({@code OnPaint}), the six preset buttons and
 * the song line range fields. Clicking a From button (or Nothing) picks the
 * source, clicking a To button assigns it ({@code OnL1R4}).
 */
final class TracksOrderDialog extends ModalDialog {

	private static final long serialVersionUID = 1L;

	private static final String[] NAMES = ChannelsSelectionDialog.NAMES;

	private final JButton[] fromButtons = new JButton[8];
	private final JButton[] toButtons = new JButton[8];
	private final JTextField fromField = new JTextField(3);
	private final JTextField toField = new JTextField(3);
	/** {@code m_tracksorder}. */
	final int[] tracksOrder = new int[8];
	private int fromTrack = -1;
	private final int tracks4_8;
	private final JPanel diagram;

	TracksOrderDialog(JFrame parent, int tracks4_8, String songLineFrom, String songLineTo) {
		super(parent, Texts.TracksOrderDialog_Title);
		this.tracks4_8 = tracks4_8;
		fromField.setText(songLineFrom);
		toField.setText(songLineTo);

		// The two button rows on a fixed-position panel so the assignment lines can be drawn between them
		diagram = new JPanel(null) {
			private static final long serialVersionUID = 1L;

			@Override
			protected void paintComponent(Graphics g) {
				super.paintComponent(g);
				for (int i = 0; i < TracksOrderDialog.this.tracks4_8; i++) {
					int z = tracksOrder[i];
					if (z >= 0) {
						Rectangle s = fromButtons[z].getBounds();
						Rectangle d = toButtons[i].getBounds();
						g.drawLine(s.x + s.width / 2, s.y + s.height, d.x + d.width / 2, d.y);
					}
				}
			}
		};
		int bw = 34;
		int bh = 26;
		int gap = 4;
		int x0 = 44;
		JLabel from = new JLabel("From:");
		from.setBounds(4, 8, 40, bh);
		diagram.add(from);
		JLabel to = new JLabel("To:");
		to.setBounds(4, 96, 40, bh);
		diagram.add(to);
		for (int i = 0; i < 8; i++) {
			int x = x0 + i * (bw + gap) + (i >= 4 ? 16 : 0);
			fromButtons[i] = new JButton(NAMES[i]);
			fromButtons[i].setMargin(new java.awt.Insets(0, 0, 0, 0));
			fromButtons[i].setBounds(x, 8, bw, bh);
			final int n = i;
			fromButtons[i].addActionListener(e -> fromTrack = n);
			diagram.add(fromButtons[i]);
			toButtons[i] = new JButton(NAMES[i]);
			toButtons[i].setMargin(new java.awt.Insets(0, 0, 0, 0));
			toButtons[i].setBounds(x, 96, bw, bh);
			toButtons[i].addActionListener(e -> {
				tracksOrder[n] = fromTrack; // m_tracksorder[m_totrack] = m_fromtrack (-1 after "Nothing" clears the column)
				diagram.repaint();
			});
			diagram.add(toButtons[i]);
			if (i >= 4 && tracks4_8 <= 4) {
				fromButtons[i].setEnabled(false);
				toButtons[i].setEnabled(false);
			}
		}
		JButton nothing = new JButton("Nothing");
		nothing.setMargin(new java.awt.Insets(0, 4, 0, 4));
		nothing.setBounds(x0 + 8 * (bw + gap) + 32, 8, 80, bh);
		nothing.addActionListener(e -> fromTrack = -1);
		diagram.add(nothing);
		diagram.setPreferredSize(new Dimension(x0 + 8 * (bw + gap) + 32 + 80 + 8, 96 + bh + 8));

		JPanel presets = new JPanel(new java.awt.GridLayout(3, 2, 4, 4));
		presets.add(preset("Mono-->stereo", new int[] { 0, 3, 4, 7, 1, 2, 5, 6 }, tracks4_8 > 4));
		presets.add(preset("Copy left-->right", new int[] { 0, 1, 2, 3, 0, 1, 2, 3 }, tracks4_8 > 4));
		presets.add(preset("Mono<--stereo", new int[] { 0, 4, 5, 1, 2, 6, 7, 3 }, tracks4_8 > 4));
		presets.add(preset("Copy left<--right", new int[] { 4, 5, 6, 7, 4, 5, 6, 7 }, tracks4_8 > 4));
		presets.add(preset("Default", new int[] { 0, 1, 2, 3, 4, 5, 6, 7 }, true));
		presets.add(preset("Clear all", new int[] { -1, -1, -1, -1, -1, -1, -1, -1 }, true));

		JPanel range = new JPanel(new java.awt.GridBagLayout());
		DialogSupport.add(range, new JLabel("From songline: $", SwingConstants.RIGHT), 0, 0, 1, true);
		DialogSupport.add(range, fromField, 1, 0, 1, false);
		DialogSupport.add(range, new JLabel("To songline: $", SwingConstants.RIGHT), 0, 1, 1, true);
		DialogSupport.add(range, toField, 1, 1, 1, false);

		JPanel bottom = new JPanel(new BorderLayout(16, 0));
		bottom.add(presets, BorderLayout.WEST);
		bottom.add(range, BorderLayout.EAST);
		JPanel content = new JPanel(new BorderLayout());
		content.add(diagram, BorderLayout.CENTER);
		content.add(bottom, BorderLayout.SOUTH);
		getContentPane().add(content, BorderLayout.CENTER);

		setOrder(new int[] { 0, 1, 2, 3, 4, 5, 6, 7 }); // OnDefault()
	}

	private JButton preset(String text, int[] order, boolean enabled) {
		JButton button = new JButton(text);
		button.setMargin(new java.awt.Insets(1, 4, 1, 4));
		button.setEnabled(enabled);
		button.addActionListener(e -> setOrder(order));
		return button;
	}

	void setOrder(int[] order) {
		System.arraycopy(order, 0, tracksOrder, 0, 8);
		diagram.repaint();
	}

	/** {@code DoModal()}: the choice, or {@code null} if cancelled. */
	SongDialogs.TracksOrderChoice showDialog() {
		showModal(fromField);
		if (!okPressed) {
			return null;
		}
		return new SongDialogs.TracksOrderChoice(fromField.getText(), toField.getText(), tracksOrder.clone());
	}
}
