# Plan 38: GetFrequency read the RMT 1.28 table layout

Status: DONE 2026-10-12 for GetFrequency itself. The same defect in
the MIDI input path is diagnosed below and NOT yet fixed - it needs
its own decision, see "The MIDI path".

## The defect

`CInstruments::GetFrequency()` and `Instruments.getFrequency()` turn a
note into the AUDF byte the player would write, for the `notes=freq`
option of the assembler export (`export asm ... notes=freq`, its only
caller - ASMFileExporterCore.cpp:70 and :204, AsmFileExporter.java:100
and :212). Both read

    RMT_FRQTABLES + 64 / 128 / 192 + note

choosing between the three by distortion. Those offsets are the RMT
1.28 layout, where the driver carried ONE page of four 64-byte tables
below the player (`$31c0` basslo, `$3200` bass1, `$3240` bass2,
`$3280` pure, `$32c0` basshi - see plan 37's "Separate findings" for
how that page worked). With `$31c0` as the base, +64/+128/+192 are
exactly bass1/bass2/pure, so the code was right when it was written.

Patch 16 replaced that single page with six 256-byte pages, one per
distortion, each holding 64 kHz at +$00, 1.79 MHz at +$40 and 16-bit
at +$80 (doc/rmt_tracker_drivers.md). `RMT_FRQTABLES` was repointed to
`$B000`; the offsets were not changed. All three therefore landed
inside the Poly5/bell page - +64 is its 1.79 MHz table, +128 and +192
are the two halves of its 16-bit word table - so every distortion
returned a bell value, and for most instruments a byte from a 16-bit
word table, which is not an AUDF at all. The code even carried a note
about it ("IMPORTANT NOTE: Tables are not set to a constant
location!") without the offsets being chased.

How wrong, at the PAL defaults, for the two commonest cases:

| Note | Pure tone, was $B0C0+n | Pure tone, now $B200+n | Buzzy, was $B040+n | Buzzy, now $B300+n |
| ---- | ---------------------- | ---------------------- | ------------------ | ------------------ |
|    0 | $82                    | $F1                    | $D6                | $7F                |
|   12 | $5A                    | $78                    | $69                | $BF                |
|   24 | $3E                    | $3B                    | $33                | $5F                |
|   36 | $2A                    | $1D                    | $17                | $2F                |

Not a near miss: an exported tune's notes were simply wrong, and
note 5 of a pure-tone instrument read `$00` out of the bell 16-bit
table.

## The fix

Both programs now mirror the player's own two-step choice
(rmtplayr.a65: the `TABLES_MSB` dispatch table, then
`store_tables_lsb`):

- The distortion selects the page - 2 Poly5, 4 Distortion 4, C buzzy,
  E gritty, and 0, 8 and A all pure, as the dispatch table says.
- The instrument's own 15 kHz AUDCTL bit selects the matching 15 kHz
  table in PAGE_EXTRA_0 instead, buzzy for distortion C and E and pure
  below them, which is what `check_15khz` does.
- Distortion 6 returns -1. It is BASS16: `do_bass16` forces 16-bit
  mode on whichever channel it plays, so the frequency is a 16-bit
  value and no single AUDF byte describes it. The callers already
  print `XXX` for -1, which is the honest answer where the old code
  returned a byte out of a word table.
- Everything else reads the page's 64 kHz table, which is also what
  the 1.28 code meant by "the table for this distortion".

1.79 MHz and the 16-bit joins are deliberately not honoured: those
AUDCTL bits are per channel (`179_CH1`/`179_CH3`, `JOIN_1_2`/
`JOIN_3_4`), and a track has no channel until the song assigns it to a
column, so there is no single answer. The 64 kHz table is the
default-clock answer and the comment in both files says so.

The C++ side now uses the named addresses from tracker_obx.h
(`RMTPLAYR_FRQTABPURE_64KHZ` and friends) rather than arithmetic on
`RMT_FRQTABLES`, which is what made the stale offsets invisible for so
long; the Java side gains the same names as constants in Atari.java.

## Verification

- The three characterization tests that pinned 64/128/192 in each
  program are replaced by three that pin every distortion to its own
  page, the 15 kHz bit, and distortion 6's -1. 680 Java and 432 C++
  tests green.
- CrossProgramExportTest caught the fix mid-flight, exactly as it
  should: with the Java port fixed and Rmt.exe still the old build, it
  reported one differing file, `d2.asm` from delta.rmtscript - the only
  `notes=freq` export in test-resources/scripts. After rebuilding
  Rmt.exe both programs agree again, which also proves the defect
  reached real output rather than only the unit tests.
- No committed reference export carries `notes=freq` output, so
  nothing else needed regenerating.

## The MIDI path (same defect, NOT fixed)

`Midi_Song.cpp`'s note-to-AUDF code (around line 495) and its Java
twin `MidiInput.java` select tables by distortion and clock too, and
their addresses are the RMT 1.34 FIVE-page layout - the one without
the Distortion 4 page, which was inserted on 2022-08-13:

| Table          | MIDI code uses | Patch 16 has |
| -------------- | -------------- | ------------ |
| Poly5 64 kHz   | $B000          | $B000        |
| Pure 64 kHz    | $B100          | $B200        |
| Buzzy 64 kHz   | $B200          | $B300        |
| Gritty 64 kHz  | $B300          | $B400        |
| Pure 15 kHz    | $B480          | $B580        |
| Buzzy 15 kHz   | $B4C0          | $B5C0        |

So every table except Poly5 sits one page too low: MIDI-played pure
tones read the Distortion 4 page, buzzy reads pure, gritty reads
buzzy, and the 15 kHz reads land inside the gritty page. Audible as
wrong pitch on MIDI note input, in both programs identically.

Not fixed here because it is a different subsystem with its own
question: that code switches on the AUDC distortion nibble (`0x20`,
`0x40`, `0x80` all fall through to the pure default, so distortion 4
never reaches the Distortion 4 page even after a page correction) and
it does have the channel context that GetFrequency lacks, so its
1.79 MHz and 16-bit handling can be made right rather than
side-stepped. Worth doing as its own change, with a note that MIDI
input is interactive and has no automated coverage today.

Taken up as plan 39, which also removes the private copy of the
mapping that made this possible three times over: the page comes from
the driver's own `TABLES_MSB` dispatch table and the sub-table rule
becomes one shared helper, which `GetFrequency` then moves onto.
