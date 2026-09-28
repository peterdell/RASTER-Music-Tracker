package org.atari.raster.rmt.ui;

import java.awt.Component;
import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntSupplier;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

import org.atari.raster.rmt.model.MessageAnswer;
import org.atari.raster.rmt.model.MessageButtons;
import org.atari.raster.rmt.model.Messages;

/**
 * The UI side of {@link Messages}: the {@code MessageBox()} branch of C++'s
 * {@code Send<Type>Message()} functions, as {@link JOptionPane} boxes over
 * the main window. Also used directly by {@link RmtCommands} for the
 * {@code MessageBox(...)} calls the C++ handlers make themselves.
 *
 * <p>Every box is shown with the session lock released
 * ({@link RmtSession#unlocked}) so the {@link AudioEngine} keeps running
 * while it is open, and from the EDT even when the model raised the message
 * on the engine's thread (C++'s timer thread shows its {@code MessageBox}
 * directly).
 */
public final class SwingMessages implements Messages.Handler {

	private final Component parent;
	private final RmtSession session;

	public SwingMessages(Component parent, RmtSession session) {
		this.parent = parent;
		this.session = session;
	}

	private int show(IntSupplier box) {
		return session.unlocked(() -> {
			if (SwingUtilities.isEventDispatchThread()) {
				return box.getAsInt();
			}
			AtomicInteger result = new AtomicInteger(JOptionPane.CLOSED_OPTION);
			try {
				SwingUtilities.invokeAndWait(() -> result.set(box.getAsInt()));
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			} catch (InvocationTargetException e) {
				throw new IllegalStateException(e.getCause());
			}
			return result.get();
		});
	}

	@Override
	public void showError(String title, String message) {
		show(() -> {
			JOptionPane.showMessageDialog(parent, message, title != null ? title : "Error", JOptionPane.ERROR_MESSAGE);
			return 0;
		});
	}

	@Override
	public void showWarning(String title, String message) {
		show(() -> {
			JOptionPane.showMessageDialog(parent, message, title != null ? title : "Warning", JOptionPane.WARNING_MESSAGE);
			return 0;
		});
	}

	@Override
	public void showInformation(String title, String message) {
		show(() -> {
			JOptionPane.showMessageDialog(parent, message, title != null ? title : "Information", JOptionPane.INFORMATION_MESSAGE);
			return 0;
		});
	}

	@Override
	public MessageAnswer askQuestion(String title, String message, MessageButtons buttons) {
		int option = switch (buttons) {
		case YES_NO -> JOptionPane.YES_NO_OPTION;
		case YES_NO_CANCEL -> JOptionPane.YES_NO_CANCEL_OPTION;
		case OK_CANCEL -> JOptionPane.OK_CANCEL_OPTION;
		};
		int r = show(() -> JOptionPane.showConfirmDialog(parent, message, title != null ? title : "Question", option, JOptionPane.QUESTION_MESSAGE));
		return switch (r) {
		case JOptionPane.YES_OPTION -> buttons == MessageButtons.OK_CANCEL ? MessageAnswer.OK : MessageAnswer.YES;
		case JOptionPane.NO_OPTION -> MessageAnswer.NO;
		default -> MessageAnswer.CANCEL;
		};
	}
}
