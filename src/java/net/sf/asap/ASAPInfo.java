// Generated automatically with "fut". Do not edit.
package net.sf.asap;
import java.nio.charset.StandardCharsets;

/**
 * Information about a music file.
 */
public class ASAPInfo
{
	public ASAPInfo()
	{
	}

	/**
	 * ASAP version - major part.
	 */
	public static final int VERSION_MAJOR = 8;

	/**
	 * ASAP version - minor part.
	 */
	public static final int VERSION_MINOR = 0;

	/**
	 * ASAP version - micro part.
	 */
	public static final int VERSION_MICRO = 0;

	/**
	 * ASAP version as a string.
	 */
	public static final String VERSION = "8.0.0";

	/**
	 * Years ASAP was created in.
	 */
	public static final String YEARS = "2005-2026";

	/**
	 * Short credits for ASAP.
	 */
	public static final String CREDITS = "Another Slight Atari Player (C) 2005-2026 Piotr Fusik\nCMC, MPT, TMC, TM2 players (C) 1994-2005 Marcin Lewandowski\nRMT player (C) 2002-2005 Radek Sterba\nDLT player (C) 2009 Marek Konopka\nCMS player (C) 1999 David Spilka\nFC player (C) 2011 Jerzy Kut\n";

	/**
	 * Short license notice.
	 * Display after the credits.
	 */
	public static final String COPYRIGHT = "This program is free software; you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation; either version 2 of the License, or (at your option) any later version.";

	/**
	 * Maximum length of a supported input file.
	 * You may assume that files longer than this are not supported by ASAP.
	 */
	public static final int MAX_MODULE_LENGTH = 65000;

	/**
	 * Maximum length of text metadata.
	 */
	public static final int MAX_TEXT_LENGTH = 127;

	/**
	 * Maximum number of songs in a file.
	 */
	public static final int MAX_SONGS = 32;
	private String filename;
	private String author;
	private String title;
	private String date;
	private int channels;
	private int songs;
	private int defaultSong;
	private final int[] durations = new int[32];
	private final boolean[] loops = new boolean[32];
	private boolean ntsc;
	ASAPModuleType type;
	ASAPModuleType originalType;
	private int fastplay;
	int music;
	private int init;
	int player;
	private int covoxAddr;
	int headerLen;
	int mptSongBugExtra;
	final byte[] songPos = new byte[32];

	private static boolean isValidChar(int c)
	{
		return c >= ' ' && c <= '|' && c != '`' && c != '{';
	}

	static int getWord(byte[] array, int i)
	{
		return (array[i] & 0xff) + ((array[i + 1] & 0xff) << 8);
	}

	private void parseModule(byte[] module, int moduleLen) throws ASAPFormatException
	{
		if ((module[0] != (byte) 255 || module[1] != (byte) 255) && (module[0] != 0 || module[1] != 0))
			throw new ASAPFormatException("Invalid two leading bytes of the module");
		this.music = getWord(module, 2);
		int musicLastByte = getWord(module, 4);
		if (this.music <= 55295 && musicLastByte >= 53248)
			throw new ASAPFormatException("Module address conflicts with hardware registers");
		int blockLen = musicLastByte + 1 - this.music;
		if (6 + blockLen != moduleLen) {
			if (this.type != ASAPModuleType.RMT || 11 + blockLen > moduleLen)
				throw new ASAPFormatException("Module length doesn't match headers");
			int infoAddr = getWord(module, 6 + blockLen);
			if (infoAddr != this.music + blockLen)
				throw new ASAPFormatException("Invalid address of RMT info");
			int infoLen = getWord(module, 8 + blockLen) + 1 - infoAddr;
			if (10 + blockLen + infoLen != moduleLen)
				throw new ASAPFormatException("Invalid RMT info block");
		}
	}

	private void addSong(int playerCalls)
	{
		long scanlines = playerCalls * this.fastplay;
		this.durations[this.songs++] = (int) (scanlines * 38000 / 591149);
	}

	private static final int SEEN_THIS_CALL = 1;

	private static final int SEEN_BEFORE = 2;

	private static final int SEEN_REPEAT = 3;

	private void parseCmcSong(byte[] module, int pos)
	{
		int tempo = module[25] & 0xff;
		int playerCalls = 0;
		int repStartPos = 0;
		int repEndPos = 0;
		int repTimes = 0;
		final byte[] seen = new byte[85];
		while (pos >= 0 && pos < 85) {
			if (pos == repEndPos && repTimes > 0) {
				for (int i = 0; i < 85; i++)
					if (seen[i] == 1 || seen[i] == 3)
						seen[i] = 0;
				repTimes--;
				pos = repStartPos;
			}
			if (seen[pos] != 0) {
				if (seen[pos] != 1)
					this.loops[this.songs] = true;
				break;
			}
			seen[pos] = 1;
			int p1 = module[518 + pos] & 0xff;
			int p2 = module[603 + pos] & 0xff;
			int p3 = module[688 + pos] & 0xff;
			if (p1 == 254 || p2 == 254 || p3 == 254) {
				pos++;
				continue;
			}
			p1 |= this.type == ASAPModuleType.CMS ? 7 : 15;
			switch (p1) {
			case 135:
			case 167:
				pos++;
				break;
			case 143:
				pos = -1;
				break;
			case 151:
				if (p2 < 128) {
					playerCalls += p2;
					if (p3 < 128)
						playerCalls += p3 * 50;
				}
				pos++;
				break;
			case 159:
				pos = p2;
				break;
			case 175:
				pos -= p2;
				break;
			case 191:
				pos += p2;
				break;
			case 207:
				if (p2 < 128) {
					tempo = p2;
					pos++;
				}
				else
					pos = -1;
				break;
			case 223:
				pos++;
				repStartPos = pos;
				repEndPos = pos + p2;
				repTimes = p3 - 1;
				break;
			case 239:
				this.loops[this.songs] = true;
				pos = -1;
				break;
			default:
				p2 = repTimes > 0 ? 3 : 2;
				for (p1 = 0; p1 < 85; p1++)
					if (seen[p1] == 1)
						seen[p1] = (byte) p2;
				playerCalls += tempo * (this.type == ASAPModuleType.CM3 ? 48 : 64);
				pos++;
				break;
			}
		}
		addSong(playerCalls);
	}

	private static final int CMR_BASS_TABLE_OFFSET = 1807;

	private void parseCmc(byte[] module, int moduleLen, ASAPModuleType type) throws ASAPFormatException
	{
		if (moduleLen < 774)
			throw new ASAPFormatException("Module too short");
		this.originalType = this.type = type;
		parseModule(module, moduleLen);
		int lastPos = 84;
		while (--lastPos >= 0) {
			if ((module[518 + lastPos] & 0xff) < 176 || (module[603 + lastPos] & 0xff) < 64 || (module[688 + lastPos] & 0xff) < 64)
				break;
			if (this.channels == 2) {
				if ((module[774 + lastPos] & 0xff) < 176 || (module[859 + lastPos] & 0xff) < 64 || (module[944 + lastPos] & 0xff) < 64)
					break;
			}
		}
		this.songs = 0;
		parseCmcSong(module, 0);
		for (int pos = 0; pos < lastPos && this.songs < 32; pos++)
			if (module[518 + pos] == (byte) 143 || module[518 + pos] == (byte) 239)
				parseCmcSong(module, pos + 1);
	}

	private static boolean isDltTrackEmpty(byte[] module, int pos)
	{
		return (module[8198 + pos] & 0xff) >= 67 && (module[8454 + pos] & 0xff) >= 64 && (module[8710 + pos] & 0xff) >= 64 && (module[8966 + pos] & 0xff) >= 64;
	}

