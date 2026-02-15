#include "SongUI.h"

#include "Canvas.h"
#include "CanvasXY.h"
#include "IOHelpers.h"
#include "RmtScreenLayout.h"
#include "Song.h"
#include "Tuning.h"
#include <assert.h>

#include "TracksControl.h"

#include "Clipboard.h"
#include "Global.h"
#include "Instruments.h"
#include "PokeyView.h"

#include "PokeyController.h"

static constexpr int ANALYZER_S = 6;
static constexpr int ANALYZER_H = 5;
static constexpr int ANALYZER_HP = 8;

static constexpr int ANALYZER2_S = 1;
static constexpr int ANALYZER2_H = 4;
static constexpr int ANALYZER2_HP = 8;

extern CTuning g_Tuning;

extern CTrackClipboard g_TrackClipboard;

extern CInstruments	g_Instruments;


// TODO

CSongUI::CSongUI(CSong& song) : m_song(&song) {

}

void CSongUI::SetCanvas(CCanvasXY& canvasXY) {
    this->canvasXY = &canvasXY;
}

const char* GetAtariMemoryHexString(MemoryAddress adr, MemorySize len)
{
    static constexpr MemorySize MAX_LENGTH = 256;

    assert(len < MAX_LENGTH);
    static char g_debugmem[6 + MAX_LENGTH * 4 + 1];

    const auto memory = g_AtariTrackerDriver->GetAtari()->GetConstMemoryAt(0);

    auto p = g_debugmem;
    sprintf(p, "$%04hX ", adr);
    p += 6;

    for (int i = 0; i < len; i++)
    {
        auto a = memory[adr + i];
        sprintf(p, "$%02hX ", a);
        p += 4;
    }
    *p = 0;
    return g_debugmem;
}



// Draw a bridge between two columns (on the tracks view)
void CSongUI::DrawTracksHook(int ANALYZER_X, int ANALYZER_Y, int g1, int g2, int yUp)
{

    canvasXY->MoveTo(ANALYZER_X + 2 + ANALYZER_S * 15 / 2 + 16 * 8 * (g1), ANALYZER_Y - 1);
    canvasXY->LineTo(ANALYZER_X + 2 + ANALYZER_S * 15 / 2 + 16 * 8 * (g1), ANALYZER_Y - yUp);
    canvasXY->LineTo(ANALYZER_X + 2 + ANALYZER_S * 15 / 2 + 16 * 8 * (g2), ANALYZER_Y - yUp);
    canvasXY->LineTo(ANALYZER_X + 2 + ANALYZER_S * 15 / 2 + 16 * 8 * (g2), ANALYZER_Y);
}

// Draw a bridge between two columns (on the instrument view)
void CSongUI::DrawInstrumentHook(int ANALYZER2_X, int ANALYZER_Y, int g1, int g2, int yUp)
{
    canvasXY->MoveTo(ANALYZER2_X + ANALYZER2_S * 15 / 2 + 3 * 8 * (g1), ANALYZER_Y - 120 - 1);
    canvasXY->LineTo(ANALYZER2_X + ANALYZER2_S * 15 / 2 + 3 * 8 * (g1), ANALYZER_Y - 120 - yUp);
    canvasXY->LineTo(ANALYZER2_X + ANALYZER2_S * 15 / 2 + 3 * 8 * (g2), ANALYZER_Y - 120 - yUp);
    canvasXY->LineTo(ANALYZER2_X + ANALYZER2_S * 15 / 2 + 3 * 8 * (g2), ANALYZER_Y - 120);
}


