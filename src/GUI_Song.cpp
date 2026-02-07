#include "GuiHelpers.h"
#include "StdAfx.h"

// MFC interface code
#include "Song.h"

#include "AtariTrackerDriver.h"
#include "EffectsDlg.h"

#include "Keyboard.h"
#include "Notes.h"
#include "PokeyController.h"

#include "Clipboard.h"
#include "Global.h"
#include "Instruments.h"
#include "Song.h"

#include "Keyboard2NoteMapping.h"


#include "Rmt.h"


extern CRmtApp g_app;
extern CSong g_Song;

extern CInstruments	g_Instruments;
extern CTrackClipboard g_TrackClipboard;

extern CAtariTrackerDriver* g_AtariTrackerDriver;

int GetTracks() {
    return g_Song.GetTracks();
};

// ----------------------------------------------------------------------------
// Support routines

BOOL IsNotAMovementVKey(int vk)
{
    //returns 1 if it is not a scroll key
    return (vk != VK_RIGHT && vk != VK_LEFT && vk != VK_UP && vk != VK_DOWN && vk != VK_TAB && vk != 13 && vk != VK_HOME && vk != VK_END && vk != VK_PRIOR && vk != VK_NEXT && vk != VK_CAPITAL);
}

void CSong::SetRMTTitle()
{
    // Prevent exceptions in headless mode
    auto window = AfxGetApp()->GetMainWnd();
    if (window != nullptr) {
        CString s, s1;
        if (m_filename == "")
        {
            if (g_changes)
            {
                s = "Noname *";
            }
            else
            {
                s = g_app.GetVersionAndBuild();
            }
        }
        else
        {
            s = m_filename;
            if (g_changes) { s += " *"; }
        }
        AfxGetApp()->GetMainWnd()->SetWindowText(s);
    }
}

int CSong::WarnUnsavedChanges()
{
    //returns 1 upon cancelation
    if (!g_changes) return 0;
    int r = MessageBox(g_hwnd, "Save current changes?", "Current song has been changed", MB_YESNOCANCEL | MB_ICONQUESTION);
    if (r == IDCANCEL) return 1;
    if (r == IDYES)
    {
        FileSave();
        SetRMTTitle();
        if (g_changes) return 1; //failed to save or canceled
    }
    return 0;
}



void NextEditArea(EditArea& editArea) {
    ((int&)editArea)++;
}

void PreviousEditArea(EditArea& editArea) {
    ((int&)editArea)--;

}

BOOL CSong::InfoKey(int vk, int shift, int control)
{
    int i, num;
    int volatile* infptab[] = { &m_speed, &m_mainSpeed, &m_instrumentSpeed, &g_trackLinePrimaryHighlight, &g_trackLineSecondaryHighlight };
    int infandtab[] = { 0xFF, 0xFF, 0x08, g_Tracks.GetMaxTrackLength() / 2, g_Tracks.GetMaxTrackLength() / 2 };	//maximum current speed, main speed, instrument speed, primary and secondary line highlights
    int areaIndex = (int)m_infoact;
    int volatile& infp = *infptab[areaIndex - 1];
    int infand = infandtab[areaIndex - 1];
    BOOL CAPSLOCK = GetKeyState(VK_CAPITAL);

    if (m_infoact == EditArea::NAME)
    {
        is_editing_infos = 1;
        if (vk == VK_DIVIDE || vk == VK_MULTIPLY || vk == VK_ADD || vk == VK_SUBTRACT) goto edit_ok;	//a workaround so the Octave and Volume can be set anywhere
        if (((!CAPSLOCK && shift) || (CAPSLOCK && !shift)) && (vk == VK_LEFT || vk == VK_RIGHT)) goto edit_ok;	//a workaround so the active instrument can be set anywhere
        if (IsNotAMovementVKey(vk))
        {	//saves undo only if it is not cursor movement
            g_Undo.ChangeInfo(0, UETYPE_INFODATA);
        }
        if (EditText(vk, shift, control, m_songname, m_songnamecur, SONG_NAME_MAX_LEN)) { m_infoact = EditArea::SPEED; }
        return 1;
    }

    if ((num = NumbKey(vk)) >= 0 && num <= infand)
    {
        if (m_infoact >= EditArea::SPEED && m_infoact <= EditArea::INSTR_SPEED)
        {
            i = infp & 0x0f; //lower digit (hex)
            if (infand < 0x0f)
            {
                if (num <= infand) i = num;
            }
            else
                i = ((i << 4) | num) & infand;
        }
        //couldn't quite get decimal to work yet... 
        else if (m_infoact == EditArea::FIRST_HIGHLIGHT || m_infoact == EditArea::SECOND_HIGHLIGHT)
        {
            //if (num > 9) return 0;	//must only accept characters between 0 and 9
            //i = infp & 9; //lower digit (dec)
            //if (infand < 9)
            i = infp & 0x0f; //lower digit (hex)
            if (infand < 0x0f)
            {
                if (num <= infand) i = num;
            }
            else
            {
                i = (i << 4) | num;
                if (i > infand) i = infand;
                //i = ((i * 10) | num) & infand;
                //if (i > infand) i = infand;
            }

        }
        if (i <= 0) i = 1;	//all values must be at least 1
        g_Undo.ChangeInfo(0, UETYPE_INFODATA);
        infp = i;
        return 1;
    }
edit_ok:
    switch (vk)
    {
    case VK_TAB:
        if (control) break;	//do nothing
        if (shift)
        {
            m_infoact = EditArea::NAME;	//Shift+TAB => Name
            is_editing_infos = 1;
        }
        else
        {
            //TAB => Speed variables 1, 2 or 3, or line highlights 4 or 5 
            if (m_infoact < EditArea::SECOND_HIGHLIGHT) {
                auto value = (int)m_infoact; // TODO: Rather have a switch/case chain
                m_infoact = (EditArea)(value++);
            }
            else {
                m_infoact = EditArea::SPEED;
            }
            is_editing_infos = 0;
        }
        return 1;

    case VK_UP:
        if (control && shift) break;	//do nothing
        if (control) goto IncrementInfoPar;
        break;

    case VK_DOWN:
        if (control && shift) break;	//do nothing
        if (control) goto DecrementInfoPar;
        break;

    case VK_LEFT:
    {
        if (control && shift) break;	//do nothing
        if (control)
        {
        DecrementInfoPar:
            i = infp;
            i--;
            if (i <= 0) i = infand; //value must be at least 1, roll back to the maximum defined earlier 
            g_Undo.ChangeInfo(0, UETYPE_INFODATA);
            infp = i;
        }
        else if (!CAPSLOCK && shift || (CAPSLOCK && !shift && is_editing_infos) || (CAPSLOCK && shift && !is_editing_infos))
        {
            ActiveInstrPrev();
        }
        else
        {
            if (m_infoact > EditArea::SPEED)
            {
                if (m_infoact == EditArea::FIRST_HIGHLIGHT)
                {
                    m_infoact = EditArea::SECOND_HIGHLIGHT;
                }
                else {
                    PreviousEditArea(m_infoact);
                }
            }
            else
            {
                m_infoact = EditArea::INSTR_SPEED;
            }
        }
    }
    return 1;

    case VK_RIGHT:
    {
        if (control && shift) break;	//do nothing
        if (control)
        {
        IncrementInfoPar:
            i = infp;
            i++;
            if (i > infand) i = 1;	//value must be at least 1
            g_Undo.ChangeInfo(0, UETYPE_INFODATA);
            infp = i;
        }
        else if (!CAPSLOCK && shift || (CAPSLOCK && !shift && is_editing_infos) || (CAPSLOCK && shift && !is_editing_infos))
        {
            ActiveInstrNext();
        }
        else
        {
            if (m_infoact >= EditArea::SPEED && m_infoact < EditArea::FIRST_HIGHLIGHT)
            {
                NextEditArea(m_infoact);
                if (m_infoact > EditArea::INSTR_SPEED) {
                    m_infoact = EditArea::SPEED;
                }
            }
            else if (m_infoact >= EditArea::FIRST_HIGHLIGHT)
            {
                NextEditArea(m_infoact);
                if (m_infoact > EditArea::SECOND_HIGHLIGHT) { m_infoact = EditArea::FIRST_HIGHLIGHT; }
            }
        }
    }
    return 1;

    case VK_RETURN:
        g_activepart = g_active_ti;
        return 1;

    case VK_MULTIPLY:
    {
        OctaveUp();
        return 1;
    }
    break;

    case VK_DIVIDE:
    {
        OctaveDown();
        return 1;
    }
    break;

    case VK_ADD:
    {
        VolumeUp();
        return 1;
    }

    case VK_SUBTRACT:
    {
        VolumeDown();
        return 1;
    }

    }
    return 0;
}

