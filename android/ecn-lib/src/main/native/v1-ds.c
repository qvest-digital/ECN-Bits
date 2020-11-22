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
#include <sys/uio.h>
#include <netinet/in.h>
#include <netinet/ip.h>
/*#include <netinet6/in6.h>*/
#include <errno.h>
#include <poll.h>
#include <stddef.h>
#include <string.h>

#include <jni.h>
#include <android/log.h>

#define NELEM(a)	(sizeof(a) / sizeof((a)[0]))

#define ECNBITS_INVALID_BIT	((unsigned short)0x0100U)
#define ECNBITS_ISVALID_BIT	((unsigned short)0x0200U)
#define ECNBITS_VALID(result)	(((unsigned short)(result) >> 8) == 0x02U)

static void JNICALL nativeInit(JNIEnv *env);
static jint JNICALL nativePoll(JNIEnv *env, jobject self, jint fd, jint tmo);
static jint JNICALL nativeRecv(JNIEnv *env, jobject self, jobject args);
static jint JNICALL nativeSetup(JNIEnv *env, jobject self, jint fd);

static void o_init(JNIEnv *env);

static size_t cmsg_actual_data_len(const struct cmsghdr *cmsg);
static void recvtos_cmsg(struct cmsghdr *cmsg, unsigned short *e);
static ssize_t ecnbits_rdmsg(int s, struct msghdr *msgh, int flags, unsigned short *e);

/* cached offsets */
static struct {
	jfieldID fd;
	jfieldID buf;
	jfieldID ofs;
	jfieldID len;
	jfieldID peekOnly;
	jfieldID addr;
	jfieldID port;
	jfieldID read;
	jfieldID tc;
	jfieldID tcValid;
	unsigned char o_valid;
} o;

