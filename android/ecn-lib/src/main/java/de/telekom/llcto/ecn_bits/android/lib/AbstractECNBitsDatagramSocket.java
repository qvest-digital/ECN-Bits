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

import java.net.DatagramSocket;
import java.net.DatagramSocketImpl;
import java.net.ECNBitsDatagramChannel;
import java.net.ECNBitsDatagramSocket;
import java.net.InetAddress;
import java.net.SocketAddress;
import java.net.SocketException;

/**
 * Façade around {@link DatagramSocket} as common parent class of both new
 * {@link ECNBitsDatagramSocket} and {@link ECNBitsDatagramChannel#socket()}
 * results, so they share a common API.
 *
 * @author mirabilos (t.glaser@tarent.de)
 */
public abstract class AbstractECNBitsDatagramSocket extends DatagramSocket implements AbstractECNBitsDatagramReceiver {
    @SuppressWarnings("unused")
    public AbstractECNBitsDatagramSocket() throws SocketException {
        super();
    }

    @SuppressWarnings("unused")
    protected AbstractECNBitsDatagramSocket(final DatagramSocketImpl impl) {
        super(impl);
    }

    @SuppressWarnings("unused")
    public AbstractECNBitsDatagramSocket(final SocketAddress bindaddr) throws SocketException {
        super(bindaddr);
    }

    @SuppressWarnings("unused")
    public AbstractECNBitsDatagramSocket(final int port) throws SocketException {
        super(port);
    }

    @SuppressWarnings("unused")
    public AbstractECNBitsDatagramSocket(final int port, final InetAddress laddr) throws SocketException {
        super(port, laddr);
    }

    abstract public Byte retrieveLastTrafficClass();

    abstract public void startMeasurement();

    abstract public ECNStatistics getMeasurement(final boolean doContinue);
}
