package de.telekom.llcto.ecn_bits.jdk.jni;

/*-
 * Copyright © 2020
 *      mirabilos <t.glaser@tarent.de>
 * Copyright © 2014
 *      The Android Open Source Project
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
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.SocketOption;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.AlreadyBoundException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.MembershipKey;
import java.nio.channels.NotYetConnectedException;
import java.nio.channels.UnresolvedAddressException;
import java.nio.channels.UnsupportedAddressTypeException;
import java.nio.channels.spi.SelectorProvider;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static de.telekom.llcto.ecn_bits.jdk.jni.JNI.*;

/**
 * Java™ side of a JNI reimplementation of a NIO datagram channel with extras.
 * Not suitable for use with IP Multicast.
 *
 * @author mirabilos (t.glaser@tarent.de)
 */
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

    // Our socket adapter, if any
    private AbstractECNBitsDatagramSocket socket;

    // -- End of fields protected by stateLock

    private final ECNMeasurer tcm = new ECNMeasurer();

    public ECNBitsDatagramChannelImpl(final SelectorProvider sp) throws IOException {
        super(sp);
        this.fdVal = n_socket();
        this.state = ST_UNCONNECTED;
    }

    @Override
    public AbstractECNBitsDatagramSocket socket() {
        synchronized (stateLock) {
            if (socket == null) {
                socket = new ECNBitsDatagramSocketAdapter(this);
            }
            return socket;
        }
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
        final int opt = DefaultOptionsHolder.optenum(name);
        final int val = DefaultOptionsHolder.to(name, opt, value);
        switch (opt) {
        case JNI.IP_TOS:
            if (val < 0 || val > 255) {
                throw new IllegalArgumentException("Invalid IP_TOS value");
            }
            break;
        case JNI.SO_RCVBUF:
        case JNI.SO_SNDBUF:
            if (val < 0) {
                throw new IllegalArgumentException("Invalid send/receive buffer size");
            }
            break;
        }

        synchronized (stateLock) {
            ensureOpen();
            n_setsockopt(fdVal, opt, val);
            return this;
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getOption(final SocketOption<T> name) throws IOException {
        final int opt = DefaultOptionsHolder.optenum(name);
        synchronized (stateLock) {
            ensureOpen();
            return (T) (Integer) n_getsockopt(fdVal, opt);
        }
    }

    static class DefaultOptionsHolder {
        static final Map<SocketOption<?>, Integer> defaultMap;
        static final Set<SocketOption<?>> defaultOptions;

        static {
            final HashMap<SocketOption<?>, Integer> map = new HashMap<>(5);
            map.put(StandardSocketOptions.SO_SNDBUF, JNI.SO_SNDBUF); // Integer
            map.put(StandardSocketOptions.SO_RCVBUF, JNI.SO_RCVBUF); // Integer
            map.put(StandardSocketOptions.SO_REUSEADDR, JNI.SO_REUSEADDR); // Boolean
            map.put(StandardSocketOptions.SO_BROADCAST, JNI.SO_BROADCAST); // Boolean
            map.put(StandardSocketOptions.IP_TOS, JNI.IP_TOS); // Integer
            defaultMap = Collections.unmodifiableMap(map);
            defaultOptions = Collections.unmodifiableSet(map.keySet());
        }

        static int to(SocketOption<?> option, int opt, Object value) throws SocketException {
            if (value == null) {
                throw new NullPointerException();
            }
            switch (opt) {
            case JNI.SO_REUSEADDR:
            case JNI.SO_BROADCAST:
                if (!(value instanceof Boolean)) {
                    throw new SocketException("Bad argument for " + option +
                      ": expected Boolean, got " + value.getClass().getSimpleName());
                }
                return ((Boolean) value) ? 1 : 0;
            case JNI.SO_SNDBUF:
            case JNI.SO_RCVBUF:
            case JNI.IP_TOS:
                if (!(value instanceof Integer)) {
                    throw new SocketException("Bad argument for " + option +
                      ": expected Integer, got " + value.getClass().getSimpleName());
                }
                return ((Integer) value);
            default:
                throw new SocketException("unexpected socket option " + option +
                  " (" + opt + ")");
            }
        }

        static int optenum(SocketOption<?> option) {
            if (option == null) {
                throw new NullPointerException();
            }
            final Integer rv = defaultMap.get(option);
            if (rv == null) {
                throw new UnsupportedOperationException("'" + option + "' not supported");
            }
            return rv;
        }
    }

    @Override
    public final Set<SocketOption<?>> supportedOptions() {
        return DefaultOptionsHolder.defaultOptions;
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
                readerThread = JNI.n_gettid();
                final JNI.AddrPort ap = new JNI.AddrPort();
                InetSocketAddress sender;
                if (isConnected() || ((security = System.getSecurityManager()) == null)) {
                    do {
                        n = i_recv(dst, ap);
                    } while ((n == JNI.EINTR) && isOpen());
                    if (n == JNI.EAVAIL) {
                        return null;
                    }
                    sender = ap.get();
                } else {
                    final ByteBuffer bb = ByteBuffer.allocateDirect(dst.remaining());
                    while (true) {
                        do {
                            n = i_recv(bb, ap);
                        } while ((n == JNI.EINTR) && isOpen());
                        if (n == JNI.EAVAIL) {
                            return null;
                        }
                        sender = ap.get();
                        if (sender == null) {
                            break;
                        }
                        try {
                            security.checkAccept(sender.getAddress().getHostAddress(), sender.getPort());
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
                return sender;
            } finally {
                readerThread = 0;
                end((n > 0) || (n == JNI.EAVAIL));
            }
        }
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
                smConnect(isa);
            }

            long n = 0;
            try {
                begin();
                if (!isOpen()) {
                    return 0;
                }
                writerThread = JNI.n_gettid();
                do {
                    n = i_send(src, isa);
                } while ((n == JNI.EINTR) && isOpen());

                synchronized (stateLock) {
                    if (isOpen() && (localAddress == null)) {
                        updateLocalAddress();
                    }
                }
                return (int) ioresult(n);
            } finally {
                writerThread = 0;
                end((n > 0) || (n == JNI.EAVAIL));
            }
        }
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
            long n = 0;
            try {
                begin();
                if (!isOpen()) {
                    return 0;
                }
                readerThread = JNI.n_gettid();
                do {
                    n = i_recv(buf, new JNI.AddrPort());
                } while ((n == JNI.EINTR) && isOpen());
                return (int) ioresult(n);
            } finally {
                readerThread = 0;
                end((n > 0) || (n == JNI.EAVAIL));
            }
        }
    }

    @Override
    public long read(final ByteBuffer[] dsts, final int offset, final int length) throws IOException {
        if ((offset < 0) || (length < 0) || (offset > dsts.length - length)) {
            throw new IndexOutOfBoundsException();
        }
        synchronized (readLock) {
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
                readerThread = JNI.n_gettid();
                do {
                    n = sg_rd(dsts, offset, length);
                } while ((n == JNI.EINTR) && isOpen());
                return ioresult(n);
            } finally {
                readerThread = 0;
                end((n > 0) || (n == JNI.EAVAIL));
            }
        }
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
        long n = 0;
        try {
            begin();
            if (!isOpen()) {
                return 0;
            }
            writerThread = JNI.n_gettid();
            do {
                n = i_send(buf, remoteAddress);
            } while ((n == JNI.EINTR) && isOpen());
            return (int) ioresult(n);
        } finally {
            writerThread = 0;
            end((n > 0) || (n == JNI.EAVAIL));
        }
    }

    @Override
    public long write(final ByteBuffer[] srcs, final int offset, final int length) throws IOException {
        if ((offset < 0) || (length < 0) || (offset > srcs.length - length)) {
            throw new IndexOutOfBoundsException();
        }
        synchronized (writeLock) {
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
                writerThread = JNI.n_gettid();
                do {
                    n = sg_wr(srcs, offset, length);
                } while ((n == JNI.EINTR) && isOpen());
                return ioresult(n);
            } finally {
                writerThread = 0;
                end((n > 0) || (n == JNI.EAVAIL));
            }
        }
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
                    n_bind(fdVal, JNI.AddrPort.addr(isa), isa.getPort(), JNI.AddrPort.scopeId(isa));
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
                    smConnect(isa);
                    n_connect(fdVal, JNI.AddrPort.addr(isa), isa.getPort(), JNI.AddrPort.scopeId(isa));

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
                            ByteBuffer tmpBuf = ByteBuffer.allocateDirect(1);
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
                    smConnect(remoteAddress);
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
                JNI.n_sigtid(th);
            }
            if ((th = writerThread) != 0) {
                JNI.n_sigtid(th);
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

    @SuppressWarnings("deprecation")
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

    private static void smConnect(final InetSocketAddress isa) {
        final SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkConnect(isa.getAddress().getHostAddress(), isa.getPort());
        }
    }

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

    private void updateLocalAddress() throws IOException {
        final JNI.AddrPort ap = new JNI.AddrPort();
        n_getsockname(fdVal, ap);
        localAddress = ap.get();
    }

    private int i_send(final ByteBuffer buf, final InetSocketAddress target) throws IOException {
        final int pos = buf.position();
        final int lim = buf.limit();
        final int rem = pos <= lim ? lim - pos : 0;

        final ByteBuffer bb;
        final int bpos;

        if (buf.isDirect()) {
            bpos = pos;
            bb = buf;
        } else {
            bpos = 0; // in newly-created bb
            bb = ByteBuffer.allocateDirect(rem);
            // copy data to be sent into bb
            bb.put(buf);
            // revert change to position in source buffer
            buf.position(pos);
            // switch bb into read mode
            bb.flip();
        }

        // send from bb
        final int n = n_send(fdVal, bb, bpos, rem,
          JNI.AddrPort.addr(target), target.getPort(), JNI.AddrPort.scopeId(target));
        // update source buffer accordingly
        if (n > 0) {
            buf.position(pos + n);
        }

        return n;
    }

    private int i_recv(final ByteBuffer dst, final JNI.AddrPort ap) throws IOException {
        tcm.listen();

        final int pos = dst.position();
        final int lim = dst.limit();
        final int rem = pos <= lim ? lim - pos : 0;
        final boolean useDirect = dst.isDirect() && rem > 0;

        final ByteBuffer bb;
        final int bpos;
        final int blen;

        if (useDirect) {
            bpos = pos;
            blen = rem;
            bb = dst;
        } else {
            bpos = 0; // in newly-created bb
            blen = Math.max(rem, 1); // always read at least one byte
            bb = ByteBuffer.allocateDirect(blen);
        }

        final int n = n_recv(fdVal, bb, bpos, blen, ap, isConnected());
        if (n > 0) {
            tcm.received(ap.tcValid, ap.tc);
            bb.position(bpos + n);
        }
        if (!useDirect && n > 0 && rem > 0) {
            bb.flip();
            dst.put(bb);
        }

        return n;
    }

    private long sg_wr(final ByteBuffer[] bufs, final int buf0, final int bufn) throws IOException {
        final JNI.SGIO[] bbs = new JNI.SGIO[bufn];

        for (int i = 0; i < bufn; ++i) {
            final JNI.SGIO nb = new JNI.SGIO();
            final ByteBuffer buf = bufs[buf0 + i];
            final int pos = buf.position();
            final int lim = buf.limit();
            final int rem = pos <= lim ? lim - pos : 0;
            nb.orig = buf;
            nb.opos = pos;
            if (buf.isDirect()) {
                nb.pos = pos;
                nb.buf = buf;
            } else {
                nb.pos = 0; // in newly-created bb
                final ByteBuffer bb = ByteBuffer.allocateDirect(rem);
                // copy data to be sent into bb
                bb.put(buf);
                // revert change to position in source buffer
                buf.position(pos);
                // switch bb into read mode
                bb.flip();
                nb.buf = bb;
            }
            nb.len = rem;
            bbs[i] = nb;
        }

        final long n = n_wr(fdVal, bbs,
          JNI.AddrPort.addr(remoteAddress), remoteAddress.getPort(), JNI.AddrPort.scopeId(remoteAddress));
        if (n > 0) {
            long rest = n;
            for (int i = 0; i < bufn; ++i) {
                final long nb = Math.min(rest, bbs[i].len);
                if (nb < 1) {
                    break;
                }
                bbs[i].orig.position(bbs[i].opos + (int) nb);
                rest -= nb;
            }
        }

        return n;
    }

    private long sg_rd(final ByteBuffer[] bufs, final int buf0, final int bufn) throws IOException {
        tcm.listen();
        final JNI.SGIO[] bbs = new JNI.SGIO[bufn];

        int nbbs = 0;
        for (int i = 0; i < bufn; ++i) {
            final ByteBuffer dst = bufs[buf0 + i];
            final int pos = dst.position();
            final int lim = dst.limit();
            final int rem = pos <= lim ? lim - pos : 0;
            if (rem < 1) {
                continue;
            }
            final JNI.SGIO nb = new JNI.SGIO();
            nb.orig = dst;
            nb.opos = pos;
            nb.useDirect = dst.isDirect();
            if (nb.useDirect) {
                nb.pos = pos;
                nb.buf = dst;
            } else {
                nb.pos = 0; // in newly-created bb
                nb.buf = ByteBuffer.allocateDirect(rem);
            }
            nb.len = rem;
            bbs[nbbs++] = nb;
        }
        if (nbbs < 1) {
            return 0L;
        }

        final JNI.AddrPort tc = new JNI.AddrPort();
        final long n = n_rd(fdVal, bbs, nbbs, tc);
        if (n > 0) {
            tcm.received(tc.tcValid, tc.tc);

            long rest = n;
            for (int i = 0; i < bufn; ++i) {
                final long nb = Math.min(rest, bbs[i].len);
                if (nb < 1) {
                    break;
                }
                bbs[i].buf.position(bbs[i].pos + (int) nb);
                if (!bbs[i].useDirect) {
                    bbs[i].buf.flip();
                    bbs[i].orig.put(bbs[i].buf);
                }
                rest -= nb;
            }
        }

        return n;
    }

    // for ECNBitsDatagramSocketAdapter

    SocketAddress i_localAddress() {
        synchronized (stateLock) {
            return localAddress;
        }
    }

    SocketAddress i_remoteAddress() {
        synchronized (stateLock) {
            return remoteAddress;
        }
    }

    // for {@link ECNBitsDatagramSocketAdapter#receive(ByteBuffer)}
    int pollin(int timeout) throws IOException {
        synchronized (readLock) {
            int n = 0;
            try {
                begin();
                synchronized (stateLock) {
                    if (!isOpen()) {
                        return 0;
                    }
                    readerThread = JNI.n_gettid();
                }
                n = n_pollin(fdVal, timeout);
            } finally {
                readerThread = 0;
                end(n > 0);
            }
            return n;
        }
    }

    // ECN bits and friends

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
