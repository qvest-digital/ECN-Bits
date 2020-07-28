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
#include <unistd.h>

#include "ecn-bits.h"

static int do_resolve(const char *host, const char *service);
static int do_connect(int sfd);

int
main(int argc, char *argv[])
{
	if (argc != 3)
		errx(1, "Usage: %s servername port", argv[0]);

	if (do_resolve(argv[1], argv[2]))
		errx(1, "Could not connect to server");
	return (0);
}

static int
do_resolve(const char *host, const char *service)
{
	int i, s, rv = 1;
	struct addrinfo *ai, *ap;
	char nh[INET6_ADDRSTRLEN];
	char np[/* 0‥65535 + NUL */ 6];
	const char *es;

	if (!(ap = calloc(1, sizeof(struct addrinfo))))
		err(1, "calloc");
	ap->ai_family = AF_UNSPEC;
	ap->ai_socktype = SOCK_DGRAM;
	ap->ai_flags = AI_ADDRCONFIG;
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
		switch ((i = getnameinfo(ap->ai_addr, ap->ai_addrlen,
		    nh, sizeof(nh), np, sizeof(np),
		    NI_NUMERICHOST | NI_NUMERICSERV))) {
		case EAI_SYSTEM:
			warn("getnameinfo");
			if (0)
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
			continue;
		}

		/* set up socket for ECN */
		if (connect(s, ap->ai_addr, ap->ai_addrlen)) {
			i = errno;
			putc('\n', stderr);
			errno = i;
			warn("connect");
			close(s);
			continue;
		}

		fprintf(stderr, " connected\n");
		if (do_connect(s)) {
			close(s);
			continue;
		}

		rv = 0;
		close(s);
		/* return */
	}

	freeaddrinfo(ai);
	return (rv);
}

static int
do_connect(int s)
{
	char buf[512];
	ssize_t n;
	struct pollfd pfd;
	int rv = 1;

	memcpy(buf, "hi\n", 3);
	if ((n = write(s, buf, 3)) != 3) {
		if (n == (ssize_t)-1) {
			warn("send");
			return (1);
		}
		warnx("wrote %zu bytes but got %zd", (size_t)3, n);
	}

 loop:
	pfd.fd = s;
	pfd.events = POLLIN;
	switch (poll(&pfd, 1, 1000)) {
	case 1:
		break;
	case 0:
		if (rv)
			warnx("timeout waiting for packet");
		return (rv);
	default:
		warn("poll");
		return (1);
	}

	if ((n = read(s, buf, sizeof(buf) - 1)) == -1) {
		warn("recv");
		return (1);
	}
	buf[n] = '\0';
	fprintf(stderr, "received <%s>\n", buf);
	rv = 0;
	goto loop;
}
