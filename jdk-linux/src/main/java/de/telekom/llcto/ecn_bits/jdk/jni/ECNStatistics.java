package de.telekom.llcto.ecn_bits.jdk.jni;

/*-
 * Copyright © 2020, 2021
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
 * Statistics about ECN CE bits set on received traffic in the last interval
 *
 * @author mirabilos (t.glaser@tarent.de)
 */
@SuppressWarnings("unused")
public class ECNStatistics {
    /**
     * Offset between {@link System#currentTimeMillis()} and
     * {@link System#nanoTime()} — add to nanoTime() to get
     * nanoseconds since the epoch.
     *
     * The year 2106 in nanoseconds since 1970 fits into 62 bits.
     */
    private static final long NANO_OFFSET;

    static {
        // this initialisation code is *carefully* tweaked for precision
        final long curMS = System.currentTimeMillis();
        final long curNS = System.nanoTime();
        NANO_OFFSET = curMS * 1000000L - curNS;
    }

    private final long nanoStart;
    private final long nanoEnd;
    private final int congested;
    private final int packets;

    /**
     * Private constructor for an ECN-Bits statistics measurement structure
     *
     * @param start       timestamp
     * @param measurement of packets
     */
    public ECNStatistics(final long start, final int[] measurement) {
        nanoEnd = System.nanoTime();
        nanoStart = start;
        congested = measurement[1];
        packets = measurement[0] + measurement[1];
    }

    /**
     * Returns the timestamp of the start of the measuring period
     *
     * @return nanoseconds since the epoch (1970)
     */
    public long getStartOfMeasuringPeriod() {
        return NANO_OFFSET + nanoStart;
    }

    /**
     * Returns the timestamp of the end of the measuring period
     *
     * @return nanoseconds since the epoch (1970)
     */
    public long getEndOfMeasuringPeriod() {
        return NANO_OFFSET + nanoEnd;
    }

    /**
     * Returns the length of the measuring period
     *
     * @return nanoseconds since the starting timestamp
     */
    public long getLengthOfMeasuringPeriod() {
        return nanoEnd - nanoStart;
    }

    /**
     * Returns the number of packets which had the ECN CE mark
     * that were received during the measuring period
     *
     * @return number of congested packets
     */
    public int getCongestedPackets() {
        return congested;
    }

    /**
     * Returns the total number of packets
     * that were received during the measuring period
     *
     * @return total number of packets received
     */
    public int getReceivedPackets() {
        return packets;
    }

    /**
     * Returns the congestion factor, that is,
     * how many packets received during the measuring period
     * had the ECN CE mark
     *
     * @return 0 ≤ factor ≤ 1
     */
    public double getCongestionFactor() {
        if (packets == 0) {
            return 0;
        }
        return (double) congested / (double) packets;
    }
}
