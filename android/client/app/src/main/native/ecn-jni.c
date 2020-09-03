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
#include <sys/uio.h>
#include <errno.h>
#include <poll.h>

#include <jni.h>
#include <android/log.h>

#include "ecn-ndk.h"

#define NELEM(a)	(sizeof(a) / sizeof((a)[0]))

static void JNICALL nativeInit(JNIEnv *env);
static jint JNICALL nativePoll(JNIEnv *env, jobject self, jint fd, jint tmo);
static jint JNICALL nativeRecv(JNIEnv *env, jobject self, jobject args);
static jint JNICALL nativeSetup(JNIEnv *env, jobject self, jint fd);

static void o_init(JNIEnv *env);

/* cached offsets */
static struct {
	jfieldID fd;
	jfieldID buf;
	jfieldID ofs;
	jfieldID len;
	jfieldID peekOnly;
	jfieldID af;
	jfieldID a4;
	jfieldID a6;
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

JNIEXPORT jint
JNI_OnLoad(JavaVM *vm, void *reserved)
{
	JNIEnv *env;
	jclass cls;
	int rc;

	if ((*vm)->GetEnv(vm, (void **)&env, JNI_VERSION_1_6) != JNI_OK) {
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
	struct pollfd pfd;

	pfd.fd = fd;
	pfd.events = POLLIN;
	switch (poll(&pfd, 1, tmo)) {
	case 1:
		return (0);
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
	fld(af, "I");
	fld(a4, "[B");
	fld(a6, "[B");
	fld(port, "I");
	fld(read, "I");
	fld(tc, "B");
	fld(tcValid, "Z");
#undef fld
	o.o_valid = 1;
}

/* jrecv callback */
static void
setProtoDependentFields(void *ep, void *ap, const void *buf, size_t len,
    int af, unsigned short port)
{
	JNIEnv *env = (JNIEnv *)ep;
	jobject args = (jobject)ap;
	jbyteArray dst;

	(*env)->SetIntField(env, args, o.af, af);
	if (af) {
		dst = (*env)->GetObjectField(env, args, af == 4 ? o.a4 : o.a6);
		(*env)->SetByteArrayRegion(env, dst, 0, (int)len, buf);
		(*env)->SetIntField(env, args, o.port, port);
	}
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
	int fd, dopeek;
	ssize_t nbytes;
	unsigned short tc;
	struct iovec io;
	jbyteArray buf_var;
	jbyte *buf_elts;

	if (!o.o_valid) {
		o_init(env);
		if (!o.o_valid)
			return (666);
	}

	fd = (*env)->GetIntField(env, args, o.fd);
	dopeek = (*env)->GetBooleanField(env, args, o.peekOnly);
	buf_var = (*env)->GetObjectField(env, args, o.buf);
	buf_elts = (*env)->GetByteArrayElements(env, buf_var, NULL);

	io.iov_base = (char *)buf_elts +
	    (size_t)((*env)->GetIntField(env, args, o.ofs));
	io.iov_len = (size_t)((*env)->GetIntField(env, args, o.len));

	if ((nbytes = ecnbits_jrecv(fd, dopeek, &tc, &io,
	    &setProtoDependentFields, env, (void *)args)) == (ssize_t)-1)
		switch (errno) {
		case EAGAIN:
			return (1);
		case ECONNREFUSED:
			return (2);
		default:
			return (3);
		}

	(*env)->ReleaseByteArrayElements(env, buf_var, buf_elts, 0);

	(*env)->SetIntField(env, args, o.read, (int)nbytes);
	(*env)->SetByteField(env, args, o.tc, tc & 0xFF);
	(*env)->SetBooleanField(env, args, o.tcValid,
	    tc == ECNBITS_INVALID ? JNI_TRUE : JNI_FALSE);

	return (0);
}
