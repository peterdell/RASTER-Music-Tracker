# lib/java

Vendored, pre-built third-party Java libraries used by the Java port
(`src/java`) that are **not** resolved via Maven (see `pom.xml`'s
`<dependencies>` for those - e.g. `com.wudsn.tools.base`/`.base.atari`).

This folder is for jars obtained/built outside Maven Central and the
project's own Maven dependencies.

## `asap.jar` / `asap-8.0.0-java-src.zip`

The official Java build of ASAP (Another Slight Atari Player,
[asap.sourceforge.net](https://asap.sourceforge.net/)) version 8.0.0, plus
its full generated Java source, copied from
`C:\jac\system\WWW\Sites\asma.atari.org\java\lib` (per that folder's own
`asap.txt`: built via `make java/asap.jar` from ASAP's upstream `.fu`
("fut"-language) portable source - the same multi-language-codegen
approach that produces `src/cpp/asap/asap.c`/`.h`, this repo's own
already-vendored C build of the same library, used today only by
`RmtTest.cpp`'s developer-only `/TEST` verification utility).

**Used in the Java port** (`plans/16_JAVA_PORT_NEXT_STEPS_PLAN.md`'s Phase A,
items 2-3 - DONE 2026-09-26; `plans/19_JAVA_AUDIO_PLAN.md` - B8, 2026-09-27):
ASAP's `net.sf.asap` package contains a complete, portable, pure-software
6502 CPU emulator (`Cpu6502.java`) plus a dual-POKEY sound chip emulator
(`Pokey`/`PokeyChannel`/`PokeyPair.java`). The C++ original's `C6502`/
`CXPokey` wrap external native DLLs (`sa_c6502.dll`/`sa_pokey.dll`) that
have no Java equivalent; ASAP's emulator has no such dependency and is the
port's CPU and POKEY in two roles:

- **"RMT mode"** (`ASAP.rmt*` methods, wrapped by
  `org.atari.raster.rmt.model.AtariCpu`): RMT's own tracker driver binary
  runs on the 6502 with the hardware pages as plain RAM (the POKEY register
  shadow at $D200/$D210, as the C++ emulator has it), and the POKEY pair is
  fed and rendered directly. This is live playback, the keyboard preview
  and `Song.dumpSongToPokeyStream`'s SAP-R dump (through
  `AtariTrackerDriver.play()`), exactly as in C++.
- **The module player** (unmodified upstream API: `load`/`playSong`, plus
  the `stepFrame`/`getPokeyRegisterShadow` extensions): the
  `LivePlaybackTest` cross-check plays the exported module as an
  independent reference for the tracker driver's register output. (The
  WAV export used it until B8c; it now replays the recorded register
  stream through the POKEY pair, as C++ does.)

**Vendored as source, not as this pre-built jar**: the integration lives in
`src/java/net/sf/asap/`, compiled as ordinary project sources via `pom.xml`'s
existing `src/java` resource rule. The jar and source archive beside this
file stay only as the untouched reference copy of the 8.0.0 release.

Those sources are no longer that release. They are generated from ASAP's
own `.fu` sources by `make java/asap.jar` in the development clone
(`C:\jac\system\Fusion\Programming\Repositories\Tools\asap`), currently
from commit `f4afcfd` - upstream `28af663` of 2026-09-22 plus the two methods
offered back to ASAP's author (`plans/31_ASAP_UPSTREAM_CONTRIBUTION_PLAN.md`,
step A2). `ASAP.stepFrame()` and `ASAP.getPokeyRegisterShadow(chip, offset)`
and the package-private `Pokey.skctl` therefore come out of the generator now
and are no longer hand edits. Regenerating needs `fut` and, for the player
routines, `xasm` on the path (`C:\jac\system\Atari800\Tools\ASM\XASM`).

What is still added by hand, and only in `ASAP.java`: the "RMT mode" section
(`rmtInitialize`/`rmtMemory`/`rmtJsr`/`rmtPokeRegister`/`rmtRender`, running
RMT's own tracker driver on this CPU with plain-RAM hardware pages and a
directly fed POKEY pair) and the two `rmtMode` checks at the top of
`peekHardware`/`pokeHardware` that it needs. See that file's header comment.
This mirrors `src/cpp/asap/asap-patch.h`/`.cpp`, the same extension-point
pattern for the C build of the same library.
