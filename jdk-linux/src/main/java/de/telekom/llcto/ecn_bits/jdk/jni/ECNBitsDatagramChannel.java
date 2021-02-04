package de.telekom.llcto.ecn_bits.jdk.jni;

/*-
 * Copyright © 2020
 *      mirabilos <t.glaser@tarent.de>
 * Copyright © 2000, 2013
 *      Oracle and/or its affiliates
 * Licensor: Deutsche Telekom
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; version 2 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * The full text of that licence can be found in the following file:
 * ‣ jdk-linux/src/legal/COPYING
 *
 * As a special exception, the copyright holders of this library give
 * you permission to link this library with independent modules to
 * produce an executable, regardless of the license terms of these
 * independent modules, and to copy and distribute the resulting
 * executable under terms of your choice, provided that you also meet,
 * for each linked independent module, the terms and conditions of the
 * license of that module.  An independent module is a module which is
 * not derived from or based on this library.  If you modify this
 * library, you may extend this exception to your version of the
 * library, but you are not obligated to do so.  If you do not wish
 * to do so, delete this exception statement from your version.
 */

import java.io.IOException;
import java.nio.channels.DatagramChannel;
import java.nio.channels.spi.SelectorProvider;

/**
 * {@link DatagramChannel} equivalent capable of returning and
 * collecting ECN bits and the traffic class octet from received packets.
 * Not suitable for use with IP Multicast.
 *
 * This class offers the methods from {@link AbstractECNBitsDatagramReceiver}
 * to determine the IP traffic class and thus the ECN bits. Any associated
 * datagram socket will also offer them (acting on the channel’s backend).
 *
 * @author mirabilos (t.glaser@tarent.de)
 */
public abstract class ECNBitsDatagramChannel extends DatagramChannel implements AbstractECNBitsDatagramReceiver {
    /**
     * Opens a datagram channel.
     *
     * The new channel is created and will not be connected.
     * ECN-Bits operations to access the traffic class octet will be available,
     * but the underlying socket will not be set up for IP multicast.
     *
     * @return A new datagram channel
     * @throws IOException If an I/O error occurs
     */
    public static ECNBitsDatagramChannel open() throws IOException {
        return new ECNBitsDatagramChannelImpl(null);
    }

    /**
     * Initialises a new instance of this class.
     *
     * @param provider The provider that created this channel
     */
    protected ECNBitsDatagramChannel(final SelectorProvider provider) {
        super(provider);
    }

    /**
     * Retrieves a datagram socket associated with this channel.
     *
     * @return an {@link AbstractECNBitsDatagramSocket} associated with this channel
     */
    @Override
    public abstract AbstractECNBitsDatagramSocket socket();
}
