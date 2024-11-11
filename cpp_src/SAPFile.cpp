#include "stdafx.h"
#include "SAPFile.h"

#include "RuntimeException.h"

CSAPFile::CSAPFile() {
    Clear();
}

void CSAPFile::Clear() {
    m_author = "";
    m_name = "";
    m_date = "";
    m_songs = 0;
    m_defsong = 0;
    m_stereo = false;
    m_ntsc = false;
    m_fastplay = 0;
    m_type = "";
    m_init = 0;
    m_player = 0;
}


CString  CSAPFile::GetAuthor() const {
    return m_author;
}

void  CSAPFile::SetAuthor(const CString& author) {
    m_author = author;
}

CString  CSAPFile::GetName() const {
    return m_name;
}

void  CSAPFile::SetName(const CString& name) {
    m_name = name;
}

CString  CSAPFile::GetDate() const {
    return m_date;
}

void  CSAPFile::SetDate(const CString& date) {
    m_date = date;
}

int  CSAPFile::GetSongs() const {
    return m_songs;
}

void  CSAPFile::SetSongs(int songs) {
    m_songs = songs;
}

int  CSAPFile::GetDefaultSong() const {
    return m_defsong;
}

void  CSAPFile::SetDefaultSong(int defaultSong) {
    m_defsong = defaultSong;
}

bool CSAPFile::IsStereo() {
    return m_stereo;
}

void  CSAPFile::SetStereo(boolean stereo) {
    m_stereo = stereo;
}

bool  CSAPFile::IsNTSC() {
    return m_ntsc;
}

void  CSAPFile::SetNTSC(boolean ntsc) {
    m_ntsc = ntsc;
}

CString  CSAPFile::GetType() const {
    return m_type;
}

void  CSAPFile::SetType(const CString& type) {
    m_type = type;
}

MemoryAddress  CSAPFile::GetInitAddress() const {
    return m_init;
}

void  CSAPFile::SetInitAddress(MemoryAddress init) {
    m_init = init;
}

MemoryAddress  CSAPFile::GetPlayerAddress() const {
    return m_player;
}

void  CSAPFile::SetPlayerAddress(MemoryAddress player) {
    m_player = player;
}


void CSAPFile::Init(const CSong& song) {
    m_author = "???";
    m_name = song.GetName();

    // Details like the number of frames do not belong into the title.
    // Instead the frames should be translated to a TIME tag.
    //  (" << g_PokeyStream.GetFirstCountPoint() << " frames)" << "\"" << EOL;	//display the total frames recorded

    CTime time = CTime::GetCurrentTime();
    m_date = time.Format("%d/%m/%Y"); // DD/MM/YYYY
    m_stereo = song.IsStereo();
    m_ntsc = song.IsNTSC();
    if (song.GetInstrumentSpeed() > 1)
    {
        switch (song.GetInstrumentSpeed())
        {
        case 2:
            m_fastplay = m_ntsc ? 131 : 156;
            break;

        case 3:
            m_fastplay = m_ntsc ? 87 : 104;
            break;

        case 4:
            m_fastplay = m_ntsc ? 66 : 78;
            break;

        default:
            m_fastplay = m_ntsc ? 262 : 312;
            break;
        }

    }
    else {
        m_fastplay = 0;
    }

    Normalize();
}

void CSAPFile::Normalize() {
    Normalize(m_author);
    Normalize(m_name);
    Normalize(m_date);
}

void CSAPFile::Export(std::ofstream& ou) {
    Normalize();
    ou << "SAP" << EOL;
    ou << "AUTHOR \"" << m_author << "\"" << EOL;
    ou << "NAME \"" << m_name << "\"" << EOL;
    ou << "DATE \"" << m_date << "\"" << EOL;
    ou << "TYPE " << m_type << EOL;

    if (m_songs > 0) {
        ou << "SONGS " << m_songs << EOL;
    }

    if (m_defsong > 0) {
        ou << "DEFSONG " << m_songs << EOL;
    }

    if (m_stereo)
    {
        ou << "STEREO" << EOL;
    }

    if (m_ntsc)
    {
        ou << "NTSC" << EOL;
    }

    if (m_fastplay > 0)
    {
        ou << "FASTPLAY " << m_fastplay << EOL;
    }

    if (m_type.IsEmpty()) {
        ThrowRuntimeException("SAP file has no type set.");

    }
    if (m_type == 'B') {
        if (m_init > 0) {
            ou << "INIT " << FormatMemoryAddress(m_init) << EOL;
        }

        if (m_player > 0) {
            ou << "PLAYER " << FormatMemoryAddress(m_player) << EOL;
        }
    }
    else if (m_type == 'R') {

    }
    else {
        ThrowRuntimeException("SAP file has type \""+m_type+"\". Only type \"B\" and \"R\" are supported for export.");
    }

    // A double EOL is necessary for making the SAP-R export functional
    ou << EOL;
}

void CSAPFile::Normalize(CString& string) {
    string.TrimRight();			// Cuts spaces after the name
    string.Replace('"', '\'');	// Replaces quotation marks with an apostrophe
}

CString CSAPFile::FormatMemoryAddress(MemoryAddress address) {
    CString line;
    line.Format("%04X", address);
    return line;

}