package org.atari.raster.rmt.ui;

import java.awt.BorderLayout;
import java.awt.Cursor;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.io.InputStream;

import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;

import com.wudsn.tools.base.Actions;
import com.wudsn.tools.base.gui.Desktop;
import com.wudsn.tools.base.gui.ElementFactory;

import net.sf.asap.ASAPInfo;

/**
 * "About RASTER Music Tracker" - the port of {@code CAboutDialog}
 * ({@code IDD_ABOUT}): icon, version, author, the clickable repository
 * link, and the two read-only boxes crediting the POKEY and 6502
 * emulation. C++ fills those from the Altirra DLLs' {@code GetAbout()}
 * texts; the Java port's emulation is ASAP's, so its version and credits
 * stand there (the plan's B7 decision). Built like dis6502's
 * {@code AboutDialog}: a plain modal {@link JDialog} with one OK button.
 */
final class AboutDialog extends JDialog {

	private static final long serialVersionUID = 1L;

	/** What the C++ Altirra DLLs report through {@code GetAbout()}, for ASAP. */
	static String asapAbout(String component) {
		String firstCreditLine = ASAPInfo.CREDITS.split("\n")[0];
		return "ASAP " + ASAPInfo.VERSION + " " + component + " (net.sf.asap)\n" + firstCreditLine + "\nhttps://asap.sourceforge.net/";
	}

	AboutDialog(Frame owner) {
		super(owner, Texts.AboutDialog_Title, true);
		setDefaultCloseOperation(DISPOSE_ON_CLOSE);

		JPanel content = new JPanel(new GridBagLayout());
		content.setBorder(javax.swing.BorderFactory.createEmptyBorder(8, 8, 8, 8));
		GridBagConstraints c = new GridBagConstraints();
		c.insets = new Insets(2, 4, 2, 4);
		c.anchor = GridBagConstraints.WEST;
		c.fill = GridBagConstraints.HORIZONTAL;

		JLabel icon = new JLabel();
		try (InputStream in = RmtMainWindow.class.getResourceAsStream("application.png")) {
			if (in != null) {
				icon.setIcon(new ImageIcon(ImageIO.read(in).getScaledInstance(32, 32, java.awt.Image.SCALE_SMOOTH)));
			}
		} catch (IOException ignored) {
			// no icon then
		}
		c.gridx = 0;
		c.gridy = 0;
		c.gridheight = 3;
		c.anchor = GridBagConstraints.NORTHWEST;
		c.fill = GridBagConstraints.NONE;
		content.add(icon, c);
		c.gridheight = 1;
		c.gridx = 1;
		c.weightx = 1;
		c.fill = GridBagConstraints.HORIZONTAL;
		content.add(new JLabel(RmtMainWindow.VERSION_AND_BUILD), c);
		c.gridy = 1;
		content.add(new JLabel(Texts.AboutDialog_Author), c);
		c.gridy = 2;
		content.add(createLinkLabel(Texts.AboutDialog_Repository), c);

		c.gridx = 0;
		c.gridwidth = 2;
		c.gridy = 3;
		content.add(new JLabel(Texts.AboutDialog_PokeyEmulation), c);
		c.gridy = 4;
		content.add(createTextBox(asapAbout("POKEY emulation")), c);
		c.gridy = 5;
		content.add(new JLabel(Texts.AboutDialog_Cpu6502Emulation), c);
		c.gridy = 6;
		content.add(createTextBox(asapAbout("6502 emulation")), c);

		JButton okButton = ElementFactory.createButton(Actions.ButtonBar_OK, false);
		okButton.addActionListener(e -> setVisible(false));
		JPanel buttonPanel = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT));
		buttonPanel.add(okButton);

		getContentPane().setLayout(new BorderLayout());
		getContentPane().add(content, BorderLayout.CENTER);
		getContentPane().add(buttonPanel, BorderLayout.SOUTH);
		getRootPane().setDefaultButton(okButton);
		getRootPane().registerKeyboardAction(e -> setVisible(false), KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ESCAPE, 0), javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW);

		pack();
		setResizable(false);
		setLocationRelativeTo(owner);
	}

	/** {@code EDITTEXT ... ES_MULTILINE | ES_READONLY}. */
	private static JScrollPane createTextBox(String text) {
		JTextArea area = new JTextArea(text, 3, 60);
		area.setEditable(false);
		return new JScrollPane(area);
	}

	/** {@code ON_STN_CLICKED(IDC_RMT_REPOSITORY)} -> {@code CShell::OpenFile(url)}. */
	private static JLabel createLinkLabel(String url) {
		JLabel label = new JLabel("<html><a href=\"" + url + "\">" + url + "</a></html>");
		label.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		label.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				Desktop.openBrowser(url);
			}
		});
		return label;
	}
}
