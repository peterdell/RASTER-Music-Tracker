#include "TracksControl.h"
#include "GuiHelpers.h"

#include "Global.h"
#include "IOHelpers.h"

#include "Notes.h"

CTracksControl::CTracksControl() {

}

CTracksControl::~CTracksControl() {

}

void CTracksControl::DrawTrackHeader(const CTracks& tracks, int x, int y, int tr, TextColor col)
{
    auto * tt = tracks.GetConstTrack(tr);
    CString s = "--  -----";

    if (tt)
    {
        s.Format(tracks.IsValidTrack(tr) ? "%02X: " : "--  ", tr);
        if (tracks.IsEmptyTrack(tr)) s.AppendFormat("EMPTY");
        else
        {
            s.AppendFormat(tracks.IsValidLength(tt->len) ? "%02X-" : "---", tt->len);
            s.AppendFormat(tracks.IsValidGo(tt->go) ? "%02X" : "--", tt->go);
        }
    }

    TextXY(s, x, y, col);
    TextXYSelN("<>", -1, x + 8 * 11, y, col);
    TextMiniXY("FX1", x + 8 * 10, y - 8);
}

void CTracksControl::DrawTrackLine(const CTracks& tracks, int col, int x, int y, int tr, int line, int aline, int cactview, int pline, BOOL isactive, int acu, int oob)
{
    const TTrack* tt;
    char s[16] = " \x8\x8\x8 \x8\x8 \x8\x8 \x8\x8\x8";
    int len = -1, last = -1, go = -1;
    auto color = TextColor::WHITE;
    int n, xline;

    if (tt = tracks.GetConstTrack(tr))
    {
        strcpy(s, " --- -- -- ---");

        len = tt->len;
        go = tt->go;
        last = go >= 0 ? tracks.GetMaxTrackLength() : len;
        xline = line < len || go < 0 ? line : ((line - len) % (len - go)) + go;

        if (go >= 0) s[0] = line == len - 1 ? '\x10' : ' '; // Left-up arrow or nothing
        if (line == go) s[0] = line == len - 1 ? '\x11' : '\x0F';	// Left-up-right or up-right arrow

        if ((n = tt->note[xline]) >= 0)
        {
            int octave = (n / g_notesperoctave) + 1 + 0x30;	// Due to ASCII characters
            int note = n % g_notesperoctave;

            // TODO -- FIXME: set the Notation elsewhere instead of computing it every time
            Notation notation = 0;	// Standard notation

            if (g_displayflatnotes) notation += 1;
            if (g_usegermannotation) notation += 2;
            if (g_notesperoctave != 12) notation = 4;	// Non-12 scales don't yet have proper display

            const auto noteAndScale = CNotes::GetNoteAndScale(notation, note);
            s[1] = noteAndScale[0];	// B
            s[2] = noteAndScale[1];	// -
            s[3] = octave;							// 1
        }

        // Instrument
        if ((n = tt->instr[xline]) >= 0)
        {
            s[5] = CharH4(n);
            s[6] = CharL4(n);
        }

        // Volume
        if ((n = tt->volume[xline]) >= 0)
        {
            s[8] = 'v';
            s[9] = CharL4(n);
        }

        // Speed
        if ((n = tt->speed[xline]) >= 0)
        {
            s[11] = 'F';		// Fxx is for speed commands, but eventually, more commands could be used...
            s[12] = CharH4(n);
            s[13] = CharL4(n);
        }

        // Display the line highlight colors only in valid patterns
        if (line % g_trackLineSecondaryHighlight == 0)  color = TextColor::GREEN;
        if (line % g_trackLinePrimaryHighlight == 0) color = TextColor::CYAN;
    }

    // The displayed colors are set from lowest to highest priority, depending on the matching conditions
    if (line >= len) color = TextColor::GRAY;
    if (line == pline) color = TextColor::YELLOW;
    if (line == aline) color = (g_prove) ? TextColor::BLUE : TextColor::RED;
    if (oob) color = TextColor::DARK_GRAY;

    // Output the constructed row once it's ready, using the cursor position for highlighted column 
    //TextXYCol(s, x, y, colac[g_activepart == PART_TRACKS && (isactive && line == aline && !oob) ? acu : 4], color);
    TextXYCol(s, x, y, g_activepart == Part::PART_TRACKS && (isactive && line == aline && !oob) ? acu : -1, color);

    // Mark the end of a pattern here, if it ends on the next line
    if (line + 1 == last && len > 0 && last != tracks.GetMaxTrackLength())
    {
        TextXY("\x0B\x0B\x0B\x0B\x0B\x0B\x0B\x0B\x0B\x0B\x0B\x0B\x0B", x + 7, y + 13, (oob) ? TextColor::DARK_GRAY : TextColor::WHITE);
    }

}