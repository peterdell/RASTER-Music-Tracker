#include "StdAfx.h"
#include "GuiHelpers.h"

// MFC interface code
#include "Song.h"

#include "EffectsDlg.h"

#include "Atari6502.h"
#include "XPokey.h"
#include "IOHelpers.h"

#include "Instruments.h"
#include "Song.h"
#include "Clipboard.h"

#include "global.h"

#include "Keyboard2NoteMapping.h"
#include "ChannelControl.h"

extern CInstruments	g_Instruments;
extern CTrackClipboard g_TrackClipboard;


// ----------------------------------------------------------------------------
// Support routines



BOOL IsnotMovementVKey(int vk)
{
	//returns 1 if it is not a scroll key
	return (vk != VK_RIGHT && vk != VK_LEFT && vk != VK_UP && vk != VK_DOWN && vk != VK_TAB && vk != 13 && vk != VK_HOME && vk != VK_END && vk != VK_PAGE_UP && vk != VK_PAGE_DOWN && vk != VK_CAPITAL);
}


void GetTracklineText(char* dest, int line)
{
	if (line < 0 || line>0xff) { dest[0] = 0; return; }
	if (g_tracklinealtnumbering)
	{
		int a = line / g_trackLinePrimaryHighlight;
		if (a >= 35) a = (a - 35) % 26 + 'a' - '9' + 1;
		int b = line % g_trackLinePrimaryHighlight;
		if (b >= 35) b = (b - 35) % 26 + 'a' - '9' + 1;
		if (a <= 8)
			a = '1' + a;
		else
			a = 'A' - 9 + a;
		if (b <= 8)
			b = '1' + b;
		else
			b = 'A' - 9 + b;
		dest[0] = a;
		dest[1] = b;
		dest[2] = 0;
	}
	else
		sprintf(dest, "%02X", line);
}

//debug display of g_atarimem bytes directly, slow and terrible, do not use unless there is a purpose for it 
void GetAtariMemHexStr(int adr, int len)
{
	unsigned int a = 0;
	char c[8] = { 0 };
	memset(g_debugmem, 0, 65536);
	for (int i = 0; i < len; i++)
	{
		a = g_atarimem[adr + i];
		sprintf(c, "$%x, ", a);
		g_debugmem[i * 4] = c[0];	//$
		//force uppercase on characters "a" to "f"
		if (c[1] >= 0x61 && c[1] < 0x67) c[1] -= 0x20;
		if (c[2] >= 0x61 && c[2] < 0x67) c[2] -= 0x20;
		if (a > 0xF)	//0x10 and above
		{
			g_debugmem[(i * 4) + 1] = c[1];	//nybble 1
			g_debugmem[(i * 4) + 2] = c[2];	//nybble 2
		}
		else	//single digit hex character, add padding 0
		{
			g_debugmem[(i * 4) + 1] = '0';	//nybble 1
			g_debugmem[(i * 4) + 2] = c[1];	//nybble 2
		}
		if (i != len - 1) g_debugmem[(i * 4) + 3] = ',';
		else g_debugmem[(i * 4) + 3] = ' ';	//, or space if last character
	}
}

void CSong::SetRMTTitle()
{
	CString s, s1, s2;
	if (m_filename == "")
	{
		if (g_changes)
		{
			s = "Noname *";
		}
		else
		{	//RMT version number and author 
			s1.LoadString(IDS_RMTVERSION);
			s2.LoadString(IDS_RMTAUTHOR);
			s.Format("%s, %s", s1, s2);
		}
	}
	else
	{
		s = m_filename;
		if (g_changes) s += " *";
	}
	AfxGetApp()->GetMainWnd()->SetWindowText(s);
}

int CSong::WarnUnsavedChanges()
{
	//returns 1 upon cancelation
	if (!g_changes) return 0;
	int r = MessageBox(g_hwnd, "Save current changes?", "Current song has been changed", MB_YESNOCANCEL | MB_ICONQUESTION);
	if (r == IDCANCEL) return 1;
	if (r == IDYES)
	{
		FileSave();
		SetRMTTitle();
		if (g_changes) return 1; //failed to save or canceled
	}
	return 0;
}

