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
#ifdef DEBUG
#include <stdio.h>
#endif

#include "ecn-bits.h"

#if !defined(IPTOS_ECN) && defined(IPTOS_ECN_MASK)
#define IPTOS_ECN(tos) ((tos) & IPTOS_ECN_MASK)
#endif

int
ecnbits_setup(int s, int af, unsigned char iptos, const char **e)
{
	int on = 1;
	int tos = (int)(unsigned int)iptos;

	switch (af) {
	case AF_INET:
		if (setsockopt(s, IPPROTO_IP, IP_TOS,
		    (const void *)&tos, sizeof(tos))) {
			if (e)
				*e = "failed to set up sender TOS";
			return (-1);
		}
		if (setsockopt(s, IPPROTO_IP, IP_RECVTOS,
		    (const void *)&on, sizeof(on))) {
			if (e)
				*e = "failed to set up receiver TOS";
			return (-1);
		}
		break;
	case AF_INET6:
		if (setsockopt(s, IPPROTO_IPV6, IPV6_TCLASS,
		    (const void *)&tos, sizeof(tos))) {
			if (e)
				*e = "failed to set up sender TOS";
			return (-1);
		}
		if (setsockopt(s, IPPROTO_IPV6, IPV6_RECVTCLASS,
		    (const void *)&on, sizeof(on))) {
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

static size_t
cmsg_actual_data_len(const struct cmsghdr *cmsg)
{
	union {
		const struct cmsghdr *cmsg;
		const unsigned char *uc;
	} ptr[(
		/* compile-time assertions */
		sizeof(socklen_t) <= sizeof(size_t)
	    ) ? 1 : -1];
	ptrdiff_t pd;

	ptr[0].cmsg = cmsg;
	pd = CMSG_DATA(cmsg) - ptr[0].uc;
	return ((size_t)cmsg->cmsg_len - (size_t)pd);
}

static void
recvtos_cmsg(struct cmsghdr *cmsg, unsigned char *e)
{
	unsigned char b1, b2;
	unsigned char *d = CMSG_DATA(cmsg);

	/* https://bugs.debian.org/966459 */
	switch (cmsg_actual_data_len(cmsg)) {
	case 0:
		/* huh? */
		return;
	case 3:
	case 2:
		/* shouldn’t happen, but… */
	case 1:
		b1 = d[0];
		break;
	default:
		/* most likely an int, but… */
		b1 = d[0];
		b2 = d[3];
		if (b1 == b2)
			break;
		if (b1 != 0 && b2 == 0)
			break;
		if (b1 == 0 && b2 != 0) {
			b1 = b2;
			break;
		}
		/* inconsistent, no luck */
		return;
	}
	*e = IPTOS_ECN(b1) | ECNBITS_VALID_BIT;
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
#ifdef DEBUG
	fprintf(stderr, "D: received message, cmsg=%p\n", cmsg);
#endif
	while (cmsg) {
#ifdef DEBUG
		unsigned char *dp = CMSG_DATA(cmsg);
		size_t dl = cmsg_actual_data_len(cmsg), di = 0;

		fprintf(stderr, "D: cmsg hdr (%d, %d) len %zu\n",
		    cmsg->cmsg_level, cmsg->cmsg_type, dl);
		while (di < dl) {
			if ((di & 15) == 0)
				fprintf(stderr, "N: %04zX ", di);
			if ((di & 15) == 8)
				fputc(' ', stderr);
			fprintf(stderr, " %02X", dp[di++]);
			if ((di & 15) == 0 || di == dl)
				fputc('\n', stderr);
		}
		fflush(stderr);
#endif
		switch (cmsg->cmsg_level) {
		case IPPROTO_IP:
			switch (cmsg->cmsg_type) {
			case IP_TOS:
			case IP_RECVTOS:
				recvtos_cmsg(cmsg, e);
				break;
			}
			break;
		case IPPROTO_IPV6:
			switch (cmsg->cmsg_type) {
			case IPV6_TCLASS:
				recvtos_cmsg(cmsg, e);
				break;
			}
			break;
		}
		cmsg = CMSG_NXTHDR(msgh, cmsg);
#ifdef DEBUG
		if (!cmsg)
			fprintf(stderr, "D: end of cmsgs\n");
#endif
	}

	errno = eno;
	return (rv);
}
