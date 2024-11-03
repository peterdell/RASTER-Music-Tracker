#pragma once

#include "lzssp.h"

#define VU_PLAYER_LOOP_FLAG		LZSSP_LOOP_COUNT		// VUPlayer's address for the Loop flag
#define VU_PLAYER_STEREO_FLAG	LZSSP_IS_STEREO_FLAG	// VUPlayer's address for the Stereo flag 
#define VU_PLAYER_SONG_SPEED	LZSSP_PLAYER_SONG_SPEED	// VUPlayer's address for setting the song speed
#define VU_PLAYER_DO_PLAY_ADDR	LZSSP_DO_PLAY			// VUPlayer's address for Play, for SAP exports bypassing the mainloop code
#define VU_PLAYER_RTS_NOP		LZSSP_VU_PLAYER_RTS_NOP	// VUPlayer's address for JMP loop being patched to RTS NOP NOP with the SAP format
#define VU_PLAYER_INIT_SAP		0X1E9B					// VUPlayer SAP initialisation hack

#define LZSS_POINTER			LZSSP_SONGINDEX			// All the LZSS subtunes index will occupy this memory page
#define VU_PLAYER_SEQUENCE		LZSSP_SONGSEQUENCE
#define VU_PLAYER_SECTION		LZSSP_SONGSECTION
#define VU_PLAYER_SONGDATA		LZSSP_LZ_DTA

#define VU_PLAYER_REGION		LZSSP_PLAYER_REGION_INIT// VUPlayer's address for the region initialisation
#define VU_PLAYER_RASTER_BAR	LZSSP_RASTERBAR_TOGGLER	// VUPlayer's address for the rasterbar display
#define VU_PLAYER_COLOUR		LZSSP_RASTERBAR_COLOUR	// VUPlayer's address for the rasterbar colour
#define VU_PLAYER_SONGTOTAL		LZSSP_SONGTOTAL			// VUPlayer's address for the total number of subtunes that could be played back

#define VU_PLAYER_SOUNGTIMER	LZSSP_SONGTIMERCOUNT
