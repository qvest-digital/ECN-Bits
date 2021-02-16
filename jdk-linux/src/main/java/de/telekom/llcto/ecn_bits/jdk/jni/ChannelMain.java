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

import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.val;
import org.evolvis.tartools.mvnparent.InitialiseLogging;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.EtchedBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.plaf.basic.BasicComboBoxRenderer;
import javax.swing.plaf.metal.DefaultMetalTheme;
import javax.swing.plaf.metal.MetalLookAndFeel;
import javax.swing.plaf.metal.OceanTheme;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.io.InputStream;
import java.nio.channels.DatagramChannel;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Example ECN-Bits client program for {@link DatagramChannel} replacement
 *
 * @author mirabilos (t.glaser@tarent.de)
 */
public final class ChannelMain {
    private static ChannelMain INSTANCE;
    // note this MUST NOT be replaced by @Log in this class ONLY
    private static final Logger LOG;

    /* initialise logging subsystem (must be done before creating a LOGGER) */
    static {
        InitialiseLogging.InitialiseJDK14Logging();
        LOG = Logger.getLogger(ChannelMain.class.getName());
    }

    public static void main(final String[] argv) {
        INSTANCE = new ChannelMain(argv);
        SwingUtilities.invokeLater(() -> INSTANCE.run());
    }

    private ChannelMain(final String[] argv) {
        int argc = argv.length;
        int argp = 0;

        SwingTheme.themesToTry = new SwingTheme[] {
          new SwingTheme("User", ""),
          new SwingTheme("Motif", "com.sun.java.swing.plaf.motif.MotifLookAndFeel"),
          new SwingTheme("Metal", "javax.swing.plaf.metal.MetalLookAndFeel")
        };
        SwingTheme.themesToTry[0].disabled = true;
        SwingTheme.themesToTry[2].hook = () -> MetalLookAndFeel.setCurrentTheme(new DefaultMetalTheme());

        while (argc > 0 && argv[argp].startsWith("--")) {
            final int optpos = argv[argp].indexOf('=');
            final String optstr = optpos == -1 ? argv[argp].substring(2) : argv[argp].substring(2, optpos);
            final String optarg = optpos == -1 ? null : argv[argp].substring(optpos + 1);
            if ("metal".equals(optstr)) {
                SwingTheme.themesToTry[0].disabled = true;
                SwingTheme.themesToTry[1].disabled = true;
                SwingTheme.themesToTry[2].disabled = false;
                if ("ocean".equals(optarg)) {
                    SwingTheme.themesToTry[2].hook = () -> MetalLookAndFeel.setCurrentTheme(new OceanTheme());
                } else if ("steel".equals(optarg)) {
                    SwingTheme.themesToTry[2].hook = () -> MetalLookAndFeel.setCurrentTheme(new DefaultMetalTheme());
                } else if ("system".equals(optarg)) {
                    SwingTheme.themesToTry[2].hook = null;
                }
            } else if ("theme".equals(optstr) && optarg == null) {
                // list themes
                for (final UIManager.LookAndFeelInfo look : UIManager.getInstalledLookAndFeels()) {
                    System.out.println(look.getClassName());
                }
                System.exit(0);
            } else if ("theme".equals(optstr) && "system".equals(optarg)) {
                SwingTheme.themesToTry[0].disabled = true;
                SwingTheme.themesToTry[1].disabled = true;
                SwingTheme.themesToTry[2].disabled = true;
            } else if ("theme".equals(optstr)) {
                SwingTheme.themesToTry[0] = new SwingTheme("User", optarg);
            } else {
                LOG.severe("unknown option: " + argv[argp]);
                System.exit(1);
            }
            ++argp;
            --argc;
        }
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

    /**
     * Tries to enable a Swing theme, with an ordered list of preferences.
     * If none works, use the default.
     */
    @RequiredArgsConstructor
    private static final class SwingTheme {
        final String name;
        final String cls;
        boolean disabled = false;
        Runnable hook = null;

        static SwingTheme[] themesToTry;

        static void tryThemes() {
            for (final SwingTheme theme : themesToTry) {
                if (!theme.disabled) {
                    try {
                        UIManager.setLookAndFeel(theme.cls);
                        if (theme.hook != null) {
                            theme.hook.run();
                        }
                        return;
                    } catch (Exception e) {
                        LOG.warning(String.format("could not set %s theme%n", theme.name));
                    }
                }
            }
            // ok, just use the default theme then
        }
    }

    private void init() {
        // hope for antialiased text (see drawAA() below for giving up on hope)
        System.setProperty("awt.useSystemAAFontSettings", "on");
        System.setProperty("prism.lcdtext", "false");
        System.setProperty("swing.aatext", "true");

        // set up theme now
        SwingTheme.tryThemes();

        try (final InputStream is = InitialiseLogging.getResourceAsStream("Inconsolatazi4varlquAH.ttf")) {
            final Font fileFont = Font.createFont(Font.TRUETYPE_FONT, is);
            monoFont = fileFont.deriveFont((float) 16);
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "could not load font", e);
            monoFont = new Font(Font.MONOSPACED, Font.PLAIN, 16);
        }
    }

