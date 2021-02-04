package de.telekom.llcto.ecn_bits.jdk.jni;

/*-
 * Copyright © 2020, 2021
 *      mirabilos <t.glaser@tarent.de>
 * Copyright © 1996, 2007, 2011, 2013, 2015
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
 * ‣ jdk-linux/src/legal/COPYING
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

import lombok.extern.java.Log;

import java.io.FileDescriptor;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocketImpl;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.SocketOptions;
import java.net.SocketTimeoutException;
import java.util.Objects;
import java.util.logging.Level;

import static de.telekom.llcto.ecn_bits.jdk.jni.JNI.*;

/**
 * Reimplemented {@link DatagramSocketImpl} with extras to receive ECN bits,
 * or the traffic class octet really; use {@link ECNBitsDatagramSocket}.
 *
 * @author mirabilos (t.glaser@tarent.de)
 */
@Log
class ECNBitsDatagramSocketImpl extends DatagramSocketImpl {
    private int fd = -1;
    private final ECNMeasurer tcm = new ECNMeasurer();
    /* timeout value for receive() */
    private int timeout = 0;
    private boolean connected = false;

    // for ECNBitsDatagramSocket only
    ECNMeasurer getMeasurer() {
        return tcm;
    }

    @Override
    public void setOption(final int optID, final Object value) throws SocketException {
        if (isClosed()) {
            throw new SocketException("Socket closed");
        }
        final int opt;
        final int val;
        switch (optID) {
        case SocketOptions.SO_TIMEOUT:
            if (!(value instanceof Integer)) {
                throw new SocketException("bad argument for SO_TIMEOUT");
            }
            val = (Integer) value;
            if (val < 0) {
                throw new IllegalArgumentException("timeout < 0");
            }
            timeout = val;
            return;
        case SocketOptions.IP_TOS:
            if (!(value instanceof Integer)) {
                throw new SocketException("bad argument for IP_TOS");
            }
            val = (Integer) value;
            if (val < 0 || val > 255) {
                throw new IllegalArgumentException("Invalid IP_TOS value");
            }
            opt = JNI.IP_TOS;
            break;
        case SocketOptions.SO_REUSEADDR:
            if (!(value instanceof Boolean)) {
                throw new SocketException("bad argument for SO_REUSEADDR");
            }
            val = (Boolean) value ? 1 : 0;
            opt = JNI.SO_REUSEADDR;
            break;
        case SocketOptions.SO_BROADCAST:
            if (!(value instanceof Boolean)) {
                throw new SocketException("bad argument for SO_BROADCAST");
            }
            val = (Boolean) value ? 1 : 0;
            opt = JNI.SO_BROADCAST;
            break;
        case SocketOptions.SO_BINDADDR:
            throw new SocketException("Cannot re-bind Socket");
        case SocketOptions.SO_RCVBUF:
        case SocketOptions.SO_SNDBUF:
            if (!(value instanceof Integer) || (Integer) value < 0) {
                throw new SocketException("bad argument for SO_SNDBUF or " +
                  "SO_RCVBUF");
            }
            val = (Integer) value;
            opt = optID == SO_RCVBUF ? JNI.SO_RCVBUF : JNI.SO_SNDBUF;
            break;
        default:
            throw new SocketException("invalid option: " + optID);
        }
        n_setsockopt(fd, opt, val);
    }

    @Override
    public Object getOption(final int optID) throws SocketException {
        if (isClosed()) {
            throw new SocketException("Socket closed");
        }
        switch (optID) {
        case SocketOptions.SO_TIMEOUT:
            return timeout;
        case SocketOptions.IP_TOS:
            return n_getsockopt(fd, JNI.IP_TOS);
        case SocketOptions.SO_BINDADDR:
            final JNI.AddrPort ap = new JNI.AddrPort();
            n_getsockname(fd, ap);
            return Objects.requireNonNull(ap.get()).getAddress();
        case SocketOptions.SO_RCVBUF:
            return n_getsockopt(fd, JNI.SO_RCVBUF);
        case SocketOptions.SO_SNDBUF:
            return n_getsockopt(fd, JNI.SO_SNDBUF);
        case SocketOptions.SO_REUSEADDR:
            return n_getsockopt(fd, JNI.SO_REUSEADDR) != 0;
        case SocketOptions.SO_BROADCAST:
            return n_getsockopt(fd, JNI.SO_BROADCAST) != 0;
        default:
            throw new SocketException("invalid option: " + optID);
        }
    }

    // we cannot override int dataAvailable() here because it would never be called (nōn-public API)

    @Override
    protected synchronized void create() throws SocketException {
        fd = n_socket();
        n_setsockopt(fd, JNI.SO_BROADCAST, 1);
    }

    @Override
    protected synchronized void bind(final int lport, final InetAddress laddr) throws SocketException {
        if (isClosed()) {
            throw new SocketException("Socket closed");
        }
        n_bind(fd, JNI.AddrPort.addr(laddr), lport, JNI.AddrPort.scopeId(laddr));
        if (lport == 0) {
            final JNI.AddrPort ap = new JNI.AddrPort();
            n_getsockname(fd, ap);
            localPort = ap.port;
        } else {
            localPort = lport;
        }
    }

    @Override
    protected void send(final DatagramPacket p) throws IOException {
        if (isClosed()) {
            throw new SocketException("Socket closed");
        }
        if (p.getData() == null || p.getAddress() == null) {
            throw new NullPointerException("null buffer || null address");
        }

        int port = connected ? 0 : p.getPort();
        InetAddress address = connected ? null : p.getAddress();
        int n;
        do {
            n = n_sendto(fd, p.getData(), p.getOffset(), p.getLength(),
              address == null ? null : JNI.AddrPort.addr(address),
              port, JNI.AddrPort.scopeId(address));
        } while (n == JNI.EINTR && !isClosed());
    }

