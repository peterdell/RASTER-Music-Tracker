#pragma once

#include <optional>
#include <string>
#include <vector>

#include "RmtModule.h"

// An .rmt file (plans/60_RMT_MODULE_CONTAINER_PLAN.md, M6 - RmtModuleFile.java
// in the Java port): a binary load file whose first segment holds the
// CRmtModule at its load address, followed - unless the module is "stripped" -
// by a second segment with the song's name and the name of every instrument
// present, each terminated by a zero byte, the instruments in number order
// (doc/rmt_format.md, "Segment Names"); it loads right after the module.
class CRmtModuleFile {
public:
    // Where RMT loads a module unless told otherwise.
    static constexpr int DEFAULT_LOAD_ADDRESS = 0x4000;
    static constexpr int SONG_NAME_MAX_LENGTH = 64;
    static constexpr int INSTRUMENT_NAME_MAX_LENGTH = 32;

    // songName and instrumentNames are both present or both absent (a stripped file).
    CRmtModuleFile(const CRmtModule& module, int loadAddress, const std::optional<std::string>& songName, const std::optional<std::vector<std::string>>& instrumentNames);
    static CRmtModuleFile Stripped(const CRmtModule& module, int loadAddress) { return CRmtModuleFile(module, loadAddress, std::nullopt, std::nullopt); }

    // Reads the file's bytes: the module from the first segment, the names
    // from the second one if there is any; a name a short segment does not
    // hold is empty.
    static CRmtModuleFile Read(const CRmtModule::Bytes& data);
    // The file's bytes: the module's segment with the $ffff marker, then the names segment unless stripped.
    CRmtModule::Bytes Write() const;

    const CRmtModule& GetModule() const { return m_module; }
    int GetLoadAddress() const { return m_loadAddress; }
    bool IsStripped() const { return !m_songName.has_value(); }
    const std::optional<std::string>& GetSongName() const { return m_songName; }
    const std::optional<std::vector<std::string>>& GetInstrumentNames() const { return m_instrumentNames; }

private:
    CRmtModule m_module;
    int m_loadAddress;
    std::optional<std::string> m_songName;
    std::optional<std::vector<std::string>> m_instrumentNames;
};