BOOL CSong::InstrKey(int vk, int shift, int control)
{
    //note: if returning 1, then screenupdate is done in RmtView
    TInstrument* ai = g_Instruments.GetInstrument(m_activeinstr);
    int& ap = ai->parameters[ai->editParameterNr];
    int& ae = ai->envelope[ai->editEnvelopeX][ai->editEnvelopeY];
    int& at = ai->noteTable[ai->editNoteTableCursorPos];
    int i;

    BOOL CAPSLOCK = GetKeyState(VK_CAPITAL);

    if (!control && !shift && NumbKey(vk) >= 0)
    {
        if (ai->activeEditSection == InstrumentSection::PARAMETERS) //parameters
        {
            int pmax = shpar[ai->editParameterNr].maxParameterValue;
            int pfrom = shpar[ai->editParameterNr].displayOffset;
            if (NumbKey(vk) > pmax + pfrom) return 0;
            i = ap + pfrom;
            i &= 0x0f; //lower digit
            if (pmax + pfrom > 0x0f)
            {
                i = (i << 4) | NumbKey(vk);
                if (i > pmax + pfrom)
                    i &= 0x0f;		//leaves only the lower digit
            }
            else
            {
                if (NumbKey(vk) >= pfrom) i = NumbKey(vk);
            }
            i -= pfrom;
            if (i < 0) i = 0;
            g_Undo.ChangeInstrument(m_activeinstr, 0, UETYPE_INSTRDATA);
            ap = i;
            goto ChangeInstrumentPar;
        }
        else if (ai->activeEditSection == InstrumentSection::ENVELOPE) //envelope
        {
            int eand = shenv[ai->editEnvelopeY].pand;
            int num = NumbKey(vk);
            i = num & eand;
            if (i != num) return 0; //something else came out after and number pressed out of range
            g_Undo.ChangeInstrument(m_activeinstr, 0, UETYPE_INSTRDATA);
            ae = i;
            //shift to the right
            i = ai->editEnvelopeX;
            if (i < ai->parameters[PAR_ENV_LENGTH]) i++;	// else i=0; //length of env
            ai->editEnvelopeX = i;
            goto ChangeInstrumentEnv;
        }
        else if (ai->activeEditSection == InstrumentSection::NOTETABLE) //table
        {
            int num = NumbKey(vk);
            i = ((at << 4) | num) & 0xff;
            g_Undo.ChangeInstrument(m_activeinstr, 0, UETYPE_INSTRDATA);
            at = i;
            goto ChangeInstrumentTab;
        }
    }

    //for name, parameters, envelope and table
    switch (vk)
    {
    case VK_TAB:
        if (control) break;	//do nothing
        if (ai->activeEditSection == InstrumentSection::NAME) {
            break;	//is editing text
        }
        if (shift)
        {
            ai->activeEditSection = InstrumentSection::NAME;	//Shift+TAB => Name
            g_isEditingInstrumentName = 1;
        }
        else
        {
            switch (ai->activeEditSection) {  //TAB 1,2,3
            case InstrumentSection::PARAMETERS:
                ai->activeEditSection = InstrumentSection::ENVELOPE;
                break;
            case InstrumentSection::ENVELOPE:
                ai->activeEditSection = InstrumentSection::NOTETABLE;
                break;
            case InstrumentSection::NOTETABLE:
                ai->activeEditSection = InstrumentSection::PARAMETERS;
                break;
            }

            g_isEditingInstrumentName = 0;
        }
        g_Undo.Separator();
        return 1;

    case VK_LEFT:
        if (!control && (!CAPSLOCK && shift || (CAPSLOCK && !shift && g_isEditingInstrumentName) || (CAPSLOCK && shift && !g_isEditingInstrumentName)))
        {
            ActiveInstrPrev();
            return 1;
        }
        break;

    case VK_RIGHT:
        if (!control && (!CAPSLOCK && shift || (CAPSLOCK && !shift && g_isEditingInstrumentName) || (CAPSLOCK && shift && !g_isEditingInstrumentName)))
        {
            ActiveInstrNext();
            return 1;
        }
        break;

    case VK_UP:
    case VK_DOWN:
        if (CAPSLOCK && shift) break;

        if (shift && !control) return 0;	//the combination Shift + Control + UP / DOWN is enabled for edit ENVELOPE and TABLE
        if (shift && control && ai->activeEditSection == InstrumentSection::PARAMETERS) return 0;	//except for edit PARAM, is not allowed there
        break;

    case VK_MULTIPLY:
    {
        OctaveUp();
        return 1;
    }
    break;

    case VK_DIVIDE:
    {
        OctaveDown();
        return 1;
    }
    break;

    case VK_SUBTRACT:	//Numlock minus
        if (shift && control)	//S+C+numlock_minus  ...reading the whole curve with a minimum of 0
        {
            g_Undo.ChangeInstrument(m_activeinstr, 0, UETYPE_INSTRDATA);
            BOOL br = 0, bl = 0;
            if (ai->activeEditSection == InstrumentSection::ENVELOPE && ai->editEnvelopeY == EnvelopeParameter::VOLUMER) br = 1;
            else
                if (ai->activeEditSection == InstrumentSection::ENVELOPE && ai->editEnvelopeY == EnvelopeParameter::VOLUMEL) bl = 1;
                else
                    br = bl = 1;
            for (int i = 0; i <= ai->parameters[PAR_ENV_LENGTH]; i++)
            {
                if (br) { if (ai->envelope[i][EnvelopeParameter::VOLUMER] > 0) ai->envelope[i][EnvelopeParameter::VOLUMER]--; }
                if (bl) { if (ai->envelope[i][EnvelopeParameter::VOLUMEL] > 0) ai->envelope[i][EnvelopeParameter::VOLUMEL]--; }
            }
            goto ChangeInstrumentEnv;
        }
        else
            VolumeDown();
        return 1;
        break;

    case VK_ADD:		//Numlock plus
        if (shift && control)	//S+C+numlock_plus ...applying the whole curve with maximum f
        {
            g_Undo.ChangeInstrument(m_activeinstr, 0, UETYPE_INSTRDATA);
            BOOL br = 0, bl = 0;
            if (ai->activeEditSection == InstrumentSection::ENVELOPE && ai->editEnvelopeY == EnvelopeParameter::VOLUMER) br = 1;
            else
                if (ai->activeEditSection == InstrumentSection::ENVELOPE && ai->editEnvelopeY == EnvelopeParameter::VOLUMEL) bl = 1;
                else
                    br = bl = 1;
            for (int i = 0; i <= ai->parameters[PAR_ENV_LENGTH]; i++)
            {
                if (br) { if (ai->envelope[i][EnvelopeParameter::VOLUMER] < 0x0f) ai->envelope[i][EnvelopeParameter::VOLUMER]++; }
                if (bl) { if (ai->envelope[i][EnvelopeParameter::VOLUMEL] < 0x0f) ai->envelope[i][EnvelopeParameter::VOLUMEL]++; }
            }
            goto ChangeInstrumentEnv;
        }
        else
            VolumeUp();
        return 1;
        break;
    }

    //and now only for special parts
    if (ai->activeEditSection == InstrumentSection::NAME)
    {
        g_isEditingInstrumentName = 1;
        //NAME
        if (IsNotAMovementVKey(vk))
        {	//saves undo only if it is not cursor movement
            g_Undo.ChangeInstrument(m_activeinstr, 0, UETYPE_INSTRDATA);
        }
        if (EditText(vk, shift, control, ai->name, ai->editNameCursorPos, INSTRUMENT_NAME_MAX_LEN)) {
            ai->activeEditSection = InstrumentSection::PARAMETERS;
        }

        return 1;
    }
    else if (ai->activeEditSection == InstrumentSection::PARAMETERS)
    {
        // Parameter section is active

        g_isEditingInstrumentName = 0;
        switch (vk)
        {
        case VK_UP:
            if (control) goto ParameterInc;
            ai->editParameterNr = shpar[ai->editParameterNr].gotoUp;
            return 1;

        case VK_DOWN:
            if (control) goto ParameterDec;
            ai->editParameterNr = shpar[ai->editParameterNr].gotoDown;
            return 1;

        case VK_LEFT:
            if (control)
            {
                // Change the parameter value
            ParameterDec:
                g_Undo.ChangeInstrument(m_activeinstr, 0, UETYPE_INSTRDATA);
                int v = ap - 1;
                if (v < 0) v = shpar[ai->editParameterNr].maxParameterValue;
                ap = v;
                goto ChangeInstrumentPar;
            }
            else
            {
                // Move to the next parameter
                ai->editParameterNr = shpar[ai->editParameterNr].gotoLeft;
            }
            return 1;

        case VK_RIGHT:
            if (control)
            {
                // Change the parameter value
            ParameterInc:
                g_Undo.ChangeInstrument(m_activeinstr, 0, UETYPE_INSTRDATA);
                int v = ap + 1;
                if (v > shpar[ai->editParameterNr].maxParameterValue) v = 0;
                ap = v;
                goto ChangeInstrumentPar;
            }
            else
            {
                // Move to the next parameter
                ai->editParameterNr = shpar[ai->editParameterNr].gotoRight;
            }
            return 1;

        case VK_HOME:
            ai->editParameterNr = PAR_ENV_LENGTH;
            return 1;

        case VK_SPACE:
            if (control) break;	//prevents inputing a SPACE while exiting PROVE mode
        case VK_BACK:	//BACKSPACE
        case VK_DELETE:
            g_Undo.ChangeInstrument(m_activeinstr, 0, UETYPE_INSTRDATA);
            ap = 0;
            goto ChangeInstrumentPar;
            return 1;

        ChangeInstrumentPar:
            //because there has been some change in the instrument parameter => stop this instrument in all channels
            g_AtariTrackerDriver->InstrumentTurnOff(m_activeinstr);
            g_Instruments.CheckInstrumentParameters(m_activeinstr);
            g_Instruments.Update(m_activeinstr);
            return 1;
        }
    }
    else if (ai->activeEditSection == InstrumentSection::ENVELOPE)
    {
        g_isEditingInstrumentName = 0;
        //ENVELOPE
        switch (vk)
        {
        case VK_UP:
            if (control)
            {	//
                if (shift)	//SHIFT+CONTROL+UP
                {
                    g_Undo.ChangeInstrument(m_activeinstr, 0, UETYPE_INSTRDATA);
                    for (int i = 0; i <= ai->parameters[PAR_ENV_LENGTH]; i++)
                    {
                        int& ae = ai->envelope[i][ai->editEnvelopeY];
                        ae = (ae + shenv[ai->editEnvelopeY].padd) & shenv[ai->editEnvelopeY].pand;
                    }
                    goto ChangeInstrumentEnv;
                }
                goto EnvelopeInc;
            }
            i = ai->editEnvelopeY;
            if (i > 0)
            {
                i--;
                if (i == 0 && GetTracks() <= 4) i = 7;	//mono mode
            }
            else
                i = 7;
            ai->editEnvelopeY = i;
            return 1;

        case VK_DOWN:
            if (control)
            {	//
                if (shift)	//SHIFT+CONTROL+DOWN
                {
                    g_Undo.ChangeInstrument(m_activeinstr, 0, UETYPE_INSTRDATA);
                    for (int i = 0; i <= ai->parameters[PAR_ENV_LENGTH]; i++)
                    {
                        int& ae = ai->envelope[i][ai->editEnvelopeY];
                        ae = (ae + shenv[ai->editEnvelopeY].psub) & shenv[ai->editEnvelopeY].pand;
                    }
                    goto ChangeInstrumentEnv;
                }
                goto EnvelopeDec;
            }
            i = ai->editEnvelopeY;
            if (i < 7) i++; else i = (GetTracks() > 4) ? 0 : 1;
            ai->editEnvelopeY = i;
            return 1;

        case VK_LEFT:
            if (control)
            {
            EnvelopeDec:
                g_Undo.ChangeInstrument(m_activeinstr, 0, UETYPE_INSTRDATA);
                ae = (ae + shenv[ai->editEnvelopeY].psub) & shenv[ai->editEnvelopeY].pand;
                goto ChangeInstrumentEnv;
            }
            else
            {
                i = ai->editEnvelopeX;
                if (i > 0) i--; else i = ai->parameters[PAR_ENV_LENGTH]; //length of env
                ai->editEnvelopeX = i;
            }
            return 1;

        case VK_RIGHT:
            if (control)
            {
            EnvelopeInc:
                g_Undo.ChangeInstrument(m_activeinstr, 0, UETYPE_INSTRDATA);
                ae = (ae + shenv[ai->editEnvelopeY].padd) & shenv[ai->editEnvelopeY].pand;
                goto ChangeInstrumentEnv;
            }
            else
            {
                i = ai->editEnvelopeX;
                if (i < ai->parameters[PAR_ENV_LENGTH]) i++; else i = 0; //length of env
                ai->editEnvelopeX = i;
            }
            return 1;

        case VK_HOME:
            if (control)
            {
                g_Undo.ChangeInstrument(m_activeinstr, 0, UETYPE_INSTRDATA);
                ai->parameters[PAR_ENV_GOTO] = ai->editEnvelopeX; //sets ENVGO to this column
                goto ChangeInstrumentPar;	//yes, that's fine, it really changed the PARAMETER, even if it's in the envelope
            }
            else
            {
                //goes left to column 0 or to the beginning of the GO loop
                if (ai->editEnvelopeX != 0)
                    ai->editEnvelopeX = 0;
                else
                    ai->editEnvelopeX = ai->parameters[PAR_ENV_GOTO];
            }
            return 1;

        case VK_END:
            if (control)
            {
                g_Undo.ChangeInstrument(m_activeinstr, 0, UETYPE_INSTRDATA);
                if (ai->editEnvelopeX == ai->parameters[PAR_ENV_LENGTH])	//sets ENVLEN to this column or to the end
                    ai->parameters[PAR_ENV_LENGTH] = ENVELOPE_MAX_COLUMNS - 1;
                else
                    ai->parameters[PAR_ENV_LENGTH] = ai->editEnvelopeX;
                goto ChangeInstrumentPar;	//yes, changed PAR from envelope
            }
            else
            {
                ai->editEnvelopeX = ai->parameters[PAR_ENV_LENGTH];	//moves the cursor to the right to the end
            }
            return 1;

        case VK_BACK:
            g_Undo.ChangeInstrument(m_activeinstr, 0, UETYPE_INSTRDATA);
            ae = 0;
            goto ChangeInstrumentEnv;
            return 1;

        case VK_SPACE:
            if (control) break;	//prevents inputing a SPACE while exiting PROVE mode
            {
                g_Undo.ChangeInstrument(m_activeinstr, 0, UETYPE_INSTRDATA);
                for (int j = 0; j < ENVROWS; j++) ai->envelope[ai->editEnvelopeX][j] = 0;
                if (ai->editEnvelopeX < ai->parameters[PAR_ENV_LENGTH]) ai->editEnvelopeX++; //shift to the right
            }
            goto ChangeInstrumentEnv;
            return 1;

        case VK_INSERT:
            if (!control)
            {	//moves the envelope from the current position to the right
                g_Undo.ChangeInstrument(m_activeinstr, 0, UETYPE_INSTRDATA);
                int i, j;
                int ele = ai->parameters[PAR_ENV_LENGTH];
                int ego = ai->parameters[PAR_ENV_GOTO];
                if (ele < ENVELOPE_MAX_COLUMNS - 1) ele++;
                if (ai->editEnvelopeX < ego && ego < ENVELOPE_MAX_COLUMNS - 1) ego++;
                for (i = ENVELOPE_MAX_COLUMNS - 2; i >= ai->editEnvelopeX; i--)
                {
                    for (j = 0; j < ENVROWS; j++) ai->envelope[i + 1][j] = ai->envelope[i][j];
                }
                //improvement: with shift it will leave it there (it will not erase the column)
                if (!shift) for (j = 0; j < ENVROWS; j++) ai->envelope[ai->editEnvelopeX][j] = 0;
                ai->parameters[PAR_ENV_LENGTH] = ele;
                ai->parameters[PAR_ENV_GOTO] = ego;
                goto ChangeInstrumentPar;	//changed length and / or go parameters
            }
            return 0; //without screen update

        case VK_DELETE:
            if (!control)	//!shift &&
            {	//moves the envelope from the current position to the left
                g_Undo.ChangeInstrument(m_activeinstr, 0, UETYPE_INSTRDATA);
                int i, j;
                int ele = ai->parameters[PAR_ENV_LENGTH];
                int ego = ai->parameters[PAR_ENV_GOTO];
                if (ele > 0)
                {
                    ele--;
                    for (i = ai->editEnvelopeX; i < ENVELOPE_MAX_COLUMNS - 1; i++)
                    {
                        for (j = 0; j < ENVROWS; j++) ai->envelope[i][j] = ai->envelope[i + 1][j];
                    }
                    for (j = 0; j < ENVROWS; j++) ai->envelope[ENVELOPE_MAX_COLUMNS - 1][j] = 0;
                }
                else
                {
                    for (j = 0; j < ENVROWS; j++) ai->envelope[0][j] = 0;
                }
                if (ai->editEnvelopeX < ego) ego--;
                if (ego > ele) ego = ele;
                ai->parameters[PAR_ENV_GOTO] = ego;
                ai->parameters[PAR_ENV_LENGTH] = ele;
                if (ai->editEnvelopeX > ele) ai->editEnvelopeX = ele;
                goto ChangeInstrumentPar;	//changed length and / or go parameters
            }
            return 0; //without screen update

        ChangeInstrumentEnv:
            //something changed => Save instrument to Atari memory
            g_Instruments.Update(m_activeinstr);
            return 1;
        }
    }
    if (ai->activeEditSection == InstrumentSection::NOTETABLE)
    {
        g_isEditingInstrumentName = 0;
        //TABLE
        switch (vk)
        {
        case VK_HOME:
            if (control)
            {	//set a TABLE go loop here
                g_Undo.ChangeInstrument(m_activeinstr, 0, UETYPE_INSTRDATA);
                ai->parameters[PAR_TBL_GOTO] = ai->editNoteTableCursorPos;
                if (ai->editNoteTableCursorPos > ai->parameters[PAR_TBL_LENGTH]) ai->parameters[PAR_TBL_LENGTH] = ai->editNoteTableCursorPos;
                goto ChangeInstrumentPar;
            }
            else
            {
                //go to the beginning of the TABLE and to the beginning of the TABLE loop
                if (ai->editNoteTableCursorPos != 0)
                    ai->editNoteTableCursorPos = 0;
                else
                    ai->editNoteTableCursorPos = ai->parameters[PAR_TBL_GOTO];
            }
            return 1;

        case VK_END:
            if (control)
            {	//set TABLE only by location
                g_Undo.ChangeInstrument(m_activeinstr, 0, UETYPE_INSTRDATA);
                if (ai->editNoteTableCursorPos == ai->parameters[PAR_TBL_LENGTH])
                    ai->parameters[PAR_TBL_LENGTH] = NOTE_TABLE_MAX_LEN - 1;
                else
                    ai->parameters[PAR_TBL_LENGTH] = ai->editNoteTableCursorPos;
                goto ChangeInstrumentPar;
            }
            else	//goes to the last parameter in the TABLE
                ai->editNoteTableCursorPos = ai->parameters[PAR_TBL_LENGTH];
            return 1;

        case VK_UP:
            if (control)
            {
                if (shift)	//Shift+Control+UP
                {
                    g_Undo.ChangeInstrument(m_activeinstr, 0, UETYPE_INSTRDATA);
                    for (int i = 0; i <= ai->parameters[PAR_TBL_LENGTH]; i++) ai->noteTable[i] = (ai->noteTable[i] + 1) & 0xff;
                    goto ChangeInstrumentTab;
                }
            TableInc:
                g_Undo.ChangeInstrument(m_activeinstr, 0, UETYPE_INSTRDATA);
                at = (at + 1) & 0xff;
                goto ChangeInstrumentTab;
            }
            return 1;

        case VK_DOWN:
            if (control)
            {
                if (shift)	//Shift+Control+DOWN
                {
                    g_Undo.ChangeInstrument(m_activeinstr, 0, UETYPE_INSTRDATA);
                    for (int i = 0; i <= ai->parameters[PAR_TBL_LENGTH]; i++) ai->noteTable[i] = (ai->noteTable[i] - 1) & 0xff;
                    goto ChangeInstrumentTab;
                }
            TableDec:
                g_Undo.ChangeInstrument(m_activeinstr, 0, UETYPE_INSTRDATA);
                at = (at - 1) & 0xff;
                goto ChangeInstrumentTab;
            }
            return 1;

        case VK_LEFT:
            if (control) goto TableDec;
            i = ai->editNoteTableCursorPos - 1;
            if (i < 0) i = ai->parameters[PAR_TBL_LENGTH];
            ai->editNoteTableCursorPos = i;
            goto ChangeInstrumentTab;

        case VK_RIGHT:
            if (control) goto TableInc;
            i = ai->editNoteTableCursorPos + 1;
            if (i > ai->parameters[PAR_TBL_LENGTH]) i = 0;
            ai->editNoteTableCursorPos = i;
            goto ChangeInstrumentTab;

        case VK_SPACE:	// parameter reset and shift by 1 to the right
            if (control) break;	//prevents inputing a SPACE while exiting PROVE mode
            if (ai->editNoteTableCursorPos < ai->parameters[PAR_TBL_LENGTH]) ai->editNoteTableCursorPos++;
            //and proceeds the same as VK_BACKSPACE
        case VK_BACK: // parameter reset
            g_Undo.ChangeInstrument(m_activeinstr, 0, UETYPE_INSTRDATA);
            at = 0;
            goto ChangeInstrumentTab;

        case VK_INSERT:
            if (!control)
            {	//moves the table from the current position to the right
                g_Undo.ChangeInstrument(m_activeinstr, 0, UETYPE_INSTRDATA);
                int i;
                int tle = ai->parameters[PAR_TBL_LENGTH];
                int tgo = ai->parameters[PAR_TBL_GOTO];
                if (tle < NOTE_TABLE_MAX_LEN - 1) tle++;
                if (ai->editNoteTableCursorPos < tgo && tgo < NOTE_TABLE_MAX_LEN - 1) tgo++;
                for (i = NOTE_TABLE_MAX_LEN - 2; i >= ai->editNoteTableCursorPos; i--) ai->noteTable[i + 1] = ai->noteTable[i];
                if (!shift) ai->noteTable[ai->editNoteTableCursorPos] = 0; //with the shift it will leave there
                ai->parameters[PAR_TBL_LENGTH] = tle;
                ai->parameters[PAR_TBL_GOTO] = tgo;
                //goto ChangeInstrumentTab; <-- It is not enough!
                goto ChangeInstrumentPar; //changed TABLE LEN or GO, must stop the instrument
            }
            return 0; //without screen update

        case VK_DELETE:
            if (!control) //!shift &&
            {	//moves the table from the current position to the left
                g_Undo.ChangeInstrument(m_activeinstr, 0, UETYPE_INSTRDATA);
                int i;
                int tle = ai->parameters[PAR_TBL_LENGTH];
                int tgo = ai->parameters[PAR_TBL_GOTO];
                if (tle > 0)
                {
                    tle--;
                    for (i = ai->editNoteTableCursorPos; i < NOTE_TABLE_MAX_LEN - 1; i++) ai->noteTable[i] = ai->noteTable[i + 1];
                    ai->noteTable[NOTE_TABLE_MAX_LEN - 1] = 0;
                }
                else
                    ai->noteTable[0] = 0;
                if (ai->editNoteTableCursorPos < tgo) tgo--;
                if (tgo > tle) tgo = tle;
                ai->parameters[PAR_TBL_LENGTH] = tle;
                ai->parameters[PAR_TBL_GOTO] = tgo;
                if (ai->editNoteTableCursorPos > tle) ai->editNoteTableCursorPos = tle;
                //goto ChangeInstrumentTab; <-- It is not enough!
                goto ChangeInstrumentPar; //changed TABLE LEN or GO, must stop the instrument
            }
            return 0; //without screen update

        ChangeInstrumentTab:
            //something changed => Save instrument to Atari memory
            g_Instruments.Update(m_activeinstr);
            return 1;

        }
    }
    return 0;	//=> SCREENUPDATE will not be performed
}

