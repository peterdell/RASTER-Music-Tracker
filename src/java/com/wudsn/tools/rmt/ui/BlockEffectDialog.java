package com.wudsn.tools.rmt.ui;

import java.awt.BorderLayout;
import java.util.Random;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

import com.wudsn.tools.base.gui.ElementFactory;
import com.wudsn.tools.base.gui.ModalDialog;
import com.wudsn.tools.rmt.model.BlockEffects;
import com.wudsn.tools.rmt.model.PlayMode;
import com.wudsn.tools.rmt.model.Track;

/**
 * "Effects/tools" - the port of {@code CEffectsDlg} ({@code IDD_EFFECTS}):
 * the effect combo, its three parameter prompts/fields (a field is disabled
 * when the effect has no such parameter), and the buttons Try (apply to the
 * live track, from the original each time), Restore (the original back),
 * Play/Stop (the block, so the effect can be auditioned - the
 * {@link AudioEngine} keeps running while the dialog is open), Default (the
 * effect's default parameters), OK (apply and keep) and
 * Cancel (restore). The chosen effect and the typed parameters are
 * remembered for the session in {@link BlockEffects.Settings}.
 */
final class BlockEffectDialog extends ModalDialog {

	private static final long serialVersionUID = 1L;

	private final RmtSession session;
	private final BlockEffects.Settings settings;
	private final Track track;
	private final Track original;
	private final int bfro;
	private final int bto;
	private final int ainstr;
	private final boolean all;
	private final Random random = new Random();

	private final JComboBox<String> effectCombo = new JComboBox<>();
	private final JLabel p1 = new JLabel("P1");
	private final JLabel p2 = new JLabel("P2");
	private final JLabel p3 = new JLabel("P3");
	private final JTextField edit1 = new JTextField(10);
	private final JTextField edit2 = new JTextField(10);
	private final JTextField edit3 = new JTextField(10);
	private int effai;

	BlockEffectDialog(JFrame parent, RmtSession session, Track track, Track original, int bfro, int bto, int ainstr, boolean all, String info) {
		super(parent, Texts.BlockEffectDialog_Title);
		this.session = session;
		this.settings = session.blockEffectSettings;
		this.track = track;
		this.original = original;
		this.bfro = bfro;
		this.bto = bto;
		this.ainstr = ainstr;
		this.all = all;

		for (BlockEffects.Effect effect : BlockEffects.EFFECTS) {
			effectCombo.addItem(effect.name());
		}
		JPanel grid = new JPanel(new java.awt.GridBagLayout());
		int row = 0;
		DialogSupport.add(grid, new JLabel(info), 0, row++, 1, true);
		DialogSupport.add(grid, effectCombo, 0, row++, 1, true);
		DialogSupport.add(grid, p1, 0, row++, 1, true);
		DialogSupport.add(grid, edit1, 0, row++, 1, false);
		DialogSupport.add(grid, p2, 0, row++, 1, true);
		DialogSupport.add(grid, edit2, 0, row++, 1, false);
		DialogSupport.add(grid, p3, 0, row++, 1, true);
		DialogSupport.add(grid, edit3, 0, row++, 1, false);
		JButton defaults = ElementFactory.createButton(Actions.BlockEffectDialog_Default, false);
		defaults.addActionListener(e -> onDefault());
		JPanel defaultsPanel = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT));
		defaultsPanel.add(defaults);
		DialogSupport.add(grid, defaultsPanel, 0, row++, 1, true);
		getContentPane().add(grid, BorderLayout.CENTER);

		JButton tryButton = ElementFactory.createButton(Actions.BlockEffectDialog_Try, true);
		tryButton.addActionListener(e -> performEffect());
		JButton restore = ElementFactory.createButton(Actions.BlockEffectDialog_Restore, true);
		restore.addActionListener(e -> onEffectRestore());
		JButton playStop = ElementFactory.createButton(Actions.BlockEffectDialog_PlayStop, true);
		playStop.addActionListener(e -> onSongStop());
		addButtonBarButton(playStop); // inserted at the front each time, so add in reverse order
		addButtonBarButton(restore);
		addButtonBarButton(tryButton);

		effai = settings.lastEffect;
		effectCombo.setSelectedIndex(effai);
		effectCombo.addActionListener(e -> onSelchangeEffCombo());
		onSelchangeEffCombo();
	}

	/** {@code OnSelchangeEffCombo()}: the prompts, the unused fields disabled, the remembered texts (or the defaults). */
	private void onSelchangeEffCombo() {
		effai = effectCombo.getSelectedIndex();
		BlockEffects.Effect effect = BlockEffects.EFFECTS[effai];
		p1.setText(effect.p1());
		p2.setText(effect.p2());
		edit2.setEnabled(!effect.p2().isEmpty());
		p3.setText(effect.p3());
		edit3.setEnabled(!effect.p3().isEmpty());
		if (settings.params[effai][0].isEmpty()) {
			onDefault(); // if the P1 parameter is empty, then set all defaults
		} else {
			edit1.setText(settings.params[effai][0]);
			edit2.setText(settings.params[effai][1]);
			edit3.setText(settings.params[effai][2]);
		}
	}

	/** {@code OnDefault()}. */
	private void onDefault() {
		BlockEffects.Effect effect = BlockEffects.EFFECTS[effai];
		edit1.setText(effect.e1());
		edit2.setText(effect.e2());
		edit3.setText(effect.e3());
		settings.params[effai][0] = effect.e1();
		settings.params[effai][1] = effect.e2();
		settings.params[effai][2] = effect.e3();
	}

	/** {@code PerformEffect()}: the fields into the remembered texts, the effect from the original into the live track. */
	void performEffect() {
		settings.params[effai][0] = edit1.getText();
		settings.params[effai][1] = edit2.getText();
		settings.params[effai][2] = edit3.getText();
		session.locked(() -> BlockEffects.perform(track, original, effai, bfro, bto, ainstr, all, edit1.getText(), edit2.getText(), edit3.getText(), session.tracks, random));
	}

	/** {@code OnEffectRestore()}. */
	private void onEffectRestore() {
		session.locked(() -> track.copyFrom(original));
	}

	/** {@code OnSongStop()}: Play/Stop toggles the block playback (audible: the dialog is modal but the sound thread keeps running). */
	private void onSongStop() {
		session.locked(() -> {
			if (session.song.getPlayMode() != PlayMode.PLAY_STOP) {
				session.song.stop(session.undo);
			} else {
				session.song.play(PlayMode.PLAY_BLOCK, session.song.getFollowPlayMode(), session.undo, session.tracks4_8, session.atariTrackerDriver, session.clipboard);
			}
		});
	}

	/** {@code DoModal()}: OK = {@code PerformEffect()} + remember the effect; anything else = {@code OnEffectRestore()}. */
	boolean showDialog() {
		showModal(effectCombo);
		if (okPressed) {
			performEffect();
			settings.lastEffect = effai;
		} else {
			onEffectRestore();
		}
		return okPressed;
	}
}
