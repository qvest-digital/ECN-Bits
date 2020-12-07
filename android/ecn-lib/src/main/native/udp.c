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
#include <pthread.h>
#include <signal.h>
#include <stdarg.h>
#include <stddef.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

#include <jni.h>

#include "nh.h"
#include "UDPnet.h"

#define ECNBITS_ALOG_TAG "ECN-v2"
#include "alog.h"

/* errlist entries in bionic are about 48 chars max, plus safety */
#define STRERROR_BUFSZ	64

#define NELEM(a)	(sizeof(a) / sizeof((a)[0]))
#define __unused	__attribute__((__unused__))

#define ethrow(env,...)	throw(env, errno, __VA_ARGS__)
#define throw(env,ec,...) \
	    vthrow(__FILE__, __func__, env, __LINE__, ec, __VA_ARGS__)
static void vthrow(const char *loc_file, const char *loc_func, JNIEnv *env,
	    int loc_line, int errcode, const char *fmt, ...);

static JNICALL jlong n_gettid(JNIEnv *, jclass);
static JNICALL void n_sigtid(JNIEnv *, jclass, jlong);
static JNICALL void n_close(JNIEnv *, jclass, jint);
static JNICALL jint n_socket(JNIEnv *, jclass);
#if 0
static JNICALL void n_setnonblock(JNIEnv *, jclass, jint, jboolean);
static JNICALL jint n_getsockopt(JNIEnv *, jclass, jint, jint);
static JNICALL void n_setsockopt(JNIEnv *, jclass, jint, jint, jint);
static JNICALL void n_getsockname(JNIEnv *, jclass, jint, jobject);
static JNICALL void n_bind(JNIEnv *, jclass, jint, jbyteArray, jint);
static JNICALL void n_connect(JNIEnv *, jclass, jint, jbyteArray, jint);
static JNICALL void n_disconnect(JNIEnv *, jclass, jint);
static JNICALL jint n_recv(JNIEnv *, jclass, jint, jobject, jint, jint, jobject);
static JNICALL jint n_send(JNIEnv *, jclass, jint, jobject, jint, jint, jbyteArray, jint);
static JNICALL jlong n_rd(JNIEnv *, jclass, jint, jobjectArray, jint, jobject);
static JNICALL jlong n_wr(JNIEnv *, jclass, jint, jobjectArray, jbyteArray, jint);
static JNICALL jint n_pollin(JNIEnv *, jclass, jint, jint);
#endif

