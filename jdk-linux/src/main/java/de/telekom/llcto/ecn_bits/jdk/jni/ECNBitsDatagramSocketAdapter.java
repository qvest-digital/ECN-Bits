package de.telekom.llcto.ecn_bits.jdk.jni;

/*-
 * Copyright © 2020
 *      mirabilos <t.glaser@tarent.de>
 * Copyright © 2014
 *      The Android Open Source Project
 * Copyright © 2000, 2001, 2012, 2013
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

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocketImpl;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.SocketOption;
import java.net.SocketTimeoutException;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.AlreadyBoundException;
import java.nio.channels.AlreadyConnectedException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.IllegalBlockingModeException;
import java.nio.channels.NotYetBoundException;
import java.nio.channels.NotYetConnectedException;
import java.nio.channels.UnresolvedAddressException;
import java.nio.channels.UnsupportedAddressTypeException;

/**
 * Makes a datagram-socket channel look like a datagram socket
 *
 * @author mirabilos (t.glaser@tarent.de)
 */
class ECNBitsDatagramSocketAdapter extends AbstractECNBitsDatagramSocket {
    // The channel being adapted
    private final ECNBitsDatagramChannelImpl dc;

    // Timeout "option" value for receives
    private volatile int timeout = 0;

    // ## super will create a useless impl
    ECNBitsDatagramSocketAdapter(final ECNBitsDatagramChannelImpl dc) {
        // Invoke the DatagramSocketAdaptor(SocketAddress) constructor,
        // passing a dummy DatagramSocketImpl object to aovid any native
        // resource allocation in super class and invoking our bind method
        // before the dc field is initialized.
        super(dummyDatagramSocket);
        this.dc = dc;
    }

    private void connectInternal(final SocketAddress remote) throws SocketException {
        final InetSocketAddress isa = net_asInetSocketAddress(remote);
        final int port = isa.getPort();
        if (port < 0 || port > 0xFFFF) {
            throw new IllegalArgumentException("connect: " + port);
        }
        if (isClosed()) {
            return;
        }
        try {
            dc.connect(remote);
        } catch (Exception x) {
            net_translateToSocketException(x);
        }
    }

    @Override
    public void bind(final SocketAddress local) throws SocketException {
        try {
            dc.bind(local);
        } catch (Exception x) {
            net_translateToSocketException(x);
        }
    }

    @Override
    public void connect(final InetAddress address, final int port) {
        try {
            connectInternal(new InetSocketAddress(address, port));
        } catch (SocketException x) {
            // Yes, j.n.DatagramSocket really does this
        }
    }

    @Override
    public void connect(final SocketAddress remote) throws SocketException {
        if (remote == null) {
            throw new IllegalArgumentException("Address can't be null");
        }
        connectInternal(remote);
    }

    @Override
    public void disconnect() {
        try {
            dc.disconnect();
        } catch (IOException x) {
            throw new Error(x);
        }
    }

    @Override
    public boolean isBound() {
        return dc.i_localAddress() != null;
    }

    @Override
    public boolean isConnected() {
        return dc.i_remoteAddress() != null;
    }

    @Override
    public InetAddress getInetAddress() {
        return (isConnected() ? net_asInetSocketAddress(dc.i_remoteAddress()).getAddress() : null);
    }

    @Override
    public int getPort() {
        return (isConnected() ? net_asInetSocketAddress(dc.i_remoteAddress()).getPort() : -1);
    }

    @Override
    public void send(final DatagramPacket p) throws IOException {
        synchronized (dc.blockingLock()) {
            if (!dc.isBlocking()) {
                throw new IllegalBlockingModeException();
            }
            try {
                // because Android originally does this it "must" be right here:
                //noinspection SynchronizationOnLocalVariableOrMethodParameter
                synchronized (p) {
                    final ByteBuffer bb = ByteBuffer.wrap(p.getData(), p.getOffset(), p.getLength());
                    if (dc.isConnected()) {
                        if (p.getAddress() == null) {
                            // Legacy DatagramSocket will send in this case
                            // and set address and port of the packet
                            InetSocketAddress isa = (InetSocketAddress) dc.i_remoteAddress();
                            p.setPort(isa.getPort());
                            p.setAddress(isa.getAddress());
                            dc.write(bb);
                        } else {
                            // Target address may not match connected address
                            dc.send(bb, p.getSocketAddress());
                        }
                    } else {
                        // Not connected so address must be valid or throw
                        dc.send(bb, p.getSocketAddress());
                    }
                }
            } catch (IOException x) {
                net_translateException(x);
            }
        }
    }

