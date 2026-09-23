#include "StdAfx.h"

#include "Undo.h"

CUndo g_Undo;

// CUndo's constructor/destructor and pure bookkeeping methods (Init/Clear/
// DeleteEvent/GetUndoSteps/GetRedoSteps/DropLast/Separator/PosIsEqual) are
// real here, copied verbatim from Undo.cpp - they only touch CUndo's own
// m_uar/m_head/m_tail/m_undosteps/m_redosteps state and are cheap to run
// (confirmed while scoping Song.cpp's editing methods, see plans/NOTES.md).
//
// Undo.cpp itself #includes Global.h and its ChangeTrack/ChangeSong/
// ChangeInstrument/ChangeInfo/Undo()/Redo()/PerformEvent methods record/
// replay real edits against g_Tracks/g_Instruments/g_Song/g_hwnd - real
// hazards and a much wider dependency graph than this test binary wants to
// link. They're stubbed as no-ops here: CSong's editing methods (see
// SongEditing.cpp) call Separator()/ChangeSong()/ChangeTrack() only to
// *record* an edit for later undo - the edit's own visible effect (what
// these tests characterize) happens independently of whether it was
// recorded.

CUndo::CUndo()
{
    for (int i = 0; i < MAXUNDO; i++) { m_uar[i] = NULL; }
}

CUndo::~CUndo()
{
    for (int i = 0; i < MAXUNDO; i++) { DeleteEvent(i); }
}

void CUndo::Init()
{
    Clear();
}

void CUndo::Clear()
{
    m_head = 0;
    m_tail = 0;
    m_headmax = 0;
    m_undosteps = m_redosteps = 0;
    for (int i = 0; i < MAXUNDO; i++) { DeleteEvent(i); }
}

char CUndo::DeleteEvent(int i)
{
    TUndoEvent* ue = m_uar[i];
    if (!ue)
    {
        return 1;
    }
    char sep = ue->separator;
    if (ue->cursor)
    {
        delete[] ue->cursor;
    }
    if (ue->pos)
    {
        delete[] ue->pos;
    }
    if (ue->data)
    {
        delete[] ue->data;
    }
    delete ue;
    m_uar[i] = NULL;
    return sep;
}

int CUndo::GetUndoSteps() const {
    return m_undosteps;
}

int CUndo::GetRedoSteps() const {
    return m_redosteps;
}

void CUndo::DropLast()
{
    if (m_head == m_tail) { return; }
    m_head = (m_head + MAXUNDO - 1) % MAXUNDO;
    DeleteEvent(m_head);
    m_undosteps--;		//will count this step
}

void CUndo::Separator(int sep)
{
    auto le = m_uar[(m_head + MAXUNDO - 1) % MAXUNDO];
    if (!le) { return; }
    if (sep < 0 && le->separator >= 0)
    {
        m_undosteps--; //the number of undo counted in InsertEvent
    }
    le->separator = sep;
}

BOOL CUndo::PosIsEqual(int* pos1, int* pos2, UndoType type)
{
    int len;
    switch (type >> 6)	//  /64
    {
    case 0:
        len = POSGROUPTYPE0_63SIZE;
        break;
    case 1:
        len = POSGROUPTYPE64_127SIZE;
        break;
    case 2:
        len = POSGROUPTYPE128_191SIZE;
        break;
    default:
        return FALSE;
    }
    for (int i = 0; i < len; i++) { if (pos1[i] != pos2[i]) { return FALSE; } }
    return TRUE;
}

// Link-only no-op stubs - see file header comment.
BOOL CUndo::Undo() { return FALSE; }
BOOL CUndo::Redo() { return FALSE; }
void CUndo::InsertEvent(TUndoEvent*) {}
char CUndo::PerformEvent(int) { return 0; }
void CUndo::ChangeTrack(int, int, UndoType, char) {}
void CUndo::ChangeSong(int, int, UndoType, char) {}
void CUndo::ChangeInstrument(int, int, UndoType, char) {}
void CUndo::ChangeInfo(int, UndoType, char) {}
