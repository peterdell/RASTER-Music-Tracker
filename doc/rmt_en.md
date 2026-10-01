![RASTER Music Tracker (RMT)](rmt.gif)

# RASTER Music Tracker (RMT) - Manual

For RMT 1.31.01 and higher by Vin Samuel and Peter Dell, 2021-2026.

For RMT 1.28, see the [RMT 1.28 Manual](rmt_en_128.html) by Radek Štěrba, RASTER/C.P.U. 2002-2009.

Most the shortcuts were changed compared to RMT 1.28 to be closer to the ones used in other music trackers, e.g., [Famitracker](http://famitracker.com/).

The UI of RMT is in one of these edit modes:

- TRACK EDIT - Displays the tracks for editing
- INSTRUMENT EDIT - Displays an instrument for editing
- INFO EDIT - Positions the cursor on the song info for editing.
- SONG EDIT - Positions the cursor on the track sequence in the song for editing.

## Menus, Toolbars and Keys

Every command of the menus and toolbars, with its key and what it does. This
table is generated from the program itself by the build (the `dump actions`
script command, see the [scripting documentation](rmt_scripting.md)), so it
is always the current state of the program.

<!-- include: rmt_action_infos.md -->

## Note Keys

The "tonekeys" of the hotkey tables below: two rows of the keyboard form a
piano, the lower row from C-1 and the upper row from C-2, with the row above
each as the black keys. The layout follows the "Keyboard layout" option
(QWERTY, AZERTY or the German QWERTZ; a first start picks it from the
keyboard language). The pictures and tables are generated from the program
(the `dump notekeys` script command): each keyboard row as its keys print
them, with the note every key plays underneath, then the same as a table by
note.

<!-- include: rmt_note_keys.md -->

## For All Edit Modes

### Hotkeys

| Key | Action |
|---|---|
| `ESC` | Stop playing song, mute all sounds. With the option "ESC resets Atari sound" (on by default) the sound routines are reinitialised as well. |
| `F2` | Switch to TRACK EDIT. |
| `F3` | Switch to INSTRUMENT EDIT. |
| `SHIFT+F4` | Go to INFO EDIT. |
| `F4` | Go to SONG EDIT. |
| `F5` | Play song from start. |
| `F6` | Play song and loop current pattern. |
| `SHIFT+F6` | Play and loop selection block. |
| `F7` | Play song from current cursor position. |
| `SHIFT+F7` | Play song from bookmark position (if set). |
| `F8` | Set bookmark to current cursor position. |
| `CONTROL+F8` | Clear bookmark. |
| `F9` | Mute/Unmute current channel. |
| `CONTROL+F9` | Solo current channel/Unmute all channels. |
| `SHIFT+F9` | Mute/Unmute all channels. |
| `F11` | RESPECT VOLUME mode on/off. |
| `F12` | Turn on/off autofollow mode. |
| `CONTROL+F12` | Toggle PAL/NTSC region. |
| `PAGE UP`, `PAGE DOWN` | Go to previous/next SONG line. In TRACK EDIT they move the cursor by the highlight step within the pattern track instead; use `CONTROL+PAGE UP`, `CONTROL+PAGE DOWN` for the SONG line there. |
| `SHIFT+PAGE UP`, `SHIFT+PAGE DOWN` | Go to next/previous subsong. |
| `numblock /` | Decrease octave for newly entered notes. |
| `numblock *` | Increase octave for newly entered notes. |
| `numblock +`, `numblock -` | Change pattern step size up/down. |
| `CONTROL+numblock +`, `CONTROL+numblock -`, `SHIFT+numblock +`, `SHIFT+numblock -` | Increase/decrease volume for newly entered notes. The mouse wheel over the VOLUME field of the song info does the same, the wheel over the OCTAVE field changes the octave. |
| `SHIFT+tonekeys` | Play note with current instrument and volume on currently active channel. |
| `SHIFT+CONTROL+tonekeys` | Play note with current instrument and volume on currently active channel in both stereo channels at once. |
| `CONTROL+SPACE` | Toggle between EDIT MODE and PROVE (JAM) MODE. |
| `SHIFT` | SHIFT is used during text input for either the SONG NAME or INSTRUMENT NAME, for uppercase and special characters. |
| `CAPSLOCK` | Toggle CAPSLOCK (indicated by "CAP" in the statusbar). CAPSLOCK also inverts the SHIFT key if it is held at the same time. |
| `CONTROL+1-8` | Turn on/off the channel 1 to 8. |
| `CONTROL+O` | Open RMT module. |
| `CONTROL+R` | Reload RMT module. |
| `CONTROL+S` | Save RMT module. |
| `CONTROL+SHIFT+S` | Save RMT module under a new name. |
| `ALT+ENTER` | Open the song properties. |
| `CONTROL+N` | Create new RMT module. |
| `CONTROL+P` | Print. |
| `CONTROL+Y` | Redo last change. |
| `CONTROL+Z` | Undo last change. |
| `SHIFT+LEFT`, `SHIFT+RIGHT` | Change active instrument. |
| `Media PLAY` | Play song from start/Stop. |
| `Media NEXT`, `Media PREVIOUS` | Play song from current pattern and skip next/previous SONG line. |
| `ALT+F4` | Exit RMT. |

## TRACK EDIT

### Fields

```
NNN TT vV FSS
```

| Field | Meaning |
|---|---|
| NNN | Notes, from C-1 to C-6. |
| TT | Instrument number, from $00 to $3F. If no note exists in highligthed row, the instrument column will behave like a note column instead. |
| vV | Volume, from $0 to $F Could also be used without a note or an instrument. "v" is simply added to clearly distinguish the volume value between the other columns. |
| FSS | Speed, from $01 to $FF. Fxx is intended to be a speed command, this is currently placeholder strings. |

### Hotkeys

| Key | Action |
|---|---|
| `UP`, `DOWN`, `LEFT`, `RIGHT`, `TAB`, `CTRL+TAB`, `SHIFT+TAB`, `PAGE UP`, `PAGE DOWN` | Move cursor. |
| `CONTROL+UP`, `CONTROL+DOWN` | Go to next/previous SONG line. |
| `CONTROL+PAGE UP`, `CONTROL+PAGE DOWN` | Go to next/previous SONG line (the same as `CONTROL+UP`, `CONTROL+DOWN`). |
| `CONTROL+LEFT`, `CONTROL+RIGHT` | Change track pattern number at current possition. |
| `HOME` | Move cursor to the start of current pattern track, or the start of a "wise loop". |
| `CONTROL+HOME` | Set/clear the start position of a "wise loop". |
| `END` | Move cursor to the end of current pattern track, or the end of a "wise loop". |
| `CONTROL+END` | Set/clear the end line of a pattern track. |
| `SHIFT+CONTROL+ENTER` | Set/clear the end line of a pattern track (the same as `CONTROL+END`). |
| `ENTER` | Play note at the cursor position. Every new ENTER hotkeys will also loop inside a selection block, if it exists. |
| `SHIFT+ENTER` | Play note at the cursor position and fetch its instrument and volume values, then become the active instrument. |
| `CONTROL+ENTER` | Play all notes at the currently edited line. (The option "Swap ENTER and CONTROL+ENTER" exchanges the two.) |
| `CONTROL+D` | Duplicate the current pattern track, and put it at the same place. (Note: If the pattern track is only used once in song, a messagebox asking for confirmation will appear.) |
| `CONTROL+I`, `CONTROL+U` | Insert/delete lines in the current pattern track. |
| `INSERT`, `DELETE` | Insert/delete lines in the current pattern track (the same as `CONTROL+I`, `CONTROL+U`). If a selection block exists, `DELETE` deletes its data instead. |
| `CONTROL+G` | Set "go to line" command in the song at current position. |
| `CONTROL+T` | Put new empty unused track to current song position and active channel. |
| `SPACE` | Delete note, instrument, volume and speed values in the track at the cursor position. |

### Note Column

| Key | Action |
|---|---|
| `tonekeys` | Insert note and play it. (Note: If RESPECT VOLUME is active, previous volume value won't be replaced.) |
| `numblock 1-6` | Change octave of the note at the cursor position and play it. |
| `BACKSPACE` | Delete note and instrument values (volume and speed values will not be cleared). |

### Instrument Number Column

| Key | Action |
|---|---|
| `tonekeys` | Insert note and play it. (Note: This will only work if the note column row is empty.) |
| `0-F` | Set up/change instrument number. |
| `BACKSPACE` | Delete note and instrument values (volume and speed values will not be cleared). |

### Volume Column

| Key | Action |
|---|---|
| `0-F` | Set up/change volume value. |
| `BACKSPACE` | Delete note, instrument and volume values (speed values will not be cleared). |

### Speed Column

| Key | Action |
|---|---|
| `0-F` | Set up/change speed value. |
| `BACKSPACE` | Delete speed values. |

### Block Operations

| Key | Action |
|---|---|
| `SHIFT+UP`, `SHIFT+DOWN`, `SHIFT+HOME`, `SHIFT+END` | Select block data. |
| `ESCAPE` | Deselect block data. |
| `DELETE` | Delete selection block data. |
| `HOME`, `END` | Go to the start/end of the selection block. |
| `CONTROL+A` | Select all valid data from the currently edited pattern track, and form a selection block. |
| `CONTROL+B` | Restore selection block data from backup. The backup is created as soon as the selection block is being formed. |
| `CONTROL+C` | Copy selection block to the clipboard. If no selection exists, the data at cursor position will be taken. |
| `CONTROL+E` | Exchange of selection block data and clipboard data. |
| `CONTROL+M` | Paste and merge data from the clipboard to the cursor position. |
| `CONTROL+V` | Paste data from the clipboard to the cursor position. |
| `CONTROL+X` | Cut selection block to the clipboard. If no selection exists, the data at cursor position will be taken. |

### Block Data Modifications

| Key | Action |
|---|---|
| `SHIFT+CONTROL+A` | Block modifications mode. All changes are provided either on each line of the block, or only on lines with instrument number equal to active instrument. |
| `CONTROL+F2`, `CONTROL+F1` | Transpose note up / down by semitones (5 octaves max). If no selection exists, the data at cursor position will be taken. |
| `CONTROL+F4`, `CONTROL+F3` | Transpose note up / down by octaves (5 octaves max). If no selection exists, the data at cursor position will be taken. |
| `SHIFT+CONTROL+LEFT`, `SHIFT+CONTROL+RIGHT` | Change instrument values, from $00 to $3F max. If no selection exists, the data at cursor position will be taken. |
| `SHIFT+CONTROL+UP`, `SHIFT+CONTROL+DOWN` | Volume up/down, from $0 to $F max. If no selection exists, the data at cursor position will be taken. |
| `CONTROL+F` | Display the Block effects/tools window. |

### Mouse control

### Tracks top area (Shown as TRACK L1 to TRACK R4 above pattern tracks)

| Key | Action |
|---|---|
| `LeftMouseButton` | Turn channel on/off. |
| `RightMouseButton` | Solo channel/turn back on all channels. |

## INSTRUMENT EDIT

### Fields

| Field | Meaning |
|---|---|
| NAME | Name of the instrument, 32 chars max. |
| ENVELOPE LENGTH | Length of the envelope, from $01 to $20 (i.e. 32 max.). |
| ENVELOPE GOTO | Jump to given envelope position when the envelope end is reached. |
| FADEOUT | Volume slide when the end of envelope is reached for the first time. $00 = no volume slide, $FF = maximal volume slide. |
| VOL MIN | Minimal volume value reached by FADEOUT parameter. |
| TABLE LENGTH | Length of the table, from $01 to $20 steps (i.e. 32 max.). |
| TABLE GOTO | Jump to given table position when the table end is reached. |
| TABLE SPEED | Speed for the each table step, from $01 to $40 vbi. |
| TABLE TYPE | Table type. 0 = notes, 1 = frequencies |
| TABLE MODE | Table mode. 0 (SET) = add note (or frequency) to base note, 1 (ADD) = add note (or frequency) to the last calculated note (or frequency). Note: If the resulting note is outside of the C-1 to C-6 range (hex values $00 to $3D), the output volume will be zero. Frequency additions are not limited however. |
| EFFECT DELAY | Delay before the start of VIBRATO and FREQSHIFT effects by $01 to $FF vbi, $00 = no effects. |
| EFFECT VIBRATO | Vibrato effect, 3 preset levels from $01 to $03, $00 = no vibrato. |
| EFFECT FREQSHIFT | Frequency shifting effect, from $00 to $FF for each vbi. |
| AUDCTL 15KHZ | Turn on/off AUDCTL bit 0, "Change main base clock from 64 KHz to 15 KHz". |
| AUDCTL HPF 2+4 | Turn on/off AUDCTL bit 1, "High pass filter into channel 2, clocked by channel 4". |
| AUDCTL HPF 1+3 | Turn on/off AUDCTL bit 2, "High pass filter into channel 1, clocked by channel 3". |
| AUDCTL JOIN 3+4 | Turn on/off AUDCTL bit 3, "Join channels 3 and 4 (16-bit frequency)". |
| AUDCTL JOIN 1+2 | Turn on/off AUDCTL bit 4, "Join channels 1 and 2 (16-bit frequency)". |
| AUDCTL 1.79 CH3 | Turn on/off AUDCTL bit 5, "Clock channel 3 with 1.79 MHz". |
| AUDCTL 1.79 CH1 | Turn on/off AUDCTL bit 6, "Clock channel 1 with 1.79 MHz". |
| AUDCTL POLY9 | Turn on/off AUDCTL bit 7, "Change the 17-bit poly to 9-bit poly (Distortion 0 and 8 only)". |

### Table of Notes/Frequencies

| Field | Meaning |
|---|---|
| TABLE $00-$1F | From 1 to 32 steps per table. Values can be either notes (in semitones), or frequencies, based on the TABLE TYPE value. Values can range from positive ($00 to $7F) or negative ($FF to $80) numbers. |

### Envelope

| Field | Meaning |
|---|---|
| VOLUME R | Volume values from $0 to $F, used only for stereo songs in tracks R1 to R4. |
| VOLUME L | Volume values from $0 to $F used in tracks L1 to L4. |
| DISTORTION | POKEY AUDC Distortion values, from $0 to $E. Only even values can be used, otherwise Volume Only output would occur. |
| COMMAND | Envelope command, from $0 to $7. |
| X, Y | Both parameters make use of values from $0 to $F for effect commands. They can be used as a two separate parameters, or as one 8-bit hexadecimal value $XY. |
| AUTOFILTER | Automatic High Pass Filter envelope effect. $0 = not active, $1 = active. It will only work with channels 1 or 2. This effect automatically enables the AUDCTL High Pass Filter bits for each channel, where HPF 1+3 or HPF 2+4 may be output. It has higher priority over non filtered voices, and will hijack them while the required conditions are met. If the resulting volume is zero, filtering won't be enabled, and won't take priority over other channels. Channels 3 and 4 won't be muted, so the resulting sound could be modulated into a richer tone if desired. |
| PORTAMENTO | Portamento. $0 = not active, $1 = active. If active, the "Portamento volatile frequency" is used instead of the current frequency. Portamento is mostly useful for producing pitch bending effects. |

### Sound Type (DISTORTION Parameter)

| Value | Meaning |
|---|---|
| 0 | Distortion 0, white noise. (AUDC $0v, Poly5+17/9) |
| 2 | Distortion 2, square-ish tones. (AUDC $2v, Poly5) |
| 4 | Distortion 4, no note table yet, Pure Table by default. (AUDC $4v, Poly4+5) |
| 6 | Distortion C, fallback setting, buzzy bass tones. (AUDC $Cv, Poly4) |
| 8 | Distortion 8, white noise. (AUDC $8v, Poly17/9) |
| A | Distortion A, pure tones. (AUDC $Av) |
| C | Distortion C, buzzy bass tones. (AUDC $Cv, Poly4) |
| E | Distortion C, gritty bass tones. (AUDC $Cv, Poly4) |

### Envelope command (COMMAND parameter)

| Value | Meaning |
|---|---|
| 0 | Play the base note shifted by $XY semitones. If the resulting note is outside the C-1 to C-6 range (hex values $00 to $3D), the volume output will be zero. |
| 1 | Play the frequency $XY directly. |
| 2 | Play the base note shifted by frequency $XY. |
| 3 | Add $XY semitones to base note. Play base note (new value). If the resulting note is outside the C-1 to C-6 range (hex values $00 to $3D), the volume output will be zero. |
| 4 | Add frequency $XY to FSHIFT register. Play base note. |
| 5 | Set up portamento speed $X, step $Y. Each $X vbi will be "volatile portamento frequency" shifted up or down by the $Y value. If $XY=$00, the current frequency will be used directly as volatile portamento frequency. |
| 6 | Add $XY value to FILTER_SHFRQ. Whenever a new note in track is playing, FILTER_SHFRQ is initialized to $01, so by default the Automatic Filter channels frequencies are offset by 1 unit in order to produce SID-like sound. |
| 7 | Set the instrument AUDCTL directly using $XY values. Play BASE_NOTE. Exceptions: $FF = VOLUME ONLY mode. $FE/$FD = enable/disable Two-Tone Filter (only for channel 1, modulated by channel 2). |

### Hotkeys

| Key | Action |
|---|---|
| `LEFT`, `RIGHT`, `UP`, `DOWN` | Move cursor. |
| `TAB` | Move cursor to the instrument parameters, the envelope parameters or the table parameters in succession |
| `SHIFT+TAB` | Move cursor to the instrument name line. |
| `0-F`, `CONTROL+LEFT`, `CONTROL+UP`, `CONTROL+RIGHT`, `CONTROL+DOWN`, `BACKSPACE`, `SPACE`, `DELETE` | Change parameter values (in valid ranges only). |
| `HOME` | In the parameters: move cursor to the first parameter (ENVELOPE LENGTH). |
| `SHIFT+CONTROL+UP`, `SHIFT+CONTROL+DOWN` | Change values for all steps of instrument envelope/table at cursor position. |
| `SHIFT+CONTROL+numblock +`, `SHIFT+CONTROL+numblock -` | Change the L+R volume envelopes up/down. If the cursor is on the "VOLUME L" or "VOLUME R" line, only the volume envelope for either line will be changed. |

### Table of notes/frequencies

| Key | Action |
|---|---|
| `HOME` | Move cursor to the start of the table/to the start of "table loop". |
| `CONTROL+HOME` | Set up the start of "table loop". |
| `END` | Move cursor to the end of the table. |
| `CONTROL+END` | Set up the end of the table. |
| `INSERT` | Insert empty step into the table at cursor position and shift to the right. |
| `SHIFT+INSERT` | Duplicate step at cursor position and shift to the right. |
| `DELETE` | Delete step at cursor position and shift to the left. |
| `SPACE` | Clear the table step at cursor position and move cursor to the right. |

### Envelope Parameters

| Key | Action |
|---|---|
| `HOME` | Move cursor to the start of the envelope/to the start of "envelope loop". |
| `CONTROL+HOME` | Set up the start of "envelope loop". |
| `END` | Move cursor to the end of the envelope. |
| `CONTROL+END` | Set up the end of the envelope/set up a maximal length of the envelope. |
| `INSERT` | Insert empty step into the envelope at cursor position and shift to the right. |
| `SHIFT+INSERT` | Duplicate step at cursor position and shift to the right. |
| `DELETE` | Delete step at cursor position and shift to the left. |
| `SPACE` | Clear the envelope step at cursor position and move cursor to the right. |

### Mouse control

### Volume Envelope Area

| Key | Action |
|---|---|
| `LeftMouseButton` | Draw the volume envelope. |
| `RightMouseButton` | Set the volume to zero in the envelope. |

## INFO EDIT

| Field | Meaning |
|---|---|
| NAME | Name of the song and author, 64 chars max. |

```
MUSIC SPEED: AA/MM/S
```

| Field | Meaning |
|---|---|
| AA | Current song speed, from $01 to $FF. |
| MM | Main speed, from $01 to $FF. By default the main speed is used when a song plays from the beginning. |
| S | Engine speed, from $1 to $8. (X player calls per frame.) Technically, speed above 4 is unsupported in the exported formats. |

### Hotkeys

| Key | Action |
|---|---|
| `LEFT`, `RIGHT`, `TAB` | Move cursor. |
| `SHIFT+TAB` | Move cursor song name line. |
| `0-F`, `CONTROL+LEFT`, `CONTROL+UP`, `CONTROL+RIGHT`, `CONTROL+DOWN` | Change parameter values (in valid ranges only). |
| `ENTER` | Return from INFO EDIT to the currently active screen (Tracks or Instruments edit). |

### Mouse control

### MAXTRACKLENGTH, MONO/STEREO, PAL/NTSC

| Key | Action |
|---|---|
| `LeftMouseButton` | Toggle or prompt dialog boxes for each elements, then apply changes to the song. |

## SONG EDIT

| Key | Action |
|---|---|
| `LEFT`, `RIGHT`, `UP`, `DOWN`, `PAGE UP`, `PAGE DOWN`, `TAB`, `SHIFT+TAB` | Move cursor. |
| `HOME` | Move cursor to the start of the song. |
| `END` | Move cursor to last song line with valid data. |
| `SHIFT+PAGE UP` | Move cursor to the start of current subsong or start of previous subsong if the action is repeated. |
| `SHIFT+PAGE DOWN` | Move cursor to start of next subsong. |
| `0-F`, `CONTROL+LEFT`, `CONTROL+RIGHT`, `BACKSPACE` | Change the pattern track number or "go to line" value in the song. |
| `INSERT`, `CONTROL+I`, `DELETE`, `CONTROL+U` | Insert/delete lines in the song (with auto-change of all relevant "go to line" values). |
| `CONTROL+D` | Duplicate the current pattern track, and put it at the same place. (Note: If the pattern track is only used once in song, a messagebox asking for confirmation will appear.) |
| `CONTROL+G` | Set "go to line" command in the song at current position. |
| `CONTROL+T` | Put new empty unused track to current song position and active channel. |
| `CONTROL+K` | Insert copy or clone of song line(s). |
| `CONTROL+J` | Prepare song line with unused empty tracks. |
| `ENTER` | Return from SONG EDIT to the currently active screen (Tracks or Instruments edit). |

## PROVE (JAM) MODE

Navigation is identical in TRACKS and SONG areas, but editing is disabled, and replaced with tonekeys always playing notes.
INSTRUMENTS and INFO areas will still behave the same as they are in EDIT MODE, simply hold SHIFT to test notes, except when the text is being edited.

## MIDI Input

RMT records and plays notes from a MIDI keyboard. The MIDI IN device is
chosen in Tools > Options (MIDI group); the toolbar button "Toggle MIDI
on/off" switches the input on and off while a device is set. The Java port
uses the same settings and the same rules.

Options:

| Option | Meaning |
|---|---|
| MIDI IN Device | The input device, or None. |
| Touch response | The key velocity becomes the note volume: `Atari Volume Offset + velocity / 8`, limited to 1-15. Without it the current volume (as typed notes) is used. |
| Atari Volume Offset | Added to the velocity volume, 0-15. |
| Record note off | A released key (note off, or note on with velocity 0) deletes the note it played and writes volume 0. Without it releases are ignored. |

The MIDI channels:

| Channel | Function |
|---|---|
| 1 | Records notes at the cursor, like the note keys: MIDI note 36 (C-2 in MIDI terms) is RMT's lowest note, the note is written with the active instrument, the cursor moves down by the "skip lines" setting (not while following playback), the note is played. During playback with follow-play a note in the first half of a line is entered on the next line (quantized). Outside the TRACKS area, in a jam mode or with SHIFT or CONTROL held the note is only played. Recording needs the RMT window to have the focus (a jam mode plays without focus). |
| 2 - 9 | Live play on the Atari tracks L1-L4, R1-R4 (channels 6-9 repeat L1-L4 on a mono song), never recorded and independent of the focus: the velocity is the volume (velocity / 8), a program change sets the channel's instrument, controller 123 (all notes off) stops the channel's note, controller 121 (reset all controllers) resets the RMT routines. |
| 11 - 15 | A program change selects the active instrument. |
| 16 and 10 | An experimental controller mapping, kept from the original: on channel 16 controller 1 (modulation wheel) shifts the recorded notes by up to 8 semitones (`(value - 64) / 8`), controller 7 sets the volume, controllers 115/116/117 are loop (play track), stop and play song, 118 steps through the edit modes like the Edit/Jam toggle (edit, jam, on a stereo song stereo jam, then "EXPLORER MODE (MIDI CH15)", then edit again), 123 stops and resets; notes on channel 16 record like channel 1 (spread over the tracks by the number of held keys). In the explorer mode the controllers 71-78 write the POKEY frequency, volume and distortion of two channels directly, the drum pads on channel 10 (notes 60, 62, 66, 70, 74, 69, 75, 73) toggle the POKEY AUDCTL/SKCTL bits, and notes on channel 16 sound directly from the driver's note tables. |

A system reset message (FF) resets the RMT routines and forgets the notes
held on channels 2-16.

Scripts can send MIDI messages with the `midi` command (see the scripting
documentation).

## Pokey Explorer

A debugging mode for pitch calculations: Pokey > Activate Pokey Explorer
Mode (Ctrl+Shift+F5) stops the RMT routines and lets the keys write the
POKEY registers directly; the Edit/Jam mode toggle leaves the mode again.
With View > Pokey Chip Registers on, three rows below the register dumps
detail one channel: its AUDF and AUDC bytes, the first divisor of
`AUDF + MODOFFSET` from 3 up ("MODULO"), the pitch formula's coarse divisor
(28 at 64 kHz, 114 at 15 kHz, 1 at 1.79 MHz and for joined channels), the
free divisor, the modulo offset (1, 4 at 1.79 MHz, 7 for joined channels)
and the resulting pitch.

The keys (the physical positions of the QWERTY layout; on the AZERTY and
QWERTZ layouts the keys at the same positions, e.g. `Z` for `Y` on QWERTZ,
`A` for `Q` on AZERTY - only `+` and `-` are the keys so labelled):

| Key | Function |
|---|---|
| `ENTER` / `BACKSPACE` | Next / previous channel (0-3). |
| `+` / `-` | Divisor +0.1 / -0.1; with `SHIFT` +1.0 / -1.0 (1.0 to 10000.0). |
| `1` `3` `5` `7` | AUDF0 to AUDF3 + 1; with `SHIFT` + $10. |
| `Q` `E` `T` `U` | AUDF0 to AUDF3 - 1; with `SHIFT` - $10. |
| `2` `4` `6` `8` | AUDC0 to AUDC3 + 1; with `SHIFT` + $10. |
| `W` `R` `Y` `I` | AUDC0 to AUDC3 - 1; with `SHIFT` - $10. |
| `C` `G` `F` `K` `J` `D` `A` `P` | Toggle AUDCTL bit 0 to 7 (15 kHz clock, the two high pass filters, the two 16-bit joins, the two 1.79 MHz clocks, 9-bit poly). |
| `M` | Toggle the two-tone bits of SKCTL. |

The Pokey menu offers the same operations as menu items; they work in the
explorer mode only.

## Files and Folders

RMT needs no installation. Everything it needs is in one folder, which
can be moved anywhere, including onto a USB stick.

| Folder or file | What it is |
|---|---|
| `rmt.exe`, `rmt` or `rmt.app` | the program, with its own Java runtime, the Atari player routines under `resources/` and the documentation under `docs/` |
| `songs` | the example songs, and where File > Open and File > Save as start |
| `instruments` | the instruments, and where the instrument load and save start |
| `exports` | finished tunes exported by older RMT versions |
| `rmt.ini`, `tuning.ini` | the settings, written when they are changed |

The settings are kept next to the program, so they travel with the
folder. If the program is started without that folder - on macOS, when
`rmt.app` alone is dragged to Applications - it still runs, but the songs
and instruments are no longer there and the settings move to the
operating system's own place for them: `%APPDATA%\RMT` on Windows,
`~/Library/Application Support/RMT` on macOS,
`$XDG_CONFIG_HOME/rmt` or `~/.config/rmt` on Linux. Help > About always
shows the two folders actually in use.

The Windows program `Rmt.exe` uses the same layout, so the two can share
one folder.

To test a different build of an Atari player routine, put the file into
`resources/drivers` or `resources/players` inside the program folder,
under the same name, and restart RMT. **Keep a copy of the original
first**: these are the files RMT ships, so writing over one replaces it
and deleting it leaves none. Without the file for the selected driver
version RMT stays silent and exports no sound; it says so at start-up and
when the version is switched in the Options.

## Disclaimer

RMT IS A SOFTWARE WITHOUT WARRANTY OF ANY KIND. THE AUTHOR DOES NOT WARRANT, GUARANTEE, OR MAKE ANY REPRESENTATIONS REGARDING THE USE, OR THE RESULTS OF USE OF THE SOFTWARE, OR WRITTEN MATERIALS, IN TERMS OF CORRECTNESS, ACCURACY, RELIABILITY, CURRENTNESS, OR OTHERWISE. THE ENTIRE RISK AS TO THE RESULTS AND PERFORMANCE OF THE SOFTWARE IS ASSUMED BY YOU.
