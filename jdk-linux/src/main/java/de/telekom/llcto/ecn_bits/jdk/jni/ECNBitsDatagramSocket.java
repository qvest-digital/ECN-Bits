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

import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.SocketException;

/**
 * {@link DatagramSocket} equivalent capable of returning and
 * collecting ECN bits and the traffic class octet from received packets.
 * Not suitable for use with IP Multicast.
 *
 * This class offers the methods from {@link AbstractECNBitsDatagramReceiver}
 * to determine the IP traffic class and thus the ECN bits.
 *
 * @author mirabilos (t.glaser@tarent.de)
 */
public class ECNBitsDatagramSocket extends AbstractECNBitsDatagramSocket {
    private final ECNMeasurer tcm;

    private ECNBitsDatagramSocket(final ECNBitsDatagramSocketImpl impl,
      final SocketAddress bindaddr) throws SocketException {
        super(impl);
        if (bindaddr != null) {
            try {
                bind(bindaddr);
            } finally {
                if (!isBound()) {
                    close();
                }
            }
        }
        tcm = impl.getMeasurer();
    }

    @SuppressWarnings("unused")
    public ECNBitsDatagramSocket() throws SocketException {
        this(new InetSocketAddress(0));
    }

    public ECNBitsDatagramSocket(final SocketAddress bindaddr) throws SocketException {
        this(new ECNBitsDatagramSocketImpl(), bindaddr);
    }

    @SuppressWarnings("unused")
    public ECNBitsDatagramSocket(final int port) throws SocketException {
        this(port, null);
    }

    public ECNBitsDatagramSocket(final int port, final InetAddress laddr) throws SocketException {
        this(new InetSocketAddress(laddr, port));
    }

    @Override
    public Byte retrieveLastTrafficClass() {
        return tcm.last();
    }

    @Override
    public void startMeasurement() {
        tcm.doMeasuring(true, false);
    }

    @Override
    public ECNStatistics getMeasurement(final boolean doContinue) {
        return tcm.doMeasuring(doContinue, true);
    }
}
