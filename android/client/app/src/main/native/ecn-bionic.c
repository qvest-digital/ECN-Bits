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
/*#include <netinet6/in6.h>*/
#include <errno.h>
#include <stddef.h>
#include <string.h>
#include <android/log.h>

#include "ecn-ndk.h"

#define MSGBUFSZ 48

#define sb(a)	&(a), sizeof(a)

int
ecnbits_setup(int s)
{
	int on = 1;
	struct sockaddr sa;
	socklen_t sa_len = sizeof(sa);

	if (getsockname(s, &sa, &sa_len)) {
		__android_log_print(ANDROID_LOG_ERROR, "ECN-JNI",
		    "could not get socket address family");
		return (1);
	}

	switch (sa.sa_family) {
	case AF_INET:
		if (setsockopt(s, IPPROTO_IP, IP_RECVTOS,
		    (const void *)&on, sizeof(on))) {
			__android_log_print(ANDROID_LOG_ERROR, "ECN-JNI",
			    "could not set up IPv4 socket");
			return (1);
		}
		break;
	case AF_INET6:
		if (setsockopt(s, IPPROTO_IPV6, IPV6_RECVTCLASS,
		    (const void *)&on, sizeof(on))) {
			__android_log_print(ANDROID_LOG_ERROR, "ECN-JNI",
			    "could not set up IPv6 socket");
			return (1);
		}
		if (setsockopt(s, IPPROTO_IP, IP_RECVTOS,
		    (const void *)&on, sizeof(on))) {
			__android_log_print(ANDROID_LOG_ERROR, "ECN-JNI",
			    "could not set up IPv6 socket for IPv4");
			return (1);
		}
		break;
	default:
		__android_log_print(ANDROID_LOG_ERROR, "ECN-JNI",
		    "could not set up socket: unknown address family");
		return (1);
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
recvtos_cmsg(struct cmsghdr *cmsg, unsigned short *e)
{
	unsigned char b1, b2;
	unsigned char *d = CMSG_DATA(cmsg);
	size_t len;

	/* https://bugs.debian.org/966459 */
	len = cmsg_actual_data_len(cmsg);
	switch (len) {
	case 0:
		/* huh? */
		__android_log_print(ANDROID_LOG_ERROR, "ECN-JNI",
		    "empty traffic class cmsg");
		return;
	case 3:
	case 2:
		/* shouldn’t happen, but… */
		__android_log_print(ANDROID_LOG_WARN, "ECN-JNI",
		    "odd-sized traffic class cmsg (%zu)", len);
		/* FALLTHROUGH */
	case 1:
		b1 = d[0];
		break;
	default:
		/* most likely an int, but… */
		__android_log_print(ANDROID_LOG_WARN, "ECN-JNI",
		    "oversized traffic class cmsg (%zu)", len);
		/* FALLTHROUGH */
	case 4:
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
		__android_log_print(ANDROID_LOG_ERROR, "ECN-JNI",
		    "inconsistent traffic class cmsg %02X %02X %02X %02X (%zu)",
		    (unsigned int)d[0], (unsigned int)d[1],
		    (unsigned int)d[2], (unsigned int)d[3], len);
		return;
	}
	*e = (unsigned short)(b1 & 0xFFU) | ECNBITS_ISVALID_BIT;
}

static ssize_t
ecnbits_rdmsg(int s, struct msghdr *msgh, int flags, unsigned short *e)
{
	struct cmsghdr *cmsg;
	ssize_t rv;
	char msgbuf[MSGBUFSZ];

	*e = ECNBITS_INVALID_BIT;

	if (!msgh->msg_control) {
		msgh->msg_control = msgbuf;
		msgh->msg_controllen = sizeof(msgbuf);
	}

	rv = recvmsg(s, msgh, flags);
	if (rv == (ssize_t)-1)
		return (rv);

	if (msgh->msg_flags & MSG_CTRUNC) {
		/* 48 is enough normally but… */
		__android_log_print(ANDROID_LOG_ERROR, "ECN-JNI",
		    "cmsg truncated, increase MSGBUFSZ and recompile!");
	}

	cmsg = CMSG_FIRSTHDR(msgh);
	while (cmsg) {
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
	}

	return (rv);
}

ssize_t
ecnbits_jrecv(int fd, int dopeek, unsigned short *tcv, struct iovec *iov,
    void (*cb)(void *ep, void *ap, const void *buf, size_t len,
      unsigned short port),
    void *ep, void *ap)
{
	ssize_t rv;
	struct msghdr m;
	union {
		struct sockaddr_storage s;
		struct sockaddr_in in;
		struct sockaddr_in6 in6;
	} ss;

	memset(&m, 0, sizeof(m));
	memset(&ss, 0, sizeof(ss));
	m.msg_name = &ss;
	m.msg_namelen = sizeof(ss);
	m.msg_iov = iov;
	m.msg_iovlen = 1;

	if ((rv = ecnbits_rdmsg(fd, &m, dopeek ? MSG_PEEK : 0,
	    tcv)) != (ssize_t)-1) {
		switch (ss.s.ss_family) {
		case AF_INET:
			(*cb)(ep, ap, sb(ss.in.sin_addr.s_addr),
			    ntohs(ss.in.sin_port));
			break;
		case AF_INET6:
			(*cb)(ep, ap, sb(ss.in6.sin6_addr.s6_addr),
			    ntohs(ss.in6.sin6_port));
			break;
		default:
			__android_log_print(ANDROID_LOG_ERROR, "ECN-JNI",
			    "bogus address family");
			errno = EAFNOSUPPORT;
			rv = -1;
			break;
		}
	}
	return (rv);
}
