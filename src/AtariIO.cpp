#include "stdafx.h"
#include "AtariIO.h"
#include <fstream>


bool CAtariIO::LoadWord(std::ifstream& in, MemoryWord& w)
{
    unsigned char a1, a2;
    char db, hb;
    if (in.eof()) { return false; }
    in.get(db);
    a1 = (unsigned char)db;
    if (in.eof()) { return false; }
    in.get(hb);
    a2 = (unsigned char)hb;
    w = a1 + (a2 << 8);
    return true;
}

int CAtariIO::LoadBinaryBlock(std::ifstream& in, byte* memory, MemoryAddress& fromAddr, MemoryAddress& toAddr)
{
    if (!LoadWord(in, fromAddr)) {
        return 0;
    }
    if (fromAddr == 0xffff)
    {
        // Skip the binary block header (0xFFFF)
        if (!LoadWord(in, fromAddr)) { return 0; }
    }
    if (!LoadWord(in, toAddr)) { return 0; }

    // Sanity check that the end is not before the start.
    if (toAddr < fromAddr) { return 0; }

    // Load the indicated number of bytes into the specified memory area
    in.read((char*)memory + fromAddr, toAddr - fromAddr + 1);
    return toAddr - fromAddr + 1;
}

int CAtariIO::LoadBinaryFile(const char* fname, byte* memory, MemoryAddress& minadr, MemoryAddress& maxadr)
{
    int fsize, blen;

    WORD bfrom, bto;

    std::ifstream fin(fname, std::ios::binary | std::ios::_Nocreate);
    if (!fin) return 0;
    fsize = 0;
    minadr = 0xffff; maxadr = 0; //the opposite limits of the minimum and maximum address
    while (!fin.eof())
    {
        blen = LoadBinaryBlock(fin, memory, bfrom, bto);
        if (blen <= 0) break;
        if (bfrom < minadr) minadr = bfrom;
        if (bto > maxadr) maxadr = bto;
        fsize += blen;
    }
    fin.close();
    return fsize;
}

int CAtariIO::LoadDataAsBinaryFile(unsigned char* data, MemorySize size, byte* memory, MemoryAddress& minadr, MemoryAddress& maxadr)
{
    if (!data)
    {
        return 0;
    }

    int blen;
    int akp = 0;
    WORD bfrom, bto;

    minadr = 0xffff; maxadr = 0; //the opposite limits of the minimum and maximum address
    while (akp < size)
    {
        bfrom = data[akp] | (data[akp + 1] << 8);
        akp += 2;
        if (bfrom == 0xffff) continue;
        bto = data[akp] | (data[akp + 1] << 8);
        akp += 2;
        blen = bto - bfrom + 1;
        if (blen <= 0) break;
        memcpy(memory + bfrom, data + akp, blen);
        akp += blen;
        if (bfrom < minadr) minadr = bfrom;
        if (bto > maxadr) maxadr = bto;
    }
    return akp;
}

/// <summary>
/// Save binary data to an output stream.
/// This is done in Atari file format.
/// 4-6 byte header with FFFF as the binary block indicator
/// followed by the START and the END addresses of the memory area.
/// </summary>
/// <param name="out">stream where data is written to</param>
/// <param name="memory">64K of memory</param>
/// <param name="fromAddr">Start address</param>
/// <param name="toAddr">End address (last byte of the data)</param>
/// <param name="withBinaryBlockHeader">True then the FROM,TO header will start with FFFF. Only required on the first block</param>
/// <returns>Total number of bytes output</returns>
int CAtariIO::SaveBinaryBlock(std::ofstream& out, const byte* memory, MemoryAddress fromAddr, MemoryAddress toAddr, bool withBinaryBlockHeader)
{
    //from "fromadr" to "toadr" inclusive
    if (fromAddr > toAddr) return 0;
    if (withBinaryBlockHeader)
    {
        out.put((char)0xff);
        out.put((char)0xff);
    }
    out.put((unsigned char)(fromAddr & 0xff));
    out.put((unsigned char)(fromAddr >> 8));
    out.put((unsigned char)(toAddr & 0xff));
    out.put((unsigned char)(toAddr >> 8));
    out.write((char*)memory + fromAddr, toAddr - fromAddr + 1);
    return toAddr - fromAddr + 1;
}