    // Must hold dc.blockingLock()
    //
    private SocketAddress receive(final ByteBuffer bb) throws IOException {
        if (timeout == 0) {
            return dc.receive(bb);
        }

        dc.configureBlocking(false);
        try {
            SocketAddress sender;
            if ((sender = dc.receive(bb)) != null) {
                return sender;
            }
            int to = timeout;
            for (; ; ) {
                if (!dc.isOpen()) {
                    throw new ClosedChannelException();
                }
                final long st = System.currentTimeMillis();
                if (dc.pollin(to) > 0) {
                    if ((sender = dc.receive(bb)) != null) {
                        return sender;
                    }
                }
                to -= System.currentTimeMillis() - st;
                if (to <= 0) {
                    throw new SocketTimeoutException();
                }
            }
        } finally {
            if (dc.isOpen()) {
                dc.configureBlocking(true);
            }
        }
    }

    @Override
    public void receive(final DatagramPacket p) throws IOException {
        synchronized (dc.blockingLock()) {
            if (!dc.isBlocking()) {
                throw new IllegalBlockingModeException();
            }
            try {
                // because Android originally does this it "must" be right here:
                //noinspection SynchronizationOnLocalVariableOrMethodParameter
                synchronized (p) {
                    final ByteBuffer bb = ByteBuffer.wrap(p.getData(), p.getOffset(), p.getLength());
                    final SocketAddress sender = receive(bb);
                    p.setSocketAddress(sender);
                    // this is wrong but what the JDK does; p.setReceivedLength()
                    // should be used, but it’s package-private and äpp-hidden so
                    // we can’t and the stock Android DatagramSocketAdapter does
                    // the same; the JDK requires users to reset length each use,
                    // as https://bugs.openjdk.java.net/browse/JDK-4161511 states
                    p.setLength(bb.position() - p.getOffset());
                }
            } catch (IOException x) {
                net_translateException(x);
            }
        }
    }

