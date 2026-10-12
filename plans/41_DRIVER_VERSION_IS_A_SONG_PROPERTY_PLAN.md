# Plan 41: The tracker driver version belongs to the song

Status: DONE 2026-10-12, S1-S4. Follows plan 40, which had to thread the
driver version through two function signatures to reach the code that
needs it. The version is the wrong kind of value to be a global, and
the codebase already says so in two places.

## Why

Plan 40 gave `CFrequencyTables`/`FrequencyTables` a
`TrackerDriverVersion` parameter, which the callers then had to obtain.
Two of the three could not simply read `g_trackerDriverVersion`:

- `InstrumentsCore.cpp` is deliberately free of `Global.h` (its header
  comment says so, and the test setup depends on it), so
  `CInstruments::GetFrequency` takes the version as a parameter.
- `ASMFileExporterCore.cpp` is linked into the test project, which does
  not link `Global.cpp`. Reading the global there failed to link, which
  is how `ExportAsAsmApply` ended up with a version parameter too.

Both are symptoms of the same thing: a value every layer needs, kept in
the one place the lower layers must not reach.

**The region already solved this.** `CSong::m_ntsc` with
`IsNTSC()`/`SetNTSC()` (`Song.h:30`, `Song.h:408`; Java
`Song.java:103`) is a program-wide playback setting that lives on the
song. It is persisted in `rmt.ini` as `NTSC_SYSTEM`, not in the `.rmt`,
and some thirty call sites read it straight off the song - the
exporters among them, which take no region parameter
(`SongExporterCore.cpp:31`, `SAPFile.cpp:157`). Decisively, **there is
no `g_ntsc`**. One value, one home, reachable from everywhere that has
a song.

**And the Java port already marked this.** `OptionsValues.java` carries

```java
	// TODO: Module
	public TrackerDriverVersion trackerDriverVersion;
```

while every other field in that class is a plain option. The intent is
recorded; this plan carries it out.

Conceptually the driver version is the stronger case of the two. The
region changes the playback rate; the driver version decides which
frequency tables exist at all - whether distortion 4 has its own page,
whether the sawtooth tables are there, which AUDC each distortion plays
with. A module composed on Patch 16 and assembled against the v1
player does not sound the same, which is the fidelity argument plan 37
recorded for the tuning settings.

## What this is and is not

It makes the version a **song-scoped program setting**, exactly what
the region is. It does **not** make it a stored song property: nothing
in the `.rmt` carries it, and nothing will after this plan.

That distinction is worth being precise about, because the module
format looks closer to ready than it is. `CSong::MakeTuningBlock` /
`DecodeTuningBlock` (`Song.cpp:100`, `Song.cpp:152`) are a complete
`0xF3` block that writes and reads region, base note, temperament and
the two line-highlight settings, with a further 64 bytes of tuning
ratios sketched behind a second `TODO`. Both are **dead**: their
declarations in `Song.h:271-272` are commented out, nothing calls them,
and the Java port never carried them at all. So the region's own
"stored with the module" story does not exist either - `m_ntsc` is
written only by `SetNTSC`, from `rmt.ini` and the Options dialog.

A welcome consequence: because nothing loads either value from a
module, opening a file does not reset them. The version will keep
behaving as the option it is today.

## Steps

