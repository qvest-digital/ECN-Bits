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
#include <string.h>

#include "ecn-bits.h"

ssize_t
ecnbits_recv(int s, void *buf, size_t buflen, int flags, unsigned short *e)
{
	struct msghdr m = {0};
	struct iovec io;
	char cmsgbuf[ECNBITS_CMSGBUFLEN];

	if (!e)
		return recv(s, buf, buflen, flags);

	io.iov_base = buf;
	io.iov_len = buflen;

	m.msg_iov = &io;
	m.msg_iovlen = 1;
	m.msg_control = cmsgbuf;
	m.msg_controllen = sizeof(cmsgbuf);

	return (ecnbits_rdmsg(s, &m, flags, e));
}
