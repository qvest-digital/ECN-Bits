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

import lombok.extern.java.Log;
import lombok.val;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;

import java.util.logging.Level;

import static org.junit.jupiter.api.condition.OS.LINUX;

@Log
public class JNITest {
    @Test
    public void testClassBoots() {
        LOGGER.info("running on " + System.getProperty("os.name"));
        if (!LINUX.isCurrentOs()) {
            LOGGER.warning("skipping JNI tests");
        }
        // for copy/paste into IntelliJ run options
        LOGGER.info("VM options: -Djava.library.path=" +
          System.getProperty("java.library.path"));

        LOGGER.info("testing Java™ part of JNI class…");
        val ap = new JNI.AddrPort();
        ap.addr = new byte[4];
        ap.port = 666;
        LOGGER.info("it works: " + ap.get());
    }

    @Test
    @EnabledOnOs(LINUX)
    public void testJNIBoots() {
        LOGGER.info("testing JNI part of JNI class…");
        final long tid;
        try {
            tid = JNI.gettid();
        } catch (Throwable t) {
            LOGGER.log(Level.SEVERE, "it failed", t);
            Assertions.fail("JNI does not work");
            return;
        }
        LOGGER.info("it also works: " + tid);
    }

    @Test
    @EnabledOnOs(LINUX)
    public void testSignallingThrows() {
        final JNI.ErrnoException t = Assertions.assertThrows(JNI.ErrnoException.class,
          () -> JNI.sigtid(0),
          "want an ESRCH exception");
        LOGGER.log(Level.INFO, "successfully caught", t);
        Assertions.assertEquals(/* ESRCH */ 3, t.getErrno(), "is not ESRCH");
    }
}
