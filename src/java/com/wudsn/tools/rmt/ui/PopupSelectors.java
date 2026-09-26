package com.wudsn.tools.rmt.ui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.GridLayout;
import java.awt.Point;
import java.awt.Window;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;

import com.wudsn.tools.rmt.model.Instruments;

/**
 * The three small modal popups the info area opens at the click position -
 * ported from {@code COctaveSelectDlg}/{@code CVolumeSelectDlg}/
 * {@code CInstrumentSelectDlg} (EffectsDlg.h/effectsdlg.cpp, dialog
 * templates {@code IDD_OCTAVESELECT}/{@code IDD_VOLUMESELECT}/
 * {@code IDD_INSTRUMENTSELECT} in Rmt.rc). Each is a tool-window-style
 * modal {@link JDialog} with the same content: five octave buttons ("5 - 6"
 * on top, the current one focused), a 16-entry volume list ("F |||...|"
 * down to "0") with a "respect vol." check box and an OK button, or the 64
 * instruments as "XX: name" with the current one selected and scrolled
 * into view. Selecting a list entry or pressing a button closes the popup
 * with that value, as C++'s {@code OnSelchangeList1}/{@code OnOctave} do.
 *
 * <p>C++ positions each at {@code view top-left + (x - 64 - 9, y - 7)} (or
 * {@code - 64 - 82} for the instrument list) with the click's logical
 * coordinates used as if they were screen pixels - an RMT quirk that is
 * only right at 100% scaling. The caller passes the click position already
 * converted to screen pixels, and the same offsets are applied.
 */
final class PopupSelectors {

	private PopupSelectors() {
	}

	private static JDialog createDialog(Component owner, String title, Point screenPos) {
		Window window = owner == null ? null : SwingUtilities.getWindowAncestor(owner);
		JDialog dialog = new JDialog(window, title, Dialog.ModalityType.APPLICATION_MODAL);
		dialog.setResizable(false);
		dialog.setLocation(Math.max(0, screenPos.x), Math.max(0, screenPos.y));
		return dialog;
	}

	/** {@code COctaveSelectDlg}: returns the chosen octave 0-4, or -1. */
	static int selectOctave(Component owner, Point screenPos, int octave) {
		JDialog dialog = createDialog(owner, "Octave", new Point(screenPos.x - 64 - 9, screenPos.y - 7));
		int[] result = { -1 };
		JPanel panel = new JPanel(new GridLayout(5, 1));
		JButton focus = null;
		for (int i = 4; i >= 0; i--) {
			final int value = i;
			JButton button = new JButton((i + 1) + " - " + (i + 2));
			button.addActionListener(e -> {
				result[0] = value;
				dialog.dispose();
			});
			panel.add(button);
			if (i == octave) {
				focus = button;
			}
		}
		dialog.setContentPane(panel);
		dialog.pack();
		if (focus != null) {
			focus.requestFocusInWindow();
		}
		dialog.setVisible(true);
		return result[0];
	}

	/** {@code CVolumeSelectDlg}: returns the chosen volume plus the "respect vol." state, or {@code null}. */
	static MouseInput.VolumeSelection selectVolume(Component owner, Point screenPos, int volume, boolean respectVolume) {
		JDialog dialog = createDialog(owner, "Volume", new Point(screenPos.x - 64 - 9, screenPos.y - 7));
		String[] vs = new String[16];
		for (int i = 0; i < 16; i++) {
			int v = 15 - i;
			vs[i] = String.format("%X  %s", v, "|".repeat(v));
		}
		JList<String> list = new JList<>(vs);
		list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		list.setSelectedIndex(15 - volume);
		JCheckBox respect = new JCheckBox("respect vol.", respectVolume);
		JButton ok = new JButton("OK");
		MouseInput.VolumeSelection[] result = { null };
		Runnable accept = () -> {
			result[0] = new MouseInput.VolumeSelection(15 - list.getSelectedIndex(), respect.isSelected());
			dialog.dispose();
		};
		list.addListSelectionListener(e -> {
			if (!e.getValueIsAdjusting() && list.getSelectedIndex() >= 0) {
				accept.run();
			}
		});
		ok.addActionListener(e -> accept.run());
		JPanel south = new JPanel(new GridLayout(2, 1));
		south.add(respect);
		south.add(ok);
		JPanel panel = new JPanel(new BorderLayout());
		panel.add(new JScrollPane(list), BorderLayout.CENTER);
		panel.add(south, BorderLayout.SOUTH);
		dialog.setContentPane(panel);
		dialog.pack();
		dialog.setVisible(true);
		return result[0];
	}

	/** {@code CInstrumentSelectDlg}: returns the chosen instrument 0-63, or -1. */
	static int selectInstrument(Component owner, Point screenPos, Instruments instruments, int selected) {
		JDialog dialog = createDialog(owner, "Instrument", new Point(screenPos.x - 64 - 82, screenPos.y - 7));
		String[] names = new String[Instruments.INSTRSNUM];
		for (int i = 0; i < Instruments.INSTRSNUM; i++) {
			names[i] = String.format("%02X: %s", i, SongUI.nameToString(instruments.getName(i)));
		}
		JList<String> list = new JList<>(names);
		list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		list.setVisibleRowCount(16);
		list.setSelectedIndex(selected);
		int[] result = { -1 };
		list.addListSelectionListener(e -> {
			if (!e.getValueIsAdjusting() && list.getSelectedIndex() >= 0) {
				result[0] = list.getSelectedIndex();
				dialog.dispose();
			}
		});
		dialog.setContentPane(new JScrollPane(list));
		dialog.pack();
		if (selected > 16) {
			list.ensureIndexIsVisible(Math.min(selected - 16, Instruments.INSTRSNUM - 16) + 15);
		}
		list.ensureIndexIsVisible(selected);
		dialog.setVisible(true);
		return result[0];
	}
}
