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
#include <sys/socket.h>
#include <netinet/in.h>
#include <netinet/ip.h>
#include <errno.h>

#include "ecn-bits.h"

static const int on = 1;

int
ecnbits_prep(SOCKET socketfd, int af)
{
	switch (af) {
	case AF_INET:
		if (setsockopt(socketfd, IPPROTO_IP, IP_RECVTOS,
		    (const void *)&on, sizeof(on))) {
			return (2);
		}
		break;
	case AF_INET6:
		if (setsockopt(socketfd, IPPROTO_IPV6, IPV6_RECVTCLASS,
		    (const void *)&on, sizeof(on))) {
			return (2);
		}
		if (setsockopt(socketfd, IPPROTO_IP, IP_RECVTOS,
		    (const void *)&on, sizeof(on))) {
			return (1);
		}
		break;
	default:
#ifdef _WIN32
		WSASetLastError(WSAEAFNOSUPPORT);
#endif
		errno = WSAEAFNOSUPPORT;
		return (2);
	}
	return (0);
}