BOOL CSong::InfoCursorGotoSongname(int x)
{
    x = x / 8;
    if (x >= 0 && x < SONG_NAME_MAX_LEN)
    {
        m_songnamecur = x;
        g_activepart = Part::PART_INFO;
        m_infoact = EditArea::NAME;
        is_editing_infos = 1;	//Song Name is being edited
        return 1;
    }
    return 0;
}

BOOL CSong::InfoCursorGotoSpeed(int x)
{
    x = (x - 4) / 8;
    if (x < 2) m_infoact = EditArea::SPEED;
    else if (x < 5) m_infoact = EditArea::MAIN_SPEED;
    else m_infoact = EditArea::INSTR_SPEED;
    g_activepart = Part::PART_INFO;
    is_editing_infos = 0;	//Song Speed is being edited
    return 1;
}

BOOL CSong::InfoCursorGotoHighlight(int x)
{
    x = (x - 4) / 8;
    if (x < 2) m_infoact = EditArea::FIRST_HIGHLIGHT;
    else m_infoact = EditArea::SECOND_HIGHLIGHT;
    g_activepart = Part::PART_INFO;
    is_editing_infos = 0;	//Song Highlight is being edited
    return 1;
}

BOOL CSong::InfoCursorGotoOctaveSelect(int x, int y)
{
    COctaveSelectDlg dlg;
    CRect rec;
    ::GetWindowRect(g_viewhwnd, &rec);
    dlg.m_pos = rec.TopLeft() + CPoint(x - 64 - 9, y - 7);
    if (dlg.m_pos.x < 0) dlg.m_pos.x = 0;
    dlg.m_octave = m_octave;
    g_mousebutt = 0;				//because the dialog sessions of the OnLbuttonUP event
    if (dlg.DoModal() == IDOK)
    {
        m_octave = dlg.m_octave;
        return 1;
    }
    return 0;
}

