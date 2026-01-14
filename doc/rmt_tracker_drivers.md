# Tracker Drivers

A tracker driver is part of the tracker that generates the actual sound from the tracker data and user input. In the case of RMT, the tracker diver is a piece of MOS 6502 code that runs on an emulated Atari 8-bit computer.

# Tracker Driver Versions

A tracker driver version is a variant of the tracker driver that interprets the data and user input differently. For example, different tracker driver versions have a distinct feature set or tuning. A correct replay of the music requires using the same tracker driver version that was used to create it.

The original tracker driver version is the one by Raster in RMT 1.28. Many people modified the driver code and data to yield new effects. All known older versions are preserved for compatibility.

Tracker driver versions are identified in different ways.
For inclusion into RMT, the tracker driver versions are numbered starting from 1. 
Additionally, they have a readable name for users and a constant name when used in the source code.

| Code       | Name                      | Constant Name          | Author   |
|------------|---------------------------|------------------------|----------|
| 1          | RMT 1.28 Unpatched        | UNPATCHED              | Raster   | 
| 2          | RMT 1.28 With Tuning      | UNPATCHED_WITH_TUNING  | VinsCool | 
| 3          | RMT 1.25 Patch 3          | PATCH3                 | Analmux  | 
| 4          | RMT 1.27 Patch 6          | PATCH6                 | Analmux  | 
| 5          | RMT 1.28 Patch 8          | PATCH8                 | Analmux  |
| 6          | RMT 1.28 Patch 16         | PATCH16                | VinsCool |
| 7          | RMT 1.28 Prince of Persia | PATCH_PRINCE_OF_PERSIA | VinsCool |


## RMT 1.28 Unpatched by Raster

The original RMT driver version that set the standard for many years with Atari 8-bit music. This version was mainly with RMT 1.28, as well as the unofficial RMT 1.30 by Rudla.

## RMT 1.28 With Tuning by VinsCool

This driver version was never completed and is no longer included in the program. It is only mentioned here to explain the gap between versions 1 and 3.

## RMT 1.25 Patch 3 by Analmux

Adapted from Analmux's original post on [AtariAge](https://forums.atariage.com/topic/228757-instrumentarium-final-release-links-summary/): 
> The music track "Instrumentarium" is composed, tracked, and RMT-patched by me: ANALogue MUltipleXer. I used a patch to change some RMT possibilities: f.e, I added sawtooth wave (ringmod), triangle wave (linear subharmonic), clarinet (16-bit), distortion guitar (16-bit), and sync modes (2-tone filter & 16-bit). At the end of 2009, this tune was named "Instrumentarium Remix 1"; it's the same tune. I was using exotic undocumented POKEY features. It takes almost no CPU time.

## RMT 1.27 Patch 6 by Analmux

Adapted from Analmux's original post on [AtariAge](https://forums.atariage.com/topic/175878-rmt-patch-6/):
> Tables freqtabbasslo and freqtabbasshi are patched (all I did was multiply each 16-bit value by 15). The '16-bit distortion 6' instrument, which was really a generator C instrument, is now a generator A instrument. I removed the blocking of the 1st channel, to enable 16-bit filter-like effects."

## RMT 1.28 Patch 8 by Analmux

Adapted from Analmux's original post on [AtariAge](https://forums.atariage.com/topic/234769-rmt-patch-8/): 

> | RMT Distortion | POKEY  Distortion | Bits | Clock    | Number of Bytes |Instrument                                                       |
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

## RMT 1.28 Patch Prince of Persia by VinsCool

This driver version is an older patch based on the original RMT 1.28 Patch 8 by Analmux.
This version was created exclusively for the Prince of Persia soundtrack.
It was thus never really used outside of a very few test tunes that used some of the sounds.
