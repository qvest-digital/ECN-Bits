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

import lombok.SneakyThrows;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;

/**
 * JNI Java™ side of a reimplementation of datagram I/O with extras
 * (not suitable for use with IP Multicast) and related functionality
 * (such as native thread signalling).
 *
 * @author mirabilos (t.glaser@tarent.de)
 */
final class JNI {
    private JNI() {
    }

    // socket options enum for native code, keep in sync with C code!
    // all supported socket options take an int
    static final int IP_TOS = 0;
    static final int SO_BROADCAST = 1;
    static final int SO_RCVBUF = 2;
    static final int SO_REUSEADDR = 3;
    static final int SO_SNDBUF = 4;

    // return values for error codes, keep in sync with C code!
    // -1 = EOF
    static final int EAVAIL = -2;
    static final int EINTR = -3;
    // -4 = exception thrown in native code, never seen in Java™

    /**
     * JNI representation of an IP address and port tuple, address created by
     * {@link #addr(InetSocketAddress)} and port just used as-is in Java™.
     * Use {@link #get()} to get the Java™ representation of this tuple.
     *
     * @author mirabilos (t.glaser@tarent.de)
     */
    static final class AddrPort {
        /**
         * 16 bytes in network order, v4-mapped or IPv6 address
         */
        byte[] addr;
        /**
         * Port in host order
         */
        int port;

        /**
         * Converts address part to native addr representation.
         *
         * @param isa {@link InetSocketAddress}
         * @return byte[] isa.getAddress() as IPv6 address or v4-mapped
         */
        static byte[] addr(final InetSocketAddress isa) {
            final byte[] ob = isa.getAddress().getAddress();
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

        /**
         * Retrieves address/port tuple in a form usable for Java™
         *
         * @return {@link InetSocketAddress}
         */
        @SneakyThrows(UnknownHostException.class)
        InetSocketAddress get() {
            // v4-mapped → Inet4Address, rest Inet6Address
            return new InetSocketAddress(InetAddress.getByAddress(addr), port);
        }

        static {
            cacheAddrPort();
        }
    }

    private static native void cacheAddrPort();

    /**
     * Scatter/Gather I/O wrapper
     *
     * @author mirabilos (t.glaser@tarent.de)
     */
    static final class SGIO {
        ByteBuffer orig; // original ByteBuffer (not used by JNI)
        ByteBuffer buf; // the one JNI uses (may be = (useDirect) or ≠ (!useDirect) orig)
        int opos; // orig.position(), not used by JNI
        int pos; // position in the buffer (0 if !useDirect)
        int len; // length of the buffer fragment
        boolean useDirect; // whether to directly use orig

        static {
            cacheSGIO();
        }
    }

    private static native void cacheSGIO();

    // +++ OpenJDK NativeThread +++

    static native long gettid();

    static native void signal(long tid);

    // +++ socket operations +++

    static native int n_socket() throws IOException;

    static native void n_close(final int fd) throws IOException;

    static native void n_setnonblock(final int fd,
      final boolean block) throws IOException;

    static native int n_getsockopt(final int fd,
      final int optenum) throws IOException;

    static native void n_setsockopt(final int fd,
      final int optenum, final int value) throws IOException;

    static native void n_getsockname(final int fd,
      final AddrPort ap) throws IOException;

    static native void n_bind(final int fd,
      final byte[] addr, final int port) throws IOException;

    static native void n_connect(final int fd,
      final byte[] addr, final int port) throws IOException;

    // connect() with empty, zero’d struct sockaddr_in6 with sin6_family = AF_UNSPEC
    static native void n_disconnect(final int fd) throws IOException;

    // ap can be nil if the caller is not interested
    static native int n_recv(final int fd,
      final ByteBuffer buf, final int bbpos, final int bbsize,
      final AddrPort ap) throws IOException;

    static native int n_send(final int fd,
      final ByteBuffer buf, final int bbpos, final int bbsize,
      final byte[] addr, final int port) throws IOException;

    // ap is nil here ☻
    static native long n_rd(final int fd,
      final SGIO[] bufs, final int nbufs) throws IOException;

    static native long n_wr(final int fd,
      final SGIO[] bufs, final byte[] addr, final int port) throws IOException;

    // 1 (ok), 0 (timeout), EINTR or THROWN; similar to nativePoll in D.Socket but different rv + throws
    static native int n_poll(final int fd,
      final int timeout) throws IOException;

    // +++ I/O operations +++

    static long ioresult(final long n) {
        return n == EAVAIL ? 0 : n;
    }
}
