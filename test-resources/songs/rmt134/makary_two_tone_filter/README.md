File Name:      Makary_Two_Tone_Filter.rmt
Title:          Makary - Two-Tone Filter
Author:         Makary (AtariAge)
Source:         https://github.com/raster-atari-org/RASTER-Music-Tracker/issues/30
                (sync.zip, https://github.com/user-attachments/files/33723142/sync.zip),
                reported on AtariAge:
                https://forums.atariage.com/topic/328790-release-raster-music-tracker-v13400/page/21/#findComment-5906877
Speed:          Instrument speed 2, song speed 5, track length 16.
Channels:       Mono (RMT4), module at $4000, format version 1.

A minimal module exercising the two-tone filter (the $7FE/$7FD command
in the envelope editor, SKCTL=$8B). The issue: in older RMT versions
(e.g. daily 1.35) the effect is audible during tracker playback, in the
Java version it is not - while the exported .xex does play it. Vin's
hypothesis on the issue: not emulated by ASAP, or the SKCTL writes are
missing during playback.

Makary_Two_Tone_Filter.rmt is the issue's module (sync.rmt) EXTENDED by
a pause and renamed: the original song was one song line (0.8 s - 16
rows, song speed 5, instrument speed 2 = 100 player ticks/s), so short
that the xex's VU player fades in and out and loops it several times
within any comparison window. 13 song lines referencing the empty track
04 were inserted before the GO-to-0 line, and the song name was set to
"Makary - Two-Tone Filter" (2026-10-22, via Song.songInsertLine and a
save through ScriptRunner), giving 0.8 s of music + 10.4 s of silence
per 11.2 s pass. The untouched original is inside sync.zip at the issue
link above.

Makary_Two_Tone_Filter-Java-Export.xex is the Java version's own xex
export of that extended module (rmtscript: export xex, default options)
- the rendition where the two-tone effect is audible, i.e. the working
reference the live playback must match. The reporter's original export
of the unextended module ("java export.xex") is likewise in sync.zip.
