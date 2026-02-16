#pragma once

#include "StdAfx.h"

// Display info messae in the status bar or in the log.
extern void SendInfoMessage(const char* message);

// Display error message in a message box or in the the log. Optionally with title.
extern void SendErrorMessage(const char* message);
extern void SendErrorMessage(const char* title, const char* message);