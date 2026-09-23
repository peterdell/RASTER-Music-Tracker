# File-tiering strategy for characterization testing

This explains the per-class `.cpp` split ("tiers") used throughout the
characterization-testing effort (see `plans/OVERALL_PLAN.md`), and why it
applies only to `.cpp` files, never to `.h` files.

## The goal

Per `plans/OVERALL_PLAN.md`, the end goal is to port RMT from C++ to Java.
Before that, the current C++ code needs GoogleTest characterization tests,
so those tests can later verify the Java port behaves identically. The
blocker: the codebase mixes UI, hardware I/O, and OS timers directly into
the model classes (`CSong`, `CTracks`, `CInstruments`, ...), and code that
pops a real `MessageBox`, starts a `timeSetEvent`, or touches
DirectSound/MIDI hardware can't run inside a test binary.

## The tiers are a per-class implementation split, not a header split

For a class with a large pile of methods originally implemented in one big
`.cpp` file, methods are triaged one at a time and moved into a sibling
`.cpp` file grouped by which hazardous dependency (if any) they touch. All
the sibling files still implement methods of **the same class**, declared
by **one shared header**.

Example - `CSong`, declared once in `Song.h`, implemented across:

| File | Tier |
|---|---|
| `SongCore.cpp` | zero-`Global.h`-dependency methods |
| `SongEditing.cpp` | the larger "safe cluster" - touches globals but never `g_hwnd`/timers/hardware |
| `Song_DumpSong.cpp`, `SongExportV2.cpp` | narrower safe slices |
| `IO_Song.cpp`, `IO_Importer.cpp`, `IO_ImporterCore.cpp` | file I/O heavy |
| `GUI_Song.cpp`, `Midi_Song.cpp` | genuinely hazardous - real dialogs/MFC/MIDI hardware, deliberately deferred |
| `Song.cpp` | the original file, now mostly one-line "implemented in X.cpp" pointer comments |

Same pattern for `CTracks` (`Tracks.cpp`/`TracksEdit.cpp`/`IO_Tracks.cpp` -
all declared by the single `Tracks.h`) and `CInstruments`
(`Instruments.cpp`/`InstrumentsCore.cpp`/`InstrumentsAtaFormat.cpp`/
`IO_Instruments.cpp`/`GUI_Instruments.cpp` - all declared by the single
`Instruments.h`).

Methods get moved into a safe-tier file once triage shows they don't call
`MessageBox`/timers/hardware directly - sometimes after a dual-mode
refactor first (e.g. `TrackInfo`/`InstrInfo` taking an optional
output-struct pointer instead of unconditionally showing a dialog, or the
`Send<Type>Message()` refactor making confirm-prompts test-injectable via
`SetTestQuestionAnswer()`), then characterization tests get added for the
newly-testable method.

## Headers are not tiered, and should not be

The hazard being triaged is *what a method's body does at runtime* (open a
dialog, start a timer, touch hardware) - a header only declares a
signature, which carries no such risk regardless of which tier its
implementation ends up in. Splitting `Song.h` into `SongCore.h`/
`SongEditing.h`/etc. would force every one of the class's `.cpp` files to
include several headers for the same class instead of one, with no
testability benefit.

The other header files that exist per topic - `SongUI.h`, `SongTimer.h`,
`SongContainer.h`, `SongExporter.h` - are **not** tiers of `CSong` at all.
They declare genuinely distinct classes (`CSongUI`, `CSongTimer`,
`CSongContainer`, `CSongExporter`) that happen to share the "Song" name
prefix because they're all part of the same domain concept, not because
they were split out of `CSong` by hazard level.

## Summary

- `.cpp` files: split into tiers per class, by runtime hazard, to grow the
  set of methods that can link into `RmtTests.vcxproj` and be tested.
- `.h` files: one header per class, shared by every tier of that class's
  `.cpp` implementation. Never split by hazard tier.

## How this translates to the Java port (open decision, revisit when the port starts)

The `.cpp`-tier mechanism is a compile/link-time trick specific to C++: it
lets the safe methods link into the test binary without pulling in
`GUI_Song.cpp`'s MFC dialogs or `Midi_Song.cpp`'s hardware calls. Java has
no header/implementation split and no partial classes - one `class` body
is one compilation unit - so there is no direct equivalent of "link only
the safe files." All of `CSong`'s methods, wherever they currently live
across its ten `.cpp` files (~9,950 lines, ~151 methods total), belong to
the same class in Java regardless of origin file.

**What carries over directly**: the *inter-class* split. `CSongUI`,
`CSongTimer`, `CSongContainer`, and `CSongExporter` are already genuinely
separate classes with their own headers, not tiers of `CSong` - these map
one-to-one onto separate Java classes/files in the same package (e.g.
`song/Song.java`, `song/SongUi.java`, `song/SongTimer.java`,
`song/SongExporter.java`). No rethinking needed there.

