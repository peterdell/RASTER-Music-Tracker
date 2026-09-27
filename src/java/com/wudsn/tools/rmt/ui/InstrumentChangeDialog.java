package com.wudsn.tools.rmt.ui;

import java.awt.BorderLayout;

import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingConstants;

import com.wudsn.tools.base.gui.ElementFactory;
import com.wudsn.tools.base.gui.ModalDialog;
import com.wudsn.tools.rmt.model.Instruments;
import com.wudsn.tools.rmt.model.Notes;
import com.wudsn.tools.rmt.model.Song;
import com.wudsn.tools.rmt.model.Tracks;

/**
 * "Change all the instrument occurences" - the port of
 * {@code CInstrumentChangeDlg} ({@code IDD_INSTRCHANGE}): the "Condition"
 * and "If true, change to" groups of six combo boxes each (instrument
 * from/to, note from/to, volume min/max), the coupling boxes ("One
 * instrument only", "The same instrument/note/volume range"), the scope
 * boxes (current track / some channels / song lines), and the "Default
 * ranges" / "All instruments" buttons. The combo indices are the model's
 * values directly ({@code DDX_CBIndex}); the extra last entry "---" of the
 * "to" combos is index {@code NOTESNUM}/16/{@code INSTRSNUM}.
 * {@code SelChangeComboX()}'s range coupling runs on every change.
 */
final class InstrumentChangeDialog extends ModalDialog {

	private static final long serialVersionUID = 1L;

	private final RmtSession session;
	private final int onlyTrack;

	// the 12 combos in C++'s IDC_COMBO1..12 order: 1-4 condition note/volume, 5-8 change-to note/volume, 9-10 change-to instr, 11-12 condition instr
	private final JComboBox<String> combo1 = notes(false);
	private final JComboBox<String> combo2 = notes(false);
	private final JComboBox<String> combo3 = volumes(false);
	private final JComboBox<String> combo4 = volumes(false);
	private final JComboBox<String> combo5 = notes(false);
	private final JComboBox<String> combo6 = notes(true);
	private final JComboBox<String> combo7 = volumes(false);
	private final JComboBox<String> combo8 = volumes(true);
	private final JComboBox<String> combo9 = instruments(false);
	private final JComboBox<String> combo10 = instruments(true);
	private final JComboBox<String> combo11 = instruments(false);
	private final JComboBox<String> combo12 = instruments(false);
	private final JCheckBox checkOneInstr = new JCheckBox("One instrument only", true);
	private final JCheckBox check4 = new JCheckBox("Only in current track");
	private final JCheckBox check5 = new JCheckBox("Only in some channels");
	private final JCheckBox check6 = new JCheckBox("Only in songlines");
	private final JCheckBox check3 = new JCheckBox("The same instrument range", true);
	private final JCheckBox check1 = new JCheckBox("The same note range", true);
	private final JCheckBox check2 = new JCheckBox("The same volume range", true);
	private final JTextField edit1 = new JTextField(3);
	private final JTextField edit2 = new JTextField(3);
	private final JLabel title = new JLabel("Title...");
	private final JLabel title2 = new JLabel("Title2...");
	private int onlyChannels = -1;
	private boolean coupling;

