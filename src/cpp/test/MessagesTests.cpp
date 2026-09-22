#include "gtest/gtest.h"

#include "Messages.h"

// Messages.cpp's g_statusBar defaults to nullptr and no test ever sets it
// (see plans/MESSAGEBOX_REFACTOR_PLAN.md), so every Send<Type>Message()
// below always takes the log-fallback path here, never the real
// MessageBox() one - this is what makes calling them from a test safe.

TEST(MessagesTest, SendErrorMessageDoesNotBlockOrCrash) {
    SendErrorMessage("Something went wrong");
    SendErrorMessage("Title", "Something went wrong");
}

TEST(MessagesTest, SendWarningMessageDoesNotBlockOrCrash) {
    SendWarningMessage("Careful now");
    SendWarningMessage("Title", "Careful now");
}

TEST(MessagesTest, SendInformationMessageDoesNotBlockOrCrash) {
    SendInformationMessage("For your information");
    SendInformationMessage("Title", "For your information");
}

// --- SendQuestionMessage / SetTestQuestionAnswer ---
// The real MessageBox() path can't be exercised here (see above), so what's
// actually being characterized is the test-injectable-answer mechanism
// itself: every MessageAnswer value round-trips through SetTestQuestionAnswer()
// regardless of which MessageButtons set is requested (the buttons only
// affect the real, unreachable-in-tests MessageBox() path).

TEST(MessagesTest, SendQuestionMessageReturnsTheInjectedAnswer) {
    SetTestQuestionAnswer(MessageAnswer::Yes);
    EXPECT_EQ(SendQuestionMessage("Title", "Continue?", MessageButtons::YesNo), MessageAnswer::Yes);

    SetTestQuestionAnswer(MessageAnswer::No);
    EXPECT_EQ(SendQuestionMessage("Title", "Continue?", MessageButtons::YesNo), MessageAnswer::No);

    SetTestQuestionAnswer(MessageAnswer::Ok);
    EXPECT_EQ(SendQuestionMessage("Title", "Continue?", MessageButtons::OkCancel), MessageAnswer::Ok);

    SetTestQuestionAnswer(MessageAnswer::Cancel);
    EXPECT_EQ(SendQuestionMessage("Title", "Continue?", MessageButtons::YesNoCancel), MessageAnswer::Cancel);
}
