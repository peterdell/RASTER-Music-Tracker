
RMT Module File Format
======================

As of 2026-01-06, there is only one version of the RMT module file format.
It is the RMT module file format [version "1"](#rmt1) and uses the default file extension ".rmt".
This document contains the official description of that file format.

This document also contains the draft of what I (JAC!) intend to introduce
as a new, extended RMT module file format [version "2"](#rmt2). It is the result of many months of considering the different goals to be achieved to overcome the problems of the original format in the context of RMT, ASAP, ASMA and the time I had to invest to create working SillyPacks with music.

The related description for the related RMT tracker can be found [here](./rmt_tracker.md).

I'm happy to receive feedback on my proposals, ideally via a [personal message on Atariage](https://forums.atariage.com/messenger/compose/?to=17404).

The latest version of this document and the related documents is located at [Github](https://github.com/peterdell/RASTER-Music-Tracker/blob/dev/doc/rmt_format.md). So before sending feedback, please check the latest version. The [history](https://github.com/peterdell/RASTER-Music-Tracker/commits/dev/doc)  also displays the past changes.


RMT Module Format Version "1"<a id='rmt1'></a>
-----------------------------

This is the module file format used by RMT trackers with version 1.xy.

### Segment Binary Load Header (Mandatory)

RMT module files are regular multi-segment binary load files. They start with a 6-byte binary load header. It includes the start address and end address in a virtual 64k address space. The absolute addresses in the segments and structures can be relocated based on their distance from the start address.
>
	Offset	Type	Description
	------	----	-----------
	$00	WORD	Magic byte sequence $FFFF
	$02	WORD	Start address, default is $4000
	$04	WORD 	End address

### Module Header Struct

>
	Offset	Type	Description
	------	----	-----------
	$00	WORD	Header string "RMT4" for mono or "RMT8" for stereo modules
	$04	BYTE	Track length ($00 means 256)
	$05	BYTE	Song speed
	$06	BYTE	Replay frequency
	$07	BYTE	Format version number ($01 for player routine 1.x compatible format $02 for 2.x)
	$08	WORD	Address of the instruments table
	$0a	WORD 	Address of the low-byte table of the track addresses
	$0c	WORD	Address of the high-byte table of the track addresses
	$0e	WORD	Address of the track sequence table


### Instrument Struct
>
	Offset	Type	Description
	------	----	-----------
	$00	BYTE	tlen (pointer to the end of the table of notes)
	$01	BYTE 	tgo (pointer to the loop of the table of notes)
	$02	BYTE	elen (pointer to the end of the envelope)
	$03	BYTE	ego (pointer to the loop of the envelope)
	$04	BYTE	tspd (bit 0-5), tmode (bit 6), ttype (bit 7)
	$05	BYTE	AUDCTL
	$06	BYTE	Vslide
	$07	BYTE	Vmin(bit 4-7)
	$08	BYTE	Delay ($00 for no vibrato & no fshift)
	09	BYTE	Vibrato
	0a	BYTE	fshift
	0b	BYTE	Unused
	0c	    	Table of notes
	??	    	Envelope


### Table of Notes Struct

>
	Type	Description
	----	-----------
	BYTE	Note or frequency (according to the ttype)


###  Envelope Struct

>
	Type	Description
	----	-----------
	BYTE	Volume (bits 0-3 for the left channel, bits 4-7 for the right channel (in RMT4 it's the same as bits 0-3))
	BYTE	Portamento (bit 0), distortion (bit 1-3), command (bit 4-6), filter (bit 7)
	BYTE	XY


### Track Struct

>
	Type	Description
	----	-----------
	BYTE
		bit 0-5	note
		bit 6-7	volume (HI) or pause (1-3 beats) or special

	if note is $00-$3c:
	BYTE
		bit 0-1	volume (LO)
		bit 2-7 instrument number

	if note is $3d:
	BYTE
		bit 0-1	volume (LO)	volume only

	if note is $3e:
		bit 6-7 pause
		if pause is $01-$03:	pause 1-3 beats
		if pause is $00:	next byte pause 1-255 beats

	if note is $3f:
		if bit 6-7 is zero:	next byte speed $01-$ff
		if bit 6 is zero, 7 is set up:	next byte is track jump pointer (go to $00-$ff from the begin of track data)
		if bit 6-7 is set up:	END of track

### Instruments Table

>
	Type	Description
	----	-----------
	WORD	ptr_instr0
	WORD	ptr_instr1
	WORD	ptr_instr3
	...

### Track Addresses Table

>
	Type	Description
	----	-----------
	BYTE	lowbyte_of_ptr_track0
	BYTE	lowbyte_of_ptr_track1
	BYTE	lowbyte_of_ptr_track2
	...

>

	BYTE	highbyte_of_ptr_track0
	BYTE	highbyte_of_ptr_track1
	BYTE	highbyte_of_ptr_track2
	...

### Track Sequence Table

>
	Type	Description
	----	-----------
	BYTE	tracknumL1,tracknumL2,tracknumL3,tracknumL4,[tracknumR1,..,tracknumR4]
	BYTE	tracknumL1,tracknumL2,tracknumL3,tracknumL4,[tracknumR1,..,tracknumR4]
	BYTE	tracknumL1,tracknumL2,tracknumL3,tracknumL4,[tracknumR1,..,tracknumR4]
	...

	if tracknum is FF, then an empty track is used

	if tracknumL1 is FE, then gotoline(BYTE)=tracknumL2, goto_pointer(WORD)=(tracknumL3,4)
	Note: gotoline(BYTE) is not used in the player (but the tracker uses it)


RMT Module Format Version "2" (DRAFT 2026-01-06)<a id='rmt2'></a>
================================================

This is the module file format planned to be used by RMT trackers with version 2.xy.

This new version has the following design goals:

- `G1 Compatibility`

  Meaning: The format shall be as compatible to the version "1" as possible.
  I don't want to reinvent the wheel, but fix problems.
  In particular the problem with RMT 1.34 is the LZSS export. It is nice, flexible and fast, but it has also severe restrictions that prevent its use in many cases:
    - No metadata about the song name and instruments (relevant for archiving, e.g. on ASMA)
	- No way to re-created and editable version of the module (relevant for updates, remixing, learning)
	- No size-optimized replay (relevant for certain demo categories)
	- Only one songs per module (often required in games)
	- No ability to play effect instruments individually while the main music is playing (often required in games)

- `G2 Distinguishability`
  
  Meaning: The format shall be distinguishable for players, even if the file extension is still ".rmt".
  This is the main problem with all RMT tracker versions after RMT 1.28. They all use the same file extension and file content, but render the sound differently because they use patched replay routines and possibly changed a tuning. Without this information, the RMT module file is incomplete and cannot be replayed correctly. And you don't even know you are playing it incorrectly.

- `G3 Completeness`

   Meaning: The format shall contain all missing information that is today provided by the composer, but is not stored in the module file itself. This shall include:
  
  - The author of the song in human readable format and optionally in machine readable format (e.g. ASMA "Composer/...") path.
  - Any additional information about the composition that is today contained in the "STIL.txt" database of ASMA. This includes information about the original composition, usages in software and rankings in competitions.
  - The description (today 5 lines of width 40 characters) to be displayed during replay. This information is today encoded in the ".xex" export. This includes the ability to toggle the display of the lines using the SHIFT key. By default, SHIFT activates the display of line 5 instead of line 4.

- `G4 Extensibility`
 Meaning: The format shall be extendable with new features and data in a compatible way that will not break existing players and tools. This includes that players and tools shall by default ignore sections that they do not understand.

- `G5 Replayability`
   Meaning: The format shall be usable on the original hardware (Atari 400/800/XL/XE) including generic RMT players. This means that the file must be easily readable for replay by machines with only 48k of RAM, no matter how much additional metadata is additionally included. The pattern to include the replay routine itself in binary is a proven way to achieve this. It is applied today in ".sap" (Atari 8-bit) and ".sid" (C64) files.
	

### Design Decisions

The files of format version "2" structure is as follows:

- - The file format will use the file extension '.rmt'.
  The original RMT module files format already contains an internal version indicator. Existing players, if implemented correctly, should correctly reject modules with versions other than `$01`. Hence there is no need to use different file extensions.

  Supported Goals: `G1`.
  
  One could rightfully argue that using a different file extension would be even beneficial for `G2` and would be more obvious for the end users. But the goal `G2` is defined with the player software in mind. End users would rather be confused and existing software that relies on the file extension would no longer work. Instead the software, e.g. the ASAP file explorer plugin, shall be enhanced to also display the module file version as a separate attribute. 

- The file is a binary load file with one of more segements.
 
  Supported Goals: `G1`, `G4`, `G5`

- The first segment is mandatory and will be identical to format version "1", except that the format version at offset `$07` in the module header struct, which will contain the value `$02` instead of the value `$01`. The default start address of the segment is `$4000`, but it can also be different.

  Supported Goals: `G1`, `G2`, `G5`

- All additional segments are optional. If they are not present the file shall be treated as a regular version "1" file for RMT 1.28.

  Supported Goals: `G1`, `G5`

- The second segment is a metadata segment and has the **fixed start** address `$2000`. The second segment must not overlap the first segment. It contains human readable metadata in text form, similar to [SAP format]((https://www.example.com)). 
  The following things are different compared to the SAP format:
	- The line separator character is EOL (`$9b`) instead CR/LF (`$0d/ $0a`) to allow direct output via the "E:" handler.
	- The text termination character is `$00` instead of the `$ff` from the executable header to simplify the code.
	- The starting line begins with segment type marker "RMX" instead of "SAP" and is followed by a one digit segment version character. There is currently only version `1`.
	- Additional tags that do not yet exist in the SAP format will be allowed, for example to include the information from the "STIL.txt".

	Supported Goals: `G3`, `G5`
	```
	RMX1
	AUTHOR "Jakub Husak"
	NAME "Inside"
	DATE "1990"
	SONGS 3
	TYPE B
	INIT 0F80
	PLAYER 247F
	TIME 06:37.62
	TIME 02:34.02 LOOP
	TIME 00:15.40 LOOP
	```


	>
		Offset	Type	Description
		------	----	-----------
		$00		WORD	Start address, must be $2000
		$02		WORD	End address, must end before the start address of the first segment 
		$04		BYTE	'R'
		$05		BYTE	'M'
		$06		BYTE	'T'
		$07		BYTE	'1' Segment version in ASCII digit format.
		$08		BYTE	$9b EOL
		$09		BYTE	Human readable text in ATASCII format, see above.
		...
		nnnn		BYTE	$9b EOL
		nnnn+1	BYTE	End marker fo the text information, must be $00
		nnnn+2	BYTE	Display routine that prints the text starting at address $2009 and waits for a keypress. The routine can assume an open "E:" and "K:" handler. The routine must use the CIOV vector and must end with an "RTS". The routine can choose to not display all tags. The routine must not have other side effects and must not set the "I" flag. 
	>


- The third segment is mandatory, if the second segment is present. It is a run address segment to start the display routine loaded via the second segment, when the file is loaded directly from a DOS or in an emulator.

	Supported Goals: `G3`, `G5`
	>
		Offset	Type	Description
		------	----	-----------
		$00		WORD	Start address, must be $02e0
		$02		WORD	End address, must $02e1
		$04		WORD	Address nnnn+2 the display routine from the second segment.
	>
- Additional segments are optional and can contain up to 16k of data per segment that cannot reasonably be represented as short text in the second segment. The segment layout follows the pattern used for the second segment. They use the fixed start address `$e000` and can have an end address up to `$ffff`. This address choice is in the OS ROM area of all original Atari-8 bit machines. Loading to that area does not have any side effects, except for the time it takes. Every segment starts with a three-letter ASCII uppercase letters type code followed by a version digit. To avoid confusion regarding the segment types, 'RMT' is reserved for the first segment. The following segment types are in draft:
	- `PLR` Relocatable 6502 object code file containing the player. The calling convention for the player will be the same as it was for the RMT 1.x players.
	- `TUN` Tuning information

	Supported Goals: `G3`, `G4`
	>
		Offset	Type	Description
		------	----	-----------
		$00		WORD	Start address, must be $e000
		$02		WORD	End address, must end before the start address of the first segment 
		$04		BYTE	First letter of type segment type code, e.g. 'T'
		$05		BYTE	Second letter of type segment type code, e.g. 'U'
		$06		BYTE	Third letter of type segment type code, e.g. 'N'
		$07		BYTE	Segment version in ASCII digit format, default '1'
		$08		BYTE	First byte of the segment content.
	    
	>

