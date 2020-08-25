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

#include <jni.h>
#include <android/log.h>

#include "ecn-ndk.h"

#define NELEM(a)	(sizeof(a) / sizeof((a)[0]))

static jint JNICALL nativePoll(JNIEnv *env, jobject self, jint fd, jint tmo);
static jint JNICALL nativeRecv(JNIEnv *env, jobject self, jobject args);
static jint JNICALL nativeSetup(JNIEnv *env, jobject self, jint fd);

#define METH(name, signature) \
	{ #name, signature, (void *)(name) }
static const JNINativeMethod methods[] = {
	METH(nativePoll, "(II)I"),
	METH(nativeRecv, "(Ljava/net/ECNBitsDatagramSocketImpl$RecvMsgArgs;)I"),
	METH(nativeSetup, "(I)I")
};
#undef METH

JNIEXPORT jint
JNI_OnLoad(JavaVM *vm, void *reserved)
{
	JNIEnv *env;
	jclass cls;
	int rc;

	__android_log_print(ANDROID_LOG_WARN, "ECN-JNI", "loading");

	if ((*vm)->GetEnv(vm, &env, JNI_VERSION_1_6) != JNI_OK) {
		__android_log_print(ANDROID_LOG_ERROR, "ECN-JNI",
		    "failed to get JNI environment");
		return (JNI_ERR);
	}

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

	__android_log_print(ANDROID_LOG_WARN, "ECN-JNI", "loaded");
	return (JNI_VERSION_1_6);
}

static jint JNICALL
nativeSetup(JNIEnv *env, jobject self, jint fd)
{
	if (fd == -1)
		return (1);
	return (ecnbits_setup(fd));
}

/* 0=ok  1=timeout  2+=fail */
static jint JNICALL
nativePoll(JNIEnv *env, jobject self, jint fd, jint tmo)
{
	return (99);
}

/*-
 * args:
 *
 * final int fd; // in
 * final byte[] buf; // in, mutated buf[ofs] .. buf[ofs + len - 1]
 * final int ofs; // in
 * final int len; // in
 * final boolean peekOnly; // in
 * int af; // out, 4 or 6
 * final byte[] a4 = new byte[4]; // mutated
 * final byte[] a6 = new byte[16]; // mutated
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
	return (99);
}
