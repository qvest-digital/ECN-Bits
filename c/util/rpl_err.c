/*-
 * Copyright © 2020
 *	mirabilos <t.glaser@tarent.de>
 * Copyright © 2016, 2017
 *	mirabilos <m@mirbsd.org>
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

#ifdef _WIN32
#define WIN32_LEAN_AND_MEAN
#include <Windows.h>
#endif

#include <stdarg.h>
#include <stdio.h>
#include <stdlib.h>
#include "rpl_err.h"

#ifdef _WIN32
static LPWSTR rplerr_getprogname(void);
#define RPLERR_PROGNAME	rplerr_getprogname()
#define RPLERR_PROGFMT	"%S"	/* would be %ls in POSIX but… */
#else
extern const char *__progname;
#define RPLERR_PROGNAME	__progname
#define RPLERR_PROGFMT	"%s"
#endif

void
err(int eval, const char *fmt, ...)
{
	fprintf(stderr, RPLERR_PROGFMT ": %s: %s",
	    RPLERR_PROGNAME, fmt, /* stub */ __func__);
	exit(eval);
}

void
errx(int eval, const char *fmt, ...)
{
	fprintf(stderr, RPLERR_PROGFMT ": %s: %s",
	    RPLERR_PROGNAME, fmt, /* stub */ __func__);
	exit(eval);
}

void
warn(const char *fmt, ...)
{
	fprintf(stderr, RPLERR_PROGFMT ": %s: %s",
	    RPLERR_PROGNAME, fmt, /* stub */ __func__);
}

void
warnx(const char *fmt, ...)
{
	fprintf(stderr, RPLERR_PROGFMT ": %s: %s",
	    RPLERR_PROGNAME, fmt, /* stub */ __func__);
}

#ifdef _WIN32
static LPWSTR
rplerr_getprogname(void)
{
	static LPWSTR progname = NULL;

	if (!progname) {
		LPWSTR buf = NULL;
		DWORD len = 0, n;
		do {
			LPWSTR cp;

			len += MAX_PATH;
			if (!(cp = realloc(buf, len * sizeof(buf[0])))) {
				free(buf);
				return (L"(out of memory)");
			}
			buf = cp;
			if ((n = GetModuleFileNameW(NULL, buf, len)) == 0) {
				free(buf);
				return (L"(cannot get program name)");
			}
		} while (n >= len);
		progname = buf;
	}
	return (progname);
}
#endif
