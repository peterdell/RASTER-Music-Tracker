#include "SongExporter.h"
#include "StdAfx.h"
#include <iomanip>

#include "AtariBinaries.h"
#include "AtariIO.h"

#include "GuiHelpers.h"
#include "Messages.h"

#include "ExportDlgs.h"
#include "SAPFileExportDialog.h"

#include "lzss_sap.h"
#include "LZSSFile.h"

#include "SAPFile.h"
#include "SAPFileExporter.h"

#include "WaveFileExporter.h"


// TODO: Find a better place and name
CString g_rmtmsxtext;

// CXEXFile::InitFromSong()/CSongExporter::CSongExporter()/StrToAtariVideo/
// BruteforceOptimalLZSS/ExportXEX_LZSS (xexFile overload) are implemented in
// SongExporterCore.cpp.

bool CSongExporter::ExportLZSS(CSongExport& songExport, std::ofstream& ou)
{
    const CPokeyStream& pokeyStream = songExport.GetPokeyStream();

    SetStatusBarText("Compressing data ...");

    const int frameSize = CLZSSFile::GetFrameSize(songExport.GetSong());

    // Now, create LZSS files using the SAP-R dump created earlier
    byte compressedData[RAM_SIZE]{};

    CCompressLzss lzssData;

    // TODO: add a Dialog box for proper standalone LZSS exports
    // This is a hacked up method that was added only out of necessity for a project making use of song sections separately
    // I refuse to touch RMT2LZSS ever again
    CString fn = songExport.GetFilePath();
    fn = fn.Left(fn.GetLength() - 5);	// In order to keep the filename without the extention 

    // Full tune playback up to its loop point
    int full = lzssData.LZSS_SAP(pokeyStream.GetConstStreamBuffer(), pokeyStream.GetFirstCountPoint() * frameSize, compressedData);
    if (full > 16)
    {
        //ou.open(fn + "_FULL.lzss", ios::binary);	// Create a new file for the Full section
        ou.write((char*)compressedData, full);	// Write the buffer contents to the export file
    }
    ou.close();	// Close the file, if successful, it should not be empty 

    // Intro section playback, up to the start of the detected loop point
    int intro = lzssData.LZSS_SAP(pokeyStream.GetConstStreamBuffer(), pokeyStream.GetThirdCountPoint() * frameSize, compressedData);
    if (intro > 16) // TODO: Why 16?
    {
        ou.open(fn + "_INTRO.lzss", std::ios::binary);	// Create a new file for the Intro section
        ou.write((char*)compressedData, intro);		// Write the buffer contents to the export file
    }
    ou.close();	// Close the file, if successful, it should not be empty 

    // Looped section playback, this part is virtually seamless to itself
    int loop = lzssData.LZSS_SAP(pokeyStream.GetConstStreamBuffer() + (pokeyStream.GetFirstCountPoint() * frameSize), pokeyStream.GetSecondCountPoint() * frameSize, compressedData);
    if (loop > 16)
    {
        ou.open(fn + "_LOOP.lzss", std::ios::binary);	// Create a new file for the Loop section
        ou.write((char*)compressedData, loop);		// Write the buffer contents to the export file
    }
    ou.close();	// Close the file, if successful, it should not be empty 

    ClearStatusBar();

    return true;
}

bool CSongExporter::ExportCompactLZSS(CSongExport& songExport, std::ofstream& ou)
{
    // TODO: everything related to exporting the stream buffer into small files and compress them to LZSS
    SetStatusBarText("Compressing data ...");

    const int frameSize = CLZSSFile::GetFrameSize(songExport.GetSong());

    int indexToSongline = 0;
    const CPokeyStream& pokeyStream = songExport.GetPokeyStream();
    int songlineCount = pokeyStream.GetSonglineCount();

    // Since 0 is also a valid offset, the initial values are set to -1 to prevent conflicts
    int listOfMatches[256];
    memset(listOfMatches, -1, sizeof(listOfMatches));

    // Now, create LZSS files using the SAP-R dump created earlier
    unsigned char compressedData[RAM_SIZE];

    CCompressLzss lzssData;

    // TODO: add a Dialog box for proper standalone LZSS exports
    CString fn = songExport.GetFilePath();
    fn = fn.Left(fn.GetLength() - 5);	// In order to keep the filename without the extention 
    ou.close();

    // For all songlines to index, process with comparisons and find duplicates 
    while (indexToSongline < songlineCount)
    {
        int bytesCount = pokeyStream.GetFramesPerSongline(indexToSongline) * frameSize;

        int index1 = pokeyStream.GetOffsetPerSongline(indexToSongline);
        const unsigned char* buff1 = pokeyStream.GetConstStreamBuffer() + (index1 * frameSize);

        // If there is no index already, assume the Index1 to be the first occurence 
        if (listOfMatches[indexToSongline] == -1)
            listOfMatches[indexToSongline] = index1;

        // Compare all indexes available and overwrite matching songline streams with index1's offset
        for (int i = 0; i < songlineCount; i++)
        {
            // If the bytes count between 2 songlines isn't matching, don't even bother trying
            if (bytesCount != pokeyStream.GetFramesPerSongline(i) * frameSize)
                continue;

            int index2 = pokeyStream.GetOffsetPerSongline(i);
            const unsigned char* buff2 = pokeyStream.GetConstStreamBuffer() + (index2 * frameSize);

            // If there is a match, the second index will adopt the offset of the first index
            if (!memcmp(buff1, buff2, bytesCount))
                listOfMatches[i] = index1;
        }

        // Process to the next songline index until they are all processed
        indexToSongline++;
    }
    indexToSongline = 0;

    // From here, data blocs based on the Songline index and offset will be written to file
    // This should strip away every duplicated chunks, but save just enough data for reconstructing everything 
    while (indexToSongline < songlineCount)
    {
        // Get the current index to Songline offset from the current position
        int index = listOfMatches[indexToSongline];

        // Find if this offset was already processed from a previous Songline
        for (int i = 0; i < songlineCount; i++)
        {
            // As soon as a match is found, increment the counter for how many times the index was referenced
            if (index == listOfMatches[i] && indexToSongline > i)
            {
                // I don't know anymore, at this point...
            }
        }

        // Process to the next songline index until they are all processed
        indexToSongline++;
    }

    // Create a new file for logging everything related to the procedure
    ou.open(fn + ".txt", std::ios::binary);
    ou << "This is a test that displays all duplicated SAP-R bytes from m_StreamBuffer." << std::endl;
    ou << "Each ones of the Buffer Chunks are indexed into memory using Songlines.\n" << std::endl;

    for (int i = 0; i < songlineCount; i++)
    {
        ou << "Index: " << PADHEX(2, i);
        ou << ",\t Offset (real): " << PADHEX(4, pokeyStream.GetOffsetPerSongline(i));
        ou << ",\t Offset (dupe): " << PADHEX(4, listOfMatches[i]);
        ou << ",\t Bytes (uncompressed): " << PADDEC(1, pokeyStream.GetFramesPerSongline(i) * frameSize);
        ou << ",\t Bytes (LZ16 compressed): " << PADDEC(1, lzssData.LZSS_SAP(pokeyStream.GetConstStreamBuffer() + (pokeyStream.GetOffsetPerSongline(i) * frameSize), pokeyStream.GetFramesPerSongline(i) * frameSize, compressedData));
        ou << std::endl;
    }

    ou.close();

    ClearStatusBar();

    return true;
}

