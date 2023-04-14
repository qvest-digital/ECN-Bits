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
#if !(defined(_WIN32) || defined(WIN32))
#include <unistd.h>
#endif
#include <time.h>

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
typedef int SOCKIOT;
#else
#define SSIZE_T		ssize_t
typedef int SOCKET;
#define INVALID_SOCKET	(-1)
#define closesocket	close
typedef SSIZE_T SOCKIOT;
#define SOCKET_ERROR	((SOCKIOT)-1)
#endif

static int do_resolve(const char *host, const char *service);
static int do_connect(SOCKET sfd, int af);

static unsigned char out_tc = ECNBITS_ECT0;
static unsigned char use_sendmsg = 0;

#if defined(_WIN32) || defined(WIN32)
static WSADATA wsaData;
#endif

int
main(int argc, char *argv[])
{
#if defined(_WIN32) || defined(WIN32)
	int ec;

	if ((ec = WSAStartup(MAKEWORD(2,2), &wsaData)))
		ws2startuperr(100, ec, "could not initialise Winsock2");
#endif
	if (argc == 4) {
		long mnum;
		char *mep;

		if (argv[3][0] == '!') {
			use_sendmsg = 1;
			++argv[3];
		}

		if (!strcmp(argv[3], "NO"))
			out_tc = ECNBITS_NON;
		else if (!strcmp(argv[3], "ECT0"))
			out_tc = ECNBITS_ECT0;
		else if (!strcmp(argv[3], "ECT1"))
			out_tc = ECNBITS_ECT1;
		else if (!strcmp(argv[3], "CE"))
			out_tc = ECNBITS_CE;
		else if ((mnum = strtol(argv[3], &mep, 0)) >= 0L &&
		    mnum < 0x100L && mep != argv[3] && !*mep)
			out_tc = (unsigned char)mnum;
		else
			errx(1, "Unknown traffic class: %s", argv[3]);
	} else if (argc != 3)
		errx(1, "Usage: %s servername port [tc]", argv[0]);

	if (do_resolve(argv[1], argv[2]))
		errx(1, "Could not connect to server or received no response");
#if defined(_WIN32) || defined(WIN32)
	WSACleanup();
#endif
	return (0);
}