#define METH(name, signature) \
	{ #name, signature, (void *)(name) }
static const JNINativeMethod methods[] = {
	METH(nativeInit, "()V"),
	METH(nativePoll, "(II)I"),
	METH(nativeRecv, "(Ljava/net/ECNBitsDatagramSocketImpl$RecvMsgArgs;)I"),
	METH(nativeSetup, "(I)I")
};
#undef METH

jint
onload_v1ds(JavaVM *vm, JNIEnv *env)
{

	jclass cls;
	int rc;

	if (!(cls = (*env)->FindClass(env, "java/net/ECNBitsDatagramSocketImpl"))) {
		__android_log_print(ANDROID_LOG_ERROR, "ECN-JNI",
		    "failed to get class to attach to");
		return (JNI_ERR);
	}

	rc = (*env)->RegisterNatives(env, cls, methods, NELEM(methods));
	if (rc != JNI_OK) {
		__android_log_print(ANDROID_LOG_ERROR, "ECN-JNI",
		    "failed to attach methods to class");
		return (rc);
	}

	return (JNI_OK);
}

static jint JNICALL
nativeSetup(JNIEnv *env, jobject self, jint fd)
{
	int on = 1;
	struct sockaddr sa;
	socklen_t sa_len = sizeof(sa);

	if (fd == -1)
		return (1);

	if (getsockname(fd, &sa, &sa_len)) {
		__android_log_print(ANDROID_LOG_ERROR, "ECN-JNI",
		    "could not get socket address family");
		return (1);
	}

	switch (sa.sa_family) {
	case AF_INET:
		if (setsockopt(fd, IPPROTO_IP, IP_RECVTOS,
		    (const void *)&on, sizeof(on))) {
			__android_log_print(ANDROID_LOG_ERROR, "ECN-JNI",
			    "could not set up IPv4 socket");
			return (1);
		}
		break;
	case AF_INET6:
		if (setsockopt(fd, IPPROTO_IPV6, IPV6_RECVTCLASS,
		    (const void *)&on, sizeof(on))) {
			__android_log_print(ANDROID_LOG_ERROR, "ECN-JNI",
			    "could not set up IPv6 socket");
			return (1);
		}
		if (setsockopt(fd, IPPROTO_IP, IP_RECVTOS,
		    (const void *)&on, sizeof(on))) {
			__android_log_print(ANDROID_LOG_ERROR, "ECN-JNI",
			    "could not set up IPv6 socket for IPv4");
			return (1);
		}
		break;
	default:
		__android_log_print(ANDROID_LOG_ERROR, "ECN-JNI",
		    "could not set up socket: unknown address family");
		return (1);
	}
	return (0);
}

/* 0=ok  1=timeout  2+=fail */
static jint JNICALL
nativePoll(JNIEnv *env, jobject self, jint fd, jint tmo)
{
	struct pollfd pfd;

	pfd.fd = fd;
	pfd.events = POLLIN;
	switch (poll(&pfd, 1, tmo)) {
	case 1:
		return ((pfd.revents & POLLIN) ? 0 : 2);
	case 0:
		return (1);
	default:
		/* caller not interested in errno here */
		return (2);
	}
}

static void JNICALL
nativeInit(JNIEnv *env)
{
	o_init(env);
}

static void
o_init(JNIEnv *env)
{
	jclass cls;

	o.o_valid = 0;
	if (!(cls = (*env)->FindClass(env, "java/net/ECNBitsDatagramSocketImpl$RecvMsgArgs"))) {
		__android_log_print(ANDROID_LOG_ERROR, "ECN-JNI",
		    "failed to get RecvMsgArgs class");
		return;
	}

#define fld(name, type) do {						\
	if (!(o.name = (*env)->GetFieldID(env, cls, #name, type))) {	\
		__android_log_print(ANDROID_LOG_ERROR, "ECN-JNI",	\
		    "failed to get RecvMsgArgs.%s field", #name);	\
		return;							\
	}								\
} while (/* CONSTCOND */ 0)

	fld(fd, "I");
	fld(buf, "[B");
	fld(ofs, "I");
	fld(len, "I");
	fld(peekOnly, "Z");
	fld(addr, "[B");
	fld(port, "I");
	fld(read, "I");
	fld(tc, "B");
	fld(tcValid, "Z");
#undef fld
	o.o_valid = 1;
}

/*-
 * args:
 *
 * final int fd; // in
 * final byte[] buf; // in, mutated buf[ofs] .. buf[ofs + len - 1]
 * final int ofs; // in
 * final int len; // in
 * final boolean peekOnly; // in
 * byte[] addr; // out
 * int port; // out
 * int read; // out
 * byte tc; // out
 * boolean tcValid; // out
 *
 * 0=ok  1=EAGAIN  2=ECONNREFUSED  3+=fail
 */
static jint JNICALL
nativeRecv(JNIEnv *env, jobject self, jobject args)
{
	int p, fd;
	ssize_t nbytes;
	unsigned short tc;
	struct iovec io;
	jbyteArray buf_var;
	jbyte *buf_elts;
	jbyteArray addr;
	struct msghdr m;
	union {
		struct sockaddr_storage s;
		struct sockaddr_in in;
		struct sockaddr_in6 in6;
	} ss;

	if (!o.o_valid) {
		o_init(env);
		if (!o.o_valid)
			return (666);
	}

	fd = (*env)->GetIntField(env, args, o.fd);
	p = (*env)->GetBooleanField(env, args, o.peekOnly);
	buf_var = (*env)->GetObjectField(env, args, o.buf);
	/* releasing needed: goto cleanup */
	buf_elts = (*env)->GetByteArrayElements(env, buf_var, NULL);

	io.iov_base = (char *)buf_elts +
	    (size_t)((*env)->GetIntField(env, args, o.ofs));
	io.iov_len = (size_t)((*env)->GetIntField(env, args, o.len));

	memset(&m, 0, sizeof(m));
	memset(&ss, 0, sizeof(ss));
	m.msg_name = &ss;
	m.msg_namelen = sizeof(ss);
	m.msg_iov = &io;
	m.msg_iovlen = 1;

	if ((nbytes = ecnbits_rdmsg(fd, &m, p ? MSG_PEEK : 0, &tc)) == (ssize_t)-1)
		switch (errno) {
		case EAGAIN:
			p = 1;
			goto cleanup;
		case ECONNREFUSED:
			p = 2;
			goto cleanup;
		default:
			p = 3;
			goto cleanup;
		}

	switch (ss.s.ss_family) {
	case AF_INET:
		addr = (*env)->NewByteArray(env, 4);
		(*env)->SetByteArrayRegion(env, addr, 0, 4,
		    (const void *)&ss.in.sin_addr.s_addr);
		p = ntohs(ss.in.sin_port);
		break;
	case AF_INET6:
		addr = (*env)->NewByteArray(env, 16);
		(*env)->SetByteArrayRegion(env, addr, 0, 16,
		    (const void *)&ss.in6.sin6_addr.s6_addr);
		p = ntohs(ss.in6.sin6_port);
		break;
	default:
		__android_log_print(ANDROID_LOG_ERROR, "ECN-JNI",
		    "bogus address family");
		p = 3;
		goto cleanup;
	}

	(*env)->SetObjectField(env, args, o.addr, addr);
	(*env)->SetIntField(env, args, o.port, p);
	(*env)->SetIntField(env, args, o.read, (int)nbytes);
	(*env)->SetByteField(env, args, o.tc, tc & 0xFF);
	(*env)->SetBooleanField(env, args, o.tcValid,
	    ECNBITS_VALID(tc) ? JNI_TRUE : JNI_FALSE);

	p = 0;
 cleanup:
	(*env)->ReleaseByteArrayElements(env, buf_var, buf_elts, 0);
	return (p);
}

#define ECNBITS_CMSGBUFLEN 64

static size_t
cmsg_actual_data_len(const struct cmsghdr *cmsg)
{
	union {
		const struct cmsghdr *cmsg;
		const unsigned char *uc;
	} ptr[(
		/* compile-time assertions */
		sizeof(socklen_t) <= sizeof(size_t)
	    ) ? 1 : -1];
	ptrdiff_t pd;

	ptr[0].cmsg = cmsg;
	pd = CMSG_DATA(cmsg) - ptr[0].uc;
	return ((size_t)cmsg->cmsg_len - (size_t)pd);
}

static void
recvtos_cmsg(struct cmsghdr *cmsg, unsigned short *e)
{
	unsigned char b1, b2;
	unsigned char *d = CMSG_DATA(cmsg);
	size_t len;

	/* https://bugs.debian.org/966459 */
	len = cmsg_actual_data_len(cmsg);
	switch (len) {
	case 0:
		/* huh? */
		__android_log_print(ANDROID_LOG_ERROR, "ECN-JNI",
		    "empty traffic class cmsg");
		return;
	case 3:
	case 2:
		/* shouldn’t happen, but… */
		__android_log_print(ANDROID_LOG_WARN, "ECN-JNI",
		    "odd-sized traffic class cmsg (%zu)", len);
		/* FALLTHROUGH */
	case 1:
		b1 = d[0];
		break;
	default:
		/* most likely an int, but… */
		__android_log_print(ANDROID_LOG_WARN, "ECN-JNI",
		    "oversized traffic class cmsg (%zu)", len);
		/* FALLTHROUGH */
	case 4:
		b1 = d[0];
		b2 = d[3];
		if (b1 == b2)
			break;
		if (b1 != 0 && b2 == 0)
			break;
		if (b1 == 0 && b2 != 0) {
			b1 = b2;
			break;
		}
		/* inconsistent, no luck */
		__android_log_print(ANDROID_LOG_ERROR, "ECN-JNI",
		    "inconsistent traffic class cmsg %02X %02X %02X %02X (%zu)",
		    (unsigned int)d[0], (unsigned int)d[1],
		    (unsigned int)d[2], (unsigned int)d[3], len);
		return;
	}
	*e = (unsigned short)(b1 & 0xFFU) | ECNBITS_ISVALID_BIT;
}

static ssize_t
ecnbits_rdmsg(int s, struct msghdr *msgh, int flags, unsigned short *e)
{
	struct cmsghdr *cmsg;
	ssize_t rv;
	char msgbuf[ECNBITS_CMSGBUFLEN];

	*e = ECNBITS_INVALID_BIT;

	if (!msgh->msg_control) {
		msgh->msg_control = msgbuf;
		msgh->msg_controllen = sizeof(msgbuf);
	}

	rv = recvmsg(s, msgh, flags);
	if (rv == (ssize_t)-1)
		return (rv);

	if (msgh->msg_flags & MSG_CTRUNC) {
		/* 64 is enough normally though */
		__android_log_print(ANDROID_LOG_ERROR, "ECN-JNI",
		    "cmsg truncated, increase ECNBITS_CMSGBUFLEN and recompile!");
	}

	cmsg = CMSG_FIRSTHDR(msgh);
	while (cmsg) {
		switch (cmsg->cmsg_level) {
		case IPPROTO_IP:
			switch (cmsg->cmsg_type) {
			case IP_TOS:
			case IP_RECVTOS:
				recvtos_cmsg(cmsg, e);
				break;
			}
			break;
		case IPPROTO_IPV6:
			switch (cmsg->cmsg_type) {
			case IPV6_TCLASS:
				recvtos_cmsg(cmsg, e);
				break;
			}
			break;
		}
		cmsg = CMSG_NXTHDR(msgh, cmsg);
	}

	return (rv);
}
