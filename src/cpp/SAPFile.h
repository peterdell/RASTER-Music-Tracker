#pragma once

#include "Song.h"
#include "Memory.h"

class CSAPFile
{


public:
    static constexpr int MAXSUBSONGS = 128; // Maximum number of subsongs in exported SAP file

    CSAPFile();
    void Clear();

    CString GetAuthor() const;
    void SetAuthor(const CString& author);

    CString GetName() const;
    void SetName(const CString& name);

    CString GetDate() const;
    void SetDate(const CString& date);

    int GetSongs() const;
    void SetSongs(int songs);

    int GetDefaultSong() const;
    void SetDefaultSong(int defaultSong);

    bool IsStereo();
    void SetStereo(boolean stereo);

    bool IsNTSC();
    void SetNTSC(boolean ntsc);

    CString GetType() const;
    void SetType(const CString& type);

    MemoryAddress GetInitAddress() const;
    void SetInitAddress(MemoryAddress init);

    MemoryAddress GetPlayerAddress() const;
    void SetPlayerAddress(MemoryAddress player);

    void Init(const CSong& song);
    void Normalize();
    void Export(std::ofstream& ou); // Not const, because it calls Normalize

private:
    static constexpr LPCSTR EOL = "\x0d\x0a"; // EOL format SAP format is defined to be CR/LR

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

    static void Normalize(CString& string);
    static CString FormatMemoryAddress(MemoryAddress address);

};

