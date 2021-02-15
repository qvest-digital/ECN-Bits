package de.telekom.llcto.ecn_bits.jdk.jni;

/*-
 * Copyright © 2021
 *      mirabilos <t.glaser@tarent.de>
 * Licensor: Deutsche Telekom
 *
 * Provided that these terms and disclaimer and all copyright notices
 * are retained or reproduced in an accompanying document, permission
 * is granted to deal in this work without restriction, including un‐
 * limited rights to use, publicly perform, distribute, sell, modify,
 * merge, give away, or sublicence.
 *
 * This work is provided “AS IS” and WITHOUT WARRANTY of any kind, to
 * the utmost extent permitted by applicable law, neither express nor
 * implied; without malicious intent or gross negligence. In no event
 * may a licensor, author or contributor be held liable for indirect,
 * direct, other damage, loss, or other issues arising in any way out
 * of dealing in the work, even if advised of the possibility of such
 * damage or existence of a defect, except proven that it results out
 * of said person’s immediate fault when using the work as intended.
 */

import java.io.*;
import java.util.*;
import javax.swing.*;
import java.awt.*;
import javax.swing.border.EtchedBorder;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;

public final class ChannelMain {

private static ChannelMain INSTANCE;

public static void main(String[] argv) {
	INSTANCE = new ChannelMain(argv);
	SwingUtilities.invokeLater(() -> { INSTANCE.run(); });
}

private ChannelMain(String[] argv) {
}

private Font monoFont;
private JFrame frame;
private JPanel contentPane;
private JTextArea outArea;
// dropdown
private JTextField tcField;
private JButton sendBtn;
private JButton quitBtn;
private JButton prevBtn;
private JLabel hostLabel;
private JLabel tgtLabel;
private JButton nextBtn;

private void init() {
	final File fontFile = new File("Inconsolatazi4varlquAH.ttf");
	try (final InputStream is = new FileInputStream(fontFile)) {
		final Font fileFont = Font.createFont(Font.TRUETYPE_FONT, is);
		monoFont = fileFont.deriveFont((float)16);
	} catch (Exception e) {
		System.out.println("could not load font: " + e);
		monoFont = new Font(Font.MONOSPACED, Font.PLAIN, 16);
	}

	System.setProperty("awt.useSystemAAFontSettings", "on");
	System.setProperty("prism.lcdtext", "false");
	System.setProperty("swing.aatext", "true");

	try {
		UIManager.setLookAndFeel("com.sun.java.swing.plaf.motif.MotifLookAndFeel");
		return;
	} catch (Exception e) {
		System.out.println("could not set motif theme");
	}//*/
	try {
		UIManager.setLookAndFeel("javax.swing.plaf.metal.MetalLookAndFeel");
		return;
	} catch (Exception e) {
		System.out.println("could not set metal theme");
	}
	// ok, default theme then
}

private void bindKey(final Action action, final int where, final KeyStroke key) {
	final JRootPane rootPane = frame.getRootPane();
	final Object name = action.getValue(Action.NAME);

	rootPane.getInputMap(where).put(key, name);
	rootPane.getActionMap().put(name, action);
}

private Graphics drawAA(final Graphics g) {
	if (g instanceof Graphics2D) {
		final Graphics2D g2d = (Graphics2D)g;
		g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g2d.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
		g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
	} else
		System.err.println("[WARNING] cannot draw antialiased");
	return g;
}

private void run() {
	init();

	frame = new JFrame("ECN-Bits Channel Example");
	frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
	frame.setSize(720, 600);
	contentPane = new JPanel(new BorderLayout());

	JPanel controlArea = new JPanel();
	controlArea.setLayout(new BoxLayout(controlArea, BoxLayout.X_AXIS));

	prevBtn = new JButton("Prev");
	prevBtn.setToolTipText("Switch to previous IP for this hostname");
	prevBtn.addActionListener((e) -> { tgtLabel.setText("prev ip"); });
	controlArea.add(prevBtn);

	controlArea.add(Box.createRigidArea(new Dimension(5, 0)));
	JPanel hostnameArea = new JPanel();
	hostnameArea.setLayout(new BoxLayout(hostnameArea, BoxLayout.Y_AXIS));
	hostLabel = new JLabel("hostname:port");
	hostLabel.setToolTipText("Selected hostname (target) and port");
	hostLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
	hostnameArea.add(hostLabel);
	tgtLabel = new JLabel("target IP");
	tgtLabel.setToolTipText("Currently selected IP address of the target hostname");
	tgtLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
	tgtLabel.setBorder(BorderFactory.createCompoundBorder(
	    BorderFactory.createEtchedBorder(EtchedBorder.LOWERED),
	    BorderFactory.createEmptyBorder(2, 2, 2, 2)));
	hostnameArea.add(tgtLabel);
	controlArea.add(hostnameArea);
	controlArea.add(Box.createRigidArea(new Dimension(5, 0)));

	nextBtn = new JButton("Next");
	nextBtn.setToolTipText("Switch to next IP for this hostname");
	nextBtn.addActionListener((e) -> { tgtLabel.setText("next ip"); });
	controlArea.add(nextBtn);

	controlArea.add(Box.createRigidArea(new Dimension(5, 0)));
	controlArea.add(new JSeparator(SwingConstants.VERTICAL));
	controlArea.add(Box.createRigidArea(new Dimension(5, 0)));

	//actionarea.add( …dropdown…

	tcField = new JTextField("02", 2 + /* offset Swing bugs */ 1) {
		@Override
		public Dimension getMinimumSize() {
			return getPreferredSize();
		}
		@Override
		public Dimension getMaximumSize() {
			return getPreferredSize();
		}
		@Override
		protected void paintComponent(final Graphics g) {
			super.paintComponent(drawAA(g));
		}
	};
	tcField.setToolTipText("Traffic Class octet to use when sending");
	tcField.setFont(monoFont);
	controlArea.add(tcField);

	sendBtn = new JButton("Send");
	sendBtn.setToolTipText("Send a single UDP packet with the chosen traffic class to the currently active IP");
	controlArea.add(sendBtn);

	controlArea.add(Box.createHorizontalGlue());

	final Action quitBtnAction = new AbstractAction("Quit") {
		@Override
		public void actionPerformed(final ActionEvent e) {
			System.exit(0);
		}
	};
	quitBtnAction.putValue(/* toolTipText */ Action.SHORT_DESCRIPTION,
	    "Exit this program");
	quitBtnAction.putValue(/* mnemonic */ Action.MNEMONIC_KEY,
	    KeyEvent.VK_Q);
	quitBtn = new JButton(quitBtnAction);
	controlArea.add(quitBtn);
	bindKey(quitBtnAction, JComponent.WHEN_IN_FOCUSED_WINDOW,
	    KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0));

	contentPane.add(BorderLayout.NORTH, controlArea);

	outArea = new JTextArea("0 packets received will be shown here") {
		@Override
		protected void paintComponent(final Graphics g) {
			super.paintComponent(drawAA(g));
		}
	};
	outArea.setToolTipText("Received UDP packets are shown here");
	outArea.setBorder(BorderFactory.createCompoundBorder(
	    BorderFactory.createEmptyBorder(6, 6, 6, 6),
	    BorderFactory.createCompoundBorder(
	    BorderFactory.createEtchedBorder(EtchedBorder.RAISED),
	    BorderFactory.createEmptyBorder(3, 3, 3, 3))));
	outArea.setEditable(false);
	outArea.setFont(monoFont);
	contentPane.add(BorderLayout.CENTER, new JScrollPane(outArea));

	frame.getRootPane().setDefaultButton(sendBtn);
	frame.setContentPane(contentPane);
	frame.setVisible(true);
}

}
