#pragma once

#include <iosfwd>

#include "Memory.h"

class CAtariIO
{

public:

    /// <summary>
    /// Load an Atari binary block. Max [4-6] byte header + the indicated number of bytes
    /// HEADER, FROM, TO
    /// HEADER is optional.
    /// </summary>
    /// <param name="in">Input stream</param>
    /// <param name="memory">Buffer that will act as Atari memory</param>
    /// <param name="fromAddr">Returns the address where the binary block was loaded (FROM)</param>
    /// <param name="toAddr">Returns the end address, (first byte after the loaded block)</param>
    /// <returns>Return number of bytes read. (0 if there was some error).</returns>
    static int LoadBinaryBlock(std::ifstream& in, byte* memory, MemoryAddress& fromadr, MemoryAddress& toadr);
    static int LoadBinaryFile(const char* fname, byte* memory, MemoryAddress& minadr, MemoryAddress& maxadr);
    static int LoadDataAsBinaryFile(unsigned char* data, MemorySize size, byte* memory, MemoryAddress& minadr, MemoryAddress& maxadr);
    static int SaveBinaryBlock(std::ofstream& out, const byte* memory, MemoryAddress fromAddr, MemoryAddress toAddr, bool withBinaryBlockHeader);

private:
    static bool LoadWord(std::ifstream& in, MemoryWord& w);

};

