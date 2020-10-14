package java.net;

/*-
 * Copyright © 2020
 *      mirabilos <t.glaser@tarent.de>
 * Copyright © 1996, 2007, 2011, 2013
 *      Oracle and/or its affiliates
 * Licensor: Deutsche Telekom
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; version 2 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * The full text of that licence can be found in the following file:
 * ‣ android/ecn-lib/src/main/resources/COPYING
 *
 * As a special exception, the copyright holders of this library give
 * you permission to link this library with independent modules to
 * produce an executable, regardless of the license terms of these
 * independent modules, and to copy and distribute the resulting
 * executable under terms of your choice, provided that you also meet,
 * for each linked independent module, the terms and conditions of the
 * license of that module.  An independent module is a module which is
 * not derived from or based on this library.  If you modify this
 * library, you may extend this exception to your version of the
 * library, but you are not obligated to do so.  If you do not wish
 * to do so, delete this exception statement from your version.
 */

import de.telekom.llcto.ecn_bits.jdk.lib.ECNStatistics;

/**
 * Plain{@link DatagramSocketImpl} with extras to receive ECN bits,
 * or the traffic class octet really; use {@link ECNBitsDatagramSocket}.
 *
 * TODO
 *
 * @author mirabilos (t.glaser@tarent.de)
 */
class ECNBitsDatagramSocketImpl /*extends PlainDatagramSocketImpl*/ {
    private Byte lastTc = null;
    private byte measuring = 0;
    final private int[] measurement = new int[2];
    private long measuredFrom;

    void setUpRecvTclass() {
        lastTc = null;
        // TODO
    }

    Byte retrieveLastTrafficClass() {
        return lastTc;
    }

    ECNStatistics doMeasuring(final boolean start) {
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
        if (measured > 1) {
            throw new ArithmeticException("too many packets during measurement period");
        }
        return rv;
    }
}