	private static boolean isDltPatternEnd(byte[] module, int pos, int i)
	{
		for (int ch = 0; ch < 4; ch++) {
			int pattern = module[8198 + (ch << 8) + pos] & 0xff;
			if (pattern < 64) {
				int offset = 6 + (pattern << 7) + (i << 1);
				if ((module[offset] & 128) == 0 && (module[offset + 1] & 128) != 0)
					return true;
			}
		}
		return false;
	}

	private void parseDltSong(byte[] module, boolean[] seen, int pos)
	{
		while (pos < 128 && !seen[pos] && isDltTrackEmpty(module, pos))
			seen[pos++] = true;
		this.songPos[this.songs] = (byte) pos;
		int playerCalls = 0;
		boolean loop = false;
		int tempo = 6;
		while (pos < 128) {
			if (seen[pos]) {
				loop = true;
				break;
			}
			seen[pos] = true;
			int p1 = module[8198 + pos] & 0xff;
			if (p1 == 64 || isDltTrackEmpty(module, pos))
				break;
			if (p1 == 65)
				pos = module[8326 + pos] & 0xff;
			else if (p1 == 66)
				tempo = module[8326 + pos++] & 0xff;
			else {
				for (int i = 0; i < 64 && !isDltPatternEnd(module, pos, i); i++)
					playerCalls += tempo;
				pos++;
			}
		}
		if (playerCalls > 0) {
			this.loops[this.songs] = loop;
			addSong(playerCalls);
		}
	}

	private void parseDlt(byte[] module, int moduleLen) throws ASAPFormatException
	{
		if (moduleLen != 11270 && moduleLen != 11271)
			throw new ASAPFormatException("Invalid module length");
		this.originalType = this.type = ASAPModuleType.DLT;
		parseModule(module, moduleLen);
		if (this.music != 8192)
			throw new ASAPFormatException("Unsupported module address");
		final boolean[] seen = new boolean[128];
		this.songs = 0;
		for (int pos = 0; pos < 128 && this.songs < 32; pos++) {
			if (!seen[pos])
				parseDltSong(module, seen, pos);
		}
		if (this.songs == 0)
			throw new ASAPFormatException("No songs found");
	}

	private static int getMptTrackAddress(byte[] module, int ch)
	{
		return (module[454 + ch] & 0xff) + ((module[458 + ch] & 0xff) << 8);
	}

	private static int getMptFirstInstrOrPatternAddr(byte[] module)
	{
		for (int offset = 6; offset < 198; offset += 2) {
			int addr = getWord(module, offset);
			if (addr != 0)
				return addr;
		}
		return getWord(module, 4) + 1;
	}

	private static int getMptTrackLen(byte[] module, int ch)
	{
		int endAddr = ch == 3 ? getMptFirstInstrOrPatternAddr(module) : getMptTrackAddress(module, ch + 1);
		return endAddr - getMptTrackAddress(module, ch);
	}

	private void parseMptSong(byte[] module, boolean[] globalSeen, int songLen, int pos)
	{
		int addrToOffset = getWord(module, 2) - 6 - this.mptSongBugExtra;
		int tempo = module[463] & 0xff;
		int playerCalls = 0;
		final byte[] seen = new byte[256];
		final int[] patternOffset = new int[4];
		final int[] blankRows = new int[4];
		final int[] blankRowsCounter = new int[4];
		while (pos < songLen) {
			if (seen[pos] != 0) {
				if (seen[pos] != 1)
					this.loops[this.songs] = true;
				break;
			}
			seen[pos] = 1;
			globalSeen[pos] = true;
			int i = module[464 + (pos << 1)] & 0xff;
			if (i == 255) {
				pos = module[465 + (pos << 1)] & 0xff;
				continue;
			}
			int ch;
			for (ch = 3; ch >= 0; ch--) {
				if (this.mptSongBugExtra == 0)
					i = getMptTrackAddress(module, ch) - addrToOffset;
				else
					i = 464 + ch * getMptTrackLen(module, 0);
				i = module[i + (pos << 1)] & 0xff;
				if (i >= 64)
					break;
				i = getWord(module, 70 + (i << 1));
				patternOffset[ch] = i == 0 ? 0 : i - addrToOffset;
				blankRowsCounter[ch] = 0;
			}
			if (ch >= 0)
				break;
			for (i = 0; i < songLen; i++)
				if (seen[i] == 1)
					seen[i] = 2;
			for (int patternRows = module[462] & 0xff; --patternRows >= 0;) {
				for (ch = 3; ch >= 0; ch--) {
					if (patternOffset[ch] == 0)
						continue;
					if (--blankRowsCounter[ch] >= 0)
						continue;
					for (;;) {
						i = module[patternOffset[ch]++] & 0xff;
						if (i < 64 || i == 254)
							break;
						if (i < 128)
							continue;
						if (i < 192) {
							blankRows[ch] = i - 128;
							continue;
						}
						if (i < 208)
							continue;
						if (i < 224) {
							tempo = i - 207;
							continue;
						}
						patternRows = 0;
					}
					blankRowsCounter[ch] = blankRows[ch];
				}
				playerCalls += tempo;
			}
			pos++;
		}
		if (playerCalls > 0)
			addSong(playerCalls);
	}

	private void parseMpt(byte[] module, int moduleLen, ASAPModuleType type) throws ASAPFormatException
	{
		if (moduleLen < 464)
			throw new ASAPFormatException("Module too short");
		this.originalType = this.type = type;
		int track0Len = getMptTrackLen(module, 0);
		if (7 + getWord(module, 4) - getWord(module, 2) == moduleLen)
			this.mptSongBugExtra = 0;
		else {
			this.mptSongBugExtra = 3 * track0Len + getMptTrackAddress(module, 1) - getMptFirstInstrOrPatternAddr(module);
			moduleLen -= this.mptSongBugExtra;
		}
		parseModule(module, moduleLen);
		if (getMptTrackAddress(module, 0) != getWord(module, 2) + 458)
			throw new ASAPFormatException("Invalid address of the first track");
		int songLen = track0Len >> 1;
		if (songLen > 254)
			throw new ASAPFormatException("Song too long");
		final boolean[] globalSeen = new boolean[256];
		this.songs = 0;
		for (int pos = 0; pos < songLen && this.songs < 32; pos++) {
			if (!globalSeen[pos]) {
				this.songPos[this.songs] = (byte) pos;
				parseMptSong(module, globalSeen, songLen, pos);
			}
		}
		if (this.songs == 0)
			throw new ASAPFormatException("No songs found");
	}

	private static int getRmtInstrumentFrames(byte[] module, int instrument, int volume, int volumeFrame, boolean onExtraPokey)
	{
		int addrToOffset = getWord(module, 2) - 6;
		instrument = getWord(module, 14) - addrToOffset + (instrument << 1);
		if (module[instrument + 1] == 0)
			return 0;
		instrument = getWord(module, instrument) - addrToOffset;
		int perFrame = module[12] & 0xff;
		int playerCall = volumeFrame * perFrame;
		int playerCalls = playerCall;
		int index = (module[instrument] & 0xff) + 1 + playerCall * 3;
		int indexEnd = (module[instrument + 2] & 0xff) + 3;
		int indexLoop = module[instrument + 3] & 0xff;
		if (indexLoop >= indexEnd)
			return 0;
		int volumeSlideDepth = module[instrument + 6] & 0xff;
		int volumeMin = module[instrument + 7] & 0xff;
		if (index >= indexEnd)
			index = (index - indexEnd) % (indexEnd - indexLoop) + indexLoop;
		else {
			do {
				int vol = module[instrument + index] & 0xff;
				if (onExtraPokey)
					vol >>= 4;
				if ((vol & 15) >= (GET_RMT_INSTRUMENT_FRAMES_RMT_VOLUME_SILENT[volume] & 0xff))
					playerCalls = playerCall + 1;
				playerCall++;
				index += 3;
			}
			while (index < indexEnd);
		}
		if (volumeSlideDepth == 0)
			return playerCalls / perFrame;
		int volumeSlide = 128;
		boolean silentLoop = false;
		for (;;) {
			if (index >= indexEnd) {
				if (silentLoop)
					break;
				silentLoop = true;
				index = indexLoop;
			}
			int vol = module[instrument + index] & 0xff;
			if (onExtraPokey)
				vol >>= 4;
			if ((vol & 15) >= (GET_RMT_INSTRUMENT_FRAMES_RMT_VOLUME_SILENT[volume] & 0xff)) {
				playerCalls = playerCall + 1;
				silentLoop = false;
			}
			playerCall++;
			index += 3;
			volumeSlide -= volumeSlideDepth;
			if (volumeSlide < 0) {
				volumeSlide += 256;
				if (--volume <= volumeMin)
					break;
			}
		}
		return playerCalls / perFrame;
	}

