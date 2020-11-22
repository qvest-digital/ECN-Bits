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
#define __unused	__attribute__((__unused__))

static jclass clsJNI;	// JNI
static jclass clsEX;	// JNI.ErrnoException
static jclass clsAP;	// JNI.AddrPort
static jclass clsSG;	// JNI.SGIO

static void
free_grefs(JNIEnv *env)
{
#define f(x) do { if (x) {			\
	(*env)->DeleteGlobalRef(env, (x));	\
	(x) = NULL;				\
} } while (/* CONSTCOND */ 0)
	f(clsSG);
	f(clsAP);
	f(clsEX);
	f(clsJNI);
#undef f
}

JNIEXPORT JNICALL jint
JNI_OnLoad(JavaVM *vm, void *reserved __unused)
{
	JNIEnv *env;
	//int rc;

	if ((*vm)->GetEnv(vm, (void **)&env, JNI_VERSION_1_6) != JNI_OK) {
		__android_log_print(ANDROID_LOG_ERROR, "ECN-v2",
		    "load: failed to get JNI environment");
		return (JNI_ERR);
	}

#define getclass(var, name) do {					\
	jclass tmpcls;							\
	if (!(tmpcls = (*env)->FindClass(env, (name))) ||		\
	    !((var) = (*env)->NewGlobalRef(env, tmpcls))) {		\
		if (tmpcls)						\
			(*env)->DeleteLocalRef(env, tmpcls);		\
		__android_log_print(ANDROID_LOG_ERROR, "ECN-v2",	\
		    "failed to get class reference for %s", (name));	\
		goto unwind;						\
	}								\
	(*env)->DeleteLocalRef(env, tmpcls);				\
} while (/* CONSTCOND */ 0)

	getclass(clsJNI, "de.telekom.llcto.ecn_bits.android.lib.JNI");
	getclass(clsEX, "de.telekom.llcto.ecn_bits.android.lib.JNI$ErrnoException");
	getclass(clsAP, "de.telekom.llcto.ecn_bits.android.lib.JNI$AddrPort");
	getclass(clsSG, "de.telekom.llcto.ecn_bits.android.lib.JNI$SGIO");

	/* … */

	__android_log_print(ANDROID_LOG_INFO, "ECN-v2",
	    "load successful");
	return (JNI_VERSION_1_6);
 unwind:
	free_grefs(env);
	return (JNI_ERR);
}

JNIEXPORT JNICALL void
JNI_OnUnload(JavaVM *vm, void *reserved __unused)
{
	JNIEnv *env;

	if ((*vm)->GetEnv(vm, (void **)&env, JNI_VERSION_1_6) != JNI_OK) {
		__android_log_print(ANDROID_LOG_ERROR, "ECN-v2",
		    "unload: failed to get JNI environment");
		return;
	}

	free_grefs(env);
	__android_log_print(ANDROID_LOG_INFO, "ECN-v2",
	    "unload successful");
}
