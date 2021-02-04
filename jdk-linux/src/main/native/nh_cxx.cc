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
 * ‣ jdk-linux/src/legal/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include <string.h>
#include <string>

#include <jni.h>

#include "nh.h"

namespace {

// Note: glibc has a nonstandard strerror_r that returns char* rather than POSIX's int.
// char *strerror_r(int errnum, char *buf, size_t n);
//
// Some versions of bionic support the glibc style call. Since the set of defines that determine
// which version is used is byzantine in its complexity we will just use this C++ template hack to
// select the correct jniStrError implementation based on the libc being used.

using GNUStrError = char* (*)(int,char*,size_t);
using POSIXStrError = int (*)(int,char*,size_t);

static inline const char *realJniStrError(GNUStrError func,
    int errnum, char *buf, size_t buflen)
    __attribute__((__always_inline__, __unused__));
static inline const char *realJniStrError(POSIXStrError func,
    int errnum, char *buf, size_t buflen)
    __attribute__((__always_inline__, __unused__));

static inline const char *
realJniStrError(GNUStrError func, int errnum, char *buf, size_t buflen)
{
	return func(errnum, buf, buflen);
}

static inline const char *
realJniStrError(POSIXStrError func, int errnum, char *buf, size_t buflen)
{
	int rc = func(errnum, buf, buflen);
	if (rc != 0) {
		// POSIX only guarantees a value other than 0. The safest way
		// to implement this function is to use C++ and overload on the
		// type of strerror_r to accurately distinguish GNU from POSIX.
		snprintf(buf, buflen, "errno %d", errnum);
	}
	return buf;
}

}  // namespace

const char *
jniStrError(int errnum, char *buf, size_t buflen)
{
	// The magic of C++ overloading selects the correct
	// implementation based on the declared type of strerror_r. The
	// inline will ensure that we don't have any indirect calls.
	return realJniStrError(strerror_r, errnum, buf, buflen);
}
