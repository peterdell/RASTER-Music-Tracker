# Tracker Drivers

A tracker driver is part of the tracker that generates the actual sound from the tracker data and user input. In the case of RMT, the tracker driver is a piece of MOS 6502 code that runs on an emulated Atari 8-bit computer.

# Tracker Driver Versions

A tracker driver version is a variant of the tracker driver that interprets the data and user input differently. For example, different tracker driver versions have a distinct feature set or tuning. A correct replay of the music requires using the same tracker driver version that was used to create it.

The original tracker driver version is the one by Radek Štěrba (Raster) in RMT 1.28. Many people modified the driver code and data to yield new effects. All known older versions are preserved for compatibility.

Tracker driver versions are identified in different ways.
For inclusion into RMT, the tracker driver versions are numbered starting from 1. 
Additionally, they have a readable name for users and a constant name when used in the source code.

| Code       | Name                            | Constant Name          | Author   |
|------------|---------------------------------|------------------------|----------|
| 1          | RMT 1.28 Unpatched              | UNPATCHED              | Raster   | 
| 2          | RMT 1.28 With Tuning            | UNPATCHED_WITH_TUNING  | VinsCool | 
| 3          | RMT 1.25 Patch 3                | PATCH3                 | Analmux  | 
| 4          | RMT 1.27 Patch 6                | PATCH6                 | Analmux  | 
| 5          | RMT 1.28 Patch 8                | PATCH8                 | Analmux  |
| 6          | RMT 1.28 Patch 16               | PATCH16                | VinsCool |
| 7          | RMT 1.28 Patch Prince of Persia | PATCH_PRINCE_OF_PERSIA | VinsCool |


## RMT 1.28 Unpatched by Raster

The original RMT driver version that set the standard for many years with Atari 8-bit music. This version was mainly used with RMT 1.28, as well as the unofficial RMT 1.30 by Rudla Kuda (Rudla).

## RMT 1.28 With Tuning by Vin Samuel (VinsCool)

This driver version was never completed and is no longer included in the program. It is only mentioned here to explain the gap between versions 1 and 3.

## RMT 1.25 Patch 3 by Bernhard Pos (Analmux)

Adapted from Analmux's original post on [AtariAge](https://forums.atariage.com/topic/228757-instrumentarium-final-release-links-summary/): 
> The music track "Instrumentarium" is composed, tracked, and RMT-patched by me: ANALogue MUltipleXer. I used a patch to change some RMT possibilities: f.e, I added sawtooth wave (ringmod), triangle wave (linear subharmonic), clarinet (16-bit), distortion guitar (16-bit), and sync modes (2-tone filter & 16-bit). At the end of 2009, this tune was named "Instrumentarium Remix 1"; it's the same tune. I was using exotic undocumented POKEY features. It takes almost no CPU time.

## RMT 1.27 Patch 6 by Analmux

Adapted from Analmux's original post on [AtariAge](https://forums.atariage.com/topic/175878-rmt-patch-6/):
> Tables freqtabbasslo and freqtabbasshi are patched (all I did was multiply each 16-bit value by 15). The '16-bit distortion 6' instrument, which was really a generator C instrument, is now a generator A instrument. I removed the blocking of the 1st channel, to enable 16-bit filter-like effects."

## RMT 1.28 Patch 8 by Analmux

Adapted from Analmux's original post on [AtariAge](https://forums.atariage.com/topic/234769-rmt-patch-8/): 

> The 16-bit 'distortion guitar' works best when you use RMT-distortion 8 instead. It looks a bit mysterious, but the HSB (highest significant byte) has a full auto-modulation control.
If you use command 2 (at voice 2), then (X/,Y\) = (0,0) gives a modulation of 0. If f.e. (X/,Y\) = (0,6) = $06, then the auto-modulation speed is not zero, and it sounds a bit like an electric distortion guitar effect. Backwards auto-modulation, then you compute a binary minus of $06, i.e. $00-$06=$FA.

| RMT Distortion | POKEY  Distortion | Bits | Clock    | Number of Bytes |Instrument                                                       |
|----------------|-------------------|------|----------|-----------------|-----------------------------------------------------------------|
| 0              | 0                 | 8    | x        | x               | White noise                                                     |
| 2              | 2                 | 8    | 1.79 MHz | 48              | Poly 5 / Generator 2                                            |
| 4              | A                 | 8    | 1.79 MHz | 36              | Sawtooth / Triangle                                             |
| 6              | C                 | 8LSB | 1.79 MHz | 32              | Clarinet (Poly 4), LSB                                          |
| 8              | A                 | 8HSB | 1.79 MHz | 64              | Harmonic square undertones of Clarinet / Distortion guitar, HSB |
| A              | A                 | 8    | 15 kHz   | 48              | Pure bass                                                       |
| A              | A                 | 8    | 1.79 MHz | 12              | Pure flute                                                      |
| C              | C                 | 8    | 1.79 MHz | 31              | Poly 4 (degenerate 1)                                           |
| C              | C                 | 8    | 1.79 MHz | 17              | Poly 4 (degenerate 3)                                           |
| E              | 8                 | 8LSB | 1.79 MHz | 32              | Distortion guitar (Poly 9), LSB                                 |