static int
do_resolve(const char *host, const char *service)
{
	int i, rv = 1;
	SOCKET s;
	struct addrinfo *ai, *ap;
	char nh[INET6_ADDRSTRLEN];
	char np[/* 0‥65535 + NUL */ 6];

	if (!(ap = calloc(1, sizeof(struct addrinfo))))
		err(1, "calloc");
	ap->ai_family = AF_UNSPEC;
	ap->ai_socktype = SOCK_DGRAM;
	ap->ai_flags = AI_ADDRCONFIG; /* note lack of AI_V4MAPPED */
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
		switch ((i = getnameinfo(ap->ai_addr, ap->ai_addrlen,
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
			fprintf(stderr, "Trying (unknown)...");
			break;
		case 0:
			fprintf(stderr, "Trying [%s]:%s...", nh, np);
			break;
		}

		if ((s = socket(ap->ai_family, ap->ai_socktype,
		    ap->ai_protocol)) == INVALID_SOCKET) {
			i = errno;
			putc('\n', stderr);
			errno = i;
			ws2warn("socket");
			continue;
		}
		if (ECNBITS_PREP_FATAL(ecnbits_prep(s, ap->ai_family))) {
			i = errno;
			putc('\n', stderr);
			errno = i;
			ws2warn("ecnbits_setup: incoming traffic class");
			closesocket(s);
			continue;
		}
		if (!use_sendmsg &&
		    ECNBITS_TC_FATAL(ecnbits_tc(s, ap->ai_family, out_tc))) {
			i = errno;
			putc('\n', stderr);
			errno = i;
			ws2warn("ecnbits_setup: outgoing traffic class");
			closesocket(s);
			continue;
		}

		if (connect(s, ap->ai_addr, ap->ai_addrlen)) {
			i = errno;
			putc('\n', stderr);
			errno = i;
			ws2warn("connect");
			closesocket(s);
			continue;
		}

		fprintf(stderr, " connected\n");
		if (do_connect(s, ap->ai_family)) {
			closesocket(s);
			continue;
		}

		rv = 0;
		closesocket(s);
		/* return */
	}

	freeaddrinfo(ai);
	return (rv);
}

static int
do_connect(SOCKET s, int af)
{
	char buf[512];
	SOCKIOT nsend;
	SSIZE_T nrecv;
	struct pollfd pfd;
	int rv = 1;
	unsigned short ecn;
	time_t tt;
	char tm[21];
	char tcs[3];
#if defined(_WIN32) || defined(WIN32)
	struct tm tmptm;
#define brokendowntime &tmptm
#else
#define brokendowntime gmtime(&tt)
#endif

	memcpy(buf, "hi!", 3);
	if (use_sendmsg) {
#if defined(_WIN32) || defined(WIN32)
		WSAMSG mh = {0};
		WSABUF io;
#else
		struct msghdr mh = {0};
		struct iovec io;
#endif
		void *cmsgbuf;
		size_t cmsgsz;
		SSIZE_T nsmsg;
		int e;

		if (!(cmsgbuf = ecnbits_mkcmsg(NULL, &cmsgsz, af, out_tc))) {
			warn("ecnbits_mkcmsg");
			return (1);
		}

		io.iov_base = buf;
		io.iov_len = 3;

		mh.msg_iov = &io;
		mh.msg_iovlen = 1;
		mh.msg_control = cmsgbuf;
		mh.msg_controllen = cmsgsz;
		nsmsg = sendmsg(s, &mh, 0);
		e = errno;
		free(cmsgbuf);
		errno = e;
		nsend = nsmsg == (SSIZE_T)-1 ? SOCKET_ERROR : (SOCKIOT)nsmsg;
	} else
		nsend = send(s, buf, 3, 0);
	if (nsend == SOCKET_ERROR) {
		ws2warn("send");
		return (1);
	}
	if (nsend != 3)
		warnx("wrote %zu bytes but got %zd", (size_t)3, (SSIZE_T)nsend);

 loop:
	pfd.fd = s;
	pfd.events = POLLIN;
	switch (WSAPoll(&pfd, 1, 1000)) {
	case 1:
		break;
	case 0:
		if (rv)
			warnx("timeout waiting for packet");
		return (rv);
	default:
		ws2warn("poll");
		return (1);
	}

	if ((nrecv = ecnbits_read(s, buf, sizeof(buf) - 1,
	    &ecn)) == (SSIZE_T)-1) {
		ws2warn("recv");
		return (1);
	}
	time(&tt);
	if ( /* gaaah! */
#if defined(_WIN32) || defined(WIN32)
	    gmtime_s(&tmptm, &tt) ||
#endif
	    strftime(tm, sizeof(tm), "%FT%TZ", brokendowntime) <= 0)
		snprintf(tm, sizeof(tm), "@%08llX", (unsigned long long)tt);
	buf[nrecv] = '\0';
	if (nrecv > 2 && buf[nrecv - 1] == '\n') {
		buf[nrecv - 1] = '\0';
		if (buf[nrecv - 2] == '\r')
			buf[nrecv - 2] = '\0';
	}
	if (ECNBITS_VALID(ecn))
		snprintf(tcs, sizeof(tcs), "%02X", ECNBITS_TCOCT(ecn));
	else
		memcpy(tcs, "??", 3);
	fprintf(stderr, "%s %s{%s} <%s>\n", tm, ECNBITS_DESC(ecn), tcs, buf);
	rv = 0;
	goto loop;
}

#if defined(_WIN32) || defined(WIN32)
#include "rpl_err.c"
#endif
