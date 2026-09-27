package com.wudsn.tools.rmt.ui;

import java.awt.event.KeyEvent;

import com.wudsn.tools.base.gui.KeyStroke;
import com.wudsn.tools.base.repository.Action;
import com.wudsn.tools.base.repository.NLS;

/**
 * Action repository: every menu and command of {@code IDR_MAIN_WINDOW}
 * (Rmt.rc's MENU, TOOLBAR and ACCELERATORS resources plus the STRINGTABLE
 * prompts), one {@link Action} field each, populated from
 * {@code Actions.properties} by {@link NLS} when this class loads - the
 * convention every WUDSN Swing tool uses (dis6502's {@code Actions} is the
 * model). Field names follow {@code MainMenu_<TopMenu>_<Item>}, nested
 * submenus add a segment.
 *
 * <p>Only the keystrokes of the real {@code IDR_MAIN_WINDOW ACCELERATORS}
 * table are accelerators here. Several C++ menu labels carry a "\tCtrl+X"
 * hint for a key that is <em>not</em> an accelerator but a key the tracker
 * handles itself ({@code TrackKey}/{@code SongKey}: Ctrl+U/I/P/O/N/D in
 * the Song menu, Ctrl+B/C/V/M/X/E/F/A/Del in the Block menu, Shift+Ctrl+S,
 * Alt+Enter); those are {@link RmtCommandId#acceleratorIsHint hint-only}
 * accelerators - displayed, never dispatched. A few C++ labels also show a
 * stale key ("Edit Tracks\tF1" while the accelerator is F2); the real
 * accelerator table wins.
 *
 * <p>Labels needed a mnemonic ({@code &}) where the C++ label had none, as
 * {@code ElementFactory} requires one for every menu item.
 */
public final class Actions extends NLS {

	// Top-level menus
	public static Action MainMenu_File;
	public static Action MainMenu_Edit;
	public static Action MainMenu_View;
	public static Action MainMenu_Play;
	public static Action MainMenu_Channels;
	public static Action MainMenu_Song;
	public static Action MainMenu_Instrument;
	public static Action MainMenu_Track;
	public static Action MainMenu_Block;
	public static Action MainMenu_Pokey;
	public static Action MainMenu_Tools;
	public static Action MainMenu_Help;

	// File
	public static Action MainMenu_File_New = new Action(KeyEvent.VK_W, KeyStroke.M1);
	public static Action MainMenu_File_Open = new Action(KeyEvent.VK_L, KeyStroke.M1);
	public static Action MainMenu_File_Reopen = new Action(KeyEvent.VK_R, KeyStroke.M1);
	public static Action MainMenu_File_Save = new Action(KeyEvent.VK_S, KeyStroke.M1);
	public static Action MainMenu_File_SaveAs = new Action(KeyEvent.VK_S, KeyStroke.M1 | KeyStroke.M2);
	public static Action MainMenu_File_Import;
	public static Action MainMenu_File_Export;
	public static Action MainMenu_File_Print;
	public static Action MainMenu_File_PrintPreview;
	public static Action MainMenu_File_PrintSetup;
	public static Action MainMenu_File_Properties = new Action(KeyEvent.VK_ENTER, KeyStroke.M3);
	public static Action MainMenu_File_Exit = new Action(KeyEvent.VK_F4, KeyStroke.M3);

	// Edit
	public static Action MainMenu_Edit_Undo = new Action(KeyEvent.VK_Z, KeyStroke.M1);
	public static Action MainMenu_Edit_Redo = new Action(KeyEvent.VK_Y, KeyStroke.M1);
	public static Action MainMenu_Edit_ClearUndoRedoHistory;
	public static Action MainMenu_Edit_PartTracks = new Action(KeyEvent.VK_F2, 0);
	public static Action MainMenu_Edit_PartInstruments = new Action(KeyEvent.VK_F3, 0);
	public static Action MainMenu_Edit_PartInfo = new Action(KeyEvent.VK_F4, KeyStroke.M2);
	public static Action MainMenu_Edit_PartSong = new Action(KeyEvent.VK_F4, 0);
	public static Action MainMenu_Edit_SwitchEditMode = new Action(KeyEvent.VK_SPACE, KeyStroke.M1);

