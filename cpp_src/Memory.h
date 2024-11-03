#pragma once

typedef unsigned short MemoryAddress;
typedef size_t MemorySize;

constexpr MemorySize RAM_SIZE = 65536; 					    // Default RAM size for most Atari XL/XE machines
constexpr MemoryAddress RAM_MAX_ADDRESS = 0xBFFF;