BOOL CSong::InfoCursorGotoVolumeSelect(int x, int y)
{
    CVolumeSelectDlg dlg;
    CRect rec;
    ::GetWindowRect(g_viewhwnd, &rec);
    dlg.m_pos = rec.TopLeft() + CPoint(x - 64 - 9, y - 7);
    if (dlg.m_pos.x < 0) dlg.m_pos.x = 0;
    dlg.m_volume = m_volume;
    dlg.m_respectvolume = g_respectvolume;

    g_mousebutt = 0;				//because the dialog sessions of the OnLbuttonUP event
    if (dlg.DoModal() == IDOK)
    {
        m_volume = dlg.m_volume;
        g_respectvolume = dlg.m_respectvolume;
        return 1;
    }
    return 0;
}

BOOL CSong::InfoCursorGotoInstrumentSelect(int x, int y)
{
    g_isEditingInstrumentName = 0;
    CInstrumentSelectDlg dlg;
    CRect rec;
    ::GetWindowRect(g_viewhwnd, &rec);
    dlg.m_pos = rec.TopLeft() + CPoint(x - 64 - 82, y - 7);
    if (dlg.m_pos.x < 0) dlg.m_pos.x = 0;
    dlg.m_selected = m_activeinstr;

    g_mousebutt = 0;				//because the dialog sessions of the OnLbuttonUP event
    if (dlg.DoModal() == IDOK)
    {
        ActiveInstrSet(dlg.m_selected);
        return 1;
    }
    return 0;
}

BOOL CSong::CursorToSpeedColumn()
{
    if (g_activepart != Part::PART_TRACKS || SongGetActiveTrack() < 0) return 0;
    BLOCKDESELECT();
    m_trackactivecur = 3;
    return 1;
}

BOOL CSong::ProveKeyPokeyExplorerMode(int vk, int shift, int control)
{
    switch (vk)
    {
        //General variables manipulation

    case VK_RETURN:
        m_PokeyController->OnNextChannel();
        break;

    case VK_BACK:
        m_PokeyController->OnPreviousChannel();
        break;

    case VK_OEM_PLUS:
        if (shift) {
            m_PokeyController->OnIncreaseDivisorBy10();
        }
        else {
            m_PokeyController->OnIncreaseDivisorBy01();
        }
        break;

    case VK_OEM_MINUS:
        if (shift) {
            m_PokeyController->OnDecreaseDivisorBy10();
        }
        else {
            m_PokeyController->OnDecreaseDivisorBy01();
        }
        break;

        //AUDF channels

    case VK_1:
        if (shift) {
            m_PokeyController->OnIncreaseAUDF0By10();
        }
        else {
            m_PokeyController->OnIncreaseAUDF0By01();
        }
        break;

    case VK_Q:
        if (shift) {
            m_PokeyController->OnDecreaseAUDF0By10();
        }
        else {
            m_PokeyController->OnDecreaseAUDF0By01();
        }
        break;

    case VK_3:
        if (shift) {
            m_PokeyController->OnIncreaseAUDF1By10();
        }
        else {
            m_PokeyController->OnIncreaseAUDF1By01();
        }
        break;

    case VK_E:
        if (shift) {
            m_PokeyController->OnDecreaseAUDF1By10();
        }
        else {
            m_PokeyController->OnDecreaseAUDF1By01();
        }
        break;

    case VK_5:
        if (shift) {
            m_PokeyController->OnIncreaseAUDF2By10();
        }
        else {
            m_PokeyController->OnIncreaseAUDF2By01();
        }
        break;

    case VK_T:
        if (shift) {
            m_PokeyController->OnDecreaseAUDF2By10();
        }
        else {
            m_PokeyController->OnDecreaseAUDF2By01();
        }
        break;

    case VK_7:
        if (shift) {
            m_PokeyController->OnIncreaseAUDF3By10();
        }
        else {
            m_PokeyController->OnIncreaseAUDF3By01();
        }
        break;

    case VK_U:
        if (shift) {
            m_PokeyController->OnDecreaseAUDF3By10();
        }
        else {
            m_PokeyController->OnDecreaseAUDF3By01();
        }
        break;

        //AUDC channels

    case VK_2:
        if (shift) {
            m_PokeyController->OnIncreaseAUDC0By10();
        }
        else {
            m_PokeyController->OnIncreaseAUDC0By01();
        }
        break;

    case VK_W:
        if (shift) {
            m_PokeyController->OnDecreaseAUDC0By10();
        }
        else {
            m_PokeyController->OnDecreaseAUDC0By01();
        }
        break;

    case VK_4:
        if (shift) {
            m_PokeyController->OnIncreaseAUDC1By10();
        }
        else {
            m_PokeyController->OnIncreaseAUDC1By01();
        }
        break;

    case VK_R:
        if (shift) {
            m_PokeyController->OnDecreaseAUDC1By10();
        }
        else {
            m_PokeyController->OnDecreaseAUDC1By01();
        }
        break;

    case VK_6:
        if (shift) {
            m_PokeyController->OnIncreaseAUDC2By10();
        }
        else {
            m_PokeyController->OnIncreaseAUDC2By01();
        }
        break;

    case VK_Y:
        if (shift) {
            m_PokeyController->OnDecreaseAUDC2By10();
        }
        else {
            m_PokeyController->OnDecreaseAUDC2By01();
        }
        break;

    case VK_8:
        if (shift) {
            m_PokeyController->OnIncreaseAUDC3By10();
        }
        else {
            m_PokeyController->OnIncreaseAUDC3By01();
        }
        break;

    case VK_I:
        if (shift) {
            m_PokeyController->OnDecreaseAUDC3By10();
        }
        else {
            m_PokeyController->OnDecreaseAUDC3By01();
        }
        break;

        //AUDCTL bits

    case VK_P:
        m_PokeyController->OnToggleAUDCTLBit7();
        break;

    case VK_A:
        m_PokeyController->OnToggleAUDCTLBit6();
        break;

    case VK_D:
        m_PokeyController->OnToggleAUDCTLBit5();
        break;

    case VK_J:
        m_PokeyController->OnToggleAUDCTLBit4();
        break;

    case VK_K:
        m_PokeyController->OnToggleAUDCTLBit3();
        break;

    case VK_F:
        m_PokeyController->OnToggleAUDCTLBit2();
        break;

    case VK_G:
        m_PokeyController->OnToggleAUDCTLBit1();
        break;

    case VK_C:
        m_PokeyController->OnToggleAUDCTLBit0();
        break;


    case VK_M:
        m_PokeyController->OnToggleTwoTone();
        break;

    default:
        return FALSE;

    }
    return TRUE;

}

