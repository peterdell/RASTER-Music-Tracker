package org.atari.raster.rmt.ui;

import java.awt.BorderLayout;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import com.wudsn.tools.base.gui.ElementFactory;
import com.wudsn.tools.base.gui.ModalDialog;
import com.wudsn.tools.base.gui.ValueSetField;
import org.atari.raster.rmt.model.AsmFileExporter;
import org.atari.raster.rmt.model.AssemblerFormat;

/**
 * "Export as Stripped RMT File" - the port of {@code CExportStrippedRMTDialog}
 * ({@code IDD_EXPORT_STRIPPED_RMT}): the start address (hex, clamped so the
 * module fits below $10000), the assembler format, the SFX / GlobalVolumeFade
 * / no-starting-songline options, and the live RMT FEAT definitions block
 * with its "Copy Text to Clipboard" button ({@code ChangeParams()} on every
 * change).
 */
final class ExportStrippedRmtDialog extends ModalDialog {

	private static final long serialVersionUID = 1L;


	private final RmtSession session;
	private final SongFiles.ModuleDescription stripped;
	private final SongFiles.ModuleDescription withSfx;
	private final String filename;

	private final JTextField addressField = new JTextField(5);
	private final JLabel infoLabel = new JLabel();
	private final ValueSetField<AssemblerFormat> formatCombo = new ValueSetField<>(AssemblerFormat.class);
	private final JCheckBox sfxBox = new JCheckBox("SFX support (also preserve unused tracks and instruments in module)");
	private final JCheckBox gvfBox = new JCheckBox("GlobalVolumeFade support (RMTGLOBALVOLUMEFADE variable)");
	private final JCheckBox nosBox = new JCheckBox("No songline start (always start from songline 0)");
	private final JTextArea featArea = new JTextArea(8, 70);
	private final JLabel warningLabel = new JLabel();

	/** {@code m_exportAddr} after clamping. */
	private int exportAddr;

	ExportStrippedRmtDialog(JFrame parent, RmtSession session, SongFiles.ModuleDescription stripped, SongFiles.ModuleDescription withSfx, String filename) {
		super(parent, Texts.ExportStrippedRmtDialog_Title);
		this.session = session;
		this.stripped = stripped;
		this.withSfx = withSfx;
		this.filename = filename;
		ExportSettings es = session.exportSettings;

		// Transfer the starting values to the controls
		sfxBox.setSelected(es.rmtStrippedSfx);
		gvfBox.setSelected(es.rmtStrippedGlobalVolumeFade);
		nosBox.setSelected(es.rmtStrippedNoStartingSongLine);
		addressField.setText(String.format("%04X", es.rmtStrippedAddress));
		formatCombo.setValue(es.asmFormat);
		featArea.setEditable(false);
		featArea.setFont(new java.awt.Font(java.awt.Font.MONOSPACED, java.awt.Font.PLAIN, featArea.getFont().getSize()));

		JPanel grid = new JPanel(new java.awt.GridBagLayout());
		DialogSupport.add(grid, new JLabel("Memory Area"), 0, 0, 3, false);
		DialogSupport.add(grid, new JLabel("Start Address:"), 0, 1, 1, false);
		DialogSupport.add(grid, addressField, 1, 1, 1, false);
		DialogSupport.add(grid, infoLabel, 2, 1, 1, true);
		DialogSupport.add(grid, new JLabel("Assembler Format:"), 0, 2, 1, false);
		DialogSupport.add(grid, formatCombo, 1, 2, 2, true);
		DialogSupport.add(grid, sfxBox, 0, 3, 3, false);
		DialogSupport.add(grid, gvfBox, 0, 4, 3, false);
		DialogSupport.add(grid, nosBox, 0, 5, 3, false);
		DialogSupport.add(grid, new JLabel("RMT FEATures definitions (for optimizations of RMT player assembler routine)"), 0, 6, 3, false);
		DialogSupport.add(grid, new JScrollPane(featArea), 0, 7, 3, true);
		JButton copy = ElementFactory.createButton(Actions.ExportDialog_CopyToClipboard, false);
		copy.addActionListener(e -> Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(featArea.getText()), null));
		JPanel copyPanel = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT));
		copyPanel.add(copy);
		DialogSupport.add(grid, copyPanel, 0, 8, 3, true);
		DialogSupport.add(grid, warningLabel, 0, 9, 3, true);
		getContentPane().add(grid, BorderLayout.CENTER);

		addressField.getDocument().addDocumentListener(new DocumentListener() {
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
		});
		sfxBox.addActionListener(e -> changeParams());
		gvfBox.addActionListener(e -> changeParams());
		nosBox.addActionListener(e -> changeParams());
		formatCombo.addActionListener(e -> changeParams());
		changeParams();
	}

	AssemblerFormat getAssemblerFormat() {
		return formatCombo.getValue();
	}

	/** {@code strtoul(s, &end, 16)}: the leading hex digits, 0 for none. */
	static int parseHexPrefix(String s) {
		int value = 0;
		int i = 0;
		while (i < s.length() && Character.isWhitespace(s.charAt(i))) {
			i++;
		}
		for (; i < s.length(); i++) {
			int d = Character.digit(s.charAt(i), 16);
			if (d < 0) {
				break;
			}
			value = value * 16 + d;
			if (value > 0xFFFFF) {
				break; // more than any address; keeps the clamp below meaningful
			}
		}
		return value;
	}

	/** {@code ChangeParams()}: clamp the address, refresh the range/length info, the warning and the FEAT block. */
	void changeParams() {
		int adr = parseHexPrefix(addressField.getText());
		boolean sfx = sfxBox.isSelected();
		SongFiles.ModuleDescription desc = sfx ? withSfx : stripped;
		int len = desc.length();
		if (adr > 0x10000 - len) {
			adr = 0x10000 - len;
		}
		exportAddr = adr;
		infoLabel.setText(String.format("=>  $%04X - $%04X , length $%04X (%d bytes)", exportAddr, exportAddr + len - 1, len, len));
		warningLabel.setText("<html>Warning:<br>" + (sfx ? "This output file doesn't contain song name and names of all instruments." : "This output file doesn't contain any unused or empty tracks and instruments, song name and names of all instruments.") + "</html>");
		String feat = AsmFileExporter.composeRMTFEATstring(session.song, session.instruments, session.tracks, session.tracks4_8, filename, desc.instrumentSavedFlags(), sfx, gvfBox.isSelected(), nosBox.isSelected(), getAssemblerFormat());
		featArea.setText(feat);
		featArea.setCaretPosition(0);
	}

	/** {@code DoModal()}: the choice, or {@code null} if cancelled. */
	SongFiles.StrippedRmtChoice showDialog() {
		showModal(addressField);
		if (!okPressed) {
			return null;
		}
		changeParams();
		return new SongFiles.StrippedRmtChoice(exportAddr, getAssemblerFormat(), sfxBox.isSelected(), gvfBox.isSelected(), nosBox.isSelected());
	}
}
