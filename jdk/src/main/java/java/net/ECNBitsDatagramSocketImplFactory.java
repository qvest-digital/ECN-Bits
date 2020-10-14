package java.net;

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

import de.telekom.llcto.ecn_bits.jdk.lib.ECNBitsLibraryException;

/**
 * {@link DatagramSocketImplFactory} for use with the ECN-Bits library
 *
 * @author mirabilos (t.glaser@tarent.de)
 * @see ECNBitsDatagramSocketImpl
 * @see ECNBitsDatagramSocket
 */
public class ECNBitsDatagramSocketImplFactory implements DatagramSocketImplFactory {
    /**
     * Creates a new {@link ECNBitsDatagramSocketImpl} instance.
     *
     * @return a new instance of {@link ECNBitsDatagramSocketImpl}.
     * @throws ECNBitsLibraryException if the library cannot be initialised
     * @see DatagramSocketImpl
     * @see ECNBitsDatagramSocketImpl
     * @see ECNBitsDatagramSocket
     */
    @Override
    public DatagramSocketImpl createDatagramSocketImpl() {
        return null;// new ECNBitsDatagramSocketImpl();
    }
}