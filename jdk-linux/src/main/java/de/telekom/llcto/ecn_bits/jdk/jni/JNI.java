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

import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.java.Log;

import java.io.IOException;
import java.net.BindException;
import java.net.ConnectException;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NoRouteToHostException;
import java.net.PortUnreachableException;
import java.net.ProtocolException;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.logging.Level;

/**
 * JNI Java™ side of a reimplementation of datagram I/O with extras
 * (not suitable for use with IP Multicast) and related functionality
 * (such as native thread signalling).
 *
 * @author mirabilos (t.glaser@tarent.de)
 */
@Log
final class JNI {
    private JNI() {
    }

    static {
        System.loadLibrary("ecnbits-jdkjni");
    }

    // socket options enum for native code, keep in sync with C code!
    // all supported socket options take an int
    static final int IP_TOS = 0;
    static final int SO_BROADCAST = 1;
    static final int SO_RCVBUF = 2;
    static final int SO_REUSEADDR = 3;
    static final int SO_SNDBUF = 4;
    static final int IPV6_MULTICAST_HOPS = 5;

    // return values for error codes, keep in sync with C code!
    // -1 = EOF
    /**
     * EAGAIN, EWOULDBLOCK
     */
    static final int EAVAIL = -2;
    static final int EINTR = -3;
    // -4 = exception thrown in native code, never seen in Java™

    static String renderNativeExceptionMessage(final String file, final int line, final String func,
      final String msg, final String str) {
        final StringBuilder sb = new StringBuilder(128);
        sb.append(file).append(':').append(line).append(':').append(func).append("(): ");
        if (msg != null) {
            sb.append(msg).append(": ");
        }
        sb.append(str);
        return sb.toString();
    }

    static void prependNativeStackTrace(final Throwable t,
      final String file, final int line, final String func) {
        StackTraceElement[] currentStack = t.getStackTrace();
        StackTraceElement[] newStack = new StackTraceElement[currentStack.length + 1];
        System.arraycopy(currentStack, 0, newStack, 1, currentStack.length);
        newStack[0] = new StackTraceElement("<native>", func, file, line);
        t.setStackTrace(newStack);
    }

    /**
     * Represents an error thrown in native code from ECN-Bits {@link JNI}.
     * This one is an {@link IOException}.
     *
     * @author mirabilos (t.glaser@tarent.de)
     */
    @Getter
    public static class ErrnoException extends IOException {
        private static final long serialVersionUID = 4796003619902398102L;

        final int errno;
        final String strerror;
        final String failureDescription;

        ErrnoException(final String file, final int line, final String func,
          final String msg, final int err, final String str, final Throwable cause) {
            super(renderNativeExceptionMessage(file, line, func, msg, str), cause);
            errno = err;
            strerror = str;
            failureDescription = msg;
            prependNativeStackTrace(this, file, line, func);
        }
    }

    /**
     * Represents an error thrown in native code from ECN-Bits {@link JNI}.
     * This one is a {@link SocketException}.
     *
     * @author mirabilos (t.glaser@tarent.de)
     */
    @Getter
    public static class ErrnoSocketException extends SocketException {
        private static final long serialVersionUID = 765509932782664221L;

        final int errno;
        final String strerror;
        final String failureDescription;

        ErrnoSocketException(final String file, final int line, final String func,
          final String msg, final int err, final String str, final Throwable cause) {
            super(renderNativeExceptionMessage(file, line, func, msg, str));
            if (cause != null) {
                LOG.log(Level.SEVERE, "Due to Android API limitations, " +
                  ErrnoSocketException.class.getSimpleName() +
                  "(" + getMessage() + ") lost cause:", cause);
            }
            errno = err;
            strerror = str;
            failureDescription = msg;
            prependNativeStackTrace(this, file, line, func);
        }
    }

    /**
     * Represents an error thrown in native code from ECN-Bits {@link JNI}.
     * This one is a {@link ProtocolException}.
     *
     * @author mirabilos (t.glaser@tarent.de)
     */
    @Getter
    public static class ErrnoProtocolException extends ProtocolException {
        private static final long serialVersionUID = -1983205541360443188L;

        final int errno;
        final String strerror;
        final String failureDescription;

        ErrnoProtocolException(final String file, final int line, final String func,
          final String msg, final int err, final String str, final Throwable cause) {
            super(renderNativeExceptionMessage(file, line, func, msg, str));
            if (cause != null) {
                LOG.log(Level.SEVERE, "Due to Android API limitations, " +
                  ErrnoProtocolException.class.getSimpleName() +
                  "(" + getMessage() + ") lost cause:", cause);
            }
            errno = err;
            strerror = str;
            failureDescription = msg;
            prependNativeStackTrace(this, file, line, func);
        }
    }