    @Override
    public InetAddress getLocalAddress() {
        if (isClosed()) {
            return null;
        }
        SocketAddress local = dc.i_localAddress();
        if (local == null) {
            local = new InetSocketAddress(0);
        }
        final InetAddress result = ((InetSocketAddress) local).getAddress();
        final SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            try {
                sm.checkConnect(result.getHostAddress(), -1);
            } catch (SecurityException x) {
                return new InetSocketAddress(0).getAddress();
            }
        }
        return result;
    }

    @Override
    public int getLocalPort() {
        if (isClosed()) {
            return -1;
        }
        try {
            final SocketAddress local = dc.getLocalAddress();
            if (local != null) {
                return ((InetSocketAddress) local).getPort();
            }
        } catch (Exception x) {
            // meow
        }
        return 0;
    }

    @Override
    public void setSoTimeout(final int timeout) {
        this.timeout = timeout;
    }

    @Override
    public int getSoTimeout() {
        return timeout;
    }

    private void setBooleanOption(final SocketOption<Boolean> name, final boolean value) throws SocketException {
        try {
            dc.setOption(name, value);
        } catch (IOException x) {
            net_translateToSocketException(x);
        }
    }

    private void setIntOption(final SocketOption<Integer> name, final int value) throws SocketException {
        try {
            dc.setOption(name, value);
        } catch (IOException x) {
            net_translateToSocketException(x);
        }
    }

    private boolean getBooleanOption(final SocketOption<Boolean> name) throws SocketException {
        try {
            return dc.getOption(name);
        } catch (IOException x) {
            net_translateToSocketException(x);
            return false;       // keep compiler happy
        }
    }

    private int getIntOption(final SocketOption<Integer> name) throws SocketException {
        try {
            return dc.getOption(name);
        } catch (IOException x) {
            net_translateToSocketException(x);
            return -1;          // keep compiler happy
        }
    }

    @Override
    public void setSendBufferSize(final int size) throws SocketException {
        if (size <= 0) {
            throw new IllegalArgumentException("Invalid send size");
        }
        setIntOption(StandardSocketOptions.SO_SNDBUF, size);
    }

    @Override
    public int getSendBufferSize() throws SocketException {
        return getIntOption(StandardSocketOptions.SO_SNDBUF);
    }

    @Override
    public void setReceiveBufferSize(final int size) throws SocketException {
        if (size <= 0) {
            throw new IllegalArgumentException("Invalid receive size");
        }
        setIntOption(StandardSocketOptions.SO_RCVBUF, size);
    }

    @Override
    public int getReceiveBufferSize() throws SocketException {
        return getIntOption(StandardSocketOptions.SO_RCVBUF);
    }

    @Override
    public void setReuseAddress(final boolean on) throws SocketException {
        setBooleanOption(StandardSocketOptions.SO_REUSEADDR, on);
    }

    @Override
    public boolean getReuseAddress() throws SocketException {
        return getBooleanOption(StandardSocketOptions.SO_REUSEADDR);
    }

    @Override
    public void setBroadcast(final boolean on) throws SocketException {
        setBooleanOption(StandardSocketOptions.SO_BROADCAST, on);
    }

    @Override
    public boolean getBroadcast() throws SocketException {
        return getBooleanOption(StandardSocketOptions.SO_BROADCAST);
    }

    @Override
    public void setTrafficClass(final int tc) throws SocketException {
        setIntOption(StandardSocketOptions.IP_TOS, tc);
    }

    @Override
    public int getTrafficClass() throws SocketException {
        return getIntOption(StandardSocketOptions.IP_TOS);
    }

    @Override
    public void close() {
        try {
            dc.close();
        } catch (IOException x) {
            throw new Error(x);
        }
    }

    @Override
    public boolean isClosed() {
        return !dc.isOpen();
    }

    @Override
    public ECNBitsDatagramChannel getChannel() {
        return dc;
    }

    @Override
    public Byte retrieveLastTrafficClass() {
        return dc.retrieveLastTrafficClass();
    }

    @Override
    public void startMeasurement() {
        dc.startMeasurement();
    }

    @Override
    public ECNStatistics getMeasurement(final boolean doContinue) {
        return dc.getMeasurement(doContinue);
    }

    /*
     * A dummy implementation of DatagramSocketImpl that can be passed to the
     * DatagramSocket constructor so that no native resources are allocated in
     * super class.
     */
    private static final DatagramSocketImpl dummyDatagramSocket = new DatagramSocketImpl() {
        @Override
        protected void create() {
        }

        @Override
        protected void bind(final int lport, final InetAddress laddr) {
        }

        @Override
        protected void send(final DatagramPacket p) {
        }

        @Override
        protected int peek(final InetAddress i) {
            return 0;
        }

        @Override
        protected int peekData(final DatagramPacket p) {
            return 0;
        }

        @Override
        protected void receive(final DatagramPacket p) {
        }

        @Override
        @SuppressWarnings({ "deprecation", /* UnIntelliJ bug */ "RedundantSuppression" })
        protected void setTTL(final byte ttl) {
        }

        @Override
        @SuppressWarnings({ "deprecation", /* UnIntelliJ bug */ "RedundantSuppression" })
        protected byte getTTL() {
            return 0;
        }

        @Override
        protected void setTimeToLive(final int ttl) {
        }

        @Override
        protected int getTimeToLive() {
            return 0;
        }

        @Override
        protected void join(final InetAddress inetaddr) {
        }

        @Override
        protected void leave(final InetAddress inetaddr) {
        }

        @Override
        protected void joinGroup(final SocketAddress mcastaddr, final NetworkInterface netIf) {
        }

        @Override
        protected void leaveGroup(final SocketAddress mcastaddr, final NetworkInterface netIf) {
        }

        @Override
        protected void close() {
        }

        @Override
        public Object getOption(final int optID) {
            return null;
        }

        @Override
        public void setOption(final int optID, final Object value) {
        }
    };

    static InetSocketAddress net_asInetSocketAddress(final SocketAddress sa) {
        if (!(sa instanceof InetSocketAddress)) {
            throw new UnsupportedAddressTypeException();
        }
        return (InetSocketAddress) sa;
    }

    static void net_translateToSocketException(final Exception x) throws SocketException {
        if (x instanceof SocketException) {
            throw (SocketException) x;
        }
        Exception nx = x;
        if (x instanceof ClosedChannelException) {
            nx = new SocketException("Socket is closed");
        } else if (x instanceof NotYetConnectedException) {
            nx = new SocketException("Socket is not connected");
        } else if (x instanceof AlreadyBoundException) {
            nx = new SocketException("Already bound");
        } else if (x instanceof NotYetBoundException) {
            nx = new SocketException("Socket is not bound yet");
        } else if (x instanceof UnsupportedAddressTypeException) {
            nx = new SocketException("Unsupported address type");
        } else if (x instanceof UnresolvedAddressException) {
            nx = new SocketException("Unresolved address");
            // Android-added: Handling for AlreadyConnectedException.
        } else if (x instanceof AlreadyConnectedException) {
            nx = new SocketException("Already connected");
        }
        if (nx != x) {
            nx.initCause(x);
        }

        if (nx instanceof SocketException) {
            throw (SocketException) nx;
        } else if (nx instanceof RuntimeException) {
            throw (RuntimeException) nx;
        } else {
            throw new Error("Untranslated exception", nx);
        }
    }

    static void net_translateException(final Exception x) throws IOException {
        if (x instanceof IOException) {
            throw (IOException) x;
        }
        net_translateToSocketException(x);
    }
}
