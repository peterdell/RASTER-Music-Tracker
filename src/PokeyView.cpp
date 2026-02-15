#include "PokeyView.h"

#include "Global.h"


static MemoryAddress AUDF_ADDRESS[8] = { 0xd200,0xd202,0xd204,0xd206,0xd210,0xd212,0xd214,0xd216 };	// AUDF and AUDC for mono and stereo
static MemoryAddress AUDCTL_ADDRESS[2] = { 0xd208,0xd218 };	//AUDCTL and SKCTL


CPokeyView::CPokeyView(CCanvas& canvas) : canvas(&canvas) {

}

void CPokeyView::Draw(const CSong& m_song, const CTuning& tuning, const bool explorerMode, const CPokeyController& pokeyController, const CAtari& atari) {

    canvas->FillSolidRect(0, 0, 680, 192, CRGBColor::BACKGROUND);


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

    const auto memory = atari.GetConstMemoryAt(0);
    for (int channel = 0; channel < m_song.GetTracks(); channel++)
    {
        const BOOL IS_RIGHT_POKEY = (channel >= 4) ? 1 : 0;

        const auto audctlAddress = AUDCTL_ADDRESS[IS_RIGHT_POKEY];
        const auto audctl = memory[audctlAddress];
        const auto skctlAddress = audctlAddress + 7;
        const auto skctl = memory[audctlAddress + 7];
        const auto audfAddress = AUDF_ADDRESS[channel];
        const auto audf = memory[audfAddress];
        const auto audc = memory[AUDF_ADDRESS[channel] + 1];

        const byte vol = audc & 0x0f;
        const byte dist = audc & 0xf0;
        pitch = audf;

        if (channel % 4 == 0) {								// only in valid sawtooth channels
            audf3 = memory[AUDF_ADDRESS[channel + 2]];
        }
        else {
            audf3 = 0;
        }

        if (channel % 2 == 1)								    // only in valid 16-bit channels
        {
            audf2 = memory[AUDF_ADDRESS[channel - 1]];
            audc2 = memory[AUDF_ADDRESS[channel - 1] + 1];
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

        const int pokeyBaseRow = (IS_RIGHT_POKEY) ? 8 : 0;
        const int channelRow = pokeyBaseRow + 2 + channel;

        auto audctlRow = ((IS_RIGHT_POKEY) ? 12 : 0) + 6;
        auto skctlRow = audctlRow + 1;

        int minus = (IS_RIGHT_POKEY) ? -8 : 0;
        int audnum = (channel * 2) + minus;

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
        SAWTOOTH = (CH1_179 && CH3_179 && HPF_CH13 && (dist == 0xA0 || dist == 0xE0) && (channel == 0 || channel == 4)) ? 1 : 0;
        SAWTOOTH_INVERTED = 0;
        JOIN_16BIT = ((JOIN_12 && CH1_179 && (channel == 1 || channel == 5)) || (JOIN_34 && CH3_179 && (channel == 3 || channel == 7))) ? 1 : 0;
        JOIN_64KHZ = ((JOIN_12 && !CH1_179 && !CLOCK_15 && (channel == 1 || channel == 5)) || (JOIN_34 && !CH3_179 && !CLOCK_15 && (channel == 3 || channel == 7))) ? 1 : 0;
        JOIN_15KHZ = ((JOIN_12 && !CH1_179 && CLOCK_15 && (channel == 1 || channel == 5)) || (JOIN_34 && !CH3_179 && CLOCK_15 && (channel == 3 || channel == 7))) ? 1 : 0;
        JOIN_WRONG = (((JOIN_12 && (channel == 0 || channel == 4)) || (JOIN_34 && (channel == 2 || channel == 6))) && (vol == 0x00));	// 16-bit, invalid channel, no volume
        REVERSE_16 = (((JOIN_12 && (channel == 0 || channel == 4)) || (JOIN_34 && (channel == 2 || channel == 6))) && (vol > 0x00));	// 16-bit, invalid channel, with volume (Reverse-16)
        CLOCK_179 = ((CH1_179 && (channel == 0 || channel == 4)) || (CH3_179 && (channel == 2 || channel == 6))) ? 1 : 0;
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


        canvas->ColorMini(TextMiniColor::GRAY);
        canvas->At(0, channelRow).PrintfMini(5, "$%04hX", audfAddress).AtColumn(5).PrintMini(": $   $     PITCH = $(HZ-- - +), VOL = $, DIST = $, ");

        // TODO: This is not the loop
        canvas->At(0, audctlRow).PrintfMini(8, "$%0hX: $", audctlAddress).NextRow().PrintfMini(8, "$%0hX: $", skctlAddress);
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
        canvas->ColorMini(TextMiniColor::BLUE).At(76, channelRow).PrintMini(text);

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
            canvas->ColorMini(TextMiniColor::BLUE).At(32, audctlRow);
            if (SAWTOOTH && !SAWTOOTH_INVERTED) {
                canvas->PrintMini("CH1: HIGH PASS FILTER, SAWTOOTH");
            }
            else {
                if (SAWTOOTH && SAWTOOTH_INVERTED) {
                    canvas->PrintMini("CH1: HIGH PASS FILTER, SAWTOOTH (INVERTED)");
                }
                else {
                    canvas->PrintMini("CH1: HIGH PASS FILTER");
                }
            }
        }

        canvas->ColorMini(TextMiniColor::BLUE);
        if (HPF_CH24) {
            canvas->At(32, skctlRow).PrintMini("CH2: HIGH PASS FILTER");
        }

        if (POLY9) {
            canvas->At(11, audctlRow).PrintMini("POLY9 ENABLED");
        }

        if (TWO_TONE) {
            canvas->At(11, skctlRow).PrintMini("CH1: TWO TONE FILTER");
        }

        if (REVERSE_16)
        {
            if (channel == 0 || channel == 4) {
                canvas->At(54, audctlRow).PrintMini("CH1: REVERSE - 16 OUTPUT");
            }
            else if (channel == 2 || channel == 6) {
                canvas->At(54, audctlRow).PrintMini("CH3: REVERSE - 16 OUTPUT");
            }
        }
        canvas->ColorMini(TextMiniColor::WHITE);
        canvas->At(8, channelRow).PrintByte(audf);
        canvas->AtColumn(12).PrintByte(audc);
        canvas->AtColumn(26).PrintByte(pitch);

        if ((JOIN_16BIT || JOIN_64KHZ || JOIN_15KHZ) && !vol2) { //16-bit without Reverse-16 output
            canvas->AtColumn(28).PrintByte(audf2);
        }


        canvas->AtColumn(61).PrintByte(vol);
        canvas->AtColumn(73).PrintByte(dist);
        if (dist == 0xf0) { canvas->PrintMini("e"); }	//empty tile

        // TODO: Not in loop
        canvas->At(8, audctlRow).PrintByte(audctl);
        canvas->At(8, skctlRow).PrintByte(skctl);

        const double PITCH = tuning.GetPOKEYPPitch(audc, i_audf, audctl, channel);

        char p[12] = {};
        canvas->ColorMini(TextMiniColor::WHITE).At(32, channelRow).PrintfMini(10, "%9.2f", PITCH);
        canvas->ColorMini(TextMiniColor::GRAY).AtColumn(61).PrintMini("$"); 	//character $ to overwrite the left volume nibble TODO: Rather just print nible
        canvas->AtColumn(74).PrintMini(","); //character , to overwrite the right distortion nibble, TODO: Rather just print nible

        canvas->AtColumn(4).PrintfMini(1, "%d", audnum); 	//register number


        canvas->ColorMini(TextMiniColor::GRAY);
        if (IS_RIGHT_POKEY)
        {
            // TODO: Move out of loop
            canvas->At(0, 0).PrintMini("POKEY REGISTERS (LEFT)");
            canvas->At(0, 12).PrintMini("POKEY REGISTERS (RIGHT)");
        }
        else {
            canvas->At(0, 0).PrintMini("POKEY REGISTERS");
        }

        double basetuning = g_tuning.basetuning;	// Defined in Tuning.cpp through initialisation using input parameter
        int basenote = g_tuning.basenote;
        int reverse_basenote = (24 - basenote) % 12;	// Since things are wack I had to do this
        //int FREQ_17 = (g_ntsc) ? FREQ_17_NTSC : FREQ_17_PAL;	//useful for debugging I guess
        auto cycles = CAtari::GetFrameCycleCount(m_song.IsNTSC());
        int tracks = m_song.GetTracks();


        canvas->ColorMini(TextMiniColor::GRAY).At(0, 9).PrintMini("A- TUNING:       HZ,");
        canvas->AtColumn(0).PrintfMini(2, "%s", CNotes::GetNote(reverse_basenote)); //overwrite A- with the given basenote
        canvas->ColorMini(TextMiniColor::WHITE).AtColumn(11).PrintfMini(10, "%3.2f", basetuning);

        canvas->ColorMini(TextMiniColor::BLUE).At(21, 9).PrintMini(m_song.IsNTSC() ? "NTSC" : "PAL").NextRow();
        canvas->ColorMini(TextMiniColor::GRAY).AtColumn(0).PrintMini("FREQ17:        HZ, MAXSCREENCYCLES:      , G_TRACKS4_8:");

        canvas->ColorMini(TextMiniColor::WHITE);
        canvas->AtColumn(8).PrintfMini(7, "%d", CAtari::GetClockFrequency(m_song.IsNTSC()));
        canvas->AtColumn(35).PrintfMini(7, "%d", cycles);
        canvas->AtColumn(56).PrintfMini(1, "%d", tracks);

        const auto channel_index = pokeyController.GetChannelIndex();
        if (explorerMode && channel == channel_index)	// Debug sound, must only be run once per loops, so this prevents it being overwritten
        {
            const int row = 25;

            canvas->ColorMini(TextMiniColor::GRAY).At(0, row);
            canvas->PrintMini("COARSE_DIVISOR:    , DIVISOR:       , MODOFFSET:  , AUDF: $    , AUDC: $  ").NextRow();
            canvas->PrintMini("CH_IDX:  , MODULO:    , IS_VALID:  ").NextRow().NextRow();
            canvas->PrintMini("         HZ = ((FREQ17 / (COARSE_DIVISOR * DIVISOR)) / (AUDF + MODOFFSET)) / 2");

            const int i_audf = (JOIN_16BIT || JOIN_64KHZ || JOIN_15KHZ) ? audf16 : audf;
            const int e_audf = audf;
            const int e_audf2 = audf2;
            const int e_audc = audc;

            BOOL e_valid = TRUE;		// Always valid for now
            int e_modulo = 0;			// Does not matter right now, used in tandem with e_valid
            double e_pitch = 0;			// Always initialised to 0

            // Always initialised to 1 to avoid a division by 0 error
            int e_modoffset = 1;
            int e_coarse_divisor = 1;

            // Set the divisor and modoffset variables based on the AUDCTL bits currently set
            if (JOIN_16BIT) e_modoffset = 7;
            else if (CLOCK_179) e_modoffset = 4;
            else e_coarse_divisor = (CLOCK_15) ? 114 : 28;

            // Identify the first Modulo value that results to 0 when used
            for (int i = 3; i < 256; i++)
            {
                e_modulo = i;
                if ((e_audf + e_modoffset) % i == 0)
                    break;
            }

            canvas->ColorMini(TextMiniColor::WHITE).At(0, row);
            canvas->AtColumn(16).PrintfMini(2, "%d", e_coarse_divisor);
            canvas->AtColumn(30).PrintfMini(9, "%6.1f", divisor);
            canvas->AtColumn(49).PrintfMini(3, "%d", e_modoffset);
            canvas->AtColumn(59).PrintByte(e_audf);
            if (JOIN_16BIT || JOIN_64KHZ || JOIN_15KHZ) {
                canvas->AtColumn(61).PrintByte(e_audf2);
            }
            canvas->AtColumn(72).PrintByte(e_audc).NextRow();

            canvas->AtColumn(8).PrintfMini(3, "%d", channel_index);
            canvas->AtColumn(19).PrintfMini(3, "%d", e_modulo);
            canvas->AtColumn(34).PrintfMini(3, "%d", e_valid);


            const auto divisor = pokeyController.GetDivisor();
            e_pitch = tuning.GetPitch(i_audf, e_coarse_divisor, divisor, e_modoffset);

            canvas->ColorMini(TextMiniColor::WHITE);
            canvas->At(0, row + 3).PrintfMini(9, "%9.2f", e_pitch);
        }

        if (PITCH)	// If 0.0 is read, there is nothing to show. The volume-only mode or invalid parameters may result in this.
        {
            if (JOIN_WRONG)	// 16-bit, but wrong channels, and the volume is 0
            {
                // TODO: masking parts of the line,cursed patch but that works so who cares
                canvas->At(17, channelRow).PrintMini("eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee");
            }
            else
            {
                // Most of the lines below could get some improvements...
                const double centnum = 1200 * log2(PITCH / basetuning);
                const int notenum = (int)round(centnum * 0.01) + 60;
                const int octave = (((notenum + 96) - basenote) / 12) - 8;
                const int cents = (int)round(centnum - (notenum - 60) * 100);

                canvas->ColorMini(TextMiniColor::WHITE).At(49, channelRow).PrintfMini(3, "%03d", cents);
                canvas->ColorMini(TextMiniColor::GRAY).At(49, channelRow).PrintMini((cents >= 0) ? "+" : "-");


                int note = ((notenum + 96) - basenote) % 12;
                if (note < 0) {
                    note *= -1;	// Invert the negative to prevent going out of bounds
                }

                canvas->ColorMini(TextMiniColor::WHITE).At(44, channelRow);
                canvas->PrintfMini(2, "%s", CNotes::GetNote(note)).AtColumn(46).PrintfMini(1, "%1d", octave);

            }
        }
    }
}
