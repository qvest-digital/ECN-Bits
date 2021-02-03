package de.telekom.llcto.ecn_bits.jdk.jni;

/*-
 * Copyright © 2020
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

/**
 * Enumerates the possible ECN bits.
 *
 * See {@link AbstractECNBitsDatagramSocket} for how to actually retrieve them.
 *
 * @author mirabilos (t.glaser@tarent.de)
 */
public enum Bits {
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
