package com.wudsn.tools.rmt.ui;

import java.awt.Component;

import javax.swing.JOptionPane;

import com.wudsn.tools.rmt.model.MessageAnswer;
import com.wudsn.tools.rmt.model.MessageButtons;
import com.wudsn.tools.rmt.model.Messages;

/**
 * The UI side of {@link Messages}: the {@code MessageBox()} branch of C++'s
 * {@code Send<Type>Message()} functions, as {@link JOptionPane} boxes over
 * the main window. Also used directly by {@link RmtCommands} for the
 * {@code MessageBox(...)} calls the C++ handlers make themselves.
 */
public final class SwingMessages implements Messages.Handler {

	private final Component parent;

	public SwingMessages(Component parent) {
		this.parent = parent;
	}

	@Override
	public void showError(String title, String message) {
		JOptionPane.showMessageDialog(parent, message, title != null ? title : "Error", JOptionPane.ERROR_MESSAGE);
	}

	@Override
	public void showWarning(String title, String message) {
		JOptionPane.showMessageDialog(parent, message, title != null ? title : "Warning", JOptionPane.WARNING_MESSAGE);
	}

	@Override
	public void showInformation(String title, String message) {
		JOptionPane.showMessageDialog(parent, message, title != null ? title : "Information", JOptionPane.INFORMATION_MESSAGE);
	}

	@Override
	public MessageAnswer askQuestion(String title, String message, MessageButtons buttons) {
		int option = switch (buttons) {
		case YES_NO -> JOptionPane.YES_NO_OPTION;
		case YES_NO_CANCEL -> JOptionPane.YES_NO_CANCEL_OPTION;
		case OK_CANCEL -> JOptionPane.OK_CANCEL_OPTION;
		};
		int r = JOptionPane.showConfirmDialog(parent, message, title != null ? title : "Question", option, JOptionPane.QUESTION_MESSAGE);
		return switch (r) {
		case JOptionPane.YES_OPTION -> buttons == MessageButtons.OK_CANCEL ? MessageAnswer.OK : MessageAnswer.YES;
		case JOptionPane.NO_OPTION -> MessageAnswer.NO;
		default -> MessageAnswer.CANCEL;
		};
	}
}