/// <summary>
/// Draw a volume analyser above each track
/// </summary>
void CSongUI::DrawVolumeAnalyzer()
{

    if (!g_view.volumeAnalyzer) return;	//the analyser won't be displayed without the setting enabled first

    const int MINIMAL_WIDTH_TRACKS = (g_tracks4_8 > 4 && g_active_ti == Part::PART_TRACKS) ? 1420 : 960;
    const int MINIMAL_WIDTH_INSTRUMENTS = (g_tracks4_8 > 4 && g_active_ti == Part::PART_INSTRUMENTS) ? 1220 : 1220;
    const int WINDOW_OFFSET = (g_width < 1320 && g_tracks4_8 > 4 && g_active_ti == Part::PART_TRACKS) ? -250 : 0;	//test displacement with the window size
    int INSTRUMENT_OFFSET = (g_active_ti == Part::PART_INSTRUMENTS && g_tracks4_8 > 4) ? -250 : 0;
    if (g_tracks4_8 == 4 && g_active_ti == Part::PART_INSTRUMENTS && g_width > MINIMAL_WIDTH_INSTRUMENTS - 220) { INSTRUMENT_OFFSET = 260; }
    const int SONG_OFFSET = CRmtScreenLayout::SONG_X + WINDOW_OFFSET + INSTRUMENT_OFFSET + ((g_tracks4_8 == 4) ? -200 : 310);	//displace the SONG block depending on certain parameters

    auto viewPokeyRegisters = g_view.pokeyRegisters;
    BOOL DEBUG_POKEY = viewPokeyRegisters;	// registers debug display
    BOOL DEBUG_MEMORY = FALSE;	// memory debug display

    if (g_width < MINIMAL_WIDTH_TRACKS && g_active_ti == Part::PART_TRACKS) DEBUG_POKEY = DEBUG_MEMORY = FALSE;
    if (g_width < MINIMAL_WIDTH_INSTRUMENTS && g_active_ti == Part::PART_INSTRUMENTS) DEBUG_POKEY = DEBUG_MEMORY = FALSE;

    const auto  ANALYZER_X = CRmtScreenLayout::TRACKS_X + 6 * 8 + 4;		// 68
    static constexpr int ANALYZER_Y(CRmtScreenLayout::TRACKS_Y - 8);			// Line 8 = 128

    const auto ANALYZER2_X = SONG_OFFSET + 6 * 8;
    static constexpr int ANALYZER2_Y = CRmtScreenLayout::TRACKS_Y - 128;

    const auto ANALYZER3_X = SONG_OFFSET + 6 * 8 - 32;
    static constexpr auto ANALYZER3_Y = (CRmtScreenLayout::TRACKS_Y + 50);

    int audf, audc, vol;
    static int idx[8] = { 0xd200,0xd202,0xd204,0xd206,0xd210,0xd212,0xd214,0xd216 };	// AUDF and AUDC for mono and stereo
    int col[8];
    int R[8];
    int G[8];
    int yUp = 7;
    for (int i = 0; i < m_song->GetTracks(); i++) { col[i] = 102; R[i] = 44; G[i] = 60; }
    int a;
    int b;
    COLORREF acol;

    if (g_active_ti == Part::PART_TRACKS) // bigger look for track edit mode
    {
        // In tracks drawing mode
        // Draw bridge connections between channels. For each connection we move 2 pixels up.
        // Max rise is 10 pixels
        const auto memory = g_AtariTrackerDriver->GetAtari()->GetConstMemoryAt(0);

        // Clear the area where the analyser is to be drawn
        canvasXY->FillSolidRect(ANALYZER_X, ANALYZER_Y - ANALYZER_HP, g_tracks4_8 * 16 * 8 - 34, ANALYZER_H + ANALYZER_HP, CRGBColor::BACKGROUND);

        // Left/Mono Channel
        // Draw which channels are joined by highpass filters or normal channel join
        a = memory[0xd208]; // AUDCTL @ $D208
        if (a & 0x04) { col[2] = CRGBColor::COL_BLOCK; DrawTracksHook(ANALYZER_X, ANALYZER_Y, 0, 2, yUp); yUp -= 2; }	// High pass filter on channel 1, clocked by channel 3
        if (a & 0x02) { col[3] = CRGBColor::COL_BLOCK;	DrawTracksHook(ANALYZER_X, ANALYZER_Y, 1, 3, yUp); yUp -= 2; }	// High pass filter on channel 3, clocked by channel 4
        if (a & 0x10) { col[0] = CRGBColor::COL_BLOCK;	DrawTracksHook(ANALYZER_X, ANALYZER_Y, 0, 1, yUp); yUp -= 2; }	// Join channels 1 + 2 (16 bit)
        if (a & 0x08) { col[2] = CRGBColor::COL_BLOCK;	DrawTracksHook(ANALYZER_X, ANALYZER_Y, 2, 3, yUp); yUp -= 2; }	// Join channels 3 + 4 (16 bit)

        b = memory[0xd20f]; // SKCTL @ $D20F
        if (b == 0x8b) { col[1] = CRGBColor::COL_BLOCK; DrawTracksHook(ANALYZER_X, ANALYZER_Y, 0, 1, yUp); yUp -= 2; }	// Two tone mode (join channel 1 + 2)
        yUp = 7;

        // Stereo Channel
        a = memory[0xd218]; // AUDCTL2 @ $D218
        if (a & 0x04) { col[2 + 4] = CRGBColor::COL_BLOCK; DrawTracksHook(ANALYZER_X, ANALYZER_Y, 0 + 4, 2 + 4, yUp); yUp -= 2; }	// High pass filter on channel 5 clocked by channel 7
        if (a & 0x02) { col[3 + 4] = CRGBColor::COL_BLOCK; DrawTracksHook(ANALYZER_X, ANALYZER_Y, 1 + 4, 3 + 4, yUp); yUp -= 2; }	// High pass filter on channel 7, clocked by channel 8
        if (a & 0x10) { col[0 + 4] = CRGBColor::COL_BLOCK; DrawTracksHook(ANALYZER_X, ANALYZER_Y, 0 + 4, 1 + 4, yUp); yUp -= 2; }	// Join channels 5 + 6 (16 bit)
        if (a & 0x08) { col[2 + 4] = CRGBColor::COL_BLOCK; DrawTracksHook(ANALYZER_X, ANALYZER_Y, 2 + 4, 3 + 4, yUp); yUp -= 2; }	// Join channels 7 + 8 (16 bit)

        b = memory[0xd21f]; // SKCTL2 @ $D21F
        if (b == 0x8b) { col[1 + 4] = CRGBColor::COL_BLOCK; DrawTracksHook(ANALYZER_X, ANALYZER_Y, 0 + 4, 1 + 4, yUp); yUp -= 2; }	// Two tone mode (join channel 5 + 6)

        for (int channelNr = 0; channelNr < m_song->GetTracks(); channelNr++)
        {
            audf = memory[idx[channelNr]];		// Get the frequency
            audc = memory[idx[channelNr] + 1];	// Get audio control, Bits: 0-3 = volume, 4 = Volume only, 5-7 = Distortion
            int skctl1 = memory[0xd20f];		// Two tone mode Mono
            int skctl2 = memory[0xd21f];		// Two tone mode Stereo

            vol = audc & 0x0f;					// Volume in lower nibble 
            a = channelNr * 16 * 8;				// X offset

            // Draw the background box of the volume analyser for this channel
            // 15 unit wide, each unit is 6 pixels (ANALYZER_S)
            // Default color is RGB(44, 60, 102) - Dark blue
            canvasXY->FillSolidRect(ANALYZER_X + a + 2, ANALYZER_Y, 15 * ANALYZER_S, ANALYZER_H, RGB(R[channelNr], G[channelNr], col[channelNr]));

            // Determine the color of the channels volume bar: Normal, mute or Volume only
            acol = g_ChannelControl.IsChannelOn(channelNr) ? ((audc & 0x10) ? CRGBColor::VOLUME_ONLY : CRGBColor::NORMAL) : CRGBColor::MUTE;

            // Check if its a two tone channel (1 or 5)
            if (g_ChannelControl.IsChannelOn(channelNr) && ((skctl1 == 0x8b && channelNr == 0) || (skctl2 == 0x8b && channelNr == 4))) { acol = CRGBColor::TWO_TONE; }

            // Draw the volume bar in the selected color
            if (vol) { canvasXY->FillSolidRect(ANALYZER_X + a + 3 + (15 - vol) * ANALYZER_S / 2, ANALYZER_Y, vol * ANALYZER_S, ANALYZER_H, acol); }

            // Draw the frequency and audio control numbers for this channel
            if (viewPokeyRegisters)
            {
                canvasXY->NumberMiniXY(audf, ANALYZER_X + 10 + a + 17, ANALYZER_Y - 8, TextMiniColor::GRAY);
                canvasXY->NumberMiniXY(audc, ANALYZER_X + 36 + a + 17, ANALYZER_Y - 8, TextMiniColor::GRAY);
            }
        }
        if (viewPokeyRegisters)
        {
            // Draw the AUDCTL (audio control) register value
            canvasXY->NumberMiniXY(memory[0xd208], ANALYZER_X + 23 + 1 * 8 * 16 + 80, ANALYZER_Y - 8);						// Mono
            if (g_tracks4_8 > 4) { canvasXY->NumberMiniXY(memory[0xd218], ANALYZER_X + 23 + 5 * 8 * 16 + 80, ANALYZER_Y - 8); }// Stereo

            // Draw the SKCTL (Two tone control/Serial port control) register value
            canvasXY->NumberMiniXY(memory[0xd20f], ANALYZER_X + 23 + 1 * 8 * 16 + 80, ANALYZER_Y - 0);						// Mono
            if (g_tracks4_8 > 4) {
                canvasXY->NumberMiniXY(memory[0xd21f], ANALYZER_X + 23 + 5 * 8 * 16 + 80, ANALYZER_Y - 0);	// Stereo
            }
        }
        else if (g_active_ti == Part::PART_INSTRUMENTS) //smaller appearance for instrument edit mode
        {
            // In instrument drawing mode

            // Clear the area where the mini volume controls are to be drawn
            canvasXY->FillSolidRect(ANALYZER2_X, ANALYZER2_Y - ANALYZER2_HP, g_tracks4_8 * 3 * 8 - 8, ANALYZER2_H + ANALYZER2_HP, CRGBColor::BACKGROUND);

            const auto memory = g_AtariTrackerDriver->GetAtari()->GetConstMemoryAt(0);

            // Left / Mono Channel
            // Draw which channels are joined by highpass filters or normal channel join
            a = memory[0xd208]; // AUDCTL @ $D208
            if (a & 0x04) { col[2] = CRGBColor::COL_BLOCK; DrawInstrumentHook(ANALYZER2_X, ANALYZER_Y, 0, 2, yUp); yUp -= 2; }	// High pass filter on channel 1, clocked by channel 3
            if (a & 0x02) { col[3] = CRGBColor::COL_BLOCK; DrawInstrumentHook(ANALYZER2_X, ANALYZER_Y, 1, 3, yUp); yUp -= 2; }	// High pass filter on channel 3, clocked by channel 4
            if (a & 0x10) { col[0] = CRGBColor::COL_BLOCK; DrawInstrumentHook(ANALYZER2_X, ANALYZER_Y, 0, 1, yUp); yUp -= 2; }	// Join channels 1 + 2 (16 bit)
            if (a & 0x08) { col[2] = CRGBColor::COL_BLOCK; DrawInstrumentHook(ANALYZER2_X, ANALYZER_Y, 2, 3, yUp); yUp -= 2; }	// Join channels 3 + 4 (16 bit)

            b = memory[0xd20f]; // SKCTL @ $D20F
            if (b == 0x8b) { col[1] = CRGBColor::COL_BLOCK; DrawInstrumentHook(ANALYZER2_X, ANALYZER_Y, 0, 1, yUp); yUp -= 2; }	// Two tone mode (join channel 1 + 2)
            yUp = 7;

            // Stereo Channel
            a = memory[0xd218]; // AUDCTL2 @ $D218
            if (a & 0x04) { col[2 + 4] = CRGBColor::COL_BLOCK; DrawInstrumentHook(ANALYZER2_X, ANALYZER_Y, 0 + 4, 2 + 4, yUp); yUp -= 2; }	// High pass filter on channel 5 clocked by channel 7
            if (a & 0x02) { col[3 + 4] = CRGBColor::COL_BLOCK; DrawInstrumentHook(ANALYZER2_X, ANALYZER_Y, 1 + 4, 3 + 4, yUp); yUp -= 2; }	// High pass filter on channel 7, clocked by channel 8
            if (a & 0x10) { col[0 + 4] = CRGBColor::COL_BLOCK; DrawInstrumentHook(ANALYZER2_X, ANALYZER_Y, 0 + 4, 1 + 4, yUp); yUp -= 2; }	// Join channels 5 + 6 (16 bit)
            if (a & 0x08) { col[2 + 4] = CRGBColor::COL_BLOCK; DrawInstrumentHook(ANALYZER2_X, ANALYZER_Y, 2 + 4, 3 + 4, yUp); yUp -= 2; }	// Join channels 7 + 8 (16 bit)

            b = memory[0xd21f]; // SKCTL2 @ $D21F
            if (b == 0x8b) { col[1 + 4] = CRGBColor::COL_BLOCK; DrawInstrumentHook(ANALYZER2_X, ANALYZER_Y, 0 + 4, 1 + 4, yUp); yUp -= 2; }	// Two tone mode (join channel 5 + 6)

            for (int channelNr = 0; channelNr < g_tracks4_8; channelNr++)
            {
                audc = memory[idx[channelNr] + 1];	// Get the frequency
                int skctl1 = memory[0xd20f];		// Two tone mode Mono
                int skctl2 = memory[0xd21f];		// Two tone mode Stereo

                vol = audc & 0x0f;						// Volume in lower nibble 

                // Draw the background box of the volume analyser for this channel
                // 15 unit wide, each unit is 6 pixels (ANALYZER_S)
                // Default color is RGB(44, 60, 102) - Dark blue
                canvasXY->FillSolidRect(ANALYZER2_X + channelNr * 3 * 8, ANALYZER2_Y, 15 * ANALYZER2_S, ANALYZER2_H, RGB(R[channelNr], G[channelNr], col[channelNr]));

                // Determine the color of the channels volume bar: Normal, mute or Volume only
                acol = g_ChannelControl.IsChannelOn(channelNr) ? ((audc & 0x10) ? CRGBColor::VOLUME_ONLY : CRGBColor::NORMAL) : CRGBColor::MUTE;

                // Check if its a two tone channel (1 or 5)
                if (g_ChannelControl.IsChannelOn(channelNr) && ((skctl1 == 0x8b && channelNr == 0) || (skctl2 == 0x8b && channelNr == 4))) acol = CRGBColor::TWO_TONE;

                // Draw the volume bar in the selected color
                if (vol) canvasXY->FillSolidRect(ANALYZER2_X + channelNr * 3 * 8 + (15 - vol) * ANALYZER2_S / 2, ANALYZER2_Y, vol * ANALYZER2_S, ANALYZER2_H, acol);
            }
        }
        if (DEBUG_POKEY) {
            CCanvas pokeyCanvas(*canvasXY, ANALYZER3_X, ANALYZER3_Y);
            CPokeyView pokeyView(pokeyCanvas);
            pokeyView.Draw(*m_song, g_Tuning, IsEditMode(EditMode::POKEY_EXPLORER_MODE), *m_song->m_PokeyController, g_Atari);
        }

        if (DEBUG_MEMORY) {
            static constexpr int ADDRESS = 0x3000; // RMTPLAYR_TABLES;
            static constexpr int BPL = 32;
            static constexpr int BLOCK = 8;
            CCanvas memoryCanvas(*canvasXY, ANALYZER3_X, ANALYZER3_Y + 192);
            memoryCanvas.ColorMini(TextMiniColor::GRAY).PrintMini("MEMORY").NextRow().NextRow();
            memoryCanvas.ColorMini(TextMiniColor::WHITE);
            for (int d = 0; d < 32; d++) {
                const auto text = GetAtariMemoryHexString(ADDRESS + BPL * d, BPL);
                memoryCanvas.PrintMini(text).NextRow();
                if (d % BLOCK == BLOCK - 1) { memoryCanvas.NextRow(); }
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
void CSongUI::DrawSong()
{
    int line, i, j, k, y, t;
    char szBuffer[32];
    TextColor color;

    auto smooth_scroll = g_view.smoothScrolling;	//TODO: make smooth scrolling an option that can be saved to .ini file

    int MINIMAL_WIDTH_INSTRUMENTS = (m_song->IsStereo() && g_active_ti == Part::PART_INSTRUMENTS) ? 1220 : 1220;
    int WINDOW_OFFSET = (g_width < 1320 && m_song->IsStereo() && g_active_ti == Part::PART_TRACKS) ? -250 : 0;	//test displacement with the window size
    int INSTRUMENT_OFFSET = (g_active_ti == Part::PART_INSTRUMENTS && m_song->IsStereo()) ? -250 : 0;
    if (!m_song->IsStereo() && g_active_ti == Part::PART_INSTRUMENTS && g_width > MINIMAL_WIDTH_INSTRUMENTS - 220) INSTRUMENT_OFFSET = 260;
    int SONG_OFFSET = CRmtScreenLayout::SONG_X + WINDOW_OFFSET + INSTRUMENT_OFFSET + ((!m_song->IsStereo()) ? -200 : 310);	//displace the SONG block depending on certain parameters

    auto active_smooth = (smooth_scroll && m_song->m_play && m_song->m_followplay) ? 1 : 0;	//could also be used as an offset
    int pattern_len = 0;
    int smooth_y = 0;

    if (active_smooth)
    {
        // Map the current track's playline into the number range -8 -> 7
        // This gives a Y position shift to draw the song line info
        // y_offset = line * 16 / track_length

        pattern_len = m_song->GetSmallestMaxtracklen(m_song->m_songplayline);
        if (!pattern_len) pattern_len = g_Tracks.GetMaxTrackLength();	//fallback to whatever is in memory instead if the value returned is invalid
        smooth_y = (active_smooth) ? (m_song->m_trackplayline * 16 / pattern_len) - 8 : 0;
        // TRACE("y offset = %d\n", smooth_y);
    }
    y = CRmtScreenLayout::SONG_Y + (1 - active_smooth) * 16 - smooth_y;

    int linescount = (WINDOW_OFFSET) ? 5 : 9;

    for (i = 0; i < linescount + active_smooth * 2; i++, y += 16)
    {
        int linesoffset = (WINDOW_OFFSET) ? -2 : -4;
        line = m_song->m_songactiveline + i + linesoffset - active_smooth;
        BOOL isOutOfBounds = 0;

        //roll over the songline if it is out of bounds
        if (line < 0 || line > 255)
        {
            line += 256;
            line %= 256;
            isOutOfBounds = 1;
        }
        // Draw either "XX: -- -- -- -- ..." or "Go to line XX"

        if ((j = m_song->m_songgo[line]) >= 0)	//there is a GO to line
        {
            // Draw: "Go to line"
            color = (isOutOfBounds) ? TextColor::DARK_GRAY : TextColor::TURQUOISE;	//turquoise text, blank tiles to mask text if needed, else gray if out of bounds
            canvasXY->TextXY("GO\x1fTO\x1fLINE", SONG_OFFSET + 16, y, color);

            // Draw: "XX"
            color = (isOutOfBounds) ? TextColor::DARK_GRAY : TextColor::WHITE;	//white, for the number used, or gray if out of bounds
            if (line == m_song->m_songactiveline)
            {
                if (IsProveMode()) color = (g_activepart == Part::PART_SONG) ? LogicalTextColor::SELECTED_PROVE : TextColor::BLUE;
                else color = (g_activepart == Part::PART_SONG) ? LogicalTextColor::SELECTED : TextColor::RED;
            }
            szBuffer[0] = CharH4(j);
            szBuffer[1] = CharL4(j);
            szBuffer[2] = 0;
            canvasXY->TextXY(szBuffer, SONG_OFFSET + 16 + 11 * 8, y, color);
        }
        else
        {
            // Draw the line number XX: (current line = YELLOW, song line = WHITE, out of bounds = TURQUOISE)
            szBuffer[0] = CharH4(line);		// XX:
            szBuffer[1] = CharL4(line);
            szBuffer[2] = ':';
            szBuffer[3] = 0;
            color = (line == m_song->m_songplayline) ? TextColor::YELLOW : TextColor::WHITE;
            if (isOutOfBounds) color = TextColor::DARK_GRAY;	//darker gray, out of bounds
            canvasXY->TextXY(szBuffer, SONG_OFFSET + 16, y, color);

            // For each track that is part of the song draw its number
            szBuffer[2] = 0;
            for (j = 0, k = 32; j < g_tracks4_8; j++, k += 24)
            {
                if ((t = m_song->m_song[line][j]) >= 0)
                {
                    szBuffer[0] = CharH4(t);
                    szBuffer[1] = CharL4(t);
                }
                else szBuffer[0] = szBuffer[1] = '-';	// No track here so draw "--"

                if (line == m_song->m_songactiveline && j == m_song->m_trackactivecol)
                {
                    if (IsProveMode()) { color = (g_activepart == Part::PART_SONG) ? LogicalTextColor::SELECTED_PROVE : TextColor::BLUE; }
                    else { color = (g_activepart == Part::PART_SONG) ? LogicalTextColor::SELECTED : TextColor::RED; }
                }
                else color = (line == m_song->m_songplayline) ? TextColor::YELLOW : TextColor::WHITE;
                if (isOutOfBounds) color = TextColor::DARK_GRAY;	//darker gray, out of bounds
                canvasXY->TextXY(szBuffer, SONG_OFFSET + 16 + k, y, color);
            }
        }
    }
    // Draw an arrow pointing to the current song line
    color = (IsProveMode()) ? TextColor::BLUE : TextColor::RED;
    int arrowpos = (WINDOW_OFFSET) ? CRmtScreenLayout::SONG_Y + 48 : CRmtScreenLayout::SONG_Y + 80;
    canvasXY->TextXY("\x04\x05", SONG_OFFSET, arrowpos, color);

    if (m_song->IsStereo())	//a line delimiting the boundary between left/right
    {
        int fl = 32;
        int tl = 32 + linescount * 16;
        int x = CRmtScreenLayout::SONG_Y + 80 + 5 * 8 + 3 + SONG_OFFSET;

        canvasXY->MoveTo(x, fl);
        canvasXY->LineTo(x, tl);
    }

    // Draw mask rectangles over the extra pixels above and below the song lines.
    // This gets rid of the pixels we dont want to see with smooth scrolling
    int width = 8 * ((m_song->IsStereo()) ? 30 : 18);
    int height = 32;
    canvasXY->FillSolidRect(SONG_OFFSET, 0, width, height, CRGBColor::BACKGROUND);	//top
    canvasXY->FillSolidRect(SONG_OFFSET, linescount * 16 + 32, width, height, CRGBColor::BACKGROUND);	//bottom

    canvasXY->TextXY("SONG", SONG_OFFSET + 8, CRmtScreenLayout::SONG_Y, TextColor::WHITE);

    //print L1 .. L4 R1 .. R4 with highlighted current track
    k = SONG_OFFSET + 6 * 8;
    szBuffer[0] = 'L';
    szBuffer[2] = 0;
    for (i = 0; i < 4; i++, k += 24)
    {
        szBuffer[1] = i + '1';	//character 1-4
        if (g_ChannelControl.IsChannelOn(i))
        {
            if (m_song->m_trackactivecol == i) color = IsProveMode() ? TextColor::BLUE : TextColor::RED;	//active channel highlight
            else color = TextColor::WHITE; //normal channel
        }
        else color = TextColor::GRAY; //switched off channels are in gray
        canvasXY->TextXY(szBuffer, k, CRmtScreenLayout::SONG_Y, color);
    }
    szBuffer[0] = 'R';
    for (i = 4; i < m_song->GetTracks(); i++, k += 24)
    {
        szBuffer[1] = i + 49 - 4;	//character 1-4
        if (g_ChannelControl.IsChannelOn(i))
        {
            if (m_song->m_trackactivecol == i) color = IsProveMode() ? TextColor::BLUE : TextColor::RED;	//active channel highlight
            else color = TextColor::WHITE; //normal channel
        }
        else color = TextColor::GRAY; //switched off channels are in gray
        canvasXY->TextXY(szBuffer, k, CRmtScreenLayout::SONG_Y, color);
    }
}


// TODO provat
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

void CSongUI::DrawTracks()
{
    const char* tnames = "L1L2L3L4R1R2R3R4";
    char s[16], stmp[16];
    int i, x, y, tr, line;
    TextColor color;
    int t;

    BOOL printdebug = g_view.debugDisplay;
    CCanvas tracksCanvas(*canvasXY, CRmtScreenLayout::TRACKS_X, CRmtScreenLayout::TRACKS_Y);

    //caching certain global variables makes sure they remain the same until the function finishes drawing the tracks
    //this appears to be related to routine timing, and might actually explain why certain bugs seem to happen randomly
    int trackactiveline = m_song->m_trackactiveline;
    int trackplayline = m_song->m_trackplayline;
    int songactiveline = m_song->m_songactiveline;
    int songplayline = m_song->m_songplayline;
    int speed = m_song->m_speed;
    int speeda = m_song->m_speeda;

    //coordinates for only the TRACKS width block rendering
    int mask_x = (!m_song->IsStereo()) ? CRmtScreenLayout::TRACKS_X + (93 - 4 * 11) * 11 - 4 : CRmtScreenLayout::TRACKS_X + (93 + 3) * 11 - 8;

    if (m_song->SongGetGo() >= 0)		//it's a GOTO line, it won't draw tracks
    {
        int TRACKS_OFFSET = (m_song->IsStereo()) ? 62 : 30;
        canvasXY->TextXY("GO TO LINE ", CRmtScreenLayout::TRACKS_X + TRACKS_OFFSET * 8, CRmtScreenLayout::TRACKS_Y + 8 * 16, TextColor::TURQUOISE);
        if (IsProveMode()) color = (g_activepart == Part::PART_TRACKS) ? LogicalTextColor::SELECTED_PROVE : TextColor::BLUE;
        else color = (g_activepart == Part::PART_TRACKS) ? LogicalTextColor::SELECTED : TextColor::RED;
        sprintf(s, "%02X", m_song->SongGetGo());
        canvasXY->TextXY(s, CRmtScreenLayout::TRACKS_X + TRACKS_OFFSET * 8 + 11 * 8, CRmtScreenLayout::TRACKS_Y + 8 * 16, color);
        return;
    }

    // the cursor position is alway centered regardless of the window size with this simple formula
    g_cursoractview = trackactiveline + 8 - g_line_y;

    BOOL active_smooth = (g_view.smoothScrolling && m_song->m_play && m_song->m_followplay && speed > 1) ? 1 : 0;	//could also be used as an offset
    int smooth_y = (active_smooth) ? ((speeda * 16) / speed) - 8 : 0;
    if (smooth_y > 8 || smooth_y < -8) active_smooth = smooth_y = 0;	//prevents going out of bounds
    y = (CRmtScreenLayout::TRACKS_Y + (3 - active_smooth) * 16) + smooth_y;
    x = CRmtScreenLayout::TRACKS_X + 5 * 8;

    strcpy(s, "--\x2");	//2 digits and the "|" tile on the right side

    BOOL is_goto = 0;
    CTracksControl tracksControl(tracksCanvas);
    tracksControl.SetCanvas(*canvasXY);

    // TODO -- FIXME: set the Notation elsewhere instead of computing it every time
    Notation notation = 0;	// Standard notation

    if (g_displayflatnotes) { notation += 1; }
    if (g_usegermannotation) { notation += 2; }
    if (g_notesperoctave != 12) { notation = 4; }	// Non-12 scales don't yet have proper display

    for (i = 0; i < g_tracklines + active_smooth * 2; i++, y += 16)
    {
        line = g_cursoractview + i - 8 - active_smooth;		//8 lines from above
        int oob = 0;

        int sl = songactiveline;	//offset by the oob songline counter when needed
        int ln = m_song->GetSmallestMaxtracklen(sl);

        if (line < 0)
        {
        minusline:
            oob--;
            sl = songactiveline + oob;
            if (sl < 0 || sl > 255) { sl += 256; sl %= 256; }
            ln = m_song->GetSmallestMaxtracklen(sl);

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
            ln = m_song->GetSmallestMaxtracklen(sl);

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
            canvasXY->FillSolidRect(CRmtScreenLayout::TRACKS_X, y, mask_x, 16, CRGBColor::BACKGROUND);

            //get the songline that has the goto set
            sl = songactiveline + oob;
            if (sl < 0 || sl > 255) { sl += 256; sl %= 256; }

            //if the line is 2 patterns or more away, it must also be gray
            canvasXY->TextXY("GO TO LINE ", CRmtScreenLayout::TRACKS_X + 6 * 8, y, (oob - 1) ? TextColor::DARK_GRAY : TextColor::TURQUOISE);
            sprintf(s, "%02X", m_song->m_songgo[sl]);
            canvasXY->TextXY(s, CRmtScreenLayout::TRACKS_X + 17 * 8, y, (oob - 1) ? TextColor::DARK_GRAY : TextColor::WHITE);
            break;
        }

        color = TextColor::WHITE;
        if (line % g_trackLineSecondaryHighlight == 0)  color = TextColor::GREEN;
        if (line % g_trackLinePrimaryHighlight == 0) color = TextColor::CYAN;
        if (line == trackplayline) color = TextColor::YELLOW;
        if (line == trackactiveline) color = (IsProveMode()) ? TextColor::BLUE : TextColor::RED;
        if (oob) color = TextColor::DARK_GRAY;
        canvasXY->TextXY(s, CRmtScreenLayout::TRACKS_X, y, color);

        for (int j = 0; j < g_tracks4_8; j++, x += 16 * 8)
        {
            //track in the current line of the song
            sl = songactiveline + oob;
            if (sl < 0 || sl > 255) { sl += 256; sl %= 256; }

            tr = m_song->m_song[sl][j];

            //is it playing?
            if (songplayline == songactiveline) { t = trackplayline; }
            else { t = -1; }
            tracksControl.DrawTrackLine(g_Tracks, j, x, y, tr, line, trackactiveline, g_cursoractview, t, (m_song->m_trackactivecol == j), m_song->m_trackactivecur, oob, notation);
        }
        x = CRmtScreenLayout::TRACKS_X + 5 * 8;
    }

    //mask rectangles for hiding extra rendered lines
    canvasXY->FillSolidRect(CRmtScreenLayout::TRACKS_X - 8, CRmtScreenLayout::TRACKS_Y + 1 * 16, mask_x, 32, CRGBColor::BACKGROUND);
    canvasXY->FillSolidRect(CRmtScreenLayout::TRACKS_X - 8, CRmtScreenLayout::TRACKS_Y + 2 * 16 + ((g_tracklines + 1) * 16) + 1, mask_x, 48, CRGBColor::BACKGROUND);

    //tracks
    strcpy(s, "  TRACK XX   ");
    x = CRmtScreenLayout::TRACKS_X + 5 * 8;
    y = (CRmtScreenLayout::TRACKS_Y + 3 * 16) + smooth_y;

    for (i = 0; i < g_tracks4_8; i++, x += 16 * 8)
    {
        s[8] = tnames[i * 2];
        s[9] = tnames[i * 2 + 1];

        color = (g_ChannelControl.IsChannelOn(i)) ? TextColor::WHITE : TextColor::GRAY;	//channels off are in gray
        //TextXY(s, x + 12, TRACKS_Y, color);
        canvasXY->TextXY(s, x, CRmtScreenLayout::TRACKS_Y, color);

        //track in the current line of the song
        tr = m_song->m_song[songactiveline][i];

        //g_Tracks.DrawTrackHeader(x + 24, TRACKS_Y + 16, tr, color);
        tracksControl.DrawTrackHeader(g_Tracks, x + 8, CRmtScreenLayout::TRACKS_Y + 16, tr, color);
    }

    //lines delimiting the current line
    x = mask_x;
    y = CRmtScreenLayout::TRACKS_Y + 3 * 16 - 2 + g_line_y * 16;

    canvasXY->MoveTo(CRmtScreenLayout::TRACKS_X, y);
    canvasXY->LineTo(x, y);
    canvasXY->MoveTo(CRmtScreenLayout::TRACKS_X, y + 19);
    canvasXY->LineTo(x, y + 19);

    //a line delimiting the boundary between left/right-- there is a bug with some tracks but the entire function needs to be rewritten anyway...
    if (g_tracks4_8 > 4)
    {
        y = (CRmtScreenLayout::TRACKS_Y + 3 * 16);
        int line_end = y + g_tracklines * 16;

        if (is_goto)
        {
            line_end = y + (8 - g_cursoractview + m_song->GetSmallestMaxtracklen(songactiveline)) * 16 + smooth_y;
        }

        canvasXY->MoveTo(CRmtScreenLayout::TRACKS_X + 50 * 11 - 3, y);
        canvasXY->LineTo(CRmtScreenLayout::TRACKS_X + 50 * 11 - 3, line_end);
    }

    //mask out any extra pixels after rendering each elements
    canvasXY->FillSolidRect(CRmtScreenLayout::TRACKS_X - 8, CRmtScreenLayout::TRACKS_Y + 2 * 16, mask_x, 16, CRGBColor::BACKGROUND);
    canvasXY->FillSolidRect(CRmtScreenLayout::TRACKS_X - 8, CRmtScreenLayout::TRACKS_Y + 2 * 16 + (g_tracklines + 1) * 16, mask_x, 32, CRGBColor::BACKGROUND);

    //selected block
    if (g_TrackClipboard.IsBlockSelected())
    {
        x = CRmtScreenLayout::TRACKS_X + 6 * 8 + g_TrackClipboard.m_selcol * 16 * 8 - 8;
        int xt = x + 14 * 8 + 8;

        y = (CRmtScreenLayout::TRACKS_Y + 3 * 16) + smooth_y;
        int bfro, bto;
        g_TrackClipboard.GetFromTo(bfro, bto);

        int yf = bfro - g_cursoractview + 8;
        int fls = 0;
        int yt = bto - g_cursoractview + 8 + 1;
        int tls = 0;
        int p1 = 1, p2 = 1;

        if (yf < 0) { yf = 0; p1 = 0; fls = active_smooth * 5; }
        if (yt > g_tracklines) { yt = g_tracklines; p2 = 0;  tls = active_smooth * 7; }
        if (yf < g_tracklines && yt > 0 && g_TrackClipboard.m_seltrack == m_song->SongGetActiveTrackInColumn(g_TrackClipboard.m_selcol) && g_TrackClipboard.m_selsongline == m_song->SongGetActiveLine())
        {
            //a rectangle delimiting the selected block
            CPen redpen(PS_SOLID, 1, RGB(255, 255, 255));
            CPen* origpen = (CPen*)canvasXY->SelectObject(&redpen);

            canvasXY->MoveTo(x, y - 2 - fls + yf * 16);
            canvasXY->LineTo(x, y + 2 + tls + yt * 16);
            canvasXY->MoveTo(xt, y - 2 - fls + yf * 16);
            canvasXY->LineTo(xt, y + 2 + tls + yt * 16);

            if (p1) { canvasXY->MoveTo(x, y - 2 + yf * 16); canvasXY->LineTo(xt, y - 2 + yf * 16); }
            if (p2) { canvasXY->MoveTo(x, y + 2 + yt * 16); canvasXY->LineTo(xt + 1, y + 2 + yt * 16); }

            canvasXY->SelectObject(origpen);
        }

        char tx[96];
        char s1[4], s2[4];
        GetTracklineText(s1, bfro);
        GetTracklineText(s2, bto);

        //mask out any extra pixels after rendering the selection box before drawing the infos below
        if (active_smooth)
        {
            canvasXY->FillSolidRect(CRmtScreenLayout::TRACKS_X - 8, CRmtScreenLayout::TRACKS_Y + 2 * 16, mask_x, 16, CRGBColor::BACKGROUND);
            canvasXY->FillSolidRect(CRmtScreenLayout::TRACKS_X - 8, CRmtScreenLayout::TRACKS_Y + 2 * 16 + (g_tracklines + 1) * 16, mask_x, 16, CRGBColor::BACKGROUND);
        }

        sprintf(tx, "%i line(s) [%s-%s] selected in the pattern track %02X", bto - bfro + 1, s1, s2, g_TrackClipboard.m_seltrack);
        canvasXY->TextXY(tx, CRmtScreenLayout::TRACKS_X + 4 * 8, CRmtScreenLayout::TRACKS_Y + (4 + g_tracklines) * 16, TextColor::WHITE);
        x = CRmtScreenLayout::TRACKS_X + 4 * 8 + (int)strlen(tx) * 8 + 8;

        if (g_TrackClipboard.m_all)
            strcpy(tx, "[edit ALL data]");
        else
            sprintf(tx, "[edit data ONLY for instrument %02X]", m_song->m_activeinstr);
        canvasXY->TextXY(tx, x, CRmtScreenLayout::TRACKS_Y + (4 + g_tracklines) * 16, TextColor::RED);
    }

    // Debug display at the bottom of the screen, this could be toggled on if needed 
    if (g_view.debugDisplay)
    {
        CString d;
        const auto width = (8 * 8);
        // Don't draw further more than what could fit on screen
        for (int i = 0; i < g_width / width; i++)
        {
            switch (i)
            {
            case 0: d.Format("GW=%04d", g_width); break;
            case 1: d.Format("GH=%04d", g_height); break;
            case 2: d.Format("PX=%04d", g_mouse.pointX); break;
            case 3: d.Format("PY=%04d", g_mouse.pointY); break;
            case 4: d.Format("MB=%02d", g_mouse.button); break;
            case 5: d.Format("CA=%02d", g_cursoractview); break;
            case 6: d.Format("TA=%02d", m_song->m_trackactiveline); break;
            case 7: d.Format("DY=%02d", g_mouse.pointY / 16); break;
            case 8: d.Format("GTL=%02d", g_tracklines); break;
            case 9: d.Format("OL=%02d", g_tracklines / 2); break;
            case 10: d.Format("VK=%c %02X", (char)LOWORD(MapVirtualKeyEx(g_lastKeyPressed, MAPVK_VK_TO_CHAR, NULL)), g_lastKeyPressed); break;
            case 11: d.Format("MO=%s%s", (g_shiftkey ? "S" : " "), (g_controlkey ? "C" : " ")); break;
            case 12: d.Format("WD=%02d", g_mouse.wheelDelta); break;
            default: continue;
            }
            canvasXY->TextXY(d, CRmtScreenLayout::TRACKS_X + i * width, g_height - 32, TextColor::TURQUOISE);
        }
    }
}


void CSongUI::DrawInstrument()
{
    g_Instruments.DrawInstrument(m_song->m_activeinstr);
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
void CSongUI::DrawInfo()
{
    char szBuffer[80];
    int i;
    auto selected = FALSE;
    is_editing_infos = 0;

    auto printdebug = g_view.debugDisplay;

    // Line 1: Time  BPM  PAL/NTSC  Hightlight (XX/XX)  FPS
    canvasXY->TextXY((m_song->IsNTSC()) ? "NTSC" : "PAL", CRmtScreenLayout::INFO_X + 33 * 8, CRmtScreenLayout::INFO_Y_LINE_1, TextColor::TURQUOISE);

    // 2x Line highlights XX/XX (go and override --)
    canvasXY->TextXY("HIGHLIGHT: --/--", 344, CRmtScreenLayout::INFO_Y_LINE_1, TextColor::WHITE);
    auto color = IsProveMode() ? LogicalTextColor::SELECTED_PROVE : LogicalTextColor::SELECTED;

    sprintf(szBuffer, "%02X", g_trackLinePrimaryHighlight);
    selected = (g_activepart == Part::PART_INFO && m_song->m_infoact == EditArea::FIRST_HIGHLIGHT) ? TRUE : FALSE;
    canvasXY->TextXY(szBuffer, 344 + 11 * 8, CRmtScreenLayout::INFO_Y_LINE_1, (selected) ? color : TextColor::TURQUOISE);

    sprintf(szBuffer, "%02X", g_trackLineSecondaryHighlight);
    selected = (g_activepart == Part::PART_INFO && m_song->m_infoact == EditArea::SECOND_HIGHLIGHT) ? TRUE : FALSE;
    canvasXY->TextXY(szBuffer, 344 + 14 * 8, CRmtScreenLayout::INFO_Y_LINE_1, (selected) ? color : TextColor::TURQUOISE);

    if (printdebug)
    {
        // A poor attempt at an FPS counter
        snprintf(szBuffer, 16, "%1.2f FPS", last_fps);
        canvasXY->TextXY(szBuffer, 560 - 9 * 8, CRmtScreenLayout::INFO_Y_LINE_1, TextColor::TURQUOISE);
    }

    // Line 2: Name
    if (g_activepart == Part::PART_INFO && m_song->m_infoact == EditArea::NAME) //info? && edit name?
    {
        is_editing_infos = 1;
        i = m_song->m_songnamecur;
        color = IsProveMode() ? TextColor::BLUE : TextColor::RED;
    }
    else
    {
        i = -1;
        color = TextColor::TURQUOISE;
    }
    canvasXY->TextXY("NAME:", CRmtScreenLayout::INFO_X, CRmtScreenLayout::INFO_Y_LINE_2, TextColor::WHITE);
    canvasXY->TextXYSelN(m_song->m_songname, i, CRmtScreenLayout::INFO_X + 6 * 8, CRmtScreenLayout::INFO_Y_LINE_2, color);

    // Line 3: Speed (XX/XX/X)  MaxTrackLength (XX)  (Mono/Stereo)
    canvasXY->TextXY("MUSIC SPEED: --/--/-    MAXTRACKLENGTH: --", CRmtScreenLayout::INFO_X, CRmtScreenLayout::INFO_Y_LINE_3, TextColor::WHITE);

    // 3x Speed indicators XX/XX/X (go and override --)
    color = IsProveMode() ? LogicalTextColor::SELECTED_PROVE : LogicalTextColor::SELECTED;

    sprintf(szBuffer, "%02X", m_song->m_speed);
    selected = (g_activepart == Part::PART_INFO && m_song->m_infoact == EditArea::SPEED) ? TRUE : FALSE;
    canvasXY->TextXY(szBuffer, CRmtScreenLayout::INFO_X + 13 * 8, CRmtScreenLayout::INFO_Y_LINE_3, (selected) ? color : TextColor::TURQUOISE);

    sprintf(szBuffer, "%02X", m_song->m_mainSpeed);
    selected = (g_activepart == Part::PART_INFO && m_song->m_infoact == EditArea::MAIN_SPEED) ? TRUE : FALSE;
    canvasXY->TextXY(szBuffer, CRmtScreenLayout::INFO_X + 16 * 8, CRmtScreenLayout::INFO_Y_LINE_3, (selected) ? color : TextColor::TURQUOISE);

    sprintf(szBuffer, "%X", m_song->m_instrumentSpeed);
    selected = (g_activepart == Part::PART_INFO && m_song->m_infoact == EditArea::INSTR_SPEED) ? TRUE : FALSE;
    canvasXY->TextXY(szBuffer, CRmtScreenLayout::INFO_X + 19 * 8, CRmtScreenLayout::INFO_Y_LINE_3, (selected) ? color : TextColor::TURQUOISE);

    // Max Track Length @ 40 chars
    sprintf(szBuffer, "%02X", g_Tracks.GetMaxTrackLength());
    canvasXY->TextXY(szBuffer, CRmtScreenLayout::INFO_X + 40 * 8, CRmtScreenLayout::INFO_Y_LINE_3, TextColor::TURQUOISE);

    // Mono or Stereo @ 46 chars
    canvasXY->TextXY(m_song->IsStereo() ? "STEREO-8-TRACKS" : "MONO-4-TRACKS", CRmtScreenLayout::INFO_X + 46 * 8, CRmtScreenLayout::INFO_Y_LINE_3, TextColor::TURQUOISE);

    // Line 4: (Mode)  Octive (X-X)
    int xpos = CRmtScreenLayout::INFO_X;
    int ypos = CRmtScreenLayout::INFO_Y_LINE_4;
    if (IsEditMode(EditMode::POKEY_EXPLORER_MODE))	// test mode exclusive to keyboard input for sound debugging, this cannot be set by accident unless I did something stupid
        canvasXY->TextXY("EXPLORER MODE (PITCH CALCULATIONS)", xpos, ypos, TextColor::TURQUOISE);
    else if (IsEditMode(EditMode::MIDI_CH15_MODE))	// test mode exclusive from MIDI CH15 inputs, this cannot be set by accident unless I did something stupid
        canvasXY->TextXY("EXPLORER MODE (MIDI CH15)", xpos, ypos, TextColor::TURQUOISE);
    else if (IsProveMode())
        canvasXY->TextXY((IsEditMode(EditMode::JAM_MONO_MODE)) ? "JAM MODE (MONO)" : "JAM MODE (STEREO)", xpos, ypos, TextColor::BLUE);
    else
        canvasXY->TextXY("EDIT MODE", xpos, ypos, TextColor::RED);

    sprintf(szBuffer, "OCTAVE %i-%i", m_song->m_octave + 1, m_song->m_octave + 2);
    szBuffer[6] = 0;
    canvasXY->TextXY(szBuffer, CRmtScreenLayout::INFO_X + 55 * 8, CRmtScreenLayout::INFO_Y_LINE_4, TextColor::WHITE);
    canvasXY->TextXY(szBuffer + 7, CRmtScreenLayout::INFO_X + 62 * 8, CRmtScreenLayout::INFO_Y_LINE_4, TextColor::TURQUOISE);

    // Line 5: Instrument (XX): (name)
    canvasXY->TextXY("INSTRUMENT", CRmtScreenLayout::INFO_X, CRmtScreenLayout::INFO_Y_LINE_5, TextColor::WHITE);
    sprintf(szBuffer, "%02X: %s", m_song->m_activeinstr, g_Instruments.GetName(m_song->m_activeinstr));
    szBuffer[3] = 0;
    canvasXY->TextXY(szBuffer, CRmtScreenLayout::INFO_X + 11 * 8, CRmtScreenLayout::INFO_Y_LINE_5, TextColor::WHITE);
    szBuffer[40] = 0;
    canvasXY->TextXY(szBuffer + 4, CRmtScreenLayout::INFO_X + 15 * 8, CRmtScreenLayout::INFO_Y_LINE_5, TextColor::TURQUOISE);

    sprintf(szBuffer, "%cVOLUME %X", g_respectvolume ? '*' : ' ', m_song->m_volume);	// Put a * infront of Volume if the RESPECT volume mode is on
    szBuffer[7] = 0;
    canvasXY->TextXY(szBuffer, CRmtScreenLayout::INFO_X + 56 * 8, CRmtScreenLayout::INFO_Y_LINE_5, TextColor::WHITE);
    canvasXY->TextXY(szBuffer + 8, CRmtScreenLayout::INFO_X + 64 * 8, CRmtScreenLayout::INFO_Y_LINE_5, TextColor::TURQUOISE);

    // Line 6: Under the instrument line draw small text indicating the instrument flags
    BYTE flag = g_Instruments.GetFlag(m_song->m_activeinstr);

    int x = CRmtScreenLayout::INFO_X;
    const int y = CRmtScreenLayout::INFO_Y_LINE_6;
    int activeChannel = (m_song->m_trackactivecol % 4) + 1;		// channel 1 to 4

    if (flag & IF_FILTER)
    {
        if (activeChannel > 2)
        {
            canvasXY->TextMiniXY("NO FILTER", x, y, TextMiniColor::GRAY);
            x += 10 * 8;
        }
        else
        {
            if (activeChannel == 1)
                canvasXY->TextMiniXY("AUTOFILTER(1+3)", x, y, TextMiniColor::BLUE);
            else
                canvasXY->TextMiniXY("AUTOFILTER(2+4)", x, y, TextMiniColor::BLUE);
            x += 16 * 8;
        }
    }

    if (flag & IF_BASS16)
    {
        if (activeChannel == 2)
        {
            canvasXY->TextMiniXY("BASS16(2+1)", x, y, TextMiniColor::BLUE);
            x += 12 * 8;
        }
        else if (activeChannel == 4)
        {
            canvasXY->TextMiniXY("BASS16(4+3)", x, y, TextMiniColor::BLUE);
            x += 12 * 8;
        }
        else
        {
            canvasXY->TextMiniXY("NO BASS16", x, y, TextMiniColor::GRAY);;
            x += 10 * 8;
        }
    }

    if (flag & IF_PORTAMENTO)
    {
        canvasXY->TextMiniXY("PORTAMENTO", x, y, TextMiniColor::BLUE);
        x += 11 * 8;
    }

    if (flag & IF_AUDCTL)
    {
        int audctl = g_Instruments.GetParameter(m_song->m_activeinstr, PAR_AUDCTL_15KHZ)
            | g_Instruments.GetParameter(m_song->m_activeinstr, PAR_AUDCTL_HPF_CH2) << 1
            | g_Instruments.GetParameter(m_song->m_activeinstr, PAR_AUDCTL_HPF_CH1) << 2
            | g_Instruments.GetParameter(m_song->m_activeinstr, PAR_AUDCTL_JOIN_3_4) << 3
            | g_Instruments.GetParameter(m_song->m_activeinstr, PAR_AUDCTL_JOIN_1_2) << 4
            | g_Instruments.GetParameter(m_song->m_activeinstr, PAR_AUDCTL_179_CH3) << 5
            | g_Instruments.GetParameter(m_song->m_activeinstr, PAR_AUDCTL_179_CH1) << 6
            | g_Instruments.GetParameter(m_song->m_activeinstr, PAR_AUDCTL_POLY9) << 7;
        sprintf(szBuffer, "AUDCTL:%02X", audctl);
        canvasXY->TextMiniXY(szBuffer, x, y, TextMiniColor::BLUE);
        // x += 6 * 8;
    }
}

/// <summary>
/// Draw the song play time and BPM
/// TODO: Merge with DrawInfo(), both are displayed in the same area
/// </summary>
/// <param name="pDC"></param>
//void CSong::DrawPlayTimeCounter(CDC* pDC)
void CSongUI::DrawPlayTimeCounter()
{
    if (!g_view.playTimeCounter) return;	//the timer won't be displayed without the setting enabled first

#define PLAYTC_X	16		//(SONG_OFFSET+7)
#define PLAYTC_Y	16		//(SONG_Y-8) 
#define PLAYTC_W	(32*8)	//(4*8)  
#define PLAYTC_H	16		//8 

    int fps = (m_song->IsNTSC()) ? 60 : 50;
    int ts = g_playtime / fps;							//total time in seconds
    int timesec = ts % 60;								//seconds 0 to 59
    int timemin = ts / 60;								//minutes 0 to ...
    int timemilisec = (g_playtime % fps) * 100 / fps;	//miliseconds 0 to 99
    double speed = 0.0;
    double bpm = 0.0;
    char timstr[16] = { 0 };
    char bpmstr[8] = { 0 };

    m_song->m_avgspeed[m_song->m_trackplayline % 8] = m_song->m_speed;				//refreshed every 8 rows
    for (int i = 0; i < 8; i++) speed += m_song->m_avgspeed[i];
    speed /= 8.0;											//average speed
    bpm = ((60.0 * fps) / g_trackLinePrimaryHighlight) / speed;	//average BPM 

    snprintf(timstr, 16, !(timesec & 1) ? "%2d:%02d.%02d" : "%2d %02d.%02d", timemin, timesec, timemilisec);
    snprintf(bpmstr, 8, (m_song->m_play) ? "%1.2f" : "0.00", bpm);

    canvasXY->TextXY("TIME:             BPM:", PLAYTC_X, PLAYTC_Y, TextColor::WHITE);
    canvasXY->TextXY(timstr, PLAYTC_X + 8 * 6, PLAYTC_Y, (m_song->m_play) ? TextColor::WHITE : TextColor::GRAY);
    canvasXY->TextXY(bpmstr, PLAYTC_X + 8 * 23, PLAYTC_Y, (m_song->m_play) ? TextColor::WHITE : TextColor::GRAY);

    //if (pDC) pDC->BitBlt( SCALE(PLAYTC_X), SCALE(PLAYTC_Y), SCALE(PLAYTC_W), SCALE(PLAYTC_H), g_mem_dc, SCALE(PLAYTC_X), SCALE(PLAYTC_Y), SRCCOPY);
}
