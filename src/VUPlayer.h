#pragma once

#include "StdAfx.h"

#include "Memory.h"
#include "lzssp.h"
#include "Song.h"

class VUPlayer {
public:

    static constexpr MemoryAddress LOOP_FLAG = LZSSP_LOOP_COUNT; // VUPlayer's address for the Loop flag
    static constexpr MemoryAddress STEREO_FLAG = LZSSP_IS_STEREO_FLAG; // VUPlayer's address for the Stereo flag 
    static constexpr MemoryAddress SONG_SPEED = LZSSP_PLAYER_SONG_SPEED; // VUPlayer's address for setting the song speed
    static constexpr MemoryAddress DO_PLAY_ADDR = LZSSP_DO_PLAY; // VUPlayer's address for Play, for SAP exports bypassing the mainloop code
    static constexpr MemoryAddress RTS_NOP = LZSSP_VU_PLAYER_RTS_NOP; // VUPlayer's address for JMP loop being patched to RTS NOP NOP with the SAP format
    static constexpr MemoryAddress INIT_SAP = 0X1E9B; // VUPlayer SAP initialisation hack

    static constexpr MemoryAddress LZSS_POINTER = LZSSP_SONGINDEX; // All the LZSS subtunes index will occupy this memory page
    static constexpr MemoryAddress SEQUENCE = LZSSP_SONGSEQUENCE;
    static constexpr MemoryAddress SECTION = LZSSP_SONGSECTION;
    static constexpr MemoryAddress SONGDATA = LZSSP_LZ_DTA;

    static constexpr MemoryAddress REGION = LZSSP_PLAYER_REGION_INIT; // VUPlayer's address for the region initialisation
    static constexpr MemoryAddress RASTER_BAR = LZSSP_RASTERBAR_TOGGLER; // VUPlayer's address for the rasterbar display
    static constexpr MemoryAddress COLOR = LZSSP_RASTERBAR_COLOUR; // VUPlayer's address for the rasterbar color, TODO: Rename to COLOR
    static constexpr MemoryAddress SONGTOTAL = LZSSP_SONGTOTAL; // VUPlayer's address for the total number of subtunes that could be played back

    static constexpr MemoryAddress SOUNGTIMER = LZSSP_SONGTIMERCOUNT;

    static void PatchMemoryForSAP_B(byte* memory, const CSong& song, byte* buf2, byte* buf3, int intro, int loop, int targetAddrOfModule, int lzss_offset, int lzss_loop);
};
