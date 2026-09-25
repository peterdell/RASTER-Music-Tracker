package com.wudsn.tools.rmt.model;

/**
 * Ported from the C++ enum class SongIOType (SongTypes.h). A plain Java
 * enum - C++'s explicit backing values (with gaps, e.g. RMT=1, RMW=2, ...,
 * WAV=20, TMC=101) aren't preserved since nothing reads this enum's
 * underlying numeric value anywhere in the ported source, only compares it
 * for equality.
 */
public enum SongIOType {
	NONE, RMT, RMW, RMTSTRIPPED, SAP, XEX, TXT, ASM, ASM_RMTPLAYER, SAPR, LZSS, LZSS_SAP, LZSS_XEX, WAV, TMC
}
