#pragma once

// Central definition of 8-bit memory related types and constants

typedef unsigned short MemoryWord;
typedef MemoryWord MemoryAddress;
typedef size_t MemorySize;

constexpr MemorySize RAM_SIZE = 65536; // Default RAM size for most Atari XL/XE machines
constexpr MemoryAddress RAM_MAX_ADDRESS = 0xBFFF;

// The 6502's memory as the file formats see it (plans/60_RMT_MODULE_CONTAINER_PLAN.md,
// M6; Memory.java in the Java port): a 64K address space of bytes, with
// address words stored low byte first.
class CMemory {
public:
    static constexpr int BITS_PER_BYTE = 8;
    static constexpr int MAX_BYTE = 0xff;
    // The bytes of an address word, low byte first.
    static constexpr int WORD_LENGTH = 2;
    // The last address of the 16-bit address space.
    static constexpr int MAX_ADDRESS = 0xffff;
    // The size of the address space: RAM_SIZE.
    static constexpr int SIZE = MAX_ADDRESS + 1;

    static int UnsignedByte(const unsigned char* mem, int offset) { return mem[offset]; }
    static int Word(const unsigned char* mem, int offset) { return SplitWord(mem, offset, offset + 1); }
    // An address word whose low and high bytes lie at two offsets - RMT's track addresses tables keep them apart.
    static int SplitWord(const unsigned char* mem, int lowOffset, int highOffset) { return mem[lowOffset] | (mem[highOffset] << BITS_PER_BYTE); }
    static unsigned char LowByte(int word) { return static_cast<unsigned char>(word & MAX_BYTE); }
    static unsigned char HighByte(int word) { return static_cast<unsigned char>((word >> BITS_PER_BYTE) & MAX_BYTE); }
    static void WriteWord(unsigned char* mem, int offset, int word) {
        mem[offset] = LowByte(word);
        mem[offset + 1] = HighByte(word);
    }
};
