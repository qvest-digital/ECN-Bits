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
 * Implementation for collecting ECN bit statistics and the last TC octet
 *
 * @author mirabilos (t.glaser@tarent.de)
 */
class ECNMeasurer {
    private Byte lastTc = null;
    private byte measuring = 0;
    final private int[] measurement = new int[2];
    private long measuredFrom;

    /**
     * Sets up listening for the next packet. Intended to be called at the
     * beginning of all functions a user can call to receive a datagram, so
     * the cached last packet’s traffic class field can be reset.
     */
    public void listen() {
        synchronized (this) {
            lastTc = null;
        }
    }

    /**
     * Retrieves the traffic class of the last datagram received.
     *
     * @return byte or nil if no record
     */
    public Byte last() {
        synchronized (this) {
            return lastTc;
        }
    }

    /**
     * Records the receipt of a new datagram. This should be called only if
     * the packet was received successfully, but independently of whether
     * a traffic class octet was actually read.
     *
     * @param valid whether the {@code octet} argument is valid
     * @param octet the traffic class octet of the received datagram
     */
    public void received(final boolean valid, final byte octet) {
        synchronized (this) {
            if (valid) {
                lastTc = octet;
            }

            if (measuring == 1) {
                final int offset = valid && Bits.CE.equals(Bits.valueOf(octet)) ? 1 : 0;
                if (measurement[offset] == Integer.MAX_VALUE / 2) {
                    measuring = 2;
                } else {
                    ++measurement[offset];
                }
            }
        }
    }

    /**
     * Starts/stops live measuring and provides statistics of received packets.
     *
     * @param start whether to start/continue measuring after this
     * @param cont  (set this to true)
     * @return statistics about the last period; nil unless measuring
     * @throws ArithmeticException if too many packets were received during the interval
     */
    public ECNStatistics doMeasuring(final boolean start, final boolean cont) {
        ECNStatistics rv = null;
        final int measured;
        synchronized (this) {
            // timestamp quickly
            if (measuring > 0) {
                rv = new ECNStatistics(measuredFrom, measurement);
            }
            // save old state
            measured = measuring;
            // start new cycle
            measuring = start ? (byte) 1 : (byte) 0;
            measurement[0] = 0;
            measurement[1] = 0;
            // timestamp just as we’re leaving the synchronised section
            measuredFrom = System.nanoTime();
        }
        // new period is started, let’s return information about past period
        if (cont && measured > 1) {
            throw new ArithmeticException("too many packets during measurement period");
        }
        return rv;
    }
}
