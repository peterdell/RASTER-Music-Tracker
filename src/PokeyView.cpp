#include "PokeyView.h"

#include "Global.h"
#include "PokeyController.h"
#include "Tuning.h"

static int AUDF_ADDRESS[8] = { 0xd200,0xd202,0xd204,0xd206,0xd210,0xd212,0xd214,0xd216 };	// AUDF and AUDC for mono and stereo
static int AUDCTL_ADDRESS[2] = { 0xd208,0xd218 };	//AUDCTL and SKCTL

#define ANALYZER3_X	(canvas->GetOriginX()) 
#define ANALYZER3_Y	(CSongScreenLayout::TRACKS_Y+50) 
#define ANALYZER3_S	6 
#define ANALYZER3_H	5 
#define ANALYZER3_HP 8 

extern CTuning g_Tuning;


CPokeyView::CPokeyView(CCanvas& canvas) : canvas(&canvas) {

}

void CPokeyView::Draw(CSong* m_song, int a) {

    canvas->FillSolidRect(0, 0, 680, 192, CRGBColor::BACKGROUND);

    auto DEBUG_SOUND = IsEditMode(EditMode::POKEY_EXPLORER_MODE);

    int audf, audf2, audf3, audf16, audc, audc2, audctl, skctl, pitch, dist, vol, vol2;

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

    const auto memory = g_AtariTrackerDriver->GetAtari()->GetConstMemoryAt(0);
    for (int i = 0; i < g_tracks4_8; i++)
    {
        BOOL IS_RIGHT_POKEY = (i >= 4) ? 1 : 0;

        audctl = memory[AUDCTL_ADDRESS[IS_RIGHT_POKEY]];
        skctl = memory[AUDCTL_ADDRESS[IS_RIGHT_POKEY] + 7];
        audf = memory[AUDF_ADDRESS[i]];
        audc = memory[AUDF_ADDRESS[i] + 1];

        vol = audc & 0x0f;
        dist = audc & 0xf0;
        pitch = audf;

        if (i % 4 == 0) {								// only in valid sawtooth channels
            audf3 = memory[AUDF_ADDRESS[i + 2]];
        }

        if (i % 2 == 1)								    // only in valid 16-bit channels
        {
            audf2 = memory[AUDF_ADDRESS[i - 1]];
            audc2 = memory[AUDF_ADDRESS[i - 1] + 1];
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

        // Combined modes for some special output...
        SAWTOOTH = (CH1_179 && CH3_179 && HPF_CH13 && (dist == 0xA0 || dist == 0xE0) && (i == 0 || i == 4)) ? 1 : 0;
        SAWTOOTH_INVERTED = 0;
        JOIN_16BIT = ((JOIN_12 && CH1_179 && (i == 1 || i == 5)) || (JOIN_34 && CH3_179 && (i == 3 || i == 7))) ? 1 : 0;
        JOIN_64KHZ = ((JOIN_12 && !CH1_179 && !CLOCK_15 && (i == 1 || i == 5)) || (JOIN_34 && !CH3_179 && !CLOCK_15 && (i == 3 || i == 7))) ? 1 : 0;
        JOIN_15KHZ = ((JOIN_12 && !CH1_179 && CLOCK_15 && (i == 1 || i == 5)) || (JOIN_34 && !CH3_179 && CLOCK_15 && (i == 3 || i == 7))) ? 1 : 0;
        JOIN_WRONG = (((JOIN_12 && (i == 0 || i == 4)) || (JOIN_34 && (i == 2 || i == 6))) && (vol == 0x00));	//16-bit, invalid channel, no volume
        REVERSE_16 = (((JOIN_12 && (i == 0 || i == 4)) || (JOIN_34 && (i == 2 || i == 6))) && (vol > 0x00));	//16-bit, invalid channel, with volume (Reverse-16)
        CLOCK_179 = ((CH1_179 && (i == 0 || i == 4)) || (CH3_179 && (i == 2 || i == 6))) ? 1 : 0;
        if (JOIN_16BIT || CLOCK_179) { CLOCK_15 = 0; }	// Override, these 2 take priority over 15khz mode

        int modoffset = 1;
        int coarse_divisor = 1;
        double divisor = 1;
        int v_modulo = 0;
        bool IS_VALID = 0;

        if (JOIN_16BIT) modoffset = 7;
        else if (CLOCK_179) modoffset = 4;
        else coarse_divisor = (CLOCK_15) ? 114 : 28;

        int i_audf = (JOIN_16BIT || JOIN_64KHZ || JOIN_15KHZ) ? audf16 : audf;
        PITCH = g_Tuning.GetPOKEYPPitch(audc, i_audf, audctl, i);
        snprintf(p, 10, "%9.2f", PITCH);


        if (g_view.pokeyRegisters)
        {
            TextMiniXY("$D200: $   $     PITCH = $     (         HZ ---  +  ), VOL = $ , DIST = $ ,", ANALYZER3_X, ANALYZER3_Y + a, TextMiniColor::GRAY);
            TextMiniXY("$D208: $  ", ANALYZER3_X, ANALYZER3_Y + gap2 + 48, TextMiniColor::GRAY);
            TextMiniXY("$D20F: $  ", ANALYZER3_X, ANALYZER3_Y + gap2 + 48 + 8, TextMiniColor::GRAY);

            if (CLOCK_15)	//15khz
                TextMiniXY("15KHZ", ANALYZER3_X + 8 * 76, ANALYZER3_Y + a, TextMiniColor::BLUE);
            else
                TextMiniXY("64KHZ", ANALYZER3_X + 8 * 76, ANALYZER3_Y + a, TextMiniColor::BLUE);

            if (CLOCK_179)
                TextMiniXY("1.79MHZ", ANALYZER3_X + 8 * 76, ANALYZER3_Y + a, TextMiniColor::BLUE);

            if (JOIN_16BIT || JOIN_64KHZ || JOIN_15KHZ)
                TextMiniXY("16-BIT", ANALYZER3_X + 8 * 76, ANALYZER3_Y + a, TextMiniColor::BLUE);

            /*
            if (JOIN_16BIT)
                TextMiniXY("16-BIT, 1.79MHZ", ANALYZER3_X + 8 * 76, ANALYZER3_Y + a, TextMiniColor::BLUE);
            else if (JOIN_64KHZ)
                TextMiniXY("16-BIT, 64KHZ", ANALYZER3_X + 8 * 76, ANALYZER3_Y + a, TextMiniColor::BLUE);
            else if (JOIN_15KHZ)
                TextMiniXY("16-BIT, 15KHZ", ANALYZER3_X + 8 * 76, ANALYZER3_Y + a, TextMiniColor::BLUE);
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
                    if (IS_BUZZY_DIST_C) TextMiniXY("BUZZY", ANALYZER3_X + 8 * 84, ANALYZER3_Y + a, TextMiniColor::BLUE);
                    else if (IS_UNSTABLE_DIST_C) TextMiniXY("UNSTABLE", ANALYZER3_X + 8 * 84, ANALYZER3_Y + a, TextMiniColor::BLUE);
                    else TextMiniXY("GRITTY", ANALYZER3_X + 8 * 84, ANALYZER3_Y + a, TextMiniColor::BLUE);
                }
            }
            */

            if (HPF_CH13)
            {
                if (SAWTOOTH && !SAWTOOTH_INVERTED)
                    TextMiniXY("CH1: HIGH PASS FILTER, SAWTOOTH", ANALYZER3_X + 8 * 32, ANALYZER3_Y + gap2 + 48, TextMiniColor::BLUE);
                else
                    if (SAWTOOTH && SAWTOOTH_INVERTED)
                        TextMiniXY("CH1: HIGH PASS FILTER, SAWTOOTH (INVERTED)", ANALYZER3_X + 8 * 32, ANALYZER3_Y + gap2 + 48, TextMiniColor::BLUE);
                    else
                        TextMiniXY("CH1: HIGH PASS FILTER", ANALYZER3_X + 8 * 32, ANALYZER3_Y + gap2 + 48, TextMiniColor::BLUE);
            }

            if (HPF_CH24)
                TextMiniXY("CH2: HIGH PASS FILTER", ANALYZER3_X + 8 * 32, ANALYZER3_Y + gap2 + 48 + 8, TextMiniColor::BLUE);

            if (POLY9)
                TextMiniXY("POLY9 ENABLED", ANALYZER3_X + 8 * 11, ANALYZER3_Y + gap2 + 48, TextMiniColor::BLUE);

            if (TWO_TONE)
                TextMiniXY("CH1: TWO TONE FILTER", ANALYZER3_X + 8 * 11, ANALYZER3_Y + gap2 + 48 + 8, TextMiniColor::BLUE);

            if (REVERSE_16)
            {
                if (i == 0 || i == 4)
                    TextMiniXY("CH1: REVERSE-16 OUTPUT", ANALYZER3_X + 8 * 54, ANALYZER3_Y + gap2 + 48, TextMiniColor::BLUE);
                else if (i == 2 || i == 6)
                    TextMiniXY("CH3: REVERSE-16 OUTPUT", ANALYZER3_X + 8 * 54, ANALYZER3_Y + gap2 + 48 + 8, TextMiniColor::BLUE);
            }

            NumberMiniXY(audf, ANALYZER3_X + 8 * 8, ANALYZER3_Y + a, TextMiniColor::WHITE);
            NumberMiniXY(audc, ANALYZER3_X + 8 * 12, ANALYZER3_Y + a, TextMiniColor::WHITE);
            NumberMiniXY(pitch, ANALYZER3_X + 8 * 26, ANALYZER3_Y + a, TextMiniColor::WHITE);

            if ((JOIN_16BIT || JOIN_64KHZ || JOIN_15KHZ) && !vol2)	//16-bit without Reverse-16 output
                NumberMiniXY(audf2, ANALYZER3_X + 8 * 28, ANALYZER3_Y + a, TextMiniColor::WHITE);

            NumberMiniXY(vol, ANALYZER3_X + 8 * 61, ANALYZER3_Y + a, TextMiniColor::WHITE);
            NumberMiniXY(dist, ANALYZER3_X + 8 * 73, ANALYZER3_Y + a, TextMiniColor::WHITE);
            if (dist == 0xf0) TextMiniXY("e", ANALYZER3_X + 8 * 73, ANALYZER3_Y + a, TextMiniColor::WHITE);	//empty tile
            NumberMiniXY(audctl, ANALYZER3_X + 8 * 8, ANALYZER3_Y + gap2 + 48, TextMiniColor::WHITE);
            NumberMiniXY(skctl, ANALYZER3_X + 8 * 8, ANALYZER3_Y + gap2 + 48 + 8, TextMiniColor::WHITE);

            TextMiniXY(p, ANALYZER3_X + 8 * 32, ANALYZER3_Y + a, TextMiniColor::WHITE);	//pitch calculation
            TextMiniXY("$", ANALYZER3_X + 8 * 61, ANALYZER3_Y + a, TextMiniColor::GRAY);	//character $ to overwrite the left volume nybble
            TextMiniXY(",", ANALYZER3_X + 8 * 74, ANALYZER3_Y + a, TextMiniColor::GRAY);	//character , to overwrite the right distortion nybble

            sprintf(s, "%d", audnum);
            TextMiniXY(s, ANALYZER3_X + 8 * 4, ANALYZER3_Y + a, TextMiniColor::GRAY);		//register number

            if (IS_RIGHT_POKEY)
            {
                TextMiniXY("POKEY REGISTERS (LEFT)", ANALYZER3_X, ANALYZER3_Y, TextMiniColor::GRAY);
                TextMiniXY("POKEY REGISTERS (RIGHT)", ANALYZER3_X, ANALYZER3_Y + 96, TextMiniColor::GRAY);

                TextMiniXY("1", ANALYZER3_X + 8 * 3, ANALYZER3_Y + a, TextMiniColor::GRAY);
                TextMiniXY("1", ANALYZER3_X + 8 * 3, ANALYZER3_Y + gap2 + 48, TextMiniColor::GRAY);
                TextMiniXY("1", ANALYZER3_X + 8 * 3, ANALYZER3_Y + gap2 + 48 + 8, TextMiniColor::GRAY);
            }
            else TextMiniXY("POKEY REGISTERS", ANALYZER3_X, ANALYZER3_Y, TextMiniColor::GRAY);

            double tuning = g_tuning.basetuning;	//defined in Tuning.cpp through initialisation using input parameter
            int basenote = g_tuning.basenote;
            int reverse_basenote = (24 - basenote) % 12;	//since things are wack I had to do this
            //int FREQ_17 = (g_ntsc) ? FREQ_17_NTSC : FREQ_17_PAL;	//useful for debugging I guess
            auto cycles = CAtari::GetFrameCycleCount(m_song->IsNTSC());
            int tracks = m_song->GetTracks();
            char t[12] = { 0 };

            TextMiniXY("A- TUNING:       HZ,", ANALYZER3_X, ANALYZER3_Y + 8 * 9, TextMiniColor::GRAY);
            snprintf(t, 10, "%3.2f", tuning);
            TextMiniXY(t, ANALYZER3_X + 8 * 11, ANALYZER3_Y + 8 * 9, TextMiniColor::WHITE);

            n[0] = CNotes::GetNote(reverse_basenote)[0];
            n[1] = CNotes::GetNote(reverse_basenote)[1];
            n[2] = 0;

            TextMiniXY(n, ANALYZER3_X, ANALYZER3_Y + 8 * 9, TextMiniColor::GRAY);	//overwrite A- to the given basenote

            TextMiniXY(m_song->IsNTSC() ? "NTSC" : "PAL", ANALYZER3_X + 8 * 21, ANALYZER3_Y + 8 * 9, TextMiniColor::BLUE);

            TextMiniXY("FREQ17:        HZ, MAXSCREENCYCLES:      , G_TRACKS4_8:", ANALYZER3_X, ANALYZER3_Y + 8 * 10, TextMiniColor::GRAY);
            canvas->ColorMini(TextMiniColor::WHITE);
            snprintf(t, 8, "%d", CAtari::GetClockFrequency(m_song->IsNTSC()));
            canvas->At(8, 10).TextMini(t);
            snprintf(t, 8, "%d", cycles);
            canvas->At(35, 10).TextMini(t);
            snprintf(t, 2, "%d", tracks);
            canvas->At(56, 10).TextMini(t);

            const auto channel_index = m_song->m_PokeyController->GetChannelIndex();
            if (DEBUG_SOUND && i == channel_index)	//Debug sound, must only be run once per loops, so this prevents it being overwritten
            {
                TextMiniXY("COARSE_DIVISOR:    , DIVISOR:       , MODOFFSET:  , AUDF: $    , AUDC: $  ", ANALYZER3_X, ANALYZER3_Y + 8 * 12, TextMiniColor::GRAY);
                TextMiniXY("CH_IDX:  , MODULO:    , IS_VALID:  ", ANALYZER3_X, ANALYZER3_Y + 8 * 13, TextMiniColor::GRAY);
                TextMiniXY("         HZ = ((FREQ17 / (COARSE_DIVISOR * DIVISOR)) / (AUDF + MODOFFSET)) / 2", ANALYZER3_X, ANALYZER3_Y + 8 * 15, TextMiniColor::GRAY);

                int i_audf = (JOIN_16BIT || JOIN_64KHZ || JOIN_15KHZ) ? audf16 : audf;
                int e_audf = audf;
                int e_audf2 = audf2;
                int e_audc = audc;

                BOOL e_valid = TRUE;		//always valid for now
                int e_modulo = 0;			//does not matter right now, used in tandem with e_valid
                double e_pitch = 0;			//always initialised to 0

                //always initialised to 1 to avoid a division by 0 error
                int e_modoffset = 1;
                int e_coarse_divisor = 1;

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

                const auto divisor = m_song->m_PokeyController->GetDivisor();

                e_pitch = g_Tuning.GetPitch(i_audf, e_coarse_divisor, divisor, e_modoffset);
                static constexpr auto color = TextMiniColor::WHITE;
                snprintf(p, 10, "%9.2f", e_pitch);
                TextMiniXY(p, ANALYZER3_X, ANALYZER3_Y + 8 * 15, color);

                snprintf(t, 4, "%d", e_coarse_divisor);
                TextMiniXY(t, ANALYZER3_X + 8 * 16, ANALYZER3_Y + 8 * 12, color);

                snprintf(p, 10, "%6.1f", divisor);
                TextMiniXY(p, ANALYZER3_X + 8 * 30, ANALYZER3_Y + 8 * 12, color);

                snprintf(t, 4, "%d", e_modoffset);
                TextMiniXY(t, ANALYZER3_X + 8 * 49, ANALYZER3_Y + 8 * 12, color);

                NumberMiniXY(e_audf, ANALYZER3_X + 8 * 59, ANALYZER3_Y + 8 * 12, color);
                if (JOIN_16BIT || JOIN_64KHZ || JOIN_15KHZ)
                    NumberMiniXY(e_audf2, ANALYZER3_X + 8 * 61, ANALYZER3_Y + 8 * 12, color);

                NumberMiniXY(e_audc, ANALYZER3_X + 8 * 72, ANALYZER3_Y + 8 * 12, color);

                snprintf(t, 4, "%d", channel_index);
                TextMiniXY(t, ANALYZER3_X + 8 * 8, ANALYZER3_Y + 8 * 13, color);

                snprintf(t, 4, "%d", e_modulo);
                TextMiniXY(t, ANALYZER3_X + 8 * 19, ANALYZER3_Y + 8 * 13, color);

                snprintf(t, 4, "%d", e_valid);
                TextMiniXY(t, ANALYZER3_X + 8 * 34, ANALYZER3_Y + 8 * 13, color);

            }

            if (PITCH)	//if 0.0 is read, there is nothing to show. Volume Only mode or invalid parameters may return this
            {
                if (JOIN_WRONG)	//16-bit, but wrong channels, and the volume is 0
                {
                    TextMiniXY("eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee", ANALYZER3_X + 8 * 17, ANALYZER3_Y + a, TextMiniColor::GRAY);	//masking parts of the line,cursed patch but that works so who cares
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
                    TextMiniXY(szBuffer, ANALYZER3_X + 8 * 49, ANALYZER3_Y + a, TextMiniColor::WHITE);

                    if (cents >= 0)
                        TextMiniXY("+", ANALYZER3_X + 8 * 49, ANALYZER3_Y + a, TextMiniColor::GRAY);
                    else
                        TextMiniXY("-", ANALYZER3_X + 8 * 49, ANALYZER3_Y + a, TextMiniColor::GRAY);

                    if (note < 0)
                        note *= -1;	//invert the negative to prevent going out of bounds

                    const auto noteString = CNotes::GetNote(note);
                    n[0] = noteString[0];
                    n[1] = noteString[1];
                    n[2] = 0;

                    sprintf(szBuffer, "%1d", octave);
                    TextMiniXY(n, ANALYZER3_X + 8 * 44, ANALYZER3_Y + a, TextMiniColor::WHITE);
                    TextMiniXY(szBuffer, ANALYZER3_X + 8 * 46, ANALYZER3_Y + a, TextMiniColor::WHITE);

                }
            }
        }
    }
}