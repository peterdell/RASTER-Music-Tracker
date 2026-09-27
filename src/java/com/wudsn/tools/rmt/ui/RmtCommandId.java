package com.wudsn.tools.rmt.ui;

import com.wudsn.tools.base.repository.Action;

/**
 * The command IDs of {@code Rmt.rc}/{@code resource.h} ({@code ID_FILE_NEW},
 * ...) that {@code CRmtView}/{@code CRmtApp} handle - each with its
 * {@link Actions} entry, whether its menu item is a check item
 * ({@code ON_UPDATE_COMMAND_UI ... SetCheck}), and whether its displayed
 * accelerator is only a hint (see {@link Actions}). {@link RmtCommands}
 * executes them and answers their enabled/checked state; {@link RmtMainMenu}
 * and {@link RmtToolBars} build the widgets. The Pokey Explorer register
 * commands are one ID each in C++ (48 of them); here they are the four
 * {@code POKEY_REGISTER_*} kinds times a register, since none can run until
 * {@code CPokeyController} is ported (they are shown disabled).
 */
public enum RmtCommandId {
	// File
	FILE_NEW(Actions.MainMenu_File_New), FILE_OPEN(Actions.MainMenu_File_Open), FILE_REOPEN(Actions.MainMenu_File_Reopen), FILE_SAVE(Actions.MainMenu_File_Save), FILE_SAVE_AS(Actions.MainMenu_File_SaveAs), FILE_IMPORT(Actions.MainMenu_File_Import), FILE_EXPORT(Actions.MainMenu_File_Export), FILE_PRINT(Actions.MainMenu_File_Print), FILE_PRINT_PREVIEW(Actions.MainMenu_File_PrintPreview), FILE_PRINT_SETUP(Actions.MainMenu_File_PrintSetup), FILE_PROPERTIES(Actions.MainMenu_File_Properties), FILE_EXIT(Actions.MainMenu_File_Exit, false, true),
	// Edit
	EDIT_UNDO(Actions.MainMenu_Edit_Undo), EDIT_REDO(Actions.MainMenu_Edit_Redo), EDIT_CLEAR_UNDO_REDO_HISTORY(Actions.MainMenu_Edit_ClearUndoRedoHistory), PART_TRACKS(Actions.MainMenu_Edit_PartTracks, true), PART_INSTRUMENTS(Actions.MainMenu_Edit_PartInstruments, true), PART_INFO(Actions.MainMenu_Edit_PartInfo, true), PART_SONG(Actions.MainMenu_Edit_PartSong, true), EDIT_SWITCH_EDIT_MODE(Actions.MainMenu_Edit_SwitchEditMode),
	// View
	VIEW_TOOLBAR(Actions.MainMenu_View_MainToolbar, true), VIEW_BLOCKTOOLBAR(Actions.MainMenu_View_BlockToolbar, true), VIEW_STATUS_BAR(Actions.MainMenu_View_StatusBar, true), VIEW_PLAYTIMECOUNTER(Actions.MainMenu_View_PlayTimeCounter, true), VIEW_VOLUMEANALYZER(Actions.MainMenu_View_VolumeAnalyzer, true), VIEW_INSTRUMENTACTIVEHELP(Actions.MainMenu_View_InstrumentActiveHelp, true),
	// Play
	SONG_PLAY_FROM_BOOKMARK(Actions.MainMenu_Play_PlayFromBookmark, true), SONG_PLAY_FROM_START(Actions.MainMenu_Play_PlayFromStart, true), SONG_PLAY_FROM_CURRENT_POSITION(Actions.MainMenu_Play_Play, true), SONG_PLAY_FROM_CURRENT_POSITION_AND_LOOP(Actions.MainMenu_Play_PlayAndLoopSongLine, true), SONG_STOP(Actions.MainMenu_Play_Stop), SONG_PLAY_FOLLOW(Actions.MainMenu_Play_ToggleFollowMode, true),
	// Channels
	CHANNELS_CHANNEL1(Actions.MainMenu_Channels_Channel1, true), CHANNELS_CHANNEL2(Actions.MainMenu_Channels_Channel2, true), CHANNELS_CHANNEL3(Actions.MainMenu_Channels_Channel3, true), CHANNELS_CHANNEL4(Actions.MainMenu_Channels_Channel4, true), CHANNELS_CHANNEL5(Actions.MainMenu_Channels_Channel5, true), CHANNELS_CHANNEL6(Actions.MainMenu_Channels_Channel6, true), CHANNELS_CHANNEL7(Actions.MainMenu_Channels_Channel7, true), CHANNELS_CHANNEL8(Actions.MainMenu_Channels_Channel8, true), CHANNELS_TOGGLE_ACTIVE_CHANNEL_ON_OFF(Actions.MainMenu_Channels_ToggleActiveChannel), CHANNELS_TOGGLE_ALL_CHANNELS_ON_OFF(Actions.MainMenu_Channels_ToggleAllChannels), CHANNELS_TOGGLE_ACTIVE_CHANNEL_SOLO(Actions.MainMenu_Channels_SoloActiveChannel),
	// Song
	SONG_COPY_LINE(Actions.MainMenu_Song_CopyLine), SONG_PASTE_LINE(Actions.MainMenu_Song_PasteLine), SONG_CLEAR_LINE(Actions.MainMenu_Song_ClearLine), SONG_SET_BOOKMARK(Actions.MainMenu_Song_SetBookmark), SONG_CLEAR_BOOKMARK(Actions.MainMenu_Song_ClearBookmark), SONG_DELETEACTUALLINE(Actions.MainMenu_Song_DeleteCurrentLine, false, true), SONG_INSERTNEWEMPTYLINE(Actions.MainMenu_Song_InsertNewEmptyLine, false, true), SONG_INSERTNEWLINEWITHUNUSEDTRACKS(Actions.MainMenu_Song_InsertNewLineWithUnusedTracks, false, true), SONG_INSERTCOPYORCLONEOFSONGLINES(Actions.MainMenu_Song_InsertCopyOrCloneOfSongLines, false, true), SONG_PUTNEWEMPTYUNUSEDTRACK(Actions.MainMenu_Song_PutNewEmptyUnusedTrack, false, true), SONG_MAKETRACKSDUPLICATE(Actions.MainMenu_Song_MakeTracksDuplicate, false, true), SONG_SONG_TOGGLE_TRACK_NUMBER(Actions.MainMenu_Song_ToggleTrackNumber), SONG_TRACKSORDERCHANGE(Actions.MainMenu_Song_TracksOrderChange), SONG_SONGCHANGEMAXIMALLENGTHOFTRACKS(Actions.MainMenu_Song_ChangeMaximalLengthOfTracks), SONG_SIZEOPTIMIZATION(Actions.MainMenu_Song_SizeOptimization),
	// Instrument
	INSTR_COPY(Actions.MainMenu_Instrument_Copy), INSTR_PASTE(Actions.MainMenu_Instrument_Paste), INSTRUMENT_PASTESPECIAL_VOLUMELRENVELOPESONLY(Actions.MainMenu_Instrument_PasteSpecial_VolumeEnvelopesOnly), INSTRUMENT_PASTESPECIAL_ENVELOPEPARAMETERSONLY(Actions.MainMenu_Instrument_PasteSpecial_EnvelopeParametersOnly), INSTRUMENT_PASTESPECIAL_VOLUMEENVANDENVELOPEPARSONLY(Actions.MainMenu_Instrument_PasteSpecial_VolumeEnvelopesAndEnvelopeParametersOnly), INSTRUMENT_PASTESPECIAL_INSERTVOLUMEENVSANDENVELOPEPARSTOCURSORPOSITION(Actions.MainMenu_Instrument_PasteSpecial_InsertVolumeEnvelopesAndEnvelopeParametersToCursorPosition), INSTRUMENT_PASTESPECIAL_VOLUMELENVELOPEONLY(Actions.MainMenu_Instrument_PasteSpecial_VolumeLEnvelopeOnly), INSTRUMENT_PASTESPECIAL_VOLUMERENVELOPEONLY(Actions.MainMenu_Instrument_PasteSpecial_VolumeREnvelopeOnly), INSTRUMENT_PASTESPECIAL_VOLUMERTOLENVELOPEONLY(Actions.MainMenu_Instrument_PasteSpecial_VolumeRToLEnvelopeOnly), INSTRUMENT_PASTESPECIAL_VOLUMELTORENVELOPEONLY(Actions.MainMenu_Instrument_PasteSpecial_VolumeLToREnvelopeOnly), INSTRUMENT_PASTESPECIAL_TABLEONLY(Actions.MainMenu_Instrument_PasteSpecial_TableOnly), INSTR_CUT(Actions.MainMenu_Instrument_Cut), INSTR_DELETE(Actions.MainMenu_Instrument_Delete), INSTRUMENT_INFO(Actions.MainMenu_Instrument_Info), INSTRUMENT_CHANGE(Actions.MainMenu_Instrument_Change), INSTRUMENT_RENUMBERALLINSTRUMENTS(Actions.MainMenu_Instrument_RenumberAllInstruments), INSTR_LOAD(Actions.MainMenu_Instrument_Load), INSTR_SAVE(Actions.MainMenu_Instrument_Save), INSTRUMENT_CLEARALLUNUSEDINSTRUMENTS(Actions.MainMenu_Instrument_ClearAllUnusedInstruments), INSTR_ALLINSTRUMENTSCLEANUP(Actions.MainMenu_Instrument_AllInstrumentsCleanup),
	// Track
	TRACK_COPY(Actions.MainMenu_Track_Copy), TRACK_PASTE(Actions.MainMenu_Track_Paste), TRACK_CUT(Actions.MainMenu_Track_Cut), TRACK_DELETE(Actions.MainMenu_Track_Delete), SONG_INCREASE_PATTERN_STEP_SIZE(Actions.MainMenu_Track_IncreaseStepSize), SONG_DECREASE_PATTERN_STEP_SIZE(Actions.MainMenu_Track_DecreaseStepSize), TRACK_INFOABOUTUSINGOFACTUALTRACK(Actions.MainMenu_Track_Info), TRACK_SEARCHANDBUILDLOOP(Actions.MainMenu_Track_SearchAndBuildLoop), TRACK_EXPANDLOOP(Actions.MainMenu_Track_ExpandLoop), SONG_SEARCHANDBUILDLOOPSINALLTRACKS(Actions.MainMenu_Track_SearchAndBuildLoopsInAllTracks), SONG_EXPANDLOOPSINALLTRACKS(Actions.MainMenu_Track_ExpandLoopsInAllTracks), TRACK_RENUMBERALLTRACKS(Actions.MainMenu_Track_RenumberAllTracks), TRACK_LOAD(Actions.MainMenu_Track_Load), TRACK_SAVE(Actions.MainMenu_Track_Save), TRACK_CLEARALLDUPLICATEDTRACKS(Actions.MainMenu_Track_ClearAllDuplicatedTracks), TRACK_CLEARALLTRACKSUNUSEDINSONG(Actions.MainMenu_Track_ClearAllTracksUnusedInSong), TRACK_ALLTRACKSCLEANUP(Actions.MainMenu_Track_AllTracksCleanup),
	// Block
	BLOCK_RESTORE_FROM_BACKUP(Actions.MainMenu_Block_RestoreFromBackup, false, true), BLOCK_COPY(Actions.MainMenu_Block_Copy, false, true), BLOCK_PASTE(Actions.MainMenu_Block_Paste, false, true), BLOCK_PASTESPECIAL_MERGEWITHCURRENTCONTENT(Actions.MainMenu_Block_PasteSpecial_MergeWithCurrentContent, false, true), BLOCK_PASTESPECIAL_VOLUMEVALUESONLY(Actions.MainMenu_Block_PasteSpecial_VolumeValuesOnly), BLOCK_PASTESPECIAL_SPEEDVALUESONLY(Actions.MainMenu_Block_PasteSpecial_SpeedValuesOnly), BLOCK_CUT(Actions.MainMenu_Block_Cut, false, true), BLOCK_DELETE(Actions.MainMenu_Block_Delete, false, true), BLOCK_EXCHANGE(Actions.MainMenu_Block_Exchange, false, true), BLOCK_APPLY_EFFECTS(Actions.MainMenu_Block_Effects, false, true), BLOCK_SELECTALL(Actions.MainMenu_Block_SelectAll, false, true), BLOCK_TRANSPOSE_NOTES_UP(Actions.MainMenu_Block_TransposeNotesUp, false, true), BLOCK_TRANSPOSE_NOTES_DOWN(Actions.MainMenu_Block_TransposeNotesDown, false, true), BLOCK_USE_PREVIOUS_INSTRUMENT(Actions.MainMenu_Block_UsePreviousInstrument, false, true), BLOCK_USE_NEXT_INSTRUMENT(Actions.MainMenu_Block_UseNextInstrument, false, true), BLOCK_INCREASE_VOLUME(Actions.MainMenu_Block_IncreaseVolume, false, true), BLOCK_DECREASE_VOLUME(Actions.MainMenu_Block_DecreaseVolume, false, true), BLOCK_TOGGLE_MODIFICATION_MODE(Actions.MainMenu_Block_ToggleModificationMode, true, true), BLOCK_PLAY_AND_LOOP(Actions.MainMenu_Block_PlayAndLoop, true),
	// Pokey
	VIEW_POKEYREGS(Actions.MainMenu_Pokey_PokeyChipRegisters, true), EDIT_ACTIVATE_POKEY_EXPLORER_MODE(Actions.MainMenu_Pokey_ActivatePokeyExplorerMode), POKEY_REGISTER_INCREASE_BY_01(Actions.MainMenu_Pokey_Register_IncreaseBy01), POKEY_REGISTER_INCREASE_BY_10(Actions.MainMenu_Pokey_Register_IncreaseBy10), POKEY_REGISTER_DECREASE_BY_01(Actions.MainMenu_Pokey_Register_DecreaseBy01), POKEY_REGISTER_DECREASE_BY_10(Actions.MainMenu_Pokey_Register_DecreaseBy10), POKEY_AUDCTL_BIT0(Actions.MainMenu_Pokey_AUDCTL_Bit0, false, true), POKEY_AUDCTL_BIT1(Actions.MainMenu_Pokey_AUDCTL_Bit1, false, true), POKEY_AUDCTL_BIT2(Actions.MainMenu_Pokey_AUDCTL_Bit2, false, true), POKEY_AUDCTL_BIT3(Actions.MainMenu_Pokey_AUDCTL_Bit3, false, true), POKEY_AUDCTL_BIT4(Actions.MainMenu_Pokey_AUDCTL_Bit4, false, true), POKEY_AUDCTL_BIT5(Actions.MainMenu_Pokey_AUDCTL_Bit5, false, true), POKEY_AUDCTL_BIT6(Actions.MainMenu_Pokey_AUDCTL_Bit6, false, true), POKEY_AUDCTL_BIT7(Actions.MainMenu_Pokey_AUDCTL_Bit7, false, true), POKEY_SKCTL_TWO_TONE_MODE(Actions.MainMenu_Pokey_SKCTL_TwoToneMode, false, true), POKEY_NEXTCHANNEL(Actions.MainMenu_Pokey_DebugChannel_NextChannel, false, true), POKEY_PREVIOUSCHANNEL(Actions.MainMenu_Pokey_DebugChannel_PreviousChannel, false, true), POKEY_DIVISOR_INCREASE_BY_01(Actions.MainMenu_Pokey_Divisor_IncreaseBy01, false, true), POKEY_DIVISOR_INCREASE_BY_1(Actions.MainMenu_Pokey_Divisor_IncreaseBy1, false, true), POKEY_DIVISOR_DECREASE_BY_01(Actions.MainMenu_Pokey_Divisor_DecreaseBy01, false, true), POKEY_DIVISOR_DECREASE_BY_1(Actions.MainMenu_Pokey_Divisor_DecreaseBy1, false, true),
	// Tools
	TOOLS_OPEN_ASMA(Actions.MainMenu_Tools_OpenASMA), TOOLS_OPEN_ASAP_FILE(Actions.MainMenu_Tools_OpenASAPFile), TOOLS_RUN_SCRIPT(Actions.MainMenu_Tools_RunScript), TOOLS_OPTIONS(Actions.MainMenu_Tools_Options),
	// Help
	HELP(Actions.MainMenu_Help_Help, false, true), CONTEXT_HELP(Actions.MainMenu_Help_OnlineHelp), HELP_ABOUT_APP(Actions.MainMenu_Help_About),
	// Toolbar-only
	TOOLBAR_SWITCH_EDIT_MODE(Actions.MainMenu_Edit_SwitchEditMode, true), MIDIONOFF(Actions.Toolbar_MidiOnOff, true),
	// Accelerator-only: Ctrl+F12 in IDR_MAIN_WINDOW ACCELERATORS, no menu item or button (RmtMainMenu registers the key)
	SONG_TOGGLE_NTSC(Actions.Key_ToggleNTSC);

	public final Action action;
	/** The menu item is a {@code JCheckBoxMenuItem} / the toolbar button a toggle. */
	public final boolean checkable;
	/** The accelerator is displayed but is really a key the tracker handles itself - never dispatched as a command. */
	public final boolean acceleratorIsHint;

	RmtCommandId(Action action) {
		this(action, false, false);
	}

	RmtCommandId(Action action, boolean checkable) {
		this(action, checkable, false);
	}

	RmtCommandId(Action action, boolean checkable, boolean acceleratorIsHint) {
		this.action = action;
		this.checkable = checkable;
		this.acceleratorIsHint = acceleratorIsHint;
	}
}
