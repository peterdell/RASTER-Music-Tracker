#pragma once

#include "Atari.h"
#include "Canvas.h"

class CAtariView
{
public:
    CAtariView(CCanvas& canvas);
    void Draw(const CAtari& atari);

private:
    CCanvas* canvas;

    const char* GetAtariMemoryHexString(const byte* memory, const MemoryAddress address, const MemorySize length);
};