	private void parseRmtSong(byte[] module, boolean[] globalSeen, int songLen, int posShift, int pos)
	{
		int addrToOffset = getWord(module, 2) - 6;
		int tempo = module[11] & 0xff;
		int frames = 0;
		int songOffset = getWord(module, 20) - addrToOffset;
		int patternLoOffset = getWord(module, 16) - addrToOffset;
		int patternHiOffset = getWord(module, 18) - addrToOffset;
		final byte[] seen = new byte[256];
		final int[] patternBegin = new int[8];
		final int[] patternOffset = new int[8];
		final int[] blankRows = new int[8];
		final int[] instrumentNo = new int[8];
		final int[] instrumentFrame = new int[8];
		final int[] volumeValue = new int[8];
		final int[] volumeFrame = new int[8];
		while (pos < songLen) {
			if (seen[pos] != 0) {
				if (seen[pos] != 1)
					this.loops[this.songs] = true;
				break;
			}
			seen[pos] = 1;
			globalSeen[pos] = true;
			if (module[songOffset + (pos << posShift)] == (byte) 254) {
				pos = module[songOffset + (pos << posShift) + 1] & 0xff;
				continue;
			}
			for (int ch = 0; ch < 1 << posShift; ch++) {
				int p = module[songOffset + (pos << posShift) + ch] & 0xff;
				if (p == 255)
					blankRows[ch] = 256;
				else {
					patternOffset[ch] = patternBegin[ch] = (module[patternLoOffset + p] & 0xff) + ((module[patternHiOffset + p] & 0xff) << 8) - addrToOffset;
					if (patternOffset[ch] < 0)
						return;
					blankRows[ch] = 0;
				}
			}
			for (int i = 0; i < songLen; i++)
				if (seen[i] == 1)
					seen[i] = 2;
			for (int patternRows = module[10] & 0xff; --patternRows >= 0;) {
				for (int ch = 0; ch < 1 << posShift; ch++) {
					if (--blankRows[ch] > 0)
						continue;
					for (;;) {
						int i = module[patternOffset[ch]++] & 0xff;
						if ((i & 63) < 62) {
							i += (module[patternOffset[ch]++] & 0xff) << 8;
							if ((i & 63) != 61) {
								instrumentNo[ch] = i >> 10;
								instrumentFrame[ch] = frames;
							}
							volumeValue[ch] = i >> 6 & 15;
							volumeFrame[ch] = frames;
							break;
						}
						if (i == 62) {
							blankRows[ch] = module[patternOffset[ch]++] & 0xff;
							break;
						}
						if ((i & 63) == 62) {
							blankRows[ch] = i >> 6;
							break;
						}
						if ((i & 191) == 63) {
							tempo = module[patternOffset[ch]++] & 0xff;
							continue;
						}
						if (i == 191) {
							patternOffset[ch] = patternBegin[ch] + (module[patternOffset[ch]] & 0xff);
							continue;
						}
						patternRows = -1;
						break;
					}
					if (patternRows < 0)
						break;
				}
				if (patternRows >= 0)
					frames += tempo;
			}
			pos++;
		}
		int instrumentFrames = 0;
		for (int ch = 0; ch < 1 << posShift; ch++) {
			int frame = instrumentFrame[ch];
			frame += getRmtInstrumentFrames(module, instrumentNo[ch], volumeValue[ch], volumeFrame[ch] - frame, ch >= 4);
			if (instrumentFrames < frame)
				instrumentFrames = frame;
		}
		if (frames > instrumentFrames) {
			if (frames - instrumentFrames > 100)
				this.loops[this.songs] = false;
			frames = instrumentFrames;
		}
		if (frames > 0)
			addSong(frames);
	}

	private static boolean validateRmt(byte[] module, int moduleLen)
	{
		if (moduleLen < 48)
			return false;
		if (module[6] != 'R' || module[7] != 'M' || module[8] != 'T' || module[13] != 1)
			return false;
		return true;
	}

	private void parseRmt(byte[] module, int moduleLen) throws ASAPFormatException
	{
		if (!validateRmt(module, moduleLen))
			throw new ASAPFormatException("Invalid RMT file");
		int posShift;
		switch (module[9]) {
		case '4':
			posShift = 2;
			break;
		case '8':
			this.channels = 2;
			posShift = 3;
			break;
		default:
			throw new ASAPFormatException("Unsupported number of channels");
		}
		int perFrame = module[12] & 0xff;
		if (perFrame < 1 || perFrame > 4)
			throw new ASAPFormatException("Unsupported player call rate");
		this.originalType = this.type = ASAPModuleType.RMT;
		parseModule(module, moduleLen);
		int blockLen = getWord(module, 4) + 1 - this.music;
		int songLen = getWord(module, 4) + 1 - getWord(module, 20);
		if (posShift == 3 && (songLen & 4) != 0 && module[6 + blockLen - 4] == (byte) 254)
			songLen += 4;
		songLen >>= posShift;
		if (songLen >= 256)
			throw new ASAPFormatException("Song too long");
		final boolean[] globalSeen = new boolean[256];
		this.songs = 0;
		for (int pos = 0; pos < songLen && this.songs < 32; pos++) {
			if (!globalSeen[pos]) {
				this.songPos[this.songs] = (byte) pos;
				parseRmtSong(module, globalSeen, songLen, posShift, pos);
			}
		}
		this.fastplay = 312 / perFrame;
		this.player = 1536;
		if (this.songs == 0)
			throw new ASAPFormatException("No songs found");
		final byte[] title = new byte[127];
		int titleLen;
		for (titleLen = 0; titleLen < 127 && 10 + blockLen + titleLen < moduleLen; titleLen++) {
			int c = module[10 + blockLen + titleLen] & 0xff;
			if (c == 0)
				break;
			title[titleLen] = (byte) (isValidChar(c) ? c : ' ');
		}
		this.title = new String(title, 0, titleLen, StandardCharsets.UTF_8);
	}

	private void parseTmcSong(byte[] module, int pos)
	{
		int addrToOffset = getWord(module, 2) - 6;
		int tempo = (module[36] & 0xff) + 1;
		int frames = 0;
		final int[] patternOffset = new int[8];
		final int[] blankRows = new int[8];
		while ((module[437 + pos] & 0xff) < 128) {
			for (int ch = 7; ch >= 0; ch--) {
				int pat = module[437 + pos - 2 * ch] & 0xff;
				patternOffset[ch] = (module[166 + pat] & 0xff) + ((module[294 + pat] & 0xff) << 8) - addrToOffset;
				blankRows[ch] = 0;
			}
			for (int patternRows = 64; --patternRows >= 0;) {
				for (int ch = 7; ch >= 0; ch--) {
					if (--blankRows[ch] >= 0)
						continue;
					for (;;) {
						int i = module[patternOffset[ch]++] & 0xff;
						if (i < 64) {
							patternOffset[ch]++;
							break;
						}
						if (i == 64) {
							i = module[patternOffset[ch]++] & 0xff;
							if ((i & 127) == 0)
								patternRows = 0;
							else
								tempo = (i & 127) + 1;
							if (i >= 128)
								patternOffset[ch]++;
							break;
						}
						if (i < 128) {
							i = module[patternOffset[ch]++] & 127;
							if (i == 0)
								patternRows = 0;
							else
								tempo = i + 1;
							patternOffset[ch]++;
							break;
						}
						if (i < 192)
							continue;
						blankRows[ch] = i - 191;
						break;
					}
				}
				frames += tempo;
			}
			pos += 16;
		}
		if ((module[436 + pos] & 0xff) < 128)
			this.loops[this.songs] = true;
		addSong(frames);
	}

