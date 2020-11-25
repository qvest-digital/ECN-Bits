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
#include <stddef.h>
#include <string.h>

#include <jni.h>

#define ECNBITS_ALOG_TAG "ECN-v2"
#include "alog.h"

#define NELEM(a)	(sizeof(a) / sizeof((a)[0]))
#define __unused	__attribute__((__unused__))

static void throw(JNIEnv *, const char *msg);

static JNICALL jlong gettid(JNIEnv *, jclass);
static JNICALL void sigtid(JNIEnv *, jclass, jlong, jint);
#if 0
static JNICALL jint n_socket(JNIEnv *, jclass);
static JNICALL void n_close(JNIEnv *, jclass, jint);
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
	METH(gettid, "()J"),
	METH(sigtid, "(JI)V"),
#if 0
	METH(n_socket, "()I"),
	METH(n_close, "(I)V"),
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

	getcons(EX, c, "(Ljava/lang/String;Ljava/lang/String;ILjava/lang/String;)V");

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

static void
throw(JNIEnv *env, const char *msg __unused/*XXX*/)
{
	int ec = errno;
	jthrowable e, cause;

	if ((cause = (*env)->ExceptionOccurred(env))) {
		//XXX
		return;
	}

	if (!(e = (*env)->NewObject(env, cls_EX, i_EX_c,
	    //XXX
	    (jobject)NULL, (jobject)NULL, (jint)ec, (jobject)NULL)))
		return;
	(*env)->Throw(env, e);
}

union tid {
	pthread_t pt;
	jlong j[sizeof(pthread_t) <= sizeof(jlong) ? 1 : -1];
};

static JNICALL jlong
gettid(JNIEnv *env __unused, jclass cls __unused)
{
	union tid u = {0};

	u.pt = pthread_self();
	return (u.j[0]);
}

static JNICALL void
sigtid(JNIEnv *env, jclass cls __unused, jlong j, jint sig)
{
	union tid u = {0};

ecnlog_info("signalling %lu with %d", j, sig);
	if (sig == -1) {
		/* signal № expected by Android’s Bionic libc */
		sig = __SIGRTMIN + 2;
ecnlog_info("signalling %lu with %d after change", j, sig);
	}
	u.j[0] = j;
	if (pthread_kill(u.pt, sig))
		throw(env, "pthread_kill");
}
