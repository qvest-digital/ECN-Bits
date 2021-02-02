package de.telekom.llcto.ecn_bits.jdk.lib;

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

/**
 * Error class for problems caused to the ECN-Bits library by Android.
 *
 * @author mirabilos (t.glaser@tarent.de)
 * @see
 * <a href="https://developer.android.com/distribute/best-practices/develop/restrictions-non-sdk-interfaces#how_can_i_enable_access_to_non-sdk_interfaces">SDK documentation</a>
 */
public class ECNBitsLibraryException extends RuntimeException {
    private static final long serialVersionUID = 8044140298655196981L;

    /**
     * Constructor, message only (no cause)
     *
     * @param message error classification message
     */
    public ECNBitsLibraryException(final String message) {
        this(message, null);
    }

    /**
     * Constructor, message and cause
     *
     * @param message error classification message
     * @param cause   exception providing more detail
     */
    public ECNBitsLibraryException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
