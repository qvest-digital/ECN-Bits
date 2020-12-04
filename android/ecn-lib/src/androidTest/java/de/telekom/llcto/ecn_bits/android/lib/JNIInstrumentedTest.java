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

import static de.telekom.llcto.ecn_bits.android.lib.Testable.assertThrows;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.fail;

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
            tid = JNI.gettid();
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
          () -> JNI.sigtid(0),
          "want an ESRCH exception");
        Log.i("ECN-Bits-JNITest", "successfully caught", t);
        assertEquals("is not ESRCH",/* ESRCH */ 3, t.getErrno());
    }
}
