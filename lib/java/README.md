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

**Planned use in the Java port** (`plans/JAVA_PORT_NEXT_STEPS_PLAN.md`'s
Phase A, items 2-4): ASAP's `net.sf.asap` package contains a complete,
portable, pure-software 6502 CPU emulator (`Cpu6502.java`) plus a
dual-POKEY sound chip emulator (`Pokey`/`PokeyChannel`/`PokeyPair.java`) -
unlike this port's own `AtariTrackerDriver`, whose `play()`/`setPokey()`/
`silence()` are permanently no-ops because the C++ original's `C6502`
wraps an external native DLL (`sa_c6502.dll`) that has no Java equivalent.
ASAP's emulator has no such dependency and is the intended real
CPU/POKEY backing for the Java port's own SAP-R/LZSS/WAV/XEX export family
once that work starts. See `plans/JAVA_PORT_NEXT_STEPS_PLAN.md` for the
full integration analysis, including a small planned patch to the vendored
`ASAP.java` source (adding one accessor method) needed for the raw
per-frame POKEY register dump that `CPokeyStream::Record()`'s SAP-R-style
export needs - not yet applied, since this item hasn't been started.
