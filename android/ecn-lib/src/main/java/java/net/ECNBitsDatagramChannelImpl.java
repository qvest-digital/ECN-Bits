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
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.AlreadyBoundException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.MembershipKey;
import java.nio.channels.NotYetConnectedException;
import java.nio.channels.UnresolvedAddressException;
import java.nio.channels.UnsupportedAddressTypeException;
import java.nio.channels.spi.SelectorProvider;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

class ECNBitsDatagramChannelImpl extends ECNBitsDatagramChannel {
    @SuppressWarnings("UnusedAssignment")
    private int fdVal = -1;

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
    @SuppressWarnings("UnusedAssignment")
    private int state = ST_UNINITIALIZED;

    // Binding
    private InetSocketAddress localAddress;
    private InetSocketAddress remoteAddress;

    // Our socket adaptor, if any
    @SuppressWarnings("unused") //XXX tbd
    private DatagramSocket socket;

    // -- End of fields protected by stateLock

    public ECNBitsDatagramChannelImpl(final SelectorProvider sp) throws IOException {
        super(sp);
        this.fdVal = n_socket();
        this.state = ST_UNCONNECTED;
    }

    @Override
    public /*AbstractECNBits*/DatagramSocket socket() {
        /*synchronized (stateLock) {
            if (socket == null)
                socket = sun.nio.ch.DatagramSocketAdaptor.create(this);
            return socket;
        }*/
        throw new RuntimeException("eimpl");
    }

    @Override
    public SocketAddress getLocalAddress() throws IOException {
        synchronized (stateLock) {
            ensureOpen();
            // Perform security check before returning address
            final SecurityManager sm = System.getSecurityManager();
            InetSocketAddress addr = localAddress;
            if (addr == null || sm == null) {
                return addr;
            }
            try {
                sm.checkConnect(addr.getAddress().getHostAddress(), -1);
                // Security check passed
            } catch (SecurityException e) {
                // Return loopback address only if security check fails
                addr = new InetSocketAddress(InetAddress.getLoopbackAddress(), addr.getPort());
            }
            return addr;
        }
    }

