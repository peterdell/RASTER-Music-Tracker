# lib/java

Vendored, pre-built third-party Java libraries used by the Java port
(`src/java`) that are **not** resolved via Maven (see `pom.xml`'s
`<dependencies>` for those - e.g. `com.wudsn.tools.base`/`.base.atari`).

This folder is for jars obtained/built outside Maven Central and the
project's own Maven dependencies - for example, a pre-built `asap.jar`
(the official Java build of the ASAP library, [asap.sourceforge.net](https://asap.sourceforge.net/)),
which would be the Java equivalent of `src/cpp/asap/` (a vendored,
auto-generated C build of the same library, currently used only by
`RmtTest.cpp`'s developer-only `/TEST` verification utility - see
`plans/JAVA_PORT_PLAN.md`).

Empty for now - nothing has needed a vendored jar yet.
