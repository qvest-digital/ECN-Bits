#include <jni.h>
#include <android/log.h>

#include "ecn-ndk.h"

#define NELEM(a)	(sizeof(a) / sizeof((a)[0]))

static jint JNICALL nativeSetup(JNIEnv *env, jobject self, jint fd);

#define METH(name, signature) \
	{ #name, signature, reinterpret_cast<void *>(name) }
static const JNINativeMethod methods[] = {
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

	if (vm->GetEnv(reinterpret_cast<void **>(&env),
	    JNI_VERSION_1_6) != JNI_OK) {
		__android_log_print(ANDROID_LOG_ERROR, "ECN-JNI",
		    "failed to get JNI environment");
		return (JNI_ERR);
	}

	cls = env->FindClass("java/net/ECNBitsDatagramSocketImpl");
	if (cls == nullptr) {
		__android_log_print(ANDROID_LOG_ERROR, "ECN-JNI",
		    "failed to get class to attach to");
		return (JNI_ERR);
	}

	rc = env->RegisterNatives(cls, methods, NELEM(methods));
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
