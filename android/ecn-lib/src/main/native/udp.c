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
#include <fcntl.h>
#include <netdb.h>
#include <poll.h>
#include <pthread.h>
#include <signal.h>
#include <stdarg.h>
#include <stddef.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

#include <jni.h>

#include "nh.h"
#include "UDPnet.h"

#define ECNBITS_ALOG_TAG "ECN-v2"
#include "alog.h"

#define NELEM(a)	(sizeof(a) / sizeof((a)[0]))
#define __unused	__attribute__((__unused__))
#define ecnbool		uint8_t

#ifdef __ANDROID__
#define rstrerrinit()	/* nothing */
#define rstrerror(e)	strerror(e)	/* thread-safe in Bionic since 4.2 */
#else
#define rstrerrinit()	char rstrerrstr[1024]	/* size from glibc manpage */
#define rstrerror(e)	jniStrError((e), rstrerrstr, sizeof(rstrerrstr))
#endif

#define rgetnaminfo(e,s,S) \
			int e = errno; \
			char s[64]; \
			if (getnameinfo((struct sockaddr *)(S), \
			    sizeof(struct sockaddr_in6), \
			    s, sizeof(s), NULL, 0, NI_NUMERICHOST)) \
				memcpy(s, "<EAI_*>", sizeof("<EAI_*>") + 1)

#define eX		0	// ErrnoException : IOException
#define eX_S		1	// ErrnoSocketException
#define eX_PROTO	2	// ErrnoProtocolException
#define eX_CONNECT	3	// ErrnoConnectException
#define eX_UNREACH	4	// ErrnoNoRouteToHostException
#define eX_BIND		5	// ErrnoBindException
#define eX_S_auto	6	// 2‥5 depending on errno, default 1
#define ethrow(env,kind,...)	throw(env, kind, errno, __VA_ARGS__)
#define throw(env,kind,ec,...)	\
	    vthrow(__FILE__, __func__, env, __LINE__, kind, ec, __VA_ARGS__)
static void vthrow(const char *loc_file, const char *loc_func, JNIEnv *env,
	    int loc_line, int kind, int errcode, const char *fmt, ...);

static JNICALL jlong n_gettid(JNIEnv *, jclass);
static JNICALL void n_sigtid(JNIEnv *, jclass, jlong);
static JNICALL void n_close(JNIEnv *, jclass, jint);
static JNICALL jint n_socket(JNIEnv *, jclass);
static JNICALL void n_setnonblock(JNIEnv *, jclass, jint, jboolean);
static JNICALL jint n_getsockopt(JNIEnv *, jclass, jint, jint);
static JNICALL void n_setsockopt(JNIEnv *, jclass, jint, jint, jint);
static JNICALL void n_getsockname(JNIEnv *, jclass, jint, jobject);
static JNICALL void n_bind(JNIEnv *, jclass, jint, jbyteArray, jint, jint);
#if 0
static JNICALL void n_connect(JNIEnv *, jclass, jint, jbyteArray, jint, jint);
static JNICALL void n_disconnect(JNIEnv *, jclass, jint);
static JNICALL jint n_recv(JNIEnv *, jclass, jint, jobject, jint, jint, jobject);
static JNICALL jint n_send(JNIEnv *, jclass, jint, jobject, jint, jint, jbyteArray, jint, jint);
static JNICALL jlong n_rd(JNIEnv *, jclass, jint, jobjectArray, jint, jobject);
static JNICALL jlong n_wr(JNIEnv *, jclass, jint, jobjectArray, jbyteArray, jint, jint);
static JNICALL jint n_pollin(JNIEnv *, jclass, jint, jint);
#endif

