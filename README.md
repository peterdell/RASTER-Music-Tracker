# RASTER Music Tracker - RMT

### About

RASTER Music Tracker (short RMT) is a cross-platform tool for making Atari XL/XE music on a Windows PC.
RMT uses the Atari XL/XE music routines created by Radek Štěrba from 2002 to 2009.
It was a small revolution for all Atari musicians and fans.

This fork is the latest development branch of RMT, version 1.36.
It is the continuation of the original version 1.28 of RMT by Štěrba and the version 1.34 of RMT by Vin Samuel.

The following versions are available for download:
- [Latest daily build of 1.36 (constantly updated)](https://github.com/peterdell/RASTER-Music-Tracker/releases)
- [Stable version 1.34 (2023-03-10)](https://www.wudsn.com/productions/windows/rastermusictracker/rmt134.00-stable.zip)
- [Stable version 1.28 (2009-05-19)](https://www.wudsn.com/productions/windows/rastermusictracker/rmt128.zip)

See the [change history](https://github.com/peterdell/RASTER-Music-Tracker/blob/dev/doc/rmt_changes.md) for the differences between the [versions](https://github.com/peterdell/RASTER-Music-Tracker/blob/dev/doc/rmt_versions.md).

Please provide your feedback about the daily version via one of the following channels:
- Send a [personal message](https://forums.atariage.com/messenger/compose/?to=17404) on AtariAge or create a post in the [AtariAge thread](https://forums.atariage.com/topic/328790-release-raster-music-tracker-v13400)
- Send an e-mail to jac at wudsn.com
- Create an issue or a feature request on [GitHub](https://github.com/raster-atari-org/RASTER-Music-Tracker/issues).


### Documentation

- Current [RMT 1.36 Documentation](https://github.com/peterdell/RASTER-Music-Tracker/blob/dev/doc/rmt_en.md)
- Original [RMT 1.28 documentation](https://html-preview.github.io/?url=https://github.com/peterdell/RASTER-Music-Tracker/blob/dev/doc/rmt_en_128.html)

Technical Documentation
- Current [RMT Tracker documentation](https://github.com/peterdell/RASTER-Music-Tracker/blob/dev/doc/rmt_tracker.md) and discussion
- Current [RMT Module File Format documentation](https://github.com/peterdell/RASTER-Music-Tracker/blob/dev/doc/rmt_format.md) and discussion


### Java port

This repository also contains a Java/Swing port of RMT 1.36 (`src/java`), developed side by side with the C++ program so RMT can run on Windows, Linux and macOS.
The port covers the tracker, the editors, the import/export formats and real-time sound; the emulated 6502 and POKEY come from [ASAP](https://asap.sourceforge.net).
Not ported: printing.

- Build and test: `mvn package` from the repository root (needs Java 21, Maven and the [WUDSN Base](https://github.com/wudsn/wudsn-base) libraries installed with `mvn install`).
  The result is the runnable `target/rmt.jar`; `mvn test` runs the tests alone.
- Run: `java -jar target/rmt.jar [song.rmt]`. The program folder is the jar's folder (or `-Drmt.config.dir=<folder>`); it holds `rmt.ini`/`tuning.ini` and, as for `Rmt.exe`, the `rmt/` layout (`resources/`, `docs/`, `instruments/`, `songs/`). From a checkout the `rmt/` sub-folder is found automatically.
- Scripting: `java -jar rmt.jar /SCRIPT:<file>` runs a script (open, export in any format, save) without a window, for batch files and CI; Tools > Run Script runs one from the window. `Rmt.exe /SCRIPT:<file>` runs the same scripts. See the [scripting documentation](doc/rmt_scripting.md). The scripts in `test-resources/scripts` run through both programs (`build/compare_exports.ps1`, the test `CrossProgramExportTest`) and every exported file must be byte-identical.
- Eclipse: import the repository root ("Existing Projects into Workspace", Maven Integration) and use `launch/Rmt.launch`.
- Releases: pushing a tag `v1.36.<n>` runs `.github/workflows/release.yml`, which builds portable app images with a bundled Java runtime for Windows, Linux and macOS (`build/stage_java_release.sh` stages the layout) and attaches them to a GitHub Release.
- Documentation: the Markdown files in `doc/` are the source. Both distributions ship them as HTML in `docs/`, generated during the build by the jar's `DocGenerator` (see `plans/23_DOC_GENERATION_PLAN.md`).
- The port's plans and notes are in `plans/` (`13_JAVA_PORT_PLAN.md`, `18_JAVA_UI_PORT_PLAN.md`, `NOTES.md`).


### Main features:

Note that this is as of RMT 1.28 and not accurate for 1.34 and later!

* Mono 4 tracks / stereo 8 tracks.
* 254 tracks, each with its own length (256 beats max.) and with support for track loop.
* 64 instruments (stereo, instrument table up to 32 steps - 2 types and 2 modes with loop,
  instrument envelope up to 32 steps with loop, portamento, filter, 16bit bass, volume slide,
  volume minimum, vibrato, frequency shifting, etc.).Fully automatic management of AUDCTL
  register (filters, 16bit basses) and/or manual AUDCTL settings.
* Support for "volume only" forced output.
* Note portamento up/down effect.
* Instrument envelope commands for note/frequency shifting and support for special 
  "like a C64 SID chip" filtering.
* Up to 256 lines for song (with "goto line" support).
* Beat speed 1 to 255 (1/50 to 255/50 sec).
* Instrument speed from 1 to 4 per screen (up to 1/200 sec).
* Main input/output song file format: RMT song files (*.rmt).
* Input/output instrument file format: RMT instrument files (*.rti).
* Export formats: RMT stripped song file (*.rmt), SAP file (*.sap),
  XEX Atari executable MSX file (*.xex), ASM simple notation source (*.asm).
* Import formats: ProTracker modules (*.mod), Atari XE/XL Theta Music Composer songs (*.tmc)
* Support for speed/size optimizations of RMT assembler player routine 
  for a concrete RMT module (very useful for background music in demos, games, etc.).
* MIDI IN support!
* MIDI multitimbral playing possibilities.
  You can use the RMT like an Atari multitimbral MIDI instrument. 
  You have to send MIDI output from your MIDI sequencer or player 
  to RMT MIDI input by means of some virtual MIDI cable (for example 
  "MIDI Yoke" etc.). The MIDI implementation chart is in the midi.txt file.

### Known Issues

Issues are tracked on the [GitHub issue tracker](https://github.com/raster-atari-org/RASTER-Music-Tracker/issues).

There are no more changes to the 1.34 version. If you find an issue in the stable version, please test the daily 1.36 version to see if it's already fixed.


### Credits

- [Radek Štěrba](http://atariki.krap.pl/index.php/Raster/C.P.U.), Raster/C.P.U., 2002-2009 ([original website](http://raster.infos.cz/atari/rmt/rmt.htm))<br>
  Thank you for everything you did, we truly miss you <3.
- Robert Petruzela, Bob!k/C.P.U. and - JirkaS/C.P.U.
- [Vin Samuel](https://github.com/VinsCool), VinsCool, 2021-2024
- [Peter Dell](www.wudsn.com), JAC!, 2024 to present

#### Additional Credits
- New features, bugfixes and improvements for RMT 1.31-1.34 by VinsCool
- POKEY Tuning Calculations programming by VinsCool, with helpful advices from synthpopalooza and OPNA2608
- SAP-R Dumper and VUPlayer programming by VinsCool
- LZSS compression programming by DMSC, C++ port by VinsCool
- Unrolled LZSS music driver by Rensoupp, with few changes and new features by VinsCool
- New Bitmap graphics, ideas and beta testing by PG
- Ideas, features suggestions and inspiration by PG, Enderdude, Spring, Ivop, Tatqoo, Miker
- Spiteful inspiration by Rensoupp, Emkay, and anyone who challenged me to try doing things believed impossible or outside of my abilities ;)
- Special thanks to everyone from The Chiptune Café, AtariAge, and GBAtemp who motivated me to work harder on the revival of RMT!

### Greetings

- Fox/Taquart - Thanks for [XASM](https://github.com/pfusik/xasm) and [ASAP](https://asap.sourceforge.net)
- Jaskier/Taquart - Thanks for TMC and a lot of RMT routine speed/size optimizations
- Tatqoo/Taquart
- Sack/Cosine
- X-ray/Grayscale
- Greg/Grayscale
- Bewu/Grayscale
- PG - Thanks for the [ASMA - Atari SAP Music Archive](https://asma.atari.org)
- Fandal
- ZdenekB
- KrupkaJ
- Pepax
- LiSU
- Miker
- Dely
- Nils Feske
- Elan
- Wrathchild
- Kozyca
- Born/LaResistance
- Sal Esquivel
- Nooly
- All the active "Atariarea" Polish Atarians (https://atariarea.krap.pl)<br>
- ...and all other 8-bit Atarians all over the world! :-)


### Disclaimer

THIS SOFTWARE IS PROVIDED "AS IS" WITHOUT WARRANTY OF ANY KIND.
AUTHOR DOES NOT WARRANT, GUARANTEE, OR MAKE ANY REPRESENTATIONS REGARDING THE USE, OR THE RESULTS OF USE, OF THE SOFTWARE OR WRITTEN MATERIALS IN TERMS OF CORRECTNESS, ACCURACY, RELIABILITY, CURRENTNESS, OR OTHERWISE.
THE ENTIRE RISK AS TO THE RESULTS AND PERFORMANCE OF THE SOFTWARE IS ASSUMED BY YOU.
