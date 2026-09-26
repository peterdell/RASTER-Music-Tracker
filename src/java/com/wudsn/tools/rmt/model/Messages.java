package com.wudsn.tools.rmt.model;

/**
 * Ported from Messages.h/.cpp - the {@code Send<Type>Message()} functions.
 * C++ shows a Win32 {@code MessageBox()} when a window exists
 * ({@code g_statusBar != nullptr}) and logs otherwise (the path every test
 * takes). The same split exists here through {@link Handler}: the default
 * handler is the log fallback, and the UI installs one that shows real
 * boxes ({@link #setHandler}). {@code MessageButtons} is kept as a
 * parameter on {@link #sendQuestionMessage} for the handler's box.
 *
 * <p><b>{@code SendInfoMessage} omitted</b>: unlike the other four
 * functions, it unconditionally calls {@code SetStatusBarText()} (no
 * log-fallback branch at all) and isn't exercised by any test.
 *
 * <p>C++'s {@code g_testQuestionAnswer} (a module-level global test hook)
 * becomes the log handler's {@link #setTestQuestionAnswer} answer,
 * defaulting to {@link MessageAnswer#CANCEL} for the same
 * "safe/non-destructive choice" reason as the original.
 */
public final class Messages {

	/** Where the messages go: the log (default) or the UI's message boxes. */
	public interface Handler {
		void showError(String title, String message);

		void showWarning(String title, String message);

		void showInformation(String title, String message);

		MessageAnswer askQuestion(String title, String message, MessageButtons buttons);
	}

	private MessageAnswer testQuestionAnswer = MessageAnswer.CANCEL;

	private Handler handler = new Handler() {
		@Override
		public void showError(String title, String message) {
			log("ERROR: ", title, message);
		}

		@Override
		public void showWarning(String title, String message) {
			log("WARNING: ", title, message);
		}

		@Override
		public void showInformation(String title, String message) {
			log("INFO: ", title, message);
		}

		@Override
		public MessageAnswer askQuestion(String title, String message, MessageButtons buttons) {
			log("QUESTION: ", title, message);
			return testQuestionAnswer;
		}
	};

	public void setTestQuestionAnswer(MessageAnswer answer) {
		testQuestionAnswer = answer;
	}

	/** Installs the UI's message boxes in place of the log fallback ({@code null} restores the log). */
	public void setHandler(Handler handler) {
		this.handler = handler != null ? handler : createLogHandler();
	}

	private Handler createLogHandler() {
		Messages fresh = new Messages();
		fresh.testQuestionAnswer = testQuestionAnswer;
		return fresh.handler;
	}

	public void sendErrorMessage(String message) {
		sendErrorMessage(null, message);
	}

	public void sendErrorMessage(String title, String message) {
		handler.showError(title, message);
	}

	public void sendWarningMessage(String message) {
		sendWarningMessage(null, message);
	}

	public void sendWarningMessage(String title, String message) {
		handler.showWarning(title, message);
	}

	public void sendInformationMessage(String message) {
		sendInformationMessage(null, message);
	}

	public void sendInformationMessage(String title, String message) {
		handler.showInformation(title, message);
	}

	public MessageAnswer sendQuestionMessage(String title, String message, MessageButtons buttons) {
		return handler.askQuestion(title, message, buttons);
	}

	private static void log(String prefix, String title, String message) {
		System.err.print(prefix);
		if (title != null) {
			System.err.println(title);
		}
		System.err.println(message);
	}
}
