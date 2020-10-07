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
#include <sys/utsname.h>
#include <stdlib.h>
#include <string.h>

static int
isWSL(void)
{
	int e = errno;
	struct utsname u;
	int uerr;

	/* check uname first (this is improbable to fail) */
	if ((uerr = uname(&u)) == 0) {
		if (strstr(u.release, "Microsoft") != NULL) {
			/* pretty certainly WSL 1 */
			errno = e;
			return (1);
		}
		if (strstr(u.release, "microsoft") != NULL) {
			/* probably WSL 2 */
			errno = e;
			return (2);
		}
	}

	/* check presence of environment variables next */
	/* hoping the user did not change them */

	if (getenv("WSL_INTEROP") != NULL) {
		/* relatively certainly WSL 2 */
		errno = e;
		return (2);
	}
	if (getenv("WSL_DISTRO_NAME") != NULL) {
		/* relatively certainly WSL under a WSL-1/2-capable NT */
		/* since we detect WSL 1 above, this is probably 2 */
		/* assume WSL 1 when uname(2) failed, though */
		errno = e;
		return (uerr ? 1 : 2);
	}

	/* other checks are even more fragile, so let’s go with not WSL */
	errno = e;
	return (0);
}

#ifdef WSLCHECK_MAIN
int
main(void)
{
	return (isWSL());
}
#endif

/*
 * Note this is only compiled for “Linux and not Android” as
 * other OSes have the check in ../inc/ecn-bits.h instead:
 * BSD does not require v4-mapped support; Win32 ignores error;
 * Android checks like Linux but can skip WSL check if known compile-time
 */
int
ecnbits_tcfatal(int rv)
{
	static signed char iswsl = -1;

	if (iswsl == -1)
		iswsl = (signed char)isWSL();

	if (iswsl == 1) {
		/* WSL 1 ⇒ ignore error */
		return (0);
	}
	/* Linux or WSL 2 */

	/* ensure both regular and v4-mapped calls work */
	return (rv >= 1);
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