/// <summary>
/// Draw a volume analyser above each track
/// </summary>
void CSong::DrawAnalyzer()
{
	if (!g_viewVolumeAnalyzer) return;	//the analyser won't be displayed without the setting enabled first

	int MINIMAL_WIDTH_TRACKS = (g_tracks4_8 > 4 && g_active_ti == PART_TRACKS) ? 1420 : 960;
	int MINIMAL_WIDTH_INSTRUMENTS = (g_tracks4_8 > 4 && g_active_ti == PART_INSTRUMENTS) ? 1220 : 1220;
	int WINDOW_OFFSET = (g_width < 1320 && g_tracks4_8 > 4 && g_active_ti == PART_TRACKS) ? -250 : 0;	//test displacement with the window size
	int INSTRUMENT_OFFSET = (g_active_ti == PART_INSTRUMENTS && g_tracks4_8 > 4) ? -250 : 0;
	if (g_tracks4_8 == 4 && g_active_ti == PART_INSTRUMENTS && g_width > MINIMAL_WIDTH_INSTRUMENTS - 220) INSTRUMENT_OFFSET = 260;
	int SONG_OFFSET = SONG_X + WINDOW_OFFSET + INSTRUMENT_OFFSET + ((g_tracks4_8 == 4) ? -200 : 310);	//displace the SONG block depending on certain parameters

	BOOL DEBUG_POKEY = 1;	//registers debug display
	BOOL DEBUG_MEMORY = 0;	//memory debug display
	BOOL DEBUG_SOUND = (g_prove == PROVE_POKEY_EXPLORER_MODE) ? 1 : 0;	//POKEY EXPLORER MODE

	if (g_width < MINIMAL_WIDTH_TRACKS && g_active_ti == PART_TRACKS) DEBUG_POKEY = DEBUG_MEMORY = 0;
	if (g_width < MINIMAL_WIDTH_INSTRUMENTS && g_active_ti == PART_INSTRUMENTS) DEBUG_POKEY = DEBUG_MEMORY = 0;

#define ANALYZER_X	(TRACKS_X+6*8+4)		// 68
#define ANALYZER_Y	(TRACKS_Y-8)			// Line 8 = 128
#define ANALYZER_S	6 
#define ANALYZER_H	5 
#define ANALYZER_HP	8 
//
#define ANALYZER2_X	(SONG_OFFSET+6*8) 
#define ANALYZER2_Y	(TRACKS_Y-128) 
#define ANALYZER2_S	1 
#define ANALYZER2_H	4 
#define ANALYZER2_HP 8 
//
#define ANALYZER3_X	(SONG_OFFSET+6*8-32) 
#define ANALYZER3_Y	(TRACKS_Y+50) 
#define ANALYZER3_S	6 
#define ANALYZER3_H	5 
#define ANALYZER3_HP 8 
//

// Draw a bridge between two columns (on the tracks view)
#define Hook1(g1, g2)																				\
	{																								\
		g_mem_dc->MoveTo(ANALYZER_X + 2 + ANALYZER_S * 15 / 2 + 16 * 8 * (g1), ANALYZER_Y - 1);		\
		g_mem_dc->LineTo(ANALYZER_X + 2 + ANALYZER_S * 15 / 2 + 16 * 8 * (g1), ANALYZER_Y - yUp);	\
		g_mem_dc->LineTo(ANALYZER_X + 2 + ANALYZER_S * 15 / 2 + 16 * 8 * (g2), ANALYZER_Y - yUp);	\
		g_mem_dc->LineTo(ANALYZER_X + 2 + ANALYZER_S * 15 / 2 + 16 * 8 * (g2), ANALYZER_Y);			\
	}

// Draw a bridge between two columns (on the instrument view)
#define Hook2(g1, g2)																				\
	{																								\
		g_mem_dc->MoveTo(ANALYZER2_X + ANALYZER2_S * 15 / 2 + 3 * 8 * (g1), ANALYZER_Y - 120 - 1);	\
		g_mem_dc->LineTo(ANALYZER2_X + ANALYZER2_S * 15 / 2 + 3 * 8 * (g1), ANALYZER_Y - 120 - yUp);\
		g_mem_dc->LineTo(ANALYZER2_X + ANALYZER2_S * 15 / 2 + 3 * 8 * (g2), ANALYZER_Y - 120 - yUp);\
		g_mem_dc->LineTo(ANALYZER2_X + ANALYZER2_S * 15 / 2 + 3 * 8 * (g2), ANALYZER_Y - 120);		\
	}

	int audf, audf2, audf3, audf16, audc, audc2, audctl, skctl, pitch, dist, vol, vol2;
	static int idx[8] = { 0xd200,0xd202,0xd204,0xd206,0xd210,0xd212,0xd214,0xd216 };	// AUDF and AUDC for mono and stereo
	static int idx2[2] = { 0xd208,0xd218 };	//AUDCTL and SKCTL
	int col[8];
	int R[8];
	int G[8];
	int yUp = 7;
	for (int i = 0; i < g_tracks4_8; i++) { col[i] = 102; R[i] = 44; G[i] = 60; }
	int a;
	int b;
	COLORREF acol;

	if (g_active_ti == PART_TRACKS) //bigger look for track edit mode
	{
		// In tracks drawing mode
		// Draw bridge connections between channels. For each connection we move 2 pixels up.
		// Max rise is 10 pixels

		// Clear the area where the analyser is to be drawn
		g_mem_dc->FillSolidRect(ANALYZER_X, ANALYZER_Y - ANALYZER_HP, g_tracks4_8 * 16 * 8 - 34, ANALYZER_H + ANALYZER_HP, RGB_BACKGROUND);

		// Left/Mono Channel
		// Draw which channels are joined by highpass filters or normal channel join
		a = g_atarimem[0xd208]; // AUDCTL @ $D208
		if (a & 0x04) { col[2] = COL_BLOCK; Hook1(0, 2); yUp -= 2; }	// High pass filter on channel 1, clocked by channel 3
		if (a & 0x02) { col[3] = COL_BLOCK;	Hook1(1, 3); yUp -= 2; }	// High pass filter on channel 3, clocked by channel 4
		if (a & 0x10) { col[0] = COL_BLOCK;	Hook1(0, 1); yUp -= 2; }	// Join channels 1 + 2 (16 bit)
		if (a & 0x08) { col[2] = COL_BLOCK;	Hook1(2, 3); yUp -= 2; }	// Join channels 3 + 4 (16 bit)

		b = g_atarimem[0xd20f]; // SKCTL @ $D20F
		if (b == 0x8b) { col[1] = COL_BLOCK; Hook1(0, 1); yUp -= 2; }	// Two tone mode (join channel 1 + 2)
		yUp = 7;

		// Stereo Channel
		a = g_atarimem[0xd218]; // AUDCTL2 @ $D218
		if (a & 0x04) { col[2 + 4] = COL_BLOCK; Hook1(0 + 4, 2 + 4); yUp -= 2; }	// High pass filter on channel 5 clocked by channel 7
		if (a & 0x02) { col[3 + 4] = COL_BLOCK; Hook1(1 + 4, 3 + 4); yUp -= 2; }	// High pass filter on channel 7, clocked by channel 8
		if (a & 0x10) { col[0 + 4] = COL_BLOCK; Hook1(0 + 4, 1 + 4); yUp -= 2; }	// Join channels 5 + 6 (16 bit)
		if (a & 0x08) { col[2 + 4] = COL_BLOCK; Hook1(2 + 4, 3 + 4); yUp -= 2; }	// Join channels 7 + 8 (16 bit)

		b = g_atarimem[0xd21f]; // SKCTL2 @ $D21F
		if (b == 0x8b) { col[1 + 4] = COL_BLOCK; Hook1(0 + 4, 1 + 4); yUp -= 2; }	// Two tone mode (join channel 5 + 6)

		for (int channelNr = 0; channelNr < g_tracks4_8; channelNr++)
		{
			audf = g_atarimem[idx[channelNr]];		// Get the frequency
			audc = g_atarimem[idx[channelNr] + 1];	// Get audio control, Bits: 0-3 = volume, 4 = Volume only, 5-7 = Distortion
			int skctl1 = g_atarimem[0xd20f];		// Two tone mode Mono
			int skctl2 = g_atarimem[0xd21f];		// Two tone mode Stereo
			
			vol = audc & 0x0f;						// Volume in lower nibble 
			a = channelNr * 16 * 8;					// X offset

			// Draw the background box of the volume analyser for this channel
			// 15 unit wide, each unit is 6 pixels (ANALYZER_S)
			// Default color is RGB(44, 60, 102) - Dark blue
			g_mem_dc->FillSolidRect(ANALYZER_X + a + 2, ANALYZER_Y, 15 * ANALYZER_S, ANALYZER_H, RGB(R[channelNr], G[channelNr], col[channelNr]));

			// Determine the color of the channels volume bar: Normal, mute or Volume only
			acol = GetChannelOnOff(channelNr) ? ((audc & 0x10) ? RGB_VOLUME_ONLY : RGB_NORMAL) : RGB_MUTE;

			// Check if its a two tone channel (1 or 5)
			if (GetChannelOnOff(channelNr) && ((skctl1 == 0x8b && channelNr == 0) || (skctl2 == 0x8b && channelNr == 4))) acol = RGB_TWO_TONE;

			// Draw the volume bar in the selected color
			if (vol) g_mem_dc->FillSolidRect(ANALYZER_X + a + 3 + (15 - vol) * ANALYZER_S / 2, ANALYZER_Y, vol * ANALYZER_S, ANALYZER_H, acol);

			// Draw the frequency and audio control numbers for this channel
			if (g_viewPokeyRegisters)
			{
				NumberMiniXY(audf, ANALYZER_X + 10 + a + 17, ANALYZER_Y - 8, TEXT_MINI_COLOR_GRAY);
				NumberMiniXY(audc, ANALYZER_X + 36 + a + 17, ANALYZER_Y - 8, TEXT_MINI_COLOR_GRAY);
			}
		}
		if (g_viewPokeyRegisters)
		{
			// Draw the AUDCTL (audio control) register value
			NumberMiniXY(g_atarimem[0xd208], ANALYZER_X + 23 + 1 * 8 * 16 + 80, ANALYZER_Y - 8);						// Mono
			if (g_tracks4_8 > 4) NumberMiniXY(g_atarimem[0xd218], ANALYZER_X + 23 + 5 * 8 * 16 + 80, ANALYZER_Y - 8);	// Stereo

			// Draw the SKCTL (Two tone control/Serial port control) register value
			NumberMiniXY(g_atarimem[0xd20f], ANALYZER_X + 23 + 1 * 8 * 16 + 80, ANALYZER_Y - 0);						// Mono
			if (g_tracks4_8 > 4) NumberMiniXY(g_atarimem[0xd21f], ANALYZER_X + 23 + 5 * 8 * 16 + 80, ANALYZER_Y - 0);	// Stereo
		}
	}
	else if (g_active_ti == PART_INSTRUMENTS) //smaller appearance for instrument edit mode
	{
		// In instrument drawing mode

		// Clear the area where the mini volume controls are to be drawn
		g_mem_dc->FillSolidRect(ANALYZER2_X, ANALYZER2_Y - ANALYZER2_HP, g_tracks4_8 * 3 * 8 - 8, ANALYZER2_H + ANALYZER2_HP, RGB_BACKGROUND);

		// Left / Mono Channel
		// Draw which channels are joined by highpass filters or normal channel join
		a = g_atarimem[0xd208]; // AUDCTL @ $D208
		if (a & 0x04) { col[2] = COL_BLOCK; Hook2(0, 2); yUp -= 2; }	// High pass filter on channel 1, clocked by channel 3
		if (a & 0x02) { col[3] = COL_BLOCK;	Hook2(1, 3); yUp -= 2; }	// High pass filter on channel 3, clocked by channel 4
		if (a & 0x10) { col[0] = COL_BLOCK;	Hook2(0, 1); yUp -= 2; }	// Join channels 1 + 2 (16 bit)
		if (a & 0x08) { col[2] = COL_BLOCK;	Hook2(2, 3); yUp -= 2; }	// Join channels 3 + 4 (16 bit)

		b = g_atarimem[0xd20f]; // SKCTL @ $D20F
		if (b == 0x8b) { col[1] = COL_BLOCK; Hook2(0, 1); yUp -= 2; }	// Two tone mode (join channel 1 + 2)
		yUp = 7;

		// Stereo Channel
		a = g_atarimem[0xd218]; // AUDCTL2 @ $D218
		if (a & 0x04) { col[2 + 4] = COL_BLOCK; Hook2(0 + 4, 2 + 4); yUp -= 2; }	// High pass filter on channel 5 clocked by channel 7
		if (a & 0x02) { col[3 + 4] = COL_BLOCK; Hook2(1 + 4, 3 + 4); yUp -= 2; }	// High pass filter on channel 7, clocked by channel 8
		if (a & 0x10) { col[0 + 4] = COL_BLOCK; Hook2(0 + 4, 1 + 4); yUp -= 2; }	// Join channels 5 + 6 (16 bit)
		if (a & 0x08) { col[2 + 4] = COL_BLOCK; Hook2(2 + 4, 3 + 4); yUp -= 2; }	// Join channels 7 + 8 (16 bit)

		b = g_atarimem[0xd21f]; // SKCTL2 @ $D21F
		if (b == 0x8b) { col[1 + 4] = COL_BLOCK; Hook2(0 + 4, 1 + 4); yUp -= 2; }	// Two tone mode (join channel 5 + 6)

		for (int channelNr = 0; channelNr < g_tracks4_8; channelNr++)
		{
			audc = g_atarimem[idx[channelNr] + 1];	// Get the frequency
			int skctl1 = g_atarimem[0xd20f];		// Two tone mode Mono
			int skctl2 = g_atarimem[0xd21f];		// Two tone mode Stereo

			vol = audc & 0x0f;						// Volume in lower nibble 

			// Draw the background box of the volume analyser for this channel
			// 15 unit wide, each unit is 6 pixels (ANALYZER_S)
			// Default color is RGB(44, 60, 102) - Dark blue
			g_mem_dc->FillSolidRect(ANALYZER2_X + channelNr * 3 * 8, ANALYZER2_Y, 15 * ANALYZER2_S, ANALYZER2_H, RGB(R[channelNr], G[channelNr], col[channelNr]));

			// Determine the color of the channels volume bar: Normal, mute or Volume only
			acol = GetChannelOnOff(channelNr) ? ((audc & 0x10) ? RGB_VOLUME_ONLY : RGB_NORMAL) : RGB_MUTE;

			// Check if its a two tone channel (1 or 5)
			if (GetChannelOnOff(channelNr) && ((skctl1 == 0x8b && channelNr == 0) || (skctl2 == 0x8b && channelNr == 4))) acol = RGB_TWO_TONE;

			// Draw the volume bar in the selected color
			if (vol) g_mem_dc->FillSolidRect(ANALYZER2_X + channelNr * 3 * 8 + (15 - vol) * ANALYZER2_S / 2, ANALYZER2_Y, vol * ANALYZER2_S, ANALYZER2_H, acol);
		}
	}
	if (DEBUG_POKEY)	// Detailed registers viewer (TODO: move to another place)
	{
		//AUDCTL bits
		BOOL CLOCK_15 = 0;	//0x01
		BOOL HPF_CH24 = 0;	//0x02
		BOOL HPF_CH13 = 0;	//0x04
		BOOL JOIN_34 = 0;	//0x08
		BOOL JOIN_12 = 0;	//0x10
		BOOL CH3_179 = 0;	//0x20
		BOOL CH1_179 = 0;	//0x40
		BOOL POLY9 = 0;	//0x80
		BOOL TWO_TONE = 0;	//0x8B

		BOOL JOIN_16BIT = 0;
		BOOL JOIN_64KHZ = 0;
		BOOL JOIN_15KHZ = 0;
		BOOL JOIN_WRONG = 0;
		BOOL REVERSE_16 = 0;
		BOOL SAWTOOTH = 0;
		BOOL SAWTOOTH_INVERTED = 0;
		BOOL CLOCK_179 = 0;

		g_mem_dc->FillSolidRect(ANALYZER3_X, ANALYZER3_Y, 680, 192, RGB_BACKGROUND);

		for (int i = 0; i < g_tracks4_8; i++)
		{
			BOOL IS_RIGHT_POKEY = (i >= 4) ? 1 : 0;

			audctl = g_atarimem[idx2[IS_RIGHT_POKEY]];
			skctl = g_atarimem[idx2[IS_RIGHT_POKEY] + 7];
			audf = g_atarimem[idx[i]];
			audc = g_atarimem[idx[i] + 1];

			vol = audc & 0x0f;
			dist = audc & 0xf0;
			pitch = audf;

			if (i % 4 == 0)								//only in valid sawtooth channels
				audf3 = g_atarimem[idx[i + 2]];

			if (i % 2 == 1)								//only in valid 16-bit channels
			{
				audf2 = g_atarimem[idx[i - 1]];
				audc2 = g_atarimem[idx[i - 1] + 1];
				vol2 = audc2 & 0x0f;
				audf16 = audf;
				audf16 <<= 8;
				audf16 += audf2;
			}

			int gap = (IS_RIGHT_POKEY) ? 64 : 0;
			int gap2 = (IS_RIGHT_POKEY) ? 96 : 0;
			a = i * 8 + gap + 16;
			int minus = (IS_RIGHT_POKEY) ? -8 : 0;
			int audnum = (i * 2) + minus;
			char s[2];
			char p[12];
			char n[4];
			double PITCH = 0;

			CLOCK_15 = audctl & 0x01;
			HPF_CH24 = audctl & 0x02;
			HPF_CH13 = audctl & 0x04;
			JOIN_34 = audctl & 0x08;
			JOIN_12 = audctl & 0x10;
			CH3_179 = audctl & 0x20;
			CH1_179 = audctl & 0x40;
			POLY9 = audctl & 0x80;
			TWO_TONE = (skctl == 0x8B) ? 1 : 0;

			//combined modes for some special output...
			SAWTOOTH = (CH1_179 && CH3_179 && HPF_CH13 && (dist == 0xA0 || dist == 0xE0) && (i == 0 || i == 4)) ? 1 : 0;
			SAWTOOTH_INVERTED = 0;
			JOIN_16BIT = ((JOIN_12 && CH1_179 && (i == 1 || i == 5)) || (JOIN_34 && CH3_179 && (i == 3 || i == 7))) ? 1 : 0;
			JOIN_64KHZ = ((JOIN_12 && !CH1_179 && !CLOCK_15 && (i == 1 || i == 5)) || (JOIN_34 && !CH3_179 && !CLOCK_15 && (i == 3 || i == 7))) ? 1 : 0;
			JOIN_15KHZ = ((JOIN_12 && !CH1_179 && CLOCK_15 && (i == 1 || i == 5)) || (JOIN_34 && !CH3_179 && CLOCK_15 && (i == 3 || i == 7))) ? 1 : 0;
			JOIN_WRONG = (((JOIN_12 && (i == 0 || i == 4)) || (JOIN_34 && (i == 2 || i == 6))) && (vol == 0x00));	//16-bit, invalid channel, no volume
			REVERSE_16 = (((JOIN_12 && (i == 0 || i == 4)) || (JOIN_34 && (i == 2 || i == 6))) && (vol > 0x00));	//16-bit, invalid channel, with volume (Reverse-16)
			CLOCK_179 = ((CH1_179 && (i == 0 || i == 4)) || (CH3_179 && (i == 2 || i == 6))) ? 1 : 0;
			if (JOIN_16BIT || CLOCK_179) CLOCK_15 = 0;	//override, these 2 take priority over 15khz mode

			int modoffset = 1;
			int coarse_divisor = 1;
			double divisor = 1;
			int v_modulo = 0;
			bool IS_VALID = 0;

			if (JOIN_16BIT) modoffset = 7;
			else if (CLOCK_179) modoffset = 4;
			else coarse_divisor = (CLOCK_15) ? 114 : 28;

			int i_audf = (JOIN_16BIT || JOIN_64KHZ || JOIN_15KHZ) ? audf16 : audf;
			PITCH = g_Tuning.generate_freq(audc, i_audf, audctl, i);
			snprintf(p, 10, "%9.2f", PITCH);

			if (g_viewPokeyRegisters)
			{
				TextMiniXY("$D200: $   $     PITCH = $     (         HZ ---  +  ), VOL = $ , DIST = $ ,", ANALYZER3_X, ANALYZER3_Y + a, TEXT_MINI_COLOR_GRAY);
				TextMiniXY("$D208: $  ", ANALYZER3_X, ANALYZER3_Y + gap2 + 48, TEXT_MINI_COLOR_GRAY);
				TextMiniXY("$D20F: $  ", ANALYZER3_X, ANALYZER3_Y + gap2 + 48 + 8, TEXT_MINI_COLOR_GRAY);

				if (CLOCK_15)	//15khz
					TextMiniXY("15KHZ", ANALYZER3_X + 8 * 76, ANALYZER3_Y + a, TEXT_MINI_COLOR_BLUE);
				else
					TextMiniXY("64KHZ", ANALYZER3_X + 8 * 76, ANALYZER3_Y + a, TEXT_MINI_COLOR_BLUE);

				if (CLOCK_179)
					TextMiniXY("1.79MHZ", ANALYZER3_X + 8 * 76, ANALYZER3_Y + a, TEXT_MINI_COLOR_BLUE);

				if (JOIN_16BIT || JOIN_64KHZ || JOIN_15KHZ)
					TextMiniXY("16-BIT", ANALYZER3_X + 8 * 76, ANALYZER3_Y + a, TEXT_MINI_COLOR_BLUE);

				/*
				if (JOIN_16BIT)
					TextMiniXY("16-BIT, 1.79MHZ", ANALYZER3_X + 8 * 76, ANALYZER3_Y + a, TEXT_MINI_COLOR_BLUE);
				else if (JOIN_64KHZ)
					TextMiniXY("16-BIT, 64KHZ", ANALYZER3_X + 8 * 76, ANALYZER3_Y + a, TEXT_MINI_COLOR_BLUE);
				else if (JOIN_15KHZ)
					TextMiniXY("16-BIT, 15KHZ", ANALYZER3_X + 8 * 76, ANALYZER3_Y + a, TEXT_MINI_COLOR_BLUE);
				*/

				/*
				if (dist == 0xC0)
				{
					int v_modulo = (CLOCK_15) ? 5 : 15;
					BOOL IS_UNSTABLE_DIST_C = ((audf + modoffset) % 5 == 0) ? 1 : 0;
					BOOL IS_BUZZY_DIST_C = ((audf + modoffset) % 3 == 0 || CLOCK_15) ? 1 : 0;
					IS_VALID = ((audf + modoffset) % v_modulo == 0) ? 0 : 1;
					if (IS_VALID)
					{
						if (IS_BUZZY_DIST_C) TextMiniXY("BUZZY", ANALYZER3_X + 8 * 84, ANALYZER3_Y + a, TEXT_MINI_COLOR_BLUE);
						else if (IS_UNSTABLE_DIST_C) TextMiniXY("UNSTABLE", ANALYZER3_X + 8 * 84, ANALYZER3_Y + a, TEXT_MINI_COLOR_BLUE);
						else TextMiniXY("GRITTY", ANALYZER3_X + 8 * 84, ANALYZER3_Y + a, TEXT_MINI_COLOR_BLUE);
					}
				}
				*/

				if (HPF_CH13)
				{
					if (SAWTOOTH && !SAWTOOTH_INVERTED)
						TextMiniXY("CH1: HIGH PASS FILTER, SAWTOOTH", ANALYZER3_X + 8 * 32, ANALYZER3_Y + gap2 + 48, TEXT_MINI_COLOR_BLUE);
					else
						if (SAWTOOTH && SAWTOOTH_INVERTED)
							TextMiniXY("CH1: HIGH PASS FILTER, SAWTOOTH (INVERTED)", ANALYZER3_X + 8 * 32, ANALYZER3_Y + gap2 + 48, TEXT_MINI_COLOR_BLUE);
						else
							TextMiniXY("CH1: HIGH PASS FILTER", ANALYZER3_X + 8 * 32, ANALYZER3_Y + gap2 + 48, TEXT_MINI_COLOR_BLUE);
				}

				if (HPF_CH24)
					TextMiniXY("CH2: HIGH PASS FILTER", ANALYZER3_X + 8 * 32, ANALYZER3_Y + gap2 + 48 + 8, TEXT_MINI_COLOR_BLUE);

				if (POLY9)
					TextMiniXY("POLY9 ENABLED", ANALYZER3_X + 8 * 11, ANALYZER3_Y + gap2 + 48, TEXT_MINI_COLOR_BLUE);

				if (TWO_TONE)
					TextMiniXY("CH1: TWO TONE FILTER", ANALYZER3_X + 8 * 11, ANALYZER3_Y + gap2 + 48 + 8, TEXT_MINI_COLOR_BLUE);

				if (REVERSE_16)
				{
					if (i == 0 || i == 4)
						TextMiniXY("CH1: REVERSE-16 OUTPUT", ANALYZER3_X + 8 * 54, ANALYZER3_Y + gap2 + 48, TEXT_MINI_COLOR_BLUE);
					else if (i == 2 || i == 6)
						TextMiniXY("CH3: REVERSE-16 OUTPUT", ANALYZER3_X + 8 * 54, ANALYZER3_Y + gap2 + 48 + 8, TEXT_MINI_COLOR_BLUE);
				}

				NumberMiniXY(audf, ANALYZER3_X + 8 * 8, ANALYZER3_Y + a, TEXT_MINI_COLOR_WHITE);
				NumberMiniXY(audc, ANALYZER3_X + 8 * 12, ANALYZER3_Y + a, TEXT_MINI_COLOR_WHITE);
				NumberMiniXY(pitch, ANALYZER3_X + 8 * 26, ANALYZER3_Y + a, TEXT_MINI_COLOR_WHITE);

				if ((JOIN_16BIT || JOIN_64KHZ || JOIN_15KHZ) && !vol2)	//16-bit without Reverse-16 output
					NumberMiniXY(audf2, ANALYZER3_X + 8 * 28, ANALYZER3_Y + a, TEXT_MINI_COLOR_WHITE);

				NumberMiniXY(vol, ANALYZER3_X + 8 * 61, ANALYZER3_Y + a, TEXT_MINI_COLOR_WHITE);
				NumberMiniXY(dist, ANALYZER3_X + 8 * 73, ANALYZER3_Y + a, TEXT_MINI_COLOR_WHITE);
				if (dist == 0xf0) TextMiniXY("e", ANALYZER3_X + 8 * 73, ANALYZER3_Y + a, TEXT_MINI_COLOR_WHITE);	//empty tile
				NumberMiniXY(audctl, ANALYZER3_X + 8 * 8, ANALYZER3_Y + gap2 + 48, TEXT_MINI_COLOR_WHITE);
				NumberMiniXY(skctl, ANALYZER3_X + 8 * 8, ANALYZER3_Y + gap2 + 48 + 8, TEXT_MINI_COLOR_WHITE);

				TextMiniXY(p, ANALYZER3_X + 8 * 32, ANALYZER3_Y + a, TEXT_MINI_COLOR_WHITE);	//pitch calculation
				TextMiniXY("$", ANALYZER3_X + 8 * 61, ANALYZER3_Y + a, TEXT_MINI_COLOR_GRAY);	//character $ to overwrite the left volume nybble
				TextMiniXY(",", ANALYZER3_X + 8 * 74, ANALYZER3_Y + a, TEXT_MINI_COLOR_GRAY);	//character , to overwrite the right distortion nybble

				sprintf(s, "%d", audnum);
				TextMiniXY(s, ANALYZER3_X + 8 * 4, ANALYZER3_Y + a, TEXT_MINI_COLOR_GRAY);		//register number

				if (IS_RIGHT_POKEY)
				{
					TextMiniXY("POKEY REGISTERS (LEFT)", ANALYZER3_X, ANALYZER3_Y, TEXT_MINI_COLOR_GRAY);
					TextMiniXY("POKEY REGISTERS (RIGHT)", ANALYZER3_X, ANALYZER3_Y + 96, TEXT_MINI_COLOR_GRAY);

					TextMiniXY("1", ANALYZER3_X + 8 * 3, ANALYZER3_Y + a, TEXT_MINI_COLOR_GRAY);
					TextMiniXY("1", ANALYZER3_X + 8 * 3, ANALYZER3_Y + gap2 + 48, TEXT_MINI_COLOR_GRAY);
					TextMiniXY("1", ANALYZER3_X + 8 * 3, ANALYZER3_Y + gap2 + 48 + 8, TEXT_MINI_COLOR_GRAY);
				}
				else TextMiniXY("POKEY REGISTERS", ANALYZER3_X, ANALYZER3_Y, TEXT_MINI_COLOR_GRAY);

				double tuning = g_basetuning;	//defined in Tuning.cpp through initialisation using input parameter
				int basenote = g_basenote;
				int reverse_basenote = (24 - basenote) % 12;	//since things are wack I had to do this
				//int FREQ_17 = (g_ntsc) ? FREQ_17_NTSC : FREQ_17_PAL;	//useful for debugging I guess
				int cycles = (g_ntsc) ? MAXSCREENCYCLES_NTSC : MAXSCREENCYCLES_PAL;
				int tracks = (g_tracks4_8 == 8) ? 8 : 4;
				char t[12] = { 0 };

				TextMiniXY("A- TUNING:       HZ,", ANALYZER3_X, ANALYZER3_Y + 8 * 9, TEXT_MINI_COLOR_GRAY);
				snprintf(t, 10, "%3.2f", tuning);
				TextMiniXY(t, ANALYZER3_X + 8 * 11, ANALYZER3_Y + 8 * 9, TEXT_MINI_COLOR_WHITE);

				n[0] = notes[reverse_basenote][0];
				n[1] = notes[reverse_basenote][1];
				n[2] = 0;

				TextMiniXY(n, ANALYZER3_X, ANALYZER3_Y + 8 * 9, TEXT_MINI_COLOR_GRAY);	//overwrite A- to the given basenote

				if (g_ntsc) TextMiniXY("NTSC", ANALYZER3_X + 8 * 21, ANALYZER3_Y + 8 * 9, TEXT_MINI_COLOR_BLUE);
				else TextMiniXY("PAL", ANALYZER3_X + 8 * 21, ANALYZER3_Y + 8 * 9, TEXT_MINI_COLOR_BLUE);

				TextMiniXY("FREQ17:        HZ, MAXSCREENCYCLES:      , G_TRACKS4_8:", ANALYZER3_X, ANALYZER3_Y + 8 * 10, TEXT_MINI_COLOR_GRAY);
				snprintf(t, 8, "%d", FREQ_17);
				TextMiniXY(t, ANALYZER3_X + 8 * 8, ANALYZER3_Y + 8 * 10, TEXT_MINI_COLOR_WHITE);
				snprintf(t, 8, "%d", cycles);
				TextMiniXY(t, ANALYZER3_X + 8 * 36, ANALYZER3_Y + 8 * 10, TEXT_MINI_COLOR_WHITE);
				snprintf(t, 2, "%d", tracks);
				TextMiniXY(t, ANALYZER3_X + 8 * 56, ANALYZER3_Y + 8 * 10, TEXT_MINI_COLOR_WHITE);

				if (DEBUG_SOUND && i == e_ch_idx)	//Debug sound, must only be run once per loops, so this prevents it being overwritten
				{
					TextMiniXY("COARSE_DIVISOR:    , DIVISOR:       , MODOFFSET:  , AUDF: $    , AUDC: $  ", ANALYZER3_X, ANALYZER3_Y + 8 * 12, 0);
					TextMiniXY("CH_IDX:  , MODULO:    , IS_VALID:  ", ANALYZER3_X, ANALYZER3_Y + 8 * 13, 0);
					TextMiniXY("         HZ = ((FREQ17 / (COARSE_DIVISOR * DIVISOR)) / (AUDF + MODOFFSET)) / 2", ANALYZER3_X, ANALYZER3_Y + 8 * 15, 0);

					//e_ch_idx = 0;				//defined manually elsewhere

					int i_audf = (JOIN_16BIT || JOIN_64KHZ || JOIN_15KHZ) ? audf16 : audf;
					int e_audf = audf;
					int e_audf2 = audf2;
					int e_audc = audc;

					e_valid = 1;				//always valid for now
					e_modulo = 0;				//does not matter right now, used in tandem with e_valid
					e_pitch = 0;				//always initialised to 0

					//always initialised to 1 to avoid a division by 0 error
					e_modoffset = 1;
					e_coarse_divisor = 1;

					//set the divisor and modoffset variables based on the AUDCTL bits currently set
					if (JOIN_16BIT) e_modoffset = 7;
					else if (CLOCK_179) e_modoffset = 4;
					else e_coarse_divisor = (CLOCK_15) ? 114 : 28;

					//identify the first Modulo value that results to 0 when used
					for (int i = 3; i < 256; i++)
					{
						e_modulo = i;
						if ((e_audf + e_modoffset) % i == 0)
							break;
					}

					e_pitch = g_Tuning.get_pitch(i_audf, e_coarse_divisor, e_divisor, e_modoffset);
					snprintf(p, 10, "%9.2f", e_pitch);
					TextMiniXY(p, ANALYZER3_X, ANALYZER3_Y + 8 * 15, 2);

					snprintf(t, 4, "%d", e_coarse_divisor);
					TextMiniXY(t, ANALYZER3_X + 8 * 16, ANALYZER3_Y + 8 * 12, 2);

					snprintf(p, 10, "%6.1f", e_divisor);
					TextMiniXY(p, ANALYZER3_X + 8 * 30, ANALYZER3_Y + 8 * 12, 2);

					snprintf(t, 4, "%d", e_modoffset);
					TextMiniXY(t, ANALYZER3_X + 8 * 49, ANALYZER3_Y + 8 * 12, 2);

					NumberMiniXY(e_audf, ANALYZER3_X + 8 * 59, ANALYZER3_Y + 8 * 12, 2);
					if (JOIN_16BIT || JOIN_64KHZ || JOIN_15KHZ)
						NumberMiniXY(e_audf2, ANALYZER3_X + 8 * 61, ANALYZER3_Y + 8 * 12, 2);

					NumberMiniXY(e_audc, ANALYZER3_X + 8 * 72, ANALYZER3_Y + 8 * 12, 2);

					snprintf(t, 4, "%d", e_ch_idx);
					TextMiniXY(t, ANALYZER3_X + 8 * 8, ANALYZER3_Y + 8 * 13, 2);

					snprintf(t, 4, "%d", e_modulo);
					TextMiniXY(t, ANALYZER3_X + 8 * 19, ANALYZER3_Y + 8 * 13, 2);

					snprintf(t, 4, "%d", e_valid);
					TextMiniXY(t, ANALYZER3_X + 8 * 34, ANALYZER3_Y + 8 * 13, 2);

				}

				if (PITCH)	//if null is read, there is nothing to show. Volume Only mode or invalid parameters may return this
				{
					if (JOIN_WRONG)	//16-bit, but wrong channels, and the volume is 0
					{
						TextMiniXY("eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee", ANALYZER3_X + 8 * 17, ANALYZER3_Y + a, TEXT_MINI_COLOR_GRAY);	//masking parts of the line,cursed patch but that works so who cares
					}
					else
					{
						char szBuffer[16];

						//most of the lines below could get some improvements...
						double centnum = 1200 * log2(PITCH / tuning);
						int notenum = (int)round(centnum * 0.01) + 60;
						int note = ((notenum + 96) - basenote) % 12;

						int octave = (((notenum + 96) - basenote) / 12) - 8;
						
						int cents = (int)round(centnum - (notenum - 60) * 100);

						snprintf(szBuffer, 4, "%03d", cents);
						TextMiniXY(szBuffer, ANALYZER3_X + 8 * 49, ANALYZER3_Y + a, TEXT_MINI_COLOR_WHITE);

						if (cents >= 0)
							TextMiniXY("+", ANALYZER3_X + 8 * 49, ANALYZER3_Y + a, TEXT_MINI_COLOR_GRAY);
						else
							TextMiniXY("-", ANALYZER3_X + 8 * 49, ANALYZER3_Y + a, TEXT_MINI_COLOR_GRAY);

						if (note < 0)
							note *= -1;	//invert the negative to prevent going out of bounds

						n[0] = notes[note][0];
						n[1] = notes[note][1];
						n[2] = 0;

						sprintf(szBuffer, "%1d", octave);
						TextMiniXY(n, ANALYZER3_X + 8 * 44, ANALYZER3_Y + a, TEXT_MINI_COLOR_WHITE);
						TextMiniXY(szBuffer, ANALYZER3_X + 8 * 46, ANALYZER3_Y + a, TEXT_MINI_COLOR_WHITE);

					}
				}
			}
		}
	}
	if (DEBUG_MEMORY)	//Atari memory display, do not use unless there is a useful purpose for it
	{
		g_mem_dc->FillSolidRect(ANALYZER3_X, ANALYZER3_Y + 192, 680 + (8 * 42), 432, RGB_BACKGROUND);

		int gap = 0; int gap2 = 32; int page = 0;

		for (int d = 0; d < 40; d++)	//1 memory page => 0x100, 32 bytes per line
		{
			//larger font...
			//GetAtariMemHexStr(0xB200 + (0x10 * d), 16);	//Distortion C page
			//TextXY(g_debugmem, ANALYZER3_X, ANALYZER3_Y + 240 + 16 * d + 8 + gap, TEXT_COLOR_WHITE);
			gap += (d % 8 == 0) ? 8 : 0;
			page += (d % 8 == 0 && d != 0) ? 1 : 0;
			gap2 = 16 * page;
			GetAtariMemHexStr(0xB000 + 0x20 * d, 32);
			TextMiniXY(g_debugmem, ANALYZER3_X, ANALYZER3_Y + 192 + 8 * d + 8 + gap + gap2, TEXT_MINI_COLOR_WHITE);

			if (d % 8 == 0)
			{
				TextMiniXY("G_ATARIMEM (      ):", ANALYZER3_X, ANALYZER3_Y + 192 + 8 * d + gap + gap2 - 8, TEXT_MINI_COLOR_GRAY);
				NumberMiniXY(page, ANALYZER3_X + 8 * 14, ANALYZER3_Y + 192 + 8 * d + gap + gap2 - 8, TEXT_MINI_COLOR_WHITE);
				TextMiniXY("0XB 00", ANALYZER3_X + 8 * 12, ANALYZER3_Y + 192 + 8 * d + gap + gap2 - 8, TEXT_MINI_COLOR_WHITE);
			}
		}
	}
}