bool CSongExporter::ExportSAP_R(CSongExport& songExport, std::ofstream& ou)
{

    CSAPFile sapFile;
    sapFile.SetType("R");
    if (!CSAPFileExportDialog::Show(songExport.GetSong(), sapFile)) {
        return false;
    };

    bool result = CSAPFileExporter::ExportSAP_R(songExport, sapFile, ou);
    return result;
}


bool CSongExporter::ExportSAP_B_LZSS(CSongExport& songExport, std::ofstream& ou)
{
    CSAPFile sapFile;
    sapFile.SetType("B");
    if (!CSAPFileExportDialog::Show(songExport.GetSong(), sapFile)) {
        return false;
    }

    bool result = CSAPFileExporter::ExportSAP_B_LZSS(songExport, sapFile, ou);
    return result;
}


bool CSongExporter::ExportWAV(CSongExport& songExport, std::ofstream& ou, CXPokey& pokey, byte* memory)
{
    return CWaveFileExporter::ExportWAV(songExport, ou, pokey, memory);
}

bool CSongExporter::ExportXEX_LZSS(CSongExport& songExport, std::ofstream& ou)
{
    // Create the export metadata for songname, Atari text, parameters, etc
    CXEXFile xexFile;

    if (!ShowXEXExportDialog(songExport.GetSong(), xexFile))
    {
        return false;
    }
    return ExportXEX_LZSS(songExport, xexFile, ou);
}

// bool overload (CSongExport&, CXEXFile, std::ostream&) is implemented in
// SongExporterCore.cpp.

// The XEX dialog process was split to a different function for clarity, same will be done for SAP later...
bool CSongExporter::ShowXEXExportDialog(const CSong& song, CXEXFile& xexFile)
{

    xexFile.InitFromSong(song);

    CString EOL = "\r\n";

    CExpMSXDlg dlg;
    CString str;

    str = xexFile.songname;
    // TODO Move this to InitFromSong!

    if (g_rmtmsxtext != "")
    {
        dlg.m_txt = g_rmtmsxtext;	// same from last time, making repeated exports faster
    }
    else
    {
        // 5 lines of text
        dlg.m_txt = str + EOL;
        if (xexFile.isStereo) { dlg.m_txt += "STEREO"; }
        dlg.m_txt += EOL;
        dlg.m_txt += xexFile.currentTime.Format("%d/%m/%Y") + EOL;
        dlg.m_txt += "Author: (press SHIFT key)" + EOL;
        dlg.m_txt += "Author: ???";
    }

    str.Format("Playback speed will be adjusted to %s Hz on both PAL and NTSC systems.", (xexFile.isNTSC ? "60" : "50"));
    dlg.m_speedinfo = str;

    if (dlg.DoModal() != IDOK)
    {
        return false;
    }

    g_rmtmsxtext = dlg.m_txt;
    g_rmtmsxtext.Replace("\x0d\x0d", "\x0d");	//13, 13 => 13

    // This block of code will handle all the user input text that will be inserted in the binary during the export process
    memset(xexFile.atariText, ' ', CXEXFile::ATARI_TEXT_SIZE);
    int p = 0, q = 0;
    char a;
    for (int i = 0; i < dlg.m_txt.GetLength(); i++)
    {
        a = dlg.m_txt.GetAt(i);
        if (a == '\n') { p += 40; q = 0; }
        else
        {
            xexFile.atariText[p + q] = a;
            q++;
        }
        if (p + q >= 5 * 40) {
            break;
        }
    }
    StrToAtariVideo((char*)xexFile.atariText, CXEXFile::ATARI_TEXT_SIZE);

    xexFile.rasterbarColor = dlg.m_metercolor;
    xexFile.displayRasterbar = dlg.m_meter;
    xexFile.autoRegion = dlg.m_region_auto;

    return true;
}