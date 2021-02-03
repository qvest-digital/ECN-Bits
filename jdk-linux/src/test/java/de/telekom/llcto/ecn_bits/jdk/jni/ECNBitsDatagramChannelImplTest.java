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

import lombok.extern.java.Log;
import lombok.val;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for ECN-Bits JNI functions (end-to-end tests, with network)
 *
 * @author mirabilos (t.glaser@tarent.de)
 */
@Log
public class ECNBitsDatagramChannelImplTest {
    /**
     * Tests scatter/gather I/O (end to end)
     *
     * @throws IOException          from the datagram channel
     * @throws InterruptedException from {@link CountDownLatch#await(long, TimeUnit)}
     */
    @Test
    public void testSGIO() throws IOException, InterruptedException {
        LOG.info("SGIO test: begin");
        try {
            val rchan = ECNBitsDatagramChannel.open();
            val schan = ECNBitsDatagramChannel.open();
            val rbound = new CountDownLatch(1);
            val sbound = new CountDownLatch(1);
            val rconnected = new CountDownLatch(1);
            // synchronisation inspired by https://stackoverflow.com/a/9148992/2171120
            val tfinished = new CountDownLatch(2);
            val tresult = new boolean[2]; // 0=recip 1=send
            val rbufs = new ByteBuffer[4];
            val recip = new Thread(() -> {
                try {
                    LOG.info("recip: start");
                    rchan.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
                    LOG.info("recip: bound");
                    rbound.countDown();
                    LOG.info("recip: counted down; waiting");
                    if (!sbound.await(1L, TimeUnit.SECONDS)) {
                        LOG.info("recip: false wait");
                        return;
                    }
                    LOG.info("recip: waited");
                    val addr = schan.getLocalAddress();
                    LOG.info("recip: connecting to " + addr);
                    rchan.connect(addr);
                    LOG.info("recip: connected");
                    rconnected.countDown();
                    rbufs[0] = ByteBuffer.allocateDirect(4);
                    rbufs[1] = ByteBuffer.allocate(4);
                    rbufs[2] = ByteBuffer.allocateDirect(4);
                    rbufs[3] = ByteBuffer.allocateDirect(4);
                    val nread = rchan.read(rbufs);
                    LOG.info("recip thread got: " + nread);
                    tresult[0] = nread == 9L;
                } catch (IOException | InterruptedException e) {
                    LOG.log(Level.WARNING, "recip excepted", e);
                } finally {
                    tfinished.countDown();
                }
            });
            val sender = new Thread(() -> {
                try {
                    LOG.info("send: start");
                    schan.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
                    LOG.info("send: bound");
                    sbound.countDown();
                    LOG.info("send: counted down");
                    val sbufs = new ByteBuffer[3];
                    sbufs[0] = ByteBuffer.allocateDirect(3);
                    sbufs[0].put((byte) 'f').put((byte) 'o').put((byte) 'o');
                    sbufs[0].flip();
                    sbufs[1] = ByteBuffer.allocate(3);
                    sbufs[1].put((byte) 'b').put((byte) 'a').put((byte) 'r');
                    sbufs[1].flip();
                    sbufs[2] = ByteBuffer.wrap("baz".getBytes(StandardCharsets.UTF_8));
                    val addr = rchan.getLocalAddress();
                    LOG.info("send: connecting to " + addr);
                    schan.connect(addr);
                    LOG.info("send: connected, waiting");
                    if (!rconnected.await(1L, TimeUnit.SECONDS)) {
                        LOG.info("send: false wait");
                        return;
                    }
                    val nwritten = schan.write(sbufs);
                    LOG.info("sender thread wrote: " + nwritten);
                    tresult[1] = nwritten == 9L;
                } catch (IOException | InterruptedException e) {
                    LOG.log(Level.WARNING, "sender excepted", e);
                } finally {
                    tfinished.countDown();
                }
            });
            LOG.info("starting recip thread");
            recip.start();
            assertTrue(rbound.await(1L, TimeUnit.SECONDS), "recip thread init");
            LOG.info("starting sender thread");
            sender.start();
            assertTrue(tfinished.await(3L, TimeUnit.SECONDS), "threads finish");
            LOG.info("threads finished");
            assertTrue(tresult[0], "recip successful");
            assertTrue(tresult[1], "sender successful");
            assertEquals(4, rbufs[0].capacity(), "buf0 capacity");
            assertEquals(4, rbufs[1].capacity(), "buf1 capacity");
            assertEquals(4, rbufs[2].capacity(), "buf2 capacity");
            assertEquals(4, rbufs[3].capacity(), "buf3 capacity");
            assertEquals(4, rbufs[0].position(), "buf0 position");
            assertEquals(4, rbufs[1].position(), "buf1 position");
            assertEquals(1, rbufs[2].position(), "buf2 position");
            assertEquals(0, rbufs[3].position(), "buf3 position");
            byte[] buf = new byte[4];
            rbufs[0].flip();
            rbufs[0].get(buf);
            assertArrayEquals(new byte[] { 'f', 'o', 'o', 'b' }, buf, "buf0 content");
            rbufs[1].flip();
            rbufs[1].get(buf);
            assertArrayEquals(new byte[] { 'a', 'r', 'b', 'a' }, buf, "buf1 content");
            buf = new byte[1];
            rbufs[2].flip();
            rbufs[2].get(buf);
            assertArrayEquals(new byte[] { 'z' }, buf, "buf2 content");
        } finally {
            LOG.info("SGIO test: end");
        }
    }
}
