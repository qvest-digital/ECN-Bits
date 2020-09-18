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

/* compat defines (see end of file) */
#if !(defined(_WIN32) || defined(WIN32))
#define SOCKET			int
#define WSAMSG			struct msghdr
#define LPWSAMSG		struct msghdr *
#define SSIZE_T			ssize_t
#endif

#ifdef __cplusplus
extern "C" {
#endif

/* operations on the result value */
#ifdef ECNBITS_INTERNAL
#define ECNBITS_INVALID_BIT	((unsigned short)0x0100U)
#define ECNBITS_ISVALID_BIT	((unsigned short)0x0200U)
#endif
/* valid? (ensure just 0 is undefined) */
#define ECNBITS_VALID(result)	(((unsigned short)(result) >> 8) == 0x02U)
/* extract bits */
#define ECNBITS_BITS(result)	((unsigned char)((result) & 0x03U))
#define ECNBITS_DSCP(result)	((unsigned char)((result) & 0xFCU))
#define ECNBITS_TCOCT(result)	((unsigned char)(result))

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
#ifdef ECNBITS_INTERNAL
#define ECNBITS_WSLCHECK
#endif
int ecnbits_tcfatal(int);
#define ECNBITS_TC_FATAL(rv) ecnbits_tcfatal(rv)
#else
#define ECNBITS_TC_FATAL(rv) ((rv) >= 2)
#endif

/* socket operations */
int ecnbits_prep(SOCKET fd, int af);
int ecnbits_tc(SOCKET fd, int af, unsigned char iptos);
SSIZE_T ecnbits_rdmsg(SOCKET fd, LPWSAMSG msg, int flags,
    unsigned short *ecnresult);

/* utility functions */
void *ecnbits_mkcmsg(void *buf, size_t *lenp, int af, unsigned char tc);
int ecnbits_stoaf(SOCKET fd);

#if defined(_WIN32) || defined(WIN32)
/* convenience functions: POSIXish sendmsg(2) and recvmsg(2) over Winsock2 */
SSIZE_T ecnws2_sendmsg(SOCKET fd, LPWSAMSG msg, int flags);
SSIZE_T ecnws2_recvmsg(SOCKET fd, LPWSAMSG msg, int flags);
#endif

/* wrapped calls */
SSIZE_T ecnbits_recvmsg(SOCKET fd, LPWSAMSG msg, int flags,
    unsigned short *ecnresult);
SSIZE_T ecnbits_recvfrom(SOCKET fd, void *buf, size_t buflen,
    int flags, struct sockaddr *src_addr, socklen_t *addrlen,
    unsigned short *ecnresult);
SSIZE_T ecnbits_recv(SOCKET fd, void *buf, size_t buflen,
    int flags,
    unsigned short *ecnresult);

/* be mindful of different semantics for zero-length datagrams */
#define ecnbits_read(socketfd,buf,buflen,ecnresult) \
	ecnbits_recv((socketfd), (buf), (buflen), 0, (ecnresult))

#ifdef __cplusplus
}
#endif

#ifndef ECNBITS_INTERNAL
/* clean up compat defines except if building the library itself */
#if !(defined(_WIN32) || defined(WIN32))
#undef SOCKET
#undef WSAMSG
#undef LPWSAMSG
#undef SSIZE_T
#endif
#else
/* building the library itself, additional compatibility/utilities */
#if !(defined(_WIN32) || defined(WIN32))
#define WSAEAFNOSUPPORT	EAFNOSUPPORT
#endif
#endif

#endif