	private static int parseTmcTitle(byte[] title, int titleLen, byte[] module, int moduleOffset)
	{
		int lastOffset = moduleOffset + 29;
		while (module[lastOffset] == ' ') {
			if (--lastOffset < moduleOffset)
				return titleLen;
		}
		if (titleLen > 0) {
			title[titleLen++] = ' ';
			title[titleLen++] = '|';
			title[titleLen++] = ' ';
		}
		while (moduleOffset <= lastOffset) {
			int c = module[moduleOffset++] & 127;
			switch (c) {
			case 20:
				c = '*';
				break;
			case 1:
			case 3:
			case 5:
			case 12:
			case 14:
			case 15:
			case 19:
				c += 96;
				break;
			case 24:
			case 26:
				c = 'z';
				break;
			default:
				if (!isValidChar(c))
					c = ' ';
				break;
			}
			title[titleLen++] = (byte) c;
		}
		return titleLen;
	}

	private void parseTmc(byte[] module, int moduleLen) throws ASAPFormatException
	{
		if (moduleLen < 464)
			throw new ASAPFormatException("Module too short");
		this.originalType = this.type = ASAPModuleType.TMC;
		parseModule(module, moduleLen);
		this.channels = 2;
		int i = 0;
		while (module[102 + i] == 0) {
			if (++i >= 64)
				throw new ASAPFormatException("No instruments");
		}
		int lastPos = ((module[102 + i] & 0xff) << 8) + (module[38 + i] & 0xff) - getWord(module, 2) - 432;
		if (437 + lastPos >= moduleLen)
			throw new ASAPFormatException("Module too short");
		do {
			if (lastPos <= 0)
				throw new ASAPFormatException("No songs found");
			lastPos -= 16;
		}
		while ((module[437 + lastPos] & 0xff) >= 128);
		this.songs = 0;
		parseTmcSong(module, 0);
		for (i = 0; i < lastPos && this.songs < 32; i += 16)
			if ((module[437 + i] & 0xff) >= 128)
				parseTmcSong(module, i + 16);
		i = module[37] & 0xff;
		if (i < 1 || i > 4)
			throw new ASAPFormatException("Unsupported player call rate");
		this.fastplay = 312 / i;
		final byte[] title = new byte[127];
		int titleLen = parseTmcTitle(title, 0, module, 6);
		this.title = new String(title, 0, titleLen, StandardCharsets.UTF_8);
	}

	private void parseTm2Song(byte[] module, int pos)
	{
		int addrToOffset = getWord(module, 2) - 6;
		int tempo = (module[36] & 0xff) + 1;
		int playerCalls = 0;
		final int[] patternOffset = new int[8];
		final int[] blankRows = new int[8];
		for (;;) {
			int patternRows = module[918 + pos] & 0xff;
			if (patternRows == 0)
				break;
			if (patternRows >= 128) {
				this.loops[this.songs] = true;
				break;
			}
			for (int ch = 7; ch >= 0; ch--) {
				int pat = module[917 + pos - 2 * ch] & 0xff;
				patternOffset[ch] = (module[262 + pat] & 0xff) + ((module[518 + pat] & 0xff) << 8) - addrToOffset;
				blankRows[ch] = 0;
			}
			while (--patternRows >= 0) {
				for (int ch = 7; ch >= 0; ch--) {
					if (--blankRows[ch] >= 0)
						continue;
					for (;;) {
						int i = module[patternOffset[ch]++] & 0xff;
						if (i == 0) {
							patternOffset[ch]++;
							break;
						}
						if (i < 64) {
							if ((module[patternOffset[ch]++] & 0xff) >= 128)
								patternOffset[ch]++;
							break;
						}
						if (i < 128) {
							patternOffset[ch]++;
							break;
						}
						if (i == 128) {
							blankRows[ch] = module[patternOffset[ch]++] & 0xff;
							break;
						}
						if (i < 192)
							break;
						if (i < 208) {
							tempo = i - 191;
							continue;
						}
						if (i < 224) {
							patternOffset[ch]++;
							break;
						}
						if (i < 240) {
							patternOffset[ch] += 2;
							break;
						}
						if (i < 255) {
							blankRows[ch] = i - 240;
							break;
						}
						blankRows[ch] = 64;
						break;
					}
				}
				playerCalls += tempo;
			}
			pos += 17;
		}
		addSong(playerCalls);
	}

	private void parseTm2(byte[] module, int moduleLen) throws ASAPFormatException
	{
		if (moduleLen < 932)
			throw new ASAPFormatException("Module too short");
		this.originalType = this.type = ASAPModuleType.TM2;
		parseModule(module, moduleLen);
		int i = module[37] & 0xff;
		if (i < 1 || i > 4)
			throw new ASAPFormatException("Unsupported player call rate");
		this.fastplay = 312 / i;
		this.player = 2048;
		if (module[31] != 0)
			this.channels = 2;
		int lastPos = 65535;
		for (i = 0; i < 128; i++) {
			int instrAddr = (module[134 + i] & 0xff) + ((module[774 + i] & 0xff) << 8);
			if (instrAddr != 0 && instrAddr < lastPos)
				lastPos = instrAddr;
		}
		for (i = 0; i < 256; i++) {
			int patternAddr = (module[262 + i] & 0xff) + ((module[518 + i] & 0xff) << 8);
			if (patternAddr != 0 && patternAddr < lastPos)
				lastPos = patternAddr;
		}
		lastPos -= getWord(module, 2) + 896;
		if (902 + lastPos >= moduleLen)
			throw new ASAPFormatException("Module too short");
		int c;
		do {
			if (lastPos <= 0)
				throw new ASAPFormatException("No songs found");
			lastPos -= 17;
			c = module[918 + lastPos] & 0xff;
		}
		while (c == 0 || c >= 128);
		this.songs = 0;
		parseTm2Song(module, 0);
		for (i = 0; i < lastPos && this.songs < 32; i += 17) {
			c = module[918 + i] & 0xff;
			if (c == 0 || c >= 128)
				parseTm2Song(module, i + 17);
		}
		final byte[] title = new byte[127];
		int titleLen = parseTmcTitle(title, 0, module, 39);
		titleLen = parseTmcTitle(title, titleLen, module, 71);
		titleLen = parseTmcTitle(title, titleLen, module, 103);
		this.title = new String(title, 0, titleLen, StandardCharsets.UTF_8);
	}

	private static int afterFF(byte[] module, int moduleLen, int currentOffset) throws ASAPFormatException
	{
		while (currentOffset < moduleLen) {
			if (module[currentOffset++] == (byte) 255)
				return currentOffset;
		}
		throw new ASAPFormatException("Module too short");
	}

	private static int getFcTrackCommand(byte[] module, int[] trackPos, int n)
	{
		return module[3 + (n << 8) + trackPos[n]] & 0xff;
	}

	private static boolean isFcSongEnd(byte[] module, int[] trackPos)
	{
		boolean allLoop = true;
		for (int n = 0; n < 3; n++) {
			if (trackPos[n] >= 256)
				return true;
			switch (getFcTrackCommand(module, trackPos, n)) {
			case 254:
				return true;
			case 255:
				break;
			default:
				allLoop = false;
				break;
			}
		}
		return allLoop;
	}