    /**
     * Binds an action to a key. Swing utility.
     *
     * @param action to bind
     * @param where  in which focus to bind
     * @param key    to bind the action to, under {@link Action#NAME}
     */
    @SuppressWarnings("SameParameterValue")
    private void bindKey(final Action action, final int where, final KeyStroke key) {
        final JRootPane rootPane = frame.getRootPane();
        final Object name = action.getValue(Action.NAME);

        rootPane.getInputMap(where).put(key, name);
        rootPane.getActionMap().put(name, action);
    }

    /**
     * Tries to convince the renderer to draw fonts anti-aliased.
     *
     * @param g hopefully a {@link Graphics2D} instance
     * @return g
     */
    private Graphics drawAA(final Graphics g) {
        if (g instanceof Graphics2D) {
            final Graphics2D g2d = (Graphics2D) g;
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
            g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        } else {
            LOG.warning("cannot draw antialiased: " + g.getClass().getName());
        }
        return g;
    }

    private void run() {
        init();

        frame = new JFrame("ECN-Bits Channel Example");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(720, 600);
        contentPane = new JPanel(new BorderLayout());

        final JPanel controlArea = new JPanel();
        controlArea.setLayout(new BoxLayout(controlArea, BoxLayout.X_AXIS));

        prevBtn = new JButton("Prev");
        prevBtn.setMnemonic(KeyEvent.VK_P);
        prevBtn.setToolTipText("Switch to previous IP for this hostname");
        prevBtn.addActionListener(e -> tgtLabel.setText("prev ip"));
        controlArea.add(prevBtn);

        controlArea.add(Box.createRigidArea(new Dimension(5, 0)));
        final JPanel hostnameArea = new JPanel();
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
        nextBtn.setMnemonic(KeyEvent.VK_N);
        nextBtn.setToolTipText("Switch to next IP for this hostname");
        nextBtn.addActionListener(e -> tgtLabel.setText("next ip"));
        controlArea.add(nextBtn);

        controlArea.add(Box.createRigidArea(new Dimension(5, 0)));
        controlArea.add(new JSeparator(SwingConstants.VERTICAL) {
            /**
             * Prevents the separator from acting like a glue.
             *
             * @return maximum size for {@link BoxLayout} to use
             */
            @Override
            public Dimension getMaximumSize() {
                /* ARRRRRRRRRGH! */
                final Dimension d = new Dimension();
                d.setSize(getPreferredSize().getWidth(),
                  super.getMaximumSize().getHeight());
                return d;
            }
        });
        controlArea.add(Box.createRigidArea(new Dimension(5, 0)));

        final JPanel labelArea = new JPanel();
        labelArea.setLayout(new BoxLayout(labelArea, BoxLayout.Y_AXIS));
        final JLabel ecnLabel = new JLabel("ECN Bits:");
        ecnLabel.setDisplayedMnemonic('E');
        ecnLabel.setAlignmentX(Component.RIGHT_ALIGNMENT);
        labelArea.add(ecnLabel);
        final JLabel tcLabel = new JLabel("Traffic Class:");
        tcLabel.setDisplayedMnemonic('T');
        tcLabel.setAlignmentX(Component.RIGHT_ALIGNMENT);
        labelArea.add(tcLabel);
        controlArea.add(labelArea);
        controlArea.add(Box.createRigidArea(new Dimension(3, 0)));

        ecnBox = new JComboBox<BitsAdapter>(BitsAdapter.values) {
            /**
             * Prevents the dropdown from being wider than necessary.
             *
             * @return maximum size for {@link BoxLayout} to use
             */
            @Override
            public Dimension getMaximumSize() {
                return getPreferredSize();
            }

            /**
             * Requests the unopened box to be drawn with anti-aliased text.
             *
             * @param g hopefully a {@link Graphics2D} instance
             */
            @Override
            protected void paintComponent(final Graphics g) {
                super.paintComponent(drawAA(g));
            }
        };
        ecnBox.setRenderer(new BasicComboBoxRenderer() {
            /**
             * Requests the opened box to be drawn with anti-aliased text.
             *
             * @param g hopefully a {@link Graphics2D} instance
             */
            @Override
            protected void paintComponent(final Graphics g) {
                super.paintComponent(drawAA(g));
            }
        });
        setDropdown(Bits.ECT0);
        ecnBox.addActionListener(e -> {
            // changing the dropdown updates the tc input field

            // we want this, it apparently was forgotten during Swing genericisation… ☹
            //final byte el = ecnBox.getSelectedItem().getBit().getBits();
            final byte el = BitsAdapter.values[ecnBox.getSelectedIndex()].getBit().getBits();
            final byte tc = retrieveTC();
            setTC((tc & 0xFC) | (el & 0x03));
        });
        ecnBox.setFont(monoFont);
        controlArea.add(ecnBox);
        ecnLabel.setLabelFor(ecnBox);

        controlArea.add(Box.createRigidArea(new Dimension(4, 0)));

        tcField = new JTextField("00", 2 + /* offset Swing bugs */ 1) {
            /**
             * Prevents the textfield from not showing up occasionally.
             *
             * @return minimum size for {@link BoxLayout} to use
             */
            @Override
            public Dimension getMinimumSize() {
                return getPreferredSize();
            }

            /**
             * Prevents the textfield from expanding beyond its area.
             *
             * @return maximum size for {@link BoxLayout} to use
             */
            @Override
            public Dimension getMaximumSize() {
                return getPreferredSize();
            }

            /**
             * Convinces the renderer to draw the font anti-aliased.
             *
             * @param g hopefully a {@link Graphics2D} instance
             */
            @Override
            protected void paintComponent(final Graphics g) {
                super.paintComponent(drawAA(g));
            }

            /**
             * Implements overwrite-by-default mode.
             *
             * @param content to insert (usually which the user typed)
             */
            @Override
            public void replaceSelection(final String content) {
                final AbstractDocument doc = (AbstractDocument) getDocument();
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
                        UIManager.getLookAndFeel().provideErrorFeedback(tcField);
                    }
                    if (composedTextSaved) {
                        restoreComposedText();
                    }
                    return;
                }
                super.replaceSelection(content);
            }
        };
        tcField.setToolTipText("Traffic Class octet to use when sending");
        tcField.setFont(monoFont);
        ((AbstractDocument) tcField.getDocument()).setDocumentFilter(new HexDocumentFilter(2));
        final Border tcFieldBorderOK = tcField.getBorder();
        final Border tcFieldBorderERR = BorderFactory.createLineBorder(Color.red);
        tcField.getDocument().addDocumentListener((DocumentListenerLambda) e -> {
            final int len = e.getDocument().getLength();
            if (len == 2) {
                // changing the text updates the dropdown
                setDropdown(Bits.valueOf(retrieveTC()));
                // indicate validity by the border (HexDocumentFilter does the rest)
                tcField.setBorder(tcFieldBorderOK);
            } else {
                tcField.setBorder(tcFieldBorderERR);
            }
        });
        tcField.setCaretPosition(0);
        controlArea.add(tcField);
        tcLabel.setLabelFor(tcField);

        controlArea.add(Box.createRigidArea(new Dimension(8, 0)));

        sendBtn = new JButton("Send");
        sendBtn.setMnemonic(KeyEvent.VK_S);
        sendBtn.setToolTipText("Send a single UDP packet with the chosen traffic class to the currently active IP");
        sendBtn.addActionListener(e -> outArea.setText("boo!"));
        controlArea.add(sendBtn);

        controlArea.add(Box.createHorizontalGlue());

        // this gunk is necessary so it can be bound to the Escape key as well
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
            /**
             * Convinces the renderer to draw the font anti-aliased.
             *
             * @param g hopefully a {@link Graphics2D} instance
             */
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
        sendBtn.requestFocusInWindow();
    }

    /**
     * Updates the dropdown according to the ECN bits to set.
     *
     * @param toset ECN {@link Bits}
     */
    private void setDropdown(final Bits toset) {
        for (final BitsAdapter bit : BitsAdapter.values) {
            if (bit.getBit() == toset) {
                SwingUtilities.invokeLater(() -> ecnBox.setSelectedItem(bit));
            }
        }
    }

    /**
     * Retrieves the traffic class to use from the input field.
     * If the field’s contents are invalid, it’s reset to 0x00.
     *
     * @return traffic class octet
     */
    private byte retrieveTC() {
        final String t = tcField.getText();
        final byte v;
        if (t.length() == 2) {
            v = (byte) Integer.parseInt(t, 16);
        } else {
            setTC(0);
            v = (byte) 0x00;
        }
        return v;
    }

    /**
     * Sets the input field to the passed traffic class value.
     *
     * @param tc traffic class octet to set
     */
    private void setTC(final int tc) {
        final String by = String.format("%02X", tc & 0xFF);

        if (!by.equals(tcField.getText())) {
            final int pos = tcField.getCaretPosition();
            tcField.setText(by);
            tcField.setCaretPosition(pos);
        }
    }

    /**
     * {@link DocumentFilter} to ensure the document is comprised of only
     * hexadecimal digits and does not exceed a certain length.
     *
     * @see HexDocumentFilter#HexDocumentFilter(int)
     */
    static class HexDocumentFilter extends DocumentFilter {
        private final int sz;

        /**
         * Constructs a new {@link HexDocumentFilter}.
         *
         * @param maxlen to not exceed in the document
         */
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
            if (offset < 0 || length < 0) {
                throw new BadLocationException("negative len/ofs",
                  Math.min(offset, length));
            }
            if ((offset + length) > oldlen) {
                throw new BadLocationException("outside of document",
                  offset + length);
            }
            final int oldafter = oldlen - (offset + length);
            final int totlen = offset + text.length() + oldafter;
            final String ins = (totlen > sz) ? text.substring(0,
              text.length() - (totlen - sz)) : text;
            if (allowed(ins)) {
                super.replace(fb, offset, length, ins, attrs);
            }
        }

        /**
         * Returns whether a string is comprised of only permitted characters.
         *
         * @param s to test
         * @return true if so, false otherwise
         */
        static boolean allowed(final String s) {
            for (final char c : s.toCharArray()) {
                if (!(((c >= '0') && (c <= '9')) ||
                  ((c >= 'A') && (c <= 'F')) ||
                  ((c >= 'a') && (c <= 'f')))) {
                    return false;
                }
            }
            return true;
        }
    }

    /**
     * Helper to make the {@link DocumentListener} settable via a Java™ 8 Lambda.
     */
    @FunctionalInterface
    public interface DocumentListenerLambda extends DocumentListener {
        /**
         * Called after an insert, remove or modify/change operation was done.
         * This is set to the lambda.
         *
         * @param e {@link DocumentEvent}
         */
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

    /**
     * Adapts a {@link Bits} enum for use with a {@link JComboBox} which
     * uses {@link #toString()} by default.
     *
     * Shared between jdk-linux/src/main/java/…/jdk/jni/ChannelMain and
     * android/app/src/main/java/…/android/client/MainActivity so take care.
     */
    @RequiredArgsConstructor
    @Getter
    private static class BitsAdapter {
        static final BitsAdapter[] values;

        static {
            val bits = Bits.values();
            values = new BitsAdapter[bits.length];
            for (int i = 0; i < bits.length; ++i) {
                values[i] = new BitsAdapter(bits[i]);
            }
        }

        private final Bits bit;

        @NonNull
        @Override
        public String toString() {
            return bit.getShortname();
        }
    }
}
