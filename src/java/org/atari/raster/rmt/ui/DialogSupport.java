package org.atari.raster.rmt.ui;

import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.text.MessageFormat;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;

/**
 * Small shared pieces of the B6 dialogs: the MFC-style field validation
 * ({@code DDX_Text}/{@code DDV_MinMax*}: a message box naming the range and
 * the focus back on the offending field) and the group-box grid layout the
 * C++ dialog resources use.
 */
final class DialogSupport {

	private DialogSupport() {
	}

	/** A titled group box ({@code GROUPBOX}) laid out as a grid. */
	static JPanel createGroup(String title) {
		JPanel panel = new JPanel(new GridBagLayout());
		panel.setBorder(BorderFactory.createTitledBorder(title));
		return panel;
	}

	/** Adds {@code c} at grid cell ({@code x}, {@code y}) spanning {@code width} columns, left-aligned; a text field or combo box stretches horizontally. */
	static void add(JPanel grid, Component c, int x, int y, int width, boolean stretch) {
		GridBagConstraints g = new GridBagConstraints();
		g.gridx = x;
		g.gridy = y;
		g.gridwidth = width;
		g.anchor = GridBagConstraints.WEST;
		g.insets = new Insets(2, 4, 2, 4);
		g.fill = stretch ? GridBagConstraints.HORIZONTAL : GridBagConstraints.NONE;
		g.weightx = stretch ? 1 : 0;
		grid.add(c, g);
	}

	/** An integer field's value, or {@code null} for anything that is not an integer ({@code DDX_Text} into an {@code int}). */
	static Integer parseInt(JTextField field) {
		try {
			return Integer.valueOf(field.getText().trim());
		} catch (NumberFormatException ex) {
			return null;
		}
	}

	/** A real field's value, or {@code null} ({@code DDX_Text} into a {@code double}). */
	static Double parseDouble(JTextField field) {
		try {
			return Double.valueOf(field.getText().trim());
		} catch (NumberFormatException ex) {
			return null;
		}
	}

	/**
	 * {@code DDX_Text} + {@code DDV_MinMaxInt}: returns {@code false} after
	 * showing MFC's prompt and focusing the field if the text is not an
	 * integer within {@code min..max}.
	 */
	static boolean validateInt(Component dialog, JTextField field, int min, int max) {
		Integer value = parseInt(field);
		if (value == null) {
			fail(dialog, field, Texts.Validation_Integer);
			return false;
		}
		if (value < min || value > max) {
			fail(dialog, field, MessageFormat.format(Texts.Validation_IntegerRange, Integer.toString(min), Integer.toString(max)));
			return false;
		}
		return true;
	}

	/** {@code DDX_Text} + {@code DDV_MinMaxDouble}. */
	static boolean validateDouble(Component dialog, JTextField field, double min, double max) {
		Double value = parseDouble(field);
		if (value == null || value.isNaN()) {
			fail(dialog, field, Texts.Validation_Real);
			return false;
		}
		if (value < min || value > max) {
			fail(dialog, field, MessageFormat.format(Texts.Validation_RealRange, RmtConfig.formatDouble(min), RmtConfig.formatDouble(max)));
			return false;
		}
		return true;
	}

	private static void fail(Component dialog, JComponent field, String message) {
		JOptionPane.showMessageDialog(dialog, message, null, JOptionPane.WARNING_MESSAGE);
		field.requestFocusInWindow();
		if (field instanceof JTextField textField) {
			textField.selectAll();
		}
	}
}
