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

**Used in the Java port** (`plans/JAVA_PORT_NEXT_STEPS_PLAN.md`'s Phase A,
items 2-3 - DONE 2026-09-26): ASAP's `net.sf.asap` package contains a
complete, portable, pure-software 6502 CPU emulator (`Cpu6502.java`) plus
a dual-POKEY sound chip emulator (`Pokey`/`PokeyChannel`/`PokeyPair.java`) -
unlike this port's own `AtariTrackerDriver`, whose `play()`/`setPokey()`/
`silence()` are permanently no-ops because the C++ original's `C6502`
wraps an external native DLL (`sa_c6502.dll`) that has no Java equivalent.
ASAP's emulator has no such dependency, and is now the real CPU/POKEY
backing for `Song.dumpSongToPokeyStream`/`PokeyStream` (via the new
`AsapEmulator` class), with the SAP-R/LZSS/XEX/WAV export family (item 4)
still to come.

**Vendored as source, not as this pre-built jar**: the actual, patched
integration lives in `src/java/net/sf/asap/` (copied from this folder's
`asap-8.0.0-java-src.zip`, plus its `.obx` player-routine resources
extracted from `asap.jar`), compiled as ordinary project sources via
`pom.xml`'s existing `src/java` resource rule - not this jar, which stays
here only as the untouched reference copy. Two small, clearly-marked RMT
additions on top of the otherwise-unmodified generated source (mirroring
`src/cpp/asap/asap-patch.h`/`.cpp`'s same extension-point pattern for this
same upstream library, see that source's own header comments for the
exact diff): `ASAP.stepFrame()` (exact single-frame stepping) and
`ASAP.getPokeyRegisterShadow(chip, offset)` (the raw last-poked register
byte, which the public API has no way to read since real POKEY audio
registers are hardware write-only).