/// <summary>
/// Draw a song's line information
///   L1 L2 L3 L4 R1 R2 R3 R4
///   00 01 02 03 -- -- -- --
/// > 04 05 06 07 -- -- -- --
///   -- -- -- -- -- -- -- --
/// Taking into account that during playback the lines can be smooth scrolled.
/// </summary>
void CSong::DrawSong()
{
	int line, i, j, k, y, t;
	char szBuffer[32], color;
	
	BOOL smooth_scroll = g_viewDoSmoothScrolling;	//TODO: make smooth scrolling an option that can be saved to .ini file

	int MINIMAL_WIDTH_INSTRUMENTS = (g_tracks4_8 > 4 && g_active_ti == PART_INSTRUMENTS) ? 1220 : 1220;
	int WINDOW_OFFSET = (g_width < 1320 && g_tracks4_8 > 4 && g_active_ti == PART_TRACKS) ? -250 : 0;	//test displacement with the window size
	int INSTRUMENT_OFFSET = (g_active_ti == PART_INSTRUMENTS && g_tracks4_8 > 4) ? -250 : 0;
	if (g_tracks4_8 == 4 && g_active_ti == PART_INSTRUMENTS && g_width > MINIMAL_WIDTH_INSTRUMENTS - 220) INSTRUMENT_OFFSET = 260;
	int SONG_OFFSET = SONG_X + WINDOW_OFFSET + INSTRUMENT_OFFSET + ((g_tracks4_8 == 4) ? -200 : 310);	//displace the SONG block depending on certain parameters

	BOOL active_smooth = (smooth_scroll && m_play && m_followplay) ? 1 : 0;	//could also be used as an offset
	int pattern_len = 0;
	int smooth_y = 0;

	if (active_smooth)
	{
		// Map the current track's playline into the number range -8 -> 7
		// This gives a Y position shift to draw the song line info
		// y_offset = line * 16 / track_length
		
		pattern_len = GetSmallestMaxtracklen(m_songplayline);
		if (!pattern_len) pattern_len = g_Tracks.GetMaxTrackLength();	//fallback to whatever is in memory instead if the value returned is invalid
		smooth_y = (active_smooth) ? (m_trackplayline * 16 / pattern_len) - 8 : 0;
		// TRACE("y offset = %d\n", smooth_y);
	}
	y = SONG_Y + (1 - active_smooth) * 16 - smooth_y;

	int linescount = (WINDOW_OFFSET) ? 5 : 9;

	for (i = 0; i < linescount + active_smooth * 2; i++, y += 16)
	{
		int linesoffset = (WINDOW_OFFSET) ? -2 : -4;
		line = m_songactiveline + i + linesoffset - active_smooth;
		BOOL isOutOfBounds = 0;

		//roll over the songline if it is out of bounds
		if (line < 0 || line > 255) 
		{ 
			line += 256; 
			line %= 256; 
			isOutOfBounds = 1; 
		}
		// Draw either "XX: -- -- -- -- ..." or "Go to line XX"

		if ((j = m_songgo[line]) >= 0)	//there is a GO to line
		{
			// Draw: "Go to line"
			color = (isOutOfBounds) ? TEXT_COLOR_DARK_GRAY : TEXT_COLOR_TURQUOISE;	//turquoise text, blank tiles to mask text if needed, else gray if out of bounds
			TextXY("GO\x1fTO\x1fLINE", SONG_OFFSET + 16, y, color);

			// Draw: "XX"
			color = (isOutOfBounds) ? TEXT_COLOR_DARK_GRAY : TEXT_COLOR_WHITE;	//white, for the number used, or gray if out of bounds
			if (line == m_songactiveline)
			{
				if (g_prove) color = (g_activepart == PART_SONG) ? COLOR_SELECTED_PROVE : TEXT_COLOR_BLUE;
				else color = (g_activepart == PART_SONG) ? COLOR_SELECTED : TEXT_COLOR_RED;
			}
			szBuffer[0] = CharH4(j);
			szBuffer[1] = CharL4(j);
			szBuffer[2] = 0;
			TextXY(szBuffer, SONG_OFFSET + 16 + 11 * 8, y, color);
		}
		else	
		{
			// Draw the line number XX: (current line = YELLOW, song line = WHITE, out of bounds = TURQUOISE)
			szBuffer[0] = CharH4(line);		// XX:
			szBuffer[1] = CharL4(line);
			szBuffer[2] = ':';
			szBuffer[3] = 0;
			color = (line == m_songplayline) ? TEXT_COLOR_YELLOW : TEXT_COLOR_WHITE;
			if (isOutOfBounds) color = TEXT_COLOR_DARK_GRAY;	//darker gray, out of bounds
			TextXY(szBuffer, SONG_OFFSET + 16, y, color);

			// For each track that is part of the song draw its number
			szBuffer[2] = 0;
			for (j = 0, k = 32; j < g_tracks4_8; j++, k += 24)
			{
				if ((t = m_song[line][j]) >= 0)
				{
					szBuffer[0] = CharH4(t);
					szBuffer[1] = CharL4(t);
				}
				else szBuffer[0] = szBuffer[1] = '-';	// No track here so draw "--"

				if (line == m_songactiveline && j == m_trackactivecol)
				{
					if (g_prove) color = (g_activepart == PART_SONG) ? COLOR_SELECTED_PROVE : TEXT_COLOR_BLUE;
					else color = (g_activepart == PART_SONG) ? COLOR_SELECTED : TEXT_COLOR_RED;
				}
				else color = (line == m_songplayline) ? TEXT_COLOR_YELLOW : TEXT_COLOR_WHITE;
				if (isOutOfBounds) color = TEXT_COLOR_DARK_GRAY;	//darker gray, out of bounds
				TextXY(szBuffer, SONG_OFFSET + 16 + k, y, color);
			}
		}
	}
	// Draw an arrow pointing to the current song line
	color = (g_prove) ? TEXT_COLOR_BLUE : TEXT_COLOR_RED;
	int arrowpos = (WINDOW_OFFSET) ? SONG_Y + 48 : SONG_Y + 80;
	TextXY("\x04\x05", SONG_OFFSET, arrowpos, color);

	if (g_tracks4_8 > 4)	//a line delimiting the boundary between left/right
	{
		int fl = 32;
		int tl = 32 + linescount * 16;
		int x = SONG_Y + 80 + 5 * 8 + 3 + SONG_OFFSET;

		g_mem_dc->MoveTo(x, fl);
		g_mem_dc->LineTo(x, tl); 
	}

	// Draw mask rectangles over the extra pixels above and below the song lines.
	// This gets rid of the pixels we dont want to see with smooth scrolling
	int width = 8 * ((g_tracks4_8 == 8) ? 30 : 18);
	int height = 32;
	g_mem_dc->FillSolidRect(SONG_OFFSET, 0, width, height, RGB_BACKGROUND);	//top
	g_mem_dc->FillSolidRect(SONG_OFFSET, linescount * 16 + 32, width, height, RGB_BACKGROUND);	//bottom

	TextXY("SONG", SONG_OFFSET + 8, SONG_Y, TEXT_COLOR_WHITE);

	//print L1 .. L4 R1 .. R4 with highlighted current track
	k = SONG_OFFSET + 6 * 8;
	szBuffer[0] = 'L';
	szBuffer[2] = 0;
	for (i = 0; i < 4; i++, k += 24)
	{
		szBuffer[1] = i + '1';	//character 1-4
		if (GetChannelOnOff(i))
		{
			if (m_trackactivecol == i) color = (g_prove) ? TEXT_COLOR_BLUE : TEXT_COLOR_RED;	//active channel highlight
			else color = TEXT_COLOR_WHITE; //normal channel
		}
		else color = TEXT_COLOR_GRAY; //switched off channels are in gray
		TextXY(szBuffer, k, SONG_Y, color);
	}
	szBuffer[0] = 'R';
	for (i = 4; i < g_tracks4_8; i++, k += 24)
	{
		szBuffer[1] = i + 49 - 4;	//character 1-4
		if (GetChannelOnOff(i))
		{
			if (m_trackactivecol == i) color = (g_prove) ? TEXT_COLOR_BLUE : TEXT_COLOR_RED;	//active channel highlight
			else color = TEXT_COLOR_WHITE; //normal channel
		}
		else color = TEXT_COLOR_GRAY; //switched off channels are in gray
		TextXY(szBuffer, k, SONG_Y, color);
	}
}

