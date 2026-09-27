package com.wudsn.tools.rmt.ui;

import java.awt.BorderLayout;

import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import com.wudsn.tools.base.gui.ModalDialog;
import com.wudsn.tools.rmt.model.AsmFileExporter;
import com.wudsn.tools.rmt.model.AssemblerFormat;

/**
 * "Export ASM for RmtPlayer.asm" - the port of
 * {@code CExportRelocatableAsmForRmtPlayer} ({@code IDD_EXPORT_RMTPLAYER_ASM}):
 * the start label, the three optional relocation labels (their fields
 * enabled by their check boxes), the assembler format, the SFX / GVF / NOS
 * options, and the live size summary {@code BuildRelocatableAsm(...,
 * wantSizeInfoOnly)} produces ({@code ChangeParams()} on every change).
 */
final class ExportRelocatableAsmDialog extends ModalDialog {

	private static final long serialVersionUID = 1L;

	private final RmtSession session;
	private final SongFiles.ModuleDescription stripped;
	private final SongFiles.ModuleDescription withSfx;

	private final JTextField startLabelField = new JTextField(24);
	private final JCheckBox relocateInstrumentsBox = new JCheckBox("Relocate instruments to");
	private final JTextField instrumentsLabelField = new JTextField(16);
	private final JCheckBox relocateTracksBox = new JCheckBox("Relocate tracks to");
	private final JTextField tracksLabelField = new JTextField(16);
	private final JCheckBox relocateSongLinesBox = new JCheckBox("Relocatable song lines to");
	private final JTextField songLinesLabelField = new JTextField(16);
	private final JComboBox<String> formatCombo = new JComboBox<>(ExportStrippedRmtDialog.ASM_FORMATS);
	private final JCheckBox sfxBox = new JCheckBox("SFX support (also preserve unused tracks and instruments in module)");
	private final JCheckBox gvfBox = new JCheckBox("GlobalVolumeFade support (RMTGLOBALVOLUMEFADE variable)");
	private final JCheckBox nosBox = new JCheckBox("No songline start (always start from songline 0)");
	private final JLabel infoLabel = new JLabel("", SwingConstants.CENTER);
	private final JTextArea featArea = new JTextArea(10, 70);
	private boolean initPhase = true;

	ExportRelocatableAsmDialog(JFrame parent, RmtSession session, SongFiles.ModuleDescription stripped, SongFiles.ModuleDescription withSfx) {
		super(parent, Texts.ExportRelocatableAsmDialog_Title);
		this.session = session;
		this.stripped = stripped;
		this.withSfx = withSfx;
		ExportSettings es = session.exportSettings;

		// Set the default song labels
		startLabelField.setText(es.asmLabelForStartOfSong.isEmpty() ? "RMT_SONG_DATA" : es.asmLabelForStartOfSong);
		tracksLabelField.setText(es.asmTracksLabel.isEmpty() ? "RMT_SONG_TRACKS" : es.asmTracksLabel);
		relocateTracksBox.setSelected(es.asmWantRelocatableTracks);
		songLinesLabelField.setText(es.asmSongLinesLabel.isEmpty() ? "RMT_SONG_LINES" : es.asmSongLinesLabel);
		relocateSongLinesBox.setSelected(es.asmWantRelocatableSongLines);
		instrumentsLabelField.setText(es.asmInstrumentsLabel.isEmpty() ? "RMT_INSTRUMENT_DATA" : es.asmInstrumentsLabel);
		relocateInstrumentsBox.setSelected(es.asmWantRelocatableInstruments);
		formatCombo.setSelectedIndex(es.asmFormat.ordinal());
		sfxBox.setSelected(es.rmtStrippedSfx);
		gvfBox.setSelected(es.rmtStrippedGlobalVolumeFade);
		nosBox.setSelected(es.rmtStrippedNoStartingSongLine);
		featArea.setEditable(false);
		featArea.setFont(new java.awt.Font(java.awt.Font.MONOSPACED, java.awt.Font.PLAIN, featArea.getFont().getSize()));

		JPanel grid = new JPanel(new java.awt.GridBagLayout());
		int row = 0;
		DialogSupport.add(grid, new JLabel("<html>The RMT song will be exported as byte definitions with specific labels.<br>The code is fully relocatable and can be split over various locations in memory!</html>"), 0, row++, 3, true);
		DialogSupport.add(grid, new JLabel("ASM Label for start of song data:"), 0, row, 1, false);
		DialogSupport.add(grid, startLabelField, 1, row++, 2, true);
		DialogSupport.add(grid, relocateInstrumentsBox, 0, row, 1, false);
		DialogSupport.add(grid, new JLabel("ASM Instruments Label:", SwingConstants.RIGHT), 1, row, 1, true);
		DialogSupport.add(grid, instrumentsLabelField, 2, row++, 1, false);
		DialogSupport.add(grid, relocateTracksBox, 0, row, 1, false);
		DialogSupport.add(grid, new JLabel("ASM Tracks Label:", SwingConstants.RIGHT), 1, row, 1, true);
		DialogSupport.add(grid, tracksLabelField, 2, row++, 1, false);
		DialogSupport.add(grid, relocateSongLinesBox, 0, row, 1, false);
		DialogSupport.add(grid, new JLabel("ASM Song Label:", SwingConstants.RIGHT), 1, row, 1, true);
		DialogSupport.add(grid, songLinesLabelField, 2, row++, 1, false);
		DialogSupport.add(grid, new JLabel("Assembler format:"), 0, row, 1, false);
		DialogSupport.add(grid, formatCombo, 1, row++, 1, true);
		DialogSupport.add(grid, sfxBox, 0, row++, 3, false);
		DialogSupport.add(grid, gvfBox, 0, row++, 3, false);
		DialogSupport.add(grid, nosBox, 0, row++, 3, false);
		DialogSupport.add(grid, infoLabel, 0, row++, 3, true);
		DialogSupport.add(grid, new JScrollPane(featArea), 0, row++, 3, true);
		getContentPane().add(grid, BorderLayout.CENTER);

		DocumentListener onLabel = new DocumentListener() {
			@Override
			public void insertUpdate(DocumentEvent e) {
				changeParams();
			}

			@Override
			public void removeUpdate(DocumentEvent e) {
				changeParams();
			}

			@Override
			public void changedUpdate(DocumentEvent e) {
				changeParams();
			}
		};
		startLabelField.getDocument().addDocumentListener(onLabel);
		instrumentsLabelField.getDocument().addDocumentListener(onLabel);
		tracksLabelField.getDocument().addDocumentListener(onLabel);
		songLinesLabelField.getDocument().addDocumentListener(onLabel);
		for (JCheckBox box : new JCheckBox[] { relocateInstrumentsBox, relocateTracksBox, relocateSongLinesBox, sfxBox, gvfBox, nosBox }) {
			box.addActionListener(e -> changeParams());
		}
		formatCombo.addActionListener(e -> changeParams());
		initPhase = false;
		changeParams();
	}

