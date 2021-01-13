/* This is an independent module from the main ECN-Bits library. */
/* It is derived from Android’s libnativehelper. */

/*-
 * Copyright © 2020
 *      mirabilos <t.glaser@tarent.de>
 * Licenced by Deutsche Telekom
 *
 * Copyright (C) 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *
 * The full text of that licence can be found in the following file:
 * ‣ android/ecn-lib/src/legal/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#ifndef ECNBITS_NH_H
#define ECNBITS_NH_H

#ifndef ECNBITS_SKIP_DALVIK
extern jclass cls_FD;			// java.io.FileDescriptor
extern jmethodID i_FD_c;		// java.io.FileDescriptor.<init>()
extern jfieldID o_FD_descriptor;	// java.io.FileDescriptor.descriptor
#endif

#ifdef __cplusplus
extern "C" {
#endif

#ifndef __ANDROID__
/* not needed on Android: Bionic libc strerror is thread-safe since 4.2 */
/*
 * Return a pointer to a locale-dependent error string explaining errno
 * value 'errnum'. The returned pointer may or may not be equal to 'buf'.
 * This function is thread-safe (unlike strerror) and portable (unlike
 * strerror_r).
 */
const char *jniStrError(int errnum, char *buf, size_t buflen);
#endif

#ifndef ECNBITS_SKIP_DALVIK
/*
 * Returns a new java.io.FileDescriptor for the given int fd.
 */
jobject jniCreateFileDescriptor(JNIEnv *env, int fd);
#endif

#ifdef __cplusplus
}
#endif

#endif
