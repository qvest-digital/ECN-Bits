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
import javax.swing.text.*;
import java.awt.*;
import javax.swing.border.Border;
import javax.swing.border.EtchedBorder;
import javax.swing.event.*;
import javax.swing.plaf.basic.BasicComboBoxRenderer;
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
private JComboBox<BitsAdapter> ecnBox;
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
	controlArea.add(new JSeparator(SwingConstants.VERTICAL) {
		/* ARRRRRRRRRGH! */
		@Override
		public Dimension getMaximumSize() {
			final Dimension d = new Dimension();
			d.setSize(getPreferredSize().getWidth(),
			    super.getMaximumSize().getHeight());
			return d;
		}
	});
	controlArea.add(Box.createRigidArea(new Dimension(5, 0)));

	ecnBox = new JComboBox<BitsAdapter>(BitsAdapter.values) {
		@Override
		public Dimension getMaximumSize() {
			return getPreferredSize();
		}
		@Override
		protected void paintComponent(final Graphics g) {
			super.paintComponent(drawAA(g));
		}
	};
	ecnBox.setRenderer(new BasicComboBoxRenderer() {
		@Override
		protected void paintComponent(final Graphics g) {
			super.paintComponent(drawAA(g));
		}
	});
	for (final BitsAdapter bit : BitsAdapter.values) {
		if (bit.getBit() == Bits.ECT0)
			ecnBox.setSelectedItem(bit);
	}
	ecnBox.addActionListener((e) -> {
		// we want this, it apparently was forgotten during Swing genericisation… ☹
		//final byte el = ecnBox.getSelectedItem().getBit().getBits();
		final byte el = BitsAdapter.values[ecnBox.getSelectedIndex()].getBit().getBits();
		final byte tc = retrieveTC();
		setTC((tc & 0xFC) | (el & 0x03));
	});
	ecnBox.setFont(monoFont);
	controlArea.add(ecnBox);

	controlArea.add(Box.createRigidArea(new Dimension(4, 0)));

	tcField = new JTextField("FF", 2 + /* offset Swing bugs */ 1) {
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
		@Override
		public void replaceSelection(final String content) {
			final AbstractDocument doc = (AbstractDocument)getDocument();
			/* overwrite by default */
			if (doc != null && getSelectionStart() == getSelectionEnd()) {
				final boolean composedTextSaved = saveComposedText(getCaretPosition());
				final int pos = getCaretPosition();
				final int inslen = content.length();
				final int txtlen = doc.getLength();
				final int dellen = Math.min(txtlen - pos, inslen);
				try {
					doc.replace(pos, dellen, content, null);
				} catch (BadLocationException e) {
					// shouldn’t happen
					UIManager.getLookAndFeel().provideErrorFeedback(this);
				}
				return;
			}
			super.replaceSelection(content);
		}

	};
	tcField.setToolTipText("Traffic Class octet to use when sending");
	tcField.setFont(monoFont);
	((AbstractDocument)tcField.getDocument()).setDocumentFilter(new HexDocumentFilter(2));
	final Border tcFieldBorderOK = tcField.getBorder();
	final Border tcFieldBorderERR = BorderFactory.createLineBorder(Color.red);
	tcField.getDocument().addDocumentListener((DocumentListenerLambda) (e) -> {
		final int len = e.getDocument().getLength();
		tcField.setBorder(len == 2 ? tcFieldBorderOK : tcFieldBorderERR);
	});
	controlArea.add(tcField);

	controlArea.add(Box.createRigidArea(new Dimension(8, 0)));

	sendBtn = new JButton("Send");
	sendBtn.setToolTipText("Send a single UDP packet with the chosen traffic class to the currently active IP");
	sendBtn.addActionListener((e) -> { outArea.setText("boo!"); });
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

	outArea = new JTextArea("packets received will be shown here") {
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

private byte retrieveTC() {
	final String t = tcField.getText();
	final byte v;
	if (t.length() == 2) {
		v = (byte)Integer.parseInt(t, 16);
	} else {
		setTC(0);
		v = (byte)0x00;
	}
	return v;
}

private void setTC(final int tc) {
	final String by = String.format("%02X", tc & 0xFF);

	if (!by.equals(tcField.getText())) {
		final int pos = tcField.getCaretPosition();
		tcField.setText(by);
		tcField.setCaretPosition(pos);
	}
}

static class HexDocumentFilter extends DocumentFilter {
	private final int sz;

	HexDocumentFilter(final int maxlen) {
		super();
		sz = maxlen;
	}

	@Override
	public void insertString(final DocumentFilter.FilterBypass fb,
	    final int offset, final String string, final AttributeSet attr)
	throws BadLocationException {
		replace(fb, offset, 0, string, attr);
	}

	@Override
	public void replace(final DocumentFilter.FilterBypass fb,
	    final int offset, final int length, final String text,
	    final AttributeSet attrs) throws BadLocationException {
		int oldlen = fb.getDocument().getLength();
		if (oldlen > sz) {
			super.remove(fb, sz, oldlen - sz);
			oldlen = fb.getDocument().getLength();
		}
		if (offset < 0 || length < 0)
			throw new BadLocationException("negative len/ofs",
			    Math.min(offset, length));
		if ((offset + length) > oldlen)
			throw new BadLocationException("outside of document",
			    offset + length);
		final int oldbef = offset;
		final int oldaft = oldlen - (offset + length);
		int totlen = oldbef + text.length() + oldaft;
		final String ins = (totlen > sz) ? text.substring(0,
		    text.length() - (totlen - sz)) : text;
		if (allowed(ins))
			super.replace(fb, offset, length, ins, attrs);
	}

	static boolean allowed(final String s) {
		for (final char c : s.toCharArray())
			if (!(((c >= '0') && (c <= '9')) ||
			      ((c >= 'A') && (c <= 'F')) ||
			      ((c >= 'a') && (c <= 'f'))))
				return false;
		return true;
	}
}

@FunctionalInterface
public interface DocumentListenerLambda extends DocumentListener {
	void hook(final DocumentEvent e);

	@Override
	default void insertUpdate(final DocumentEvent e) {
		hook(e);
	}

	@Override
	default void removeUpdate(final DocumentEvent e) {
		hook(e);
	}

	@Override
	default void changedUpdate(final DocumentEvent e) {
		hook(e);
	}
}




    private static class BitsAdapter {
        static final BitsAdapter[] values;

        static {
            /*val*/final Bits[] bits = Bits.values();
            values = new BitsAdapter[bits.length];
            for (int i = 0; i < bits.length; ++i) {
                values[i] = new BitsAdapter(bits[i]);
            }
        }

        private final Bits bit;

        @Override
        public String toString() {
            return bit.getShortname();
        }

	Bits getBit() { return bit; }
	BitsAdapter(final Bits thisBit) { bit = thisBit; }
    }


private static enum Bits {
    NO(0, "no ECN", "nōn-ECN-capable transport"),
    ECT0(2, "ECT(0)", "ECN-capable; L4S: legacy transport"),
    ECT1(1, "ECT(1)", "ECN-capable; L4S: L4S-aware transport"),
    CE(3, "ECN CE", "congestion experienced");

    /**
     * Short name corresponding to “ECN bits unknown”, cf. {@link #getShortname()}
     */
    public static final String UNKNOWN = "??ECN?";

    private final byte bits;
    private final String shortname;
    private final String meaning;

    Bits(final int b, final String s, final String m) {
        bits = (byte) b;
        shortname = s;
        meaning = m;
    }

    /**
     * Returns the bits corresponding to this ECN flag
     *
     * @return bits suitable for use in IP traffic class
     */
    public byte getBits() {
        return bits;
    }

    /**
     * Returns the short name of this ECN flag, cf. {@link #UNKNOWN}
     *
     * @return String comprised of 6 ASCII characters
     */
    public String getShortname() {
        return shortname;
    }

    /**
     * Returns a long English description of this ECN flag
     *
     * @return Unicode String describing this flag in English
     */
    @SuppressWarnings({ "unused", /* UnIntelliJ bug */ "RedundantSuppression" })
    public String getMeaning() {
        return meaning;
    }

    /**
     * Returns the enum value for the supplied traffic class’ lowest two bits
     *
     * @param tc traffic class octet
     * @return matching {@link Bits}
     */
    public static Bits valueOf(final byte tc) {
        final byte bits = (byte) (tc & 0x03);

        for (final Bits bit : values()) {
            if (bit.bits == bits) {
                return bit;
            }
        }
        /* NOTREACHED */
        throw new NullPointerException("unreachable code");
    }

    /**
     * Returns the short description of the ECN bits for the supplied
     * traffic class, or the {@link #UNKNOWN} description if it is null.
     *
     * @param tc traffic class octet
     * @return String comprised of 6 ASCII characters
     * @see #getShortname()
     */
    public static String print(final Byte tc) {
        if (tc == null) {
            return UNKNOWN;
        }
        return valueOf(tc).getShortname();
    }
}
}
