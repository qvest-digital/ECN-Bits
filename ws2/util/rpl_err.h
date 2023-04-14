/*-
 * Copyright © 2020, 2023
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

#ifndef RPL_ERR_H
#define RPL_ERR_H

#ifdef _MSC_VER
#define RPL_ERR_H_dead	__declspec(noreturn)
#elif defined(__GNUC__)
#define RPL_ERR_H_dead	__attribute__((__noreturn__))
#else
#define RPL_ERR_H_dead	/* nothing? */
#endif

#ifdef __cplusplus
extern "C" {
#endif

#if defined(_WIN32) || defined(WIN32)
RPL_ERR_H_dead
extern void ws2err(int, const char *, ...);
#else
#define ws2err err
#endif
RPL_ERR_H_dead
extern void err(int, const char *, ...);
RPL_ERR_H_dead
extern void errx(int, const char *, ...);

#if defined(_WIN32) || defined(WIN32)
extern void ws2warn(const char *, ...);
#else
#define ws2warn warn
#endif
extern void warn(const char *, ...);
extern void warnx(const char *, ...);

#ifdef __cplusplus
}
#endif

#endif