void CSong::DrawTracks()
{
	const char* tnames = "L1L2L3L4R1R2R3R4";
	char s[16], stmp[16];
	int i, x, y, tr, line, color;
	int t;

	BOOL printdebug = g_viewDebugDisplay;

	//caching certain global variables makes sure they remain the same until the function finishes drawing the tracks
	//this appears to be related to routine timing, and might actually explain why certain bugs seem to happen randomly
	int trackactiveline = m_trackactiveline;
	int trackplayline = m_trackplayline;
	int songactiveline = m_songactiveline;
	int songplayline = m_songplayline;
	int speed = m_speed;
	int speeda = m_speeda;

	//coordinates for only the TRACKS width block rendering
	int mask_x = (g_tracks4_8 == 4) ? TRACKS_X + (93 - 4 * 11) * 11 - 4 : TRACKS_X + (93 + 3) * 11 - 8;

	if (SongGetGo() >= 0)		//it's a GOTO line, it won't draw tracks
	{
		int TRACKS_OFFSET = (g_tracks4_8 == 8) ? 62 : 30;
		TextXY("GO TO LINE ", TRACKS_X + TRACKS_OFFSET * 8, TRACKS_Y + 8 * 16, TEXT_COLOR_TURQUOISE);
		if (g_prove) color = (g_activepart == PART_TRACKS) ? COLOR_SELECTED_PROVE : TEXT_COLOR_BLUE;
		else color = (g_activepart == PART_TRACKS) ? COLOR_SELECTED : TEXT_COLOR_RED;
		sprintf(s, "%02X", SongGetGo());
		TextXY(s, TRACKS_X + TRACKS_OFFSET * 8 + 11 * 8, TRACKS_Y + 8 * 16, color);
		return;
	}

	//the cursor position is alway centered regardless of the window size with this simple formula
	g_cursoractview = trackactiveline + 8 - g_line_y;

	BOOL active_smooth = (g_viewDoSmoothScrolling && m_play && m_followplay && speed > 1) ? 1 : 0;	//could also be used as an offset
	int smooth_y = (active_smooth) ? ((speeda * 16) / speed) - 8 : 0;
	if (smooth_y > 8 || smooth_y < -8) active_smooth = smooth_y = 0;	//prevents going out of bounds
	y = (TRACKS_Y + (3 - active_smooth) * 16) + smooth_y;
	x = TRACKS_X + 5 * 8;

	strcpy(s, "--\x2");	//2 digits and the "|" tile on the right side

	BOOL is_goto = 0;

	for (i = 0; i < g_tracklines + active_smooth * 2; i++, y += 16)
	{
		line = g_cursoractview + i - 8 - active_smooth;		//8 lines from above
		int oob = 0;

		int sl = songactiveline;	//offset by the oob songline counter when needed
		int ln = GetSmallestMaxtracklen(sl);

		if (line < 0)
		{
		minusline:
			oob--;
			sl = songactiveline + oob;
			if (sl < 0 || sl > 255) { sl += 256; sl %= 256; }
			ln = GetSmallestMaxtracklen(sl);

			line += ln;
			if (line < 0) goto minusline;
		}
		if (line >= ln)
		{
		plusline:
			oob++;
			line -= ln;
			sl = songactiveline + oob;
			if (sl < 0 || sl > 255) { sl += 256; sl %= 256; }
			ln = GetSmallestMaxtracklen(sl);

			if (!ln)
			{
				is_goto = 1;
				ln = g_Tracks.GetMaxTrackLength();
			}

			if (line >= ln) goto plusline;
		}

		if (g_tracklinealtnumbering)
		{
			GetTracklineText(stmp, line);
			s[0] = (stmp[1] == '1') ? stmp[0] : ' ';
			s[1] = stmp[1];
		}
		else
		{
			s[0] = CharH4(line);
			s[1] = CharL4(line);
		}

		if (is_goto)
		{
			//mask out the first line
			g_mem_dc->FillSolidRect(TRACKS_X, y, mask_x, 16, RGB_BACKGROUND);
			
			//get the songline that has the goto set
			sl = songactiveline + oob;
			if (sl < 0 || sl > 255) { sl += 256; sl %= 256; }

			//if the line is 2 patterns or more away, it must also be gray
			TextXY("GO TO LINE ", TRACKS_X + 6 * 8, y, (oob - 1) ? TEXT_COLOR_DARK_GRAY : TEXT_COLOR_TURQUOISE);
			sprintf(s, "%02X", m_songgo[sl]);
			TextXY(s, TRACKS_X + 17 * 8, y, (oob - 1) ? TEXT_COLOR_DARK_GRAY : TEXT_COLOR_WHITE);
			break;
		}

		color = TEXT_COLOR_WHITE;
		if (line % g_trackLineSecondaryHighlight == 0)  color = TEXT_COLOR_GREEN;
		if (line % g_trackLinePrimaryHighlight == 0) color = TEXT_COLOR_CYAN;
		if (line == trackplayline) color = TEXT_COLOR_YELLOW;
		if (line == trackactiveline) color = (g_prove) ? TEXT_COLOR_BLUE : TEXT_COLOR_RED;
		if (oob) color = TEXT_COLOR_DARK_GRAY;
		TextXY(s, TRACKS_X, y, color);

		for (int j = 0; j < g_tracks4_8; j++, x += 16 * 8)
		{
			//track in the current line of the song
			sl = songactiveline + oob;
			if (sl < 0 || sl > 255) { sl += 256; sl %= 256; }
			
			tr = m_song[sl][j];

			//is it playing?
			if (songplayline == songactiveline) t = trackplayline; else t = -1;
			g_Tracks.DrawTrackLine(j, x, y, tr, line, trackactiveline, g_cursoractview, t, (m_trackactivecol == j), m_trackactivecur, oob);
		}
		x = TRACKS_X + 5 * 8;
	}

	//mask rectangles for hiding extra rendered lines
	g_mem_dc->FillSolidRect(TRACKS_X - 8, TRACKS_Y + 1 * 16, mask_x, 32, RGB_BACKGROUND);
	g_mem_dc->FillSolidRect(TRACKS_X - 8, TRACKS_Y + 2 * 16 + ((g_tracklines + 1) * 16) + 1, mask_x, 48, RGB_BACKGROUND);

	//tracks
	strcpy(s, "  TRACK XX   ");
	x = TRACKS_X + 5 * 8;
	y = (TRACKS_Y + 3 * 16) + smooth_y;

	for (i = 0; i < g_tracks4_8; i++, x += 16 * 8)
	{
		s[8] = tnames[i * 2];
		s[9] = tnames[i * 2 + 1];

		color = (GetChannelOnOff(i)) ? TEXT_COLOR_WHITE : TEXT_COLOR_GRAY;	//channels off are in gray
		//TextXY(s, x + 12, TRACKS_Y, color);
		TextXY(s, x, TRACKS_Y, color);

		//track in the current line of the song
		tr = m_song[songactiveline][i];

		//g_Tracks.DrawTrackHeader(x + 24, TRACKS_Y + 16, tr, color);
		g_Tracks.DrawTrackHeader(x + 8, TRACKS_Y + 16, tr, color);
	}

	//lines delimiting the current line
	x = mask_x;
	y = TRACKS_Y + 3 * 16 - 2 + g_line_y * 16;

	g_mem_dc->MoveTo(TRACKS_X, y);
	g_mem_dc->LineTo(x, y);
	g_mem_dc->MoveTo(TRACKS_X, y + 19);
	g_mem_dc->LineTo(x, y + 19);

	//a line delimiting the boundary between left/right-- there is a bug with some tracks but the entire function needs to be rewritten anyway...
	if (g_tracks4_8 > 4)
	{
		y = (TRACKS_Y + 3 * 16);
		int line_end = y + g_tracklines * 16;

		if (is_goto)
		{
			line_end = y + (8 - g_cursoractview + GetSmallestMaxtracklen(songactiveline)) * 16 + smooth_y;
		}

		g_mem_dc->MoveTo(TRACKS_X + 50 * 11 - 3, y);
		g_mem_dc->LineTo(TRACKS_X + 50 * 11 - 3, line_end);
	}

	//mask out any extra pixels after rendering each elements
	g_mem_dc->FillSolidRect(TRACKS_X - 8, TRACKS_Y + 2 * 16, mask_x, 16, RGB_BACKGROUND);
	g_mem_dc->FillSolidRect(TRACKS_X - 8, TRACKS_Y + 2 * 16 + (g_tracklines + 1) * 16, mask_x, 32, RGB_BACKGROUND);

	//selected block
	if (g_TrackClipboard.IsBlockSelected())
	{
		x = TRACKS_X + 6 * 8 + g_TrackClipboard.m_selcol * 16 * 8 - 8;
		int xt = x + 14 * 8 + 8;

		y = (TRACKS_Y + 3 * 16) + smooth_y;
		int bfro, bto;
		g_TrackClipboard.GetFromTo(bfro, bto);

		int yf = bfro - g_cursoractview + 8;
		int fls = 0;
		int yt = bto - g_cursoractview + 8 + 1;
		int tls = 0;
		int p1 = 1, p2 = 1;

		if (yf < 0) { yf = 0; p1 = 0; fls = active_smooth * 5; }
		if (yt > g_tracklines) { yt = g_tracklines; p2 = 0;  tls = active_smooth * 7; }
		if (yf < g_tracklines && yt > 0 && g_TrackClipboard.m_seltrack == SongGetActiveTrackInColumn(g_TrackClipboard.m_selcol) && g_TrackClipboard.m_selsongline == SongGetActiveLine())
		{
			//a rectangle delimiting the selected block
			CPen redpen(PS_SOLID, 1, RGB(255, 255, 255));
			CPen* origpen = g_mem_dc->SelectObject(&redpen);

			g_mem_dc->MoveTo(x, y - 2 - fls + yf * 16);
			g_mem_dc->LineTo(x, y + 2 + tls + yt * 16);
			g_mem_dc->MoveTo(xt, y - 2 - fls + yf * 16);
			g_mem_dc->LineTo(xt, y + 2 + tls + yt * 16);

			if (p1) { g_mem_dc->MoveTo(x, y - 2 + yf * 16); g_mem_dc->LineTo(xt, y - 2 + yf * 16); }
			if (p2) { g_mem_dc->MoveTo(x, y + 2 + yt * 16); g_mem_dc->LineTo(xt + 1, y + 2 + yt * 16); }

			g_mem_dc->SelectObject(origpen);
		}

		char tx[96];
		char s1[4], s2[4];
		GetTracklineText(s1, bfro);
		GetTracklineText(s2, bto);

		//mask out any extra pixels after rendering the selection box before drawing the infos below
		if (active_smooth)
		{
			g_mem_dc->FillSolidRect(TRACKS_X - 8, TRACKS_Y + 2 * 16, mask_x, 16, RGB_BACKGROUND);
			g_mem_dc->FillSolidRect(TRACKS_X - 8, TRACKS_Y + 2 * 16 + (g_tracklines + 1) * 16, mask_x, 16, RGB_BACKGROUND);
		}

		sprintf(tx, "%i line(s) [%s-%s] selected in the pattern track %02X", bto - bfro + 1, s1, s2, g_TrackClipboard.m_seltrack);
		TextXY(tx, TRACKS_X + 4 * 8, TRACKS_Y + (4 + g_tracklines) * 16, TEXT_COLOR_WHITE);
		x = TRACKS_X + 4 * 8 + (int) strlen(tx) * 8 + 8;

		if (g_TrackClipboard.m_all)
			strcpy(tx, "[edit ALL data]");
		else
			sprintf(tx, "[edit data ONLY for instrument %02X]", m_activeinstr);
		TextXY(tx, x, TRACKS_Y + (4 + g_tracklines) * 16, TEXT_COLOR_RED);
	}

	// Debug display at the bottom of the screen, this could be toggled on if needed 
	if (g_viewDebugDisplay)
	{
		CString d;

		// Don't draw further more than what could fit on screen
		for (int i = 0; i < g_width / (16 * 6); i++)
		{
			switch (i)
			{
			case 0: d.Format("GW = %02d", g_width); break;
			case 1: d.Format("GH = %02d", g_height); break;
			case 2: d.Format("PX = %02d", g_mouseLastPointX); break;
			case 3: d.Format("PY = %02d", g_mouseLastPointY); break;
			case 4: d.Format("MB = %02d", g_mouseLastButton); break;
			case 5: d.Format("CA = %02d", g_cursoractview); break;
			case 6: d.Format("TA = %02d", m_trackactiveline); break;
			case 7: d.Format("DY = %02d", g_mouseLastPointY / 16); break;
			case 8: d.Format("GTL = %02d", g_tracklines); break;
			case 9: d.Format("OL = %02d", g_tracklines / 2); break;
			case 10: d.Format("VK = %02X", g_lastKeyPressed); break;
			case 11: d.Format("WD = %02d", g_mouseLastWheelDelta); break;
			default: continue;
			}

			TextXY(d, TRACKS_X + i * (16 * 6), g_height - 32, TEXT_COLOR_TURQUOISE);
		}
	}
}

void CSong::DrawInstrument()
{
	g_Instruments.DrawInstrument(m_activeinstr);
}

/// <summary>
/// Draw song/play information:
/// Line 1: Time  BPM  PAL/NTSC  Highlight  FPS
/// Line 2: Song name
/// Line 3: Music Speed   MaxTrackLength   Mono/Stereo
/// Line 4: Edit/Jam/Midi/Explorer mode    Octave
/// Line 5: Instrument                     Volume
/// Line 6: Instrument flags
/// </summary>
void CSong::DrawInfo()
{
	char szBuffer[80];
	int i, color;
	BOOL selected = FALSE;
	is_editing_infos = 0;

	BOOL printdebug = g_viewDebugDisplay;

	// Line 1: Time  BPM  PAL/NTSC  Hightlight (XX/XX)  FPS
	TextXY((g_ntsc) ? "NTSC" : "PAL", INFO_X + 33 * 8, INFO_Y_LINE_1, TEXT_COLOR_TURQUOISE);

	// 2x Line highlights XX/XX (go and override --)
	TextXY("HIGHLIGHT: --/--", 344, INFO_Y_LINE_1, TEXT_COLOR_WHITE);
	color = g_prove ? COLOR_SELECTED_PROVE : COLOR_SELECTED;

	sprintf(szBuffer, "%02X", g_trackLinePrimaryHighlight);
	selected = (g_activepart == PART_INFO && m_infoact == INFO_ACTIVE_1ST_HIGHLIGHT) ? TRUE : FALSE;
	TextXY(szBuffer, 344 + 11 * 8, INFO_Y_LINE_1, (selected) ? color : TEXT_COLOR_TURQUOISE);

	sprintf(szBuffer, "%02X", g_trackLineSecondaryHighlight);
	selected = (g_activepart == PART_INFO && m_infoact == INFO_ACTIVE_2ND_HIGHLIGHT) ? TRUE : FALSE;
	TextXY(szBuffer, 344 + 14 * 8, INFO_Y_LINE_1, (selected) ? color : TEXT_COLOR_TURQUOISE);

	if (printdebug)
	{
		// A poor attempt at an FPS counter
		snprintf(szBuffer, 16, "%1.2f FPS", last_fps);
		TextXY(szBuffer, 560 - 9 * 8, INFO_Y_LINE_1, TEXT_COLOR_TURQUOISE);
	}

	// Line 2: Name
	if (g_activepart == PART_INFO && m_infoact == INFO_ACTIVE_NAME) //info? && edit name?
	{
		is_editing_infos = 1;
		i = m_songnamecur;
		color = g_prove ? TEXT_COLOR_BLUE : TEXT_COLOR_RED;
	}
	else
	{
		i = -1;
		color = TEXT_COLOR_TURQUOISE;
	}
	TextXY("NAME:", INFO_X, INFO_Y_LINE_2, TEXT_COLOR_WHITE);
	TextXYSelN(m_songname, i, INFO_X + 6 * 8, INFO_Y_LINE_2, color);

	// Line 3: Speed (XX/XX/X)  MaxTrackLength (XX)  (Mono/Stereo)
	TextXY("MUSIC SPEED: --/--/-    MAXTRACKLENGTH: --", INFO_X, INFO_Y_LINE_3, TEXT_COLOR_WHITE);

	// 3x Speed indicators XX/XX/X (go and override --)
	color = g_prove ? COLOR_SELECTED_PROVE : COLOR_SELECTED;

	sprintf(szBuffer, "%02X", m_speed);
	selected = (g_activepart == PART_INFO && m_infoact == INFO_ACTIVE_SPEED) ? TRUE : FALSE;
	TextXY(szBuffer, INFO_X + 13 * 8, INFO_Y_LINE_3, (selected) ? color : TEXT_COLOR_TURQUOISE);

	sprintf(szBuffer, "%02X", m_mainSpeed);
	selected = (g_activepart == PART_INFO && m_infoact == INFO_ACTIVE_MAIN_SPEED) ? TRUE : FALSE;
	TextXY(szBuffer, INFO_X + 16 * 8, INFO_Y_LINE_3, (selected) ? color : TEXT_COLOR_TURQUOISE);

	sprintf(szBuffer, "%X", m_instrumentSpeed);
	selected = (g_activepart == PART_INFO && m_infoact == INFO_ACTIVE_INSTR_SPEED) ? TRUE : FALSE;
	TextXY(szBuffer, INFO_X + 19 * 8, INFO_Y_LINE_3, (selected) ? color : TEXT_COLOR_TURQUOISE);

	// Max Track Length @ 40 chars
	sprintf(szBuffer, "%02X", g_Tracks.GetMaxTrackLength());
	TextXY(szBuffer, INFO_X + 40 * 8, INFO_Y_LINE_3, TEXT_COLOR_TURQUOISE);

	// Mono or Stereo @ 46 chars
	TextXY((g_tracks4_8 == 4) ? "MONO-4-TRACKS" : "STEREO-8-TRACKS", INFO_X + 46 * 8, INFO_Y_LINE_3, TEXT_COLOR_TURQUOISE);

	// Line 4: (Mode)  Octive (X-X)
	int xpos = INFO_X;
	int ypos = INFO_Y_LINE_4;
	if (g_prove == PROVE_POKEY_EXPLORER_MODE)	// test mode exclusive to keyboard input for sound debugging, this cannot be set by accident unless I did something stupid
		TextXY("EXPLORER MODE (PITCH CALCULATIONS)", xpos, ypos, TEXT_COLOR_TURQUOISE);
	else if (g_prove == PROVE_MIDI_CH15_MODE)	// test mode exclusive from MIDI CH15 inputs, this cannot be set by accident unless I did something stupid
		TextXY("EXPLORER MODE (MIDI CH15)", xpos, ypos, TEXT_COLOR_TURQUOISE);
	else if (g_prove > PROVE_EDIT_MODE)
		TextXY((g_prove == PROVE_JAM_MONO_MODE) ? "JAM MODE (MONO)" : "JAM MODE (STEREO)", xpos, ypos, TEXT_COLOR_BLUE);
	else
		TextXY("EDIT MODE", xpos, ypos, TEXT_COLOR_RED);

	sprintf(szBuffer, "OCTAVE %i-%i", m_octave + 1, m_octave + 2);
	szBuffer[6] = 0;
	TextXY(szBuffer, INFO_X + 55 * 8, INFO_Y_LINE_4, TEXT_COLOR_WHITE);
	TextXY(szBuffer + 7, INFO_X + 62 * 8, INFO_Y_LINE_4, TEXT_COLOR_TURQUOISE);

	// Line 5: Instrument (XX): (name)
	TextXY("INSTRUMENT", INFO_X, INFO_Y_LINE_5, TEXT_COLOR_WHITE);
	sprintf(szBuffer, "%02X: %s", m_activeinstr, g_Instruments.GetName(m_activeinstr));
	szBuffer[3] = 0;
	TextXY(szBuffer, INFO_X + 11 * 8, INFO_Y_LINE_5, TEXT_COLOR_WHITE);
	szBuffer[40] = 0;
	TextXY(szBuffer + 4, INFO_X + 15 * 8, INFO_Y_LINE_5, TEXT_COLOR_TURQUOISE);

	sprintf(szBuffer, "%cVOLUME %X", g_respectvolume ? '*' : ' ', m_volume);	// Put a * infront of Volume if the RESPECT volume mode is on
	szBuffer[7] = 0;
	TextXY(szBuffer, INFO_X + 56 * 8, INFO_Y_LINE_5, TEXT_COLOR_WHITE);
	TextXY(szBuffer + 8, INFO_X + 64 * 8, INFO_Y_LINE_5, TEXT_COLOR_TURQUOISE);

	// Line 6: Under the instrument line draw small text indicating the instrument flags
	BYTE flag = g_Instruments.GetFlag(m_activeinstr);

	int x = INFO_X;
	const int y = INFO_Y_LINE_6;
	int activeChannel = (m_trackactivecol % 4) + 1;		// channel 1 to 4

	if (flag & IF_FILTER)
	{
		if (activeChannel > 2)
		{
			TextMiniXY("NO FILTER", x, y, TEXT_MINI_COLOR_GRAY);
			x += 10 * 8;
		}
		else
		{
			if (activeChannel == 1)
				TextMiniXY("AUTOFILTER(1+3)", x, y, TEXT_MINI_COLOR_BLUE);
			else
				TextMiniXY("AUTOFILTER(2+4)", x, y, TEXT_MINI_COLOR_BLUE);
			x += 16 * 8;
		}
	}

	if (flag & IF_BASS16)
	{
		if (activeChannel == 2)
		{
			TextMiniXY("BASS16(2+1)", x, y, TEXT_MINI_COLOR_BLUE);
			x += 12 * 8;
		}
		else if (activeChannel == 4)
		{
			TextMiniXY("BASS16(4+3)", x, y, TEXT_MINI_COLOR_BLUE);
			x += 12 * 8;
		}
		else
		{
			TextMiniXY("NO BASS16", x, y, TEXT_MINI_COLOR_GRAY);;
			x += 10 * 8;
		}
	}

	if (flag & IF_PORTAMENTO)
	{
		TextMiniXY("PORTAMENTO", x, y, TEXT_MINI_COLOR_BLUE);
		x += 11 * 8;
	}

	if (flag & IF_AUDCTL)
	{
		int audctl = g_Instruments.GetParameter(m_activeinstr, PAR_AUDCTL_15KHZ)
			| g_Instruments.GetParameter(m_activeinstr, PAR_AUDCTL_HPF_CH2) << 1
			| g_Instruments.GetParameter(m_activeinstr, PAR_AUDCTL_HPF_CH1) << 2
			| g_Instruments.GetParameter(m_activeinstr, PAR_AUDCTL_JOIN_3_4) << 3
			| g_Instruments.GetParameter(m_activeinstr, PAR_AUDCTL_JOIN_1_2) << 4
			| g_Instruments.GetParameter(m_activeinstr, PAR_AUDCTL_179_CH3) << 5
			| g_Instruments.GetParameter(m_activeinstr, PAR_AUDCTL_179_CH1) << 6
			| g_Instruments.GetParameter(m_activeinstr, PAR_AUDCTL_POLY9) << 7;
		sprintf(szBuffer, "AUDCTL:%02X", audctl);
		TextMiniXY(szBuffer, x, y, TEXT_MINI_COLOR_BLUE);
		// x += 6 * 8;
	}
}

/// <summary>
/// Draw the song play time and BPM
/// TODO: Merge with DrawInfo(), both are displayed in the same area
/// </summary>
/// <param name="pDC"></param>
//void CSong::DrawPlayTimeCounter(CDC* pDC)
void CSong::DrawPlayTimeCounter()
{
	if (!g_viewPlayTimeCounter) return;	//the timer won't be displayed without the setting enabled first

#define PLAYTC_X	16		//(SONG_OFFSET+7)
#define PLAYTC_Y	16		//(SONG_Y-8) 
#define PLAYTC_W	(32*8)	//(4*8)  
#define PLAYTC_H	16		//8 

	int fps = (g_ntsc) ? 60 : 50;
	int ts = g_playtime / fps;							//total time in seconds
	int timesec = ts % 60;								//seconds 0 to 59
	int timemin = ts / 60;								//minutes 0 to ...
	int timemilisec = (g_playtime % fps) * 100 / fps;	//miliseconds 0 to 99
	double speed = 0.0;
	double bpm = 0.0;
	char timstr[16] = { 0 };
	char bpmstr[8] = { 0 };

	m_avgspeed[m_trackplayline % 8] = m_speed;				//refreshed every 8 rows
	for (int i = 0; i < 8; i++) speed += m_avgspeed[i];
	speed /= 8.0;											//average speed
	bpm = ((60.0 * fps) / g_trackLinePrimaryHighlight) / speed;	//average BPM 

	snprintf(timstr, 16, !(timesec & 1) ? "%2d:%02d.%02d" : "%2d %02d.%02d", timemin, timesec, timemilisec);
	snprintf(bpmstr, 8, (m_play) ? "%1.2f" : "0.00", bpm);

	TextXY("TIME:             BPM:", PLAYTC_X, PLAYTC_Y, TEXT_COLOR_WHITE);
	TextXY(timstr, PLAYTC_X + 8 * 6, PLAYTC_Y, (m_play) ? TEXT_COLOR_WHITE : TEXT_COLOR_GRAY);
	TextXY(bpmstr, PLAYTC_X + 8 * 23, PLAYTC_Y, (m_play) ? TEXT_COLOR_WHITE : TEXT_COLOR_GRAY);

	//if (pDC) pDC->BitBlt( SCALE(PLAYTC_X), SCALE(PLAYTC_Y), SCALE(PLAYTC_W), SCALE(PLAYTC_H), g_mem_dc, SCALE(PLAYTC_X), SCALE(PLAYTC_Y), SRCCOPY);
}


