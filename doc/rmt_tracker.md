# RASTER Music Tracker - RMT

### About

RASTER Music Tracker (short RMT) is a cross-platform tool for making Atari XL/XE music on a Windows PC.
RMT uses the Atari XL/XE music routines created by Radek Štěrba for a very long time and it was small revolution for all Atari musicians and fans.

This fork called 1.35 is the latest development branch of version 1.34 of RMT.
It is the continuation of the original version 1.28 of RMT by Štěrba and the version 1.34 of RMT by Vin Samuel.

The following versions are available for download:
- [Latest daily build of 1.35 (constantly updated)](https://www.wudsn.com/productions/windows/rastermusictracker/rmt135-daily.zip)
- [Stable version 1.34 (2023-03-10)](https://www.wudsn.com/productions/windows/rastermusictracker/rmt134.0.2023-03-10.zip)
- [Stable version 1.28 (2009-05-19)](https://www.wudsn.com/productions/windows/rastermusictracker/rmt128.zip)

This document contains the official technical description of the tracker and it's design. For every section the current status, the known issues in RMT version 1.34, the work in progress and and the planned changes for the coming version **RMT 2.0** are described. The known issues include not only the issues that affect the end user, but also those which impact the maintainers of the code.

The related description for the relatd RMT module file format can be found [here](./rmt_format.md).

I'm happy to receive feedback on my proposals, ideally via a [personal message on Atariage](https://forums.atariage.com/messenger/compose/?to=17404).

The latest version of this document and the related documents is located at [Github](https://github.com/peterdell/RASTER-Music-Tracker/blob/dev/doc/rmt_tracker.md). So before sending feedback, please check the latest version. The [history](https://github.com/peterdell/RASTER-Music-Tracker/commits/dev/doc)  also displays the past changes.


RMT Tracker 2.0 (DRAFT 2026-01-06)<a id='rmt1'></a>
----------------------------------

Atari 8-bit Emulation
---------------------

The Atari 8-bit emulation consists of two parts
- The emulation of the [MOS Technology 6502 CPU](https://en.wikipedia.org/wiki/MOS_Technology_6502)
- The emulation of one or two [Atari POKEY sound chips](https://en.wikipedia.org/wiki/POKEY).  

### Current Situation

The Pokey sound emulation and Atari 6502 processor emulation aren't built-in components of RMT. If the sound output is needed, the external dynamic DLL libraries with the following functions are required.  If you run RMT without this way described DLLs ( `sa_c6502.dll`, `apokeysnd.dll` or `sa_pokey.dll`), RMT will work, but there won't be any Pokey sound output and Atari sound routines won't be executed.

#### CPU Emulation

Contained in `sa_c6502.dll`
Procedures
- `void C6502_Initialise(BYTE* memory);`
- `int C6502_JSR(WORD* addr, BYTE* areg, BYTE* xreg, BYTE* yreg, int* maxcycles);`
- `void C6502_About(char** name, char** author, char** description)`;


#### Pokey Emulation

Contained in `sa_pokey.dll`
- `void Pokey_Initialise(int *argc, char *argv[]);`
- `void Pokey_SoundInit(uint32 freq17, uint16 playback_freq, uint8 num_pokeys);`
- `void Pokey_Process(uint8 * sndbuffer, const uint16 sndn);`
- `UBYTE Pokey_GetByte(UWORD addr);`
- `void Pokey_PutByte(UWORD addr, UBYTE byte);`
- `void Pokey_About(char** name, char** author, char** description);`

or in `apokeysnd.dll`
- `void APokeySound_Initialize(abool stereo);`
- `void APokeySound_PutByte(int addr, int data);`
- `int APokeySound_GetRandom(int addr, int cycle);`
- `int APokeySound_Generate(int cycles, byte buffer[], ASAP_SampleFormat format);`
- `void APokeySound_About(const char **name, const char **author, const char **description);`


### Known Issues

The following end user issues are already known:
- The export as SAP Type C generates an invalid files.
- The export as WAV does not work yet with the Altirra `sa_pokey.dll`.

The following maintainer issues are already known:
- The code heavily uses macros and global variables. It it not testable.
- The DLL loading and initialization is broken and uses workaround to detect frequencies and PAL/NTSC somehow correct
- Different path in the code to do the same thing (e.g. toggle PAL/NTSC) are copy/past coding, but slightly differebnt.


### Work in Progress

- The code for the CPU and Pokey emulation has been restructured in to separate classes.
- Dependencies have been reduced.
- The initilization sequence was cleaned up and made more robust.

### Future Plans

- Replace the usage of the external DLLs (for which maintenance is either unclear, or nor guarateed/offically supported) by the C-version of ASAP by Fox. There is not reason why a different engine should be used in the tracker that is used during the replay.
- The Atari binary code for the different patches of the player code are currentl included in the C-code as source code. This makes it hard to replace them and check/update/version their content. Instead all Atari binary code should be 
	- present as source code
	- compatible as part fo the build
	- included are binaries in the distribution, similar to for the .prf files are distributed and loaded in DIS602