    /**
     * Represents an error thrown in native code from ECN-Bits {@link JNI}.
     * This one is a {@link ConnectException}.
     *
     * @author mirabilos (t.glaser@tarent.de)
     */
    @Getter
    public static class ErrnoConnectException extends ConnectException {
        private static final long serialVersionUID = 3188126153655642133L;

        final int errno;
        final String strerror;
        final String failureDescription;

        ErrnoConnectException(final String file, final int line, final String func,
          final String msg, final int err, final String str, final Throwable cause) {
            super(renderNativeExceptionMessage(file, line, func, msg, str));
            if (cause != null) {
                LOG.log(Level.SEVERE, "Due to Android API limitations, " +
                  ErrnoConnectException.class.getSimpleName() +
                  "(" + getMessage() + ") lost cause:", cause);
            }
            errno = err;
            strerror = str;
            failureDescription = msg;
            prependNativeStackTrace(this, file, line, func);
        }
    }

    /**
     * Represents an error thrown in native code from ECN-Bits {@link JNI}.
     * This one is a {@link NoRouteToHostException}.
     *
     * @author mirabilos (t.glaser@tarent.de)
     */
    @Getter
    public static class ErrnoNoRouteToHostException extends NoRouteToHostException {
        private static final long serialVersionUID = 4471447243574037695L;

        final int errno;
        final String strerror;
        final String failureDescription;

        ErrnoNoRouteToHostException(final String file, final int line, final String func,
          final String msg, final int err, final String str, final Throwable cause) {
            super(renderNativeExceptionMessage(file, line, func, msg, str));
            if (cause != null) {
                LOG.log(Level.SEVERE, "Due to Android API limitations, " +
                  ErrnoNoRouteToHostException.class.getSimpleName() +
                  "(" + getMessage() + ") lost cause:", cause);
            }
            errno = err;
            strerror = str;
            failureDescription = msg;
            prependNativeStackTrace(this, file, line, func);
        }
    }

    /**
     * Represents an error thrown in native code from ECN-Bits {@link JNI}.
     * This one is a {@link BindException}.
     *
     * @author mirabilos (t.glaser@tarent.de)
     */
    @Getter
    public static class ErrnoBindException extends BindException {
        private static final long serialVersionUID = -6833794700911991286L;

        final int errno;
        final String strerror;
        final String failureDescription;

        ErrnoBindException(final String file, final int line, final String func,
          final String msg, final int err, final String str, final Throwable cause) {
            super(renderNativeExceptionMessage(file, line, func, msg, str));
            if (cause != null) {
                LOG.log(Level.SEVERE, "Due to Android API limitations, " +
                  ErrnoBindException.class.getSimpleName() +
                  "(" + getMessage() + ") lost cause:", cause);
            }
            errno = err;
            strerror = str;
            failureDescription = msg;
            prependNativeStackTrace(this, file, line, func);
        }
    }

    /**
     * Represents an error thrown in native code from ECN-Bits {@link JNI}.
     * This one is a {@link PortUnreachableException}.
     *
     * @author mirabilos (t.glaser@tarent.de)
     */
    @Getter
    public static class ErrnoPortUnreachableException extends PortUnreachableException {
        private static final long serialVersionUID = 83407582898923649L;

        final int errno;
        final String strerror;
        final String failureDescription;

        ErrnoPortUnreachableException(final String file, final int line, final String func,
          final String msg, final int err, final String str, final Throwable cause) {
            super(renderNativeExceptionMessage(file, line, func, msg, str));
            if (cause != null) {
                LOG.log(Level.SEVERE, "Due to Android API limitations, " +
                  ErrnoPortUnreachableException.class.getSimpleName() +
                  "(" + getMessage() + ") lost cause:", cause);
            }
            errno = err;
            strerror = str;
            failureDescription = msg;
            prependNativeStackTrace(this, file, line, func);
        }
    }

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
         * IPv6 scope ID (numeric); -1 if not set
         */
        int scopeId = -1;

        // not part of AddrPort but provided by the receiving JNI calls
        byte tc; // out
        boolean tcValid; // out

        /**
         * Converts address part to native addr representation.
         *
         * @param isa {@link InetSocketAddress} or {@link InetAddress}
         * @return byte[16] isa.getAddress() as IPv6 address or v4-mapped
         */
        static byte[] addr(final InetSocketAddress isa) {
            return addr(isa.getAddress());
        }

        /**
         * Converts address part to native addr representation.
         *
         * @param ia {@link InetSocketAddress} or {@link InetAddress}
         * @return byte[16] isa.getAddress() as IPv6 address or v4-mapped
         */
        @SneakyThrows(UnknownHostException.class)
        static byte[] addr(final InetAddress ia) {
            final byte[] ob = ia.getAddress();
            if (ob.length == 16) {
                return ob;
            }
            if (ob.length != 4) {
                /* NOTREACHED */
                throw new UnknownHostException("addr is of illegal length");
            }
            final byte[] nb = new byte[16];
            if (ob[0] == 0 && ob[1] == 0 && ob[2] == 0 && ob[3] == 0) {
                // INADDR_ANY, map to IN6ADDR_ANY
                return nb;
            }
            nb[10] = (byte) 0xFF;
            nb[11] = (byte) 0xFF;
            nb[12] = ob[0];
            nb[13] = ob[1];
            nb[14] = ob[2];
            nb[15] = ob[3];
            return nb;
        }