#define METH(name,signature) \
	{ #name, signature, (void *)(name) }
static const JNINativeMethod methods[] = {
	METH(n_gettid, "()J"),
	METH(n_sigtid, "(J)V"),
	METH(n_close, "(I)V"),
	METH(n_socket, "()I"),
	METH(n_setnonblock, "(IZ)V"),
	METH(n_getsockopt, "(II)I"),
	METH(n_setsockopt, "(III)V"),
	METH(n_getsockname, "(ILde/telekom/llcto/ecn_bits/android/lib/JNI$AddrPort;)V"),
	METH(n_bind, "(I[BII)V"),
#if 0
	METH(n_connect, "(I[BII)V"),
	METH(n_disconnect, "(I)V"),
	METH(n_recv, "(ILjava/nio/ByteBuffer;IILde/telekom/llcto/ecn_bits/android/lib/JNI$AddrPort;)I"),
	METH(n_send, "(ILjava/nio/ByteBuffer;II[BII)I"),
	METH(n_rd, "(I[Lde/telekom/llcto/ecn_bits/android/lib/JNI$SGIO;ILde/telekom/llcto/ecn_bits/android/lib/JNI$AddrPort;)J"),
	METH(n_wr, "(I[Lde/telekom/llcto/ecn_bits/android/lib/JNI$SGIO;[BII)J"),
	METH(n_pollin, "(II)I")
#endif
};
#undef METH

static jclass cls_JNI;		// JNI
static jclass cls_EX;		// JNI.ErrnoException
static jclass cls_EX_S;		// JNI.ErrnoSocketException
static jclass cls_EX_PROTO;	// JNI.ErrnoProtocolException
static jclass cls_EX_CONNECT;	// JNI.ErrnoConnectException
static jclass cls_EX_UNREACH;	// JNI.ErrnoNoRouteToHostException
static jclass cls_EX_BIND;	// JNI.ErrnoBindException
static jclass cls_AP;		// JNI.AddrPort
static jclass cls_SG;		// JNI.SGIO

// exception constructors:
static jmethodID i_EX_c;
static jmethodID i_EX_S_c;
static jmethodID i_EX_PROTO_c;
static jmethodID i_EX_CONNECT_c;
static jmethodID i_EX_UNREACH_c;
static jmethodID i_EX_BIND_c;

static jfieldID o_AP_addr;
static jfieldID o_AP_port;
static jfieldID o_AP_scopeId;
static jfieldID o_AP_tc;
static jfieldID o_AP_tcValid;

static void
free_grefs(JNIEnv *env)
{
#define f(x) do { if (x) {			\
	(*env)->DeleteGlobalRef(env, (x));	\
	(x) = NULL;				\
} } while (/* CONSTCOND */ 0)
#ifndef ECNBITS_SKIP_DALVIK
	f(cls_FD);
	f(cls_STAG);
#endif
	f(cls_SG);
	f(cls_AP);
	f(cls_EX_BIND);
	f(cls_EX_UNREACH);
	f(cls_EX_CONNECT);
	f(cls_EX_PROTO);
	f(cls_EX_S);
	f(cls_EX);
	f(cls_JNI);
#undef f
}

