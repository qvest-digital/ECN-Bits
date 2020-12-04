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
import android.content.Context;
import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;

import lombok.val;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.logging.Level;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class JNITest {
    @Test
    public void testClassBoots() {
        Log.i("ECN-Bits:JNITest", "testing Java™ part of JNI class…");
        val ap = new JNI.AddrPort();
        ap.addr = new byte[4];
        ap.port = 666;
        Log.i("ECN-Bits:JNITest", "it works: " + ap.get());
    }

    @Test
    public void testJNIBoots() {
        Log.i("ECN-Bits:JNITest", "testing JNI part of JNI class…");
        final long tid;
        try {
            tid = JNI.gettid();
        } catch (Throwable t) {
            Log.e("ECN-Bits:JNITest", "it failed", t);
            Assertions.fail("JNI does not work");
            return;
        }
        Log.i("ECN-Bits:JNITest", "it also works: " + tid);
        assertNotEquals(0, tid, "but is 0");
    }

    @Test
    public void testSignallingThrows() {
        final JNI.ErrnoException t = Assertions.assertThrows(JNI.ErrnoException.class,
          () -> JNI.sigtid(0),
          "want an ESRCH exception");
        Log.i("ECN-Bits:JNITest", "successfully caught", t);
        Assertions.assertEquals(/* ESRCH */ 3, t.getErrno(), "is not ESRCH");
    }
}