	InstrumentChangeDialog(JFrame parent, RmtSession session, int instr, int onlyTrack, int onlySongLine) {
		super(parent, Texts.InstrumentChangeDialog_Title);
		this.session = session;
		this.onlyTrack = onlyTrack;

		if (onlyTrack >= 0) {
			check4.setText(String.format("Only in current track ($%02X)", onlyTrack));
		} else {
			check4.setEnabled(false);
		}
		edit1.setText(String.format("%02X", onlySongLine));
		edit2.setText(String.format("%02X", onlySongLine));
		edit1.setEnabled(false);
		edit2.setEnabled(false);
		combo11.setSelectedIndex(instr);
		combo12.setSelectedIndex(instr);

		JPanel condition = DialogSupport.createGroup("Condition");
		addRow(condition, 0, "From instr", combo11);
		addRow(condition, 1, "To instr", combo12);
		addRow(condition, 2, "From note", combo1);
		addRow(condition, 3, "To note", combo2);
		addRow(condition, 4, "Min volume", combo3);
		addRow(condition, 5, "Max volume", combo4);
		JPanel change = DialogSupport.createGroup("If true, change to");
		addRow(change, 0, "From instr", combo9);
		addRow(change, 1, "To instr", combo10);
		addRow(change, 2, "From note", combo5);
		addRow(change, 3, "To note", combo6);
		addRow(change, 4, "Min volume", combo7);
		addRow(change, 5, "Max volume", combo8);

		JPanel grid = new JPanel(new java.awt.GridBagLayout());
		int row = 0;
		DialogSupport.add(grid, title, 0, row++, 2, true);
		DialogSupport.add(grid, title2, 0, row++, 2, true);
		DialogSupport.add(grid, condition, 0, row, 1, true);
		DialogSupport.add(grid, change, 1, row++, 1, true);
		DialogSupport.add(grid, checkOneInstr, 0, row, 1, false);
		DialogSupport.add(grid, check3, 1, row++, 1, false);
		DialogSupport.add(grid, check4, 0, row, 1, false);
		DialogSupport.add(grid, check1, 1, row++, 1, false);
		DialogSupport.add(grid, check5, 0, row, 1, false);
		DialogSupport.add(grid, check2, 1, row++, 1, false);
		DialogSupport.add(grid, check6, 0, row++, 1, false);
		JPanel lines = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 4, 0));
		lines.add(new JLabel("      from $"));
		lines.add(edit1);
		lines.add(new JLabel("to $"));
		lines.add(edit2);
		DialogSupport.add(grid, lines, 0, row++, 2, false);
		getContentPane().add(grid, BorderLayout.CENTER);

		javax.swing.JButton defaults = ElementFactory.createButton(Actions.InstrumentChangeDialog_DefaultRanges, false);
		defaults.addActionListener(e -> onDefault());
		javax.swing.JButton all = ElementFactory.createButton(Actions.InstrumentChangeDialog_AllInstruments, false);
		all.addActionListener(e -> onFullRanges());
		addButtonBarButton(all);
		addButtonBarButton(defaults);

		@SuppressWarnings("unchecked")
		JComboBox<String>[] coupled = new JComboBox[] { combo1, combo2, combo3, combo4, combo5, combo6, combo7, combo8, combo9, combo10 };
		for (JComboBox<String> combo : coupled) {
			combo.addActionListener(e -> selChangeComboX());
		}
		combo11.addActionListener(e -> onDefault());
		combo12.addActionListener(e -> onDefault());
		check1.addActionListener(e -> selChangeComboX());
		check2.addActionListener(e -> selChangeComboX());
		check3.addActionListener(e -> selChangeComboX());
		checkOneInstr.addActionListener(e -> onCheckOneInstrument());
		check4.addActionListener(e -> onCheckTrackOnly());
		check5.addActionListener(e -> onCheckSomeChannelsOnly());
		check6.addActionListener(e -> onCheckSomeSonglinesOnly());

		onDefault();
	}

	private static void addRow(JPanel group, int row, String label, JComboBox<String> combo) {
		DialogSupport.add(group, new JLabel(label, SwingConstants.RIGHT), 0, row, 1, true);
		DialogSupport.add(group, combo, 1, row, 1, false);
	}

	private static JComboBox<String> notes(boolean withNone) {
		JComboBox<String> combo = new JComboBox<>();
		for (int i = 0; i < Notes.NOTESNUM; i++) {
			combo.addItem(Notes.getNote(i));
		}
		if (withNone) {
			combo.addItem("---");
		}
		return combo;
	}

	private static JComboBox<String> volumes(boolean withNone) {
		JComboBox<String> combo = new JComboBox<>();
		for (int i = 0; i <= 15; i++) {
			combo.addItem(String.format("%X", i));
		}
		if (withNone) {
			combo.addItem("---");
		}
		return combo;
	}

	private static JComboBox<String> instruments(boolean withNone) {
		JComboBox<String> combo = new JComboBox<>();
		for (int i = 0; i < Instruments.INSTRSNUM; i++) {
			combo.addItem(String.format("%02X", i));
		}
		if (withNone) {
			combo.addItem("---");
		}
		return combo;
	}

	private void select(JComboBox<String> combo, int index) {
		if (combo.getSelectedIndex() != index) {
			combo.setSelectedIndex(index);
		}
	}

	/** {@code OnDefault()}: the ranges the chosen instrument(s) actually use, the titles, all coupling on. */
	void onDefault() {
		if (coupling) {
			return;
		}
		coupling = true;
		try {
			int instrfrom = combo11.getSelectedIndex();
			int instrto = combo12.getSelectedIndex();
			if (checkOneInstr.isSelected() || instrto < instrfrom) {
				instrto = instrfrom;
			}
			Song.InstrInfo iinfo = new Song.InstrInfo();
			session.song.instrInfo(iinfo, instrfrom, instrto);
			int count = iinfo.count;
			if (count == 0) {
				iinfo.minNote = 0;
				iinfo.maxNote = Notes.NOTESNUM - 1;
				iinfo.minVol = 0;
				iinfo.maxVol = Tracks.MAXVOLUME;
			}
			if (instrfrom == instrto) {
				title.setText(String.format("Instrument: %02X      Name: %s", instrfrom, Song.nameToString(session.instruments.getName(instrfrom))));
			} else {
				title.setText(String.format("Instruments %02X-%02X (%d)", instrfrom, instrto, instrto - instrfrom + 1));
			}
			title2.setText(String.format("Used in %d tracks, globally %d times.", iinfo.usedInTracks, count));

			check1.setSelected(true);
			check2.setSelected(true);
			check3.setSelected(true);

			select(combo1, iinfo.minNote);
			select(combo2, iinfo.maxNote);
			select(combo3, iinfo.minVol);
			select(combo4, iinfo.maxVol);
			select(combo5, iinfo.minNote);
			select(combo6, iinfo.maxNote);
			select(combo7, iinfo.minVol);
			select(combo8, iinfo.maxVol);
			select(combo9, instrfrom);
			select(combo10, instrto);
			select(combo11, instrfrom);
			select(combo12, instrto);
		} finally {
			coupling = false;
		}
		selChangeComboX();
	}

	/** {@code OnFullRanges()} ("All instruments"): the whole used instrument range, or everything if nothing is used. */
	void onFullRanges() {
		coupling = true;
		try {
			check1.setSelected(true);
			check2.setSelected(true);
			check3.setSelected(true);
			checkOneInstr.setSelected(false);
			Song.InstrInfo iinfo = new Song.InstrInfo();
			session.song.instrInfo(iinfo, 0, Instruments.INSTRSNUM - 1);
			if (iinfo.count == 0) {
				select(combo1, 0);
				select(combo2, Notes.NOTESNUM - 1);
				select(combo3, 0);
				select(combo4, 15);
				select(combo5, 0);
				select(combo6, Notes.NOTESNUM - 1);
				select(combo7, 0);
				select(combo8, 15);
				select(combo11, 0);
				select(combo12, Instruments.INSTRSNUM - 1);
				select(combo9, 0);
				select(combo10, Instruments.INSTRSNUM - 1);
			} else {
				select(combo11, iinfo.instrFrom);
				select(combo12, iinfo.instrTo);
			}
		} finally {
			coupling = false;
		}
		onDefault();
	}

	/** {@code SelChangeComboX()}: "to" never below "from", the coupled ranges follow the condition, the coupled combos disabled. */
	void selChangeComboX() {
		if (coupling) {
			return;
		}
		coupling = true;
		try {
			@SuppressWarnings("unchecked")
			JComboBox<String>[] combos = new JComboBox[] { combo1, combo2, combo3, combo4, combo5, combo6, combo7, combo8, combo9, combo10, combo11, combo12 };
			int[] c = new int[12];
			for (int i = 0; i < 12; i++) {
				c[i] = combos[i].getSelectedIndex();
			}
			if (c[1] < c[0]) {
				c[1] = c[0]; // noteto<notefrom
			}
			if (c[3] < c[2]) {
				c[3] = c[2]; // volumemax<volumemin
			}
			if (c[5] < c[4]) {
				c[5] = c[4];
			}
			if (c[7] < c[6]) {
				c[7] = c[6];
			}
			if (c[9] < c[8]) {
				c[9] = c[8]; // instrto<instrfrom
			}
			if (c[11] < c[10]) {
				c[11] = c[10];
			}
			if (check1.isSelected()) { // same note range
				c[5] = Math.min(c[1] - c[0] + c[4], Notes.NOTESNUM); // an "---" item is added at the end
			}
			if (check2.isSelected()) { // same volume range
				c[7] = Math.min(c[3] - c[2] + c[6], 16); // 16th item "---" is added at the end of 0-15
			}
			if (check3.isSelected() || checkOneInstr.isSelected()) { // same instrument range || only one instrument
				if (checkOneInstr.isSelected()) {
					c[11] = c[10];
				}
				c[9] = Math.min(c[11] - c[10] + c[8], Instruments.INSTRSNUM);
			}
			combo6.setEnabled(!check1.isSelected());
			combo8.setEnabled(!check2.isSelected());
			combo10.setEnabled(!check3.isSelected() && !checkOneInstr.isSelected());
			boolean ch = checkOneInstr.isSelected();
			combo12.setEnabled(!ch);
			check3.setEnabled(!ch);
			for (int i = 0; i < 12; i++) {
				select(combos[i], c[i]);
			}
		} finally {
			coupling = false;
		}
	}

	/** {@code OnCheckoneinstrument()}. */
	private void onCheckOneInstrument() {
		if (checkOneInstr.isSelected()) {
			onDefault();
		} else {
			selChangeComboX();
		}
	}

	/** {@code OnCheckTrackOnly()}: mutually exclusive with the channel and song line scopes. */
	private void onCheckTrackOnly() {
		if (check4.isSelected()) {
			check5.setSelected(false);
			check5.setText("Only in some channels");
			onlyChannels = -1; // all
			check6.setSelected(false);
			edit1.setEnabled(false);
			edit2.setEnabled(false);
		}
	}

	/** {@code OnCheckSomeChannelsOnly()}: opens the channel selection, labels the box with the chosen channels. */
	private void onCheckSomeChannelsOnly() {
		if (check5.isSelected()) {
			check4.setSelected(false);
			int channelyes = new ChannelsSelectionDialog((JFrame) getOwner(), session.tracks4_8).showDialog();
			if (channelyes > 0) {
				onlyChannels = channelyes;
				StringBuilder s = new StringBuilder("Only in ");
				int j = 0;
				for (int i = 0; i < session.tracks4_8; i++) {
					if ((onlyChannels & (1 << i)) != 0) {
						if (j > 0) {
							s.append(',');
						}
						s.append(ChannelsSelectionDialog.NAMES[i]);
						j++;
					}
				}
				check5.setText(s.toString());
			} else {
				check5.setSelected(false);
			}
		}
		if (!check5.isSelected()) {
			check5.setText("Only in some channels");
			onlyChannels = -1; // all
		}
	}

	/** {@code OnCheckSomeSonglinesOnly()}. */
	private void onCheckSomeSonglinesOnly() {
		boolean check = check6.isSelected();
		if (check) {
			check4.setSelected(false);
		}
		edit1.setEnabled(check);
		edit2.setEnabled(check);
	}

	/** {@code OnOK()} + the {@code DDX_CBIndex} transfer into {@code TInstrChangeParams}. */
	Song.InstrChangeParams getParams() {
		Song.InstrChangeParams p = new Song.InstrChangeParams();
		p.snotefrom = combo1.getSelectedIndex();
		p.snoteto = combo2.getSelectedIndex();
		p.svolmin = combo3.getSelectedIndex();
		p.svolmax = combo4.getSelectedIndex();
		p.sinstrfrom = combo11.getSelectedIndex();
		p.sinstrto = combo12.getSelectedIndex();
		p.dnotefrom = combo5.getSelectedIndex();
		p.dnoteto = combo6.getSelectedIndex();
		p.dvolmin = combo7.getSelectedIndex();
		p.dvolmax = combo8.getSelectedIndex();
		p.dinstrfrom = combo9.getSelectedIndex();
		p.dinstrto = combo10.getSelectedIndex();
		p.onlytrack = check4.isSelected() ? onlyTrack : -1;
		p.onlychannels = check5.isSelected() ? onlyChannels : -1;
		if (check6.isSelected()) {
			p.onlysonglinefrom = Song.hexstr(edit1.getText().trim(), 0, 4);
			p.onlysonglineto = Song.hexstr(edit2.getText().trim(), 0, 4);
		} else {
			p.onlysonglinefrom = -1;
			p.onlysonglineto = -1;
		}
		return p;
	}

	/** {@code DoModal()}: the parameters, or {@code null} if cancelled. */
	Song.InstrChangeParams showDialog() {
		showModal(combo11);
		return okPressed ? getParams() : null;
	}
}
