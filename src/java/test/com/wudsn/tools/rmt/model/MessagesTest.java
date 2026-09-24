package com.wudsn.tools.rmt.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * Mirrors src/cpp/test/MessagesTests.cpp. Every Send*Message() always takes
 * the log-fallback path in this port (see Messages's class javadoc), so
 * these just characterize that calling them doesn't throw, and that the
 * test-injectable-answer mechanism round-trips correctly.
 */
class MessagesTest {

	@Test
	void sendErrorMessageDoesNotThrow() {
		Messages messages = new Messages();
		messages.sendErrorMessage("Something went wrong");
		messages.sendErrorMessage("Title", "Something went wrong");
	}

	@Test
	void sendWarningMessageDoesNotThrow() {
		Messages messages = new Messages();
		messages.sendWarningMessage("Careful now");
		messages.sendWarningMessage("Title", "Careful now");
	}

	@Test
	void sendInformationMessageDoesNotThrow() {
		Messages messages = new Messages();
		messages.sendInformationMessage("For your information");
		messages.sendInformationMessage("Title", "For your information");
	}

	// --- sendQuestionMessage / setTestQuestionAnswer ---
	// The real MessageBox() path can't be exercised here either (see
	// Messages's class javadoc), so what's actually being characterized is
	// the test-injectable-answer mechanism itself: every MessageAnswer value
	// round-trips through setTestQuestionAnswer() regardless of which
	// MessageButtons set is requested (the buttons only affect the real,
	// unimplemented-in-Java MessageBox() path).

	@Test
	void sendQuestionMessageReturnsTheInjectedAnswer() {
		Messages messages = new Messages();

		messages.setTestQuestionAnswer(MessageAnswer.YES);
		assertEquals(MessageAnswer.YES, messages.sendQuestionMessage("Title", "Continue?", MessageButtons.YES_NO));

		messages.setTestQuestionAnswer(MessageAnswer.NO);
		assertEquals(MessageAnswer.NO, messages.sendQuestionMessage("Title", "Continue?", MessageButtons.YES_NO));

		messages.setTestQuestionAnswer(MessageAnswer.OK);
		assertEquals(MessageAnswer.OK, messages.sendQuestionMessage("Title", "Continue?", MessageButtons.OK_CANCEL));

		messages.setTestQuestionAnswer(MessageAnswer.CANCEL);
		assertEquals(MessageAnswer.CANCEL, messages.sendQuestionMessage("Title", "Continue?", MessageButtons.YES_NO_CANCEL));
	}
}
