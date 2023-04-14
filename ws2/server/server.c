/*-
 * Copyright © 2020, 2021, 2023
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
#pragma warning(disable:4710 4706 5045)
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
#if defined(_WIN32) || defined(WIN32)
#include "rpl_err.h"
#else
#include <err.h>
#endif
#include <errno.h>
#if !(defined(_WIN32) || defined(WIN32))
#include <netdb.h>
#include <poll.h>
#define WSAPoll poll
#endif
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>
#if !(defined(_WIN32) || defined(WIN32))
#include <unistd.h>
#endif

#include "ecn-bitw.h"

#if defined(_WIN32) || defined(WIN32)
#define iov_base	buf
#define iov_len		len
#define msg_name	name
#define msg_namelen	namelen
#define msg_iov		lpBuffers
#define msg_iovlen	dwBufferCount
#define msg_control	Control.buf
#define msg_controllen	Control.len
#define msg_flags	dwFlags
#define sendmsg		ecnws2_sendmsg
#else
#define SSIZE_T		ssize_t
typedef int SOCKET;
#define INVALID_SOCKET	(-1)
#define closesocket	close
#endif

#ifdef __APPLE__
#define ECNBITS_REUSEPORT SO_REUSEPORT
#else
#define ECNBITS_REUSEPORT SO_REUSEADDR
#endif
/* #undef ECNBITS_REUSEPORT */
/*
 * undefine it if you do not wish for a port reuse
 * socket option to be set; for details, refer to:
 * https://stackoverflow.com/q/64345193/2171120
 */

#define NUMSOCK 16
static struct pollfd pfd[NUMSOCK];
#if defined(_WIN32) || defined(WIN32)
static WSADATA wsaData;
#endif

static int do_resolve(const char *host, const char *service);
static void do_packet(int sockfd, unsigned int dscp);
static const char *revlookup(const struct sockaddr *addr, socklen_t addrlen);

int
main(int argc, char *argv[])
{
	int nfd, i = 1;
	unsigned char dscp = 0;

#if defined(_WIN32) || defined(WIN32)
	if (WSAStartup(MAKEWORD(2,2), &wsaData))
		errx(100, "could not initialise Winsock2");
#endif
	if (argc < 2) {
 earg:
		errx(1, "Usage: %s [+dscp] [servername] port", argv[0]);
	}
	if (argv[1][0] == '+') {
		long mnum;
		char *mep;

		if ((mnum = strtol(argv[1] + 1, &mep, 0)) >= 0L &&
		    mnum < 0x100L && mep != (argv[1] + 1) && !*mep)
			dscp = (unsigned char)(mnum & 0xFC);
		else
			goto earg;
		++i;
	}
	if (argc < (i + 1) || argc > (i + 2))
		goto earg;

	nfd = do_resolve(argc == (i + 1) ? NULL : argv[i],
	    argv[argc == (i + 1) ? i : (i + 1)]);
	if (nfd < 1)
		errx(1, "Could not open server sockets");
	putc('\n', stderr);
	fflush(NULL);
 loop:
	if (WSAPoll(pfd, nfd, -1) < 0)
		ws2err(1, "poll");
	i = 0;
	while (i < nfd) {
		if (pfd[i].revents & POLLIN)
			do_packet(pfd[i].fd, dscp);
		++i;
	}
	goto loop;
}

static const char *
revlookup(const struct sockaddr *addr, socklen_t addrlen)
{
	static char buf[INET6_ADDRSTRLEN + 9];
	int i;
	char nh[INET6_ADDRSTRLEN];
	char np[/* 0‥65535 + NUL */ 6];

	switch ((i = getnameinfo(addr, addrlen,
	    nh, sizeof(nh), np, sizeof(np),
	    NI_NUMERICHOST | NI_NUMERICSERV))) {
#if !(defined(_WIN32) || defined(WIN32))
	case EAI_SYSTEM:
		warn("getnameinfo");
		if (0)
#endif
			/* FALLTHROUGH */
	default:
		  warnx("%s returned %s", "getnameinfo",
		    gai_strerror(i));
		memcpy(buf, "(unknown)", sizeof("(unknown)"));
		break;
	case 0:
		snprintf(buf, sizeof(buf), "[%s]:%s", nh, np);
		break;
	}
	return (buf);
}