BOOL CSong::InfoKey(int vk, int shift, int control)
{
	int i, num;
	int volatile* infptab[] = { &m_speed, &m_mainSpeed, &m_instrumentSpeed, &g_trackLinePrimaryHighlight, &g_trackLineSecondaryHighlight };
	int infandtab[] = { 0xFF, 0xFF, 0x08, g_Tracks.GetMaxTrackLength() / 2, g_Tracks.GetMaxTrackLength() / 2 };	//maximum current speed, main speed, instrument speed, primary and secondary line highlights
	int volatile& infp = *infptab[m_infoact - 1];
	int infand = infandtab[m_infoact - 1];
	BOOL CAPSLOCK = GetKeyState(20);	//VK_CAPS_LOCK

	if (m_infoact == INFO_ACTIVE_NAME)
	{
		is_editing_infos = 1;
		if (vk == VK_DIVIDE || vk == VK_MULTIPLY || vk == VK_ADD || vk == VK_SUBTRACT) goto edit_ok;	//a workaround so the Octave and Volume can be set anywhere
		if (((!CAPSLOCK && shift) || (CAPSLOCK && !shift)) && (vk == VK_LEFT || vk == VK_RIGHT)) goto edit_ok;	//a workaround so the active instrument can be set anywhere
		if (IsnotMovementVKey(vk))
		{	//saves undo only if it is not cursor movement
			g_Undo.ChangeInfo(0, UETYPE_INFODATA);
		}
		if (EditText(vk, shift, control, m_songname, m_songnamecur, SONG_NAME_MAX_LEN)) m_infoact = INFO_ACTIVE_SPEED;
		return 1;
	}

	if ((num = NumbKey(vk)) >= 0 && num <= infand)
	{
		if (m_infoact >= INFO_ACTIVE_SPEED && m_infoact <= INFO_ACTIVE_INSTR_SPEED)
		{
			i = infp & 0x0f; //lower digit (hex)
			if (infand < 0x0f)
			{
				if (num <= infand) i = num;
			}
			else
				i = ((i << 4) | num) & infand;
		}
		//couldn't quite get decimal to work yet... 
		else if (m_infoact == INFO_ACTIVE_1ST_HIGHLIGHT || m_infoact == INFO_ACTIVE_2ND_HIGHLIGHT)
		{
			//if (num > 9) return 0;	//must only accept characters between 0 and 9
			//i = infp & 9; //lower digit (dec)
			//if (infand < 9)
			i = infp & 0x0f; //lower digit (hex)
			if (infand < 0x0f)
			{
				if (num <= infand) i = num;
			}
			else
			{
				i = (i << 4) | num;
				if (i > infand) i = infand;
				//i = ((i * 10) | num) & infand;
				//if (i > infand) i = infand;
			}

		} 
		if (i <= 0) i = 1;	//all values must be at least 1
		g_Undo.ChangeInfo(0, UETYPE_INFODATA);
		infp = i;
		return 1;
	}
edit_ok:
	switch (vk)
	{
		case VK_TAB:
			if (control) break;	//do nothing
			if (shift)
			{
				m_infoact = INFO_ACTIVE_NAME;	//Shift+TAB => Name
				is_editing_infos = 1;
			}
			else
			{
				if (m_infoact < INFO_ACTIVE_2ND_HIGHLIGHT) m_infoact++; else m_infoact = INFO_ACTIVE_SPEED; //TAB => Speed variables 1, 2 or 3, or line highlights 4 or 5 
				is_editing_infos = 0;
			}
			return 1;

		case VK_UP:
			if (control && shift) break;	//do nothing
			if (control) goto IncrementInfoPar;
			break;

		case VK_DOWN:
			if (control && shift) break;	//do nothing
			if (control) goto DecrementInfoPar;
			break;

		case VK_LEFT:
		{
			if (control && shift) break;	//do nothing
			if (control)
			{
			DecrementInfoPar:
				i = infp;
				i--;
				if (i <= 0) i = infand; //value must be at least 1, roll back to the maximum defined earlier 
				g_Undo.ChangeInfo(0, UETYPE_INFODATA);
				infp = i;
			}
			else if (!CAPSLOCK && shift || (CAPSLOCK && !shift && is_editing_infos) || (CAPSLOCK && shift && !is_editing_infos))
			{
				ActiveInstrPrev();
			}
			else
			{
				if (m_infoact > INFO_ACTIVE_SPEED)
				{
					m_infoact--;
					if (m_infoact == INFO_ACTIVE_1ST_HIGHLIGHT - 1) m_infoact = INFO_ACTIVE_2ND_HIGHLIGHT;
				}
				else
				{
					m_infoact = INFO_ACTIVE_INSTR_SPEED;
				}
			}
		}
		return 1;

		case VK_RIGHT:
		{
			if (control && shift) break;	//do nothing
			if (control)
			{
			IncrementInfoPar:
				i = infp;
				i++;
				if (i > infand) i = 1;	//value must be at least 1
				g_Undo.ChangeInfo(0, UETYPE_INFODATA);
				infp = i;
			}
			else if (!CAPSLOCK && shift || (CAPSLOCK && !shift && is_editing_infos) || (CAPSLOCK && shift && !is_editing_infos))
			{
				ActiveInstrNext();
			}
			else
			{
				if (m_infoact >= INFO_ACTIVE_SPEED && m_infoact < INFO_ACTIVE_1ST_HIGHLIGHT)
				{
					m_infoact++;
					if (m_infoact > INFO_ACTIVE_INSTR_SPEED) m_infoact = INFO_ACTIVE_SPEED;
				}
				else if (m_infoact >= INFO_ACTIVE_1ST_HIGHLIGHT)
				{
					m_infoact++;
					if (m_infoact > INFO_ACTIVE_2ND_HIGHLIGHT) m_infoact = INFO_ACTIVE_1ST_HIGHLIGHT;
				}
			}
		}
		return 1;

		case 13:		//VK_ENTER
			g_activepart = g_active_ti;
			return 1;

		case VK_MULTIPLY:
		{
			OctaveUp();
			return 1;
		}
		break;

		case VK_DIVIDE:
		{
			OctaveDown();
			return 1;
		}
		break;

		case VK_ADD:
		{
			VolumeUp();
			return 1;
		}

		case VK_SUBTRACT:
		{
			VolumeDown();
			return 1;
		}

	}
	return 0;
}