	private static boolean validateFc(byte[] module, int moduleLen)
	{
		if (moduleLen < 899)
			return false;
		if (module[0] != 38 || module[1] != 35)
			return false;
		return true;
	}

	private void parseFc(byte[] module, int moduleLen) throws ASAPFormatException
	{
		if (!validateFc(module, moduleLen))
			throw new ASAPFormatException("Invalid FC file");
		this.originalType = this.type = ASAPModuleType.FC;
		this.player = 1024;
		this.music = 2560;
		this.songs = 0;
		this.headerLen = -1;
		final int[] patternOffsets = new int[64];
		int currentOffset = 899;
		for (int i = 0; i < 64; i++) {
			patternOffsets[i] = currentOffset;
			currentOffset = afterFF(module, moduleLen, currentOffset);
		}
		for (int i = 0; i < 32; i++)
			currentOffset = afterFF(module, moduleLen, currentOffset);
		for (int pos = 0; pos < 256 && this.songs < 32;) {
			final int[] trackPos = new int[3];
			for (int n = 0; n < 3; n++)
				trackPos[n] = pos;
			final int[] patternDelay = new int[3];
			final int[] noteDuration = new int[3];
			final int[] patternPos = new int[3];
			int playerCalls = 0;
			this.loops[this.songs] = true;
			while (!isFcSongEnd(module, trackPos)) {
				for (int n = 0; n < 3; n++) {
					if (getFcTrackCommand(module, trackPos, n) == 255)
						continue;
					if (patternDelay[n]-- > 0)
						continue;
					while (trackPos[n] < 256) {
						int trackCmd = getFcTrackCommand(module, trackPos, n);
						if (trackCmd < 64) {
							int patternCmd = module[patternOffsets[trackCmd] + patternPos[n]++] & 0xff;
							if (patternCmd < 64) {
								patternDelay[n] = noteDuration[n];
								break;
							}
							else if (patternCmd < 96)
								noteDuration[n] = patternCmd - 64;
							else if (patternCmd == 255) {
								patternDelay[n] = 0;
								noteDuration[n] = 0;
								patternPos[n] = 0;
								trackPos[n]++;
							}
						}
						else if (trackCmd == 64)
							trackPos[n] += 2;
						else if (trackCmd == 254) {
							this.loops[this.songs] = false;
							break;
						}
						else if (trackCmd == 255)
							break;
						else
							trackPos[n]++;
					}
				}
				if (isFcSongEnd(module, trackPos))
					break;
				playerCalls += module[2] & 0xff;
			}
			pos = -1;
			for (int n = 0; n < 3; n++) {
				int nxtrkpos = trackPos[n];
				if (patternPos[n] > 0)
					nxtrkpos++;
				if (pos < nxtrkpos)
					pos = nxtrkpos;
			}
			pos++;
			if (pos <= 256)
				addSong(playerCalls);
		}
	}

	static final int MPT_SAMPLES_VBLK_PAUSE_SCANLINES = 11;

	private void parseMptSamples(byte[] content, int contentLen) throws ASAPFormatException
	{
		if (contentLen < 288)
			throw new ASAPFormatException("File too short");
		this.originalType = this.type = ASAPModuleType.D15;
		this.songs = 0;
		this.headerLen = -1;
		int end = -1;
		for (int i = 0; i < 16; i++) {
			int start = content[i] & 0xff;
			if (start == 0) {
				for (; i < 16; i++) {
					if (content[i] != 0 || content[16 + i] != 0)
						throw new ASAPFormatException("Invalid header");
				}
				break;
			}
			if (i > 0 && start != end)
				throw new ASAPFormatException("Invalid header");
			end = content[16 + i] & 0xff;
			if (end <= start)
				throw new ASAPFormatException("Invalid header");
			int samples = (end - start) << 9;
			addSong(samples + samples / 301 * 11);
		}
		if (contentLen < ((end - (content[0] & 0xff)) << 8) + 32)
			throw new ASAPFormatException("File too short");
		this.music = ((content[0] & 0xff) << 8) - 32;
	}

	private static String parseText(byte[] module, int i, int argEnd)
	{
		int len = argEnd - i - 2;
		if (i < 0 || len < 0 || module[i] != '"' || module[argEnd - 1] != '"')
			return "";
		if (len == 3 && module[i + 1] == '<' && module[i + 2] == '?' && module[i + 3] == '>')
			return "";
		return new String(module, i + 1, len, StandardCharsets.UTF_8);
	}

	private static boolean hasStringAt(byte[] module, int moduleIndex, String s)
	{
		for (int _i = 0; _i < s.length(); _i++) {
			int c = s.charAt(_i);
			if (c != (module[moduleIndex++] & 0xff))
				return false;
		}
		return true;
	}

	private static int parseDec(byte[] module, int i, int argEnd, int minVal, int maxVal) throws ASAPFormatException
	{
		if (i < 0)
			throw new ASAPFormatException("Missing number");
		int r = 0;
		while (i < argEnd) {
			int c = module[i++] & 0xff;
			if (c < '0' || c > '9')
				throw new ASAPFormatException("Invalid number");
			r = r * 10 + c - '0';
			if (r > maxVal)
				throw new ASAPFormatException("Number too big");
		}
		if (r < minVal)
			throw new ASAPFormatException("Number too small");
		return r;
	}

	private static int parseHex(byte[] module, int i, int argEnd) throws ASAPFormatException
	{
		if (i < 0)
			throw new ASAPFormatException("Missing number");
		int r = 0;
		while (i < argEnd) {
			int c = module[i++] & 0xff;
			if (r > 4095)
				throw new ASAPFormatException("Number too big");
			r <<= 4;
			if (c >= '0' && c <= '9')
				r += c - '0';
			else if (c >= 'A' && c <= 'F')
				r += c - 'A' + 10;
			else if (c >= 'a' && c <= 'f')
				r += c - 'a' + 10;
			else
				throw new ASAPFormatException("Invalid number");
		}
		return r;
	}

	/**
	 * Returns the number of milliseconds represented by the given string.
	 * @param s Time in the <code>"mm:ss.xxx"</code> format.
	 */
	public static int parseDuration(String s) throws ASAPFormatException
	{
		final DurationParser parser = new DurationParser();
		return parser.parse(s);
	}

	private static boolean validateSap(byte[] module, int moduleLen)
	{
		return moduleLen >= 30 && hasStringAt(module, 0, "SAP\r\n");
	}

	final int getFirstBlockLen(byte[] module)
	{
		return getWord(module, this.headerLen + 4) - getWord(module, this.headerLen + 2) + 7;
	}

	static final int RMT_INIT = 3200;

	final int getRmtSapOffset(byte[] module, int moduleLen)
	{
		if (this.player != 13315)
			return -1;
		int offset = this.headerLen + getFirstBlockLen(module);
		if (offset + 6 >= moduleLen || module[offset + 4] != 'R' || module[offset + 5] != 'M' || module[offset + 6] != 'T')
			return -1;
		return offset;
	}