static int
do_resolve(const char *host, const char *service)
{
	int i;
	SOCKET s;
	struct addrinfo *ai, *ap;
	int n = 0;

	if (!(ap = calloc(1, sizeof(struct addrinfo))))
		err(1, "calloc");
	ap->ai_family = AF_UNSPEC;
	ap->ai_socktype = SOCK_DGRAM;
	ap->ai_flags = AI_ADDRCONFIG | AI_PASSIVE; /* no AI_V4MAPPED either */
	switch ((i = getaddrinfo(host, service, ap, &ai))) {
#if !(defined(_WIN32) || defined(WIN32))
	case EAI_SYSTEM:
		err(1, "getaddrinfo");
#endif
	default:
		errx(1, "%s returned %s", "getaddrinfo", gai_strerror(i));
	case 0:
		break;
	}
	free(ap);

	for (ap = ai; ap != NULL; ap = ap->ai_next) {
		fprintf(stderr, "Listening on %s...",
		    revlookup(ap->ai_addr, ap->ai_addrlen));

		if (n + 1 > NUMSOCK) {
			fprintf(stderr, " too many sockets, skipped\n");
			continue;
		}

		if ((s = socket(ap->ai_family, ap->ai_socktype,
		    ap->ai_protocol)) == INVALID_SOCKET) {
			i = errno;
			putc('\n', stderr);
			errno = i;
			ws2warn("socket");
			continue;
		}

#ifdef ECNBITS_REUSEPORT
		i = 1;
		if (setsockopt(s, SOL_SOCKET, ECNBITS_REUSEPORT,
		    (const void *)&i, sizeof(i))) {
			i = errno;
			putc('\n', stderr);
			errno = i;
			ws2warn("setsockopt");
			closesocket(s);
			continue;
		}
#endif

		if (ECNBITS_PREP_FATAL(ecnbits_prep(s, ap->ai_family))) {
			i = errno;
			putc('\n', stderr);
			errno = i;
			ws2warn("ecnbits_setup: incoming traffic class");
			closesocket(s);
			continue;
		}

		if (bind(s, ap->ai_addr, ap->ai_addrlen)) {
			i = errno;
			putc('\n', stderr);
			errno = i;
			ws2warn("bind");
			closesocket(s);
			continue;
		}

		pfd[n].fd = s;
		pfd[n].events = POLLIN;
		++n;
		fprintf(stderr, " ok\n");
	}

	freeaddrinfo(ai);
	return (n);
}

static void
do_packet(int s, unsigned int dscp)
{
	static char data[512];
	SSIZE_T len;
	struct sockaddr_storage ss;
#if defined(_WIN32) || defined(WIN32)
	WSAMSG mh = {0};
	WSABUF io;
#else
	struct msghdr mh = {0};
	struct iovec io;
#endif
	unsigned short ecn;
	time_t tt;
	char tm[21];
	const char *trc;
	int af;
	void *cmsgbuf;
	size_t cmsgsz;
	char tcs[3];
#if defined(_WIN32) || defined(WIN32)
	struct tm tmptm;
#define brokendowntime &tmptm
#else
#define brokendowntime gmtime(&tt)
#endif

	io.iov_base = data;
	io.iov_len = sizeof(data) - 1;

	mh.msg_name = (void *)&ss;
	mh.msg_namelen = sizeof(ss);
	mh.msg_iov = &io;
	mh.msg_iovlen = 1;

	len = ecnbits_recvmsg(s, &mh, 0, &ecn);
	if (len == (SSIZE_T)-1) {
		ws2warn("recvmsg");
		return;
	}
	data[len] = '\0';

	time(&tt);
	if ( /* gaaah! */
#if defined(_WIN32) || defined(WIN32)
	    gmtime_s(&tmptm, &tt) ||
#endif
	    strftime(tm, sizeof(tm), "%FT%TZ", brokendowntime) <= 0)
		snprintf(tm, sizeof(tm), "@%08llX", (unsigned long long)tt);

	switch (mh.msg_flags & (MSG_TRUNC | MSG_CTRUNC)) {
	case 0:
		trc = "notrunc"; break;
	case MSG_TRUNC | MSG_CTRUNC:
		trc = "truncMC"; break;
	case MSG_TRUNC:
		trc = "trunc:M"; break;
	case MSG_CTRUNC:
		trc = "trunc:C"; break;
	default:
		trc = "huh?"; break;
	}

	if (ECNBITS_VALID(ecn))
		snprintf(tcs, sizeof(tcs), "%02X", ECNBITS_TCOCT(ecn));
	else
		memcpy(tcs, "??", 3);
	printf("%s %s{%s} %s %s <%s>\n", tm, ECNBITS_DESC(ecn), tcs,
	    trc, revlookup(mh.msg_name, mh.msg_namelen), data);

	if ((af = ecnbits_stoaf(s)) == -1) {
		ws2warn("getsockname");
		return;
	}
	/* pre-allocate one cmsg buffer to reuse */
	if (!(cmsgbuf = ecnbits_mkcmsg(NULL, &cmsgsz, af, 0))) {
		ws2warn("ecnbits_mkcmsg");
		return;
	}
	mh.msg_control = cmsgbuf;
	mh.msg_controllen = cmsgsz;

	len = snprintf(data, sizeof(data), "%s %s %s{%s} %s -> 0x%02X+0",
	    revlookup(mh.msg_name, mh.msg_namelen),
	    tm, ECNBITS_DESC(ecn), tcs, trc, dscp);
	io.iov_len = len;
	do {
		ecnbits_mkcmsg(cmsgbuf, &cmsgsz, af,
		    (unsigned char)(dscp | (data[len - 1] - '0')));
		if (sendmsg(s, &mh, 0) == (SSIZE_T)-1)
			ws2warn("sendmsg for %s", data + (len - 6));
	} while (++data[len - 1] < '4');
}

#if defined(_WIN32) || defined(WIN32)
#include "rpl_err.c"
#endif