BOOL CSong::InstrKey(int vk, int shift, int control)
{
	//note: if returning 1, then screenupdate is done in RmtView
	TInstrument* ai = g_Instruments.GetInstrument(m_activeinstr);
	int& ap = ai->parameters[ai->editParameterNr];
	int& ae = ai->envelope[ai->editEnvelopeX][ai->editEnvelopeY];
	int& at = ai->noteTable[ai->editNoteTableCursorPos];
	int i;

	BOOL CAPSLOCK = GetKeyState(20);	//VK_CAPS_LOCK

	if (!control && !shift && NumbKey(vk) >= 0)
	{
		if (ai->activeEditSection == INSTRUMENT_SECTION_PARAMETERS) //parameters
		{
			int pmax = shpar[ai->editParameterNr].maxParameterValue;
			int pfrom = shpar[ai->editParameterNr].displayOffset;
			if (NumbKey(vk) > pmax + pfrom) return 0;
			i = ap + pfrom;
			i &= 0x0f; //lower digit
			if (pmax + pfrom > 0x0f)
			{
				i = (i << 4) | NumbKey(vk);
				if (i > pmax + pfrom)
					i &= 0x0f;		//leaves only the lower digit
			}
			else
			{
				if (NumbKey(vk) >= pfrom) i = NumbKey(vk);
			}
			i -= pfrom;
			if (i < 0) i = 0;
			g_Undo.ChangeInstrument(m_activeinstr, 0, UETYPE_INSTRDATA);
			ap = i;
			goto ChangeInstrumentPar;
		}
		else if (ai->activeEditSection == INSTRUMENT_SECTION_ENVELOPE) //envelope
		{
			int eand = shenv[ai->editEnvelopeY].pand;
			int num = NumbKey(vk);
			i = num & eand;
			if (i != num) return 0; //something else came out after and number pressed out of range
			g_Undo.ChangeInstrument(m_activeinstr, 0, UETYPE_INSTRDATA);
			ae = i;
			//shift to the right
			i = ai->editEnvelopeX;
			if (i < ai->parameters[PAR_ENV_LENGTH]) i++;	// else i=0; //length of env
			ai->editEnvelopeX = i;
			goto ChangeInstrumentEnv;
		}
		else if (ai->activeEditSection == INSTRUMENT_SECTION_NOTETABLE) //table
		{
			int num = NumbKey(vk);
			i = ((at << 4) | num) & 0xff;
			g_Undo.ChangeInstrument(m_activeinstr, 0, UETYPE_INSTRDATA);
			at = i;
			goto ChangeInstrumentTab;
		}
	}

	//for name, parameters, envelope and table
	switch (vk)
	{
		case VK_TAB:
			if (control) break;	//do nothing
			if (ai->activeEditSection == INSTRUMENT_SECTION_NAME) break;	//is editing text
			if (shift)
			{
				ai->activeEditSection = INSTRUMENT_SECTION_NAME;	//Shift+TAB => Name
				g_isEditingInstrumentName = 1;
			}
			else
			{
				if (ai->activeEditSection < INSTRUMENT_SECTION_NOTETABLE)
					ai->activeEditSection++;
				else 
					ai->activeEditSection = INSTRUMENT_SECTION_PARAMETERS; //TAB 1,2,3
				g_isEditingInstrumentName = 0;
			}
			g_Undo.Separator();
			return 1;

		case VK_LEFT:
			if (!control && (!CAPSLOCK && shift || (CAPSLOCK && !shift && g_isEditingInstrumentName) || (CAPSLOCK && shift && !g_isEditingInstrumentName)))
			{
				ActiveInstrPrev();
				return 1;
			}
			break;

		case VK_RIGHT:
			if (!control && (!CAPSLOCK && shift || (CAPSLOCK && !shift && g_isEditingInstrumentName) || (CAPSLOCK && shift && !g_isEditingInstrumentName)))
			{
				ActiveInstrNext();
				return 1;
			}
			break;

		case VK_UP:
		case VK_DOWN:
			if (CAPSLOCK && shift) break;

			if (shift && !control) return 0;	//the combination Shift + Control + UP / DOWN is enabled for edit ENVELOPE and TABLE
			if (shift && control && ai->activeEditSection == INSTRUMENT_SECTION_PARAMETERS) return 0;	//except for edit PARAM, is not allowed there
			break;

		case VK_MULTIPLY:
		{
			OctaveUp();
			return 1;
		}
		break;

		case VK_DIVIDE:
		{
			OctaveDown();
			return 1;
		}
		break;

		case VK_SUBTRACT:	//Numlock minus
			if (shift && control)	//S+C+numlock_minus  ...reading the whole curve with a minimum of 0
			{
				g_Undo.ChangeInstrument(m_activeinstr, 0, UETYPE_INSTRDATA);
				BOOL br = 0, bl = 0;
				if (ai->activeEditSection == INSTRUMENT_SECTION_ENVELOPE && ai->editEnvelopeY == ENV_VOLUMER) br = 1;
				else
					if (ai->activeEditSection == INSTRUMENT_SECTION_ENVELOPE && ai->editEnvelopeY == ENV_VOLUMEL) bl = 1;
					else
						br = bl = 1;
				for (int i = 0; i <= ai->parameters[PAR_ENV_LENGTH]; i++)
				{
					if (br) { if (ai->envelope[i][ENV_VOLUMER] > 0) ai->envelope[i][ENV_VOLUMER]--; }
					if (bl) { if (ai->envelope[i][ENV_VOLUMEL] > 0) ai->envelope[i][ENV_VOLUMEL]--; }
				}
				goto ChangeInstrumentEnv;
			}
			else
				VolumeDown();
			return 1;
			break;

		case VK_ADD:		//Numlock plus
			if (shift && control)	//S+C+numlock_plus ...applying the whole curve with maximum f
			{
				g_Undo.ChangeInstrument(m_activeinstr, 0, UETYPE_INSTRDATA);
				BOOL br = 0, bl = 0;
				if (ai->activeEditSection == INSTRUMENT_SECTION_ENVELOPE && ai->editEnvelopeY == ENV_VOLUMER) br = 1;
				else
					if (ai->activeEditSection == INSTRUMENT_SECTION_ENVELOPE && ai->editEnvelopeY == ENV_VOLUMEL) bl = 1;
					else
						br = bl = 1;
				for (int i = 0; i <= ai->parameters[PAR_ENV_LENGTH]; i++)
				{
					if (br) { if (ai->envelope[i][ENV_VOLUMER] < 0x0f) ai->envelope[i][ENV_VOLUMER]++; }
					if (bl) { if (ai->envelope[i][ENV_VOLUMEL] < 0x0f) ai->envelope[i][ENV_VOLUMEL]++; }
				}
				goto ChangeInstrumentEnv;
			}
			else
				VolumeUp();
			return 1;
			break;
	}

	//and now only for special parts
	if (ai->activeEditSection == INSTRUMENT_SECTION_NAME)
	{
		g_isEditingInstrumentName = 1;
		//NAME
		if (IsnotMovementVKey(vk))
		{	//saves undo only if it is not cursor movement
			g_Undo.ChangeInstrument(m_activeinstr, 0, UETYPE_INSTRDATA);
		}
		if (EditText(vk, shift, control, ai->name, ai->editNameCursorPos, INSTRUMENT_NAME_MAX_LEN)) ai->activeEditSection = 1;

		return 1;
	}
	else if (ai->activeEditSection == INSTRUMENT_SECTION_PARAMETERS)
	{
		// Parameter section is active

		g_isEditingInstrumentName = 0;
		switch (vk)
		{
			case VK_UP:
				if (control) goto ParameterInc;
				ai->editParameterNr = shpar[ai->editParameterNr].gotoUp;
				return 1;

			case VK_DOWN:
				if (control) goto ParameterDec;
				ai->editParameterNr = shpar[ai->editParameterNr].gotoDown;
				return 1;

			case VK_LEFT:
				if (control)
				{
					// Change the parameter value
ParameterDec:
					g_Undo.ChangeInstrument(m_activeinstr, 0, UETYPE_INSTRDATA);
					int v = ap - 1;
					if (v < 0) v = shpar[ai->editParameterNr].maxParameterValue;
					ap = v;
					goto ChangeInstrumentPar;
				}
				else
				{
					// Move to the next parameter
					ai->editParameterNr = shpar[ai->editParameterNr].gotoLeft;
				}
				return 1;

			case VK_RIGHT:
				if (control)
				{
					// Change the parameter value
ParameterInc:
					g_Undo.ChangeInstrument(m_activeinstr, 0, UETYPE_INSTRDATA);
					int v = ap + 1;
					if (v > shpar[ai->editParameterNr].maxParameterValue) v = 0;
					ap = v;
					goto ChangeInstrumentPar;
				}
				else
				{
					// Move to the next parameter
					ai->editParameterNr = shpar[ai->editParameterNr].gotoRight;
				}
				return 1;

			case VK_HOME:
				ai->editParameterNr = PAR_ENV_LENGTH;
				return 1;

			case VK_SPACE:
				if (control) break;	//prevents inputing a SPACE while exiting PROVE mode
			case VK_BACK:	//BACKSPACE
			case VK_DELETE:
				g_Undo.ChangeInstrument(m_activeinstr, 0, UETYPE_INSTRDATA);
				ap = 0;
				goto ChangeInstrumentPar;
				return 1;

ChangeInstrumentPar:
				//because there has been some change in the instrument parameter => stop this instrument in all channels
                CAtari::InstrumentTurnOff(m_activeinstr);
				g_Instruments.CheckInstrumentParameters(m_activeinstr);
				g_Instruments.WasModified(m_activeinstr);
				return 1;
		}
	}
	else if (ai->activeEditSection == INSTRUMENT_SECTION_ENVELOPE)
	{
		g_isEditingInstrumentName = 0;
		//ENVELOPE
		switch (vk)
		{
			case VK_UP:
				if (control)
				{	//
					if (shift)	//SHIFT+CONTROL+UP
					{
						g_Undo.ChangeInstrument(m_activeinstr, 0, UETYPE_INSTRDATA);
						for (int i = 0; i <= ai->parameters[PAR_ENV_LENGTH]; i++)
						{
							int& ae = ai->envelope[i][ai->editEnvelopeY];
							ae = (ae + shenv[ai->editEnvelopeY].padd) & shenv[ai->editEnvelopeY].pand;
						}
						goto ChangeInstrumentEnv;
					}
					goto EnvelopeInc;
				}
				i = ai->editEnvelopeY;
				if (i > 0)
				{
					i--;
					if (i == 0 && g_tracks4_8 <= 4) i = 7;	//mono mode
				}
				else
					i = 7;
				ai->editEnvelopeY = i;
				return 1;

			case VK_DOWN:
				if (control)
				{	//
					if (shift)	//SHIFT+CONTROL+DOWN
					{
						g_Undo.ChangeInstrument(m_activeinstr, 0, UETYPE_INSTRDATA);
						for (int i = 0; i <= ai->parameters[PAR_ENV_LENGTH]; i++)
						{
							int& ae = ai->envelope[i][ai->editEnvelopeY];
							ae = (ae + shenv[ai->editEnvelopeY].psub) & shenv[ai->editEnvelopeY].pand;
						}
						goto ChangeInstrumentEnv;
					}
					goto EnvelopeDec;
				}
				i = ai->editEnvelopeY;
				if (i < 7) i++; else i = (g_tracks4_8 > 4) ? 0 : 1;
				ai->editEnvelopeY = i;
				return 1;

			case VK_LEFT:
				if (control)
				{
				EnvelopeDec:
					g_Undo.ChangeInstrument(m_activeinstr, 0, UETYPE_INSTRDATA);
					ae = (ae + shenv[ai->editEnvelopeY].psub) & shenv[ai->editEnvelopeY].pand;
					goto ChangeInstrumentEnv;
				}
				else
				{
					i = ai->editEnvelopeX;
					if (i > 0) i--; else i = ai->parameters[PAR_ENV_LENGTH]; //length of env
					ai->editEnvelopeX = i;
				}
				return 1;

			case VK_RIGHT:
				if (control)
				{
				EnvelopeInc:
					g_Undo.ChangeInstrument(m_activeinstr, 0, UETYPE_INSTRDATA);
					ae = (ae + shenv[ai->editEnvelopeY].padd) & shenv[ai->editEnvelopeY].pand;
					goto ChangeInstrumentEnv;
				}
				else
				{
					i = ai->editEnvelopeX;
					if (i < ai->parameters[PAR_ENV_LENGTH]) i++; else i = 0; //length of env
					ai->editEnvelopeX = i;
				}
				return 1;

			case VK_HOME:
				if (control)
				{
					g_Undo.ChangeInstrument(m_activeinstr, 0, UETYPE_INSTRDATA);
					ai->parameters[PAR_ENV_GOTO] = ai->editEnvelopeX; //sets ENVGO to this column
					goto ChangeInstrumentPar;	//yes, that's fine, it really changed the PARAMETER, even if it's in the envelope
				}
				else
				{
					//goes left to column 0 or to the beginning of the GO loop
					if (ai->editEnvelopeX != 0)
						ai->editEnvelopeX = 0;
					else
						ai->editEnvelopeX = ai->parameters[PAR_ENV_GOTO];
				}
				return 1;

			case VK_END:
				if (control)
				{
					g_Undo.ChangeInstrument(m_activeinstr, 0, UETYPE_INSTRDATA);
					if (ai->editEnvelopeX == ai->parameters[PAR_ENV_LENGTH])	//sets ENVLEN to this column or to the end
						ai->parameters[PAR_ENV_LENGTH] = ENVELOPE_MAX_COLUMNS - 1;
					else
						ai->parameters[PAR_ENV_LENGTH] = ai->editEnvelopeX;
					goto ChangeInstrumentPar;	//yes, changed PAR from envelope
				}
				else
				{
					ai->editEnvelopeX = ai->parameters[PAR_ENV_LENGTH];	//moves the cursor to the right to the end
				}
				return 1;

			case 8:			//VK_BACKSPACE:
				g_Undo.ChangeInstrument(m_activeinstr, 0, UETYPE_INSTRDATA);
				ae = 0;
				goto ChangeInstrumentEnv;
				return 1;

			case VK_SPACE:	//VK_SPACE
				if (control) break;	//prevents inputing a SPACE while exiting PROVE mode
				{
					g_Undo.ChangeInstrument(m_activeinstr, 0, UETYPE_INSTRDATA);
					for (int j = 0; j < ENVROWS; j++) ai->envelope[ai->editEnvelopeX][j] = 0;
					if (ai->editEnvelopeX < ai->parameters[PAR_ENV_LENGTH]) ai->editEnvelopeX++; //shift to the right
				}
				goto ChangeInstrumentEnv;
				return 1;

			case VK_INSERT:
				if (!control)
				{	//moves the envelope from the current position to the right
					g_Undo.ChangeInstrument(m_activeinstr, 0, UETYPE_INSTRDATA);
					int i, j;
					int ele = ai->parameters[PAR_ENV_LENGTH];
					int ego = ai->parameters[PAR_ENV_GOTO];
					if (ele < ENVELOPE_MAX_COLUMNS - 1) ele++;
					if (ai->editEnvelopeX < ego && ego < ENVELOPE_MAX_COLUMNS - 1) ego++;
					for (i = ENVELOPE_MAX_COLUMNS - 2; i >= ai->editEnvelopeX; i--)
					{
						for (j = 0; j < ENVROWS; j++) ai->envelope[i + 1][j] = ai->envelope[i][j];
					}
					//improvement: with shift it will leave it there (it will not erase the column)
					if (!shift) for (j = 0; j < ENVROWS; j++) ai->envelope[ai->editEnvelopeX][j] = 0;
					ai->parameters[PAR_ENV_LENGTH] = ele;
					ai->parameters[PAR_ENV_GOTO] = ego;
					goto ChangeInstrumentPar;	//changed length and / or go parameters
				}
				return 0; //without screen update

			case VK_DELETE:
				if (!control)	//!shift &&
				{	//moves the envelope from the current position to the left
					g_Undo.ChangeInstrument(m_activeinstr, 0, UETYPE_INSTRDATA);
					int i, j;
					int ele = ai->parameters[PAR_ENV_LENGTH];
					int ego = ai->parameters[PAR_ENV_GOTO];
					if (ele > 0)
					{
						ele--;
						for (i = ai->editEnvelopeX; i < ENVELOPE_MAX_COLUMNS - 1; i++)
						{
							for (j = 0; j < ENVROWS; j++) ai->envelope[i][j] = ai->envelope[i + 1][j];
						}
						for (j = 0; j < ENVROWS; j++) ai->envelope[ENVELOPE_MAX_COLUMNS - 1][j] = 0;
					}
					else
					{
						for (j = 0; j < ENVROWS; j++) ai->envelope[0][j] = 0;
					}
					if (ai->editEnvelopeX < ego) ego--;
					if (ego > ele) ego = ele;
					ai->parameters[PAR_ENV_GOTO] = ego;
					ai->parameters[PAR_ENV_LENGTH] = ele;
					if (ai->editEnvelopeX > ele) ai->editEnvelopeX = ele;
					goto ChangeInstrumentPar;	//changed length and / or go parameters
				}
				return 0; //without screen update

			ChangeInstrumentEnv:
				//something changed => Save instrument to Atari memory
				g_Instruments.WasModified(m_activeinstr);
				return 1;
		}
	}
	if (ai->activeEditSection == INSTRUMENT_SECTION_NOTETABLE)
	{
		g_isEditingInstrumentName = 0;
		//TABLE
		switch (vk)
		{
			case VK_HOME:
				if (control)
				{	//set a TABLE go loop here
					g_Undo.ChangeInstrument(m_activeinstr, 0, UETYPE_INSTRDATA);
					ai->parameters[PAR_TBL_GOTO] = ai->editNoteTableCursorPos;
					if (ai->editNoteTableCursorPos > ai->parameters[PAR_TBL_LENGTH]) ai->parameters[PAR_TBL_LENGTH] = ai->editNoteTableCursorPos;
					goto ChangeInstrumentPar;
				}
				else
				{
					//go to the beginning of the TABLE and to the beginning of the TABLE loop
					if (ai->editNoteTableCursorPos != 0)
						ai->editNoteTableCursorPos = 0;
					else
						ai->editNoteTableCursorPos = ai->parameters[PAR_TBL_GOTO];
				}
				return 1;

			case VK_END:
				if (control)
				{	//set TABLE only by location
					g_Undo.ChangeInstrument(m_activeinstr, 0, UETYPE_INSTRDATA);
					if (ai->editNoteTableCursorPos == ai->parameters[PAR_TBL_LENGTH])
						ai->parameters[PAR_TBL_LENGTH] = NOTE_TABLE_MAX_LEN - 1;
					else
						ai->parameters[PAR_TBL_LENGTH] = ai->editNoteTableCursorPos;
					goto ChangeInstrumentPar;
				}
				else	//goes to the last parameter in the TABLE
					ai->editNoteTableCursorPos = ai->parameters[PAR_TBL_LENGTH];
				return 1;

			case VK_UP:
				if (control)
				{
					if (shift)	//Shift+Control+UP
					{
						g_Undo.ChangeInstrument(m_activeinstr, 0, UETYPE_INSTRDATA);
						for (int i = 0; i <= ai->parameters[PAR_TBL_LENGTH]; i++) ai->noteTable[i] = (ai->noteTable[i] + 1) & 0xff;
						goto ChangeInstrumentTab;
					}
				TableInc:
					g_Undo.ChangeInstrument(m_activeinstr, 0, UETYPE_INSTRDATA);
					at = (at + 1) & 0xff;
					goto ChangeInstrumentTab;
				}
				return 1;

			case VK_DOWN:
				if (control)
				{
					if (shift)	//Shift+Control+DOWN
					{
						g_Undo.ChangeInstrument(m_activeinstr, 0, UETYPE_INSTRDATA);
						for (int i = 0; i <= ai->parameters[PAR_TBL_LENGTH]; i++) ai->noteTable[i] = (ai->noteTable[i] - 1) & 0xff;
						goto ChangeInstrumentTab;
					}
				TableDec:
					g_Undo.ChangeInstrument(m_activeinstr, 0, UETYPE_INSTRDATA);
					at = (at - 1) & 0xff;
					goto ChangeInstrumentTab;
				}
				return 1;

			case VK_LEFT:
				if (control) goto TableDec;
				i = ai->editNoteTableCursorPos - 1;
				if (i < 0) i = ai->parameters[PAR_TBL_LENGTH];
				ai->editNoteTableCursorPos = i;
				goto ChangeInstrumentTab;

			case VK_RIGHT:
				if (control) goto TableInc;
				i = ai->editNoteTableCursorPos + 1;
				if (i > ai->parameters[PAR_TBL_LENGTH]) i = 0;
				ai->editNoteTableCursorPos = i;
				goto ChangeInstrumentTab;

			case VK_SPACE:	//VK_SPACE: parameter reset and shift by 1 to the right
				if (control) break;	//prevents inputing a SPACE while exiting PROVE mode
				if (ai->editNoteTableCursorPos < ai->parameters[PAR_TBL_LENGTH]) ai->editNoteTableCursorPos++;
				//and proceeds the same as VK_BACKSPACE
			case 8:			//VK_BACKSPACE: parameter reset
				g_Undo.ChangeInstrument(m_activeinstr, 0, UETYPE_INSTRDATA);
				at = 0;
				goto ChangeInstrumentTab;

			case VK_INSERT:
				if (!control)
				{	//moves the table from the current position to the right
					g_Undo.ChangeInstrument(m_activeinstr, 0, UETYPE_INSTRDATA);
					int i;
					int tle = ai->parameters[PAR_TBL_LENGTH];
					int tgo = ai->parameters[PAR_TBL_GOTO];
					if (tle < NOTE_TABLE_MAX_LEN - 1) tle++;
					if (ai->editNoteTableCursorPos < tgo && tgo < NOTE_TABLE_MAX_LEN - 1) tgo++;
					for (i = NOTE_TABLE_MAX_LEN - 2; i >= ai->editNoteTableCursorPos; i--) ai->noteTable[i + 1] = ai->noteTable[i];
					if (!shift) ai->noteTable[ai->editNoteTableCursorPos] = 0; //with the shift it will leave there
					ai->parameters[PAR_TBL_LENGTH] = tle;
					ai->parameters[PAR_TBL_GOTO] = tgo;
					//goto ChangeInstrumentTab; <-- It is not enough!
					goto ChangeInstrumentPar; //changed TABLE LEN or GO, must stop the instrument
				}
				return 0; //without screen update

			case VK_DELETE:
				if (!control) //!shift &&
				{	//moves the table from the current position to the left
					g_Undo.ChangeInstrument(m_activeinstr, 0, UETYPE_INSTRDATA);
					int i;
					int tle = ai->parameters[PAR_TBL_LENGTH];
					int tgo = ai->parameters[PAR_TBL_GOTO];
					if (tle > 0)
					{
						tle--;
						for (i = ai->editNoteTableCursorPos; i < NOTE_TABLE_MAX_LEN - 1; i++) ai->noteTable[i] = ai->noteTable[i + 1];
						ai->noteTable[NOTE_TABLE_MAX_LEN - 1] = 0;
					}
					else
						ai->noteTable[0] = 0;
					if (ai->editNoteTableCursorPos < tgo) tgo--;
					if (tgo > tle) tgo = tle;
					ai->parameters[PAR_TBL_LENGTH] = tle;
					ai->parameters[PAR_TBL_GOTO] = tgo;
					if (ai->editNoteTableCursorPos > tle) ai->editNoteTableCursorPos = tle;
					//goto ChangeInstrumentTab; <-- It is not enough!
					goto ChangeInstrumentPar; //changed TABLE LEN or GO, must stop the instrument
				}
				return 0; //without screen update

			ChangeInstrumentTab:
				//something changed => Save instrument to Atari memory
				g_Instruments.WasModified(m_activeinstr);
				return 1;

		}
	}
	return 0;	//=> SCREENUPDATE will not be performed
}

BOOL CSong::InfoCursorGotoSongname(int x)
{
	x = x / 8;
	if (x >= 0 && x < SONG_NAME_MAX_LEN)
	{
		m_songnamecur = x;
		g_activepart = PART_INFO;
		m_infoact = INFO_ACTIVE_NAME;
		is_editing_infos = 1;	//Song Name is being edited
		return 1;
	}
	return 0;
}

BOOL CSong::InfoCursorGotoSpeed(int x)
{
	x = (x - 4) / 8;
	if (x < 2) m_infoact = INFO_ACTIVE_SPEED;
	else if (x < 5) m_infoact = INFO_ACTIVE_MAIN_SPEED;
	else m_infoact = INFO_ACTIVE_INSTR_SPEED;
	g_activepart = PART_INFO;
	is_editing_infos = 0;	//Song Speed is being edited
	return 1;
}

BOOL CSong::InfoCursorGotoHighlight(int x)
{
	x = (x - 4) / 8;
	if (x < 2) m_infoact = INFO_ACTIVE_1ST_HIGHLIGHT;
	else m_infoact = INFO_ACTIVE_2ND_HIGHLIGHT;
	g_activepart = PART_INFO;
	is_editing_infos = 0;	//Song Highlight is being edited
	return 1;
}

BOOL CSong::InfoCursorGotoOctaveSelect(int x, int y)
{
	COctaveSelectDlg dlg;
	CRect rec;
	::GetWindowRect(g_viewhwnd, &rec);
	dlg.m_pos = rec.TopLeft() + CPoint(x - 64 - 9, y - 7);
	if (dlg.m_pos.x < 0) dlg.m_pos.x = 0;
	dlg.m_octave = m_octave;
	g_mousebutt = 0;				//because the dialog sessions of the OnLbuttonUP event
	if (dlg.DoModal() == IDOK)
	{
		m_octave = dlg.m_octave;
		return 1;
	}
	return 0;
}

BOOL CSong::InfoCursorGotoVolumeSelect(int x, int y)
{
	CVolumeSelectDlg dlg;
	CRect rec;
	::GetWindowRect(g_viewhwnd, &rec);
	dlg.m_pos = rec.TopLeft() + CPoint(x - 64 - 9, y - 7);
	if (dlg.m_pos.x < 0) dlg.m_pos.x = 0;
	dlg.m_volume = m_volume;
	dlg.m_respectvolume = g_respectvolume;

	g_mousebutt = 0;				//because the dialog sessions of the OnLbuttonUP event
	if (dlg.DoModal() == IDOK)
	{
		m_volume = dlg.m_volume;
		g_respectvolume = dlg.m_respectvolume;
		return 1;
	}
	return 0;
}

BOOL CSong::InfoCursorGotoInstrumentSelect(int x, int y)
{
	g_isEditingInstrumentName = 0;
	CInstrumentSelectDlg dlg;
	CRect rec;
	::GetWindowRect(g_viewhwnd, &rec);
	dlg.m_pos = rec.TopLeft() + CPoint(x - 64 - 82, y - 7);
	if (dlg.m_pos.x < 0) dlg.m_pos.x = 0;
	dlg.m_selected = m_activeinstr;

	g_mousebutt = 0;				//because the dialog sessions of the OnLbuttonUP event
	if (dlg.DoModal() == IDOK)
	{
		ActiveInstrSet(dlg.m_selected);
		return 1;
	}
	return 0;
}

BOOL CSong::CursorToSpeedColumn()
{
	if (g_activepart != PART_TRACKS || SongGetActiveTrack() < 0) return 0;
	BLOCKDESELECT;
	m_trackactivecur = 3;
	return 1;
}