	// View
	public static Action MainMenu_View_MainToolbar;
	public static Action MainMenu_View_BlockToolbar;
	public static Action MainMenu_View_StatusBar;
	public static Action MainMenu_View_PlayTimeCounter;
	public static Action MainMenu_View_VolumeAnalyzer;
	public static Action MainMenu_View_InstrumentActiveHelp;

	// Play
	public static Action MainMenu_Play_PlayFromBookmark = new Action(KeyEvent.VK_F7, KeyStroke.M2);
	public static Action MainMenu_Play_PlayFromStart = new Action(KeyEvent.VK_F5, 0);
	public static Action MainMenu_Play_Play = new Action(KeyEvent.VK_F7, 0);
	public static Action MainMenu_Play_PlayAndLoopSongLine = new Action(KeyEvent.VK_F6, 0);
	public static Action MainMenu_Play_Stop = new Action(KeyEvent.VK_ESCAPE, 0);
	public static Action MainMenu_Play_ToggleFollowMode = new Action(KeyEvent.VK_F12, 0);

	// Channels
	public static Action MainMenu_Channels_Channel1 = new Action(KeyEvent.VK_1, KeyStroke.M1);
	public static Action MainMenu_Channels_Channel2 = new Action(KeyEvent.VK_2, KeyStroke.M1);
	public static Action MainMenu_Channels_Channel3 = new Action(KeyEvent.VK_3, KeyStroke.M1);
	public static Action MainMenu_Channels_Channel4 = new Action(KeyEvent.VK_4, KeyStroke.M1);
	public static Action MainMenu_Channels_Channel5 = new Action(KeyEvent.VK_5, KeyStroke.M1);
	public static Action MainMenu_Channels_Channel6 = new Action(KeyEvent.VK_6, KeyStroke.M1);
	public static Action MainMenu_Channels_Channel7 = new Action(KeyEvent.VK_7, KeyStroke.M1);
	public static Action MainMenu_Channels_Channel8 = new Action(KeyEvent.VK_8, KeyStroke.M1);
	public static Action MainMenu_Channels_ToggleActiveChannel = new Action(KeyEvent.VK_F9, 0);
	public static Action MainMenu_Channels_ToggleAllChannels = new Action(KeyEvent.VK_F9, KeyStroke.M2);
	public static Action MainMenu_Channels_SoloActiveChannel = new Action(KeyEvent.VK_F9, KeyStroke.M1);

	// Song
	public static Action MainMenu_Song_CopyLine;
	public static Action MainMenu_Song_PasteLine;
	public static Action MainMenu_Song_ClearLine;
	public static Action MainMenu_Song_SetBookmark = new Action(KeyEvent.VK_F8, 0);
	public static Action MainMenu_Song_ClearBookmark = new Action(KeyEvent.VK_F8, KeyStroke.M1);
	public static Action MainMenu_Song_DeleteCurrentLine = new Action(KeyEvent.VK_U, KeyStroke.M1);
	public static Action MainMenu_Song_InsertNewEmptyLine = new Action(KeyEvent.VK_I, KeyStroke.M1);
	public static Action MainMenu_Song_InsertNewLineWithUnusedTracks = new Action(KeyEvent.VK_P, KeyStroke.M1);
	public static Action MainMenu_Song_InsertCopyOrCloneOfSongLines = new Action(KeyEvent.VK_O, KeyStroke.M1);
	public static Action MainMenu_Song_PutNewEmptyUnusedTrack = new Action(KeyEvent.VK_N, KeyStroke.M1);
	public static Action MainMenu_Song_MakeTracksDuplicate = new Action(KeyEvent.VK_D, KeyStroke.M1);
	public static Action MainMenu_Song_ToggleTrackNumber;
	public static Action MainMenu_Song_TracksOrderChange;
	public static Action MainMenu_Song_ChangeMaximalLengthOfTracks;
	public static Action MainMenu_Song_SizeOptimization;

