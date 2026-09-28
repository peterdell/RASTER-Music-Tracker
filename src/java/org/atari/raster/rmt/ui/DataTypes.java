package org.atari.raster.rmt.ui;

import com.wudsn.tools.base.repository.DataType;
import com.wudsn.tools.base.repository.NLS;

/**
 * Data type repository: the labels (with mnemonics and tool tips) of the
 * dialogs' fields, one {@link DataType} per field, populated from
 * {@code DataTypes.properties} by {@link NLS} when this class loads - the
 * WUDSN convention dis6502's {@code DataTypes} follows. The texts are those
 * of the C++ dialog resources (Rmt.rc); mnemonics were added or moved
 * where {@code ElementFactory} demands one per label and the C++ resource
 * had none or a clashing one.
 */
public final class DataTypes extends NLS {

	// OptionsDialog (IDD_OPTIONS)
	public static DataType OptionsDialog_ScalingPercentage = new DataType(Integer.class);
	public static DataType OptionsDialog_TrackLineHighlight = new DataType(Integer.class);
	public static DataType OptionsDialog_UseGermanNotation = new DataType(Boolean.class);
	public static DataType OptionsDialog_TrackLineAltNumbering = new DataType(Boolean.class);
	public static DataType OptionsDialog_DisplayFlatNotes = new DataType(Boolean.class);
	public static DataType OptionsDialog_SmoothScroll = new DataType(Boolean.class);
	public static DataType OptionsDialog_NoHwSoundBuffer = new DataType(Boolean.class);
	public static DataType OptionsDialog_DebugDisplay = new DataType(Boolean.class);
	public static DataType OptionsDialog_NTSC = new DataType(Boolean.class);
	public static DataType OptionsDialog_TrackerDriverVersion = new DataType(String.class);
	public static DataType OptionsDialog_KeyboardLayout = new DataType(String.class);
	public static DataType OptionsDialog_KeyboardUpDownContinue = new DataType(Boolean.class);
	public static DataType OptionsDialog_KeyboardRememberOctavesAndVolumes = new DataType(Boolean.class);
	public static DataType OptionsDialog_KeyboardEscResetAtariSound = new DataType(Boolean.class);
	public static DataType OptionsDialog_KeyboardAskWhenControlS = new DataType(Boolean.class);
	public static DataType OptionsDialog_MidiDevice = new DataType(String.class);
	public static DataType OptionsDialog_MidiTouchResponse = new DataType(Boolean.class);
	public static DataType OptionsDialog_MidiVolumeOffset = new DataType(Integer.class);
	public static DataType OptionsDialog_MidiNoteOff = new DataType(Boolean.class);

	// OptionsPathsDialog (IDD_OPTIONS_FILE_PATHS)
	public static DataType OptionsPathsDialog_ModuleFilesFolder = new DataType(String.class);
	public static DataType OptionsPathsDialog_InstrumentFilesFolder = new DataType(String.class);
	public static DataType OptionsPathsDialog_TrackFilesFolder = new DataType(String.class);

	// FileNewDialog (IDD_FILE_NEW)
	public static DataType FileNewDialog_Tracks = new DataType(String.class);
	public static DataType FileNewDialog_NotesPerTrack = new DataType(Integer.class);

	// ExportSapDialog (IDD_EXPORT_SAP_TYPE_R)
	public static DataType ExportSapDialog_Title = new DataType(String.class);
	public static DataType ExportSapDialog_Author = new DataType(String.class);
	public static DataType ExportSapDialog_Date = new DataType(String.class);
	public static DataType ExportSapDialog_Subsongs = new DataType(String.class);

	// TuningDialog (IDD_TUNING)
	public static DataType TuningDialog_BaseTuning = new DataType(Double.class);
	public static DataType TuningDialog_BaseNote = new DataType(String.class);
	public static DataType TuningDialog_Temperament = new DataType(String.class);

	static {
		initializeClass(DataTypes.class, null);
	}
}
