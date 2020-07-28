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
#include <arpa/inet.h>
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

#define NUMSOCK 16
static struct pollfd pfd[NUMSOCK];

static int do_resolve(const char *host, const char *service);
static void do_packet(int sockfd);
static const char *revlookup(const struct sockaddr *addr, socklen_t addrlen);

int
main(int argc, char *argv[])
{
	int nfd, i;

	if (argc < 2 || argc > 3)
		errx(1, "Usage: %s [servername] port", argv[0]);

	nfd = do_resolve(argc == 2 ? NULL : argv[1], argv[argc == 2 ? 1 : 2]);
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
			do_packet(pfd[i].fd);
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
	case EAI_SYSTEM:
		warn("getnameinfo");
		if (0)
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
	int i, s;
	struct addrinfo *ai, *ap;
	const char *es;
	int n = 0;

	if (!(ap = calloc(1, sizeof(struct addrinfo))))
		err(1, "calloc");
	ap->ai_family = AF_UNSPEC;
	ap->ai_socktype = SOCK_DGRAM;
	ap->ai_flags = AI_ADDRCONFIG | AI_PASSIVE;
	switch ((i = getaddrinfo(host, service, ap, &ai))) {
	case EAI_SYSTEM:
		err(1, "getaddrinfo");
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
		    ap->ai_protocol)) == -1) {
			i = errno;
			putc('\n', stderr);
			errno = i;
			warn("socket");
			continue;
		}
		if (ecnbits_setup(s, ap->ai_family, ECNBITS_ECT0, &es)) {
			i = errno;
			putc('\n', stderr);
			errno = i;
			warn("ecnbits_setup: %s", es);
			close(s);
			continue;
		}
		i = 1;
		if (setsockopt(s, SOL_SOCKET, SO_REUSEADDR, &i, sizeof(i))) {
			i = errno;
			putc('\n', stderr);
			errno = i;
			warn("setsockopt");
			close(s);
			continue;
		}

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
do_packet(int s)
{
	static char data[512];
	ssize_t len;
	struct sockaddr_storage ss;
	struct msghdr mh;
	struct iovec io;
	unsigned char ecn;
	time_t tt;
	char tm[21];
	const char *trc;

	io.iov_base = data;
	io.iov_len = sizeof(data) - 1;

	memset(&mh, 0, sizeof(mh));
	mh.msg_name = &ss;
	mh.msg_namelen = sizeof(ss);
	mh.msg_iov = &io;
	mh.msg_iovlen = 1;

	len = ecnbits_recvmsg(s, &mh, 0, &ecn);
	if (len == (ssize_t)-1) {
		warn("recvmsg");
		return;
	}
	data[len] = '\0';

	time(&tt);
	strftime(tm, sizeof(tm), "%FT%TZ", gmtime(&tt));

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

	printf("%s %s %s %s <%s>\n", tm, trc,
	    revlookup(mh.msg_name, mh.msg_namelen),
	    ECNBITS_DESC(ecn), data);

 /*
	len = snprintf(data, sizeof(data), "%s %s %s %s -> 0",
	    revlookup(mh.msg_name, mh.msg_namelen),
	    tm, ECNBITS_DESC(ecn), trc);
	do {
 */
}
