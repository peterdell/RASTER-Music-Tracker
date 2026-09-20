#pragma once

#include "General.h"

enum UndoType : int {
    UETYPE_NOTEINSTRVOL = 1,
    UETYPE_NOTEINSTRVOLSPEED = 2,
    UETYPE_SPEED = 3,
    UETYPE_LENGO = 4,
    UETYPE_TRACKDATA = 5,

    UETYPE_SONGTRACK = 33,
    UETYPE_SONGGO = 34,

    UETYPE_SONGDATA = 65,
    UETYPE_INSTRDATA = 66,
    UETYPE_TRACKSALL = 67,
    UETYPE_INSTRSALL = 68,
    UETYPE_INFODATA = 69
};

struct TUndoEvent
{
    Part part;		// the part in which the editing is performed
    int* cursor;	// cursor
    UndoType type;	// type of changed data
    int* pos;		// position of changed data
    void* data;		// change data
    char separator;	// = 0 accumulate continuous changes, = 1 completed change, = -1 more events for one step
};



//-----------------------------------------------------

class CUndo
{
public:
    CUndo();
    ~CUndo();

    void Init();
    void Clear();

    void DropLast();


    int GetUndoSteps() const;
    BOOL Undo();

    int GetRedoSteps() const;
    BOOL Redo();


    BOOL PosIsEqual(int* pos1, int* pos2, UndoType type);

    void Separator(int sep = 1);
    void ChangeTrack(int tracknum, int trackline, UndoType type, char separator = 0);
    void ChangeSong(int songline, int trackcol, UndoType type, char separator = 0);
    void ChangeInstrument(int instrnum, int paridx, UndoType type, char separator = 0);
    void ChangeInfo(int paridx, UndoType type, char separator = 0);

private:

    // Undo operation (one can consume up to 3 records)
    static constexpr int UNDOSTEPS = 100;
    static constexpr int MAXUNDO = (UNDOSTEPS * 3 + 8);	// 302	// 2 extra separating gap

    static constexpr int POSGROUPTYPE0_63SIZE = 2;
    static constexpr int POSGROUPTYPE64_127SIZE = 1;
    static constexpr int POSGROUPTYPE128_191SIZE = 3;

    void InsertEvent(TUndoEvent* ue);
    char DeleteEvent(int i);
    char PerformEvent(int i);

    TUndoEvent* m_uar[MAXUNDO];
    int m_head, m_tail, m_headmax;
    int m_undosteps, m_redosteps;
};

extern CUndo g_Undo;