	private void parseSap(byte[] module, int moduleLen) throws ASAPFormatException
	{
		if (!validateSap(module, moduleLen))
			throw new ASAPFormatException("Invalid SAP file");
		this.fastplay = -1;
		int type = 0;
		int moduleIndex = 5;
		int durationIndex = 0;
		while (module[moduleIndex] != (byte) 255) {
			int lineStart = moduleIndex;
			while ((module[moduleIndex] & 0xff) > ' ') {
				if (++moduleIndex >= moduleLen)
					throw new ASAPFormatException("Invalid SAP file");
			}
			int tagLen = moduleIndex - lineStart;
			int argStart = -1;
			int argEnd = -1;
			for (;;) {
				int c = module[moduleIndex] & 0xff;
				if (c > ' ') {
					if (!isValidChar(c))
						throw new ASAPFormatException("Invalid character");
					if (argStart < 0)
						argStart = moduleIndex;
					argEnd = -1;
				}
				else {
					if (argEnd < 0)
						argEnd = moduleIndex;
					if (c == '\n')
						break;
				}
				if (++moduleIndex >= moduleLen)
					throw new ASAPFormatException("Invalid SAP file");
			}
			if (++moduleIndex + 6 >= moduleLen)
				throw new ASAPFormatException("Invalid SAP file");
			switch (new String(module, lineStart, tagLen, StandardCharsets.UTF_8)) {
			case "AUTHOR":
				this.author = parseText(module, argStart, argEnd);
				break;
			case "NAME":
				this.title = parseText(module, argStart, argEnd);
				break;
			case "DATE":
				this.date = parseText(module, argStart, argEnd);
				break;
			case "TIME":
				if (durationIndex >= 32)
					throw new ASAPFormatException("Too many TIME tags");
				if (argStart < 0)
					throw new ASAPFormatException("Missing TIME argument");
				if (argEnd - argStart > 5 && hasStringAt(module, argEnd - 5, " LOOP")) {
					this.loops[durationIndex] = true;
					argEnd -= 5;
				}
				{
					String arg = new String(module, argStart, argEnd - argStart, StandardCharsets.UTF_8);
					this.durations[durationIndex++] = parseDuration(arg);
				}
				break;
			case "SONGS":
				this.songs = parseDec(module, argStart, argEnd, 1, 32);
				break;
			case "DEFSONG":
				this.defaultSong = parseDec(module, argStart, argEnd, 0, 31);
				break;
			case "TYPE":
				if (argStart < 0)
					throw new ASAPFormatException("Missing TYPE argument");
				type = module[argStart] & 0xff;
				break;
			case "FASTPLAY":
				this.fastplay = parseDec(module, argStart, argEnd, 1, 32767);
				break;
			case "MUSIC":
				this.music = parseHex(module, argStart, argEnd);
				break;
			case "INIT":
				this.init = parseHex(module, argStart, argEnd);
				break;
			case "PLAYER":
				this.player = parseHex(module, argStart, argEnd);
				break;
			case "COVOX":
				this.covoxAddr = parseHex(module, argStart, argEnd);
				if (this.covoxAddr != 54784)
					throw new ASAPFormatException("COVOX should be D600");
				this.channels = 2;
				break;
			case "STEREO":
				this.channels = 2;
				break;
			case "NTSC":
				this.ntsc = true;
				break;
			default:
				break;
			}
		}
		if (this.defaultSong >= this.songs)
			throw new ASAPFormatException("DEFSONG too big");
		this.headerLen = moduleIndex;
		switch (type) {
		case 'B':
			if (this.player < 0)
				throw new ASAPFormatException("Missing PLAYER tag");
			if (this.init < 0)
				throw new ASAPFormatException("Missing INIT tag");
			this.originalType = this.type = ASAPModuleType.SAP_B;
			if ((this.init == 1019 || this.init == 1017) && this.player == 1283)
				this.originalType = ASAPModuleType.DLT;
			else if (((this.init == 1267 || this.init == 1263) && this.player == 1283) || (this.init == 62707 && this.player == 62723))
				this.originalType = ASAPModuleType.MPT;
			else if (this.init == 3200 || getRmtSapOffset(module, moduleLen) > 0)
				this.originalType = ASAPModuleType.RMT;
			else if (this.init == 1269 || this.init == 62709 || this.init == 1266 || ((this.init == 1255 || this.init == 62695 || this.init == 1252) && this.fastplay == 156) || ((this.init == 1253 || this.init == 62693 || this.init == 1250) && (this.fastplay == 104 || this.fastplay == 78)))
				this.originalType = ASAPModuleType.TMC;
			else if ((this.init == 4224 && this.player == 1283) || (this.init == 4992 && this.player == 2051))
				this.originalType = ASAPModuleType.TM2;
			else if (this.init == 1024 && this.player == 1027)
				this.originalType = ASAPModuleType.FC;
			break;
		case 'C':
			if (this.player < 0)
				throw new ASAPFormatException("Missing PLAYER tag");
			if (this.music < 0)
				throw new ASAPFormatException("Missing MUSIC tag");
			this.originalType = this.type = ASAPModuleType.SAP_C;
			if ((this.player == 1280 || this.player == 62720) && moduleLen >= 1024) {
				if (this.channels > 1)
					this.originalType = ASAPModuleType.CMS;
				else if (module[moduleLen - 170] == 30)
					this.originalType = ASAPModuleType.CMR;
				else if (module[moduleLen - 909] == 48)
					this.originalType = ASAPModuleType.CM3;
				else
					this.originalType = ASAPModuleType.CMC;
			}
			break;
		case 'D':
			if (this.init < 0)
				throw new ASAPFormatException("Missing INIT tag");
			this.originalType = this.type = ASAPModuleType.SAP_D;
			if (this.player == 1283 || this.player == 62723) {
				if (this.init == this.player - 32 && moduleLen > 2231 && module[moduleLen - 2231] == (byte) 162)
					this.originalType = ASAPModuleType.MD1;
				else if (this.init == this.player - 30)
					this.originalType = ASAPModuleType.MD2;
			}
			break;
		case 'S':
			if (this.init < 0)
				throw new ASAPFormatException("Missing INIT tag");
			this.originalType = this.type = ASAPModuleType.SAP_S;
			if (this.fastplay < 0)
				this.fastplay = 78;
			break;
		default:
			throw new ASAPFormatException("Unsupported TYPE");
		}
		if (this.fastplay < 0)
			this.fastplay = this.ntsc ? 262 : 312;
		if (module[moduleIndex + 1] != (byte) 255)
			throw new ASAPFormatException("Invalid binary header");
	}

	static int packExt(String ext)
	{
		return ext.length() == 2 && ext.charAt(0) <= 'z' && ext.charAt(1) <= 'z' ? ext.charAt(0) | ext.charAt(1) << 8 | 2105376 : ext.length() == 3 && ext.charAt(0) <= 'z' && ext.charAt(1) <= 'z' && ext.charAt(2) <= 'z' ? ext.charAt(0) | ext.charAt(1) << 8 | ext.charAt(2) << 16 | 2105376 : 0;
	}

	static int getPackedExt(String filename)
	{
		int ext = 0;
		for (int i = filename.length(); --i > 0;) {
			int c = filename.charAt(i);
			if (c <= ' ' || c > 'z')
				return 0;
			if (c == '.')
				return ext | 2105376;
			ext = (ext << 8) + c;
		}
		return 0;
	}

	private static boolean isOurPackedExt(int ext)
	{
		switch (ext) {
		case 7364979:
		case 6516067:
		case 3370339:
		case 7499107:
		case 7564643:
		case 6516068:
		case 7629924:
		case 7630957:
		case 6582381:
		case 3236973:
		case 3302509:
		case 7630194:
		case 6516084:
		case 3698036:
		case 3304820:
		case 2122598:
		case 3486052:
		case 2111588:
			return true;
		default:
			return false;
		}
	}

	/**
	 * Checks whether the filename represents a module type supported by ASAP.
	 * Returns <code>true</code> if the filename is supported by ASAP.
	 * @param filename Filename to check the extension of.
	 */
	public static boolean isOurFile(String filename)
	{
		return isOurPackedExt(getPackedExt(filename));
	}

	/**
	 * Checks whether the filename extension represents a module type supported by ASAP.
	 * Returns <code>true</code> if the filename extension is supported by ASAP.
	 * @param ext Filename extension without the leading dot.
	 */
	public static boolean isOurExt(String ext)
	{
		return isOurPackedExt(packExt(ext));
	}

