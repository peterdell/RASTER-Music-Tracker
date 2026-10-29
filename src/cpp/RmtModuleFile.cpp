#include "StdAfx.h"

#include "RmtModuleFile.h"

#include <cstring>
#include <sstream>

#include "AtariIO.h"

namespace {

// The zero-terminated name at mem[pos...], at most maxLength characters,
// bounded by end; advances pos past the terminator.
std::string ReadName(const unsigned char* mem, int& pos, int end, int maxLength) {
    std::string name;
    while (pos < end && static_cast<int>(name.size()) < maxLength && mem[pos] != 0) {
        name += static_cast<char>(mem[pos]);
        pos++;
    }
    pos++; // the terminator (or one past the end)
    return name;
}

} // namespace

CRmtModuleFile::CRmtModuleFile(const CRmtModule& module, int loadAddress, const std::optional<std::string>& songName, const std::optional<std::vector<std::string>>& instrumentNames)
    : m_module(module), m_loadAddress(loadAddress), m_songName(songName), m_instrumentNames(instrumentNames) {
    if (songName.has_value() != instrumentNames.has_value()) {
        throw std::invalid_argument("The song name and the instrument names are both present or both absent.");
    }
    if (instrumentNames.has_value() && instrumentNames->size() != module.GetInstruments().size()) {
        throw std::invalid_argument("There are " + std::to_string(instrumentNames->size()) + " instrument names for " + std::to_string(module.GetInstruments().size()) + " instruments.");
    }
    if (loadAddress < 0 || loadAddress + module.GetLength() - 1 > CMemory::MAX_ADDRESS) {
        std::ostringstream message;
        message << "The module of " << module.GetLength() << " bytes does not fit at $" << std::hex << loadAddress << ": its last byte would lie beyond $" << CMemory::MAX_ADDRESS << ".";
        throw CRmtModule::FormatException(message.str());
    }
}

CRmtModuleFile CRmtModuleFile::Read(const CRmtModule::Bytes& data) {
    std::vector<unsigned char> mem(CMemory::SIZE);
    std::istringstream in(std::string(data.begin(), data.end()));
    MemoryAddress fromAddr = 0;
    MemoryAddress toAddr = 0;
    if (CAtariIO::LoadBinaryBlock(in, mem.data(), fromAddr, toAddr) <= 0) {
        throw CRmtModule::FormatException("The file does not start with a binary load segment.");
    }
    int loadAddress = fromAddr;
    CRmtModule module = CRmtModule::Parse(mem.data(), fromAddr, toAddr + 1);

    if (CAtariIO::LoadBinaryBlock(in, mem.data(), fromAddr, toAddr) < 1) {
        return Stripped(module, loadAddress);
    }
    int pos = fromAddr;
    int end = toAddr + 1;
    std::string songName = ReadName(mem.data(), pos, end, SONG_NAME_MAX_LENGTH);
    std::vector<std::string> instrumentNames;
    for (size_t i = 0; i < module.GetInstruments().size(); i++) {
        instrumentNames.push_back(ReadName(mem.data(), pos, end, INSTRUMENT_NAME_MAX_LENGTH));
    }
    return CRmtModuleFile(module, loadAddress, songName, instrumentNames);
}

CRmtModule::Bytes CRmtModuleFile::Write() const {
    std::vector<unsigned char> mem(CMemory::SIZE);
    int firstByteAfterModule = m_module.Write(mem.data(), CMemory::SIZE, m_loadAddress);
    std::ostringstream out;
    CAtariIO::SaveBinaryBlock(out, mem.data(), m_loadAddress, firstByteAfterModule - 1, true);
    if (!IsStripped()) {
        int pos = firstByteAfterModule;
        auto put = [&mem, &pos](const std::string& name) {
            std::memcpy(mem.data() + pos, name.data(), name.size());
            pos += static_cast<int>(name.size());
            mem[pos++] = 0;
        };
        put(*m_songName);
        for (const std::string& name : *m_instrumentNames) {
            put(name);
        }
        CAtariIO::SaveBinaryBlock(out, mem.data(), firstByteAfterModule, pos - 1, false);
    }
    std::string bytes = out.str();
    return CRmtModule::Bytes(bytes.begin(), bytes.end());
}
