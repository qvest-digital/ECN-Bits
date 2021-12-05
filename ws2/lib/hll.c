/*-
 * Copyright © 2021
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
#if defined(_WIN32) || defined(WIN32)
#pragma warning(push,1)
#include <winsock2.h>
#include <ws2tcpip.h>
#pragma warning(pop)
#else
#include <sys/socket.h>
#include <netinet/in.h>
#include <netinet/ip.h>
#endif
#include <errno.h>
#include <stddef.h>
#include <string.h>

#include "ecn-bitw.h"

#define FIELD_SIZEOF(t,f) (sizeof(((t*)0)->f))

/* compile-time assert */
struct ecnhll_rcv_cta_size {
	/* size of ecnhll_rcv must be known for C and C# and match */
	char size_correct[sizeof(struct ecnhll_rcv) == 32 ? 1 : -1];
	/* test various offsets */
	char offsets_correct[(
		offsetof(struct ecnhll_rcv, nbytes) == 0 &&
		offsetof(struct ecnhll_rcv, flags) == 4 &&
		offsetof(struct ecnhll_rcv, ipscope) == 8 &&
		offsetof(struct ecnhll_rcv, port) == 12 &&
		offsetof(struct ecnhll_rcv, tosvalid) == 14 &&
		offsetof(struct ecnhll_rcv, tosbyte) == 15 &&
		offsetof(struct ecnhll_rcv, addr) == 16 &&
	    FIELD_SIZEOF(struct ecnhll_rcv, addr) == 16) ? 1 : -1];
};

#if (AF_INET != 0) && (AF_INET6 != 0)
/*
 * Wraps ecnbits_prep() for high-level languages.
 *
 * af: address family: 4=IPv4, 6=IPv6 (or 0 if unknown, causes an error)
 *
 * Returns errors via WSAGetLastError and errno if the return value
 * is >= 2, 1 on Linux if v4-mapped IPv6 addresses must be supported.
 */
ECNBITS_EXPORTAPI int
ecnhll_prep(SOCKET socketfd, int af)
{
	return (ecnbits_prep(socketfd, af == 6 ? AF_INET6 :
	    af == 4 ? AF_INET : 0));
}
#else
# error AF_INET or AF_INET6 conflict with the error af value
#endif

/*
 * Wraps ecnbits_recvfrom() for .net
 *
 * Returns -1 on error, 4 if src_addr is AF_INET, 6 for AF_INET6,
 * and 0 otherwise setting errno suitably.
 */
ECNBITS_EXPORTAPI int
ecnhll_recv(SOCKET socketfd, void *buf, struct ecnhll_rcv *p)
{
	int flags = 0;
	struct sockaddr_storage ss;
	socklen_t slen = sizeof(ss);
	SSIZE_T len;
	unsigned short ecn;

	if (p->flags & 1)
		flags |= MSG_OOB;
	if (p->flags & 2)
		flags |= MSG_PEEK;
	if ((len = ecnbits_recvfrom(socketfd, buf, p->nbytes, flags,
	    (struct sockaddr *)&ss, &slen, &ecn)) == -1)
		return (-1);
	p->nbytes = (unsigned int)len;
	if (ECNBITS_VALID(ecn)) {
		p->tosbyte = ECNBITS_TCOCT(ecn);
		p->tosvalid = 1;
	} else
		p->tosvalid = 0;
	switch (ss.ss_family) {
	case AF_INET6: {
		struct sockaddr_in6 *sin6 = (void *)&ss;

		p->ipscope = ntohl(sin6->sin6_scope_id);
		p->port = ntohs(sin6->sin6_port);
		memcpy(p->addr, sin6->sin6_addr.s6_addr, 16);
		return (6);
	    }
	case AF_INET: {
		struct sockaddr_in *sin = (void *)&ss;

		p->ipscope = sin->sin_addr.s_addr;
		p->port = ntohs(sin->sin_port);
		return (4);
	    }
	default:
#if defined(_WIN32) || defined(WIN32)
		WSASetLastError(WSAEAFNOSUPPORT);
#endif
		errno = WSAEAFNOSUPPORT;
		return (0);
	}
}
