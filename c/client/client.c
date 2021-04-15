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
#include <err.h>
#include <errno.h>
#include <netdb.h>
#include <poll.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <time.h>

#include "ecn-bits.h"

static int do_resolve(const char *host, const char *service);
static int do_connect(int sfd, int af);

static unsigned char out_tc = ECNBITS_ECT0;
static unsigned char use_sendmsg = 0;

int
main(int argc, char *argv[])
{
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
	return (0);
}

static int
do_resolve(const char *host, const char *service)
{
	int i, s, rv = 1;
	struct addrinfo *ai, *ap;
	char nh[INET6_ADDRSTRLEN];
	char np[/* 0‥65535 + NUL */ 6];

	if (!(ap = calloc(1, sizeof(struct addrinfo))))
		err(1, "calloc");
	ap->ai_family = AF_UNSPEC;
	ap->ai_socktype = SOCK_DGRAM;
	ap->ai_flags = AI_ADDRCONFIG; /* note lack of AI_V4MAPPED */
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
		if (ECNBITS_PREP_FATAL(ecnbits_prep(s, ap->ai_family))) {
			i = errno;
			putc('\n', stderr);
			errno = i;
			warn("ecnbits_setup: incoming traffic class");
			close(s);
			continue;
		}
		if (!use_sendmsg &&
		    ECNBITS_TC_FATAL(ecnbits_tc(s, ap->ai_family, out_tc))) {
			i = errno;
			putc('\n', stderr);
			errno = i;
			warn("ecnbits_setup: outgoing traffic class");
			close(s);
			continue;
		}

		if (connect(s, ap->ai_addr, ap->ai_addrlen)) {
			i = errno;
			putc('\n', stderr);
			errno = i;
			warn("connect");
			close(s);
			continue;
		}

		fprintf(stderr, " connected\n");
		if (do_connect(s, ap->ai_family)) {
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
do_connect(int s, int af)
{
	char buf[512];
	ssize_t n;
	struct pollfd pfd;
	int rv = 1;
	unsigned short ecn;
	time_t tt;
	char tm[21];
	char tcs[3];

	memcpy(buf, "hi!", 3);
	if (use_sendmsg) {
		struct msghdr mh = {0};
		struct iovec io;
		void *cmsgbuf;
		size_t cmsgsz;
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
		n = sendmsg(s, &mh, 0);
		e = errno;
		free(cmsgbuf);
		errno = e;
	} else
		n = write(s, buf, 3);
	if (n == (ssize_t)-1) {
		warn("send");
		return (1);
	}
	if (n != 3)
		warnx("wrote %zu bytes but got %zd", (size_t)3, n);

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

	if ((n = ecnbits_read(s, buf, sizeof(buf) - 1, &ecn)) == (ssize_t)-1) {
		warn("recv");
		return (1);
	}
	time(&tt);
	if (strftime(tm, sizeof(tm), "%FT%TZ", gmtime(&tt)) <= 0)
		snprintf(tm, sizeof(tm), "@%08llX", (unsigned long long)tt);
	buf[n] = '\0';
	if (n > 2 && buf[n - 1] == '\n') {
		buf[n - 1] = '\0';
		if (buf[n - 2] == '\r')
			buf[n - 2] = '\0';
	}
	if (ECNBITS_VALID(ecn))
		snprintf(tcs, sizeof(tcs), "%02X", ECNBITS_TCOCT(ecn));
	else
		memcpy(tcs, "??", 3);
	fprintf(stderr, "%s %s{%s} <%s>\n", tm, ECNBITS_DESC(ecn), tcs, buf);
	rv = 0;
	goto loop;
}
