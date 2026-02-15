#include "AtariView.h"
#include <assert.h>



CAtariView::CAtariView(CCanvas& canvas) : canvas(&canvas) {

}

const char* CAtariView::GetAtariMemoryHexString(const byte* memory, const MemoryAddress address, const MemorySize length)
{
    static constexpr MemorySize MAX_LENGTH = 256;

    assert(length < MAX_LENGTH);
    static char g_debugmem[6 + MAX_LENGTH * 4 + 1];

    auto p = g_debugmem;
    sprintf(p, "$%04hX ", address);
    p += 6;

    for (int i = 0; i < length; i++)
    {
        auto a = memory[address + i];
        sprintf(p, "$%02hX ", a);
        p += 4;
    }
    *p = 0;
    return g_debugmem;
}

void CAtariView::Draw(const CAtari& atari) {

    static constexpr int ADDRESS = 0x3000; // RMTPLAYR_TABLES;
    static constexpr int BPL = 32;
    static constexpr int BLOCK = 8;


    const auto memory = atari.GetConstMemoryAt(0);

    canvas->ColorMini(TextMiniColor::GRAY).PrintMini("MEMORY").NextRow().NextRow();
    canvas->ColorMini(TextMiniColor::WHITE);
    for (int d = 0; d < 32; d++) {
        const auto text = GetAtariMemoryHexString(memory, ADDRESS + BPL * d, BPL);
        canvas->PrintMini(text).NextRow();
        if (d % BLOCK == BLOCK - 1) { canvas->NextRow(); }
    }

}