BOOL CSong::ProveKey(int vk, int shift, int control)
{

    if (IsEditMode(EditMode::POKEY_EXPLORER_MODE))	//POKEY EXPLORER MODE: FULL CONTROL OVER THE POKEY (IGNORE RMT ROUTINES EXCEPT SETPOKEY)
    {

        return ProveKeyPokeyExplorerMode(vk, shift, control);

    }

    int note = NoteKey(vk);

    if (note >= 0)
    {
        int i = note + m_octave * 12;
        if (i >= 0 && i < CNotes::NOTESNUM)		//only within limits
        {
            SetPlayPressedTonesTNIV(m_trackactivecol, i, m_activeinstr, m_volume);
            if ((control || IsEditMode(EditMode::JAM_STEREO_MODE)) && GetTracks() > 4)
            {
                //with control or in prove2 => stereo test
                SetPlayPressedTonesTNIV((m_trackactivecol + 4) & 0x07, i, m_activeinstr, m_volume);
            }
        }
        return 0; //they don't have to redraw
    }

    if (SongGetGo() >= 0) //is active song go to line => they must not edit anything
    {
        if (!control && (vk == VK_UP || vk == VK_PRIOR))  //GO - key up
        {
            m_trackactiveline = 0;
            if (!g_SkipLinesAfterNoteInsert) TrackUp(1);
            else TrackUp(g_SkipLinesAfterNoteInsert);
            return 1;
        }
        if (!control && (vk == VK_DOWN || vk == VK_NEXT)) //GO - key down
        {
            m_trackactiveline = g_Tracks.GetMaxTrackLength() - 1;
            TrackDown(1, 0);
            return 1;
        }
    }

    switch (vk)
    {
    case VK_LEFT:
        if (control) break;	//do nothing
        if (shift)
            ActiveInstrPrev();
        else if (g_activepart != Part::PART_TRACKS)	//anywhere but tracks
            TrackLeft(1);
        else
            TrackLeft();
        break;

    case VK_RIGHT:
        if (control) break;	//do nothing
        if (shift)
            ActiveInstrNext();
        else if (g_activepart != Part::PART_TRACKS)	//anywhere but tracks
            TrackRight(1);
        else
            TrackRight();
        break;

    case VK_UP:
        if (shift) break;	//do nothing
        if (control || g_activepart != Part::PART_TRACKS)	//anywhere but tracks
        {
            SongUp();
        }
        else
        {
            if (!g_SkipLinesAfterNoteInsert) TrackUp(1);
            else TrackUp(g_SkipLinesAfterNoteInsert);
        }
        break;

    case VK_DOWN:
        if (shift) break;	//do nothing
        if (control || g_activepart != Part::PART_TRACKS)	//anywhere but tracks
        {
            SongDown();
        }
        else
        {
            if (!g_SkipLinesAfterNoteInsert) TrackDown(1, 0);
            else TrackDown(g_SkipLinesAfterNoteInsert, 0);	//stoponlastline = 0 => will not stop on the last line of the track
        }
        break;

    case VK_TAB:
        if (shift)
            TrackLeft(1); // Shift+TAB
        else if (control)
            CursorToSpeedColumn(); //Ctral+TAB
        else
            TrackRight(1);
        break;

    case VK_SPACE:
        if (control) break;	//prevents inputing a SPACE while exiting PROVE mode
        break;

    case VK_SUBTRACT:
        VolumeDown();
        break;

    case VK_ADD:
        VolumeUp();
        break;

    case VK_DIVIDE:
        OctaveDown();
        break;

    case VK_MULTIPLY:
        OctaveUp();
        break;

    case VK_PRIOR:
        if (g_activepart != Part::PART_TRACKS)
        {
            if (shift)
                SongSubsongPrev();
            else
            {
                SongUp();
            }
            break;
        }
        else
            if (g_activepart == Part::PART_TRACKS)
            {
                if (!shift && control)
                {
                    SongUp();
                }
                else
                    if (!control && shift)
                    {
                        //move to the previous goto
                        SongSubsongPrev();
                    }
                if (m_play && m_followplay) break;	//prevents moving at all during play+follow
                else
                {
                    if (m_trackactiveline > 0)
                    {
                        m_trackactiveline = ((m_trackactiveline - 1) / g_trackLinePrimaryHighlight) * g_trackLinePrimaryHighlight;
                    }
                }
            }
        break;

    case VK_NEXT:
        if (g_activepart != Part::PART_TRACKS)
        {
            if (shift)
                SongSubsongNext();
            else
            {
                SongDown();
            }
            break;
        }
        else
            if (g_activepart == Part::PART_TRACKS)
            {
                if (!shift && control)
                {
                    SongDown();
                }
                else
                    if (!control && shift)
                    {
                        //move to the next goto
                        SongSubsongNext();
                    }
                if (m_play && m_followplay) break;	//prevents moving at all during play+follow
                else
                {
                    m_trackactiveline = ((m_trackactiveline + g_trackLinePrimaryHighlight) / g_trackLinePrimaryHighlight) * g_trackLinePrimaryHighlight;
                    if (m_trackactiveline > GetSmallestMaxtracklen(m_songactiveline) - 1)
                        m_trackactiveline -= g_trackLinePrimaryHighlight;
                }
            }
        break;

    case VK_HOME:
        if (control || shift) break; //do nothing
        if (g_activepart == Part::PART_TRACKS)	//tracks
            m_trackactiveline = 0;		//line 0
        else if (g_activepart == Part::PART_SONG)	//song lines
            m_songactiveline = 0;
        break;

    case VK_END:
        if (control || shift) break; //do nothing
        if (g_activepart == Part::PART_TRACKS)	//tracks
        {
            if (TrackGetGoLine() >= 0)
                m_trackactiveline = g_Tracks.GetMaxTrackLength() - 1; //last line
            else
                m_trackactiveline = TrackGetLastLine();	//end line
            if (m_trackactiveline < 0) m_trackactiveline = g_Tracks.GetMaxTrackLength() - 1; //failsafe in case the active line is out of bounds
        }
        else if (g_activepart == Part::PART_SONG)	//song lines
        {
            int i, j, la = 0;
            for (j = 0; j < SONGLEN; j++)
            {
                for (i = 0; i < GetTracks(); i++) if (m_song[j][i] >= 0) { la = j; break; }
                if (m_songgo[j] >= 0) la = j;
            }
            m_songactiveline = la;
        }
        break;

    case VK_RETURN:
        if (g_activepart == Part::PART_TRACKS)
        {
            int instr, vol;
            if ((BOOL)control != (BOOL)g_keyboard_swapenter)	//control+Enter => plays a whole line (all tracks)
            {
                //for all track columns except the active track column
                for (int i = 0; i < GetTracks(); i++)
                {
                    if (i != m_trackactivecol)
                    {
                        TrackGetLoopingNoteInstrVol(m_song[m_songactiveline][i], note, instr, vol);
                        if (note >= 0)		//is there a note?
                            SetPlayPressedTonesTNIV(i, note, instr, vol);	//it will lose it as it is there
                        else
                            if (vol >= 0) //there is no note, but is there a separate volume?
                                SetPlayPressedTonesV(i, vol);				//adjust the volume as it is
                    }
                }
            }
            //and now for that active track column
            TrackGetLoopingNoteInstrVol(SongGetActiveTrack(), note, instr, vol);
            if (note >= 0)		//is there a note?
            {
                SetPlayPressedTonesTNIV(m_trackactivecol, note, instr, vol);	//it will lose it as it is there
            }
            else
                if (vol >= 0) //there is no note, but is there a separate volume?
                {
                    SetPlayPressedTonesV(m_trackactivecol, vol); //adjust the volume
                }
            TrackDown(1, 0);	//move down 1 step always
        }
        else
            if (g_activepart != Part::PART_TRACKS)
            {
                g_activepart = g_active_ti;
                return 1;
            }
        break;

    default:
        return 0;
        break;
    }
    return 1;
}


