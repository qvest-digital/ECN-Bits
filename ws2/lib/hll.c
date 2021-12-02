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

#include "ecn-bitw.h"

#define FIELD_SIZEOF(t,f) (sizeof(((t*)0)->f))

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

struct ecnhll_rcv {
	unsigned int nbytes;	/* in/out */
	unsigned int flags;	/* in */
	unsigned int ipscope;	/* out */
	unsigned short port;	/* out */
	unsigned char tosvalid;	/* out */
	unsigned char tosbyte;	/* out */
	unsigned char addr[16];	/* out */
};

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

#include <stdio.h>
#include <string.h>
/*
 * Wraps ecnbits_recvfrom() for .net
 *
 * tbd
 */
ECNBITS_EXPORTAPI int
ecnhll_recv(SOCKET socketfd, void *buf, struct ecnhll_rcv *p)
{
	fprintf(stderr, "ecnhll_recv:socketfd<%08X> buf<%p> rcv<%p>\n",
	    (unsigned)socketfd, buf, p);
unsigned char *cp = (void *)p;
int i;
for (i = 0; i < 32; ++i) {
 fprintf(stderr, " %02X", cp[i]);
 if ((i&15)==15) fprintf(stderr,"\n");
}

	p->addr[0] = 0;
	p->addr[15] = 1;
	p->port = 2;
	memcpy(&p->ipscope, "\x7F\0\0\01", 4);
	*(char *)buf = 42;

	return (0); // or 4 or 6
}

/*

	in: SOCKET socketfd		IntPtr, ECNBits.SocketHandle(Socket socket)
	in: buffer/length		byte[] buffer, Int32 buffer.Length
	in: flags			SocketFlags Enum

     SSIZE_T
     ecnbits_recvfrom(SOCKET fd, void *buf, size_t buflen, int flags,
         struct sockaddr *src_addr, socklen_t *addrlen,
         unsigned short *ecnresult);

	fd ← socketfd
	buf, buflen ← buffer/length
	flags ← flags/0
		1	OutOfBand → MSG_OOB
		2	Peek → MSG_PEEK
	src_addr, addrlen ← local struct sockaddr_storage
	ecnresult ← local unsigned short

	out: error flag / address version
	out: #bytes read (Int32)
	out: socket data
		v4: UInt32 addr		u_long sin_addr.s_addr
		v4: int port		ntohs(sin_port)
		v4 → IPEndpoint((Int64)addr,port)
		v6: byte[16] addr	struct sockaddr_in6 . sin6_addr.s6_addr[]
		v6: UInt32 scope	u_long(w32)/uint32_t(fbsd) sin6_scope_id
		v6: int port		ntohs(sin6_port)
		v6 → IPEndpoint(IPAddress(addr,(Int64)scope),port)
	out: ecnresult

 */
