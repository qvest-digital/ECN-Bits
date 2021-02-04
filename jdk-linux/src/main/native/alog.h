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

#ifndef ECNBITS_ALOG_H
#define ECNBITS_ALOG_H

#ifndef ECNBITS_ALOG_TAG
#define ECNBITS_ALOG_TAG "ECN-Bits-JNI"
#endif

#include <stdio.h>

#define ecnlog_err(msg, ...)	fprintf(stderr, \
	    "E: [" ECNBITS_ALOG_TAG "] " msg "\n", ##__VA_ARGS__)
#define ecnlog_warn(msg, ...)	fprintf(stderr, \
	    "W: [" ECNBITS_ALOG_TAG "] " msg "\n", ##__VA_ARGS__)
#define ecnlog_info(msg, ...)	fprintf(stderr, \
	    "I: [" ECNBITS_ALOG_TAG "] " msg "\n", ##__VA_ARGS__)

#endif
