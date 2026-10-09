# RASTER Music Tracker - RMT

### About

RASTER Music Tracker (short RMT) is a cross-platform tool for making Atari XL/XE music on a Windows PC.
RMT uses the Atari XL/XE music routines created by Radek Štěrba from 2002 to 2009.
It was a small revolution for all Atari musicians and fans.

This fork is the latest development branch of RMT, version 1.36.
It is the continuation of the original version 1.28 of RMT by Štěrba and the version 1.34 of RMT by Vin Samuel.

This document contains the official technical description of the tracker and its design. For each section, the current status, known issues in RMT version 1.34, work in progress, and planned changes for the upcoming version **RMT 2.0** are described. The known issues include not only those affecting end users but also those impacting code maintainers.

The description for the RMT module file format is [here](./rmt_format.md).

I'm happy to receive feedback on my proposals, ideally via a [personal message on Atariage](https://forums.atariage.com/messenger/compose/?to=17404).

The latest version of this document and the related documents are located at [Github](https://github.com/peterdell/RASTER-Music-Tracker/blob/dev/doc/rmt_tracker.md). So before sending feedback, please check the latest version. The [history](https://github.com/peterdell/RASTER-Music-Tracker/commits/dev/doc) also shows past changes.

Glossary
---------

The following terms are used in the documentation. Outside of this documentation, they are sometimes used interchangeably when the distinction is not relevant. For example, people will use "song" when they refer to either the "song", the "module", or the "module file". This documentation will use the terms only as defined below.

- Tracker - An editor program to create music and save it as a file that can be opened for editing again.
- Tracker Driver - A part of the tracker that generates the actual sound from the tracker data and user input. In the case of RMT, the tracker diver is a piece of MOS 6502 code that runs on an emulated Atari 8-bit computer. See [Tracker Drivers](rmt_tracker_drivers.md) for details.
- Tracker Driver Version - A variant of the tracker driver that interprets the tracker data and user input differently. For example, different tracker driver versions have a distinct feature set or tuning. A correct replay of the music requires using the same tracker driver version that was used to create it. See [Tracker Drivers](rmt_tracker_drivers.md) for details.
- Instrument - A logical device to create a characteristic sound at different pitches.
- Pattern - A sequence of notes and their attributes (e.g., length, effects, ...) to be played on an instrument.
- Track - A logical voice. Patterns can be assigned to tracks for replay.
- Channel - A physical output to create sound. Tracks can be assigned to channels for replay.
- Mono - Mono indicates that all channels are combined into a single output.
- Stereo - Stereo indicates that all channels are combined into two different outputs called "left" and "right".
- Song - A piece of music created in a tracker. It consists of a sequence of patterns.
- Module - A data structure with one or more songs.
- Module File - A file storing the module.
- Module File Extension - A file extension of the module files, indicating the type of module, e.g., ".rmt" or ".mod".
- Module File Format - A layout that defines how the module is stored in the module file.
- Module File Format Version - A version of the layout that defines how the module is stored in the module file. Different module file format versions may be reflected by having different file extensions, e.g., ".cmc" vs. ".cmr", or by data inside the file itself.

RMT Tracker 2.0 (DRAFT 2026-01-06)<a id='rmt1'></a>
----------------------------------

Atari 8-bit Emulation
---------------------

The Atari 8-bit emulation consists of two parts
- The emulation of the [MOS Technology 6502 CPU](https://en.wikipedia.org/wiki/MOS_Technology_6502)
- The emulation of one or two [Atari POKEY sound chips](https://en.wikipedia.org/wiki/POKEY).  

### Current Situation

The two programs obtain the emulation differently.

#### C++ Program (Windows)

The Pokey sound emulation and Atari 6502 processor emulation aren't compiled into `Rmt.exe`. They are loaded at startup from external dynamic DLL libraries located next to the program, and the release ships the required DLLs (`sa_c6502.dll`, `sa_pokey.dll`), so sound works out of the box. If the DLLs are removed, RMT will work, but there won't be any Pokey sound output and the Atari sound routines won't be executed; a warning reports each missing library.

##### CPU Emulation

Contained in `sa_c6502.dll` from [Altirra](https://www.virtualdub.org/altirra.html) by Avery Lee.
Procedures
- `void C6502_Initialise(BYTE* memory);`
- `int C6502_JSR(WORD* addr, BYTE* areg, BYTE* xreg, BYTE* yreg, int* maxcycles);`
- `void C6502_About(char** name, char** author, char** description)`;

##### Pokey Emulation

Two libraries are supported and tried in this order:

`apokeysnd.dll` from [ASAP](https://asap.sourceforge.net/) by Piotr Fusik is loaded first, if present. It is not part of the release.
- `void APokeySound_Initialize(abool stereo);`
- `void APokeySound_PutByte(int addr, int data);`
- `int APokeySound_GetRandom(int addr, int cycle);`
- `int APokeySound_Generate(int cycles, byte buffer[], ASAP_SampleFormat format);`
- `void APokeySound_About(const char **name, const char **author, const char **description);`

`sa_pokey.dll` from [Altirra](https://www.virtualdub.org/altirra.html) by Avery Lee is the fallback and is part of the release.
- `void Pokey_Initialise(int *argc, char *argv[]);`
- `void Pokey_SoundInit(uint32 freq17, uint16 playback_freq, uint8 num_pokeys);`
- `void Pokey_Process(uint8 * sndbuffer, const uint16 sndn);`
- `UBYTE Pokey_GetByte(UWORD addr);`
- `void Pokey_PutByte(UWORD addr, UBYTE byte);`
- `void Pokey_About(char** name, char** author, char** description);`

In addition, the C sources of ASAP itself are compiled into `Rmt.exe` (`src/cpp/asap/`), but they do not serve any feature yet. They are groundwork for playing `.sap` and other Atari sound module files directly ("Tools > Open ASAP File", a prepared but not yet functional menu entry).

#### Java Program (all platforms)

The Java program needs no external libraries for the emulation. ASAP's pure-Java 6502 CPU and dual-POKEY emulation - the same emulation `apokeysnd.dll` is built from - is vendored as source in `src/java/net/sf/asap`, extended with an "RMT mode" for the tracker (see `lib/java/README.md`), and compiled into the program. Sound therefore always works; the only thing that silences the Java program is a missing tracker driver file (`resources/drivers/*.obx`), which is reported with a warning.

### Known Issues

Issues are tracked on the GitHub issue tracker:
https://github.com/raster-atari-org/RASTER-Music-Tracker/issues

The following general maintainer issues concern the C++ program, which the Java port is replacing:
- The code still heavily uses macros and global variables. The testable halves have been split out into `...Core.cpp` files and are covered by the GoogleTest suite in `src/cpp/test/`, but the UI- and hardware-facing halves remain untested.
- The DLL loading and initialization is brittle and uses workarounds to detect the frequencies and PAL/NTSC.
- Different paths in the code do the same thing (e.g. toggle PAL/NTSC) as copy/paste coding, but slightly different.

### Future Plans

- Replace the usage of the external DLLs (for which maintenance is either unclear, or nor guaranteed/officially supported) by the C-version of ASAP by Fox. There is not reason why a different engine should be used in the tracker that is used during the replay.
- The Atari binary code for the different patches of the player code are currently included in the C-code as source code. This makes it hard to replace them and check/update/version their content. Instead all Atari binary code should be 
	- present as source code
	- compatible as part of the build
	- included are binaries in the distribution, similar to for the ".prf" files are distributed and loaded in DIS602
