# Plan: replace `MessageBox(g_hwnd, ...)` with `Send<Type>Message(...)` helpers

## Context

Across this whole characterization-testing effort, a recurring pattern has
been methods that call `MessageBox(g_hwnd, ...)` directly for guard-only
errors/warnings (corrupted file, out-of-range input, etc.). These have
always been treated as "safe to test around" by simply not feeding the
invalid input that would trigger them - never as something to change,
since this effort has consistently avoided production behavior changes.

This plan is different in kind: it's a **requested refactor** (replace
direct `MessageBox`/`g_hwnd` calls with a small set of global
`Send<Type>Message()` functions, one per message "type"), not a
characterization batch. Read every `MessageBox(g_hwnd, ...)` call site
(71 across 24 files) before writing this plan.

**There is already a precedent to build on.** `Messages.h`/`Messages.cpp`
already exist and already contain exactly this pattern for one type:

```cpp
void SendErrorMessage(const char* message);                    // delegates to the 2-arg overload with title=nullptr
void SendErrorMessage(const char* title, const char* message);  // real body below

void SendErrorMessage(const char* title, const char* message) {
    if (g_statusBar == nullptr) {
        OutputDebugString("ERROR: "); if (title) { OutputDebugString(title); OutputDebugString("\n"); }
        OutputDebugString(message); OutputDebugString("\n");
    } else {
        MessageBox(g_hwnd, message, title, MB_ICONERROR);
    }
}
```

`g_statusBar` defaults to `nullptr` in the test project (no real status
bar is ever constructed there), so this function already *never* shows a
real, blocking `MessageBox` in tests - it silently logs instead. This
refactor generalizes that exact pattern to the other message types.
(`SendInfoMessage(const char*)` also already exists, but it's a different
thing - it writes to the status bar via `SetStatusBarText()`, not a
`MessageBox` - see the naming note below.)

## Survey: what's out there

71 call sites, all of the shape `MessageBox(g_hwnd, <message>, <title>,
<flags>)` (message before title - note this is the **opposite** argument
order from `SendErrorMessage(title, message)`, a real risk of transposing
the two during migration). Breakdown by flags:

