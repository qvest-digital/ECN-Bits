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
#include <errno.h>

#include "ecn-bits.h"

#ifdef ECNBITS_WSLCHECK
#include <stdio.h>
#include <string.h>

static int
iswinorwsl(void)
{
	int iswin = 0;
	int e = errno;
	FILE *fp;
	char buf[256];

	if ((fp = fopen("/proc/sys/kernel/osrelease", "r"))) {
		if (fgets(buf, sizeof(buf), fp) &&
		    strstr(buf, "Microsoft"))
			iswin = 1;
		fclose(fp);
	}
	errno = e;
	return (iswin);
}

int
ecnbits_tcfatal(int rv)
{
	static enum {
		ECN_OS_MAYBEWSL,
		ECN_OS_NOTWIN32,
		ECN_OS_WINORWSL
	} os = ECN_OS_MAYBEWSL;

	if (os == ECN_OS_MAYBEWSL) {
		os = iswinorwsl() ? ECN_OS_WINORWSL : ECN_OS_NOTWIN32;
	}

	if (os != ECN_OS_WINORWSL)
		return (rv >= /* Linux */ 1);

	/* Windows®/WSL, ignore error */
	return (0);
}
#endif

int
ecnbits_tc(int socketfd, int af, unsigned char iptos)
{
	int tos = (int)(unsigned int)iptos;

	switch (af) {
	case AF_INET:
		if (setsockopt(socketfd, IPPROTO_IP, IP_TOS,
		    (const void *)&tos, sizeof(tos))) {
			return (2);
		}
		break;
	case AF_INET6:
		if (setsockopt(socketfd, IPPROTO_IPV6, IPV6_TCLASS,
		    (const void *)&tos, sizeof(tos))) {
			return (2);
		}
		if (setsockopt(socketfd, IPPROTO_IP, IP_TOS,
		    (const void *)&tos, sizeof(tos))) {
			return (1);
		}
		break;
	default:
		errno = EAFNOSUPPORT;
		return (2);
	}
	return (0);
}