## RMT 1.28 Patch 16 by VinsCool

This driver version is an extensive hack of the original RMT 1.28 driver by Raster, inspired directly by Analmux's RMT Patch8 hack. 
The new features include: more tuning tables, a two-tone filter toggle, an AUDCTL timing fix, AUTOFILTER behavior tweaks, etc.
Unfortunately, this patch also introduced many compatibility issues with older modules and a couple of bugs.
Despite that, this is probably the most "feature-rich" version of the RMT driver patches created so far.
All that came at the cost of extra memory usage and heavier CPU load, so this is no longer an "optimal" driver.
This is currently the default RMT driver version included and used by RMT 1.34 and later.

## Tuning

Selecting this driver enables the tuning settings (File > Properties... >
Tuning...); all other drivers play their own built-in frequency tables, so
the button is disabled for them. The settings are stored with the module.

| Setting           | Default                                                    | Description |
|-------------------|------------------------------------------------------------|-------------|
| A4 frequency | 440.83751645933 Hz for PAL<br>444.8957788679134 Hz for NTSC | The reference pitch of the note A-4, in Hertz. The defaults are RMT's traditional tuning, not concert pitch 440 Hz: the frequency tables the Patch 16 player ships were generated with exactly these values (the section below asserts that equality). The hand-made tables of the RMT 1.28 era drivers are close to, but not byte-identical with, the generated ones. |
| Base note         | A- (3)                                                     | The key the temperament is tuned to: the note that receives the temperament's unison ratio (the tonic). Stored as a number, C- = 0 counting downward, so A- = 3. |
| Temperament       | Equal Temperament (Default)                                | Equal temperament, one of 21 historical presets (each verified against its source file in the [Scala scale archive](https://www.huygens-fokker.org/microtonality/scales.html)), or a custom temperament entered in musical cents. |

The two default base frequencies are in the exact ratio of the NTSC and PAL
machine clocks (1789773 / 1773447) and therefore produce bit-identical
frequency tables: every generated pitch is proportional to the base
frequency, and every POKEY divider is computed from the quotient
clock / pitch, so scaling the base frequency by the clock ratio cancels out
exactly - before any rounding. This holds for every temperament, base note
and custom tuning, not only for equal temperament. It is why one player
binary serves both systems, and why switching a module between PAL and NTSC
multiplies the base frequency by exactly this ratio.

## The Frequency Table Page of Patch 16 at $B000

Patch 16 is the only included tracker driver version that keeps its
frequency tables in a dedicated memory block at $B000-$B5FF, called
TABLES in the driver source. All older versions carry their tables
inside the player code body instead. The block consists of six
256-byte pages, one purpose each. A distortion page holds three
tables: 64 bytes for the 64 kHz clock, 64 bytes for the 1.79 MHz
clock, and 128 bytes for the 16-bit joined mode (64 words, low byte
first). Every 64-byte table maps a note number from 0 to 63 to the
AUDF divisor for that note.

| Page  | Name              | Content                                  |
|-------|-------------------|------------------------------------------|
| $B000 | PAGE_DISTORTION_2 | Poly5 (Distortion 2)                     |
| $B100 | PAGE_DISTORTION_4 | Distortion 4                             |
| $B200 | PAGE_DISTORTION_A | Pure tones                               |
| $B300 | PAGE_DISTORTION_C | Buzzy bass                               |
| $B400 | PAGE_DISTORTION_E | Gritty (the Distortion C variant)        |
| $B500 | PAGE_EXTRA_0      | Sawtooth ch1, ch3; pure and buzzy 15 kHz |

The driver selects the page through a dispatch table that maps the
instrument's RMT distortion to the page and the AUDC distortion bits
written to POKEY: distortions 0, 6, 8 and A all play from the pure
page (with AUDC $00, $A0, $80 and $A0 respectively), 2 from the
Poly5 page, 4 from the Distortion 4 page, C from the buzzy page and
E from the gritty page with AUDC $C0. The 15 kHz and sawtooth tables
in PAGE_EXTRA_0 are selected separately, by the channel's clock and
the sawtooth feature.

That dispatch table is part of the driver's interface to the tracker,
not just to the player: it sits at a known address, so the tracker
reads the page and the AUDC bits straight out of the loaded driver
wherever it needs to know what a note would sound like - the
assembler export's frequency mode and MIDI note input both do. The
mapping is therefore defined once, by the driver, and a future
reshuffle of the pages reaches the tracker without anything being
edited there. Both of those places used to keep their own copy of the
addresses, and both had fallen a page behind: one still assumed the
single-page layout of RMT 1.28, the other the five-page layout from
before the Distortion 4 page was added. Every other driver version
has a dispatch table of its own, in a different shape and at a
different address, and the tracker reads that one the same way - see
the last section of this page.

The content of the block is assembled into the driver binary
byte-for-byte from the data statements in `asm/Patch-16/rmtdata.a65`,
and those statements hold exactly what RMT's own tuning system
generates for its default PAL tuning settings mentioned above.

Tests on both the C++ and the
Java side assert that equality, so the two cannot drift apart
unnoticed. The two "Croissant Sawtooth" tables are the exception:
they are hand-crafted, no formula produces them, and RMT carries the
same bytes as constants so that it writes the page complete. The
Patch 16 build shipped with RMT 1.34 still used a five-page layout
without the Distortion 4 page, so its pages from the pure page
upward sit $100 lower - relevant when comparing driver builds
extracted from older executables.

The baked-in copy exists so the driver is self-contained wherever no
tracker is present to compute anything: in the Simple RMT Player,
and in any project that assembles this driver with its own module,
these tables are what plays. The tracker's own XEX and SAP exports
carry no frequency tables at all - they are compressed register
streams next to VUPlayer, recorded from RMT's emulated POKEY. Inside
the tracker, the tuning system regenerates the whole page from the
current tuning settings after every driver load, so those settings
always win. RMT 1.34 behaved this way, and RMT 1.36 does again -
intermediate development builds reloaded the driver after generating
the tables, which silently put the baked-in snapshot back in
control, audible as AUDF deviations of one step in the Poly5
1.79 MHz and the 15 kHz tables.

Before RMT 1.36 these numbers were a frozen snapshot of an older
version of the tuning calculations, and the Distortion 4 page was an
all-zero placeholder, so a tune using that distortion fell silent
once assembled into a standalone build. Both are fixed by matching
the generated defaults. A composer who changes any tuning setting
still has to carry their own tables into such a build, since the
shipped driver can only hold one configuration.

## RMT 1.28 Patch Prince of Persia by VinsCool

This driver version is an older patch based on the original RMT 1.28 Patch 8 by Analmux.
This version was created exclusively for the Prince of Persia soundtrack.
It was thus never really used outside of a very few test tunes that used some of the sounds.

## The Frequency Tables of the Other Drivers

Every driver but Patch 16 keeps its frequency tables inside the
player instead, in the page below it, and has only four of them
rather than Patch 16's eighteen:

| Address | Table        | Used for                     |
| ------- | ------------ | ---------------------------- |
| $31C0   | frqtabbasslo | 16-bit bass, the low bytes   |
| $3200   | frqtabbass1  | Distortions 6 and C          |
| $3240   | frqtabbass2  | Distortion E                 |
| $3280   | frqtabpure   | Distortions 0, 2, 4, 8 and A |
| $32C0   | frqtabbasshi | 16-bit bass, the high bytes  |

The dispatch table sits at $3182 and names, per distortion, an offset
into the $3200 page and the AUDC bits - the same two answers Patch 16's
gives, in a different shape. There are no clock variants at all: a
channel running at 1.79 MHz or 15 kHz reads the very same table, which
is why those two clocks play out of tune on these drivers rather than
playing nothing.

These tables are assembled into each binary, and the tuning system
cannot reach them: it only ever writes the $B000 page. They are
therefore exactly what those drivers play, whatever the tuning dialog
says - a reason to prefer Patch 16 for anything that is to be tuned.
The module properties dialog says so as well: with any other driver
version selected, its "Tuning..." button is disabled.

Only the unpatched driver's source is in this repository, and the five
patched binaries each changed the table's contents: Patch 6 plays
distortion 6 with AUDC $A0 where the unpatched driver plays it with
$C0, and Patch 8 and Patch Prince of Persia do not even keep their
tables on $40 boundaries any more. So the tracker reads this layout
from the loaded driver too, rather than assuming the original numbers,
and the assembler export's frequency mode and MIDI note input work with
every driver version. The one thing it will not read is the 16-bit
bass, whose two tables lie a page apart and cannot be identified with
confidence in binaries that have no source to check against.
