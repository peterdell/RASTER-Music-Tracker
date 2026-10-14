#include "VUPlayer.h"
#include "AtariIO.h"
#include "Song.h"

void VUPlayer::PatchMemoryForSAP_B(byte* memory, const CSong& song, int subsongs) {

    // Patch: change a JMP [label] to a RTS with 2 NOPs
    byte saprtsnop[3] = {0x60, 0xEA, 0xEA};
    for (int i = 0; i < 3; i++) {
        memory[VUPlayer::RTS_NOP + i] = saprtsnop[i];
    }

    // Patch: change a $00 to $FF to force the LOOP flag to be infinite
    memory[VUPlayer::LOOP_FLAG] = 0xFF;

    // SAP initialisation patch: the SAP player calls INIT with the subtune number in A
    byte sapbytes[14] =
        {
            0x8D, LZSSP_SONGIDX & 0xff, LZSSP_SONGIDX >> 8, // STA SongIdx
            0xA2, 0x00, // LDX #0
            0x8E, LZSSP_IS_FADEING_OUT & 0xff, LZSSP_IS_FADEING_OUT >> 8, // STX is_fadeing_out
            0x8E, LZSSP_STOP_ON_FADE_END & 0xff, LZSSP_STOP_ON_FADE_END >> 8, // STX stop_on_fade_end
            0x4C, LZSSP_SETNEWSONGPTRSFULL & 0xff, LZSSP_SETNEWSONGPTRSFULL >> 8 // JMP SetNewSongPtrsLoopsOnly
        };
    memcpy(memory + VUPlayer::INIT_SAP, sapbytes, 14);

    memory[VUPlayer::SONG_SPEED] = song.GetInstrumentSpeed(); // Song speed
    memory[VUPlayer::STEREO_FLAG] = song.IsStereo() ? 0xFF : 0x00; // Is the song stereo?
    memory[VUPlayer::SONGTOTAL] = subsongs; // Total number of subtunes
}
