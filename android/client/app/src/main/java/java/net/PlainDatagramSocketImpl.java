package java.net;

import java.io.IOException;

/**
 * Dummy class. At runtime, the class from the JDK will replace this class.
 */
public class PlainDatagramSocketImpl extends DatagramSocketImpl {
    @Override
    protected void create() throws SocketException {
        throw new RuntimeException("meow PlainDatagramSocketImpl dummy");
    }

    @Override
    protected void bind(final int lport, final InetAddress laddr) throws SocketException {
        throw new RuntimeException("meow PlainDatagramSocketImpl dummy");
    }

    @Override
    protected void send(final DatagramPacket p) throws IOException {
        throw new RuntimeException("meow PlainDatagramSocketImpl dummy");
    }

    @Override
    protected int peek(final InetAddress i) throws IOException {
        throw new RuntimeException("meow PlainDatagramSocketImpl dummy");
    }

    @Override
    protected int peekData(final DatagramPacket p) throws IOException {
        throw new RuntimeException("meow PlainDatagramSocketImpl dummy");
    }

    @Override
    protected void receive(final DatagramPacket p) throws IOException {
        throw new RuntimeException("meow PlainDatagramSocketImpl dummy");
    }

    @Override
    protected void setTTL(final byte ttl) throws IOException {
        throw new RuntimeException("meow PlainDatagramSocketImpl dummy");
    }

    @Override
    protected byte getTTL() throws IOException {
        throw new RuntimeException("meow PlainDatagramSocketImpl dummy");
    }

    @Override
    protected void setTimeToLive(final int ttl) throws IOException {
        throw new RuntimeException("meow PlainDatagramSocketImpl dummy");
    }

    @Override
    protected int getTimeToLive() throws IOException {
        throw new RuntimeException("meow PlainDatagramSocketImpl dummy");
    }

    @Override
    protected void join(final InetAddress inetaddr) throws IOException {
        throw new RuntimeException("meow PlainDatagramSocketImpl dummy");
    }

    @Override
    protected void leave(final InetAddress inetaddr) throws IOException {
        throw new RuntimeException("meow PlainDatagramSocketImpl dummy");
    }

    @Override
    protected void joinGroup(final SocketAddress mcastaddr, final NetworkInterface netIf) throws IOException {
        throw new RuntimeException("meow PlainDatagramSocketImpl dummy");
    }

    @Override
    protected void leaveGroup(final SocketAddress mcastaddr, final NetworkInterface netIf) throws IOException {
        throw new RuntimeException("meow PlainDatagramSocketImpl dummy");
    }

    @Override
    protected void close() {
        throw new RuntimeException("meow PlainDatagramSocketImpl dummy");
    }

    @Override
    public void setOption(final int optID, final Object value) throws SocketException {
        throw new RuntimeException("meow PlainDatagramSocketImpl dummy");
    }

    @Override
    public Object getOption(final int optID) throws SocketException {
        throw new RuntimeException("meow PlainDatagramSocketImpl dummy");
    }

    protected void datagramSocketCreate() throws SocketException {
        throw new RuntimeException("meow PlainDatagramSocketImpl dummy");
    }
}