	// Instrument
	public static Action MainMenu_Instrument_Copy;
	public static Action MainMenu_Instrument_Paste;
	public static Action MainMenu_Instrument_PasteSpecial;
	public static Action MainMenu_Instrument_PasteSpecial_VolumeEnvelopesOnly;
	public static Action MainMenu_Instrument_PasteSpecial_EnvelopeParametersOnly;
	public static Action MainMenu_Instrument_PasteSpecial_VolumeEnvelopesAndEnvelopeParametersOnly;
	public static Action MainMenu_Instrument_PasteSpecial_InsertVolumeEnvelopesAndEnvelopeParametersToCursorPosition;
	public static Action MainMenu_Instrument_PasteSpecial_VolumeLEnvelopeOnly;
	public static Action MainMenu_Instrument_PasteSpecial_VolumeREnvelopeOnly;
	public static Action MainMenu_Instrument_PasteSpecial_VolumeRToLEnvelopeOnly;
	public static Action MainMenu_Instrument_PasteSpecial_VolumeLToREnvelopeOnly;
	public static Action MainMenu_Instrument_PasteSpecial_TableOnly;
	public static Action MainMenu_Instrument_Cut;
	public static Action MainMenu_Instrument_Delete;
	public static Action MainMenu_Instrument_Info;
	public static Action MainMenu_Instrument_Change;
	public static Action MainMenu_Instrument_RenumberAllInstruments;
	public static Action MainMenu_Instrument_Load;
	public static Action MainMenu_Instrument_Save;
	public static Action MainMenu_Instrument_ClearAllUnusedInstruments;
	public static Action MainMenu_Instrument_AllInstrumentsCleanup;

	// Track
	public static Action MainMenu_Track_Copy;
	public static Action MainMenu_Track_Paste;
	public static Action MainMenu_Track_Cut;
	public static Action MainMenu_Track_Delete;
	public static Action MainMenu_Track_IncreaseStepSize = new Action(KeyEvent.VK_ADD, 0);
	public static Action MainMenu_Track_DecreaseStepSize = new Action(KeyEvent.VK_SUBTRACT, 0);
	public static Action MainMenu_Track_Info;
	public static Action MainMenu_Track_SearchAndBuildLoop;
	public static Action MainMenu_Track_ExpandLoop;
	public static Action MainMenu_Track_SearchAndBuildLoopsInAllTracks;
	public static Action MainMenu_Track_ExpandLoopsInAllTracks;
	public static Action MainMenu_Track_RenumberAllTracks;
	public static Action MainMenu_Track_Load;
	public static Action MainMenu_Track_Save;
	public static Action MainMenu_Track_ClearAllDuplicatedTracks;
	public static Action MainMenu_Track_ClearAllTracksUnusedInSong;
	public static Action MainMenu_Track_AllTracksCleanup;

	// Block
	public static Action MainMenu_Block_RestoreFromBackup = new Action(KeyEvent.VK_B, KeyStroke.M1);
	public static Action MainMenu_Block_Copy = new Action(KeyEvent.VK_C, KeyStroke.M1);
	public static Action MainMenu_Block_Paste = new Action(KeyEvent.VK_V, KeyStroke.M1);
	public static Action MainMenu_Block_PasteSpecial;
	public static Action MainMenu_Block_PasteSpecial_MergeWithCurrentContent = new Action(KeyEvent.VK_M, KeyStroke.M1);
	public static Action MainMenu_Block_PasteSpecial_VolumeValuesOnly;
	public static Action MainMenu_Block_PasteSpecial_SpeedValuesOnly;
	public static Action MainMenu_Block_Cut = new Action(KeyEvent.VK_X, KeyStroke.M1);
	public static Action MainMenu_Block_Delete = new Action(KeyEvent.VK_DELETE, 0);
	public static Action MainMenu_Block_Exchange = new Action(KeyEvent.VK_E, KeyStroke.M1);
	public static Action MainMenu_Block_Effects = new Action(KeyEvent.VK_F, KeyStroke.M1);
	public static Action MainMenu_Block_SelectAll = new Action(KeyEvent.VK_A, KeyStroke.M1);
	// Block toolbar only
	public static Action MainMenu_Block_TransposeNotesUp;
	public static Action MainMenu_Block_TransposeNotesDown;
	public static Action MainMenu_Block_UsePreviousInstrument;
	public static Action MainMenu_Block_UseNextInstrument;
	public static Action MainMenu_Block_IncreaseVolume;
	public static Action MainMenu_Block_DecreaseVolume;
	public static Action MainMenu_Block_ToggleModificationMode;
	public static Action MainMenu_Block_PlayAndLoop = new Action(KeyEvent.VK_F6, KeyStroke.M2);

