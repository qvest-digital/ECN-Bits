/*-
 * Copyright © 2020
 *      mirabilos <t.glaser@tarent.de>
 * Copyright © 2016
 *	The Android Open Source Project
 * Licensor: Deutsche Telekom
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; version 2 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * The full text of that licence can be found in the following file:
 * ‣ android/ecn-lib/src/main/resources/COPYING
 *
 * As a special exception, the copyright holders of this library give
 * you permission to link this library with independent modules to
 * produce an executable, regardless of the license terms of these
 * independent modules, and to copy and distribute the resulting
 * executable under terms of your choice, provided that you also meet,
 * for each linked independent module, the terms and conditions of the
 * license of that module.  An independent module is a module which is
 * not derived from or based on this library.  If you modify this
 * library, you may extend this exception to your version of the
 * library, but you are not obligated to do so.  If you do not wish
 * to do so, delete this exception statement from your version.
 */

#include <jni.h>

#include "nh.h"
#include "UDPnet.h"

#ifndef ECNBITS_SKIP_DALVIK
jclass cls_STAG;	// dalvik.system.SocketTagger
jmethodID M_STAG_get;	// dalvik.system.SocketTagger::get()
jmethodID m_STAG_tag;	// dalvik.system.SocketTagger.tag()
#endif

#ifndef ECNBITS_SKIP_DALVIK
#define _dalvik_used	/* nothing */
#else
#define _dalvik_used	__attribute__((__unused__))
#endif

void
tagSocket(JNIEnv *env _dalvik_used, int fd _dalvik_used)
{
#ifndef ECNBITS_SKIP_DALVIK
	jobject socketTagger = (*env)->CallStaticObjectMethod(env, cls_STAG, M_STAG_get);
	jobject fileDescriptor = jniCreateFileDescriptor(env, fd);
	(*env)->CallVoidMethod(env, socketTagger, m_STAG_tag, fileDescriptor);
#endif
}
