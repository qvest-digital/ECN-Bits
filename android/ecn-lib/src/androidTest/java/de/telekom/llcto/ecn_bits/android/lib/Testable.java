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

import static org.junit.Assert.*;

/**
 * Implementation of {@link #assertThrows(Class, Testable, String)}
 * for AndroidJUnit4-run instrumented tests
 *
 * @author mirabilos (t.glaser@tarent.de)
 */
public interface Testable {
    /**
     * Runnable to be implemented by lambda.
     *
     * @throws Throwable we want to test for
     */
    void run() throws Throwable;

    /**
     * Runs some code and ensures that the expected Throwable is thrown
     *
     * @param expectedType (super‑)class of the Throwable expected to be thrown
     * @param runnable     code under test
     * @param message      prefix in case of errors (no or wrong exception thrown)
     * @param <T>          expectedType
     * @return the Throwable
     */
    static <T extends Throwable> T assertThrows(final Class<T> expectedType,
      final Testable runnable, final String message) {
        try {
            runnable.run();
        } catch (Throwable t) {
            if (!expectedType.isInstance(t)) {
                fail(message + ": " + t);
                /* NOTREACHED */
                throw new RuntimeException("not reached");
            }
            return expectedType.cast(t);
        }
        fail(message + ": no exception thrown");
        /* NOTREACHED */
        throw new RuntimeException("not reached");
    }
}
