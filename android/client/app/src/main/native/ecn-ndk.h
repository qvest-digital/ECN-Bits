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

#ifndef ECN_BITS_H
#define ECN_BITS_H

#ifdef __cplusplus
extern "C" {
#endif

#define ECNBITS_NON		0 /* nōn-ECN-capable transport */
#define ECNBITS_ECT0		2 /* ECN-capable; L4S: legacy transport */
#define ECNBITS_ECT1		1 /* ECN-capable; L4S: L4S-aware transport */
#define ECNBITS_CE		3 /* congestion experienced */
#define ECNBITS_INVALID		4 /* no ECN bits received/extracted */

int ecnbits_setup(int socketfd);
/*XXX TODO: something to read, with/without peer address, calling ecnbits_rdmsg */

#ifdef __cplusplus
}
#endif

#endif
