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
#include <netinet/ip.h>
#include <errno.h>
#include <stdlib.h>
#include <string.h>

#include "ecn-bits.h"

int
ecnbits_setup(int s, int af, unsigned char iptos, const char **e)
{
	int on = 1;

	switch (af) {
	case AF_INET:
		if (setsockopt(s, IPPROTO_IP, IP_TOS,
		    &iptos, sizeof(iptos))) {
			if (e)
				*e = "failed to set up sender TOS";
			return (-1);
		}
		if (setsockopt(s, IPPROTO_IP, IP_RECVTOS,
		    &on, sizeof(on))) {
			if (e)
				*e = "failed to set up receiver TOS";
			return (-1);
		}
		break;
	case AF_INET6:
		if (setsockopt(s, IPPROTO_IPV6, IPV6_TCLASS,
		    &iptos, sizeof(iptos))) {
			if (e)
				*e = "failed to set up sender TOS";
			return (-1);
		}
		if (setsockopt(s, IPPROTO_IPV6, IPV6_RECVTCLASS,
		    &on, sizeof(on))) {
			if (e)
				*e = "failed to set up receiver TOS";
			return (-1);
		}
		break;
	default:
		if (e)
			*e = "unknown address family";
		errno = EAFNOSUPPORT;
		return (-1);
	}
	return (0);
}

static char msgbuf[8192];

ssize_t
ecnbits_recvmsg(int s, struct msghdr *mh, int flags, unsigned char *e)
{
	ssize_t rv;
	struct cmsghdr *cmsg;
	struct msghdr *msg;
	unsigned char nok;
	int eno;
	struct msghdr mrpl;

	if (!e)
		return (recvmsg(s, mh, flags));
	*e = 0;

	if ((nok = (mh->msg_control == NULL))) {
		memcpy(&mrpl, mh, sizeof(mrpl));
		mrpl.msg_control = msgbuf;
		mrpl.msg_controllen = sizeof(msgbuf);
		msg = &mrpl;
	} else
		msg = mh;

	rv = recvmsg(s, msg, flags);
	eno = errno;

	if (nok) {
		struct msghdr mres;

		memcpy(&mres, msg, sizeof(mres));
		mres.msg_control = mh->msg_control;
		mres.msg_controllen = mh->msg_controllen;
		memcpy(mh, &mres, sizeof(mres));
	}

	if (rv == (ssize_t)-1) {
		errno = eno;
		return (rv);
	}

	cmsg = CMSG_FIRSTHDR(msg);
	while (cmsg) {
		if ((cmsg->cmsg_level == IPPROTO_IP &&
		     cmsg->cmsg_type == IP_TOS) ||
		    (cmsg->cmsg_level == IPPROTO_IPV6 &&
		     cmsg->cmsg_type == IPV6_TCLASS)) {
			*e = IPTOS_ECN(CMSG_DATA(cmsg)[0]) | 4;
			break;
		}
		cmsg = CMSG_NXTHDR(msg, cmsg);
	}

	errno = eno;
	return (rv);
}