BOOL CSong::TrackKey(int vk, int shift, int control)
{
    //
    static constexpr int VKX_SONGINSERTLINE = VK_I;
    static constexpr int  VKX_SONGDELETELINE = VK_U;
    static constexpr int  VKX_SONGDUPLICATELINE = VK_O;
    static constexpr int  VKX_SONGPREPARELINE = VK_P;
    static constexpr int  VKX_SONGPUTNEWTRACK = VK_N;
    static constexpr int  VKX_SONGMAKETRACKSDUPLICATE = VK_D;

    //
    int note, i, j;

    if (g_TrackClipboard.IsBlockSelected() && SongGetActiveTrack() != g_TrackClipboard.m_seltrack) BLOCKDESELECT();

    if (SongGetGo() >= 0) //is active song go to line => they must not edit anything
    {
        if (!control && (vk == VK_UP || vk == VK_PRIOR))  //GO - key up
        {
            m_trackactiveline = 0;	//always assume it went from line 0
            if (!g_SkipLinesAfterNoteInsert) TrackUp(1);
            else TrackUp(g_SkipLinesAfterNoteInsert);
            return 1;
        }
        if (!control && (vk == VK_DOWN || vk == VK_NEXT)) //GO - key down
        {
            m_trackactiveline = g_Tracks.GetMaxTrackLength() - 1;	//always reset to line 0
            TrackDown(1, 0);
            return 1;
        }
        if (!control && !shift) return 0;
        if (control && (vk == 8 || vk == 71)) //control+backspace or control+G
        {
            SongTrackGoOnOff();
            return 1;
        }
        if (control && !shift && (vk == VKX_SONGINSERTLINE || vk == VKX_SONGDELETELINE || vk == VKX_SONGPREPARELINE || vk == VKX_SONGDUPLICATELINE || vk == VK_PRIOR || vk == VK_NEXT)) goto TrackKeyOk;
        if (vk != VK_LEFT && vk != VK_RIGHT && vk != VK_UP && vk != VK_DOWN) return 0;
    }
TrackKeyOk:

    switch (m_trackactivecur)
    {
    case 0: //note column
        if (control) break;		//with control, notes are not entered (break continues)
        note = NoteKey(vk);
        if (note >= 0)
        {
        insertnotes:
            i = note + m_octave * 12;
            if (i >= 0 && i < CNotes::NOTESNUM)		//only within limits
            {
                BLOCKDESELECT();
                //Quantization
                if (m_play && m_followplay && (m_speeda < (m_speed / 2)))
                {
                    m_quantization_note = i;
                    m_quantization_instr = m_activeinstr;
                    m_quantization_vol = m_volume;
                    return 1;
                }
                //end Quantization
                if (TrackSetNoteActualInstrVol(i))
                {
                    SetPlayPressedTonesTNIV(m_trackactivecol, i, m_activeinstr, TrackGetVol());
                    if (!(m_play && m_followplay)) TrackDown(g_SkipLinesAfterNoteInsert);
                }
            }
            return 1;
        }
        else //the numbers 1-6 on the numeral are overwritten by an octave
            if ((j = Numblock09Key(vk)) >= 1 && j <= 6 && m_trackactiveline <= TrackGetLastLine())
            {
                note = TrackGetNote();
                if (note >= 0)		//is there a note?
                {
                    BLOCKDESELECT();
                    note = (note % 12) + ((j - 1) * 12);		//changes its octave according to the number pressed on the numblock
                    if (note >= 0 && note < CNotes::NOTESNUM)
                    {
                        int instr = TrackGetInstr(), vol = TrackGetVol();
                        if (TrackSetNoteInstrVol(note, instr, vol))
                            SetPlayPressedTonesTNIV(m_trackactivecol, note, instr, vol);
                    }
                }
                if (!(m_play && m_followplay)) TrackDown(g_SkipLinesAfterNoteInsert);
                return 1;
            }
        break;

    case 1: //instrument column
        i = NumbKey(vk);
        note = NoteKey(vk);	//workaround: the note key is known early in case it is needed
        if (i >= 0 && !shift && !control)
        {
            BLOCKDESELECT();
            if (TrackGetNote() >= 0) //the instrument number can only be changed if there is a note
            {
                j = ((TrackGetInstr() & 0x0f) << 4) | i;
                if (j >= INSTRSNUM) j &= 0x0f;	//leaves only the lower digit
                TrackSetInstr(j);
            }
            else goto testnotevalue;	//attempt to catch a fail by testing the other possible condition anyway
            return 1;
        }
        else if (note >= 0 && !shift && !control)
        {
        testnotevalue:
            BLOCKDESELECT();
            if (TrackGetNote() >= 0) break; //do not input a note if there is already a note!
            else goto insertnotes;	//force a note insertion otherwise
        }
        break;

    case 2: //volume column
        i = NumbKey(vk);
        if (i >= 0 && !shift && !control)
        {
            BLOCKDESELECT();
            if (TrackSetVol(i) && !(m_play && m_followplay)) TrackDown(g_SkipLinesAfterNoteInsert);
            return 1;
        }
        break;

    case 3: //speed column
        i = NumbKey(vk);
        if (i >= 0 && !shift && !control)
        {
            BLOCKDESELECT();
            j = TrackGetSpeed();
            if (j < 0) j = 0;
            j = ((j & 0x0f) << 4) | i;
            if (j >= TRACKMAXSPEED) j &= 0x0f;	//leaves only the lower digit
            if (j <= 0) j = -1;	//zero does not exist
            TrackSetSpeed(j);
            return 1;
        }
        break;

    }

    switch (vk)
    {
    case VK_UP:
        if (control && shift)
        {
            if (!g_TrackClipboard.IsBlockSelected())
            {	//if no block is selected, make a block at the current location
                g_TrackClipboard.BlockSetBegin(m_trackactivecol, SongGetActiveTrack(), m_trackactiveline);
                g_TrackClipboard.BlockSetEnd(m_trackactiveline);
            }
            //volume change incrementing
            g_Undo.ChangeTrack(SongGetActiveTrack(), m_trackactiveline, UETYPE_TRACKDATA);
            g_TrackClipboard.BlockVolumeChange(m_activeinstr, 1);
        }
        else
            if (shift && !control)
            {
                //block selection
                BLOCKSETBEGIN();
                if (!g_SkipLinesAfterNoteInsert) TrackUp(1);
                else TrackUp(g_SkipLinesAfterNoteInsert);
                BLOCKSETEND();
            }
            else
                if (control && !shift)
                {
                    if (ISBLOCKSELECTED())
                    {
                        BLOCKDESELECT();
                        break;
                    }
                    else SongUp();
                }
                else
                {
                    BLOCKDESELECT();
                    if (!g_SkipLinesAfterNoteInsert) TrackUp(1);
                    else TrackUp(g_SkipLinesAfterNoteInsert);
                }
        break;

    case VK_DOWN:
        if (control && shift)
        {
            if (!g_TrackClipboard.IsBlockSelected())
            {	//if no block is selected, make a block at the current location
                g_TrackClipboard.BlockSetBegin(m_trackactivecol, SongGetActiveTrack(), m_trackactiveline);
                g_TrackClipboard.BlockSetEnd(m_trackactiveline);
            }
            //volume change decrementing
            g_Undo.ChangeTrack(SongGetActiveTrack(), m_trackactiveline, UETYPE_TRACKDATA);
            g_TrackClipboard.BlockVolumeChange(m_activeinstr, -1);
        }
        else
            if (shift && !control)
            {
                //block selection
                BLOCKSETBEGIN();
                if (!g_SkipLinesAfterNoteInsert) TrackDown(1, 0);
                else TrackDown(g_SkipLinesAfterNoteInsert, 0);	//will not stop on the last line
                BLOCKSETEND();
            }
            else
                if (control && !shift)
                {
                    if (ISBLOCKSELECTED())
                    {
                        BLOCKDESELECT();
                        break;
                    }
                    else
                    {
                        SongDown();
                    }
                }
                else
                {
                    BLOCKDESELECT();
                    if (!g_SkipLinesAfterNoteInsert) TrackDown(1, 0);
                    else TrackDown(g_SkipLinesAfterNoteInsert, 0);	//will not stop on the last line
                }
        break;

    case VK_LEFT:
        if (control && shift)
        {
            if (!g_TrackClipboard.IsBlockSelected())
            {	//if no block is selected, make a block at the current location
                g_TrackClipboard.BlockSetBegin(m_trackactivecol, SongGetActiveTrack(), m_trackactiveline);
                g_TrackClipboard.BlockSetEnd(m_trackactiveline);
            }
            //instrument changes decrementing
            g_Undo.ChangeTrack(SongGetActiveTrack(), m_trackactiveline, UETYPE_TRACKDATA);
            g_TrackClipboard.BlockInstrumentChange(m_activeinstr, -1);
        }
        else
            if (shift && !control)
                ActiveInstrPrev();
            else
                if (control && !shift)
                {
                    if (ISBLOCKSELECTED())
                    {
                        BLOCKDESELECT();
                        break;
                    }
                    else SongTrackDec();
                }
                else
                {
                    BLOCKDESELECT();
                    TrackLeft();
                }
        break;

    case VK_RIGHT:
        if (control && shift)
        {
            if (!g_TrackClipboard.IsBlockSelected())
            {	//if no block is selected, make a block at the current location
                g_TrackClipboard.BlockSetBegin(m_trackactivecol, SongGetActiveTrack(), m_trackactiveline);
                g_TrackClipboard.BlockSetEnd(m_trackactiveline);
            }
            //instrument changes incrementing
            g_Undo.ChangeTrack(SongGetActiveTrack(), m_trackactiveline, UETYPE_TRACKDATA);
            g_TrackClipboard.BlockInstrumentChange(m_activeinstr, 1);
        }
        else
            if (shift && !control)
                ActiveInstrNext();
            else
                if (control && !shift)
                {
                    if (ISBLOCKSELECTED())
                    {
                        BLOCKDESELECT();
                        break;
                    }
                    else SongTrackInc();
                }
                else
                {
                    BLOCKDESELECT();
                    TrackRight();
                }
        break;

    case VK_PRIOR:
        if (!shift && control)
        {
            BLOCKDESELECT();
            SongUp();
        }
        else
            if (!control && shift)
            {
                //move to the previous goto
                BLOCKDESELECT();
                SongSubsongPrev();
            }
            else
                if (m_play && m_followplay) break;	//prevents moving at all during play+follow
                else
                {
                    BLOCKDESELECT();
                    if (m_trackactiveline > 0)
                    {
                        m_trackactiveline = ((m_trackactiveline - 1) / g_trackLinePrimaryHighlight) * g_trackLinePrimaryHighlight;
                    }
                }
        break;

    case VK_NEXT:
        if (!shift && control)
        {
            BLOCKDESELECT();
            SongDown();
        }
        else
            if (!control && shift)
            {
                //move to the next goto
                BLOCKDESELECT();
                SongSubsongNext();
            }
            else
                if (m_play && m_followplay) break;	//prevents moving at all during play+follow
                else
                {
                    BLOCKDESELECT();
                    m_trackactiveline = ((m_trackactiveline + g_trackLinePrimaryHighlight) / g_trackLinePrimaryHighlight) * g_trackLinePrimaryHighlight;
                    if (m_trackactiveline > GetSmallestMaxtracklen(m_songactiveline) - 1)
                        m_trackactiveline -= g_trackLinePrimaryHighlight;
                }
        break;

    case VK_SUBTRACT:
        VolumeDown();
        break;

    case VK_ADD:
        VolumeUp();
        break;

    case VK_DIVIDE:
        OctaveDown();
        break;

    case VK_MULTIPLY:
        OctaveUp();
        break;

    case VK_TAB:
        BLOCKDESELECT();
        if (shift)
            TrackLeft(1); //SHIFT+TAB
        else if (control)
            CursorToSpeedColumn(); //CTRL+TAB
        else
            TrackRight(1);
        break;

    case VK_ESCAPE:
        BLOCKDESELECT();
        break;

    case VK_A:
        if (g_TrackClipboard.IsBlockSelected() && shift && control)
        {	//Shift+control+A
            //switch ALL / no ALL
            g_TrackClipboard.BlockAllOnOff();
        }
        else
            if (control && !shift)
            {
                //control+A
                //selection of the whole track (from 0 to the length of that track)
                g_TrackClipboard.BlockDeselect();
                g_TrackClipboard.BlockSetBegin(m_trackactivecol, SongGetActiveTrack(), 0);
                g_TrackClipboard.BlockSetEnd(GetSmallestMaxtracklen(m_songactiveline) - 1);
            }
        break;

    case VK_B:			//restore block from backup
        if (g_TrackClipboard.IsBlockSelected() && control && !shift)
        {
            g_Undo.ChangeTrack(SongGetActiveTrack(), m_trackactiveline, UETYPE_TRACKDATA, 1);
            g_TrackClipboard.BlockRestoreFromBackup();
        }
        break;

    case VK_C:
        if (control && !shift)
        {
            if (!g_TrackClipboard.IsBlockSelected())
            {	//if no block is selected, make a block at the current location
                g_TrackClipboard.BlockSetBegin(m_trackactivecol, SongGetActiveTrack(), m_trackactiveline);
                g_TrackClipboard.BlockSetEnd(m_trackactiveline);
            }
            g_TrackClipboard.BlockCopyToClipboard();
        }
        break;

    case VK_E:
        if (control && !shift)		//exchange block and clipboard
        {
            if (g_TrackClipboard.IsBlockSelected())
            {
                g_Undo.ChangeTrack(SongGetActiveTrack(), m_trackactiveline, UETYPE_TRACKDATA, 1);
                if (!g_TrackClipboard.BlockExchangeClipboard()) g_Undo.DropLast();
            }
        }
        break;

    case VK_M:
        if (control && !shift)
        {
            BLOCKDESELECT();
            BlockPaste(1);	//paste merge
        }
        break;

    case VK_V:
        if (control && !shift)
        {
            BLOCKDESELECT();
            BlockPaste();	//classic paste
        }
        break;

    case VK_X:
        if (control && !shift)
        {
            if (!g_TrackClipboard.IsBlockSelected())
            {	//if no block is selected, make a block at the current location
                g_TrackClipboard.BlockSetBegin(m_trackactivecol, SongGetActiveTrack(), m_trackactiveline);
                g_TrackClipboard.BlockSetEnd(m_trackactiveline);
            }
            g_Undo.ChangeTrack(SongGetActiveTrack(), m_trackactiveline, UETYPE_TRACKDATA, 1);
            g_TrackClipboard.BlockCopyToClipboard();
            g_TrackClipboard.BlockClear();
        }
        break;

    case VK_F:
        if (control && !shift && g_TrackClipboard.IsBlockSelected())
        {
            g_Undo.ChangeTrack(SongGetActiveTrack(), m_trackactiveline, UETYPE_TRACKDATA, 1);
            if (!g_TrackClipboard.BlockEffect()) g_Undo.DropLast();
        }
        break;

    case VK_G:		//song goto on/off
        BLOCKDESELECT();
        if (control && !shift) SongTrackGoOnOff();	//control+G => goto on/off line in the song
        break;

    case VK_N:
        BLOCKDESELECT();
        if (control && !shift)
            SongPutnewemptyunusedtrack();
        break;

    case VK_D:
        BLOCKDESELECT();
        if (control && !shift)
            SongMaketracksduplicate();
        break;

    case VK_HOME:
        if (control)
            TrackSetGo();
        else
        {
            if (shift)
            {
                BLOCKSETBEGIN();
                m_trackactiveline = 0;		//line 0
                BLOCKSETEND();
            }
            else
            {
                if (g_TrackClipboard.IsBlockSelected())
                {
                    //sets to the first line in the block
                    int bfro, bto;
                    g_TrackClipboard.GetFromTo(bfro, bto);
                    m_trackactiveline = bfro;
                }
                else
                {
                    if (m_trackactiveline != 0)
                        m_trackactiveline = 0;		//line 0
                    else
                    {
                        i = TrackGetGoLine();
                        if (i >= 0) m_trackactiveline = i;	//at the beginning of the GO loop
                    }
                    BLOCKDESELECT();
                }
            }
        }
        break;

    case VK_END:
        if (control)
            TrackSetEnd();
        else
        {
            if (shift)
            {
                BLOCKSETBEGIN();
                if (TrackGetGoLine() >= 0)
                    m_trackactiveline = g_Tracks.GetMaxTrackLength() - 1; //last line
                else
                    m_trackactiveline = TrackGetLastLine();	//end line
                BLOCKSETEND();
                if (m_trackactiveline < 0)
                {
                    m_trackactiveline = g_Tracks.GetMaxTrackLength() - 1; //failsafe in case the active line is out of bounds
                    BLOCKDESELECT();	//prevents selecting invalid data
                }
            }
            else
            {
                if (g_TrackClipboard.IsBlockSelected())
                {
                    //sets to the first line in the block
                    int bfro, bto;
                    g_TrackClipboard.GetFromTo(bfro, bto);
                    m_trackactiveline = bto;
                }
                else
                {
                    i = TrackGetLastLine();
                    if (i != m_trackactiveline)
                    {
                        m_trackactiveline = i;	//at the end of the GO loop or end line
                        if (m_trackactiveline < 0) m_trackactiveline = g_Tracks.GetMaxTrackLength() - 1; //failsafe in case the active line is out of bounds
                    }
                    else m_trackactiveline = g_Tracks.GetMaxTrackLength() - 1; //last line
                    BLOCKDESELECT();
                }
            }
        }
        break;

    case VK_RETURN:	//FIXME: Channels are desynched when track End or Loops are detected, bad hack...
        int instr, vol, oldline;
        {
            if (shift && control)
            {
                BLOCKDESELECT();
                TrackSetEnd();
                break;
            }
            if (!shift && (BOOL)control != (BOOL)g_keyboard_swapenter)	//control+Enter => plays a whole line (all tracks)
            {
                //for all track columns except the active track column
                for (i = 0; i < GetTracks(); i++)
                {
                    if (i != m_trackactivecol)
                    {
                        TrackGetLoopingNoteInstrVol(m_song[m_songactiveline][i], note, instr, vol);
                        if (note >= 0)		//is there a note?
                            SetPlayPressedTonesTNIV(i, note, instr, vol);	//it will lose it as it is there
                        else
                            if (vol >= 0) //there is no note, but is there a separate volume?
                                SetPlayPressedTonesV(i, vol);				//adjust the volume as it is
                    }
                }
            }
            //and now for that active track column
            TrackGetLoopingNoteInstrVol(SongGetActiveTrack(), note, instr, vol);
            if (note >= 0)		//is there a note?
            {
                SetPlayPressedTonesTNIV(m_trackactivecol, note, instr, vol);	//it will lose it as it is there
                if (shift && !control)	//with the shift, this instrument and the volume will "pick up" as current (only if it is not 0)
                {
                    ActiveInstrSet(instr);
                    if (vol > 0) m_volume = vol;
                }
            }
            else
                if (vol >= 0) //there is no note, but is there a separate volume?
                {
                    SetPlayPressedTonesV(m_trackactivecol, vol); //adjust the volume
                    if (shift && !control && vol > 0) m_volume = vol; //"picks up" the volume as current (only if it is not 0)
                }
        }
        oldline = m_trackactiveline;	//hack, force a line move even if TrackDown prevents it after Enter called it, otherwise the last line would get stuck
        if (TrackDown(1, 0) && oldline == m_trackactiveline) m_trackactiveline++;
        if (g_TrackClipboard.IsBlockSelected())	//if a block is selected, it moves (and plays) only in it
        {
            int bfro, bto;
            g_TrackClipboard.GetFromTo(bfro, bto);
            if (m_trackactiveline<bfro || m_trackactiveline>bto) m_trackactiveline = bfro;
        }
        break;

    case VK_I:
        if (control && !shift)
            goto insertline;
        break;

    case VK_U:
        if (control && !shift)
            goto deleteline;
        break;

    case VK_INSERT:
    {
    insertline:
        BLOCKDESELECT();
        g_Undo.ChangeTrack(SongGetActiveTrack(), m_trackactiveline, UETYPE_TRACKDATA, 0);
        g_Tracks.InsertLine(SongGetActiveTrack(), m_trackactiveline);
    }
    break;

    case VK_DELETE:
        if (g_TrackClipboard.IsBlockSelected())
        {
            //the block is selected, so it deletes it
            g_Undo.ChangeTrack(SongGetActiveTrack(), m_trackactiveline, UETYPE_TRACKDATA, 1);
            g_TrackClipboard.BlockClear();
        }
        else
            if (!shift)
            {
            deleteline:
                BLOCKDESELECT();
                g_Undo.ChangeTrack(SongGetActiveTrack(), m_trackactiveline, UETYPE_TRACKDATA, 0);
                g_Tracks.DeleteLine(SongGetActiveTrack(), m_trackactiveline);
            }
        break;

    case VK_SPACE:
        if (control) break; //fixes the "return to EDIT MODE space input" bug, by ignoring SPACE if CTRL is also detected
        BLOCKDESELECT();
        if (TrackDelNoteInstrVolSpeed(1 + 2 + 4 + 8)) //all
        {
            if (!(m_play && m_followplay)) TrackDown(g_SkipLinesAfterNoteInsert);
        }
        break;

    case VK_BACK:
    {
        BLOCKDESELECT();
        int r = 0;
        switch (m_trackactivecur)
        {
        case 0:	//note
        case 1: //instrument
            r = TrackDelNoteInstrVolSpeed(1 + 2); //delete note + instrument
            break;
        case 2: //volume
            r = TrackDelNoteInstrVolSpeed(1 + 2 + 4); //delete note + instrument + volume
            break;
        case 3: //speed
            r = TrackSetSpeed(-1);	//delete speed
            break;
        }
        if (r)
        {
            if (!(m_play && m_followplay)) TrackDown(g_SkipLinesAfterNoteInsert);
        }
    }
    break;

    case VK_F1:
        if (control)
        {
            if (!g_TrackClipboard.IsBlockSelected())
            {	//if no block is selected, make a block at the current location
                g_TrackClipboard.BlockSetBegin(m_trackactivecol, SongGetActiveTrack(), m_trackactiveline);
                g_TrackClipboard.BlockSetEnd(m_trackactiveline);
            }
            //transpose down by 1 semitone
            g_Undo.ChangeTrack(SongGetActiveTrack(), m_trackactiveline, UETYPE_TRACKDATA);
            g_TrackClipboard.BlockNoteTransposition(m_activeinstr, -1);
        }
        break;

    case VK_F2:
        if (control)
        {
            if (!g_TrackClipboard.IsBlockSelected())
            {	//if no block is selected, make a block at the current location
                g_TrackClipboard.BlockSetBegin(m_trackactivecol, SongGetActiveTrack(), m_trackactiveline);
                g_TrackClipboard.BlockSetEnd(m_trackactiveline);
            }
            //transpose up by 1 semitone
            g_Undo.ChangeTrack(SongGetActiveTrack(), m_trackactiveline, UETYPE_TRACKDATA);
            g_TrackClipboard.BlockNoteTransposition(m_activeinstr, 1);
        }
        break;

    case VK_F3:
        if (control)
        {
            if (!g_TrackClipboard.IsBlockSelected())
            {	//if no block is selected, make a block at the current location
                g_TrackClipboard.BlockSetBegin(m_trackactivecol, SongGetActiveTrack(), m_trackactiveline);
                g_TrackClipboard.BlockSetEnd(m_trackactiveline);
            }
            //transpose down by 1 octave
            g_Undo.ChangeTrack(SongGetActiveTrack(), m_trackactiveline, UETYPE_TRACKDATA);
            g_TrackClipboard.BlockNoteTransposition(m_activeinstr, -12);
        }
        break;

    case VK_F4:
        if (control)
        {
            if (!g_TrackClipboard.IsBlockSelected())
            {	//if no block is selected, make a block at the current location
                g_TrackClipboard.BlockSetBegin(m_trackactivecol, SongGetActiveTrack(), m_trackactiveline);
                g_TrackClipboard.BlockSetEnd(m_trackactiveline);
            }
            //transpose up by 1 octave
            g_Undo.ChangeTrack(SongGetActiveTrack(), m_trackactiveline, UETYPE_TRACKDATA);
            g_TrackClipboard.BlockNoteTransposition(m_activeinstr, 12);
        }
        break;

    default:
        return 0;
        break;
    }
    return 1;
}