	private static int guessPackedExt(byte[] module, int moduleLen) throws ASAPFormatException
	{
		if (validateSap(module, moduleLen))
			return 7364979;
		if (validateFc(module, moduleLen))
			return 2122598;
		if (validateRmt(module, moduleLen))
			return 7630194;
		throw new ASAPFormatException("Unknown format");
	}

	/**
	 * Loads file information.
	 * @param filename Filename, used to determine the format.
	 * @param module Contents of the file.
	 * @param moduleLen Length of the file.
	 */
	public final void load(String filename, byte[] module, int moduleLen) throws ASAPFormatException
	{
		int ext;
		if (filename != null) {
			int basename = 0;
			int extPos = -1;
			for (int i = filename.length(); --i >= 0;) {
				int c = filename.charAt(i);
				if (c == '/' || c == '\\') {
					basename = i + 1;
					break;
				}
				if (c == '.')
					extPos = i;
			}
			if (extPos < 0)
				throw new ASAPFormatException("Filename has no extension");
			extPos = Math.min(extPos - basename, 127);
			this.filename = filename.substring(basename, basename + extPos);
			ext = getPackedExt(filename);
		}
		else {
			this.filename = "";
			ext = guessPackedExt(module, moduleLen);
		}
		this.author = "";
		this.title = "";
		this.date = "";
		this.channels = 1;
		this.songs = 1;
		this.defaultSong = 0;
		for (int i = 0; i < 32; i++) {
			this.durations[i] = -1;
			this.loops[i] = false;
		}
		this.ntsc = false;
		this.fastplay = 312;
		this.music = -1;
		this.init = -1;
		this.player = -1;
		this.covoxAddr = -1;
		this.headerLen = 0;
		switch (ext) {
		case 7364979:
			parseSap(module, moduleLen);
			return;
		case 6516067:
			parseCmc(module, moduleLen, ASAPModuleType.CMC);
			return;
		case 3370339:
			parseCmc(module, moduleLen, ASAPModuleType.CM3);
			return;
		case 7499107:
			parseCmc(module, moduleLen, ASAPModuleType.CMR);
			return;
		case 7564643:
			this.channels = 2;
			parseCmc(module, moduleLen, ASAPModuleType.CMS);
			return;
		case 6516068:
			this.fastplay = 156;
			parseCmc(module, moduleLen, ASAPModuleType.CMC);
			return;
		case 7629924:
			parseDlt(module, moduleLen);
			return;
		case 7630957:
			parseMpt(module, moduleLen, ASAPModuleType.MPT);
			return;
		case 6582381:
			this.fastplay = 156;
			parseMpt(module, moduleLen, ASAPModuleType.MPT);
			return;
		case 3236973:
			parseMpt(module, moduleLen, ASAPModuleType.MD1);
			return;
		case 3302509:
			parseMpt(module, moduleLen, ASAPModuleType.MD2);
			return;
		case 7630194:
			parseRmt(module, moduleLen);
			return;
		case 6516084:
		case 3698036:
			parseTmc(module, moduleLen);
			return;
		case 3304820:
			parseTm2(module, moduleLen);
			return;
		case 2122598:
			parseFc(module, moduleLen);
			return;
		case 3486052:
			this.fastplay = 1;
			parseMptSamples(module, moduleLen);
			return;
		case 2111588:
			this.fastplay = 2;
			parseMptSamples(module, moduleLen);
			return;
		default:
			throw new ASAPFormatException("Unknown filename extension");
		}
	}

	private static void checkValidText(String s) throws ASAPArgumentException
	{
		if (s.length() > 127)
			throw new ASAPArgumentException("Text too long");
		for (int _i = 0; _i < s.length(); _i++) {
			int c = s.charAt(_i);
			if (!isValidChar(c))
				throw new ASAPArgumentException("Invalid character");
		}
	}

	/**
	 * Returns author's name.
	 * A nickname may be included in parentheses after the real name.
	 * Multiple authors are separated with <code>" &amp; "</code>.
	 * An empty string means the author is unknown.
	 */
	public final String getAuthor()
	{
		return this.author;
	}

	/**
	 * Sets author's name.
	 * A nickname may be included in parentheses after the real name.
	 * Multiple authors are separated with <code>" &amp; "</code>.
	 * An empty string means the author is unknown.
	 * @param value New author's name for the current music.
	 */
	public final void setAuthor(String value) throws ASAPArgumentException
	{
		checkValidText(value);
		this.author = value;
	}

	/**
	 * Returns music title.
	 * An empty string means the title is unknown.
	 */
	public final String getTitle()
	{
		return this.title;
	}

	/**
	 * Sets music title.
	 * An empty string means the title is unknown.
	 * @param value New title for the current music.
	 */
	public final void setTitle(String value) throws ASAPArgumentException
	{
		checkValidText(value);
		this.title = value;
	}

	/**
	 * Returns music title or filename.
	 * If title is unknown returns filename without the path or extension.
	 */
	public final String getTitleOrFilename()
	{
		return this.title.length() > 0 ? this.title : this.filename;
	}

	/**
	 * Returns music creation date.
	 * 
	 * <p>Some of the possible formats are:
	 * <ul>
	 * <li>YYYY</li>
	 * <li>MM/YYYY</li>
	 * <li>DD/MM/YYYY</li>
	 * <li>YYYY-YYYY</li>
	 * </ul>
	 * <p>An empty string means the date is unknown.
	 */
	public final String getDate()
	{
		return this.date;
	}

	/**
	 * Sets music creation date.
	 * 
	 * <p>Some of the possible formats are:
	 * <ul>
	 * <li>YYYY</li>
	 * <li>MM/YYYY</li>
	 * <li>DD/MM/YYYY</li>
	 * <li>YYYY-YYYY</li>
	 * </ul>
	 * <p>An empty string means the date is unknown.
	 * @param value New music creation date.
	 */
	public final void setDate(String value) throws ASAPArgumentException
	{
		checkValidText(value);
		this.date = value;
	}

	private int checkDate()
	{
		int n = this.date.length();
		switch (n) {
		case 4:
		case 7:
		case 10:
			break;
		default:
			return -1;
		}
		for (int i = 0; i < n; i++) {
			int c = this.date.charAt(i);
			if (i == n - 5 || i == n - 8) {
				if (c != '/')
					return -1;
			}
			else if (c < '0' || c > '9')
				return -1;
		}
		return n;
	}

	private int getTwoDateDigits(int i)
	{
		return (this.date.charAt(i) - '0') * 10 + this.date.charAt(i + 1) - '0';
	}

	/**
	 * Returns music creation year.
	 * -1 means the year is unknown.
	 */
	public final int getYear()
	{
		int n = checkDate();
		if (n < 0)
			return -1;
		return getTwoDateDigits(n - 4) * 100 + getTwoDateDigits(n - 2);
	}

	/**
	 * Returns music creation month (1-12).
	 * -1 means the month is unknown.
	 */
	public final int getMonth()
	{
		int n = checkDate();
		if (n < 7)
			return -1;
		return getTwoDateDigits(n - 7);
	}

	/**
	 * Returns day of month of the music creation date.
	 * -1 means the day is unknown.
	 */
	public final int getDayOfMonth()
	{
		int n = checkDate();
		if (n != 10)
			return -1;
		return getTwoDateDigits(0);
	}

	/**
	 * Returns 1 for mono or 2 for stereo.
	 */
	public final int getChannels()
	{
		return this.channels;
	}

	/**
	 * Returns number of songs in the file.
	 */
	public final int getSongs()
	{
		return this.songs;
	}

	/**
	 * Returns 0-based index of the "main" song.
	 * The specified song should be played by default.
	 */
	public final int getDefaultSong()
	{
		return this.defaultSong;
	}

