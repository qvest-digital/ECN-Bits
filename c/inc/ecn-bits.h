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

#ifndef ECN_BITS_H
#define ECN_BITS_H

#ifdef __cplusplus
extern "C" {
#endif

/* operations on the result value */
#ifdef ECNBITS_INTERNAL
#define ECNBITS_VALID_BIT	4
#endif
#define ECNBITS_VALID(result)	(((result) & 4) == 4)	/* valid? */
#define ECNBITS_BITS(result)	((result) & 3)		/* extract bits */

#define ECNBITS_DESC(result)	(ECNBITS_VALID(result) ?	\
		ecnbits_shortnames[ECNBITS_BITS(result)] :	\
		"??ECN?")

/* ECN bits’ meanings */
#define ECNBITS_NON		0 /* nōn-ECN-capable transport */
#define ECNBITS_ECT0		2 /* ECN-capable; L4S: legacy transport */
#define ECNBITS_ECT1		1 /* ECN-capable; L4S: L4S-aware transport */
#define ECNBITS_CE		3 /* congestion experienced */
extern const char *ecnbits_meanings[4];
extern const char *ecnbits_shortnames[4];

/* setup return values */
#if defined(__linux__)
#define ECNBITS_PREP_FATAL(rv) ((rv) >= 1)
#else
#define ECNBITS_PREP_FATAL(rv) ((rv) >= 2)
#endif

#if defined(_WIN32) || defined(WIN32)
#define ECNBITS_TC_FATAL(rv) ((rv), 0)
#elif defined(__linux__) && !defined(__ANDROID__)
#define ECNBITS_WSLCHECK
int ecnbits_tcfatal(int);
#define ECNBITS_TC_FATAL(rv) ecnbits_tcfatal(rv)
#else
#define ECNBITS_TC_FATAL(rv) ((rv) >= 2)
#endif

/* socket operations */
int ecnbits_prep(int socketfd, int af);
int ecnbits_tc(int socketfd, int af, unsigned char iptos);
int ecnbits_setup(int socketfd, int af, unsigned char iptos,
    const char **errstring);
ssize_t ecnbits_rdmsg(int socketfd, struct msghdr *msg, int flags,
    unsigned char *ecnbits);

/* wrapped calls */
ssize_t ecnbits_recvmsg(int socketfd, struct msghdr *msg, int flags,
    unsigned char *ecnbits);
ssize_t ecnbits_recvfrom(int socketfd, void *buf, size_t buflen,
    int flags, struct sockaddr *src_addr, socklen_t *addrlen,
    unsigned char *ecnbits);
ssize_t ecnbits_recv(int socketfd, void *buf, size_t buflen,
    int flags,
    unsigned char *ecnbits);

/* be mindful of different semantics for zero-length datagrams */
#define ecnbits_read(socketfd,buf,buflen,ecnbits) \
	ecnbits_recv((socketfd), (buf), (buflen), 0, (ecnbits))

#ifdef __cplusplus
}
#endif

#endif