BOOL CSong::TrackCursorGoto(CPoint point)
{
    int xch, x, y;
    xch = (point.x / (16 * 8));
    x = (point.x - (xch * 16 * 8)) / 8;

    y = (point.y + 0) / 16 - 8 + g_cursoractview;	//m_trackactiveline;

    //if (y >= 0 && y < g_Tracks.m_maxtracklen)
    if (y >= 0 && y < GetSmallestMaxtracklen(m_songactiveline))	//variable pattern size, to prevent clicking "out of bounds" with the new tracks display
    {
        if (xch >= 0 && xch < GetTracks()) m_trackactivecol = xch;
        if (m_play && m_followplay)	//prevents moving at all during play+follow
            goto notracklinechange;
        else
            m_trackactiveline = y;
    }
    else
        return 0;
notracklinechange:
    switch (x)
    {
    case 0:
    case 1:
    case 2:
    case 3:
        m_trackactivecur = 0;
        break;
    case 4:
    case 5:
    case 6:
        m_trackactivecur = 1;
        break;
    case 7:
    case 8:
    case 9:
        m_trackactivecur = 2;
        break;
    case 10:
    case 11:
    case 12:
    case 13:
    case 14:
    case 15:	//filling more area avoids jumping all over the place, between the speed column and the next channel's note column
    case 16:
        m_trackactivecur = 3;
        break;
    }
    g_activepart = Part::PART_TRACKS;
    return 1;
}