    @Override
    protected void connect(final InetAddress address, final int port) throws SocketException {
        if (isClosed()) {
            throw new SocketException("Socket closed");
        }
        n_connect(fd, JNI.AddrPort.addr(address), port, JNI.AddrPort.scopeId(address));
        connected = true;
    }

    @Override
    protected void disconnect() {
        if (isClosed()) {
            return;
        }
        try {
            n_disconnect(fd);
        } catch (SocketException e) {
            // no way to signal this
            throw new RuntimeException("ECNBitsDatagramSocketImpl.disconnect()", e);
        } finally {
            connected = false;
        }
    }

    @SuppressWarnings("RedundantThrows")
    @Override
    protected int peek(final InetAddress i) throws IOException {
        // not called because we implement peekData (below)
        LOG.severe("unsupported operation called (cannot be implemented)");
        throw new UnsupportedOperationException("DatagramSocketImpl.peek() is IPv4-only");
    }

    @Override
    protected synchronized int peekData(final DatagramPacket p) throws IOException {
        doRecv(p, true);
        return p.getPort();
    }

    @Override
    protected synchronized void receive(final DatagramPacket p) throws IOException {
        doRecv(p, false);
    }

    private void doRecv(DatagramPacket p, boolean peekOnly) throws IOException {
        tcm.listen();
        if (isClosed()) {
            throw new SocketException("Socket closed");
        }
        if (timeout != 0) {
            switch (n_pollin(fd, timeout)) {
            case 0:
                throw new SocketTimeoutException("recvmsg poll timed out");
            case 1:
                break;
            default:
                throw new SocketException("recvmsg poll failed");
            }
        }
        final JNI.AddrPort aptc = new JNI.AddrPort();
        /*
         * this is wrong (should be p.bufLength) but see the comment in
         * {@link ECNBitsDatagramSocketAdapter#receive(DatagramPacket)}
         * concerning the JDK guarantees and Android’s use of the same;
         * see also https://bugs.openjdk.java.net/browse/JDK-4161511
         */
        int rv;
        do {
            rv = n_recvfrom(fd, p.getData(), p.getOffset(), p.getLength(),
              aptc, peekOnly, connected);
        } while (rv == JNI.EINTR && !isClosed());
        final InetSocketAddress src = aptc.get();
        if (rv == JNI.EAVAIL || src == null) {
            throw new SocketTimeoutException("recvmsg timed out");
        }
        // also wrong (see above), should be p.setReceivedLength()
        p.setLength(rv);
        p.setPort(src.getPort());
        // packet.address should only be changed when it is different from src
        if (!src.getAddress().equals(p.getAddress())) {
            p.setAddress(src.getAddress());
        }
        tcm.received(aptc.tcValid, aptc.tc);
    }

    @Override
    @Deprecated
    protected void setTTL(final byte ttl) throws IOException {
        setTimeToLive((int) ttl & 0xff);
    }

    @Override
    @Deprecated
    protected byte getTTL() throws IOException {
        return (byte) getTimeToLive();
    }

    @Override
    protected void setTimeToLive(final int ttl) throws IOException {
        if (isClosed()) {
            throw new SocketException("Socket closed");
        }
        n_setsockopt(fd, JNI.IPV6_MULTICAST_HOPS, ttl);
    }

    @Override
    protected int getTimeToLive() throws IOException {
        if (isClosed()) {
            throw new SocketException("Socket closed");
        }
        return n_getsockopt(fd, JNI.IPV6_MULTICAST_HOPS);
    }

    @Override
    @SuppressWarnings("RedundantThrows")
    protected void join(final InetAddress inetaddr) throws IOException {
        LOG.severe("unsupported operation called (no multicast support)");
        throw new UnsupportedOperationException("ECNBitsDatagramSocketImpl does not support IPv4 Multicast");
    }

    @Override
    @SuppressWarnings("RedundantThrows")
    protected void leave(final InetAddress inetaddr) throws IOException {
        LOG.severe("unsupported operation called (no multicast support)");
        throw new UnsupportedOperationException("ECNBitsDatagramSocketImpl does not support IPv4 Multicast");
    }

    @Override
    @SuppressWarnings("RedundantThrows")
    protected void joinGroup(final SocketAddress mcastaddr, final NetworkInterface netIf) throws IOException {
        LOG.severe("unsupported operation called (no multicast support)");
        throw new UnsupportedOperationException("ECNBitsDatagramSocketImpl does not support IPv4 Multicast");
    }

    @Override
    @SuppressWarnings("RedundantThrows")
    protected void leaveGroup(final SocketAddress mcastaddr, final NetworkInterface netIf) throws IOException {
        LOG.severe("unsupported operation called (no multicast support)");
        throw new UnsupportedOperationException("ECNBitsDatagramSocketImpl does not support IPv4 Multicast");
    }

    @Override
    protected void close() {
        if (fd != -1) {
            try {
                // no way to call AsynchronousCloseMonitor::signalBlockedThreads (nōn-public API)
                n_close(fd);
            } catch (IOException e) {
                LOG.log(Level.WARNING, "close(" + fd + ") failed", e);
            } finally {
                fd = -1;
            }
        }
    }

    private boolean isClosed() {
        return fd == -1;
    }

    @SuppressWarnings("deprecation")
    @Override
    public void finalize() {
        close();
    }

    @Override
    protected FileDescriptor getFileDescriptor() {
        LOG.severe("unsupported operation called (cannot work)");
        throw new UnsupportedOperationException("DatagramSocketImpl.getFileDescriptor() will not work");
    }
}
