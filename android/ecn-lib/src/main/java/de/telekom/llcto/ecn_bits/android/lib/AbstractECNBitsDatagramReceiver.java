package de.telekom.llcto.ecn_bits.android.lib;

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

import java.net.ECNBitsDatagramChannel;
import java.net.ECNBitsDatagramSocket;

/**
 * Common interface for an API provided to read the IP traffic class octet
 * by {@link ECNBitsDatagramSocket} and {@link ECNBitsDatagramChannel}.
 *
 * @author mirabilos (t.glaser@tarent.de)
 */
public interface AbstractECNBitsDatagramReceiver {
    /**
     * Retrieves the traffic class the last packet that was peekData’d or received had
     *
     * @return byte tc; null if the tc could not be determined or there was no packet
     */
    Byte retrieveLastTrafficClass();

    /**
     * Starts (or restarts, resetting) measurement of the percentage of received
     * packets that had the ECN CE mark set, i.e. that were congested.
     *
     * @see #getMeasurement(boolean)
     */
    void startMeasurement();

    /**
     * Retrieves the measurement data regarding the percentage of received
     * packets that had the ECN CE mark set, i.e. that were congested, for
     * the period between the previous call to {@link #startMeasurement()}
     * or this function with {@code doContinue} set to true, and this call.
     *
     * If {@code doContinue} is true, also starts a new measurement cycle;
     * otherwise, stops measuring for the current socket;to save CPU, doing
     * measurements is not enabled unless explicitly requested.
     *
     * @param doContinue whether to continue measuring
     * @return {@link ECNStatistics}, or null if not measuring before
     * @throws ArithmeticException if too many packets were received in the last period
     */
    ECNStatistics getMeasurement(final boolean doContinue);
}
