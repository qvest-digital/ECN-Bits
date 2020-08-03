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

#if defined(_POSIX_C_SOURCE) && defined(__FreeBSD__)
#define __BSD_VISIBLE 1
#endif

#include <sys/types.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <netinet/ip.h>
#include <errno.h>
#include <stddef.h>

#include "ecn-bits.h"

int
ecnbits_setup(int s, int af, unsigned char iptos, const char **e)
{
	int on = 1;
	int tos;

	switch (af) {
	case AF_INET:
		if (setsockopt(s, IPPROTO_IP, IP_TOS,
		    /*XXX check FreeBSD; Linux accepts &tos here, too */
		    &iptos, sizeof(iptos))) {
			if (e)
				*e = "failed to set up sender TOS";
			return (-1);
		}
		if (setsockopt(s, IPPROTO_IP, IP_RECVTOS,
		    &on, sizeof(on))) {
			if (e)
				*e = "failed to set up receiver TOS";
			return (-1);
		}
		break;
	case AF_INET6:
		tos = (int)(unsigned int)iptos;
		if (setsockopt(s, IPPROTO_IPV6, IPV6_TCLASS,
		    &tos, sizeof(tos))) {
			if (e)
				*e = "failed to set up sender TOS";
			return (-1);
		}
		if (setsockopt(s, IPPROTO_IPV6, IPV6_RECVTCLASS,
		    &on, sizeof(on))) {
			if (e)
				*e = "failed to set up receiver TOS";
			return (-1);
		}
		break;
	default:
		if (e)
			*e = "unknown address family";
		errno = EAFNOSUPPORT;
		return (-1);
	}
	return (0);
}

static char msgbuf[8192];

ssize_t
ecnbits_rdmsg(int s, struct msghdr *msgh, int flags, unsigned char *e)
{
	struct cmsghdr *cmsg;
	ssize_t rv;
	int eno;

	*e = 0;

	if (!msgh->msg_control) {
		msgh->msg_control = msgbuf;
		msgh->msg_controllen = sizeof(msgbuf);
	}

	rv = recvmsg(s, msgh, flags);
	if (rv == (ssize_t)-1)
		return (rv);
	eno = errno;

	cmsg = CMSG_FIRSTHDR(msgh);
	while (cmsg) {
		if ((cmsg->cmsg_level == IPPROTO_IP &&
		     cmsg->cmsg_type == IP_TOS) ||
		    (cmsg->cmsg_level == IPPROTO_IPV6 &&
		     cmsg->cmsg_type == IPV6_TCLASS)) {
			/* https://bugs.debian.org/966459 */
			unsigned char b1, b2;

			b1 = CMSG_DATA(cmsg)[0];
			b2 = CMSG_DATA(cmsg)[3];
			if (b1 == b2)
				*e = IPTOS_ECN(b1) | ECNBITS_VALID_BIT;
			else if (b1 == 0 && b2 != 0)
				*e = IPTOS_ECN(b2) | ECNBITS_VALID_BIT;
			else if (b1 != 0 && b2 == 0)
				*e = IPTOS_ECN(b1) | ECNBITS_VALID_BIT;
			break;
		}
		cmsg = CMSG_NXTHDR(msgh, cmsg);
	}

	errno = eno;
	return (rv);
}
