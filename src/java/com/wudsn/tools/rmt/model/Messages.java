package com.wudsn.tools.rmt.model;

/**
 * Ported from Messages.h/.cpp - the subset {@code MessagesTests.cpp}
 * exercises. C++'s {@code g_statusBar} always stays {@code nullptr} in
 * every test (no real status bar/UI is ever constructed there), so every
 * {@code Send<Type>Message()} always takes the log-fallback path, never the
 * real Win32 {@code MessageBox()} one - this port only implements that
 * log-fallback path, since there's no Java UI/window to show a dialog in
 * and no test reaches the other branch. {@code MessageButtons} is kept as a
 * parameter on {@link #sendQuestionMessage} purely for signature parity
 * with the (now unreachable-in-Java) buttons-to-icon mapping it fed in
 * C++'s {@code MessageBox()} branch.
 *
 * <p><b>{@code SendInfoMessage} omitted</b>: unlike the other four
 * functions, it unconditionally calls {@code SetStatusBarText()} (no
 * log-fallback branch at all) and isn't exercised by any test.
 *
 * <p>C++'s {@code g_testQuestionAnswer} (a module-level global test hook)
 * becomes an instance field here, defaulting to {@link MessageAnswer#CANCEL}
 * for the same "safe/non-destructive choice" reason as the original.
 */
public final class Messages {

	private MessageAnswer testQuestionAnswer = MessageAnswer.CANCEL;

	public void setTestQuestionAnswer(MessageAnswer answer) {
		testQuestionAnswer = answer;
	}

	public void sendErrorMessage(String message) {
		sendErrorMessage(null, message);
	}

	public void sendErrorMessage(String title, String message) {
		log("ERROR: ", title, message);
	}

	public void sendWarningMessage(String message) {
		sendWarningMessage(null, message);
	}

	public void sendWarningMessage(String title, String message) {
		log("WARNING: ", title, message);
	}

	public void sendInformationMessage(String message) {
		sendInformationMessage(null, message);
	}

	public void sendInformationMessage(String title, String message) {
		log("INFO: ", title, message);
	}

	public MessageAnswer sendQuestionMessage(String title, String message, MessageButtons buttons) {
		log("QUESTION: ", title, message);
		return testQuestionAnswer;
	}

	private static void log(String prefix, String title, String message) {
		System.err.print(prefix);
		if (title != null) {
			System.err.println(title);
		}
		System.err.println(message);
	}
}
