# Style Rules

Style rules to follow across this project (code, tests, commits, and plan docs).

## Always brace the body of `if`/`else`/`for`/`while`/`do`

Every `if`, `else`, `for`, `while`, and `do` must be followed by a `{ }`
block, even when the body is a single statement or empty. Never rely on
C++'s rule that a single statement following one of these needs no braces.

The opening `{` goes on the **same line** as the `if`/`else`/`for`/
`while`/`do` (not on its own line) - chosen to match the brace style this
project will use in Java once it's ported there. This applies everywhere,
not just control statements: function, method, class, struct, enum, and
namespace bodies also put `{` on the same line as their header, matching
the same Java-bound convention rather than mixing two brace styles in one
codebase.

```cpp
// Not allowed:
if (x < 0)
    return false;

// Not allowed either - braced, but not this project's chosen brace style:
if (x < 0)
{
    return false;
}

// Required:
if (x < 0) {
    return false;
}
```

**Why (braces themselves)**: a brace-less body silently absorbs only the *next single
statement* into the `if`/`loop`. Adding a second statement later - a debug
print, a log line, another assignment - without adding braces at the same
time compiles cleanly but silently runs unconditionally, outside the
control statement, changing the code's behavior with no compiler warning.
(This is the exact class of bug behind real-world incidents like Apple's
2014 "goto fail" SSL bug.) Indentation alone does not protect against
this - misleadingly-indented code can look correct while behaving
differently. Requiring braces always means a later edit that adds a
statement inside an existing block is textually forced to happen between
`{` and `}`, so it can never accidentally fall outside the intended scope.

### Enforcement

Enforced via `clang-tidy`'s `readability-braces-around-statements` check
(plus `readability-misleading-indentation`, which catches code that's
*already* braced correctly but is indented in a way that looks like it
belongs to the wrong block):

- **`.clang-tidy`** at the repo root configures both checks (and nothing
  else - this project doesn't otherwise use clang-tidy for general static
  analysis).
- **Visual Studio integration**: `EnableClangTidyCodeAnalysis` +
  `ClangTidyChecks` are set in both `src/cpp/Rmt.vcxproj` and
  `src/cpp/test/RmtTests.vcxproj`. This surfaces violations live in the
  editor and via *Analyze > Run Code Analysis* - it does **not** run
  during a normal Build/Rebuild (confirmed: `EnableClangTidyCodeAnalysis`
  only takes effect when MSBuild's `RunCppAnalysis` property is also set,
  which only happens for an explicit analysis run, not a plain build).
  Requires the "C++ Clang tools for Windows" optional component installed
  via the Visual Studio Installer.
- **`ClangTidyChecks` is kept in sync with `.clang-tidy` by hand**:
  Visual Studio's integration always passes its own `-checks=` argument to
  `clang-tidy`, which overrides whatever `.clang-tidy` says - so the two
  must list the same checks, or the IDE-integrated analysis silently uses
  the wrong check list.
- **One-time bulk fix applied** (see `plans/NOTES.md`) to bring the
  existing codebase into compliance: `clang-tidy --fix` inserts braces but
  in its own minimal/inline style, so it's followed by `git-clang-format`
  (scoped to only the lines `clang-tidy` had just changed, to avoid
  reformatting unrelated code) with `BreakBeforeBraces: Custom` /
  `BraceWrapping: { AfterControlStatement: false, AfterFunction: false,
  AfterClass: false, AfterStruct: false, AfterEnum: false,
  AfterNamespace: false, BeforeElse: false, BeforeWhile: false,
  BeforeCatch: false }` to place every brace - control statements, and
  function/class/struct/enum/namespace bodies alike - on the same line as
  their header, matching each file's existing tab/space convention.
  `IndentCaseLabels: false`, `PointerAlignment: Left` (with
  `DerivePointerAlignment: false`), `SortIncludes: false`, and
  `AlignTrailingComments: false` are all set explicitly - without them, a
  brace-only pass also reorders `#include`s, flips `Type* p` to `Type *p`,
  and column-aligns trailing comments, none of which this rule is about.
  clang-format also won't relocate a brace past an existing end-of-line
  comment on the header line (e.g. `if (cond) //comment` stays with `{` on
  its own line below) - these need a manual one-line fix moving the brace
  before the comment (`if (cond) { //comment`); `TuningTables.cpp` had 2
  such cases.
- **`.h` files are in scope too**, applied the same way as `.cpp` files:
  each header is passed to `clang-tidy`/`clang-format` directly (not
  reached transitively via a `.cpp`'s `#include`s), with `/FIStdAfx.h`
  (plus `/FIMemory.h`/`/FIostream` for the couple of headers that assume
  those without including them directly) force-included so a header can
  be checked standalone despite relying on the project's
  precompiled-header include order. `AllowShortFunctionsOnASingleLine:
  InlineOnly` is also set for headers specifically - unlike `.cpp` files,
  headers have many one-line inline accessor methods
  (`BOOL Undo() { return g_Undo.Undo(); };`) that are already compliant
  with the brace rule as written and must stay on one line; without this,
  clang-format force-expands every one of them across 3 lines, which has
  nothing to do with brace placement. `AccessModifierOffset: -4` is also
  needed for headers (not `.cpp` files, which have no class bodies) so
  `public:`/`private:`/`protected:` stay at column 0 instead of LLVM's
  default half-indent. Auto-generated files (`resource.h`) are excluded
  outright - they have no braces to fix and clang-format's other
  normalizations (mainly comment-column dealignment) would otherwise
  produce a large, pointless diff.
- **Known gap**: 8 pure-UI/MFC files (`MainFrm.cpp`, `OptionsDialog.cpp`,
  `Rmt.cpp`, `RmtView.cpp`, `TuningDialog.cpp`, `effectsdlg.cpp`,
  `exportdlgs.cpp`, `importdlgs.cpp`) couldn't be processed by the
  automated bulk fix - they use an old-style MFC message-map macro
  (`ON_COMMAND(ID, ClassName::Method)` instead of
  `ON_COMMAND(ID, &ClassName::Method)`) that Clang's parser rejects
  outright, unrelated to braces. These still need the rule applied
  manually (via Visual Studio's own live analysis) whenever they're
  touched.
