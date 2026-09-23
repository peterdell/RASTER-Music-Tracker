#include "Clipboard.h"
#include "EffectsDlg.h"
#include "GuiHelpers.h"
#include "Song.h"
#include "StdAfx.h"


extern CSong g_Song;


// CTrackClipboard::CTrackClipboard() is implemented in ClipboardCore.cpp (only touches g_Tracks/g_Song, not EffectsDlg.h's dialog or GuiHelpers.h's Global.h dependency).

// CTrackClipboard::IsBlockSelected() is implemented in ClipboardCore.cpp (only touches g_Tracks/g_Song, not EffectsDlg.h's dialog or GuiHelpers.h's Global.h dependency).

// CTrackClipboard::IsTrackSelected() is implemented in ClipboardCore.cpp (only touches g_Tracks/g_Song, not EffectsDlg.h's dialog or GuiHelpers.h's Global.h dependency).

// CTrackClipboard::Clear() is implemented in ClipboardCore.cpp (only touches g_Tracks/g_Song, not EffectsDlg.h's dialog or GuiHelpers.h's Global.h dependency).

// CTrackClipboard::ClearTrack() is implemented in ClipboardCore.cpp (only touches g_Tracks/g_Song, not EffectsDlg.h's dialog or GuiHelpers.h's Global.h dependency).

// CTrackClipboard::BlockSetBegin() is implemented in ClipboardCore.cpp (only touches g_Tracks/g_Song, not EffectsDlg.h's dialog or GuiHelpers.h's Global.h dependency).

// CTrackClipboard::BlockSetEnd() is implemented in ClipboardCore.cpp (only touches g_Tracks/g_Song, not EffectsDlg.h's dialog or GuiHelpers.h's Global.h dependency).

// CTrackClipboard::BlockDeselect() is implemented in ClipboardCore.cpp (only touches g_Tracks/g_Song, not EffectsDlg.h's dialog or GuiHelpers.h's Global.h dependency).

// CTrackClipboard::BlockInitBase() is implemented in ClipboardCore.cpp (only touches g_Tracks/g_Song, not EffectsDlg.h's dialog or GuiHelpers.h's Global.h dependency).

// CTrackClipboard::BlockAllOnOff() is implemented in ClipboardCore.cpp (only touches g_Tracks/g_Song, not EffectsDlg.h's dialog or GuiHelpers.h's Global.h dependency).

// CTrackClipboard::BlockCopyToClipboard() is implemented in ClipboardCore.cpp (only touches g_Tracks/g_Song, not EffectsDlg.h's dialog or GuiHelpers.h's Global.h dependency).

// CTrackClipboard::BlockExchangeClipboard() is implemented in ClipboardCore.cpp (only touches g_Tracks/g_Song, not EffectsDlg.h's dialog or GuiHelpers.h's Global.h dependency).

// CTrackClipboard::BlockPasteToTrack() is implemented in ClipboardCore.cpp (only touches g_Tracks/g_Song, not EffectsDlg.h's dialog or GuiHelpers.h's Global.h dependency).

// CTrackClipboard::BlockClear() is implemented in ClipboardCore.cpp (only touches g_Tracks/g_Song, not EffectsDlg.h's dialog or GuiHelpers.h's Global.h dependency).

// CTrackClipboard::BlockRestoreFromBackup() is implemented in ClipboardCore.cpp (only touches g_Tracks/g_Song, not EffectsDlg.h's dialog or GuiHelpers.h's Global.h dependency).

// CTrackClipboard::GetFromTo() is implemented in ClipboardCore.cpp (only touches g_Tracks/g_Song, not EffectsDlg.h's dialog or GuiHelpers.h's Global.h dependency).

// CTrackClipboard::BlockNoteTransposition() is implemented in ClipboardCore.cpp (only touches g_Tracks/g_Song, not EffectsDlg.h's dialog or GuiHelpers.h's Global.h dependency).

// CTrackClipboard::BlockInstrumentChange() is implemented in ClipboardCore.cpp (only touches g_Tracks/g_Song, not EffectsDlg.h's dialog or GuiHelpers.h's Global.h dependency).

// CTrackClipboard::BlockVolumeChange() is implemented in ClipboardCore.cpp (only touches g_Tracks/g_Song, not EffectsDlg.h's dialog or GuiHelpers.h's Global.h dependency).

BOOL CTrackClipboard::BlockEffect()
{
    CEffectsDlg dlg;
    TTrack* td = g_Tracks.GetTrack(m_seltrack);
    TTrack m_trackorig;
    int bfro, bto;
    int ainstr = g_Song.GetActiveInstr();

    if (td && IsBlockSelected() && IsTrackSelected())
    {
        GetFromTo(bfro, bto);
        if (bto >= td->len)
        {
            bto = td->len - 1;
        }

        m_trackorig = *td;
        dlg.m_trackorig = &m_trackorig;
        dlg.m_trackptr = td;
        dlg.m_bfro = bfro;
        dlg.m_bto = bto;
        dlg.m_ainstr = ainstr;
        dlg.m_all = m_all;
        dlg.m_info.Format(m_all ? "Changes will be provided for all data in the block" : "Changes will be provided for data making use of instrument %02X only", ainstr);

        return (dlg.DoModal() == IDOK);
    }

    return 0;
}