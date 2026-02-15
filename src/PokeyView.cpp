#include "PokeyView.h"

#include "Global.h"

static MemoryAddress POKEY_ADDRESS[2] = { 0xd200,0xd210 };
static MemoryAddress AUDF_ADDRESS[8] = { 0xd200,0xd202,0xd204,0xd206,0xd210,0xd212,0xd214,0xd216 };	// AUDF and AUDC for mono and stereo


CPokeyView::CPokeyView(CCanvas& canvas) : canvas(&canvas) {

}

void CPokeyView::Draw(const CSong& m_song, const CTuning& tuning, const bool explorerMode, const CPokeyController& pokeyController, const CAtari& atari) {
    const int tuningRow = 9;

    const int pokey1Row = 0;
    const int pokey2Row = 12;

    canvas->FillSolidRect(0, 0, 680, 192, CRGBColor::BACKGROUND);


    // Tuning
    const double basetuning = g_tuning.basetuning;	// Defined in Tuning.cpp through initialisation using input parameter
    const int basenote = g_tuning.basenote;
    const int reverse_basenote = (24 - basenote) % 12;	// Since things are wack I had to do this
    const auto cycles = CAtari::GetFrameCycleCount(m_song.IsNTSC());
    const auto tracks = m_song.GetTracks();

    canvas->ColorMini(TextMiniColor::GRAY).At(0, tuningRow).PrintMini("A- TUNING:       HZ,");
    canvas->PrintfMini(2, "%s", CNotes::GetNote(reverse_basenote)); //overwrite A- with the given basenote
    canvas->ColorMini(TextMiniColor::WHITE).AtColumn(11).PrintfMini(10, "%3.2f", basetuning);

    canvas->ColorMini(TextMiniColor::BLUE).AtColumn(21).PrintMini(m_song.IsNTSC() ? "NTSC" : "PAL").NextRow();
    canvas->ColorMini(TextMiniColor::GRAY).AtColumn(0).PrintMini("FREQ17:        HZ, MAXSCREENCYCLES:      ");

    canvas->ColorMini(TextMiniColor::WHITE);
    canvas->AtColumn(8).PrintfMini(7, "%d", CAtari::GetClockFrequency(m_song.IsNTSC()));
    canvas->AtColumn(35).PrintfMini(7, "%d", cycles);

    // Pokeys

    canvas->ColorMini(TextMiniColor::GRAY);
    int pokeyCount = 0;
    if (m_song.IsStereo())
    {
        pokeyCount = 2;
        canvas->At(0, pokey1Row).PrintMini("POKEY REGISTERS (LEFT)");
        canvas->At(0, pokey2Row).PrintMini("POKEY REGISTERS (RIGHT)");
    }
    else {
        pokeyCount = 1;
        canvas->At(0, pokey1Row).PrintMini("POKEY REGISTERS");
    }


    const auto memory = atari.GetConstMemoryAt(0);
    int channel = 0;
    for (int pokey = 0; pokey < pokeyCount; pokey++) {

        // Addresses
        const auto pokeyAddress = POKEY_ADDRESS[pokey];
        const auto audctlAddress = pokeyAddress + 0x8;
        const auto skctlAddress = pokeyAddress + 0xf;

        // Values
        const auto skctl = memory[skctlAddress];
        const auto audctl = memory[audctlAddress];

        // Bits
        const BOOL CLOCK_15 = audctl & 0x01;
        const BOOL HPF_CH24 = audctl & 0x02;
        const BOOL HPF_CH13 = audctl & 0x04;
        const BOOL JOIN_34 = audctl & 0x08;
        const BOOL JOIN_12 = audctl & 0x10;
        const BOOL CH3_179 = audctl & 0x20;
        const BOOL CH1_179 = audctl & 0x40;
        const BOOL POLY9 = audctl & 0x80;
        const BOOL TWO_TONE = (skctl == 0x8B) ? 1 : 0;

        // Rows
        const auto pokeyBaseRow = pokey * 8;
        const auto channelBaseRow = pokeyBaseRow + 2;
        const auto audctlRow = ((pokey == 0) ? pokey1Row : pokey2Row) + 6;
        const auto skctlRow = audctlRow + 1;

        // Print
        canvas->ColorMini(TextMiniColor::GRAY).At(0, audctlRow).PrintfMini(8, "$%0hX: $", audctlAddress).ColorMini(TextMiniColor::WHITE).AtColumn(8).PrintByte(audctl).NextRow();
        if (POLY9) {
            canvas->AtColumn(11).PrintMini("POLY9 ENABLED");
        }

        canvas->ColorMini(TextMiniColor::GRAY).At(0, skctlRow).PrintfMini(8, "$%0hX: $", skctlAddress).ColorMini(TextMiniColor::WHITE).AtColumn(8).PrintByte(skctl);

        canvas->ColorMini(TextMiniColor::BLUE);

        if (TWO_TONE) {
            canvas->AtColumn(11).PrintMini("CH1: TWO TONE FILTER");
        }
        if (HPF_CH24) {
            canvas->AtColumn(32).PrintMini("CH2: HIGH PASS FILTER");
        }


        for (int cx = 0; cx < 4; cx++) {

            // Addresses
            const auto audfAddress = AUDF_ADDRESS[channel];

            // Values
            const auto audf = memory[audfAddress];
            const auto audc = memory[audfAddress + 1];

            const byte vol = audc & 0x0f;
            const byte dist = audc & 0xf0;

            int audf2, audf3, audf16, audc2, vol2;

            int pitch = audf;

            if (channel % 4 == 0) {								// only in valid sawtooth channels
                audf3 = memory[AUDF_ADDRESS[channel + 2]];
            }
            else {
                audf3 = 0;
            }

            if (channel % 2 == 1)								// only in valid 16-bit channels
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

            // Compute combined modes for some special output.
            const BOOL SAWTOOTH = (CH1_179 && CH3_179 && HPF_CH13 && (dist == 0xA0 || dist == 0xE0) && (channel == 0 || channel == 4)) ? 1 : 0;
            const BOOL SAWTOOTH_INVERTED = 0;
            const BOOL JOIN_16BIT = ((JOIN_12 && CH1_179 && (channel == 1 || channel == 5)) || (JOIN_34 && CH3_179 && (channel == 3 || channel == 7))) ? 1 : 0;
            const BOOL JOIN_64KHZ = ((JOIN_12 && !CH1_179 && !CLOCK_15 && (channel == 1 || channel == 5)) || (JOIN_34 && !CH3_179 && !CLOCK_15 && (channel == 3 || channel == 7))) ? 1 : 0;
            const BOOL JOIN_15KHZ = ((JOIN_12 && !CH1_179 && CLOCK_15 && (channel == 1 || channel == 5)) || (JOIN_34 && !CH3_179 && CLOCK_15 && (channel == 3 || channel == 7))) ? 1 : 0;
            const BOOL JOIN_WRONG = (((JOIN_12 && (channel == 0 || channel == 4)) || (JOIN_34 && (channel == 2 || channel == 6))) && (vol == 0x00));	// 16-bit, invalid channel, no volume
            const BOOL REVERSE_16 = (((JOIN_12 && (channel == 0 || channel == 4)) || (JOIN_34 && (channel == 2 || channel == 6))) && (vol > 0x00));	// 16-bit, invalid channel, with volume (Reverse-16)
            const BOOL CLOCK_179 = ((CH1_179 && (channel == 0 || channel == 4)) || (CH3_179 && (channel == 2 || channel == 6))) ? 1 : 0;
            BOOL EFFECTIVE_CLOCK_15 = CLOCK_15;
            if (JOIN_16BIT || CLOCK_179) { EFFECTIVE_CLOCK_15 = 0; }	// Override, these 2 take priority over 15khz mode

            int modoffset = 1;
            int coarse_divisor = 1;
            double divisor = 1;
            int v_modulo = 0;
            bool IS_VALID = 0;

            if (JOIN_16BIT) { modoffset = 7; }
            else if (CLOCK_179) { modoffset = 4; }
            else { coarse_divisor = (CLOCK_15) ? 114 : 28; }

            const int i_audf = (JOIN_16BIT || JOIN_64KHZ || JOIN_15KHZ) ? audf16 : audf;
            const double PITCH = tuning.GetPOKEYPPitch(audc, i_audf, audctl, channel);

            // Rows
            const int channelRow = channelBaseRow + channel;

            // Print
            canvas->ColorMini(TextMiniColor::GRAY);
            canvas->At(0, channelRow).PrintfMini(5, "$%04hX", audfAddress).AtColumn(5).PrintMini(": $   $     PITCH = $     (         HZ ---  +  ), VOL = $ , DIST = $ ,");


            /*/
            if (dist == 0xC0)
            {
                int v_modulo = (CLOCK_15) ? 5 : 15;
                BOOL IS_UNSTABLE_DIST_C = ((audf + modoffset) % 5 == 0) ? 1 : 0;
                BOOL IS_BUZZY_DIST_C = ((audf + modoffset) % 3 == 0 || CLOCK_15) ? 1 : 0;
                IS_VALID = ((audf + modoffset) % v_modulo == 0) ? 0 : 1;
                if (IS_VALID)
                {
                    if (IS_BUZZY_DIST_C) {
                        canvas->AtColumn(84).PrintMini("BUZZY");
                    }
                    else if (IS_UNSTABLE_DIST_C) {
                        canvas->AtColumn(84).PrintMini("UNSTABLE");
                    }
                    else {
                        canvas->AtColumn(84).PrintMini("GRITTY");
                    }
                }
            }*/


            canvas->ColorMini(TextMiniColor::WHITE);
            canvas->AtColumn(8).PrintByte(audf);
            canvas->AtColumn(12).PrintByte(audc);
            canvas->AtColumn(26).PrintByte(pitch);

            if ((JOIN_16BIT || JOIN_64KHZ || JOIN_15KHZ) && !vol2) { //16-bit without Reverse-16 output
                canvas->AtColumn(28).PrintByte(audf2);
            }

            canvas->ColorMini(TextMiniColor::WHITE);
            canvas->AtColumn(61).PrintByte(vol);
            canvas->AtColumn(73).PrintByte(dist);
            if (dist == 0xf0) { canvas->PrintMini("e"); }	//empty tile

            canvas->ColorMini(TextMiniColor::WHITE).AtColumn(32).PrintfMini(10, "%9.2f", PITCH);
            canvas->ColorMini(TextMiniColor::GRAY).AtColumn(61).PrintMini("$"); 	//character $ to overwrite the left volume nibble TODO: Rather just print nible
            canvas->AtColumn(74).PrintMini(","); //character , to overwrite the right distortion nibble, TODO: Rather just print nible


            // Channel suffix
            const char* text = "";
            if (EFFECTIVE_CLOCK_15) {	//15khz
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

            /* TODO
            if (JOIN_16BIT)
                text = "16-BIT, 1.79MHZ";
            else if (JOIN_64KHZ)
                text= "16-BIT, 64KHZ";
            else if (JOIN_15KHZ)
                text ="16-BIT, 15KHZ";
            */
            canvas->ColorMini(TextMiniColor::BLUE).AtColumn(76).PrintMini(text);

            // AUDCTL row, channel-specific additions
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

            // SKCTL row, channel-specific additions
            canvas->ColorMini(TextMiniColor::BLUE);
            if (HPF_CH24) {
                canvas->At(32, skctlRow).PrintMini("CH2: HIGH PASS FILTER");
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

            // Pokey Explorer Mode
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
            channel++;
        }

    }
}
