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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.condition.OS.LINUX;

/**
 * Unit tests for {@link JNI}
 *
 * These are run on the build host, with a copy of the JNI library linked
 * against the system libc (usually glibc), which makes testing some aspects
 * impossible (see JNIInstrumentedTest for these). But we can do some basic
 * testing here.
 *
 * @author mirabilos (t.glaser@tarent.de)
 */
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
            tid = JNI.n_gettid();
        } catch (Throwable t) {
            LOGGER.log(Level.SEVERE, "it failed", t);
            fail("JNI does not work");
            return;
        }
        LOGGER.info("it also works: " + tid);
        assertNotEquals(0, tid, "but is 0");
    }

    // Java 9 has List.of(…) directly but Android is at best Java 8 :/
    final List<String> knownStrerrorVariantsEBADF = Stream.of("Bad file descriptor",
      "Ungültiger Dateideskriptor").collect(Collectors.toCollection(ArrayList::new));

    @Test
    @EnabledOnOs(LINUX)
    public void testJNIException() {
        val t = assertThrows(JNI.ErrnoException.class,
          () -> JNI.n_close(-1),
          "want an EBADF exception");
        LOGGER.log(Level.INFO, "successfully caught", t);
        assertEquals(/* EBADF */ 9, t.getErrno(), "is not EBADF");
        assertEquals("close(-1)", t.getFailureDescription(), "description");

        if (knownStrerrorVariantsEBADF.contains(t.getStrerror())) {
            LOGGER.info("strerror also matches EBADF (close) or is known");
        } else {
            LOGGER.warning(String.format("strerror \"%s\" does not match EBADF (close)," +
              " could also be locale-dependent, please check manually!", t.getStrerror()));
        }
    }

    @Test
    @EnabledOnOs(LINUX)
    public void testJNISocketException() {
        val t = assertThrows(JNI.ErrnoSocketException.class,
          () -> JNI.n_getsockopt(-1, JNI.IP_TOS),
          "want an EBADF exception");
        LOGGER.log(Level.INFO, "successfully caught", t);
        assertEquals(/* EBADF */ 9, t.getErrno(), "is not EBADF");
        assertEquals("getsockopt(-1, 41, 67)", t.getFailureDescription(), "description");

        if (knownStrerrorVariantsEBADF.contains(t.getStrerror())) {
            LOGGER.info("strerror also matches EBADF (getsockopt) or is known");
        } else {
            LOGGER.warning(String.format("strerror \"%s\" does not match EBADF (getsockopt)," +
              " could also be locale-dependent, please check manually!", t.getStrerror()));
        }
    }
}
