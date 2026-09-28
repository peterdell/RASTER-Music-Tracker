#pragma once

// Mirrors Rmt.rc's IDS_RMT_VERSION string resource - kept as a compile-time
// constant (rather than a runtime CString::LoadString(IDS_RMT_VERSION) call)
// so code that uses it doesn't need the app's compiled resources linked in
// (e.g. testable RMW/RMT file format code). Must be kept in sync with
// Rmt.rc's IDS_RMT_VERSION entry by hand.
constexpr const char* RMT_VERSION_STRING = "RASTER Music Tracker 1.35";
