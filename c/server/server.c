/*-
 * Copyright © 2020, 2021
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
#include <arpa/inet.h>
#include <netinet/in.h>
#include <netinet/ip.h>
#include <err.h>
#include <errno.h>
#include <netdb.h>
#include <poll.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>
#include <unistd.h>

#include "ecn-bits.h"

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

static int do_resolve(const char *host, const char *service);
static void do_packet(int sockfd, unsigned int dscp);
static const char *revlookup(const struct sockaddr *addr, socklen_t addrlen);

int
main(int argc, char *argv[])
{
	int nfd, i = 1;
	unsigned char dscp = 0;

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
	if (poll(pfd, nfd, -1) < 0)
		err(1, "poll");
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

	i = getnameinfo(addr, addrlen, nh, sizeof(nh), np, sizeof(np),
	    NI_NUMERICHOST | NI_NUMERICSERV);
	switch (i) {
	case EAI_SYSTEM:
		warn("getnameinfo");
		if (0)
			/* FALLTHROUGH */
	default:
		  warnx("%s: %s", "getnameinfo", gai_strerror(i));
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
	int i, s;
	struct addrinfo *ai, *ap;
	int n = 0;

	if (!(ap = calloc(1, sizeof(struct addrinfo))))
		err(1, "calloc");
	ap->ai_family = AF_UNSPEC;
	ap->ai_socktype = SOCK_DGRAM;
	ap->ai_flags = AI_ADDRCONFIG | AI_PASSIVE; /* no AI_V4MAPPED either */
	i = getaddrinfo(host, service, ap, &ai);
	switch (i) {
	case EAI_SYSTEM:
		err(1, "getaddrinfo");
	default:
		errx(1, "%s: %s", "getaddrinfo", gai_strerror(i));
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
		    ap->ai_protocol)) == -1) {
			i = errno;
			putc('\n', stderr);
			errno = i;
			warn("socket");
			continue;
		}

#ifdef ECNBITS_REUSEPORT
		i = 1;
		if (setsockopt(s, SOL_SOCKET, ECNBITS_REUSEPORT,
		    (const void *)&i, sizeof(i))) {
			i = errno;
			putc('\n', stderr);
			errno = i;
			warn("setsockopt");
			close(s);
			continue;
		}
#endif

		if (ECNBITS_PREP_FATAL(ecnbits_prep(s, ap->ai_family))) {
			i = errno;
			putc('\n', stderr);
			errno = i;
			warn("ecnbits_setup: incoming traffic class");
			close(s);
			continue;
		}
		/*
		 * ecnbits_tc not needed, as this server uses sendmsg(2) with
		 * explicit tc setting exclusively, but it would be called
		 * here if we used it (note that porting to Winsock2 requires
		 * use of sendmsg with explicit ECN bit setting anyway)
		 */

		if (bind(s, ap->ai_addr, ap->ai_addrlen)) {
			i = errno;
			putc('\n', stderr);
			errno = i;
			warn("bind");
			close(s);
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
now2buf(char *buf, size_t len)
{
	time_t tt;

	time(&tt);
	if (strftime(buf, len, "%FT%TZ", gmtime(&tt)) <= 0)
		snprintf(buf, len, "@%08llX", (unsigned long long)tt);
}

static void
do_packet(int s, unsigned int dscp)
{
	static char data[512];
	ssize_t len;
	struct sockaddr_storage ss;
	struct msghdr mh = {0};
	struct iovec io;
	unsigned short ecn;
	char tm[21];
	const char *trc;
	int af;
	void *cmsgbuf;
	size_t cmsgsz;
	char tcs[3];

	io.iov_base = data;
	io.iov_len = sizeof(data) - 1;

	mh.msg_name = (void *)&ss;
	mh.msg_namelen = sizeof(ss);
	mh.msg_iov = &io;
	mh.msg_iovlen = 1;

	len = ecnbits_rdmsg(s, &mh, 0, &ecn);
	if (len == (ssize_t)-1) {
		warn("recvmsg");
		return;
	}
	data[len] = '\0';

	now2buf(tm, sizeof(tm));

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
		warn("getsockname");
		return;
	}
	/* pre-allocate one cmsg buffer to reuse */
	if (!(cmsgbuf = ecnbits_mkcmsg(NULL, &cmsgsz, af, 0))) {
		warn("ecnbits_mkcmsg");
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
		if (sendmsg(s, &mh, 0) == (ssize_t)-1)
			warn("sendmsg for %s", data + (len - 6));
	} while (++data[len - 1] < '4');
}
