#include "PokeyView.h"

#include "Global.h"
#include "PokeyController.h"
#include "Tuning.h"

static int AUDF_ADDRESS[8] = { 0xd200,0xd202,0xd204,0xd206,0xd210,0xd212,0xd214,0xd216 };	// AUDF and AUDC for mono and stereo
static int AUDCTL_ADDRESS[2] = { 0xd208,0xd218 };	//AUDCTL and SKCTL

#define ANALYZER3_X	(canvas->GetOriginX()) 
static constexpr int ANALYZER3_Y = CSongScreenLayout::TRACKS_Y + 50;

extern CTuning g_Tuning;


CPokeyView::CPokeyView(CCanvas& canvas) : canvas(&canvas) {

}

void CPokeyView::Draw(CSong* m_song) {

    canvas->FillSolidRect(0, 0, 680, 192, CRGBColor::BACKGROUND);


    if (!g_view.pokeyRegisters) {
        return;
    }

    auto DEBUG_SOUND = IsEditMode(EditMode::POKEY_EXPLORER_MODE);

    int  audf2, audf3, audf16, audc2, pitch, vol2;

    // AUDCTL bits
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
        const BOOL IS_RIGHT_POKEY = (i >= 4) ? 1 : 0;

        const auto audctl = memory[AUDCTL_ADDRESS[IS_RIGHT_POKEY]];
        const auto skctl = memory[AUDCTL_ADDRESS[IS_RIGHT_POKEY] + 7];
        const auto audf = memory[AUDF_ADDRESS[i]];
        const auto audc = memory[AUDF_ADDRESS[i] + 1];

        const byte vol = audc & 0x0f;
        const byte dist = audc & 0xf0;
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
        else {
            audf2 = 0;
            audc2 = 0;
            vol2 = 0;
            audf16 = 0;
        }

        const int gapRows = (IS_RIGHT_POKEY) ? 8 : 0;
        const int gap2Rows = (IS_RIGHT_POKEY) ? 12 : 0;
        const int gapY = gapRows * 8;
        const int gap2Y = gap2Rows * 8;

        const int aRows = i + gapRows + 2;
        const int aY = aRows * 8;

        int minus = (IS_RIGHT_POKEY) ? -8 : 0;
        int audnum = (i * 2) + minus;
        char s[2];
        char p[12] = {};

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
        JOIN_WRONG = (((JOIN_12 && (i == 0 || i == 4)) || (JOIN_34 && (i == 2 || i == 6))) && (vol == 0x00));	// 16-bit, invalid channel, no volume
        REVERSE_16 = (((JOIN_12 && (i == 0 || i == 4)) || (JOIN_34 && (i == 2 || i == 6))) && (vol > 0x00));	// 16-bit, invalid channel, with volume (Reverse-16)
        CLOCK_179 = ((CH1_179 && (i == 0 || i == 4)) || (CH3_179 && (i == 2 || i == 6))) ? 1 : 0;
        if (JOIN_16BIT || CLOCK_179) { CLOCK_15 = 0; }	// Override, these 2 take priority over 15khz mode

        int modoffset = 1;
        int coarse_divisor = 1;
        double divisor = 1;
        int v_modulo = 0;
        bool IS_VALID = 0;

        if (JOIN_16BIT) { modoffset = 7; }
        else if (CLOCK_179) { modoffset = 4; }
        else { coarse_divisor = (CLOCK_15) ? 114 : 28; }

        const int i_audf = (JOIN_16BIT || JOIN_64KHZ || JOIN_15KHZ) ? audf16 : audf;
        const double PITCH = g_Tuning.GetPOKEYPPitch(audc, i_audf, audctl, i);
        snprintf(p, 10, "%9.2f", PITCH);

        canvas->ColorMini(TextMiniColor::GRAY);
        canvas->At(0, aRows).PrintMini("$D200: $   $     PITCH = $     (         HZ ---  +  ), VOL = $ , DIST = $ ,");

        // TODO: This is not the loop
        canvas->At(0, gap2Rows + 6).PrintMini("$D208: $  ").NextRow().PrintMini("$D20F: $  ");
        const auto testX = ANALYZER3_X + 8 * 76;

        const char* text = "";
        if (CLOCK_15) {	//15khz
            text = "15KHZ";
        }
        else {
            text = "64KHZ";
        }

        if (CLOCK_179) {
            text = "1.79MHZ";
        }

        if (JOIN_16BIT || JOIN_64KHZ || JOIN_15KHZ) {
            text = "16-BIT";
        }
        canvas->ColorMini(TextMiniColor::BLUE).At(76, aRows).PrintMini(text);

        /*
        if (JOIN_16BIT)
            TextMiniXY("16-BIT, 1.79MHZ", ANALYZER3_X + 8 * 76, ANALYZER3_Y + aY, TextMiniColor::BLUE);
        else if (JOIN_64KHZ)
            TextMiniXY("16-BIT, 64KHZ", ANALYZER3_X + 8 * 76, ANALYZER3_Y + aY, TextMiniColor::BLUE);
        else if (JOIN_15KHZ)
            TextMiniXY("16-BIT, 15KHZ", ANALYZER3_X + 8 * 76, ANALYZER3_Y + aY, TextMiniColor::BLUE);
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
                if (IS_BUZZY_DIST_C) TextMiniXY("BUZZY", ANALYZER3_X + 8 * 84, ANALYZER3_Y + aY, TextMiniColor::BLUE);
                else if (IS_UNSTABLE_DIST_C) TextMiniXY("UNSTABLE", ANALYZER3_X + 8 * 84, ANALYZER3_Y + aY, TextMiniColor::BLUE);
                else TextMiniXY("GRITTY", ANALYZER3_X + 8 * 84, ANALYZER3_Y + aY, TextMiniColor::BLUE);
            }
        }
        */

        if (HPF_CH13)
        {
            if (SAWTOOTH && !SAWTOOTH_INVERTED) {
                TextMiniXY("CH1: HIGH PASS FILTER, SAWTOOTH", ANALYZER3_X + 8 * 32, ANALYZER3_Y + gap2Y + 48, TextMiniColor::BLUE);
            }
            else {
                if (SAWTOOTH && SAWTOOTH_INVERTED) {
                    TextMiniXY("CH1: HIGH PASS FILTER, SAWTOOTH (INVERTED)", ANALYZER3_X + 8 * 32, ANALYZER3_Y + gap2Y + 48, TextMiniColor::BLUE);
                }
                else {
                    TextMiniXY("CH1: HIGH PASS FILTER", ANALYZER3_X + 8 * 32, ANALYZER3_Y + gap2Y + 48, TextMiniColor::BLUE);
                }
            }
        }

        if (HPF_CH24) {
            TextMiniXY("CH2: HIGH PASS FILTER", ANALYZER3_X + 8 * 32, ANALYZER3_Y + gap2Y + 48 + 8, TextMiniColor::BLUE);
        }

        if (POLY9)
            TextMiniXY("POLY9 ENABLED", ANALYZER3_X + 8 * 11, ANALYZER3_Y + gap2Y + 48, TextMiniColor::BLUE);

        if (TWO_TONE)
            TextMiniXY("CH1: TWO TONE FILTER", ANALYZER3_X + 8 * 11, ANALYZER3_Y + gap2Y + 48 + 8, TextMiniColor::BLUE);

        if (REVERSE_16)
        {
            if (i == 0 || i == 4) {
                TextMiniXY("CH1: REVERSE-16 OUTPUT", ANALYZER3_X + 8 * 54, ANALYZER3_Y + gap2Y + 48, TextMiniColor::BLUE);
            }
            else if (i == 2 || i == 6) {
                TextMiniXY("CH3: REVERSE-16 OUTPUT", ANALYZER3_X + 8 * 54, ANALYZER3_Y + gap2Y + 48 + 8, TextMiniColor::BLUE);
            }
        }

        NumberMiniXY(audf, ANALYZER3_X + 8 * 8, ANALYZER3_Y + aY, TextMiniColor::WHITE);
        NumberMiniXY(audc, ANALYZER3_X + 8 * 12, ANALYZER3_Y + aY, TextMiniColor::WHITE);
        NumberMiniXY(pitch, ANALYZER3_X + 8 * 26, ANALYZER3_Y + aY, TextMiniColor::WHITE);

        if ((JOIN_16BIT || JOIN_64KHZ || JOIN_15KHZ) && !vol2)	//16-bit without Reverse-16 output
            NumberMiniXY(audf2, ANALYZER3_X + 8 * 28, ANALYZER3_Y + aY, TextMiniColor::WHITE);

        NumberMiniXY(vol, ANALYZER3_X + 8 * 61, ANALYZER3_Y + aY, TextMiniColor::WHITE);
        NumberMiniXY(dist, ANALYZER3_X + 8 * 73, ANALYZER3_Y + aY, TextMiniColor::WHITE);
        if (dist == 0xf0) TextMiniXY("e", ANALYZER3_X + 8 * 73, ANALYZER3_Y + aY, TextMiniColor::WHITE);	//empty tile
        NumberMiniXY(audctl, ANALYZER3_X + 8 * 8, ANALYZER3_Y + gap2Y + 48, TextMiniColor::WHITE);
        NumberMiniXY(skctl, ANALYZER3_X + 8 * 8, ANALYZER3_Y + gap2Y + 48 + 8, TextMiniColor::WHITE);

        TextMiniXY(p, ANALYZER3_X + 8 * 32, ANALYZER3_Y + aY, TextMiniColor::WHITE);	//pitch calculation
        TextMiniXY("$", ANALYZER3_X + 8 * 61, ANALYZER3_Y + aY, TextMiniColor::GRAY);	//character $ to overwrite the left volume nibble
        TextMiniXY(",", ANALYZER3_X + 8 * 74, ANALYZER3_Y + aY, TextMiniColor::GRAY);	//character , to overwrite the right distortion nibble

        sprintf(s, "%d", audnum);
        TextMiniXY(s, ANALYZER3_X + 8 * 4, ANALYZER3_Y + aY, TextMiniColor::GRAY);		//register number

        canvas->ColorMini(TextMiniColor::GRAY);
        if (IS_RIGHT_POKEY)
        {
            // TODO: Move out of loop
            canvas->At(0, 0).PrintMini("POKEY REGISTERS (LEFT)");
            canvas->At(0, 12).PrintMini("POKEY REGISTERS (RIGHT)");

            // TODO: Make $D20? a $D21?
            canvas->At(3, aRows).PrintMini("1");
            canvas->At(3, gap2Rows + 6).PrintMini("1").NextRow().PrintMini("1");
        }
        else {
            canvas->At(0, 0).PrintMini("POKEY REGISTERS");
        }

        double tuning = g_tuning.basetuning;	//defined in Tuning.cpp through initialisation using input parameter
        int basenote = g_tuning.basenote;
        int reverse_basenote = (24 - basenote) % 12;	//since things are wack I had to do this
        //int FREQ_17 = (g_ntsc) ? FREQ_17_NTSC : FREQ_17_PAL;	//useful for debugging I guess
        auto cycles = CAtari::GetFrameCycleCount(m_song->IsNTSC());
        int tracks = m_song->GetTracks();


        canvas->ColorMini(TextMiniColor::GRAY).At(0, 9).PrintMini("A- TUNING:       HZ,");
        canvas->AtColumn(0).PrintfMini(2, "%s", CNotes::GetNote(reverse_basenote)); //overwrite A- with the given basenote
        canvas->ColorMini(TextMiniColor::WHITE).AtColumn(11).PrintfMini(10, "%3.2f", tuning);

        canvas->ColorMini(TextMiniColor::BLUE).At(21, 9).PrintMini(m_song->IsNTSC() ? "NTSC" : "PAL").NextRow();
        canvas->ColorMini(TextMiniColor::GRAY).AtColumn(0).PrintMini("FREQ17:        HZ, MAXSCREENCYCLES:      , G_TRACKS4_8:");

        canvas->ColorMini(TextMiniColor::WHITE);
        canvas->AtColumn(8).PrintfMini(7, "%d", CAtari::GetClockFrequency(m_song->IsNTSC()));
        canvas->AtColumn(35).PrintfMini(7, "%d", cycles);
        canvas->AtColumn(56).PrintfMini(1, "%d", tracks);

        const auto channel_index = m_song->m_PokeyController->GetChannelIndex();
        if (DEBUG_SOUND && i == channel_index)	// Debug sound, must only be run once per loops, so this prevents it being overwritten
        {
            canvas->ColorMini(TextMiniColor::GRAY).At(0, 12);
            canvas->PrintMini("COARSE_DIVISOR:    , DIVISOR:       , MODOFFSET:  , AUDF: $    , AUDC: $  ").NextRow();
            canvas->PrintMini("CH_IDX:  , MODULO:    , IS_VALID:  ").NextRow().NextRow();
            canvas->PrintMini("         HZ = ((FREQ17 / (COARSE_DIVISOR * DIVISOR)) / (AUDF + MODOFFSET)) / 2");

            const int i_audf = (JOIN_16BIT || JOIN_64KHZ || JOIN_15KHZ) ? audf16 : audf;
            const int e_audf = audf;
            const int e_audf2 = audf2;
            const int e_audc = audc;

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

            canvas->ColorMini(color).At(16, 12).PrintfMini(3, "%d", e_coarse_divisor);

            snprintf(p, 10, "%6.1f", divisor);
            TextMiniXY(p, ANALYZER3_X + 8 * 30, ANALYZER3_Y + 8 * 12, color);

            snprintf(p, 4, "%d", e_modoffset);
            TextMiniXY(p, ANALYZER3_X + 8 * 49, ANALYZER3_Y + 8 * 12, color);

            NumberMiniXY(e_audf, ANALYZER3_X + 8 * 59, ANALYZER3_Y + 8 * 12, color);
            if (JOIN_16BIT || JOIN_64KHZ || JOIN_15KHZ)
                NumberMiniXY(e_audf2, ANALYZER3_X + 8 * 61, ANALYZER3_Y + 8 * 12, color);

            NumberMiniXY(e_audc, ANALYZER3_X + 8 * 72, ANALYZER3_Y + 8 * 12, color);

            snprintf(p, 4, "%d", channel_index);
            TextMiniXY(p, ANALYZER3_X + 8 * 8, ANALYZER3_Y + 8 * 13, color);

            snprintf(p, 4, "%d", e_modulo);
            TextMiniXY(p, ANALYZER3_X + 8 * 19, ANALYZER3_Y + 8 * 13, color);

            snprintf(p, 4, "%d", e_valid);
            TextMiniXY(p, ANALYZER3_X + 8 * 34, ANALYZER3_Y + 8 * 13, color);

        }

        if (PITCH)	// If 0.0 is read, there is nothing to show. Volume Only mode or invalid parameters may return this
        {
            if (JOIN_WRONG)	//16-bit, but wrong channels, and the volume is 0
            {
                TextMiniXY("eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee", ANALYZER3_X + 8 * 17, ANALYZER3_Y + aY, TextMiniColor::GRAY);	//masking parts of the line,cursed patch but that works so who cares
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
                TextMiniXY(szBuffer, ANALYZER3_X + 8 * 49, ANALYZER3_Y + aY, TextMiniColor::WHITE);

                if (cents >= 0)
                    TextMiniXY("+", ANALYZER3_X + 8 * 49, ANALYZER3_Y + aY, TextMiniColor::GRAY);
                else
                    TextMiniXY("-", ANALYZER3_X + 8 * 49, ANALYZER3_Y + aY, TextMiniColor::GRAY);

                if (note < 0) {
                    note *= -1;	//invert the negative to prevent going out of bounds
                }

                canvas->ColorMini(TextMiniColor::WHITE).At(44, aY / 8);
                canvas->PrintfMini(2, "%s", CNotes::GetNote(note)).AtColumn(46).PrintfMini(1, "%1d", octave);

            }
        }
    }
}
