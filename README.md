# RASTER Music Tracker - RMT

### About

RASTER Music Tracker (short RMT) is a cross-platform tool for making Atari XL/XE music on a Windows PC.
RMT uses the Atari XL/XE music routines created by Radek Štěrba for a very long time and it was small revolution for all Atari musicians and fans.

This fork is the latest development branch of version 1.34 of RMT.
It is the continuation of the original version 1.28 of RMT by Štěrba and the version 1.34 of RMT by Vin Samuel.

The following versions are available for download:
- [Latest daily build of 1.34.1 (constantly updated)](https://www.wudsn.com/productions/windows/rastermusictracker/rmt134.1-daily.zip)
- [Stable version 1.34.0 (2023-03-10)](https://www.wudsn.com/productions/windows/rastermusictracker/rmt134.0.2023-03-10.zip)
- [Stable version 1.28 (2009-05-19)](https://www.wudsn.com/productions/windows/rastermusictracker/rmt128.zip)

Please provide your feedback about the daily version via one of the following channels:
- Send a [personal message](https://forums.atariage.com/messenger/compose/?to=17404) on AtariAge or create a post in the [AtariAge thread](https://forums.atariage.com/topic/328790-release-raster-music-tracker-v13400)
- Send an e-mail to jac at wudsn.com

### Credits

- [Radek Štěrba](http://atariki.krap.pl/index.php/Raster/C.P.U.), Raster/C.P.U., 2002-2009 ([original website](http://raster.infos.cz/atari/rmt/rmt.htm))<br>
  Thank you for everything you did, we truely miss you <3.
- Robert Petruzela, Bob!k/C.P.U. and - JirkaS/C.P.U.
- Vin Samuel, VinsCool, 2021-2024
- [Peter Dell](www.wudsn.com), JAC! 2024 to present

### Known Issues

There are no more changes to the stable version. If you find and issue in the stable version, please test the daily version to see if the issue is already fixed.

The following issues are already known in the daily version
- The export as SAP Type C generates an invalid files
- The export as WAV does not work yet with the Altirra `sa_pokey.dll`.

### Documentation

- Original [RMT 1.28 documentation](https://html-preview.github.io/?url=https://github.com/peterdell/RASTER-Music-Tracker/blob/dev/doc/rmt_en.html)

- Current [RMT Tracker documentation](https://github.com/peterdell/RASTER-Music-Tracker/blob/dev/doc/rmt_tracker.md) and discussion
- Current [RMT Module File Format documentation](https://github.com/peterdell/RASTER-Music-Tracker/blob/dev/doc/rmt_format.md) and discussion

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
  for concrete RMT module (very useful for background music in demos, games, etc.).
* MIDI IN support!
* MIDI multitimbral playing possibilities.
  You can use the RMT like a Atari multitimbral MIDI instrument. 
  You have to send MIDI output from your MIDI sequencer or player 
  to RMT MIDI input by means of some virtual MIDI cable (for example 
  "MIDI Yoke" etc.). MIDI implementation chart is in midi.txt file.


### Greetings

- Fox/Taquart - Thanks for [XASM|https://github.com/pfusik/xasm] and [ASAP](https://asap.sourceforge.net)
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
