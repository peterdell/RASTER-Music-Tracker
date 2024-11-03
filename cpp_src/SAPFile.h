#pragma once

#include "Song.h"
#include "Memory.h"

class CSAPFile
{


public:
    static constexpr int MAXSUBSONGS = 128; // Maximum number of subsongs in exported SAP file

    CString m_author;
    CString m_name;
    CString m_date;
    int m_songs;
    int m_defsong;
    bool m_stereo;
    bool m_ntsc;
    int m_fastplay;
    CString m_type;
    MemoryAddress m_init;
    MemoryAddress m_player;

    CSAPFile();
    void Clear();
    void Init(const CSong& song);
    void Normalize();
    void Export(std::ofstream& ou); // Not const, because it calls Normalize

private:
    static constexpr LPCSTR EOL = "\x0d\x0a"; // EOL format SAP format is defined to be CR/LR

    static void Normalize(CString& string);
    static CString FormatMemoryAddress(MemoryAddress address);

};

