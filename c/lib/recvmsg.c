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
#ifdef _WIN32
#include <winsock2.h>
#else
#include <sys/socket.h>
#include <netinet/in.h>
#include <netinet/ip.h>
#endif
#include <errno.h>
#include <string.h>

#include "ecn-bits.h"

ssize_t
ecnbits_recvmsg(SOCKET s, struct msghdr *mh, int flags, unsigned short *e)
{
	ssize_t rv;
	int eno;
	struct msghdr mrpl;

	if (!e)
		return (recvmsg(s, mh, flags));

	if (mh->msg_control)
		return (ecnbits_rdmsg(s, mh, flags, e));

	memcpy(&mrpl, mh, sizeof(mrpl));
	rv = ecnbits_rdmsg(s, &mrpl, flags, e);
	eno = errno;
	mrpl.msg_control = mh->msg_control;
	mrpl.msg_controllen = mh->msg_controllen;
	memcpy(mh, &mrpl, sizeof(mrpl));
	errno = eno;
	return (rv);
}