BOOL CSong::ProveKey(int vk, int shift, int control)
{
	int note, i;
	note = NoteKey(vk);

	if (g_prove == PROVE_POKEY_EXPLORER_MODE)	//POKEY EXPLORER MODE: FULL CONTROL OVER THE POKEY (IGNORE RMT ROUTINES EXCEPT SETPOKEY)
	{
		//trackn_audf => g_atarimem[0x3178]
		//trackn_audc => g_atarimem[0x3180]
		//v_audctl => g_atarimem[0x3C69]
		//v_skctl => g_atarimem[0x3CD3]

		int audf = 0x3178;
		int audc = 0x3180;
		int audctl = 0x3C69;
		int skctl = 0x3CD3;

		int ch = 0;	//channel separation used in registers write
		int step = (g_shiftkey) ? 0x10 : 0x01;	//when the SHIFT key is held, increments/decrements are in steps are $10

		switch (vk)
		{
			//General variables manipulation

			case 13:	//VK_ENTER
				e_ch_idx++;
				if (e_ch_idx > 3)
					e_ch_idx = 0;
				break;

			case 8:		//VK_BACKSPACE
				e_ch_idx--;
				if (e_ch_idx < 0)
					e_ch_idx = 3;
				break;

			case VK_OEM_PLUS:
				e_divisor += (g_shiftkey) ? 1 : 0.1;
				if (e_divisor > 10000)
					e_divisor = 10000;
				break;

			case VK_OEM_MINUS:
				e_divisor -= (g_shiftkey) ? 1 : 0.1;
				if (e_divisor < 1)
					e_divisor = 1;
				break;

				//AUDF channels

			case 0x31:	//VK_1
				ch = 0;
				g_atarimem[audf + ch] += step;
				break;

			case 0x51:	//VK_Q
				ch = 0;
				g_atarimem[audf + ch] -= step;
				break;

			case 0x33:	//VK_3
				ch = 1;
				g_atarimem[audf + ch] += step;
				break;

			case 0x45:	//VK_E
				ch = 1;
				g_atarimem[audf + ch] -= step;
				break;

			case 0x35:	//VK_5
				ch = 2;
				g_atarimem[audf + ch] += step;
				break;

			case 0x54:	//VK_T
				ch = 2;
				g_atarimem[audf + ch] -= step;
				break;

			case 0x37:	//VK_7
				ch = 3;
				g_atarimem[audf + ch] += step;
				break;

			case 0x55:	//VK_U
				ch = 3;
				g_atarimem[audf + ch] -= step;
				break;

				//AUDC channels

			case 0x32:	//VK_2
				ch = 0;
				g_atarimem[audc + ch] += step;
				break;

			case 0x57:	//VK_W
				ch = 0;
				g_atarimem[audc + ch] -= step;
				break;

			case 0x34:	//VK_4
				ch = 1;
				g_atarimem[audc + ch] += step;
				break;

			case 0x52:	//VK_R
				ch = 1;
				g_atarimem[audc + ch] -= step;
				break;

			case 0x36:	//VK_6
				ch = 2;
				g_atarimem[audc + ch] += step;
				break;

			case 0x59:	//VK_Y
				ch = 2;
				g_atarimem[audc + ch] -= step;
				break;

			case 0x38:	//VK_8
				ch = 3;
				g_atarimem[audc + ch] += step;
				break;

			case 0x49:	//VK_I
				ch = 3;
				g_atarimem[audc + ch] -= step;
				break;

				//AUDCTL bits

			case 0x50:	//VK_P
				g_atarimem[audctl] ^= 0x80;
				break;

			case 0x41:	//VK_A
				g_atarimem[audctl] ^= 0x40;
				break;

			case 0x44:	//VK_D
				g_atarimem[audctl] ^= 0x20;
				break;

			case 0x4A:	//VK_J
				g_atarimem[audctl] ^= 0x10;
				break;

			case 0x4B:	//VK_K
				g_atarimem[audctl] ^= 0x08;
				break;

			case 0x46:	//VK_F
				g_atarimem[audctl] ^= 0x04;
				break;

			case 0x47:	//VK_G
				g_atarimem[audctl] ^= 0x02;
				break;

			case 0x43:	//VK_C
				g_atarimem[audctl] ^= 0x01;
				break;

				//SKCTL Two-Tone toggle

			case 0x4D:	//VK_M
				g_atarimem[skctl] ^= 0x88;
				break;

			default:
				return 0;

		}
		return 1;

	}

	if (note >= 0)
	{
		i = note + m_octave * 12;
		if (i >= 0 && i < NOTESNUM)		//only within limits
		{
			SetPlayPressedTonesTNIV(m_trackactivecol, i, m_activeinstr, m_volume);
			if ((control || g_prove == PROVE_JAM_STEREO_MODE) && g_tracks4_8 > 4)
			{	
				//with control or in prove2 => stereo test
				SetPlayPressedTonesTNIV((m_trackactivecol + 4) & 0x07, i, m_activeinstr, m_volume);
			}
		}
		return 0; //they don't have to redraw
	}

	if (SongGetGo() >= 0) //is active song go to line => they must not edit anything
	{
		if (!control && (vk == VK_UP || vk == VK_PAGE_UP))  //GO - key up
		{
			m_trackactiveline = 0;
			if (!g_linesafter) TrackUp(1);
			else TrackUp(g_linesafter);
			return 1;
		}
		if (!control && (vk == VK_DOWN || vk == VK_PAGE_DOWN)) //GO - key down
		{
			m_trackactiveline = g_Tracks.GetMaxTrackLength() - 1;
			TrackDown(1, 0);
			return 1;
		}
	}

	switch (vk)
	{
		case VK_LEFT:
			if (control) break;	//do nothing
			if (shift)
				ActiveInstrPrev();
			else if (g_activepart != 1)	//anywhere but tracks
				TrackLeft(1);
			else
				TrackLeft();
			break;

		case VK_RIGHT:
			if (control) break;	//do nothing
			if (shift)
				ActiveInstrNext();
			else if (g_activepart != 1)	//anywhere but tracks
				TrackRight(1);
			else
				TrackRight();
			break;

		case VK_UP:
			if (shift) break;	//do nothing
			if (control || g_activepart != 1)	//anywhere but tracks
			{
				SongUp();
			}
			else
			{
				if (!g_linesafter) TrackUp(1);
				else TrackUp(g_linesafter);
			}
			break;

		case VK_DOWN:
			if (shift) break;	//do nothing
			if (control || g_activepart != 1)	//anywhere but tracks
			{
				SongDown();
			}
			else
			{
				if (!g_linesafter) TrackDown(1, 0);
				else TrackDown(g_linesafter, 0);	//stoponlastline = 0 => will not stop on the last line of the track
			}
			break;

		case VK_TAB:
			if (shift)
				TrackLeft(1); //SHIFT+TAB
			else if (control)
				CursorToSpeedColumn(); //CTRL+TAB
			else
				TrackRight(1);
			break;

		case VK_SPACE:
			if (control) break;	//prevents inputing a SPACE while exiting PROVE mode
			break;

		case VK_SUBTRACT:
			VolumeDown();
			break;

		case VK_ADD:
			VolumeUp();
			break;

		case VK_DIVIDE:
			OctaveDown();
			break;

		case VK_MULTIPLY:
			OctaveUp();
			break;

		case VK_PAGE_UP:
			if (g_activepart != 1)
			{
				if (shift)
					SongSubsongPrev();
				else
				{
					SongUp();
				}
				break;
			}
			else
				if (g_activepart == 1)
				{
					if (!shift && control)
					{
						SongUp();
					}
					else
						if (!control && shift)
						{
							//move to the previous goto
							SongSubsongPrev();
						}
					if (m_play && m_followplay) break;	//prevents moving at all during play+follow
					else
					{
						if (m_trackactiveline > 0)
						{
							m_trackactiveline = ((m_trackactiveline - 1) / g_trackLinePrimaryHighlight) * g_trackLinePrimaryHighlight;
						}
					}
				}
			break;

		case VK_PAGE_DOWN:
			if (g_activepart != 1)
			{
				if (shift)
					SongSubsongNext();
				else
				{
					SongDown();
				}
				break;
			}
			else
				if (g_activepart == 1)
				{
					if (!shift && control)
					{
						SongDown();
					}
					else
						if (!control && shift)
						{
							//move to the next goto
							SongSubsongNext();
						}
					if (m_play && m_followplay) break;	//prevents moving at all during play+follow
					else
					{
						m_trackactiveline = ((m_trackactiveline + g_trackLinePrimaryHighlight) / g_trackLinePrimaryHighlight) * g_trackLinePrimaryHighlight;
						if (m_trackactiveline > GetSmallestMaxtracklen(m_songactiveline) - 1)
							m_trackactiveline -= g_trackLinePrimaryHighlight;
					}
				}
			break;

		case VK_HOME:
			if (control || shift) break; //do nothing
			if (g_activepart == 1)	//tracks
				m_trackactiveline = 0;		//line 0
			else if (g_activepart == 3)	//song lines
				m_songactiveline = 0;
			break;

		case VK_END:
			if (control || shift) break; //do nothing
			if (g_activepart == 1)	//tracks
			{
				if (TrackGetGoLine() >= 0)
					m_trackactiveline = g_Tracks.GetMaxTrackLength() - 1; //last line
				else
					m_trackactiveline = TrackGetLastLine();	//end line
				if (m_trackactiveline < 0) m_trackactiveline = g_Tracks.GetMaxTrackLength() - 1; //failsafe in case the active line is out of bounds
			}
			else if (g_activepart == 3)	//song lines
			{
				int i, j, la = 0;
				for (j = 0; j < SONGLEN; j++)
				{
					for (i = 0; i < g_tracks4_8; i++) if (m_song[j][i] >= 0) { la = j; break; }
					if (m_songgo[j] >= 0) la = j;
				}
				m_songactiveline = la;
			}
			break;

		case 13:		//VK_ENTER:
			if (g_activepart == 1)
			{
				int instr, vol;
				if ((BOOL)control != (BOOL)g_keyboard_swapenter)	//control+Enter => plays a whole line (all tracks)
				{
					//for all track columns except the active track column
					for (i = 0; i < g_tracks4_8; i++)
					{
						if (i != m_trackactivecol)
						{
							TrackGetLoopingNoteInstrVol(m_song[m_songactiveline][i], note, instr, vol);
							if (note >= 0)		//is there a note?
								SetPlayPressedTonesTNIV(i, note, instr, vol);	//it will lose it as it is there
							else
								if (vol >= 0) //there is no note, but is there a separate volume?
									SetPlayPressedTonesV(i, vol);				//adjust the volume as it is
						}
					}
				}
				//and now for that active track column
				TrackGetLoopingNoteInstrVol(SongGetActiveTrack(), note, instr, vol);
				if (note >= 0)		//is there a note?
				{
					SetPlayPressedTonesTNIV(m_trackactivecol, note, instr, vol);	//it will lose it as it is there
				}
				else
					if (vol >= 0) //there is no note, but is there a separate volume?
					{
						SetPlayPressedTonesV(m_trackactivecol, vol); //adjust the volume
					}
				TrackDown(1, 0);	//move down 1 step always
			}
			else
				if (g_activepart != 1)
				{
					g_activepart = g_active_ti;
					return 1;
				}
			break;

		default:
			return 0;
			break;
	}
	return 1;
}


