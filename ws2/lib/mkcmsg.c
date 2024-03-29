/*-
 * Copyright © 2020, 2023
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
#include <arpa/inet.h>
#include <netinet/in.h>
#include <netinet/ip.h>
#endif
#include <errno.h>
#include <stdlib.h>
#include <string.h>

#include "ecn-bitw.h"

#if defined(_WIN32) || defined(WIN32)

/* from: https://learn.microsoft.com/en-us/windows/win32/winsock/winsock-ecn */
#ifndef IP_ECN
#define IP_ECN		50
#endif
#ifndef IPV6_ECN
#define IPV6_ECN	50
#endif

/* usual portability defines */
#define msg_control	Control.buf
#define msg_controllen	Control.len
#else
#define WSA_CMSG_DATA	CMSG_DATA
#endif

#ifdef _MSC_VER
#pragma warning(disable:4116 4706 4820)
#endif

ECNBITS_EXPORTAPI void *
ecnbits_mkcmsg(void *buf, size_t *lenp, int af, unsigned char tc)
{
	struct cmsghdr *cmsg;
#if defined(_WIN32) || defined(WIN32)
	WSAMSG mh;
#else
	struct msghdr mh;
#endif
	int i = (int)(unsigned int)tc;

	/* determine minimum buffer size */
	switch (af) {
	case AF_INET6:
#if defined(__linux__)
		mh.msg_controllen = 2 * CMSG_SPACE(sizeof(int));
		break;
#endif
	case AF_INET:
		mh.msg_controllen = CMSG_SPACE(sizeof(int));
		break;
	default:
#if defined(_WIN32) || defined(WIN32)
		WSASetLastError(WSAEAFNOSUPPORT);
#endif
		errno = WSAEAFNOSUPPORT;
		return (NULL);
	}

	if (buf) {
		if (*lenp < mh.msg_controllen) {
#if defined(_WIN32) || defined(WIN32)
			WSASetLastError(ERANGE);
#endif
			errno = ERANGE;
			return (NULL);
		}
	} else if (!(buf = calloc(1, mh.msg_controllen))) {
#if defined(_WIN32) || defined(WIN32)
		int eno = errno;

		WSASetLastError(eno);
		errno = eno;
#endif
		return (NULL);
	}

	mh.msg_control = buf;
	*lenp = mh.msg_controllen;
	cmsg = CMSG_FIRSTHDR(&mh);

	switch (af) {
	case AF_INET6:
		cmsg->cmsg_level = IPPROTO_IPV6;
#if defined(_WIN32) || defined(WIN32)
		cmsg->cmsg_type = IPV6_ECN;
#else
		cmsg->cmsg_type = IPV6_TCLASS;
#endif
		cmsg->cmsg_len = CMSG_LEN(sizeof(i));
		memcpy(WSA_CMSG_DATA(cmsg), &i, sizeof(i));
#if defined(__linux__)
		/* send two, for v4-mapped */
		cmsg = CMSG_NXTHDR(&mh, cmsg);
#else
		/* sending multiple cmsg headers is a Linux extension */
		break;
#endif
		/* FALLTHROUGH */
	case AF_INET:
		cmsg->cmsg_level = IPPROTO_IP;
#if defined(_WIN32) || defined(WIN32)
		cmsg->cmsg_type = IP_ECN;
#else
		cmsg->cmsg_type = IP_TOS;
#endif
#if defined(__linux__) || defined(__APPLE__) || \
    defined(_WIN32) || defined(WIN32)
		/*
		 * The generic case below works on Linux 5.7 (Debian) but
		 * fails on Linux 3.18 (Android); this here works on both
		 * but fails on e.g. MidnightBSD because it’s asymmetric:
		 * we get a char, this sends an int. Winsock (even if the
		 * traffic class cannot be set!) also wants this, as does
		 * Darwin (MacOSX).
		 */
		cmsg->cmsg_len = CMSG_LEN(sizeof(i));
		memcpy(WSA_CMSG_DATA(cmsg), &i, sizeof(i));
#else
		cmsg->cmsg_len = CMSG_LEN(sizeof(tc));
		memcpy(WSA_CMSG_DATA(cmsg), &tc, sizeof(tc));
#endif
		break;
	}
	return (buf);
}