    @Override
    public SocketAddress getRemoteAddress() throws IOException {
        synchronized (stateLock) {
            ensureOpen();
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

    @Override
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
            synchronized (stateLock) {
                if (localAddress == null) {
                    return null;
                }
            }
            int n = 0;
            try {
                begin();
                if (!isOpen()) {
                    return null;
                }
                final SecurityManager security;
                readerThread = NativeThread.current();
                final ReceiveArgs args;
                if (isConnected() || ((security = System.getSecurityManager()) == null)) {
                    args = new ReceiveArgs(dst);
                    do {
                        n = i_recv(args);
                    } while ((n == IOStatus.INTERRUPTED) && isOpen());
                    if (n == IOStatus.UNAVAILABLE) {
                        return null;
                    }
                } else {
                    final ByteBuffer bb = ByteBuffer.allocateDirect(dst.remaining());
                    args = new ReceiveArgs(bb);
                    while (true) {
                        do {
                            n = i_recv(args);
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
            }
        }

	/*
    private int receive(FileDescriptor fd, ByteBuffer dst)
        throws IOException
    {
        int pos = dst.position();
        int lim = dst.limit();
        assert (pos <= lim);
        int rem = (pos <= lim ? lim - pos : 0);
        if (dst instanceof sun.nio.ch.DirectBuffer && rem > 0)
            return receiveIntoNativeBuffer(fd, dst, rem, pos);

        // Substitute a native buffer. If the supplied buffer is empty
        // we must instead use a nonempty buffer, otherwise the call
        // will not block waiting for a datagram on some platforms.
        int newSize = Math.max(rem, 1);
        ByteBuffer bb = ByteBuffer.allocateDirect(newSize);
            int n = receiveIntoNativeBuffer(fd, bb, newSize, 0);
            bb.flip();
            if (n > 0 && rem > 0)
                dst.put(bb);
            return n;
    }

    private int receiveIntoNativeBuffer(FileDescriptor fd, ByteBuffer bb,
                                        int rem, int pos)
        throws IOException
    {
        int n = receive0(fd, ((DirectBuffer)bb).address() + pos, rem,
                         isConnected());
        if (n > 0)
            bb.position(pos + n);
        return n;
    }
	*/
    }

    @Override
    public int send(final ByteBuffer src, final SocketAddress target) throws IOException {
        if (src == null) {
            throw new NullPointerException();
        }
        synchronized (writeLock) {
            ensureOpen();
            final InetSocketAddress isa = netCheckAddress(target);
            synchronized (stateLock) {
                if (isConnected()) {
                    if (!target.equals(remoteAddress)) {
                        throw new IllegalArgumentException("Connected address not equal to target address");
                    }
                    return write_locked(src);
                }
                final SecurityManager sm = System.getSecurityManager();
                if (sm != null) {
                    sm.checkConnect(isa.getAddress().getHostAddress(), isa.getPort());
                }
            }

            int n = 0;
            try {
                begin();
                if (!isOpen()) {
                    return 0;
                }
                writerThread = NativeThread.current();
                do {
                    n = i_send(src, isa);
                } while ((n == IOStatus.INTERRUPTED) && isOpen());

                synchronized (stateLock) {
                    if (isOpen() && (localAddress == null)) {
                        updateLocalAddress();
                    }
                }
                return IOStatus.normalize(n);
            } finally {
                writerThread = 0;
                end((n > 0) || (n == IOStatus.UNAVAILABLE));
            }
        }
	/*
    private int send(FileDescriptor fd, ByteBuffer src, InetSocketAddress target)
        throws IOException
    {
        if (src instanceof sun.nio.ch.DirectBuffer)
            return sendFromNativeBuffer(fd, src, target);

        // Substitute a native buffer
        int pos = src.position();
        int lim = src.limit();
        assert (pos <= lim);
        int rem = (pos <= lim ? lim - pos : 0);

        ByteBuffer bb = ByteBuffer.allocateDirect(rem);
            bb.put(src);
            bb.flip();
            // Do not update src until we see how many bytes were written
            src.position(pos);

            int n = sendFromNativeBuffer(fd, bb, target);
            if (n > 0) {
                // now update src
                src.position(pos + n);
            }
            return n;
    }

    private int sendFromNativeBuffer(FileDescriptor fd, ByteBuffer bb,
                                     InetSocketAddress target)
        throws IOException
    {
        int pos = bb.position();
        int lim = bb.limit();
        assert (pos <= lim);
        int rem = (pos <= lim ? lim - pos : 0);

        boolean preferIPv6 = true;
        int written;
        try {
            written = send0(preferIPv6, fd, ((DirectBuffer)bb).address() + pos,
                            rem, target.getAddress(), target.getPort());
        } catch (PortUnreachableException pue) {
            if (isConnected())
                throw pue;
            written = rem;
        }
        if (written > 0)
            bb.position(pos + written);
        return written;
    }
	*/
    }

    @Override
    public int read(final ByteBuffer buf) throws IOException {
        if (buf == null) {
            throw new NullPointerException();
        }
        synchronized (readLock) {
            synchronized (stateLock) {
                ensureOpen();
                if (!isConnected()) {
                    throw new NotYetConnectedException();
                }
            }
            int n = 0;
            try {
                begin();
                if (!isOpen()) {
                    return 0;
                }
                readerThread = NativeThread.current();
                final ReceiveArgs args = new ReceiveArgs(buf);
                do {
                    n = i_recv(args);
                } while ((n == IOStatus.INTERRUPTED) && isOpen());
                return IOStatus.normalize(n);
            } finally {
                readerThread = 0;
                end((n > 0) || (n == IOStatus.UNAVAILABLE));
            }
        }
    }

    @Override
    public long read(final ByteBuffer[] dsts, final int offset, final int length) throws IOException {
        if ((offset < 0) || (length < 0) || (offset > dsts.length - length)) {
            throw new IndexOutOfBoundsException();
        }
        throw new IOException("scatter/gather I/O not yet implemented");
        /*synchronized (readLock) {
            synchronized (stateLock) {
                ensureOpen();
                if (!isConnected()) {
                    throw new NotYetConnectedException();
                }
            }
            long n = 0;
            try {
                begin();
                if (!isOpen()) {
                    return 0;
                }
                readerThread = NativeThread.current();
                do {
                    n = IOUtil.read(fd, dsts, offset, length, nd);
                } while ((n == IOStatus.INTERRUPTED) && isOpen());
                return IOStatus.normalize(n);
            } finally {
                readerThread = 0;
                end((n > 0) || (n == IOStatus.UNAVAILABLE));
                assert IOStatus.check(n);
            }
        }*/
    }

    @Override
    public int write(final ByteBuffer buf) throws IOException {
        if (buf == null) {
            throw new NullPointerException();
        }
        synchronized (writeLock) {
            synchronized (stateLock) {
                ensureOpen();
                if (!isConnected()) {
                    throw new NotYetConnectedException();
                }
            }
            return write_locked(buf);
        }
    }

    private int write_locked(final ByteBuffer buf) throws IOException {
        int n = 0;
        try {
            begin();
            if (!isOpen()) {
                return 0;
            }
            writerThread = NativeThread.current();
            do {
                n = i_send(buf, remoteAddress);
            } while ((n == IOStatus.INTERRUPTED) && isOpen());
            return IOStatus.normalize(n);
        } finally {
            writerThread = 0;
            end((n > 0) || (n == IOStatus.UNAVAILABLE));
        }
    }

    @Override
    public long write(final ByteBuffer[] srcs, final int offset, final int length) throws IOException {
        if ((offset < 0) || (length < 0) || (offset > srcs.length - length)) {
            throw new IndexOutOfBoundsException();
        }
        throw new IOException("scatter/gather I/O not yet implemented");
        /*synchronized (writeLock) {
            synchronized (stateLock) {
                ensureOpen();
                if (!isConnected()) {
                    throw new NotYetConnectedException();
                }
            }
            long n = 0;
            try {
                begin();
                if (!isOpen()) {
                    return 0;
                }
                writerThread = NativeThread.current();
                do {
                    n = IOUtil.write(fd, srcs, offset, length, nd);
                } while ((n == IOStatus.INTERRUPTED) && isOpen());
                return IOStatus.normalize(n);
            } finally {
                writerThread = 0;
                end((n > 0) || (n == IOStatus.UNAVAILABLE));
                assert IOStatus.check(n);
            }
        }*/
    }

    @Override
    public ECNBitsDatagramChannel bind(final SocketAddress local) throws IOException {
        synchronized (readLock) {
            synchronized (writeLock) {
                synchronized (stateLock) {
                    ensureOpen();
                    if (localAddress != null) {
                        throw new AlreadyBoundException();
                    }
                    final InetSocketAddress isa = local == null ?
                      new InetSocketAddress(0) : netCheckAddress(local);
                    SecurityManager sm = System.getSecurityManager();
                    if (sm != null) {
                        sm.checkListen(isa.getPort());
                    }
                    n_bind(fdVal, toaddr(isa.getAddress()), isa.getPort());
                    updateLocalAddress();
                }
            }
        }
        return this;
    }

    @Override
    public boolean isConnected() {
        synchronized (stateLock) {
            return (state == ST_CONNECTED);
        }
    }

    @Override
    public ECNBitsDatagramChannel connect(final SocketAddress sa) throws IOException {
        synchronized (readLock) {
            synchronized (writeLock) {
                synchronized (stateLock) {
                    ensureOpen();
                    if (state != ST_UNCONNECTED) {
                        throw new IllegalStateException("Connect already invoked");
                    }
                    final InetSocketAddress isa = netCheckAddress(sa);
                    final SecurityManager sm = System.getSecurityManager();
                    if (sm != null) {
                        sm.checkConnect(isa.getAddress().getHostAddress(), isa.getPort());
                    }
                    n_connect(fdVal, toaddr(isa.getAddress()), isa.getPort());

                    // Connection succeeded; disallow further invocation
                    state = ST_CONNECTED;
                    remoteAddress = isa;
                    //cachedSenderInetAddress = isa.getAddress();
                    //cachedSenderPort = isa.getPort();

                    // set or refresh local address
                    updateLocalAddress();

                    // flush any packets already received.
                    boolean blocking = false;
                    synchronized (blockingLock()) {
                        try {
                            blocking = isBlocking();
                            // remainder of each packet thrown away
                            ByteBuffer tmpBuf = ByteBuffer.allocate(1);
                            if (blocking) {
                                configureBlocking(false);
                            }
                            do {
                                tmpBuf.clear();
                            } while (receive(tmpBuf) != null);
                        } finally {
                            if (blocking) {
                                configureBlocking(true);
                            }
                        }
                    }
                }
            }
        }
        return this;
    }

    @Override
    public ECNBitsDatagramChannel disconnect() throws IOException {
        synchronized (readLock) {
            synchronized (writeLock) {
                synchronized (stateLock) {
                    if (!isConnected() || !isOpen()) {
                        return this;
                    }
                    final SecurityManager sm = System.getSecurityManager();
                    if (sm != null) {
                        sm.checkConnect(remoteAddress.getAddress().getHostAddress(), remoteAddress.getPort());
                    }
                    n_disconnect(fdVal);
                    remoteAddress = null;
                    state = ST_UNCONNECTED;

                    // refresh local address
                    updateLocalAddress();
                }
            }
        }
        return this;
    }

    @Override
    public MembershipKey join(final InetAddress group, final NetworkInterface interf) {
        throw new UnsupportedOperationException("ECNBitsDatagramChannel not suitable for Multicast");
    }

    @Override
    public MembershipKey join(final InetAddress group, final NetworkInterface interf, final InetAddress source) {
        throw new UnsupportedOperationException("ECNBitsDatagramChannel not suitable for Multicast");
    }

    @Override
    protected void implCloseSelectableChannel() throws IOException {
        synchronized (stateLock) {
            long th;
            if ((th = readerThread) != 0) {
                NativeThread.signal(th);
            }
            if ((th = writerThread) != 0) {
                NativeThread.signal(th);
            }
            if (!isRegistered()) {
                if (state == ST_KILLED) {
                    return;
                }
                if (state == ST_UNINITIALIZED) {
                    state = ST_KILLED;
                    return;
                }
                n_close(fdVal);
                fdVal = -1;
                state = ST_KILLED;
            }
        }
    }

    @Override
    protected void implConfigureBlocking(final boolean block) throws IOException {
        n_setnonblock(fdVal, block);
    }

    @Override
    protected void finalize() throws Throwable {
        try {
            if (fdVal != -1) {
                n_close(fdVal);
                fdVal = -1;
            }
        } finally {
            super.finalize();
        }
    }

    private static class NativeAddrPort {
        byte[] addr;
        int port;
    }

    private static native int n_socket() throws IOException;

    private static native void n_close(final int fd) throws IOException;

    private static native void n_setnonblock(final int fd,
      final boolean block) throws IOException;

    private static native void n_getsockname(final int fd,
      final NativeAddrPort ap) throws IOException;

    private static native void n_bind(final int fd,
      final byte[] addr, final int port) throws IOException;

    private static native void n_connect(final int fd,
      final byte[] addr, final int port) throws IOException;

    // connect() with empty, zero’d struct sockaddr_in6 with sin6_family = AF_UNSPEC
    private static native void n_disconnect(final int fd) throws IOException;

    private static native int n_recv(final int fd,
      final ByteBuffer buf, final NativeAddrPort ap) throws IOException;

    private static native int n_send(final int fd,
      final ByteBuffer buf, final byte[] addr, final int port) throws IOException;

    private static InetSocketAddress netCheckAddress(final SocketAddress sa) {
        if (sa == null) {
            throw new IllegalArgumentException("sa == null");
        }
        if (!(sa instanceof InetSocketAddress)) {
            throw new UnsupportedAddressTypeException();
        }
        InetSocketAddress isa = (InetSocketAddress) sa;
        if (isa.isUnresolved()) {
            throw new UnresolvedAddressException();
        }
        InetAddress addr = isa.getAddress();
        if (!(addr instanceof Inet4Address || addr instanceof Inet6Address)) {
            throw new IllegalArgumentException("Invalid address type");
        }
        if (addr.isMulticastAddress()) {
            throw new UnsupportedOperationException("ECNBitsDatagramChannel not suitable for Multicast");
        }
        return isa;
    }

    private static byte[] toaddr(final InetAddress ia) {
        final byte[] ob = ia.getAddress();
        if (ob.length == 16) {
            return ob;
        }
        final byte[] nb = new byte[16];
        nb[10] = (byte) 0xFF;
        nb[11] = (byte) 0xFF;
        nb[12] = ob[0];
        nb[13] = ob[1];
        nb[14] = ob[2];
        nb[15] = ob[3];
        return nb;
    }

    @SneakyThrows(UnknownHostException.class)
    private static InetAddress fromaddr(final byte[] ia) {
        // v4-mapped → Inet4Address, rest Inet6Address
        return InetAddress.getByAddress(ia);
    }

    private void updateLocalAddress() throws IOException {
        final NativeAddrPort ap = new NativeAddrPort();
        n_getsockname(fdVal, ap);
        localAddress = new InetSocketAddress(fromaddr(ap.addr), ap.port);
    }

    private int i_send(final ByteBuffer buf, final InetSocketAddress target) throws IOException {
        return n_send(fdVal, buf, toaddr(target.getAddress()), target.getPort());
    }

    @RequiredArgsConstructor
    private static class ReceiveArgs {
        final ByteBuffer buf; // in, mutated
        InetSocketAddress sender; // out
    }

    private int i_recv(final ReceiveArgs args) throws IOException {
        final NativeAddrPort ap = new NativeAddrPort();
        final int rv = n_recv(fdVal, args.buf, ap);
        if (rv >= 0) {
            args.sender = new InetSocketAddress(fromaddr(ap.addr), ap.port);
        }
        return rv;
    }
}
