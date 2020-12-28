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

import android.util.Log;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import lombok.val;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

/**
 * Integration test for ECN-Bits JNI functions (end-to-end tests, with network)
 *
 * @author mirabilos (t.glaser@tarent.de)
 */
@RunWith(AndroidJUnit4.class)
public class ECNBitsDatagramChannelImplTest {
    /**
     * Tests scatter/gather I/O (end to end)
     *
     * @throws IOException          from the datagram channel
     * @throws InterruptedException from {@link CountDownLatch#await(long, TimeUnit)}
     */
    @Test
    public void testSGIO() throws IOException, InterruptedException {
        Log.i("ECN-Bits-JNITest", "SGIO test: begin");
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
                    Log.i("ECN-Bits-JNITest", "recip: start");
                    rchan.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
                    Log.i("ECN-Bits-JNITest", "recip: bound");
                    rbound.countDown();
                    Log.i("ECN-Bits-JNITest", "recip: counted down; waiting");
                    if (!sbound.await(1L, TimeUnit.SECONDS)) {
                        Log.i("ECN-Bits-JNITest", "recip: false wait");
                        return;
                    }
                    Log.i("ECN-Bits-JNITest", "recip: waited");
                    val addr = schan.getLocalAddress();
                    Log.i("ECN-Bits-JNITest", "recip: connecting to " + addr);
                    rchan.connect(addr);
                    Log.i("ECN-Bits-JNITest", "recip: connected");
                    rconnected.countDown();
                    rbufs[0] = ByteBuffer.allocateDirect(4);
                    rbufs[1] = ByteBuffer.allocate(4);
                    rbufs[2] = ByteBuffer.allocateDirect(4);
                    rbufs[3] = ByteBuffer.allocateDirect(4);
                    val nread = rchan.read(rbufs);
                    Log.i("ECN-Bits-JNITest", "recip thread got: " + nread);
                    tresult[0] = nread == 9L;
                } catch (IOException | InterruptedException e) {
                    Log.w("ECN-Bits-JNITest", "recip excepted", e);
                } finally {
                    tfinished.countDown();
                }
            });
            val sender = new Thread(() -> {
                try {
                    Log.i("ECN-Bits-JNITest", "send: start");
                    schan.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
                    Log.i("ECN-Bits-JNITest", "send: bound");
                    sbound.countDown();
                    Log.i("ECN-Bits-JNITest", "send: counted down");
                    val sbufs = new ByteBuffer[3];
                    sbufs[0] = ByteBuffer.allocateDirect(3);
                    sbufs[0].put((byte) 'f').put((byte) 'o').put((byte) 'o');
                    sbufs[0].flip();
                    sbufs[1] = ByteBuffer.allocate(3);
                    sbufs[1].put((byte) 'b').put((byte) 'a').put((byte) 'r');
                    sbufs[1].flip();
                    sbufs[2] = ByteBuffer.wrap("baz".getBytes(StandardCharsets.UTF_8));
                    val addr = rchan.getLocalAddress();
                    Log.i("ECN-Bits-JNITest", "send: connecting to " + addr);
                    schan.connect(addr);
                    Log.i("ECN-Bits-JNITest", "send: connected, waiting");
                    if (!rconnected.await(1L, TimeUnit.SECONDS)) {
                        Log.i("ECN-Bits-JNITest", "send: false wait");
                        return;
                    }
                    val nwritten = schan.write(sbufs);
                    Log.i("ECN-Bits-JNITest", "sender thread wrote: " + nwritten);
                    tresult[1] = nwritten == 9L;
                } catch (IOException | InterruptedException e) {
                    Log.w("ECN-Bits-JNITest", "sender excepted", e);
                } finally {
                    tfinished.countDown();
                }
            });
            Log.i("ECN-Bits-JNITest", "starting recip thread");
            recip.start();
            assertTrue("recip thread init", rbound.await(1L, TimeUnit.SECONDS));
            Log.i("ECN-Bits-JNITest", "starting sender thread");
            sender.start();
            assertTrue("threads finish", tfinished.await(3L, TimeUnit.SECONDS));
            Log.i("ECN-Bits-JNITest", "threads finished");
            assertTrue("recip successful", tresult[0]);
            assertTrue("sender successful", tresult[1]);
            assertEquals("buf0 capacity", 4, rbufs[0].capacity());
            assertEquals("buf1 capacity", 4, rbufs[1].capacity());
            assertEquals("buf2 capacity", 4, rbufs[2].capacity());
            assertEquals("buf3 capacity", 4, rbufs[3].capacity());
            assertEquals("buf0 position", 4, rbufs[0].position());
            assertEquals("buf1 position", 4, rbufs[1].position());
            assertEquals("buf2 position", 1, rbufs[2].position());
            assertEquals("buf3 position", 0, rbufs[3].position());
            byte[] buf = new byte[4];
            rbufs[0].flip();
            rbufs[0].get(buf);
            assertArrayEquals("buf0 content", new byte[] { 'f', 'o', 'o', 'b' }, buf);
            rbufs[1].flip();
            rbufs[1].get(buf);
            assertArrayEquals("buf1 content", new byte[] { 'a', 'r', 'b', 'a' }, buf);
            buf = new byte[1];
            rbufs[2].flip();
            rbufs[2].get(buf);
            assertArrayEquals("buf2 content", new byte[] { 'z' }, buf);
        } finally {
            Log.i("ECN-Bits-JNITest", "SGIO test: end");
        }
    }
}