JNIEXPORT JNICALL jint
JNI_OnLoad(JavaVM *vm, void *reserved __unused)
{
	JNIEnv *env;
	jint rc;

	if ((*vm)->GetEnv(vm, (void **)&env, JNI_VERSION_1_6) != JNI_OK) {
		ecnlog_err("load: failed to get JNI environment");
		return (JNI_ERR);
	}

#define getclass(dst,name) do {						\
	jclass tmpcls;							\
	if (!(tmpcls = (*env)->FindClass(env, (name))) ||		\
	    !((cls_ ## dst) = (*env)->NewGlobalRef(env, tmpcls))) {	\
		if (tmpcls)						\
			(*env)->DeleteLocalRef(env, tmpcls);		\
		ecnlog_err("failed to get class reference for %s",	\
		    (name));						\
		goto unwind;						\
	}								\
	(*env)->DeleteLocalRef(env, tmpcls);				\
} while (/* CONSTCOND */ 0)

#define _getid(what,pfx,cls,vn,jn,sig,sep,how) do {			\
	if (!((pfx ## _ ## cls ## _ ## vn) =				\
	    (*env)->how(env, cls_ ## cls, jn, sig))) {			\
		ecnlog_err("failed to get %s reference to %s%s%s",	\
		    what, #cls, sep, jn);				\
		goto unwind;						\
	}								\
} while (/* CONSTCOND */ 0)

#define getfield(cls,name,sig)	_getid("field",  o, cls, name, #name, sig, ".", GetFieldID)
#define getmeth(cls,name,sig)	_getid("method", m, cls, name, #name, sig, ".", GetMethodID)
#define getsfield(cls,name,sig)	_getid("field",  O, cls, name, #name, sig, "::", GetStaticFieldID)
#define getsmeth(cls,name,sig)	_getid("method", M, cls, name, #name, sig, "::", GetStaticMethodID)
#define getcons(cls,vn,sig)	_getid("constructor", i, cls, vn, "<init>", sig, "", GetMethodID)

	getclass(JNI, "de/telekom/llcto/ecn_bits/android/lib/JNI");
	getclass(EX, "de/telekom/llcto/ecn_bits/android/lib/JNI$ErrnoException");
	getclass(EX_S, "de/telekom/llcto/ecn_bits/android/lib/JNI$ErrnoSocketException");
	getclass(EX_PROTO, "de/telekom/llcto/ecn_bits/android/lib/JNI$ErrnoProtocolException");
	getclass(EX_CONNECT, "de/telekom/llcto/ecn_bits/android/lib/JNI$ErrnoConnectException");
	getclass(EX_UNREACH, "de/telekom/llcto/ecn_bits/android/lib/JNI$ErrnoNoRouteToHostException");
	getclass(EX_BIND, "de/telekom/llcto/ecn_bits/android/lib/JNI$ErrnoBindException");
	getclass(AP, "de/telekom/llcto/ecn_bits/android/lib/JNI$AddrPort");
	getclass(SG, "de/telekom/llcto/ecn_bits/android/lib/JNI$SGIO");
#ifndef ECNBITS_SKIP_DALVIK
	/* for udpnet */
	getclass(STAG, "dalvik/system/SocketTagger");
	/* for nh library */
	getclass(FD, "java/io/FileDescriptor");
#endif

	getcons(EX, c, "(Ljava/lang/String;ILjava/lang/String;Ljava/lang/String;ILjava/lang/String;Ljava/lang/Throwable;)V");
	getcons(EX_S, c, "(Ljava/lang/String;ILjava/lang/String;Ljava/lang/String;ILjava/lang/String;Ljava/lang/Throwable;)V");
	getcons(EX_PROTO, c, "(Ljava/lang/String;ILjava/lang/String;Ljava/lang/String;ILjava/lang/String;Ljava/lang/Throwable;)V");
	getcons(EX_CONNECT, c, "(Ljava/lang/String;ILjava/lang/String;Ljava/lang/String;ILjava/lang/String;Ljava/lang/Throwable;)V");
	getcons(EX_UNREACH, c, "(Ljava/lang/String;ILjava/lang/String;Ljava/lang/String;ILjava/lang/String;Ljava/lang/Throwable;)V");
	getcons(EX_BIND, c, "(Ljava/lang/String;ILjava/lang/String;Ljava/lang/String;ILjava/lang/String;Ljava/lang/Throwable;)V");
#ifndef ECNBITS_SKIP_DALVIK
	/* for nh library */
	getcons(FD, c, "()V");
#endif

#ifndef ECNBITS_SKIP_DALVIK
	/* for udpnet */
	getsmeth(STAG, get, "()Ldalvik/system/SocketTagger;");
	getmeth(STAG, tag, "(Ljava/io/FileDescriptor;)V");
#endif

	getfield(AP, addr, "[B");
	getfield(AP, port, "I");
	getfield(AP, scopeId, "I");
	getfield(AP, tc, "B");
	getfield(AP, tcValid, "Z");
#ifndef ECNBITS_SKIP_DALVIK
	/* for nh library */
	getfield(FD, descriptor, "I");
#endif

	rc = (*env)->RegisterNatives(env, cls_JNI, methods, NELEM(methods));
	if (rc != JNI_OK) {
		ecnlog_err("failed to attach methods to class");
		goto unwind2;
	}

	ecnlog_info("load successful");
	return (JNI_VERSION_1_6);
 unwind:
	rc = JNI_ERR;
 unwind2:
	free_grefs(env);
	return (rc);
}

JNIEXPORT JNICALL void
JNI_OnUnload(JavaVM *vm, void *reserved __unused)
{
	JNIEnv *env;

	if ((*vm)->GetEnv(vm, (void **)&env, JNI_VERSION_1_6) != JNI_OK) {
		ecnlog_err("unload: failed to get JNI environment");
		return;
	}

	free_grefs(env);
	ecnlog_info("unload successful");
}

static void vthrow(const char *loc_file, const char *loc_func, JNIEnv *env,
    int loc_line, int kind, int errcode, const char *fmt, ...)
{
	jthrowable e;
	va_list ap;
	jstring jfile = NULL;
	jint jline = loc_line;
	jstring jfunc = NULL;
	jstring jmsg = NULL;
	jint jerr = errcode;
	jstring jstr = NULL;
	jthrowable cause = NULL;
	const char *msg;
	jclass e_cls;
	jmethodID e_init;
	char *msgbuf;
	rstrerrinit();

 rekindle:
	switch (kind) {
#define KIND(what) \
	case eX_ ## what: \
		e_cls = cls_EX_ ## what; \
		e_init = i_EX_ ## what ## _c; \
		break
	KIND(S);
	KIND(PROTO);
	KIND(CONNECT);
	KIND(UNREACH);
	KIND(BIND);
#undef KIND
	case eX_S_auto:
		kind = eX_S;
// handleSocketErrorWithDefault:
		switch (errcode) {
		case EPROTO:
			kind = eX_PROTO;
			break;
		case ECONNREFUSED:
		case ETIMEDOUT:
			kind = eX_CONNECT;
			break;
		case EHOSTUNREACH:
			kind = eX_UNREACH;
			break;
		case EADDRINUSE:
		case EADDRNOTAVAIL:
			kind = eX_BIND;
			break;
		}
		goto rekindle;
	case eX:
	default:
		e_cls = cls_EX;
		e_init = i_EX_c;
		break;
	}

	if ((*env)->PushLocalFrame(env, 6)) {
		cause = (*env)->ExceptionOccurred(env);
		(*env)->ExceptionClear(env);
		(*env)->Throw(env, (*env)->NewObject(env, e_cls, e_init,
		    jfile, jline, jfunc, jmsg, jerr, jstr, cause));
		return;
	}

	if ((cause = (*env)->ExceptionOccurred(env))) {
		/* will be treated as cause */
		(*env)->ExceptionClear(env);
	}

	va_start(ap, fmt);
	if (vasprintf(&msgbuf, fmt, ap) == -1) {
		msgbuf = NULL;
		msg = fmt;
	} else
		msg = msgbuf;
	va_end(ap);

	jmsg = (*env)->NewStringUTF(env, msg);
	free(msgbuf);
	if (!jmsg)
		goto onStringError;

	if (!(jfunc = (*env)->NewStringUTF(env, loc_func)))
		goto onStringError;
	if (!(jstr = (*env)->NewStringUTF(env, rstrerror(errcode))))
		goto onStringError;
#ifdef OLD_CLANG_SRCDIR_HACK
	if (!strncmp(loc_file, OLD_CLANG_SRCDIR_HACK, sizeof(OLD_CLANG_SRCDIR_HACK) - 1) &&
	    asprintf(&msgbuf, "«ECN-Bits»/%s", loc_file + sizeof(OLD_CLANG_SRCDIR_HACK) - 1) != -1) {
		msg = msgbuf;
	} else {
		msg = loc_file;
		msgbuf = NULL;
	}
#else
#define msg loc_file
#endif
	jfile = (*env)->NewStringUTF(env, msg);
#ifdef OLD_CLANG_SRCDIR_HACK
	free(msgbuf);
#else
#undef msg
#endif
	if (!jfile) {
 onStringError:
		(*env)->ExceptionClear(env);
	}

	e = (*env)->PopLocalFrame(env, (*env)->NewObject(env, e_cls, e_init,
	    jfile, jline, jfunc, jmsg, jerr, jstr, cause));
	if (e)
		(*env)->Throw(env, e);
}

union tid {
	pthread_t pt;
	jlong j[sizeof(pthread_t) <= sizeof(jlong) ? 1 : -1];
};

static JNICALL jlong
n_gettid(JNIEnv *env __unused, jclass cls __unused)
{
	union tid u = {0};

	u.pt = pthread_self();
	return (u.j[0]);
}

static JNICALL void
n_sigtid(JNIEnv *env, jclass cls __unused, jlong j)
{
	union tid u = {0};
	int e;

	u.j[0] = j;
	if ((e = pthread_kill(u.pt, /* Bionic */ __SIGRTMIN + 2)))
		throw(env, eX, e, "pthread_kill(%llu)", (unsigned long long)j);
}

static JNICALL void
n_close(JNIEnv *env, jclass cls __unused, jint fd)
{
	if (close(fd))
		ethrow(env, eX, "close(%d)", (int)fd);
}

static void
eclose(int fd)
{
	int e = errno;

	close(fd);
	errno = e;
}

static JNICALL jint
n_socket(JNIEnv *env, jclass cls __unused)
{
	int fd;
	int so;

	if ((fd = socket(AF_INET6, SOCK_DGRAM, 0)) == -1) {
		ethrow(env, eX_S_auto, "socket");
		return (-1);
	}
	tagSocket(env, fd);
	if ((*env)->ExceptionCheck(env) == JNI_TRUE) {
		/* OOM in jniCreateFileDescriptor or failure to tag */
		ecnlog_warn("could not tag newly created socket");
		/* don’t care */
		(*env)->ExceptionClear(env);
	}

	/* ensure v4-mapped is enabled on this socket */
	so = 0;
	if (setsockopt(fd, IPPROTO_IPV6, IPV6_V6ONLY,
	    (const void *)&so, sizeof(so))) {
		eclose(fd);
		ethrow(env, eX_S, "setsockopt(%s)", "IPV6_V6ONLY");
		return (-1);
	}

	/* setup ECN-Bits */
	so = 1;
	if (setsockopt(fd, IPPROTO_IPV6, IPV6_RECVTCLASS,
	    (const void *)&so, sizeof(so))) {
		eclose(fd);
		ethrow(env, eX_S, "setsockopt(%s)", "IPV6_RECVTCLASS");
		return (-1);
	}
	if (setsockopt(fd, IPPROTO_IP, IP_RECVTOS,
	    (const void *)&so, sizeof(so))) {
		eclose(fd);
		ethrow(env, eX_S, "setsockopt(%s)", "IP_RECVTOS");
		return (-1);
	}

	return (fd);
}

static JNICALL void
n_setnonblock(JNIEnv *env, jclass cls __unused, jint fd, jboolean block)
{
	int oflags, nflags;

	if ((oflags = fcntl(fd, F_GETFL)) == -1)
		ethrow(env, eX, "fcntl(%d, %s)", fd, "F_GETFL");
	nflags = (oflags & ~O_NONBLOCK) | (block == JNI_TRUE ? 0 : O_NONBLOCK);
	if (nflags != oflags && fcntl(fd, F_SETFL, nflags) == -1)
		ethrow(env, eX, "fcntl(%d, %s)", fd, "F_SETFL");
}

static JNICALL jint
n_getsockopt(JNIEnv *env, jclass cls __unused, jint fd, jint optenum)
{
	int level, optname, optval;
	socklen_t optlen;
	ecnbool isbool;

	switch (optenum) {
	case 0: // IP_TOS
		isbool = 0;
		level = IPPROTO_IPV6;
		optname = IPV6_TCLASS;
		break;
	case 1: // SO_BROADCAST
		isbool = 1;
		level = SOL_SOCKET;
		optname = SO_BROADCAST;
		break;
	case 2: // SO_RCVBUF
		isbool = 0;
		level = SOL_SOCKET;
		optname = SO_RCVBUF;
		break;
	case 3: // SO_REUSEADDR
		isbool = 1;
		level = SOL_SOCKET;
		optname = SO_REUSEADDR;
		break;
	case 4: // SO_SNDBUF
		isbool = 0;
		level = SOL_SOCKET;
		optname = SO_SNDBUF;
		break;
	default:
		/* NOTREACHED */
		return (-1);
	}

	optlen = sizeof(optval);
	if (getsockopt(fd, level, optname, &optval, &optlen) == -1) {
		ethrow(env, eX_S, "getsockopt(%d, %d, %d)",
		    fd, level, optname);
		return (-1);
	}

	switch (optenum) {
	case 2: // SO_RCVBUF
	case 4: // SO_SNDBUF
		/* doubled return values under Linux */
		optval /= 2;
		break;
	}

	return (isbool ? (optval ? JNI_TRUE : JNI_FALSE) : (jint)optval);
}

static void
do_setsockopt(JNIEnv *env, int fd, int level, int name, int val)
{
	if (setsockopt(fd, level, name, &val, sizeof(val)) == -1)
		ethrow(env, eX_S, "setsockopt(%d, %d, %d, %d)",
		    fd, level, name, val);
}

static JNICALL void
n_setsockopt(JNIEnv *env, jclass cls __unused, jint fd, jint optenum, jint val)
{
	switch (optenum) {
	case 0: // IP_TOS
		do_setsockopt(env, fd, IPPROTO_IP, IP_TOS, val);
		do_setsockopt(env, fd, IPPROTO_IPV6, IPV6_TCLASS, val);
		/* ordered like this as the last exception is shown */
		break;
	case 1: // SO_BROADCAST
		do_setsockopt(env, fd, SOL_SOCKET, SO_BROADCAST,
		    val == JNI_FALSE ? 0 : 1);
		break;
	case 2: // SO_RCVBUF
		/*
		 * kernel minimum is 128 but this also discards smaller
		 * packets (say the JRE comments at least) so bump too
		 * small requests
		 */
		if (val < 1024)
			val = 1024;
		do_setsockopt(env, fd, SOL_SOCKET, SO_RCVBUF, val);
		break;
	case 3: // SO_REUSEADDR
		do_setsockopt(env, fd, SOL_SOCKET, SO_REUSEADDR,
		    val == JNI_FALSE ? 0 : 1);
		break;
	case 4: // SO_SNDBUF
		/* kernel minimum is 1024 */
		if (val < 1024)
			val = 1024;
		do_setsockopt(env, fd, SOL_SOCKET, SO_SNDBUF, val);
		break;
	}
}

static JNICALL void
n_getsockname(JNIEnv *env, jclass cls __unused, jint fd, jobject ap)
{
	union {
		struct sockaddr_storage ss;
		struct sockaddr_in6 sin6;
	} sa;
	socklen_t slen = sizeof(sa);
	jbyteArray addr;

	if (getsockname(fd, (struct sockaddr *)&sa, &slen) == -1)
		ethrow(env, eX_S_auto, "getsockname(%d)", fd);
	if (sa.ss.ss_family != AF_INET6)
		throw(env, eX_S, EAFNOSUPPORT,
		    "AF %d in getsockname(%d)", (int)sa.ss.ss_family, fd);

	addr = (*env)->NewByteArray(env, 16);
	(*env)->SetByteArrayRegion(env, addr, 0, 16,
	    (const void *)&sa.sin6.sin6_addr.s6_addr);
	(*env)->SetObjectField(env, ap, o_AP_addr, addr);
	(*env)->SetIntField(env, ap, o_AP_port, ntohs(sa.sin6.sin6_port));
}

static int
mksockaddr(JNIEnv *env, struct sockaddr_in6 *sin6, jbyteArray arr, jint port, jint scope)
{
	jbyte *addr;

	memset(sin6, '\0', sizeof(struct sockaddr_in6));
	sin6->sin6_family = AF_INET6;
	sin6->sin6_port = htons((uint16_t)port);

	if (!(addr = (*env)->GetPrimitiveArrayCritical(env, arr, NULL)))
		return (1);
	memcpy(sin6->sin6_addr.s6_addr, addr, 16);
	(*env)->ReleasePrimitiveArrayCritical(env, arr, addr, JNI_ABORT);

	sin6->sin6_scope_id = scope > 0 ? (uint32_t)scope : 0U;

	return (0);
}

static JNICALL void
n_bind(JNIEnv *env, jclass cls __unused, jint fd, jbyteArray addr, jint port, jint scope)
{
	struct sockaddr_in6 sin6;

	if (mksockaddr(env, &sin6, addr, port, scope) /* threw an exception */)
		return;
	if (bind(fd, (struct sockaddr *)&sin6, sizeof(sin6)) == -1) {
		rgetnaminfo(r_errno, r_host, &sin6);
		throw(env, eX_S_auto, r_errno, "bind(%d, [%s]:%u)",
		    fd, r_host, (int)port);
	}
}

#if 0
static JNICALL void
n_connect(JNIEnv *env, jclass cls __unused, jint fd, jbyteArray addr, jint port, jint scope)
// connect() with empty, zero’d struct sockaddr_in6 with sin6_family = AF_UNSPEC
static JNICALL void
n_disconnect(JNIEnv *env, jclass cls __unused, jint fd)
static JNICALL jint
n_recv(JNIEnv *env, jclass cls __unused, jint fd,
    jobject bbuf, jint bbpos, jint bbsize, jobject aptc)
static JNICALL jint
n_send(JNIEnv *env, jclass cls __unused, jint fd,
    jobject bbuf, jint bbpos, jint bbsize, jbyteArray addr, jint port, jint scope)
static JNICALL jlong
n_rd(JNIEnv *env, jclass cls __unused, jint fd,
    jobjectArray bufs, jint nbufs, jobject tc)
static JNICALL jlong
n_wr(JNIEnv *env, jclass cls __unused, jint fd,
    jobjectArray bufs, jbyteArray addr, jint port, jint scope)
static JNICALL jint
n_pollin(JNIEnv *env, jclass cls __unused, jint fd, jint timeout)
#endif