        /**
         * Retrieves the IPv6 scope ID of an IP address
         *
         * @param isa {@link InetSocketAddress} or {@link InetAddress}
         * @return scope ID (-1 if not set or null)
         */
        static int scopeId(final SocketAddress isa) {
            return isa instanceof InetSocketAddress ? scopeId(((InetSocketAddress) isa).getAddress()) : -1;
        }

        /**
         * Retrieves the IPv6 scope ID of an IP address
         *
         * @param ia {@link InetSocketAddress} or {@link InetAddress}
         * @return scope ID (-1 if not set or null)
         */
        static int scopeId(final InetAddress ia) {
            return ia instanceof Inet6Address ? ((Inet6Address) ia).getScopeId() : -1;
        }

        /**
         * Checks whether this 16-byte address is a v4-mapped IPv6 address.
         *
         * @return true if it is a v4-mapped address
         */
        private boolean isIPv4MappedAddress() {
            return (addr[0] == 0x00) && (addr[1] == 0x00) &&
              (addr[2] == 0x00) && (addr[3] == 0x00) &&
              (addr[4] == 0x00) && (addr[5] == 0x00) &&
              (addr[6] == 0x00) && (addr[7] == 0x00) &&
              (addr[8] == 0x00) && (addr[9] == 0x00) &&
              (addr[10] == (byte) 0xFF) &&
              (addr[11] == (byte) 0xFF);
        }

        /**
         * Retrieves address in a form usable for Java™
         *
         * @return {@link Inet4Address} or (possibly scoped) {@link Inet6Address}
         */
        @SneakyThrows(UnknownHostException.class)
        InetAddress getAddr() {
            if (addr.length == 16 && (scopeId != -1 || !isIPv4MappedAddress())) {
                return Inet6Address.getByAddress(null, addr, scopeId);
            }
            return InetAddress.getByAddress(addr);
        }

        /**
         * Retrieves address/scope/port tuple in a form usable for Java™
         *
         * @return {@link InetSocketAddress} or null if no sender (channel closed or so)
         */
        InetSocketAddress get() {
            if (addr == null) {
                return null;
            }
            // v4-mapped → Inet4Address, rest Inet6Address
            return new InetSocketAddress(getAddr(), port);
        }
    }

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
    }

    // +++ OpenJDK NativeThread +++

    static native long n_gettid();

    static native void n_sigtid(long tid) throws ErrnoException;

    // +++ socket operations +++

    static native int n_socket() throws SocketException;

    static native void n_close(final int fd) throws ErrnoException;

    static native void n_setnonblock(final int fd,
      final boolean block) throws ErrnoException;

    static native int n_getsockopt(final int fd,
      final int optenum) throws SocketException;

    static native void n_setsockopt(final int fd,
      final int optenum, final int value) throws SocketException;

    static native void n_getsockname(final int fd,
      final AddrPort ap) throws SocketException;

    static native void n_bind(final int fd,
      final byte[] addr, final int port, final int scopeId) throws SocketException;

    static native void n_connect(final int fd,
      final byte[] addr, final int port, final int scopeId) throws SocketException;

    static native void n_disconnect(final int fd) throws SocketException;

    static native int n_recv(final int fd,
      final ByteBuffer buf, final int bbpos, final int bbsize,
      final AddrPort aptc, final boolean connected) throws SocketException;

    static native int n_send(final int fd,
      final ByteBuffer buf, final int bbpos, final int bbsize,
      final byte[] addr, final int port, final int scopeId) throws SocketException;

    static native int n_recvfrom(final int fd,
      final byte[] buf, final int bufpos, final int len,
      final AddrPort aptc, final boolean peekOnly, final boolean connected) throws SocketException;

    static native int n_sendto(final int fd,
      final byte[] buf, final int bufpos, final int len,
      final byte[] addr, final int port, final int scopeId) throws SocketException;

    static native long n_rd(final int fd,
      final SGIO[] bufs, final int nbufs, final AddrPort tc) throws SocketException;

    static native long n_wr(final int fd,
      final SGIO[] bufs,
      final byte[] addr, final int port, final int scopeId) throws SocketException;

    // 1 (ok), 0 (timeout or POLLERR/POLLHUP/POLLNVAL), EINTR or THROWN
    static native int n_pollin(final int fd,
      final int timeout) throws SocketException;

    // +++ I/O operations +++

    static long ioresult(final long n) {
        return n == EAVAIL ? 0 : n;
    }
}