#define METH(name,signature) \
	{ #name, signature, (void *)(name) }
static const JNINativeMethod methods[] = {
	METH(n_gettid, "()J"),
	METH(n_sigtid, "(J)V"),
	METH(n_close, "(I)V"),
	METH(n_socket, "()I"),
#if 0
	METH(n_setnonblock, "(IZ)V"),
	METH(n_getsockopt, "(II)I"),
	METH(n_setsockopt, "(III)V"),
	METH(n_getsockname, "(ILde/telekom/llcto/ecn_bits/android/lib/JNI$AddrPort;)V"),
	METH(n_bind, "(I[BI)V"),
	METH(n_connect, "(I[BI)V"),
	METH(n_disconnect, "(I)V"),
	METH(n_recv, "(ILjava/nio/ByteBuffer;IILde/telekom/llcto/ecn_bits/android/lib/JNI$AddrPort;)I"),
	METH(n_send, "(ILjava/nio/ByteBuffer;II[BI)I"),
	METH(n_rd, "(I[Lde/telekom/llcto/ecn_bits/android/lib/JNI$SGIO;ILde/telekom/llcto/ecn_bits/android/lib/JNI$AddrPort;)J"),
	METH(n_wr, "(I[Lde/telekom/llcto/ecn_bits/android/lib/JNI$SGIO;[BI)J"),
	METH(n_pollin, "(II)I")
#endif
};
#undef METH

static jclass cls_JNI;	// JNI
static jclass cls_EX;	// JNI.ErrnoException
static jclass cls_AP;	// JNI.AddrPort
static jclass cls_SG;	// JNI.SGIO

static jmethodID i_EX_c;	// exception constructor

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
	getclass(AP, "de/telekom/llcto/ecn_bits/android/lib/JNI$AddrPort");
	getclass(SG, "de/telekom/llcto/ecn_bits/android/lib/JNI$SGIO");
#ifndef ECNBITS_SKIP_DALVIK
	/* for udpnet */
	getclass(STAG, "dalvik/system/SocketTagger");
	/* for nh library */
	getclass(FD, "java/io/FileDescriptor");
#endif

	getcons(EX, c, "(Ljava/lang/String;ILjava/lang/String;Ljava/lang/String;ILjava/lang/String;Ljava/lang/Throwable;)V");
#ifndef ECNBITS_SKIP_DALVIK
	/* for nh library */
	getcons(FD, c, "()V");
#endif

#ifndef ECNBITS_SKIP_DALVIK
	/* for udpnet */
	getsmeth(STAG, get, "()Ldalvik/system/SocketTagger;");
	getmeth(STAG, tag, "(Ljava/io/FileDescriptor;)V");
#endif

#ifndef ECNBITS_SKIP_DALVIK
	/* for nh library */
	getfield(FD, descriptor, "I");
#endif

	/* … */

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
    int loc_line, int errcode, const char *fmt, ...)
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
	char *msgbuf;
	char errbuf[STRERROR_BUFSZ];

	if ((*env)->PushLocalFrame(env, 6)) {
		cause = (*env)->ExceptionOccurred(env);
		(*env)->ExceptionClear(env);
		(*env)->Throw(env, (*env)->NewObject(env, cls_EX, i_EX_c,
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
	if (!(jstr = (*env)->NewStringUTF(env, jniStrError(errcode,
	    errbuf, sizeof(errbuf)))))
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

	e = (*env)->PopLocalFrame(env, (*env)->NewObject(env, cls_EX, i_EX_c,
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
		throw(env, e, "pthread_kill(%llu)", (unsigned long long)j);
}

static JNICALL void
n_close(JNIEnv *env, jclass cls __unused, jint fd)
{
	if (close(fd))
		ethrow(env, "close(%d)", (int)fd);
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
		ethrow(env, "socket");
		return (-1);
	}
	tagSocket(env, fd);

	/* ensure v4-mapped is enabled on this socket */
	so = 0;
	if (setsockopt(fd, IPPROTO_IPV6, IPV6_V6ONLY,
	    (const void *)&so, sizeof(so))) {
		eclose(fd);
		ethrow(env, "setsockopt(%s)", "IPV6_V6ONLY");
		return (-1);
	}

	/* setup ECN-Bits */
	so = 1;
	if (setsockopt(fd, IPPROTO_IPV6, IPV6_RECVTCLASS,
	    (const void *)&so, sizeof(so))) {
		eclose(fd);
		ethrow(env, "setsockopt(%s)", "IPV6_RECVTCLASS");
		return (-1);
	}
	if (setsockopt(fd, IPPROTO_IP, IP_RECVTOS,
	    (const void *)&so, sizeof(so))) {
		eclose(fd);
		ethrow(env, "setsockopt(%s)", "IP_RECVTOS");
		return (-1);
	}

	return (fd);
}

#if 0
static JNICALL void
n_setnonblock(JNIEnv *env, jclass cls __unused, jint, jboolean block)
static JNICALL jint
n_getsockopt(JNIEnv *env, jclass cls __unused, jint, jint optenum)
static JNICALL void
n_setsockopt(JNIEnv *env, jclass cls __unused, jint, jint optenum, jint value)
static JNICALL void
n_getsockname(JNIEnv *env, jclass cls __unused, jint fd, jobject ap)
static JNICALL void
n_bind(JNIEnv *env, jclass cls __unused, jint fd, jbyteArray addr, jint port)
static JNICALL void
n_connect(JNIEnv *env, jclass cls __unused, jint fd, jbyteArray addr, jint port)
// connect() with empty, zero’d struct sockaddr_in6 with sin6_family = AF_UNSPEC
static JNICALL void
n_disconnect(JNIEnv *env, jclass cls __unused, jint fd)
static JNICALL jint
n_recv(JNIEnv *env, jclass cls __unused, jint fd,
    jobject bbuf, jint bbpos, jint bbsize, jobject aptc)
static JNICALL jint
n_send(JNIEnv *env, jclass cls __unused, jint fd,
    jobject bbuf, jint bbpos, jint bbsize, jbyteArray addr, jint port)
static JNICALL jlong
n_rd(JNIEnv *env, jclass cls __unused, jint fd,
    jobjectArray bufs, jint nbufs, jobject tc)
static JNICALL jlong
n_wr(JNIEnv *env, jclass cls __unused, jint fd,
    jobjectArray bufs, jbyteArray addr, jint port)
static JNICALL jint
n_pollin(JNIEnv *env, jclass cls __unused, jint fd, jint timeout)
#endif
