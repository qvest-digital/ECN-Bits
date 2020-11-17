package java.net;

/*-
 * Copyright © 2020
 *      mirabilos <t.glaser@tarent.de>
 * Copyright © 2014
 *	The Android Open Source Project
 * Copyright © 2000, 2001, 2013, 2018
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

import de.telekom.llcto.ecn_bits.android.lib.IOStatus;
import de.telekom.llcto.ecn_bits.android.lib.NativeThread;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.spi.SelectorProvider;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

class ECNBitsDatagramChannelImpl extends ECNBitsDatagramChannel {
    private final int fdVal;

    // IDs of native threads doing reads and writes, for signalling
    private volatile long readerThread = 0;
    private volatile long writerThread = 0;

    // Lock held by current reading or connecting thread
    private final Object readLock = new Object();

    // Lock held by current writing or connecting thread
    private final Object writeLock = new Object();

    // Lock held by any thread that modifies the state fields declared below
    // DO NOT invoke a blocking I/O operation while holding this lock!
    private final Object stateLock = new Object();

    // -- The following fields are protected by stateLock

    // State (does not necessarily increase monotonically)
    private static final int ST_UNINITIALIZED = -1;
    private static final int ST_UNCONNECTED = 0;
    private static final int ST_CONNECTED = 1;
    private static final int ST_KILLED = 2;
    private int state = ST_UNINITIALIZED;

    // Binding
    private InetSocketAddress localAddress;
    private InetSocketAddress remoteAddress;

    // Our socket adaptor, if any
    private DatagramSocket socket;

    // -- End of fields protected by stateLock

    private static native int n_socket() throws IOException;

    private static native int receive(final ReceiveArgs args) throws IOException;

    public ECNBitsDatagramChannelImpl(final SelectorProvider sp) throws IOException {
        super(sp);
        this.fdVal = n_socket();
        this.state = ST_UNCONNECTED;
    }

    @Override
    public DatagramSocket socket() {
        /*synchronized (stateLock) {
            if (socket == null)
                socket = sun.nio.ch.DatagramSocketAdaptor.create(this);
            return socket;
        }*/
        throw new RuntimeException("eimpl");
    }

    private static InetSocketAddress getLoopbackAddress(final int port) {
        return new InetSocketAddress(InetAddress.getLoopbackAddress(), port);
    }

    @Override
    public SocketAddress getLocalAddress() throws IOException {
        synchronized (stateLock) {
            if (!isOpen()) {
                throw new ClosedChannelException();
            }
            // Perform security check before returning address
            SecurityManager sm = System.getSecurityManager();
            InetSocketAddress addr = localAddress;
            if (addr == null || sm == null) {
                return addr;
            }

            try {
                sm.checkConnect(addr.getAddress().getHostAddress(), -1);
                // Security check passed
            } catch (SecurityException e) {
                // Return loopback address only if security check fails
                addr = getLoopbackAddress(addr.getPort());
            }
            return addr;
        }
    }

    @Override
    public SocketAddress getRemoteAddress() throws IOException {
        synchronized (stateLock) {
            if (!isOpen()) {
                throw new ClosedChannelException();
            }
            return remoteAddress;
        }
    }

    @Override
    public <T> ECNBitsDatagramChannel setOption(final SocketOption<T> name, final T value) throws IOException {
        if (name == null) {
            throw new NullPointerException();
        }
        if (!supportedOptions().contains(name)) {
            throw new UnsupportedOperationException("'" + name + "' not supported");
        }

        synchronized (stateLock) {
            ensureOpen();

            if (name == StandardSocketOptions.IP_TOS) {
                // options are protocol dependent
                Net.setSocketOption(fd, family, name, value);
                return this;
            }

            // remaining options don't need any special handling
            Net.setSocketOption(fd, Net.UNSPEC, name, value);
            return this;
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getOption(final SocketOption<T> name) throws IOException {
        if (name == null) {
            throw new NullPointerException();
        }
        if (!supportedOptions().contains(name)) {
            throw new UnsupportedOperationException("'" + name + "' not supported");
        }

        synchronized (stateLock) {
            ensureOpen();

            if (name == StandardSocketOptions.IP_TOS) {
                return (T) Net.getSocketOption(fd, family, name);
            }
            // no special handling
            return (T) Net.getSocketOption(fd, Net.UNSPEC, name);
        }
    }

    private static class DefaultOptionsHolder {
        static final Set<SocketOption<?>> defaultOptions = defaultOptions();

        private static Set<SocketOption<?>> defaultOptions() {
            HashSet<SocketOption<?>> set = new HashSet<SocketOption<?>>(5);
            set.add(StandardSocketOptions.SO_SNDBUF);
            set.add(StandardSocketOptions.SO_RCVBUF);
            set.add(StandardSocketOptions.SO_REUSEADDR);
            set.add(StandardSocketOptions.SO_BROADCAST);
            set.add(StandardSocketOptions.IP_TOS);
            return Collections.unmodifiableSet(set);
        }
    }

    @Override
    public final Set<SocketOption<?>> supportedOptions() {
        return ECNBitsDatagramChannelImpl.DefaultOptionsHolder.defaultOptions;
    }

    private void ensureOpen() throws ClosedChannelException {
        if (!isOpen()) {
            throw new ClosedChannelException();
        }
    }

    private SocketAddress localAddress() {
        synchronized (stateLock) {
            return localAddress;
        }
    }

    private SocketAddress remoteAddress() {
        synchronized (stateLock) {
            return remoteAddress;
        }
    }

    private static class ReceiveArgs {
        int fd; // in
        ByteBuffer buf; // in, mutated
        InetSocketAddress sender; // out
    }

    public SocketAddress receive(final ByteBuffer dst) throws IOException {
        if (dst == null) {
            throw new NullPointerException();
        }
        if (dst.isReadOnly()) {
            throw new IllegalArgumentException("Read-only buffer");
        }
        synchronized (readLock) {
            ensureOpen();
            // Socket was not bound before attempting receive
            // Android-changed: Do not implicitly to bind to 0 (or 0.0.0.0), return null instead.
            if (localAddress() == null) {
                return null;
            }
            int n = 0;
            ByteBuffer bb = null;
            final ReceiveArgs args = new ReceiveArgs();
            args.fd = fdVal;
            try {
                begin();
                if (!isOpen()) {
                    return null;
                }
                SecurityManager security = System.getSecurityManager();
                readerThread = NativeThread.current();
                if (isConnected() || (security == null)) {
                    args.buf = dst;
                    do {
                        n = receive(args);
                    } while ((n == IOStatus.INTERRUPTED) && isOpen());
                    if (n == IOStatus.UNAVAILABLE) {
                        return null;
                    }
                } else {
                    bb = ByteBuffer.allocateDirect(dst.remaining());
                    args.buf = bb;
                    for (; ; ) {
                        do {
                            n = receive(args);
                        } while ((n == IOStatus.INTERRUPTED) && isOpen());
                        if (n == IOStatus.UNAVAILABLE) {
                            return null;
                        }
                        try {
                            security.checkAccept(args.sender.getAddress().getHostAddress(),
                              args.sender.getPort());
                        } catch (SecurityException se) {
                            // Ignore packet
                            bb.clear();
                            n = 0;
                            continue;
                        }
                        bb.flip();
                        dst.put(bb);
                        break;
                    }
                }
                return args.sender;
            } finally {
                readerThread = 0;
                end((n > 0) || (n == IOStatus.UNAVAILABLE));
                assert IOStatus.check(n);
            }
        }
    }
}