	private AssemblerFormat getAssemblerFormat() {
		return formatCombo.getSelectedIndex() == AssemblerFormat.ATASM.ordinal() ? AssemblerFormat.ATASM : AssemblerFormat.XASM;
	}

	/** The current field values as the exporter's parameter record. */
	AsmFileExporter.RelocatableAsmExportParams getParams() {
		return new AsmFileExporter.RelocatableAsmExportParams(startLabelField.getText(), relocateInstrumentsBox.isSelected(), relocateTracksBox.isSelected(), relocateSongLinesBox.isSelected(), instrumentsLabelField.getText(), tracksLabelField.getText(),
				songLinesLabelField.getText(), getAssemblerFormat(), sfxBox.isSelected(), gvfBox.isSelected(), nosBox.isSelected());
	}

	/** {@code ChangeParams()}: enable the label fields per their boxes, show the module length and the size summary. */
	void changeParams() {
		if (initPhase) {
			return;
		}
		instrumentsLabelField.setEnabled(relocateInstrumentsBox.isSelected());
		tracksLabelField.setEnabled(relocateTracksBox.isSelected());
		songLinesLabelField.setEnabled(relocateSongLinesBox.isSelected());
		boolean sfx = sfxBox.isSelected();
		SongFiles.ModuleDescription desc = sfx ? withSfx : stripped;
		int len = desc.length();
		infoLabel.setText(String.format("Length $%04X (%d bytes)", len, len));
		AsmFileExporter.RelocatableAsmExportParams p = getParams();
		AsmFileExporter.Result r = AsmFileExporter.buildRelocatableAsm(session.song, session.instruments, session.tracks, session.tracks4_8, desc.mem(), desc.targetAddrOfModule(), desc.firstByteAfterModule(), desc.instrumentSavedFlags(), "",
				p.wantRelocatableTracks() ? p.strAsmTracksLabel() : "", p.wantRelocatableSongLines() ? p.strAsmSongLinesLabel() : "", p.wantRelocatableInstruments() ? p.strAsmInstrumentsLabel() : "", p.assemblerFormat(), sfx, false, false, true); // Just give me the size info
		featArea.setText(r.success() ? r.code() : "");
		featArea.setCaretPosition(0);
	}

	/** {@code DoModal()}: the parameters, or {@code null} if cancelled. */
	AsmFileExporter.RelocatableAsmExportParams showDialog() {
		showModal(startLabelField);
		return okPressed ? getParams() : null;
	}
}
