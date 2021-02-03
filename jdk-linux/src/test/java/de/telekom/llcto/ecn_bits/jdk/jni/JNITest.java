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
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link JNI}
 *
 * @author mirabilos (t.glaser@tarent.de)
 */
@Log
public class JNITest {
    @Test
    public void testClassBoots() {
        LOG.info("running on " + System.getProperty("os.name"));

        LOG.info("testing Java™ part of JNI class…");
        val ap = new JNI.AddrPort();
        ap.addr = new byte[4];
        ap.port = 666;
        LOG.info("it works: " + ap.get());
    }

    @Test
    public void testJNIBoots() {
        LOG.info("testing JNI part of JNI class…");
        final long tid;
        try {
            tid = JNI.n_gettid();
        } catch (Throwable t) {
            LOG.log(Level.SEVERE, "it failed", t);
            fail("JNI does not work");
            return;
        }
        LOG.info("it also works: " + tid);
        assertNotEquals(0, tid, "but is 0");
    }

    // Java 9 has List.of(…) directly but Android is at best Java 8 :/
    final List<String> knownStrerrorVariantsEBADF = Stream.of("Bad file descriptor",
      "Ungültiger Dateideskriptor").collect(Collectors.toCollection(ArrayList::new));

    @Test
    public void testJNIException() {
        val t = assertThrows(JNI.ErrnoException.class,
          () -> JNI.n_close(-1),
          "want an EBADF exception");
        LOG.log(Level.INFO, "successfully caught", t);
        assertEquals(/* EBADF */ 9, t.getErrno(), "is not EBADF");
        assertEquals("close(-1)", t.getFailureDescription(), "description");

        if (knownStrerrorVariantsEBADF.contains(t.getStrerror())) {
            LOG.info("strerror also matches EBADF (close) or is known");
        } else {
            LOG.warning(String.format("strerror \"%s\" does not match EBADF (close)," +
              " could also be locale-dependent, please check manually!", t.getStrerror()));
        }
    }

    @Test
    public void testJNISocketException() {
        val t = assertThrows(JNI.ErrnoSocketException.class,
          () -> JNI.n_getsockopt(-1, JNI.IP_TOS),
          "want an EBADF exception");
        LOG.log(Level.INFO, "successfully caught", t);
        assertEquals(/* EBADF */ 9, t.getErrno(), "is not EBADF");
        assertEquals("getsockopt(-1, 41, 67)", t.getFailureDescription(), "description");

        if (knownStrerrorVariantsEBADF.contains(t.getStrerror())) {
            LOG.info("strerror also matches EBADF (getsockopt) or is known");
        } else {
            LOG.warning(String.format("strerror \"%s\" does not match EBADF (getsockopt)," +
              " could also be locale-dependent, please check manually!", t.getStrerror()));
        }
    }

    @Test
    public void testOtherExceptionNoCause() {
        val cause = new NullPointerException("meow");
        LOG.info("constructing JNI exception caused by cat NPE");
        val t = new JNI.ErrnoSocketException("file", 1, "func",
          "msg", 0, "Success", cause);
        assertNull(t.getCause(), "cause is nil");
        LOG.info("end of test");
    }

    @Test
    public void testOpenSocket() throws IOException {
        final int fd = JNI.n_socket();
        LOG.info(String.format("got socket %d", fd));
        val ap = new JNI.AddrPort();
        JNI.n_getsockname(fd, ap);
        LOG.info(" bound to " + ap.get());
        JNI.n_close(fd);
    }
}