BOOL CSong::SongKey(int vk, int shift, int control)
{
    int isgo = (m_songgo[m_songactiveline] >= 0) ? 1 : 0;

    if (!control && NumbKey(vk) >= 0)
    {
        return SongTrackSetByNum(NumbKey(vk));
    }

    switch (vk)
    {
    case VK_UP:
        BLOCKDESELECT();
        SongUp();
        break;

    case VK_DOWN:
        BLOCKDESELECT();
        SongDown();
        break;

    case VK_LEFT:
        if (shift)
            ActiveInstrPrev();
        else
            if (control)
            {
                if (isgo)
                    SongTrackGoDec();
                else
                    SongTrackDec();
            }
            else
                TrackLeft(1);
        break;

    case VK_RIGHT:
        if (shift)
            ActiveInstrNext();
        else
            if (control)
            {
                if (isgo)
                    SongTrackGoInc();
                else
                    SongTrackInc();
            }
            else
                TrackRight(1);
        break;

    case VK_TAB:
        if (shift)
            TrackLeft(1); //SHIFT+TAB
        else
            TrackRight(1);
        break;

    case VK_U:	//Control+VK_U:
        if (!control) break;
    case VK_DELETE:
        SongDeleteLine(m_songactiveline);
        break;

    case VK_I:	//Control+VK_I:
        if (!control) break;
    case VK_INSERT:
        SongInsertLine(m_songactiveline);
        break;

    case VK_O:	//Control+VK_O
        if (control)
            SongInsertCopyOrCloneOfSongLines(m_songactiveline);
        break;

    case VK_P:	//Control+VK_P
        if (control)
            SongPrepareNewLine(m_songactiveline);
        break;

    case VK_N:	//Control+VK_N
        if (control)
            SongPutnewemptyunusedtrack();
        break;

    case VK_D:	//Control+VK_D
        BLOCKDESELECT();
        if (control)
            SongMaketracksduplicate();
        break;

    case VK_BACK:
        if (isgo)
            SongTrackGoOnOff();	//Go off
        else
            SongTrackEmpty();
        break;

    case VK_G:
        if (control)
            SongTrackGoOnOff();	//Go on/off
        break;

    case VK_RETURN:
        g_activepart = g_active_ti;
        break;

    case VK_HOME:
        m_songactiveline = 0;
        break;

    case VK_END:
    {
        int i, j, la = 0;
        for (j = 0; j < SONGLEN; j++)
        {
            for (i = 0; i < GetTracks(); i++) if (m_song[j][i] >= 0) { la = j; break; }
            if (m_songgo[j] >= 0) la = j;
        }
        m_songactiveline = la;
    }
    break;

    case VK_PRIOR:
        if (shift)
            SongSubsongPrev();
        else
        {
            SongUp();
        }
        break;

    case VK_NEXT:
        if (shift)
            SongSubsongNext();
        else
        {
            SongDown();
        }
        break;

    case VK_MULTIPLY:
    {
        OctaveUp();
        return 1;
    }
    break;

    case VK_DIVIDE:
    {
        OctaveDown();
        return 1;
    }
    break;

    case VK_ADD:
    {
        VolumeUp();
        return 1;
    }

    case VK_SUBTRACT:
    {
        VolumeDown();
        return 1;
    }

    default:
        return 0;
        break;
    }
    return 1;
}


BOOL CSong::SongCursorGoto(CPoint point)
{
    int xch, y;
    xch = ((point.x + 4) / (3 * 8));
    y = (point.y + 0) / 16 - 2 + m_songactiveline;
    if (y >= 0 && y < SONGLEN)
    {
        if (xch >= 0 && xch < GetTracks()) m_trackactivecol = xch;
        if (y != m_songactiveline)
        {
            g_activepart = Part::PART_SONG;
            if (m_play && m_followplay)
            {
                auto mode = (m_play == PLAY_TRACK) ? PLAY_TRACK : PLAY_FROM;	//play track in loop, else, play from cursor position
                Stop();
                m_songplayline = m_songactiveline = y;
                m_trackplayline = m_trackactiveline = 0;
                Play(mode, m_followplay); // continue playing using the correct parameters
            }
            else
                m_songactiveline = y;
        }

    }
    else
        return 0;
    m_trackactivecol = xch;
    g_activepart = Part::PART_SONG;
    return 1;
}