**What needs a different mechanism for a heavily-tiered class like
`CSong`** - two options to choose between when the port begins:

1. **Mechanical recombination**: fold all of a class's tiers back into one
   Java file (e.g. one `Song.java`), using the former tier boundaries only
   as section comments. Lowest risk, preserves behavior exactly file-for-
   file, but produces a very large class (`CSong` alone would be on the
   order of 10,000 lines) with no sibling-file escape hatch the way C++
   had one.

2. **Architectural decomposition via composition**: use the tier
   boundaries as the seams for real composition instead of just
   recombining them. They already sort close to single-responsibility
   lines - core state/editing, import/export I/O, UI orchestration,
   hardware/MIDI playback - which is itself evidence that `CSong` is a god
   class the C++ codebase merely tolerated. In this option, the hazardous
   dependencies (dialogs, timers, hardware) get extracted behind
   interfaces (e.g. `UserPrompt`, `AudioClock`, `SoundDevice`) that `Song`
   depends on via injection instead of calling globals directly; the
   production app wires in real implementations, tests wire in fakes.
   `GUI_Song.cpp`/`Midi_Song.cpp` stop being "the hazardous tier of
   `CSong`" and become their own classes that depend on `Song`, not the
   reverse.

Either way, the C++-side triage work already done is not wasted: the
dual-mode refactors performed during characterization testing (e.g.
`SendQuestionMessage()`'s test-injectable answer via
`SetTestQuestionAnswer()`, `InstrInfo`/`TrackInfo`'s optional
output-struct parameter) are already previews of the same seam design
option 2 would use in Java, just expressed as an `if (param) {...} else
{...}` branch instead of an injected interface. Whichever option is
chosen for recombining the files themselves, the seams already identified
carry over directly.

### Recommendation: don't pull option 2 forward into C++ yet

It's tempting to do the composition-based split (option 2) directly in
C++ now, on the theory that the codebase needs improving before the port
anyway. The recommendation is **not to**, for now:

- The methods that would benefit most from an injected-interface split are
  exactly the ones with **no test coverage today**. *Correction, checked
  against the existing triage docs after first writing this
  recommendation*: `GUI_Song.cpp` (16 methods) and `Midi_Song.cpp` (1
  method) are not untapped candidates - `plans/BROADER_SURVEY_PLAN.md`
  already read them and confirmed them Category A ("real UI, hardware, or
  mega-wiring"): `GUI_Song.cpp` is the real keyboard-input dispatch layer
  (`InfoKey`/`InstrKey`/`ProveKey`/`TrackKey`/`SongKey`, cursor-goto
  helpers), and `Midi_Song.cpp`'s one method does real
  `midiInGetNumDevs()`/`midiInGetDevCaps()` MIDI hardware enumeration -
  neither has a `MessageBox`/dialog gating otherwise-extractable logic the
  way `InstrInfo`/`TrackInfo` did. `IO_Song.cpp`'s remaining 12 methods are
  the `FileXxx` family, individually re-verified in
  `plans/SONG_IO_SONG_REMAINING_PLAN.md` (Batch 7) to have no
  `InstrChange`-style split available - each unconditionally constructs a
  real `CFileDialog`/`CFileNewDlg` and the dialog's result (the chosen
  path) *is* the method, not a parameter to route around. `IO_Importer.cpp`
  is fully done (`plans/IO_IMPORTER_PLAN.md`, both batches closed). So the
  20%-untested figure is real, but essentially none of it is currently
  dual-mode-shaped - see `plans/DUAL_MODE_PATTERN_PLAN.md` for the honest
  accounting of what (if anything) is actually left.
- The already-tested safe tiers (`SongCore.cpp`: 22, `SongEditing.cpp`:
  82, plus `Song_DumpSong.cpp`/`SongExportV2.cpp` - about 106 methods,
  ~70%) already have 108+ characterization tests locking their behavior
  down. There's little left to gain from splitting further there.
- Building C++-side abstract-base-class/virtual-dispatch interfaces for
  MFC-coupled hazards (dialogs, hardware) is its own kind of complexity
  (ownership, header dependencies, vtables) that may not resemble the
  eventual Java interfaces closely enough to be worth doing twice.

**What's worth continuing now**: applying the dual-mode pattern (optional
output parameter / test-injectable answer, not a full injected interface)
to more of the untested hazardous methods where it's cheap to do safely.
It's lower-risk than real interface extraction, grows test coverage now,
and is exactly the seam-identification work option 2 would need later
regardless of when the bigger split happens. See
`plans/DUAL_MODE_PATTERN_PLAN.md` for the concrete plan.
