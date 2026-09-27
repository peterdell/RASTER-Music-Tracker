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
items 2-3 - DONE 2026-09-26; `plans/JAVA_AUDIO_PLAN.md` - B8, 2026-09-27):
ASAP's `net.sf.asap` package contains a complete, portable, pure-software
6502 CPU emulator (`Cpu6502.java`) plus a dual-POKEY sound chip emulator
(`Pokey`/`PokeyChannel`/`PokeyPair.java`). The C++ original's `C6502`/
`CXPokey` wrap external native DLLs (`sa_c6502.dll`/`sa_pokey.dll`) that
have no Java equivalent; ASAP's emulator has no such dependency and is the
port's CPU and POKEY in two roles:

- **"RMT mode"** (`ASAP.rmt*` methods, wrapped by
  `com.wudsn.tools.rmt.model.AtariCpu`): RMT's own tracker driver binary
  runs on the 6502 with the hardware pages as plain RAM (the POKEY register
  shadow at $D200/$D210, as the C++ emulator has it), and the POKEY pair is
  fed and rendered directly. This is live playback, the keyboard preview
  and `Song.dumpSongToPokeyStream`'s SAP-R dump (through
  `AtariTrackerDriver.play()`), exactly as in C++.
- **The module player** (unmodified upstream API: `load`/`playSong`/
  `generate`): `WaveFileExporter` renders the exported module to WAV, and
  the `LivePlaybackTest` cross-check plays the exported module as an
  independent reference for the tracker driver's register output.

**Vendored as source, not as this pre-built jar**: the actual, patched
integration lives in `src/java/net/sf/asap/` (copied from this folder's
`asap-8.0.0-java-src.zip`, plus its `.obx` player-routine resources
extracted from `asap.jar`), compiled as ordinary project sources via
`pom.xml`'s existing `src/java` resource rule - not this jar, which stays
here only as the untouched reference copy. Clearly-marked RMT additions on
top of the otherwise-unmodified generated source (mirroring
`src/cpp/asap/asap-patch.h`/`.cpp`'s same extension-point pattern for this
same upstream library, see `ASAP.java`'s own header comment for the exact
list): `ASAP.stepFrame()` (exact single-frame stepping) and
`ASAP.getPokeyRegisterShadow(chip, offset)` (the raw last-poked register
byte, which the public API has no way to read since real POKEY audio
registers are hardware write-only) for the module player, the "RMT mode"
section (`rmtInitialize`/`rmtMemory`/`rmtJsr`/`rmtPokeRegister`/
`rmtRender`, plus the RMT-mode checks at the top of `peekHardware`/
`pokeHardware`), and `Pokey.skctl` widened to package-private.
