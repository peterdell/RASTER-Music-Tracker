#pragma once

#include "StdAfx.h"

// Display info message in the status bar or in the log.
extern void SendInfoMessage(const char* message);

// Display error message in a message box or in the log. Optionally with
// title. Also covers what used to be shown with MB_ICONSTOP - the same
// Win32 icon as MB_ICONERROR (both alias MB_ICONHAND), so there is no
// visible difference between the two.
extern void SendErrorMessage(const char* message);
extern void SendErrorMessage(const char* title, const char* message);

// Display warning message in a message box or in the log. Optionally with
// title. Covers what used to be shown with either MB_ICONEXCLAMATION or
// MB_ICONWARNING - the same Win32 icon value, so there is no visible
// difference between the two.
extern void SendWarningMessage(const char* message);
extern void SendWarningMessage(const char* title, const char* message);

// Display informational message in a message box or in the log. Optionally
// with title. Distinct from SendInfoMessage() above - this shows a real,
// modal notice with content to read (matching today's MB_ICONINFORMATION
// call sites, e.g. "Instrument info"/"Track Info"), not a status-bar hint.
extern void SendInformationMessage(const char* message);
extern void SendInformationMessage(const char* title, const char* message);

// Confirmation prompt - shows a real message box asking the user to pick
// among the given buttons. When no real status bar/UI is present (i.e. in
// every test), logs instead and returns a test-injected answer - see
// SetTestQuestionAnswer() below - rather than a fixed default, so code
// paths gated on the user's choice (e.g. "are you sure?" prompts) can be
// characterized on every branch.
enum class MessageButtons { YesNo,
							YesNoCancel,
							OkCancel };
enum class MessageAnswer { Yes,
						   No,
						   Ok,
						   Cancel };
extern MessageAnswer SendQuestionMessage(const char* title, const char* message, MessageButtons buttons);

// Test-only hook: sets the answer SendQuestionMessage() returns whenever no
// real status bar/UI is present. Defaults to MessageAnswer::Cancel (the
// safe/non-destructive choice) so a test that forgets to set this doesn't
// accidentally take a destructive branch.
extern void SetTestQuestionAnswer(MessageAnswer answer);
