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

import android.support.test.runner.AndroidJUnit4;
import android.util.Log;
import lombok.val;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;

import static de.telekom.llcto.ecn_bits.android.lib.Testable.assertThrows;
import static org.junit.Assert.*;

/**
 * Instrumented tests for {@link JNI}
 *
 * Basically, tests that must run on the Android emulator because these things
 * cannot be unit-tested on the buildhost system, no thanks to glibc…
 *
 * @author mirabilos (t.glaser@tarent.de)
 */
@RunWith(AndroidJUnit4.class)
public class JNIInstrumentedTest {
    @Test
    public void testClassBoots() {
        Log.i("ECN-Bits-JNITest", "testing Java™ part of JNI class…");
        val ap = new JNI.AddrPort();
        ap.addr = new byte[4];
        ap.port = 666;
        Log.i("ECN-Bits-JNITest", "it works: " + ap.get());
    }

    @Test
    public void testJNIBoots() {
        Log.i("ECN-Bits-JNITest", "testing JNI part of JNI class…");
        final long tid;
        try {
            tid = JNI.n_gettid();
        } catch (Throwable t) {
            Log.e("ECN-Bits-JNITest", "it failed", t);
            fail("JNI does not work");
            return;
        }
        Log.i("ECN-Bits-JNITest", "it also works: " + tid);
        assertNotEquals("but is 0", 0, tid);
    }

    @Test
    public void testSignallingThrows() {
        final JNI.ErrnoException t = assertThrows(JNI.ErrnoException.class,
          () -> JNI.n_sigtid(0),
          "want an ESRCH exception");
        Log.i("ECN-Bits-JNITest", "successfully caught", t);
        assertEquals("is not ESRCH", /* ESRCH */ 3, t.getErrno());
        assertEquals("description", "pthread_kill(0)", t.getFailureDescription());
        if ("No such process".equals(t.getStrerror())) {
            Log.i("ECN-Bits-JNITest", "strerror also matches ESRCH (sigtid)");
        } else {
            Log.w("ECN-Bits-JNITest", String.format("strerror \"%s\" does not match ESRCH (sigtid)," +
              " could also be locale-dependent, please check manually!", t.getStrerror()));
        }
    }

    @Test
    public void testJNISocketException() {
        final JNI.ErrnoSocketException t = assertThrows(JNI.ErrnoSocketException.class,
          () -> JNI.n_getsockopt(-1, JNI.IP_TOS),
          "want an EBADF exception");
        Log.i("ECN-Bits-JNITest", "successfully caught", t);
        assertEquals("is not EBADF", /* EBADF */ 9, t.getErrno());
        assertEquals("description", "getsockopt(-1, 41, 67)", t.getFailureDescription());
        if ("Bad file descriptor".equals(t.getStrerror())) {
            Log.i("ECN-Bits-JNITest", "strerror also matches EBADF (getsockopt)");
        } else {
            Log.w("ECN-Bits-JNITest", String.format("strerror \"%s\" does not match EBADF (getsockopt)," +
              " could also be locale-dependent, please check manually!", t.getStrerror()));
        }
    }

    @Test
    public void testOtherExceptionNoCause() {
        final NullPointerException cause = new NullPointerException("meow");
        Log.i("ECN-Bits-JNITest", "constructing JNI exception caused by cat NPE");
        final JNI.ErrnoSocketException t = new JNI.ErrnoSocketException("file", 1, "func",
          "msg", 0, "Success", cause);
        assertNull("cause is nil", t.getCause());
        Log.i("ECN-Bits-JNITest", "end of test");
    }

    @Test
    public void testOpenSocket() throws IOException {
        final int fd = JNI.n_socket();
        Log.i("ECN-Bits-JNITest", String.format("got socket %d", fd));
        final JNI.AddrPort ap = new JNI.AddrPort();
        JNI.n_getsockname(fd, ap);
        Log.i("ECN-Bits-JNITest", " bound to " + ap.get().toString());
        JNI.n_close(fd);
    }
}