	/**
	 * Sets the 0-based index of the "main" song.
	 * @param song New default song.
	 */
	public final void setDefaultSong(int song) throws ASAPArgumentException
	{
		if (song < 0 || song >= this.songs)
			throw new ASAPArgumentException("Song out of range");
		this.defaultSong = song;
	}

	/**
	 * Returns length of the specified song.
	 * The length is specified in milliseconds. -1 means the length is indeterminate.
	 * @param song Song to get length of, 0-based.
	 */
	public final int getDuration(int song)
	{
		return this.durations[song];
	}

	/**
	 * Sets length of the specified song.
	 * The length is specified in milliseconds. -1 means the length is indeterminate.
	 * @param song Song to set length of, 0-based.
	 * @param duration New length in milliseconds.
	 */
	public final void setDuration(int song, int duration) throws ASAPArgumentException
	{
		if (song < 0 || song >= this.songs)
			throw new ASAPArgumentException("Song out of range");
		this.durations[song] = duration;
	}

	/**
	 * Returns information whether the specified song loops.
	 * 
	 * <p>Returns:
	 * <ul>
	 * <li><code>true</code> if the song loops</li>
	 * <li><code>false</code> if the song stops</li>
	 * </ul>
	 * @param song Song to check for looping, 0-based.
	 */
	public final boolean getLoop(int song)
	{
		return this.loops[song];
	}

	/**
	 * Sets information whether the specified song loops.
	 * 
	 * <p>Use:
	 * <ul>
	 * <li><code>true</code> if the song loops</li>
	 * <li><code>false</code> if the song stops</li>
	 * </ul>
	 * @param song Song to set as looping, 0-based.
	 * @param loop <code>true</code> if the song loops.
	 */
	public final void setLoop(int song, boolean loop) throws ASAPArgumentException
	{
		if (song < 0 || song >= this.songs)
			throw new ASAPArgumentException("Song out of range");
		this.loops[song] = loop;
	}

	/**
	 * Returns <code>true</code> for an NTSC song and <code>false</code> for a PAL song.
	 */
	public final boolean isNtsc()
	{
		return this.ntsc;
	}

	/**
	 * Returns <code>true</code> if NTSC can be set or removed.
	 */
	public final boolean canSetNtsc()
	{
		return this.type == ASAPModuleType.SAP_B && this.fastplay == (this.ntsc ? 262 : 312);
	}

	/**
	 * Marks a SAP file as NTSC or PAL.
	 * @param ntsc <code>true</code> for NTSC, <code>false</code> for PAL.
	 */
	public final void setNtsc(boolean ntsc)
	{
		this.ntsc = ntsc;
		this.fastplay = ntsc ? 262 : 312;
		for (int song = 0; song < this.songs; song++) {
			long duration = this.durations[song];
			if (duration > 0) {
				this.durations[song] = (int) (ntsc ? duration * 5956963 / 7159090 : duration * 7159090 / 5956963);
			}
		}
	}

	/**
	 * Returns the letter argument for the TYPE SAP tag.
	 * Returns zero for non-SAP files.
	 */
	public final int getTypeLetter()
	{
		switch (this.type) {
		case SAP_B:
			return 'B';
		case SAP_C:
			return 'C';
		case SAP_D:
			return 'D';
		case SAP_S:
			return 'S';
		default:
			return 0;
		}
	}

	/**
	 * Returns player routine rate in Atari scanlines.
	 */
	public final int getPlayerRateScanlines()
	{
		return this.fastplay;
	}

	/**
	 * Returns approximate player routine rate in Hz.
	 */
	public final int getPlayerRateHz()
	{
		int scanlineClock = this.ntsc ? 15699 : 15556;
		return (scanlineClock + (this.fastplay >> 1)) / this.fastplay;
	}

	/**
	 * Returns the address of the module.
	 * Returns -1 if unknown.
	 */
	public final int getMusicAddress()
	{
		return this.music;
	}

	/**
	 * Causes music to be relocated.
	 * Use only with <code>ASAPWriter.Write</code>.
	 * @param address New music address.
	 */
	public final void setMusicAddress(int address) throws ASAPArgumentException
	{
		if (address < 0 || address >= 65535)
			throw new ASAPArgumentException("Invalid music address");
		this.music = address;
	}

	/**
	 * Returns the address of the player initialization routine.
	 * Returns -1 if no initialization routine.
	 */
	public final int getInitAddress()
	{
		return this.init;
	}

	/**
	 * Returns the address of the player routine.
	 */
	public final int getPlayerAddress()
	{
		return this.player;
	}

	/**
	 * Returns the address of the COVOX chip.
	 * Returns -1 if no COVOX enabled.
	 */
	public final int getCovoxAddress()
	{
		return this.covoxAddr;
	}

	/**
	 * Returns the length of the SAP header in bytes.
	 */
	public final int getSapHeaderLength()
	{
		return this.headerLen;
	}

	/**
	 * Returns the offset of instrument names for RMT module.
	 * Returns -1 if not an RMT module or RMT module without instrument names.
	 * @param module Content of the RMT file.
	 * @param moduleLen Length of the RMT file.
	 */
	public final int getInstrumentNamesOffset(byte[] module, int moduleLen)
	{
		if (this.type != ASAPModuleType.RMT)
			return -1;
		for (int offset = getWord(module, 4) - getWord(module, 2) + 12; offset < moduleLen; offset++) {
			if (module[offset - 1] == 0)
				return offset;
		}
		return -1;
	}

	/**
	 * Returns human-readable description of the filename extension.
	 * @param ext Filename extension without the leading dot.
	 */
	public static String getExtDescription(String ext) throws ASAPFormatException
	{
		switch (packExt(ext)) {
		case 7364979:
			return "Slight Atari Player";
		case 6516067:
			return "Chaos Music Composer";
		case 3370339:
			return "CMC \"3/4\"";
		case 7499107:
			return "CMC \"Rzog\"";
		case 7564643:
			return "Stereo Double CMC";
		case 6516068:
			return "CMC DoublePlay";
		case 7629924:
			return "Delta Music Composer";
		case 7630957:
			return "Music ProTracker";
		case 3236973:
			return "Music ProTracker (1-channel samples)";
		case 3302509:
			return "Music ProTracker (2-channel samples)";
		case 6582381:
			return "MPT DoublePlay";
		case 7630194:
			return "Raster Music Tracker";
		case 6516084:
		case 3698036:
			return "Theta Music Composer 1.x";
		case 3304820:
			return "Theta Music Composer 2.x";
		case 2122598:
			return "Future Composer";
		case 3486052:
			return "Music ProTracker 15 kHz samples";
		case 2111588:
			return "Music ProTracker 8 kHz samples";
		case 7890296:
			return "Atari 8-bit executable";
		default:
			throw new ASAPFormatException("Unknown extension");
		}
	}

	/**
	 * Returns the extension of the original module format.
	 * For native modules it simply returns their extension.
	 * For the SAP format it attempts to detect the original module format.
	 */
	public final String getOriginalModuleExt()
	{
		switch (this.originalType) {
		case CMC:
			return this.fastplay == 156 ? "dmc" : "cmc";
		case CM3:
			return "cm3";
		case CMR:
			return "cmr";
		case CMS:
			return "cms";
		case DLT:
			return "dlt";
		case MPT:
			return this.fastplay == 156 ? "mpd" : "mpt";
		case MD1:
			return "md1";
		case MD2:
			return "md2";
		case RMT:
			return "rmt";
		case TMC:
			return "tmc";
		case TM2:
			return "tm2";
		case FC:
			return "fc";
		case D15:
			return this.fastplay == 1 ? "d15" : "d8";
		default:
			return null;
		}
	}

	private static final byte[] GET_RMT_INSTRUMENT_FRAMES_RMT_VOLUME_SILENT = { 16, 8, 4, 3, 2, 2, 2, 2, 1, 1, 1, 1, 1, 1, 1, 1 };
}
