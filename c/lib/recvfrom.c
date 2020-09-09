/*-
 * Copyright © 2020
 *	mirabilos <t.glaser@tarent.de>
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

#include <sys/types.h>
#ifdef _WIN32
#pragma warning(push,1)
#include <winsock2.h>
#pragma warning(pop)
#else
#include <sys/socket.h>
#endif
#include <string.h>

#include "ecn-bits.h"

SOCKIOT
ecnbits_recvfrom(SOCKET s, void *buf, size_t buflen, int flags,
    struct sockaddr *addr, socklen_t *addrlenp, unsigned short *e)
{
	SOCKIOT rv;
	struct msghdr m;
	struct iovec io;

	if (!e)
		return recvfrom(s, buf, buflen, flags, addr, addrlenp);

	io.iov_base = buf;
	io.iov_len = buflen;

	memset(&m, 0, sizeof(m));
	m.msg_iov = &io;
	m.msg_iovlen = 1;
	if (addr) {
		m.msg_name = addr;
		m.msg_namelen = *addrlenp;
	}

	rv = ecnbits_rdmsg(s, &m, flags, e);

	if (rv != SOCKET_ERROR) {
		if (addr)
			*addrlenp = m.msg_namelen;
	}

	return (rv);
}
