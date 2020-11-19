package java.net;

/*-
 * Copyright © 2020
 *      mirabilos <t.glaser@tarent.de>
 * Copyright © 1996, 2007, 2011, 2013
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

import android.annotation.SuppressLint;
import android.util.Log;
import de.telekom.llcto.ecn_bits.android.lib.ECNBitsLibraryException;
import de.telekom.llcto.ecn_bits.android.lib.ECNMeasurer;
import lombok.RequiredArgsConstructor;

import java.io.FileDescriptor;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Plain{@link DatagramSocketImpl} with extras to receive ECN bits,
 * or the traffic class octet really; use {@link ECNBitsDatagramSocket}.
 *
 * cf. https://issuetracker.google.com/issues/165812106
 *
 * @author mirabilos (t.glaser@tarent.de)
 */
class ECNBitsDatagramSocketImpl extends DatagramSocketImpl {
    private final DatagramSocketImpl p;
    private int sockfd = -1;
    private final ECNMeasurer tcm = new ECNMeasurer();
    private final Method getFDRM;
    private final Field bufLengthRF;
    private final Method setReceivedLengthRM;

    native private static void nativeInit();

    native private int nativeSetup(final int fd);

    native private int nativePoll(final int fd, final int timeout);

    native private int nativeRecv(final RecvMsgArgs args);

    static {
        System.loadLibrary("ecnbits-ndk");
    }

    ECNMeasurer setUpRecvTclass() {
        if (nativeSetup(sockfd) != 0) {
            throw new ECNBitsLibraryException("unable to set up socket to receive traffic class");
        }
        return tcm;
    }

    @SuppressLint({ "BlockedPrivateApi", "DiscouragedPrivateApi" })
    ECNBitsDatagramSocketImpl() {
        try {
            final Class<?> plainDatagramSocketImplRC = Class.forName("java.net.PlainDatagramSocketImpl");
            final Constructor<?> plainDatagramSocketImplRI = plainDatagramSocketImplRC.getDeclaredConstructor();
            plainDatagramSocketImplRI.setAccessible(true);
            p = (DatagramSocketImpl) plainDatagramSocketImplRI.newInstance();

            final Class<?> fileDescriptorRC = FileDescriptor.class;
            //noinspection JavaReflectionMemberAccess
            getFDRM = fileDescriptorRC.getDeclaredMethod("getInt$");
            getFDRM.setAccessible(true);

            //noinspection JavaReflectionMemberAccess
            bufLengthRF = DatagramPacket.class.getDeclaredField("bufLength");
            bufLengthRF.setAccessible(true);
            //noinspection JavaReflectionMemberAccess
            setReceivedLengthRM = DatagramPacket.class.getDeclaredMethod("setReceivedLength", int.class);
            setReceivedLengthRM.setAccessible(true);
        } catch (ClassNotFoundException | IllegalAccessException | InstantiationException | InvocationTargetException | NoSuchMethodException | NoSuchFieldException e) {
            Log.e("ECN-Bits", "instantiating", e);
            throw new ECNBitsLibraryException("could not access nōn-SDK interfaces via reflection", e);
        }
    }

    protected int getSocketFD() {
        try {
            return (int) getFDRM.invoke(p.fd);
        } catch (IllegalAccessException | InvocationTargetException e) {
            Log.e("ECN-Bits", "getFD reflection", e);
            throw new ECNBitsLibraryException("could not execute nōn-SDK reflection target", e);
        }
    }

    @Override
    protected synchronized void create() throws SocketException {
        p.create();
        sockfd = getSocketFD();
    }

    @Override
    protected synchronized void bind(final int lport, final InetAddress laddr) throws SocketException {
        p.bind(lport, laddr);
    }

    @Override
    protected void send(final DatagramPacket packet) throws IOException {
        p.send(packet);
    }

    @Override
    protected synchronized int peek(final InetAddress i) {
        // not called because we implement peekData (below)
        Log.e("ECN-Bits", "called peek");
        throw new UnsupportedOperationException("DatagramSocket.peek() is IPv4-only");
    }

    @Override
    protected synchronized int peekData(final DatagramPacket packet) throws IOException {
        doRecv(packet, true);
        return packet.getPort();
    }

    @Override
    protected synchronized void receive(final DatagramPacket packet) throws IOException {
        doRecv(packet, false);
    }

