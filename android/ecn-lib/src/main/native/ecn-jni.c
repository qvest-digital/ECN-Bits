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

extern jint onload_v1ds(JavaVM *vm, JNIEnv *env);
extern jint onload_v2dc(JavaVM *vm, JNIEnv *env);

JNIEXPORT jint
JNI_OnLoad(JavaVM *vm, void *reserved __attribute__((__unused__)))
{
	JNIEnv *env;
	int rc;

	if ((*vm)->GetEnv(vm, (void **)&env, JNI_VERSION_1_6) != JNI_OK) {
		__android_log_print(ANDROID_LOG_ERROR, "ECN-JNI",
		    "failed to get JNI environment");
		return (JNI_ERR);
	}

	/* dispatch */
	if ((rc = onload_v1ds(vm, env)) != JNI_OK)
		return (rc);
	if ((rc = onload_v2dc(vm, env)) != JNI_OK)
		return (rc);

	return (JNI_VERSION_1_6);
}
