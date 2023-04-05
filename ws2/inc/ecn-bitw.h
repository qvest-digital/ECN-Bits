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

#ifndef ECN_BITS_H
#define ECN_BITS_H

/* compat defines (see end of file) */
#if !(defined(_WIN32) || defined(WIN32))
#define SOCKET			int
#define WSAMSG			struct msghdr
#define LPWSAMSG		struct msghdr *
#define SSIZE_T			ssize_t
#endif

/* stuff to make DLLs work; we offer the cdecl calling convention */
#if !defined(ECNBITS_WIN32_DLL)
#define ECNBITS_EXPORTAPI	/* nothing */
#elif !defined(ECNBITS_INTERNAL)
#define ECNBITS_EXPORTAPI	__declspec(dllimport)
#else
#define ECNBITS_EXPORTAPI	__declspec(dllexport)
#endif

#ifdef __cplusplus
extern "C" {
#endif

/* control message buffer size */
#ifdef ECNBITS_INTERNAL
/*
 * 16 or 20 bytes on (most) OSes for an int, less than twice that
 * for an IPv6 packet info struct, so use this for recv/recvfrom,
 * twice that for recvmsg where chances for more cmsgs are higher
 */
#define ECNBITS_CMSGBUFLEN 64
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
extern ECNBITS_EXPORTAPI const char *ecnbits_meanings[4];
extern ECNBITS_EXPORTAPI const char *ecnbits_shortnames[4];

/* setup return values */
#if !defined(__linux__)
/* ignore v4-mapped setup failure */
#define ECNBITS_PREP_FATAL(rv) ((rv) >= 2)
#else
/* require v4-mapped setup */
#define ECNBITS_PREP_FATAL(rv) ((rv) >= 1)
#endif

/* socket operations */
ECNBITS_EXPORTAPI int ecnbits_prep(SOCKET fd, int af);
ECNBITS_EXPORTAPI SSIZE_T ecnbits_rdmsg(SOCKET fd, LPWSAMSG msg, int flags,
    unsigned short *ecnresult);

/* utility functions */
ECNBITS_EXPORTAPI int ecnbits_stoaf(SOCKET fd);
ECNBITS_EXPORTAPI void *ecnbits_mkcmsg(void *buf, size_t *lenp, int af,
    unsigned char tc);

#if defined(_WIN32) || defined(WIN32)
/* convenience functions: POSIXish sendmsg(2) and recvmsg(2) over Winsock2 */
ECNBITS_EXPORTAPI SSIZE_T ecnws2_sendmsg(SOCKET fd, LPWSAMSG msg, int flags);
ECNBITS_EXPORTAPI SSIZE_T ecnws2_recvmsg(SOCKET fd, LPWSAMSG msg, int flags);
#endif

/* wrapped calls */
ECNBITS_EXPORTAPI SSIZE_T ecnbits_recvmsg(SOCKET fd, LPWSAMSG msg, int flags,
    unsigned short *ecnresult);
ECNBITS_EXPORTAPI SSIZE_T ecnbits_recvfrom(SOCKET fd, void *buf, size_t buflen,
    int flags, struct sockaddr *src_addr, socklen_t *addrlen,
    unsigned short *ecnresult);
ECNBITS_EXPORTAPI SSIZE_T ecnbits_recv(SOCKET fd, void *buf, size_t buflen,
    int flags,
    unsigned short *ecnresult);

/* be mindful of different semantics for zero-length datagrams */
#define ecnbits_read(socketfd,buf,buflen,ecnresult) \
	ecnbits_recv((socketfd), (buf), (buflen), 0, (ecnresult))

/* parameter block for HLL function */
struct ecnhll_rcv {
	unsigned int nbytes;	/* in/out, keep <= INT_MAX */
	unsigned int flags;	/* in 1=OutOfBand 2=Peek */
	unsigned int ipscope;	/* out (v6: scope; v4: sin_addr.s_addr) */
	unsigned short port;	/* out (host endianness) */
	unsigned char tosvalid;	/* out (1=valid) */
	unsigned char tosbyte;	/* out */
	unsigned char addr[16];	/* out (v6: address) */
};

/* extra functions for better support for high-level languages */
ECNBITS_EXPORTAPI int ecnhll_prep(SOCKET fd, int af);
ECNBITS_EXPORTAPI int ecnhll_recv(SOCKET fd, void *buf, struct ecnhll_rcv *p);
/* support code for Mono */
ECNBITS_EXPORTAPI void ecnhll_mono_test(void);
ECNBITS_EXPORTAPI int ecnhll_mono_map(int errno_code);

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
#undef ECNBITS_EXPORTAPI
#else
/* building the library itself, additional compatibility/utilities */
#if !(defined(_WIN32) || defined(WIN32))
#define WSAEAFNOSUPPORT	EAFNOSUPPORT
#endif
#endif

#endif