- **S1. C++: move the value onto `CSong`.** DONE. Add
  `m_trackerDriverVersion` beside `m_ntsc`, with
  `GetTrackerDriverVersion()` / `SetTrackerDriverVersion()` in
  `SongCore.cpp` beside `IsNTSC()`. Delete
  `extern TrackerDriverVersion g_trackerDriverVersion` from `Global.h`
  and its definition from `Global.cpp`, and update the call sites in
  `Rmt.cpp`, `RmtView.cpp` (the `rmt.ini` read and write, the defaults,
  and the Options dialog's compare-and-reload) and `ScriptRunner.cpp`.
  The setter stays a plain accessor, unlike `SetNTSC`, which calls
  `ReInitSound()`: reloading the driver needs `g_AtariTrackerDriver`
  and the missing-file report around it, which is the Global.h
  dependency `SongCore.cpp` exists to avoid, and both call sites
  already orchestrate that reload.

  Two things turned up while doing it. `CSong::MidiEvent` needed no
  accessor at all - it is a `CSong` member, so `GetTrackerDriverVersion()`
  is simply in scope, which is one dependency fewer than the global was.
  And `MissingTrackerDriverText()` in `ScriptRunner.cpp` is a free
  function that read the global, so it took the version as a parameter;
  its two callers now pass the song's and the newly chosen one
  respectively, which is more accurate than before - the message for a
  failed `set driver` now names the file that was missing rather than the
  version in effect.

  One line was dropped as provably dead: the Options dialog assigned
  `g_trackerDriverVersion = dlg.m_trackerDriverVersion` both inside the
  changed-version branch and again after it, where the two are already
  equal on both paths. The NTSC branch beside it never had that
  duplication.
- **S2. C++: drop the parameter plan 40 added.** DONE.
  `ExportAsAsmApply(song, ou, exportType, notesIndexOrFreq,
  durationsType)` again, reading the version from its own `song`
  argument. `CInstruments::GetFrequency` keeps its parameter:
  `CInstruments` has no song and should not acquire one, so the
  exporter passes `song.GetTrackerDriverVersion()` down.
- **S3. Java: the same move.** DONE. The field goes from `RmtOptions` to
  `Song`; `RmtConfig` reads and writes it through the song as it
  already does for `NTSC_SYSTEM`; `OptionsValues.from` reads it beside
  `v.ntsc = session.song.isNTSC()` and the `// TODO: Module` comment
  goes away; `RmtSession.setTrackerDriverVersion` stores it on the
  song; `MidiInput` and `SongFiles` read it from the song they already
  have. `AsmFileExporter.exportAsAsmApply` loses its version
  parameter. This also removes a layering wart plan 40 left behind:
  `SongFiles` currently hands a `ui`-package value
  (`session.options.trackerDriverVersion`) into a `model` function.
- **S4. Tests.** DONE. The existing suites cover the mechanics through every
  exporter and MIDI test. Add one test per program that the version
  round-trips through the song, and keep plan 40's
  "the version decides which layout is read" tests exactly as they
  are - they call the helper directly and must not start depending on
  a song.

  The added test pins three things in each program: the default is
  PATCH16, the accessor round-trips, and `ClearSong()` leaves it alone.
  The last is the one that could regress silently - if the version ever
  became part of what a module load resets, every open would quietly
  throw away the user's driver choice.

## Verification

- 450 C++ and 698 Java tests green, up from 449 and 697 - S4's one test
  per program and nothing else moved.
- `compare_exports`: 4 scripts, every exported file identical in both
  programs. The move altered no output.
- `rmt.ini`: `TRACKERDRIVERVERSION = 6` still written beside
  `NTSC_SYSTEM = 0` by `Rmt.exe`, and Java's
  `RmtConfigTest.theDefaultsFormatExactlyAsRmtExeWritesThem` (which
  asserts the whole default file verbatim) and
  `everyValueSurvivesAWriteReadRoundTrip` both still pass, so the two
  programs' files have not drifted.
- The script's `set driver unpatched` / `patch16` export of Delta with
  `notes=freq` produces exactly the values plan 40 measured - `$66, $88,
  $44, $5B` from the v1 tables and `$65, $87, $43, $5A` from Patch 16's.

## Out of scope

- Storing the version in the module. That is the `0xF3` block's
  revival, together with plan 37's finding about carrying the
  composer's tuning, and it is a file-format decision for the user -
  including what an older RMT should do with a block it does not
  understand.
- `CAtariTrackerDriver` remembering which version it loaded. That is
  the more authoritative answer to "what layout do these bytes have?",
  and the two cannot diverge today because changing the option reloads
  the driver on the spot (`RmtView.cpp:870`, `ScriptRunner.cpp:682`).
  The song is still the right home for "what the composer used", which
  is the question the exporters are really asking.
