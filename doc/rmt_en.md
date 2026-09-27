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

## For All Edit Modes

### Hotkeys

| Key | Action |
|---|---|
| `ESC` | Stop playing song, mute all sounds. |
| `SHIFT+ESC` | Stop playing song, mute all sounds and reinitialise the sound output. |
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
| `PAGE UP`, `PAGE DOWN` | Go to next/previous SONG line. |
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
| `CONTROL+L` | Load RMT module. |
| `CONTROL+R` | Reload RMT module. |
| `CONTROL+S` | Save RMT module. |
| `CONTROL+W` | Create new RMT module. |
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
| `CONTROL+PAGE UP`, `CONTROL+PAGE DOWN` |  |
| `CONTROL+LEFT`, `CONTROL+RIGHT` | Change track pattern number at current possition. |
| `HOME` | Move cursor to the start of current pattern track, or the start of a "wise loop". |
| `CONTROL+HOME` | Set/clear the start position of a "wise loop". |
| `END` | Move cursor to the end of current pattern track, or the end of a "wise loop". |
| `CONTROL+END` | Set/clear the end line of a pattern track. |
| `SHIFT+CONTROL+ENTER` |  |
| `ENTER` | Play note at the cursor position. Every new ENTER hotkeys will also loop inside a selection block, if it exists. |
| `SHIFT+ENTER` | Play note at the cursor position and fetch its instrument and volume values, then become the active instrument. |
| `CONTROL+ENTER` | Play all notes at the currently edited line. |
| `CONTROL+D` | Duplicate the current pattern track, and put it at the same place. (Note: If the pattern track is only used once in song, a messagebox asking for confirmation will appear.) |
| `CONTROL+I`, `CONTROL+U` | Insert/delete lines in the current pattern track. |
| `INSERT`, `DELETE` |  |
| `CONTROL+G` | Set "go to line" command in the song at current position. |
| `CONTROL+N` | Put new empty unused track to current song position and active channel. |
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
| `CONTROL+PAGE UP` | Move cursor to the start of current subsong or start of previous subsong if the action is repeated. |
| `CONTROL+PAGE DOWN` | Move cursor to start of next subsong. |
| `0-F`, `CONTROL+LEFT`, `CONTROL+RIGHT`, `BACKSPACE` | Change the pattern track number or "go to line" value in the song. |
| `INSERT`, `CONTROL+I`, `DELETE`, `CONTROL+U` | Insert/delete lines in the song (with auto-change of all relevant "go to line" values). |
| `CONTROL+D` | Duplicate the current pattern track, and put it at the same place. (Note: If the pattern track is only used once in song, a messagebox asking for confirmation will appear.) |
| `CONTROL+G` | Set "go to line" command in the song at current position. |
| `CONTROL+N` | Put new empty unused track to current song position and active channel. |
| `CONTROL+O` | Insert copy or clone of song line(s). |
| `CONTROL+P` | Prepare song line with unused empty tracks. |
| `ENTER` | Return from SONG EDIT to the currently active screen (Tracks or Instruments edit). |

## PROVE (JAM) MODE

Navigation is identical in TRACKS and SONG areas, but editing is disabled, and replaced with tonekeys always playing notes.
INSTRUMENTS and INFO areas will still behave the same as they are in EDIT MODE, simply hold SHIFT to test notes, except when the text is being edited.

## Disclaimer

RMT IS A SOFTWARE WITHOUT WARRANTY OF ANY KIND. THE AUTHOR DOES NOT WARRANT, GUARANTEE, OR MAKE ANY REPRESENTATIONS REGARDING THE USE, OR THE RESULTS OF USE OF THE SOFTWARE, OR WRITTEN MATERIALS, IN TERMS OF CORRECTNESS, ACCURACY, RELIABILITY, CURRENTNESS, OR OTHERWISE. THE ENTIRE RISK AS TO THE RESULTS AND PERFORMANCE OF THE SOFTWARE IS ASSUMED BY YOU.
