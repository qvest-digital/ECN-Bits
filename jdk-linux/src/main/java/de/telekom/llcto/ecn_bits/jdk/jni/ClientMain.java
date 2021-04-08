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

import org.evolvis.tartools.mvnparent.InitialiseLogging;
import org.evolvis.tartools.rfc822.FQDN;
import org.evolvis.tartools.rfc822.IPAddress;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Example ECN-Bits client program for {@link DatagramSocket} replacement
 *
 * @author mirabilos (t.glaser@tarent.de)
 */
public final class ClientMain {
    // note this MUST NOT be replaced by @Log in this class ONLY
    private static final Logger LOG;

    /* initialise logging subsystem (must be done before creating a LOGGER) */
    static {
        InitialiseLogging.InitialiseJDK14Logging();
        LOG = Logger.getLogger(ClientMain.class.getName());
    }

    static class IPorFQDN {
        final boolean resolved;
        final String s;
        final InetAddress[] a = new InetAddress[1];

        IPorFQDN(final String i) {
            resolved = false;
            s = i;
        }

        IPorFQDN(final String i, final InetAddress ip) {
            resolved = true;
            s = i;
            a[0] = ip;
        }
    }

    static RuntimeException usage(final String err) {
        return usage(err, null);
    }

    // must be an array so ChannelMain can mutate it
    static String[] USAGE = new String[] { "Usage: ./client.sh hostname port [tc]" };

    static RuntimeException usage(final String err, final Throwable t) {
        if (err != null) {
            LOG.log(Level.SEVERE, err, t);
        }
        LOG.severe(USAGE[0]);
        System.exit(1);
        return new RuntimeException();
    }

    @SuppressWarnings("unused")
    static RuntimeException die(final String err) {
        return die(err, null);
    }

    static RuntimeException die(final String err, final Throwable t) {
        LOG.log(Level.SEVERE, err, t);
        System.exit(1);
        return new RuntimeException();
    }

    static IPorFQDN parseHostname(final String s) {
        if ("".equals(s)) {
            throw usage("empty hostname");
        }
        if (FQDN.isDomain(s)) {
            return new IPorFQDN(s);
        }
        final InetAddress ip = IPAddress.from(s);
        if (ip != null) {
            return new IPorFQDN(s, ip);
        }
        throw usage("invalid hostname: " + s);
    }

    static int parsePort(final String arg) {
        if ("".equals(arg)) {
            throw usage("empty port");
        }
        final int port;
        try {
            port = Integer.parseUnsignedInt(arg);
        } catch (NumberFormatException e) {
            throw usage("bad port: " + arg, e);
        }
        if (port < 1) {
            throw usage("port too small: " + port);
        }
        if (port > 65535) {
            throw usage("port too large: " + port);
        }
        return port;
    }

    public static void main(String[] argv) {
        try {
            client(argv);
        } catch (Throwable t) {
            LOG.log(Level.SEVERE, "fatal error", t);
            System.exit(255);
        }
    }

    private static void client(String[] argv) {
        final Bits outBits;
        final /*byte*/ int outTc;
        if (argv.length == 3) {
            if ("NO".equals(argv[2])) {
                outBits = Bits.NO;
                outTc = outBits.getBits();
            } else if ("ECT0".equals(argv[2])) {
                outBits = Bits.ECT0;
                outTc = outBits.getBits();
            } else if ("ECT1".equals(argv[2])) {
                outBits = Bits.ECT1;
                outTc = outBits.getBits();
            } else if ("CE".equals(argv[2])) {
                outBits = Bits.CE;
                outTc = outBits.getBits();
            } else {
                try {
                    outBits = null;
                    outTc = Integer.decode(argv[2]);
                    if (outTc < 0 || outTc > 255) {
                        throw usage("invalid traffic class: " + argv[2]);
                    }
                } catch (NumberFormatException e) {
                    throw usage("invalid traffic class number: " + argv[2], e);
                }
            }
        } else if (argv.length == 2) {
            outBits = Bits.ECT0;
            outTc = outBits.getBits();
        } else {
            throw usage(null);
        }

        final IPorFQDN hostname = parseHostname(argv[0]);
        final int port = parsePort(argv[1]);

        final ECNBitsDatagramSocket sock;
        try {
            sock = new ECNBitsDatagramSocket();
        } catch (SocketException e) {
            throw die("create socket", e);
        }
        sock.startMeasurement();

        LOG.info(String.format("connect to [%s]:%d with %s%n",
          hostname.resolved ? hostname.a[0].getHostAddress() : hostname.s, port,
          outBits == null ? String.format("0x%02X", outTc) : outBits.getShortname()));
        boolean oneSuccess = false;
        try {
            sock.setSoTimeout(1000);
            sock.setTrafficClass(outTc);
            final byte[] buf = new byte[512];
            final InetAddress[] dstArr = hostname.resolved ? hostname.a :
              InetAddress.getAllByName(hostname.s);
            for (final InetAddress dst : dstArr) {
                System.out.printf(" → [%s]:%d%n",
                  dst.getHostAddress(), port);
                buf[0] = 'h';
                buf[1] = 'i';
                buf[2] = '!';
                final DatagramPacket psend = new DatagramPacket(buf, 3, dst, port);
                try {
                    sock.send(psend);
                } catch (IOException e) {
                    LOG.log(Level.WARNING, "send", e);
                    continue;
                }
                final DatagramPacket precv = new DatagramPacket(buf, buf.length);
                while (true) {
                    // this is REQUIRED for EACH call if re-using a DatagramPacket for multiple
                    // receive calls; cf. https://bugs.openjdk.java.net/browse/JDK-4161511 but
                    // do not use setLength as suggested there, use the following invocation:
                    precv.setData(precv.getData());
                    try {
                        sock.receive(precv);
                    } catch (SocketTimeoutException e) {
                        break;
                    } catch (IOException e) {
                        LOG.log(Level.WARNING, "recv", e);
                        break;
                    }
                    final String stamp = ZonedDateTime.now(ZoneOffset.UTC)
                      .truncatedTo(ChronoUnit.MILLIS)
                      .format(DateTimeFormatter.ISO_INSTANT);
                    final Byte trafficClass = sock.retrieveLastTrafficClass();
                    oneSuccess = true;
                    final String userData = new String(buf,
                      precv.getOffset(), precv.getLength(), StandardCharsets.UTF_8);
                    System.out.printf(/* what other client would you like it to look like? */
                      /* Android */ /* "• %s %s{%s}%n%s%n" */
                      /* C / CLI */ "%s %s{%s}<%s>%n",
                      stamp, Bits.print(trafficClass),
                      trafficClass == null ? "??" : String.format("%02X", trafficClass),
                      userData.trim());
                }
            }
            if (oneSuccess) {
                System.out.println(" ‣ Success!");
            } else {
                System.out.println("!! failed !!");
            }
        } catch (UnknownHostException e) {
            LOG.log(Level.WARNING, "resolve", e);
        } catch (SocketException e) {
            LOG.log(Level.WARNING, "setsockopt", e);
        } finally {
            try {
                final ECNStatistics stats = sock.getMeasurement(false);
                System.out.println(stats == null ?
                  "!! no congestion measurement" :
                  String.format("ℹ %.2f%% of %d packets received over %d ms were congested",
                    stats.getCongestionFactor() * 100.0, stats.getReceivedPackets(),
                    stats.getLengthOfMeasuringPeriod() / 1000000L));
            } catch (ArithmeticException e) {
                LOG.log(Level.WARNING, "ECNStatistics", e);
            }
            if (!sock.isClosed()) {
                sock.close();
            }
        }
    }
}