| Flags | Count | Notes |
|---|---|---|
| `MB_ICONERROR` | 30 | |
| `MB_ICONSTOP` | 11 | **`MB_ICONSTOP` and `MB_ICONERROR` are the same Win32 value** (both alias `MB_ICONHAND`, `0x10`) - the original author just used the two names inconsistently. Visually identical icon. |
| `MB_ICONEXCLAMATION` | 11 | **`MB_ICONEXCLAMATION` and `MB_ICONWARNING` are the same Win32 value** (`0x30`) - same situation. |
| `MB_ICONINFORMATION` | 8 | Real modal notices with content to read (e.g. "Instrument info", "Track Info", TMC/MOD import summaries) - not the same thing as the existing status-bar-only `SendInfoMessage`. |
| `MB_ICONWARNING` | 7 | (see `MB_ICONEXCLAMATION` above - same underlying icon) |
| `MB_ICONQUESTION` | 4 | Confirmation prompts - **the return value is used** (`MB_YESNOCANCEL`/`MB_YESNO`/`MB_OKCANCEL` combined with it), unlike every other row here. Two more (`Song.cpp`'s duplicate/switch-stereo confirms) use `MB_ICONWARNING`/`MB_ICONEXCLAMATION` combined with `MB_YESNOCANCEL`/`MB_OKCANCEL` - 6 confirmation prompts total. |

So there are really only **4 distinct icons** in play (error/stop, warning/
exclamation, information, question), and the question one is structurally
different (needs a return value) from the other three (fire-and-forget).

**6 of the 71 are confirmation prompts** (return value drives control
flow): `GUI_Song.cpp:76`, `IO_Song.cpp:51`, `IO_Song.cpp:926`,
`Song.cpp:350` (`SongMaketracksduplicate`), `Song.cpp:482`, `Song.cpp:509`
(`Songswitch4_8`). The last two are exactly the methods
`plans/SONG_IO_SONG_REMAINING_PLAN.md` deferred specifically *because* of
this hazard ("Decisions (resolved) #3: defer both entirely, not worth
extracting"). See "Testability payoff" below - this refactor could
actually unblock those two, if the design supports it.

## Design

### The three functions (resolved)

**Decided**: merge `MB_ICONSTOP` call sites into `SendErrorMessage`, and
merge `MB_ICONWARNING` call sites into the same function as
`MB_ICONEXCLAMATION` (`SendWarningMessage`) - they're the same Win32 icon
value, so this is a pure consolidation with no visible behavior change.
Three public fire-and-forget functions, following the existing
`SendErrorMessage` shape (title before message, one 1-arg + one 2-arg
overload each):

```cpp
// Fire-and-forget notices - no return value.
void SendErrorMessage(const char* message);                       // already exists - now also covers today's MB_ICONSTOP sites
void SendErrorMessage(const char* title, const char* message);    // already exists

void SendWarningMessage(const char* message);                     // covers today's MB_ICONEXCLAMATION and MB_ICONWARNING sites
void SendWarningMessage(const char* title, const char* message);

void SendInformationMessage(const char* message);
void SendInformationMessage(const char* title, const char* message);

// Confirmation prompt - needs a return value and a button-set choice.
enum class MessageButtons { YesNo, YesNoCancel, OkCancel };
enum class MessageAnswer { Yes, No, Ok, Cancel };
MessageAnswer SendQuestionMessage(const char* title, const char* message, MessageButtons buttons);
```

To avoid duplicating the `if (g_statusBar == nullptr) { log } else {
MessageBox }` logic three times, all three fire-and-forget functions share
one small internal helper parameterized by the Win32 icon flag and a short
log-prefix string (`"ERROR: "`, `"WARNING: "`, `"INFO: "`), with each
public function a thin wrapper picking its icon/prefix - same idea as the
existing 1-arg-delegates-to-2-arg pattern.

**Naming collision, resolved by keeping distinct names**: `SendInfoMessage`
already exists but means something else (status-bar text, not a
`MessageBox`). The new one is `SendInformationMessage` - close enough to
mis-type as the existing one, so call sites should be double-checked
during migration, but renaming the existing status-bar function was
decided against (touches unrelated call sites for no functional gain).

### Testability payoff for `SendQuestionMessage` (resolved: test-injectable answer)

Every other `Send*Message` function makes its call site *automatically*
safe to trigger in tests (since `g_statusBar` is `nullptr` there) - a real
win, since today's guard-only `MessageBox` calls have to be carefully
avoided with valid test input; after this refactor they can be triggered
on purpose and their log output could even be asserted on if desired.

`SendQuestionMessage` can't get this for free the same way, because a
confirmation prompt's *answer* drives control flow - in tests there's no
user to click a button. **Decided**: a test-injectable answer - a settable
global/thread-local (e.g. `void SetTestQuestionAnswer(MessageAnswer
answer);`, consulted only when `g_statusBar == nullptr`) that a test sets
before calling into code that shows a confirm prompt, then resets
afterward. This is what actually lets `SongMaketracksduplicate`/
`Songswitch4_8` (and `IO_Song.cpp`'s `FileReload`/warning confirms, if that
family is ever revisited) become testable for the first time, on both
branches - not just automatically safe to avoid.

Implementation sketch: a file-local `MessageAnswer g_testQuestionAnswer =
MessageAnswer::Cancel;` (a safe/non-destructive default so a test that
forgets to set it doesn't accidentally take a destructive branch), read
only inside the `g_statusBar == nullptr` path.

### Where the new functions live (resolved: extend `Messages.h`/`.cpp`)

Extending the existing `Messages.h`/`Messages.cpp`, since they already
hold `SendErrorMessage` and are the obvious conceptual home - no new file
pair.

**Prerequisite worth doing either way**: `Messages.cpp` currently
`#include`s `Global.h` (the wide-dependency-graph file this whole effort
avoids linking), which is why it isn't linked into `RmtTests.vcxproj`
today - instead, `test/AtariBinariesStub.cpp` and `test/Song_DumpSongStub.cpp`
each carry their own **verbatim copy** of `SendErrorMessage`/
`g_statusBar`/`SendInfoMessage` (a pattern this effort has used
repeatedly, e.g. for `SendErrorMessage` in the `ExportSAP_B_LZSS` batch).
Checked: the only thing `Messages.cpp` actually needs from `Global.h` is
`extern HWND g_hwnd;` (already a real, linked global via
`test/SongEditingStub.cpp`) - so `Messages.cpp` can very likely drop
`Global.h` entirely, the same way `IO_Instruments.cpp` did earlier in
this effort, and link directly into `RmtTests.vcxproj`. Doing this *before*
adding the new functions means:
- The new functions are immediately available to tests with no
  duplication anywhere.
- The two existing verbatim copies (`AtariBinariesStub.cpp`'s
  `SendErrorMessage`/`g_statusBar`, `Song_DumpSongStub.cpp`'s
  `SendInfoMessage`) can be deleted, removing a real (if currently
  harmless) duplicate-source-of-truth risk.

## Migration batches

Once the three new functions exist and are linked/tested, mechanically
replace call sites. Grouped by payoff, matching this effort's established
priority (biggest testability win first) - each batch gets its own
build+full-test-suite verification and commit decision, same cadence as
every other batch in this project:

1. **Prerequisite - DONE**: `Messages.cpp` `Global.h` removal + real link
   into `RmtTests.vcxproj`; delete the two verbatim stub copies; add
   `SendWarningMessage`/`SendInformationMessage`/`SendQuestionMessage`
   (plus the test-injectable `SendQuestionMessage` answer hook) with their
   own new characterization tests (asserting the log-fallback path, since
   the real `MessageBox` path is - by design - never reachable in tests).
   `Messages.cpp` needed only one line changed (`#include "Global.h"` →
   a direct `extern HWND g_hwnd;`) to become linkable - confirmed by a
   clean build of both `Rmt.vcxproj` and `RmtTests.vcxproj` with the same
   `Messages.cpp` now shared between them. The three fire-and-forget
   functions share one internal `SendMessageBox()` helper (icon + log-prefix
   parameters) to avoid tripling the `if (g_statusBar == nullptr) {...}
   else {...}` logic. New tests in `test/MessagesTests.cpp` (4 tests): smoke
   tests for `SendErrorMessage`/`SendWarningMessage`/`SendInformationMessage`
   (little else is observable - they log to `OutputDebugString`, not
   anywhere a test can inspect), and a real round-trip test proving
   `SendQuestionMessage` returns whatever `SetTestQuestionAnswer()` set,
   across all four `MessageAnswer` values and all three `MessageButtons`
   sets. Full solution rebuild (Release|x64) confirmed 0 errors; 243 tests
   pass (up from 239, +4, 0 regressions).
2. **Already-linked/near-linked files with fire-and-forget notices only -
   DONE.** Re-counted against the actual source (the original survey's
   per-file numbers were slightly off): `SongEditing.cpp` (14 sites, not
   10), `IO_ImporterCore.cpp` (6), `IO_Importer.cpp` (6, including the
   `errorCode`-driven `switch` from `ImportMOD`'s three header guards),
   `Undo.cpp` (5, all `"...BAD!"` internal-error assertions on invalid enum
   values - already unreachable with valid input), `TuningTables.cpp` (1) -
   32 call sites total. `SongEditing.cpp`/`IO_ImporterCore.cpp` each had
   their own `extern HWND g_hwnd;` removed (no longer referenced anywhere
   in either file); `IO_Importer.cpp`/`Undo.cpp`/`TuningTables.cpp` keep
   their existing `#include "Global.h"` since they still use other globals
   from it (`g_tracks4_8`, `g_Song`/`g_activepart`/`g_changes`,
   `g_notesperoctave`/`g_tuning`/`g_tuningRatios` respectively) -
   fully removing `Global.h` from those three was out of scope for this
   batch (a bigger, separate decision, not requested). No new tests - this
   batch has no new hazard categories to characterize, it's a mechanical
   swap. Full solution rebuild (Release|x64) confirmed 0 errors; 243 tests
   pass (unchanged, 0 regressions).
3. **`IO_Song.cpp`'s fire-and-forget notices - DONE** (18 sites). This file
   itself stays deferred (`FileXxx` family, confirmed no dialog-free
   `Apply()` core exists - see `plans/SONG_IO_SONG_REMAINING_PLAN.md`
   Batch 7), so this batch was pure consistency/cleanup, not a new testing
   unlock. `#include "Global.h"` kept (other globals still needed).
4. **The 6 confirmation prompts - DONE** (`GUI_Song.cpp` x1, `IO_Song.cpp`
   x2, `Song.cpp` x3) via `SendQuestionMessage`. Bundled in
   `Song.cpp`'s own 2 stray fire-and-forget sites (lines 365/469 - not
   named in the original survey's per-file list, found while touching the
   file for its confirm prompts anyway). One transcription note:
   `IO_Song.cpp`'s `TestBeforeFileSave()` reused a shared `int r` (declared
   alongside several unrelated loop counters in one combined
   declaration) for its `MessageBox()` result - removed `r` from that
   declaration and introduced a locally-scoped `MessageAnswer answer`
   at the call site instead, since `r` had no other use in the function.
   This is where `SongMaketracksduplicate`/`Songswitch4_8` (`Song.cpp`)
   could finally get real characterization tests using the test-injectable
   answer hook from #1. **Done as a follow-up**: both moved verbatim into
   `SongEditing.cpp` (all their other dependencies - `MarkTF_USED`/
   `MarkTF_NOEMPTY`/`FindNearTrackBySongLineAndColumn`/`TrackCopyFromTo`/
   `SetTracks`/`g_Atari.Init(bool)` - were already safe and linked, so the
   move needed zero code changes beyond relocating the two method bodies),
   with `Song.cpp` left with a one-line "implemented in SongEditing.cpp"
   comment for each, matching every other split in this effort. 7 new
   tests cover both the confirm and cancel branches. See `plans/NOTES.md`
   and `plans/SONG_IO_SONG_REMAINING_PLAN.md` (its "defer both entirely"
   decision no longer applies to these two).
5. **Everything else - DONE** (`C6502.cpp` x2, `Pokey.cpp` x3,
   `PokeyRenderer.cpp` x5, `RmtMidi.cpp` x1 - 11 sites). Real hardware/
   DLL-coupled files already confirmed permanently out of scope
   (`plans/BROADER_SURVEY_PLAN.md`) - pure architectural consistency, no
   testability payoff, but done anyway since the migration is now
   complete everywhere: **zero** `MessageBox(g_hwnd, ...)` call sites
   remain in the codebase outside `Messages.cpp`'s own real
   implementation.

## Risks / things to double-check while migrating

- **Argument order**: Win32 `MessageBox(hwnd, message, title, flags)` vs.
  the established `Send*Message(title, message)` - easy to transpose
  message and title by habit when mechanically converting 71 call sites.
- Several call sites build the message via `CString`/`.Format()` into a
  local variable first - these pass straight through unchanged (`CString`
  converts implicitly to `const char*`/`LPCTSTR`, already proven by
  today's `SendErrorMessage` call sites).
- After migration, `extern HWND g_hwnd;` becomes dead in every file that
  no longer calls `MessageBox` directly - remove it per file as part of
  that file's batch (matches this effort's "remove dependencies as they
  become unused" habit, e.g. the `SendErrorMessage`/`Messages.h`
  precedent already does this).
- `SendQuestionMessage`'s log-fallback text (for the `g_statusBar ==
  nullptr` path) needs *some* sensible format even though no
  characterization test will assert much beyond "it took the fixed/
  injected answer without hanging" - keep it simple (e.g. one
  `OutputDebugString` line with the answer taken).
