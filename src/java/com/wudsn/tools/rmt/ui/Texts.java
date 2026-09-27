package com.wudsn.tools.rmt.ui;

import com.wudsn.tools.base.repository.NLS;

/**
 * Text repository: dialog titles, group-box captions and the validation
 * messages, populated from {@code Texts.properties} by {@link NLS} when this
 * class loads (dis6502's {@code Texts} convention). Message texts C++ builds
 * inline ({@code MessageBox("Could not find: ...")}) stay inline in the
 * Java port too, next to their logic.
 */
public final class Texts extends NLS {

	// OptionsDialog (IDD_OPTIONS)
	public static String OptionsDialog_Title;
	public static String OptionsDialog_GroupGeneral;
	public static String OptionsDialog_GroupModuleDefaults;
	public static String OptionsDialog_GroupKeyboard;
	public static String OptionsDialog_GroupMidi;
	/** The MIDI device combo box's first entry, {@code "None"} (id 0 in C++, device -1). */
	public static String OptionsDialog_MidiDeviceNone;

	// OptionsPathsDialog (IDD_OPTIONS_FILE_PATHS)
	public static String OptionsPathsDialog_Title;
	/** {@code CFolderPickerDialog}'s title has no C++ text; the field's label is used. */
	public static String OptionsPathsDialog_BrowseTitle;

	// TuningDialog (IDD_TUNING)
	public static String TuningDialog_Title;
	public static String TuningDialog_GroupGeneral;
	public static String TuningDialog_GroupRatios;

	// FileNewDialog (IDD_FILE_NEW)
	public static String FileNewDialog_Title;

	// TracksLoadDialog (IDD_TRACKSLOAD)
	public static String TracksLoadDialog_Title;

	// AboutDialog (IDD_ABOUT and the IDS_RMT_* strings)
	public static String AboutDialog_Title;
	public static String AboutDialog_Author;
	public static String AboutDialog_Repository;
	public static String AboutDialog_PokeyEmulation;
	public static String AboutDialog_Cpu6502Emulation;

	// Import dialogs (IDD_IMPORTMOD, IDD_IMPORTMODFINISHED, IDD_IMPORTTMC, IDD_IMPORTTMCFINISHED)
	public static String ImportModDialog_Title;
	public static String ImportModDialog_GroupType;
	public static String ImportTmcDialog_Title;
	public static String ImportTmcFinishedDialog_Title;

	// Export dialogs (IDD_EXPORT_STRIPPED_RMT, IDD_EXPORT_ASM, IDD_EXPORT_RMTPLAYER_ASM, IDD_EXPMSX)
	public static String ExportStrippedRmtDialog_Title;
	public static String ExportAsmDialog_Title;
	public static String ExportRelocatableAsmDialog_Title;
	public static String ExportXexDialog_Title;

	// Editing dialogs
	public static String InsertCopyOrCloneDialog_Title;
	public static String InstrumentChangeDialog_Title;
	public static String ChannelsSelectionDialog_Title;
	public static String TracksOrderDialog_Title;
	public static String ChangeMaxTrackLengthDialog_Title;
	public static String RenumberTracksDialog_Title;
	public static String RenumberInstrumentsDialog_Title;

	// MFC's DDV_MinMaxInt/DDV_MinMaxDouble/DDX_Text messages (AFX_IDP_PARSE_*)
	/** "Please enter an integer between {0} and {1}." */
	public static String Validation_IntegerRange;
	/** "Please enter a number between {0} and {1}." */
	public static String Validation_RealRange;
	/** "Please enter an integer." */
	public static String Validation_Integer;
	/** "Please enter a number." */
	public static String Validation_Real;

	static {
		initializeClass(Texts.class, null);
	}
}
