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

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.DatagramSocketImpl;
import java.net.InetAddress;
import java.net.PortUnreachableException;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.channels.DatagramChannel;
import java.nio.channels.IllegalBlockingModeException;

/**
 * Façade around {@link DatagramSocket} as common parent class of both new
 * {@link ECNBitsDatagramSocket} and {@link ECNBitsDatagramChannel#socket()}
 * results, so they share a common API.
 *
 * This class is basically a stock {@link DatagramSocket} that also offers
 * the methods from {@link AbstractECNBitsDatagramReceiver} for its instances.
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

    /**
     * {@inheritDoc}
     *
     * <hr>
     * <h3 style="text-align:center;">Important API note</h3>
     * <p>It is <strong>mandatory</strong> to reset the DatagramPacket’s buffer
     * after each call to {@link #receive(DatagramPacket)} if the DatagramPacket
     * will be reused (such as for another receive call, send, etc.) because the
     * recorded length of its buffer will be set to the received data size, so
     * information about the actual buffer size/capacity will not be retained:</p>
     *
     * <br><pre>datagramPacket.setData(datagramPacket.getData());</pre><br>
     *
     * <p>This is also a requirement with the original J2SE function, but the
     * standard DatagramSocket implementation, although <em>not</em> the one
     * from {@link DatagramChannel#socket()}, forgives failure to reset the
     * buffer because it can change the length but keep the capacity known;
     * something extension and adapter classes, like this one, cannot do. This
     * subtle difference can cause surprises when switching implementations.</p>
     *
     * <p>Resetting the buffer can also be done before every call to this method
     * if the DatagramPacket is used only to receive data. This may be easier.</p>
     *
     * @throws IOException                  {@inheritDoc}
     * @throws SocketTimeoutException       {@inheritDoc}
     * @throws PortUnreachableException     {@inheritDoc}
     * @throws IllegalBlockingModeException {@inheritDoc}
     * @see DatagramPacket
     * @see DatagramSocket
     * @see DatagramPacket#setData(byte[])
     * @see DatagramSocket#receive(DatagramPacket)
     */
    @Override
    public synchronized void receive(final DatagramPacket p) throws IOException {
        super.receive(p);
    }
}
