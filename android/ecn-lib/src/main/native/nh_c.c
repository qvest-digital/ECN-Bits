/* This is an independent module from the main ECN-Bits library. */
/* It is derived from Android’s libnativehelper. */

/*-
 * Copyright © 2020
 *      mirabilos <t.glaser@tarent.de>
 * Licenced by Deutsche Telekom
 *
 * Copyright (C) 2006 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *
 * The full text of that licence can be found in the following file:
 * ‣ android/ecn-lib/src/main/resources/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include <jni.h>

#include "nh.h"

#ifndef ECNBITS_SKIP_DALVIK
jclass cls_FD;			// java.io.FileDescriptor
jmethodID i_FD_c;		// java.io.FileDescriptor.<init>()
jfieldID o_FD_descriptor;	// java.io.FileDescriptor.descriptor

jobject
jniCreateFileDescriptor(JNIEnv* env, int fd)
{
	jobject fileDescriptor = (*env)->NewObject(env, cls_FD, i_FD_c);
	// NOTE: NewObject ensures that an OutOfMemoryError will be seen by the Java
	// caller if the alloc fails, so we just return nullptr when that happens.
	if (fileDescriptor != NULL) {
		(*env)->SetIntField(env, fileDescriptor, o_FD_descriptor, fd);
	}
	return fileDescriptor;
}
#endif