	// Pokey
	public static Action MainMenu_Pokey_PokeyChipRegisters;
	public static Action MainMenu_Pokey_ActivatePokeyExplorerMode = new Action(KeyEvent.VK_F5, KeyStroke.M1 | KeyStroke.M2);
	public static Action MainMenu_Pokey_Channel1;
	public static Action MainMenu_Pokey_Channel2;
	public static Action MainMenu_Pokey_Channel3;
	public static Action MainMenu_Pokey_Channel4;
	public static Action MainMenu_Pokey_AUDF0;
	public static Action MainMenu_Pokey_AUDC0;
	public static Action MainMenu_Pokey_AUDF1;
	public static Action MainMenu_Pokey_AUDC1;
	public static Action MainMenu_Pokey_AUDF2;
	public static Action MainMenu_Pokey_AUDC2;
	public static Action MainMenu_Pokey_AUDF3;
	public static Action MainMenu_Pokey_AUDC3;
	public static Action MainMenu_Pokey_Register_IncreaseBy01;
	public static Action MainMenu_Pokey_Register_IncreaseBy10;
	public static Action MainMenu_Pokey_Register_DecreaseBy01;
	public static Action MainMenu_Pokey_Register_DecreaseBy10;
	public static Action MainMenu_Pokey_AUDCTL;
	public static Action MainMenu_Pokey_AUDCTL_Bit0;
	public static Action MainMenu_Pokey_AUDCTL_Bit1;
	public static Action MainMenu_Pokey_AUDCTL_Bit2;
	public static Action MainMenu_Pokey_AUDCTL_Bit3;
	public static Action MainMenu_Pokey_AUDCTL_Bit4;
	public static Action MainMenu_Pokey_AUDCTL_Bit5;
	public static Action MainMenu_Pokey_AUDCTL_Bit6;
	public static Action MainMenu_Pokey_AUDCTL_Bit7;
	public static Action MainMenu_Pokey_SKCTL;
	public static Action MainMenu_Pokey_SKCTL_TwoToneMode;
	public static Action MainMenu_Pokey_DebugChannel;
	public static Action MainMenu_Pokey_DebugChannel_NextChannel;
	public static Action MainMenu_Pokey_DebugChannel_PreviousChannel;
	public static Action MainMenu_Pokey_Divisor;
	public static Action MainMenu_Pokey_Divisor_IncreaseBy01;
	public static Action MainMenu_Pokey_Divisor_IncreaseBy1;
	public static Action MainMenu_Pokey_Divisor_DecreaseBy01;
	public static Action MainMenu_Pokey_Divisor_DecreaseBy1;

	// Tools
	public static Action MainMenu_Tools_OpenASMA;
	public static Action MainMenu_Tools_OpenASAPFile;
	public static Action MainMenu_Tools_RunScript;
	public static Action MainMenu_Tools_Options;

	// Help
	public static Action MainMenu_Help_Help;
	public static Action MainMenu_Help_OnlineHelp = new Action(KeyEvent.VK_F1, KeyStroke.M2);
	public static Action MainMenu_Help_About;

	// Toolbar-only
	public static Action Toolbar_MidiOnOff;
	public static Action Toolbar_SkipLinesAfterNoteInsert;

	// Dialog buttons (IDD_OPTIONS, IDD_OPTIONS_FILE_PATHS, IDD_TUNING)
	public static Action OptionsDialog_Paths;
	public static Action OptionsDialog_Tuning;
	public static Action OptionsPathsDialog_Browse;
	public static Action TuningDialog_Test;
	public static Action TuningDialog_Reset;
	public static Action ExportDialog_CopyToClipboard;
	public static Action InstrumentChangeDialog_DefaultRanges;
	public static Action InstrumentChangeDialog_AllInstruments;
	public static Action BlockEffectDialog_Try;
	public static Action BlockEffectDialog_Restore;
	public static Action BlockEffectDialog_PlayStop;
	public static Action BlockEffectDialog_Default;

	static {
		initializeClass(Actions.class, null);
	}
}
