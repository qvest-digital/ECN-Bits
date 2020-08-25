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
 * Refer to android/lib/COPYING for the full text of that licence.
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

import android.util.Log;
import lombok.val;

import java.io.FileDescriptor;
import java.io.IOException;
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
@SuppressWarnings({ "deprecation", "unused", /*UnIntelliJ bug*/ "RedundantSuppression" })
class ECNBitsDatagramSocketImpl extends DatagramSocketImpl {
    private final DatagramSocketImpl p;
    private int sockfd = -1;
    private final Method dataAvailable;
    private final Method setDatagramSocket;
    private final Method getDatagramSocket;
    private final Method setOption;
    private final Method getOption;
    private final Method getFD;

    native private int nativeSetup(final int fd);

    static {
        System.loadLibrary("ecnbits-ndk");
    }

    void setUpRecvTclass() {
        Log.w("ECN-Bits", String.format("setting up socket #%d", sockfd));
        if (nativeSetup(sockfd) != 0) {
            throw new UnsupportedOperationException("unable to set up socket to receive traffic class");
        }
        Log.w("ECN-Bits", "set up socket successfully");
    }

    Byte retrieveLastTrafficClass() {
        //XXX TODO implement
        return null;
    }

    ECNBitsDatagramSocketImpl() {
        try {
            final Class<?> clazz = Class.forName("java.net.PlainDatagramSocketImpl");
            val cons = clazz.getDeclaredConstructor();
            cons.setAccessible(true);
            p = (DatagramSocketImpl) cons.newInstance();
            final Class<?> dsiClazz = clazz.getSuperclass().getSuperclass();

            dataAvailable = dsiClazz.getDeclaredMethod("dataAvailable");
            dataAvailable.setAccessible(true);
            setDatagramSocket = dsiClazz.getDeclaredMethod("setDatagramSocket", DatagramSocket.class);
            setDatagramSocket.setAccessible(true);
            getDatagramSocket = dsiClazz.getDeclaredMethod("getDatagramSocket");
            getDatagramSocket.setAccessible(true);
            setOption = clazz.getDeclaredMethod("setOption", SocketOption.class, Object.class);
            setOption.setAccessible(true);
            getOption = clazz.getDeclaredMethod("getOption", SocketOption.class);
            getOption.setAccessible(true);

            final Class<?> fdClazz = FileDescriptor.class;
            //noinspection JavaReflectionMemberAccess
            getFD = fdClazz.getDeclaredMethod("getInt$");
            getFD.setAccessible(true);
        } catch (ClassNotFoundException | IllegalAccessException | InstantiationException | InvocationTargetException | NoSuchMethodException e) {
            Log.e("ECN-Bits", "instantiating", e);
            throw new RuntimeException(e);
        }
    }

    protected int getSocketFD() {
        try {
            return (int) getFD.invoke(p.fd);
        } catch (IllegalAccessException | InvocationTargetException e) {
            Log.e("ECN-Bits", "getFD reflection", e);
            throw new RuntimeException(e);
        }
    }

    @Override
    protected synchronized void create() throws SocketException {
        Log.w("ECN-Bits", "creating socket");
        p.create();
        sockfd = getSocketFD();
        Log.w("ECN-Bits", String.format("created socket #%d", sockfd));
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
    protected synchronized int peek(final InetAddress i) throws IOException {
        // uses doRecv() but does not actually read the packet usefully anyway
        return p.peek(i);
    }

    @Override
    protected synchronized int peekData(final DatagramPacket packet) throws IOException {
        // uses doRecv() and exposes the packet
        Log.w("ECN-Bits", "called peekData");
        return p.peekData(packet);
    }

    @Override
    protected synchronized void receive(final DatagramPacket packet) throws IOException {
        // uses doRecv() via receive0()
        Log.w("ECN-Bits", "called receive");
        p.receive(packet);
    }

    @Override
    protected void setTTL(final byte ttl) throws IOException {
        p.setTTL(ttl);
    }

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
        p.close();
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

    int dataAvailable() {
        try {
            return (int) dataAvailable.invoke(p);
        } catch (IllegalAccessException | InvocationTargetException e) {
            Log.e("ECN-Bits", "dataAvailable reflection", e);
            throw new RuntimeException(e);
        }
    }

    void setDatagramSocket(final DatagramSocket socket) {
        try {
            setDatagramSocket.invoke(p, socket);
        } catch (IllegalAccessException | InvocationTargetException e) {
            Log.e("ECN-Bits", "setDatagramSocket reflection", e);
            throw new RuntimeException(e);
        }
    }

    DatagramSocket getDatagramSocket() {
        try {
            return (DatagramSocket) getDatagramSocket.invoke(p);
        } catch (IllegalAccessException | InvocationTargetException e) {
            Log.e("ECN-Bits", "getDatagramSocket reflection", e);
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("RedundantThrows")
    <T> void setOption(final SocketOption<T> name, final T value) throws IOException {
        try {
            setOption.invoke(p, name, value);
        } catch (IllegalAccessException | InvocationTargetException e) {
            Log.e("ECN-Bits", "setOption reflection", e);
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings({ "RedundantThrows", "unchecked" })
    <T> T getOption(SocketOption<T> name) throws IOException {
        try {
            return (T) getOption.invoke(p, name);
        } catch (IllegalAccessException | InvocationTargetException e) {
            Log.e("ECN-Bits", "getOption reflection", e);
            throw new RuntimeException(e);
        }
    }
}