    @SuppressWarnings("deprecation")
    @Override
    protected void setTTL(final byte ttl) throws IOException {
        p.setTTL(ttl);
    }

    @SuppressWarnings("deprecation")
    @Override
    protected byte getTTL() throws IOException {
        return p.getTTL();
    }

    @Override
    protected void setTimeToLive(final int ttl) throws IOException {
        p.setTimeToLive(ttl);
    }

    @Override
    protected int getTimeToLive() throws IOException {
        return p.getTimeToLive();
    }

    @Override
    protected void join(final InetAddress inetaddr) throws IOException {
        p.join(inetaddr);
    }

    @Override
    protected void leave(final InetAddress inetaddr) throws IOException {
        p.leave(inetaddr);
    }

    @Override
    protected void joinGroup(final SocketAddress mcastaddr, final NetworkInterface netIf) throws IOException {
        p.joinGroup(mcastaddr, netIf);
    }

    @Override
    protected void leaveGroup(final SocketAddress mcastaddr, final NetworkInterface netIf) throws IOException {
        p.leaveGroup(mcastaddr, netIf);
    }

    @Override
    protected void close() {
        try {
            p.close();
        } finally {
            sockfd = -1;
        }
    }

    @Override
    public void setOption(final int optID, final Object value) throws SocketException {
        p.setOption(optID, value);
    }

    @Override
    public Object getOption(final int optID) throws SocketException {
        return p.getOption(optID);
    }

    @Override
    protected void connect(final InetAddress address, final int port) throws SocketException {
        p.connect(address, port);
    }

    @Override
    protected void disconnect() {
        p.disconnect();
    }

    @Override
    protected int getLocalPort() {
        return p.getLocalPort();
    }

    @Override
    protected FileDescriptor getFileDescriptor() {
        return p.getFileDescriptor();
    }

    @RequiredArgsConstructor
    private static class RecvMsgArgs {
        final int fd; // in
        final byte[] buf; // in, mutated buf[ofs] .. buf[ofs + len - 1]
        final int ofs; // in
        final int len; // in
        final boolean peekOnly; // in
        byte[] addr; // out
        int port; // out
        int read; // out
        byte tc; // out
        boolean tcValid; // out

        static {
            nativeInit();
        }
    }

    // note: this is always called synchronised
    private void doRecv(final DatagramPacket packet, final boolean peekOnly) throws IOException {
        tcm.listen();

        // also checks for isClosed and throws accordingly
        final int timeout = (Integer) getOption(SO_TIMEOUT);

        if (timeout != 0) {
            switch (nativePoll(sockfd, timeout)) {
            case 0:
                break;
            case 1:
                throw new SocketTimeoutException("recvmsg poll timed out");
            default:
                throw new SocketException("recvmsg poll failed");
            }
        }

        final RecvMsgArgs args;
        try {
            args = new RecvMsgArgs(sockfd, packet.getData(), packet.getOffset(),
              bufLengthRF.getInt(packet), peekOnly);
        } catch (IllegalAccessException e) {
            Log.e("ECN-Bits", "bufLength reflection", e);
            throw new ECNBitsLibraryException("could not execute nōn-SDK reflection target", e);
        }
        switch (nativeRecv(args)) {
        case 0:
            break;
        case 1: // EAGAIN
            throw new SocketTimeoutException("recvmsg timed out");
        case 2: // ECONNREFUSED
            throw new PortUnreachableException("ICMP Port Unreachable");
        default:
            throw new SocketException("recvmsg failed");
        }
        final InetAddress srcaddr = InetAddress.getByAddress(args.addr);
        final InetSocketAddress src = new InetSocketAddress(srcaddr, args.port);

        try {
            setReceivedLengthRM.invoke(packet, args.read);
        } catch (IllegalAccessException | InvocationTargetException e) {
            Log.e("ECN-Bits", "setReceivedLength reflection", e);
            throw new ECNBitsLibraryException("could not execute nōn-SDK reflection target", e);
        }
        packet.setPort(src.getPort());
        // packet.address should only be changed when it is different from src
        if (!src.getAddress().equals(packet.getAddress())) {
            packet.setAddress(src.getAddress());
        }

        tcm.received(args.tcValid, args.tc);
    }
}
