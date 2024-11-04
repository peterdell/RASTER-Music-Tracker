#pragma once

// Central definition of 8-bit memory related types and constants

typedef unsigned short MemoryWord;
typedef MemoryWord MemoryAddress;
typedef size_t MemorySize;

constexpr MemorySize RAM_SIZE = 65536; 					    // Default RAM size for most Atari XL/XE machines
constexpr MemoryAddress RAM_MAX_ADDRESS = 0xBFFF;
