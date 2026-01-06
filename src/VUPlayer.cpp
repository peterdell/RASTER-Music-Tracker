#include "VUPlayer.h"
#include "AtariIO.h"
#include "Song.h"

void VUPlayer::PatchMemoryForSAP_B(byte* memory, const CSong& song, byte* buff2, byte* buff3, int intro, int loop, int targetAddrOfModule, int lzss_offset, int lzss_end) {


    // Patch: change a JMP [label] to a RTS with 2 NOPs
    byte saprtsnop[3] = { 0x60,0xEA,0xEA };
    for (int i = 0; i < 3; i++) { memory[VUPlayer::RTS_NOP + i] = saprtsnop[i]; }

    // Patch: change a $00 to $FF to force the LOOP flag to be infinite
    memory[VUPlayer::LOOP_FLAG] = 0xFF;

    // SAP initialisation patch, running from address 0x3080 in Atari executable 
    byte sapbytes[14] =
    {
        0x8D,LZSSP_SONGIDX & 0xff,LZSSP_SONGIDX >> 8,								// STA SongIdx
        0xA2,0x00,																	// LDX #0
        0x8E,LZSSP_IS_FADEING_OUT & 0xff, LZSSP_IS_FADEING_OUT >> 8,				// STX is_fadeing_out
        0x8E,LZSSP_STOP_ON_FADE_END & 0xff, LZSSP_STOP_ON_FADE_END >> 8,			// STX stop_on_fade_end
        0x4C,LZSSP_SETNEWSONGPTRSFULL & 0xff, LZSSP_SETNEWSONGPTRSFULL >> 8	// JMP SetNewSongPtrsLoopsOnly
    };
    memcpy(memory + VUPlayer::INIT_SAP, sapbytes, 14);

    memory[VUPlayer::SONG_SPEED] = song.GetInstrumentSpeed();			// Song speed
    memory[VUPlayer::STEREO_FLAG] = song.IsStereo() ? 0xFF : 0x00;		// Is the song stereo?


    // SongStart pointers
        // TODO: Why same address?
    memory[VUPlayer::LZSS_POINTER] = targetAddrOfModule >> 8;	// SongsSHIPtrs
    memory[VUPlayer::LZSS_POINTER] = lzss_offset >> 8;			// SongsIndexEnd
    memory[VUPlayer::LZSS_POINTER] = targetAddrOfModule & 0xFF;	// SongsSLOPtrs
    memory[VUPlayer::LZSS_POINTER] = lzss_offset & 0xFF;				// SongsDummyEnd

    // SongEnd pointers
    // TODO: Why same address?
    memory[VUPlayer::LZSS_POINTER] = lzss_offset >> 8;			// LoopsIndexStart
    memory[VUPlayer::LZSS_POINTER] = lzss_end >> 8;				// LoopsIndexEnd
    memory[VUPlayer::LZSS_POINTER] = lzss_offset & 0xFF;		// LoopsSLOPtrs
    memory[VUPlayer::LZSS_POINTER] = lzss_end & 0xFF;			// LoopsDummyEnd

    if (intro > 16)
    {
        memcpy(memory + targetAddrOfModule, buff2, intro);
        memcpy(memory + lzss_offset, buff3, loop);
    }
    else
    {
        //memcpy(mem + targetAddrOfModule, buff1, full);
        memcpy(memory + lzss_offset, buff3, loop);
    }
}