BOOL CSong::TrackKey(int vk, int shift, int control)
{
//
#define VKX_SONGINSERTLINE	73		//VK_I
#define VKX_SONGDELETELINE	85		//VK_U
#define VKX_SONGDUPLICATELINE 79	//VK_O
#define VKX_SONGPREPARELINE 80		//VK_P
#define VKX_SONGPUTNEWTRACK 78		//VK_N
#define VKX_SONGMAKETRACKSDUPLICATE 68	//VK_D
//
	int note, i, j;

	if (g_TrackClipboard.IsBlockSelected() && SongGetActiveTrack() != g_TrackClipboard.m_seltrack) BLOCKDESELECT;

	if (SongGetGo() >= 0) //is active song go to line => they must not edit anything
	{
		if (!control && (vk == VK_UP || vk == VK_PAGE_UP))  //GO - key up
		{
			m_trackactiveline = 0;	//always assume it went from line 0
			if (!g_linesafter) TrackUp(1);
			else TrackUp(g_linesafter);
			return 1;
		}
		if (!control && (vk == VK_DOWN || vk == VK_PAGE_DOWN)) //GO - key down
		{
			m_trackactiveline = g_Tracks.GetMaxTrackLength() - 1;	//always reset to line 0
			TrackDown(1, 0);
			return 1;
		}
		if (!control && !shift) return 0;
		if (control && (vk == 8 || vk == 71)) //control+backspace or control+G
		{
			SongTrackGoOnOff();
			return 1;
		}
		if (control && !shift && (vk == VKX_SONGINSERTLINE || vk == VKX_SONGDELETELINE || vk == VKX_SONGPREPARELINE || vk == VKX_SONGDUPLICATELINE || vk == VK_PAGE_UP || vk == VK_PAGE_DOWN)) goto TrackKeyOk;
		if (vk != VK_LEFT && vk != VK_RIGHT && vk != VK_UP && vk != VK_DOWN) return 0;
	}
TrackKeyOk:

	switch (m_trackactivecur)
	{
		case 0: //note column
			if (control) break;		//with control, notes are not entered (break continues)
			note = NoteKey(vk);
			if (note >= 0)
			{
			insertnotes:
				i = note + m_octave * 12;
				if (i >= 0 && i < NOTESNUM)		//only within limits
				{
					BLOCKDESELECT;
					//Quantization
					if (m_play && m_followplay && (m_speeda < (m_speed / 2)))
					{
						m_quantization_note = i;
						m_quantization_instr = m_activeinstr;
						m_quantization_vol = m_volume;
						return 1;
					}
					//end Quantization
					if (TrackSetNoteActualInstrVol(i))
					{
						SetPlayPressedTonesTNIV(m_trackactivecol, i, m_activeinstr, TrackGetVol());
						if (!(m_play && m_followplay)) TrackDown(g_linesafter);
					}
				}
				return 1;
			}
			else //the numbers 1-6 on the numeral are overwritten by an octave
				if ((j = Numblock09Key(vk)) >= 1 && j <= 6 && m_trackactiveline <= TrackGetLastLine())
				{
					note = TrackGetNote();
					if (note >= 0)		//is there a note?
					{
						BLOCKDESELECT;
						note = (note % 12) + ((j - 1) * 12);		//changes its octave according to the number pressed on the numblock
						if (note >= 0 && note < NOTESNUM)
						{
							int instr = TrackGetInstr(), vol = TrackGetVol();
							if (TrackSetNoteInstrVol(note, instr, vol))
								SetPlayPressedTonesTNIV(m_trackactivecol, note, instr, vol);
						}
					}
					if (!(m_play && m_followplay)) TrackDown(g_linesafter);
					return 1;
				}
			break;

		case 1: //instrument column
			i = NumbKey(vk);
			note = NoteKey(vk);	//workaround: the note key is known early in case it is needed
			if (i >= 0 && !shift && !control)
			{
				BLOCKDESELECT;
				if (TrackGetNote() >= 0) //the instrument number can only be changed if there is a note
				{
					j = ((TrackGetInstr() & 0x0f) << 4) | i;
					if (j >= INSTRSNUM) j &= 0x0f;	//leaves only the lower digit
					TrackSetInstr(j);
				}
				else goto testnotevalue;	//attempt to catch a fail by testing the other possible condition anyway
				return 1;
			}
			else if (note >= 0 && !shift && !control)
			{
			testnotevalue:
				BLOCKDESELECT;
				if (TrackGetNote() >= 0) break; //do not input a note if there is already a note!
				else goto insertnotes;	//force a note insertion otherwise
			}
			break;

		case 2: //volume column
			i = NumbKey(vk);
			if (i >= 0 && !shift && !control)
			{
				BLOCKDESELECT;
				if (TrackSetVol(i) && !(m_play && m_followplay)) TrackDown(g_linesafter);
				return 1;
			}
			break;

		case 3: //speed column
			i = NumbKey(vk);
			if (i >= 0 && !shift && !control)
			{
				BLOCKDESELECT;
				j = TrackGetSpeed();
				if (j < 0) j = 0;
				j = ((j & 0x0f) << 4) | i;
				if (j >= TRACKMAXSPEED) j &= 0x0f;	//leaves only the lower digit
				if (j <= 0) j = -1;	//zero does not exist
				TrackSetSpeed(j);
				return 1;
			}
			break;

	}

	switch (vk)
	{
		case VK_UP:
			if (control && shift)
			{
				if (!g_TrackClipboard.IsBlockSelected())
				{	//if no block is selected, make a block at the current location
					g_TrackClipboard.BlockSetBegin(m_trackactivecol, SongGetActiveTrack(), m_trackactiveline);
					g_TrackClipboard.BlockSetEnd(m_trackactiveline);
				}
				//volume change incrementing
				g_Undo.ChangeTrack(SongGetActiveTrack(), m_trackactiveline, UETYPE_TRACKDATA);
				g_TrackClipboard.BlockVolumeChange(m_activeinstr, 1);
			}
			else
				if (shift && !control)
				{
					//block selection
					BLOCKSETBEGIN;
					if (!g_linesafter) TrackUp(1);
					else TrackUp(g_linesafter);
					BLOCKSETEND;
				}
				else
					if (control && !shift)
					{
						if (ISBLOCKSELECTED)
						{
							BLOCKDESELECT;
							break;
						}
						else SongUp();
					}
					else
					{
						BLOCKDESELECT;
						if (!g_linesafter) TrackUp(1);
						else TrackUp(g_linesafter);
					}
			break;

		case VK_DOWN:
			if (control && shift)
			{
				if (!g_TrackClipboard.IsBlockSelected())
				{	//if no block is selected, make a block at the current location
					g_TrackClipboard.BlockSetBegin(m_trackactivecol, SongGetActiveTrack(), m_trackactiveline);
					g_TrackClipboard.BlockSetEnd(m_trackactiveline);
				}
				//volume change decrementing
				g_Undo.ChangeTrack(SongGetActiveTrack(), m_trackactiveline, UETYPE_TRACKDATA);
				g_TrackClipboard.BlockVolumeChange(m_activeinstr, -1);
			}
			else
				if (shift && !control)
				{
					//block selection
					BLOCKSETBEGIN;
					if (!g_linesafter) TrackDown(1, 0);
					else TrackDown(g_linesafter, 0);	//will not stop on the last line
					BLOCKSETEND;
				}
				else
					if (control && !shift)
					{
						if (ISBLOCKSELECTED)
						{
							BLOCKDESELECT;
							break;
						}
						else
						{
							SongDown();
						}
					}
					else
					{
						BLOCKDESELECT;
						if (!g_linesafter) TrackDown(1, 0);
						else TrackDown(g_linesafter, 0);	//will not stop on the last line
					}
			break;

		case VK_LEFT:
			if (control && shift)
			{
				if (!g_TrackClipboard.IsBlockSelected())
				{	//if no block is selected, make a block at the current location
					g_TrackClipboard.BlockSetBegin(m_trackactivecol, SongGetActiveTrack(), m_trackactiveline);
					g_TrackClipboard.BlockSetEnd(m_trackactiveline);
				}
				//instrument changes decrementing
				g_Undo.ChangeTrack(SongGetActiveTrack(), m_trackactiveline, UETYPE_TRACKDATA);
				g_TrackClipboard.BlockInstrumentChange(m_activeinstr, -1);
			}
			else
				if (shift && !control)
					ActiveInstrPrev();
				else
					if (control && !shift)
					{
						if (ISBLOCKSELECTED)
						{
							BLOCKDESELECT;
							break;
						}
						else SongTrackDec();
					}
					else
					{
						BLOCKDESELECT;
						TrackLeft();
					}
			break;

		case VK_RIGHT:
			if (control && shift)
			{
				if (!g_TrackClipboard.IsBlockSelected())
				{	//if no block is selected, make a block at the current location
					g_TrackClipboard.BlockSetBegin(m_trackactivecol, SongGetActiveTrack(), m_trackactiveline);
					g_TrackClipboard.BlockSetEnd(m_trackactiveline);
				}
				//instrument changes incrementing
				g_Undo.ChangeTrack(SongGetActiveTrack(), m_trackactiveline, UETYPE_TRACKDATA);
				g_TrackClipboard.BlockInstrumentChange(m_activeinstr, 1);
			}
			else
				if (shift && !control)
					ActiveInstrNext();
				else
					if (control && !shift)
					{
						if (ISBLOCKSELECTED)
						{
							BLOCKDESELECT;
							break;
						}
						else SongTrackInc();
					}
					else
					{
						BLOCKDESELECT;
						TrackRight();
					}
			break;

		case VK_PAGE_UP:
			if (!shift && control)
			{
				BLOCKDESELECT;
				SongUp();
			}
			else
				if (!control && shift)
				{
					//move to the previous goto
					BLOCKDESELECT;
					SongSubsongPrev();
				}
				else
					if (m_play && m_followplay) break;	//prevents moving at all during play+follow
					else
					{
						BLOCKDESELECT;
						if (m_trackactiveline > 0)
						{
							m_trackactiveline = ((m_trackactiveline - 1) / g_trackLinePrimaryHighlight) * g_trackLinePrimaryHighlight;
						}
					}
			break;

		case VK_PAGE_DOWN:
			if (!shift && control)
			{
				BLOCKDESELECT;
				SongDown();
			}
			else
				if (!control && shift)
				{
					//move to the next goto
					BLOCKDESELECT;
					SongSubsongNext();
				}
				else
					if (m_play && m_followplay) break;	//prevents moving at all during play+follow
					else
					{
						BLOCKDESELECT;
						m_trackactiveline = ((m_trackactiveline + g_trackLinePrimaryHighlight) / g_trackLinePrimaryHighlight) * g_trackLinePrimaryHighlight;
						if (m_trackactiveline > GetSmallestMaxtracklen(m_songactiveline) - 1)
							m_trackactiveline -= g_trackLinePrimaryHighlight;
					}
			break;

		case VK_SUBTRACT:
			VolumeDown();
			break;

		case VK_ADD:
			VolumeUp();
			break;

		case VK_DIVIDE:
			OctaveDown();
			break;

		case VK_MULTIPLY:
			OctaveUp();
			break;

		case VK_TAB:
			BLOCKDESELECT;
			if (shift)
				TrackLeft(1); //SHIFT+TAB
			else if (control)
				CursorToSpeedColumn(); //CTRL+TAB
			else
				TrackRight(1);
			break;

		case VK_ESCAPE:
			BLOCKDESELECT;
			break;

		case 65:	//VK_A
			if (g_TrackClipboard.IsBlockSelected() && shift && control)
			{	//Shift+control+A
				//switch ALL / no ALL
				g_TrackClipboard.BlockAllOnOff();
			}
			else
				if (control && !shift)
				{
					//control+A
					//selection of the whole track (from 0 to the length of that track)
					g_TrackClipboard.BlockDeselect();
					g_TrackClipboard.BlockSetBegin(m_trackactivecol, SongGetActiveTrack(), 0);
					g_TrackClipboard.BlockSetEnd(GetSmallestMaxtracklen(m_songactiveline) - 1);
				}
			break;

		case 66:	//VK_B			//restore block from backup
			if (g_TrackClipboard.IsBlockSelected() && control && !shift)
			{
				g_Undo.ChangeTrack(SongGetActiveTrack(), m_trackactiveline, UETYPE_TRACKDATA, 1);
				g_TrackClipboard.BlockRestoreFromBackup();
			}
			break;

		case 67:	//VK_C
			if (control && !shift)
			{
				if (!g_TrackClipboard.IsBlockSelected())
				{	//if no block is selected, make a block at the current location
					g_TrackClipboard.BlockSetBegin(m_trackactivecol, SongGetActiveTrack(), m_trackactiveline);
					g_TrackClipboard.BlockSetEnd(m_trackactiveline);
				}
				g_TrackClipboard.BlockCopyToClipboard();
			}
			break;

		case 69:	//VK_E
			if (control && !shift)		//exchange block and clipboard
			{
				if (g_TrackClipboard.IsBlockSelected())
				{
					g_Undo.ChangeTrack(SongGetActiveTrack(), m_trackactiveline, UETYPE_TRACKDATA, 1);
					if (!g_TrackClipboard.BlockExchangeClipboard()) g_Undo.DropLast();
				}
			}
			break;

		case 0x4D:	//VK_M
			if (control && !shift)
			{
				BLOCKDESELECT;
				BlockPaste(1);	//paste merge
			}
			break;

		case 86:	//VK_V
			if (control && !shift)
			{
				BLOCKDESELECT;
				BlockPaste();	//classic paste
			}
			break;

		case 88:	//VK_X
			if (control && !shift)
			{
				if (!g_TrackClipboard.IsBlockSelected())
				{	//if no block is selected, make a block at the current location
					g_TrackClipboard.BlockSetBegin(m_trackactivecol, SongGetActiveTrack(), m_trackactiveline);
					g_TrackClipboard.BlockSetEnd(m_trackactiveline);
				}
				g_Undo.ChangeTrack(SongGetActiveTrack(), m_trackactiveline, UETYPE_TRACKDATA, 1);
				g_TrackClipboard.BlockCopyToClipboard();
				g_TrackClipboard.BlockClear();
			}
			break;

		case 70:	//VK_F
			if (control && !shift && g_TrackClipboard.IsBlockSelected())
			{
				g_Undo.ChangeTrack(SongGetActiveTrack(), m_trackactiveline, UETYPE_TRACKDATA, 1);
				if (!g_TrackClipboard.BlockEffect()) g_Undo.DropLast();
			}
			break;

		case 71:	//VK_G		//song goto on/off
			BLOCKDESELECT;
			if (control && !shift) SongTrackGoOnOff();	//control+G => goto on/off line in the song
			break;

		case VKX_SONGPUTNEWTRACK:	//VK_N
			BLOCKDESELECT;
			if (control && !shift)
				SongPutnewemptyunusedtrack();
			break;

		case VKX_SONGMAKETRACKSDUPLICATE:	//VK_D
			BLOCKDESELECT;
			if (control && !shift)
				SongMaketracksduplicate();
			break;

		case VK_HOME:
			if (control)
				TrackSetGo();
			else
			{
				if (shift)
				{
					BLOCKSETBEGIN;
					m_trackactiveline = 0;		//line 0
					BLOCKSETEND;
				}
				else
				{
					if (g_TrackClipboard.IsBlockSelected())
					{
						//sets to the first line in the block
						int bfro, bto;
						g_TrackClipboard.GetFromTo(bfro, bto);
						m_trackactiveline = bfro;
					}
					else
					{
						if (m_trackactiveline != 0)
							m_trackactiveline = 0;		//line 0
						else
						{
							i = TrackGetGoLine();
							if (i >= 0) m_trackactiveline = i;	//at the beginning of the GO loop
						}
						BLOCKDESELECT;
					}
				}
			}
			break;

		case VK_END:
			if (control)
				TrackSetEnd();
			else
			{
				if (shift)
				{
					BLOCKSETBEGIN;
					if (TrackGetGoLine() >= 0)
						m_trackactiveline = g_Tracks.GetMaxTrackLength() - 1; //last line
					else
						m_trackactiveline = TrackGetLastLine();	//end line
					BLOCKSETEND;
					if (m_trackactiveline < 0)
					{
						m_trackactiveline = g_Tracks.GetMaxTrackLength() - 1; //failsafe in case the active line is out of bounds
						BLOCKDESELECT;	//prevents selecting invalid data
					}
				}
				else
				{
					if (g_TrackClipboard.IsBlockSelected())
					{
						//sets to the first line in the block
						int bfro, bto;
						g_TrackClipboard.GetFromTo(bfro, bto);
						m_trackactiveline = bto;
					}
					else
					{
						i = TrackGetLastLine();
						if (i != m_trackactiveline)
						{
							m_trackactiveline = i;	//at the end of the GO loop or end line
							if (m_trackactiveline < 0) m_trackactiveline = g_Tracks.GetMaxTrackLength() - 1; //failsafe in case the active line is out of bounds
						}
						else m_trackactiveline = g_Tracks.GetMaxTrackLength() - 1; //last line
						BLOCKDESELECT;
					}
				}
			}
			break;

		case 13:		//VK_ENTER:	//FIXME: Channels are desynched when track End or Loops are detected, bad hack...
			int instr, vol, oldline;
			{
				if (shift && control)
				{
					BLOCKDESELECT;
					TrackSetEnd();
					break;
				}
				if (!shift && (BOOL)control != (BOOL)g_keyboard_swapenter)	//control+Enter => plays a whole line (all tracks)
				{
					//for all track columns except the active track column
					for (i = 0; i < g_tracks4_8; i++)
					{
						if (i != m_trackactivecol)
						{
							TrackGetLoopingNoteInstrVol(m_song[m_songactiveline][i], note, instr, vol);
							if (note >= 0)		//is there a note?
								SetPlayPressedTonesTNIV(i, note, instr, vol);	//it will lose it as it is there
							else
								if (vol >= 0) //there is no note, but is there a separate volume?
									SetPlayPressedTonesV(i, vol);				//adjust the volume as it is
						}
					}
				}
				//and now for that active track column
				TrackGetLoopingNoteInstrVol(SongGetActiveTrack(), note, instr, vol);
				if (note >= 0)		//is there a note?
				{
					SetPlayPressedTonesTNIV(m_trackactivecol, note, instr, vol);	//it will lose it as it is there
					if (shift && !control)	//with the shift, this instrument and the volume will "pick up" as current (only if it is not 0)
					{
						ActiveInstrSet(instr);
						if (vol > 0) m_volume = vol;
					}
				}
				else
					if (vol >= 0) //there is no note, but is there a separate volume?
					{
						SetPlayPressedTonesV(m_trackactivecol, vol); //adjust the volume
						if (shift && !control && vol > 0) m_volume = vol; //"picks up" the volume as current (only if it is not 0)
					}
			}
			oldline = m_trackactiveline;	//hack, force a line move even if TrackDown prevents it after Enter called it, otherwise the last line would get stuck
			if (TrackDown(1, 0) && oldline == m_trackactiveline) m_trackactiveline++;
			if (g_TrackClipboard.IsBlockSelected())	//if a block is selected, it moves (and plays) only in it
			{
				int bfro, bto;
				g_TrackClipboard.GetFromTo(bfro, bto);
				if (m_trackactiveline<bfro || m_trackactiveline>bto) m_trackactiveline = bfro;
			}
			break;

		case VKX_SONGINSERTLINE:	//VK_I:
			if (control && !shift)
				goto insertline;
			break;

		case VKX_SONGDELETELINE:	//VK_U:
			if (control && !shift)
				goto deleteline;
			break;

		case VK_INSERT:
		{
		insertline:
			BLOCKDESELECT;
			g_Undo.ChangeTrack(SongGetActiveTrack(), m_trackactiveline, UETYPE_TRACKDATA, 0);
			g_Tracks.InsertLine(SongGetActiveTrack(), m_trackactiveline);
		}
		break;

		case VK_DELETE:
			if (g_TrackClipboard.IsBlockSelected())
			{
				//the block is selected, so it deletes it
				g_Undo.ChangeTrack(SongGetActiveTrack(), m_trackactiveline, UETYPE_TRACKDATA, 1);
				g_TrackClipboard.BlockClear();
			}
			else
				if (!shift)
				{
				deleteline:
					BLOCKDESELECT;
					g_Undo.ChangeTrack(SongGetActiveTrack(), m_trackactiveline, UETYPE_TRACKDATA, 0);
					g_Tracks.DeleteLine(SongGetActiveTrack(), m_trackactiveline);
				}
			break;

		case VK_SPACE:
			if (control) break; //fixes the "return to EDIT MODE space input" bug, by ignoring SPACE if CTRL is also detected
			BLOCKDESELECT;
			if (TrackDelNoteInstrVolSpeed(1 + 2 + 4 + 8)) //all
			{
				if (!(m_play && m_followplay)) TrackDown(g_linesafter);
			}
			break;

		case 8:			//VK_BACKSPACE:
		{
			BLOCKDESELECT;
			int r = 0;
			switch (m_trackactivecur)
			{
				case 0:	//note
				case 1: //instrument
					r = TrackDelNoteInstrVolSpeed(1 + 2); //delete note + instrument
					break;
				case 2: //volume
					r = TrackDelNoteInstrVolSpeed(1 + 2 + 4); //delete note + instrument + volume
					break;
				case 3: //speed
					r = TrackSetSpeed(-1);	//delete speed
					break;
			}
			if (r)
			{
				if (!(m_play && m_followplay)) TrackDown(g_linesafter);
			}
		}
		break;

		case VK_F1:
			if (control)
			{
				if (!g_TrackClipboard.IsBlockSelected())
				{	//if no block is selected, make a block at the current location
					g_TrackClipboard.BlockSetBegin(m_trackactivecol, SongGetActiveTrack(), m_trackactiveline);
					g_TrackClipboard.BlockSetEnd(m_trackactiveline);
				}
				//transpose down by 1 semitone
				g_Undo.ChangeTrack(SongGetActiveTrack(), m_trackactiveline, UETYPE_TRACKDATA);
				g_TrackClipboard.BlockNoteTransposition(m_activeinstr, -1);
			}
			break;

		case VK_F2:
			if (control)
			{
				if (!g_TrackClipboard.IsBlockSelected())
				{	//if no block is selected, make a block at the current location
					g_TrackClipboard.BlockSetBegin(m_trackactivecol, SongGetActiveTrack(), m_trackactiveline);
					g_TrackClipboard.BlockSetEnd(m_trackactiveline);
				}
				//transpose up by 1 semitone
				g_Undo.ChangeTrack(SongGetActiveTrack(), m_trackactiveline, UETYPE_TRACKDATA);
				g_TrackClipboard.BlockNoteTransposition(m_activeinstr, 1);
			}
			break;

		case VK_F3:
			if (control)
			{
				if (!g_TrackClipboard.IsBlockSelected())
				{	//if no block is selected, make a block at the current location
					g_TrackClipboard.BlockSetBegin(m_trackactivecol, SongGetActiveTrack(), m_trackactiveline);
					g_TrackClipboard.BlockSetEnd(m_trackactiveline);
				}
				//transpose down by 1 octave
				g_Undo.ChangeTrack(SongGetActiveTrack(), m_trackactiveline, UETYPE_TRACKDATA);
				g_TrackClipboard.BlockNoteTransposition(m_activeinstr, -12);
			}
			break;

		case VK_F4:
			if (control)
			{
				if (!g_TrackClipboard.IsBlockSelected())
				{	//if no block is selected, make a block at the current location
					g_TrackClipboard.BlockSetBegin(m_trackactivecol, SongGetActiveTrack(), m_trackactiveline);
					g_TrackClipboard.BlockSetEnd(m_trackactiveline);
				}
				//transpose up by 1 octave
				g_Undo.ChangeTrack(SongGetActiveTrack(), m_trackactiveline, UETYPE_TRACKDATA);
				g_TrackClipboard.BlockNoteTransposition(m_activeinstr, 12);
			}
			break;

		default:
			return 0;
			break;
	}
	return 1;
}

BOOL CSong::TrackCursorGoto(CPoint point)
{
	int xch, x, y;
	xch = (point.x / (16 * 8));
	x = (point.x - (xch * 16 * 8)) / 8;

	y = (point.y + 0) / 16 - 8 + g_cursoractview;	//m_trackactiveline;

	//if (y >= 0 && y < g_Tracks.m_maxtracklen)
	if (y >= 0 && y < GetSmallestMaxtracklen(m_songactiveline))	//variable pattern size, to prevent clicking "out of bounds" with the new tracks display
	{
		if (xch >= 0 && xch < g_tracks4_8) m_trackactivecol = xch;
		if (m_play && m_followplay)	//prevents moving at all during play+follow
			goto notracklinechange;
		else
			m_trackactiveline = y;
	}
	else
		return 0;
notracklinechange:
	switch (x)
	{
		case 0:
		case 1:
		case 2:
		case 3:
			m_trackactivecur = 0;
			break;
		case 4:
		case 5:
		case 6:
			m_trackactivecur = 1;
			break;
		case 7:
		case 8:
		case 9:
			m_trackactivecur = 2;
			break;
		case 10:
		case 11:
		case 12:
		case 13:
		case 14:
		case 15:	//filling more area avoids jumping all over the place, between the speed column and the next channel's note column
		case 16:
			m_trackactivecur = 3;
			break;
	}
	g_activepart = PART_TRACKS;
	return 1;
}



BOOL CSong::SongKey(int vk, int shift, int control)
{
	int isgo = (m_songgo[m_songactiveline] >= 0) ? 1 : 0;

	if (!control && NumbKey(vk) >= 0)
	{
		return SongTrackSetByNum(NumbKey(vk));
	}

	switch (vk)
	{
		case VK_UP:
			BLOCKDESELECT;
			SongUp();
			break;

		case VK_DOWN:
			BLOCKDESELECT;
			SongDown();
			break;

		case VK_LEFT:
			if (shift)
				ActiveInstrPrev();
			else
				if (control)
				{
					if (isgo)
						SongTrackGoDec();
					else
						SongTrackDec();
				}
				else
					TrackLeft(1);
			break;

		case VK_RIGHT:
			if (shift)
				ActiveInstrNext();
			else
				if (control)
				{
					if (isgo)
						SongTrackGoInc();
					else
						SongTrackInc();
				}
				else
					TrackRight(1);
			break;

		case VK_TAB:
			if (shift)
				TrackLeft(1); //SHIFT+TAB
			else
				TrackRight(1);
			break;

		case VKX_SONGDELETELINE:	//Control+VK_U:
			if (!control) break;
		case VK_DELETE:
			SongDeleteLine(m_songactiveline);
			break;

		case VKX_SONGINSERTLINE:	//Control+VK_I:
			if (!control) break;
		case VK_INSERT:
			SongInsertLine(m_songactiveline);
			break;

		case VKX_SONGDUPLICATELINE:	//Control+VK_O
			if (control)
				SongInsertCopyOrCloneOfSongLines(m_songactiveline);
			break;

		case VKX_SONGPREPARELINE:	//Control+VK_P
			if (control)
				SongPrepareNewLine(m_songactiveline);
			break;

		case VKX_SONGPUTNEWTRACK:	//Control+VK_N
			if (control)
				SongPutnewemptyunusedtrack();
			break;

		case VKX_SONGMAKETRACKSDUPLICATE:	//Control+VK_D
			BLOCKDESELECT;
			if (control)
				SongMaketracksduplicate();
			break;

		case 8:			//VK_BACKSPACE:
			if (isgo)
				SongTrackGoOnOff();	//Go off
			else
				SongTrackEmpty();
			break;

		case 71:		//VK_G
			if (control)
				SongTrackGoOnOff();	//Go on/off
			break;

		case 13:		//VK_ENTER
			g_activepart = g_active_ti;
			break;

		case VK_HOME:
			m_songactiveline = 0;
			break;

		case VK_END:
		{
			int i, j, la = 0;
			for (j = 0; j < SONGLEN; j++)
			{
				for (i = 0; i < g_tracks4_8; i++) if (m_song[j][i] >= 0) { la = j; break; }
				if (m_songgo[j] >= 0) la = j;
			}
			m_songactiveline = la;
		}
		break;

		case VK_PAGE_UP:
			if (shift)
				SongSubsongPrev();
			else
			{
				SongUp();
			}
			break;

		case VK_PAGE_DOWN:
			if (shift)
				SongSubsongNext();
			else
			{
				SongDown();
			}
			break;

		case VK_MULTIPLY:
		{
			OctaveUp();
			return 1;
		}
		break;

		case VK_DIVIDE:
		{
			OctaveDown();
			return 1;
		}
		break;

		case VK_ADD:
		{
			VolumeUp();
			return 1;
		}

		case VK_SUBTRACT:
		{
			VolumeDown();
			return 1;
		}

		default:
			return 0;
			break;
	}
	return 1;
}


BOOL CSong::SongCursorGoto(CPoint point)
{
	int xch, y;
	xch = ((point.x + 4) / (3 * 8));
	y = (point.y + 0) / 16 - 2 + m_songactiveline;
	if (y >= 0 && y < SONGLEN)
	{
		if (xch >= 0 && xch < g_tracks4_8) m_trackactivecol = xch;
		if (y != m_songactiveline)
		{
			g_activepart = PART_SONG;
			if (m_play && m_followplay)
			{
				auto mode = (m_play == PLAY_TRACK) ? PLAY_TRACK : PLAY_FROM;	//play track in loop, else, play from cursor position
				Stop();
				m_songplayline = m_songactiveline = y;
				m_trackplayline = m_trackactiveline = 0;
				Play(mode, m_followplay); // continue playing using the correct parameters
			}
			else
				m_songactiveline = y;
		}

	}
	else
		return 0;
	m_trackactivecol = xch;
	g_activepart = PART_SONG;
	return 1;
}