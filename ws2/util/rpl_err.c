/*-
 * Copyright © 2020, 2021, 2023
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

#if defined(_WIN32) || defined(WIN32)
#define WIN32_LEAN_AND_MEAN
#include <Windows.h>
#pragma warning(push,1)
#include <winsock2.h>
#pragma warning(pop)
#endif

#include <errno.h>
#include <stdarg.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include "rpl_err.h"

#if defined(_WIN32) || defined(WIN32)
static inline LPWSTR rplerr_getprogname(void);
#define RPLERR_PROGNAME	rplerr_getprogname()
#define RPLERR_PROGFMT	"%S"	/* would be %ls in POSIX but… */
#else
extern const char *__progname;
#define RPLERR_PROGNAME	__progname
#define RPLERR_PROGFMT	"%s"
#endif

#if defined(_WIN32) || defined(WIN32)
static const char sEAF[] = "Address family not supported by protocol family";
#endif

#ifdef _MSC_VER
#pragma warning(disable:4706)
#endif

static void
vrpl_err(int docode, int code, int ws2code, const char *fmt, va_list ap)
{
#if defined(_WIN32) || defined(WIN32)
	/* gaaah! */
	char buf[88];
	const char *errstr = buf;
#endif

	fprintf(stderr, RPLERR_PROGFMT, RPLERR_PROGNAME);
	if (fmt) {
		fprintf(stderr, ": ");
		vfprintf(stderr, fmt, ap);
	}
	if (docode >= 2) {
#if defined(_WIN32) || defined(WIN32)
		if (ws2code != WSAEAFNOSUPPORT) {
			wchar_t *errwcs = NULL;

			if (FormatMessageW(FORMAT_MESSAGE_ALLOCATE_BUFFER | FORMAT_MESSAGE_FROM_SYSTEM,
			    NULL, ws2code, 0, (LPWSTR)&errwcs, 1, NULL) &&
			    errwcs) {
				wchar_t wc;
				size_t ofs = wcslen(errwcs);

				while (ofs > 0) {
					wc = errwcs[--ofs];
					if (wc == L'\r' || wc == L'\n')
						errwcs[ofs] = L'\0';
					else
						break;
				}
			}
			if (errwcs && *errwcs) {
				/* would be %ls in POSIX but… */
				fprintf(stderr, ": %S\n", errwcs);
				LocalFree(errwcs);
				return;
			}
			if (errwcs)
				LocalFree(errwcs);

			/* try as errno code */
			if (!strerror_s(buf, sizeof(buf), ws2code)) {
				fprintf(stderr, ": Winsock error %d: %s\n",
				    ws2code, buf);
				return;
			}

			/* no different errno code? */
			if (code == ws2code || !code) {
				fprintf(stderr, ": Winsock error %d\n",
				    ws2code);
				return;
			}

			/* render unknown WS2 error + different errno */
			if (code == WSAEAFNOSUPPORT)
				errstr = sEAF;
			else if (strerror_s(buf, sizeof(buf), code))
				snprintf(buf, sizeof(buf),
				    "Unknown errno 0x%08X", code);
			fprintf(stderr, ": Winsock error %d; %s\n",
			    ws2code, errstr);
			return;
		}
#endif
		code = ws2code ? ws2code : code;
	}
	if (docode) {
#if defined(_WIN32) || defined(WIN32)
		if (code == WSAEAFNOSUPPORT)
			errstr = sEAF;
		else if (strerror_s(buf, sizeof(buf), code))
			snprintf(buf, sizeof(buf),
			    "Unknown error 0x%08X", code);
		fprintf(stderr, ": %s\n", errstr);
#else
		fprintf(stderr, ": %s\n", strerror(code));
#endif
	} else
		putc('\n', stderr);
}

#if defined(_WIN32) || defined(WIN32)
void
ws2err(int eval, const char *fmt, ...)
{
	int code, ws2c;
	va_list ap;

	code = errno;
	ws2c = WSAGetLastError();
	va_start(ap, fmt);
	vrpl_err(2, code, ws2c, fmt, ap);
	va_end(ap);
	exit(eval);
}
#endif

void
err(int eval, const char *fmt, ...)
{
	int code;
	va_list ap;

	code = errno;
	va_start(ap, fmt);
	vrpl_err(1, code, 0, fmt, ap);
	va_end(ap);
	exit(eval);
}

void
errx(int eval, const char *fmt, ...)
{
	va_list ap;

	va_start(ap, fmt);
	vrpl_err(0, 0, 0, fmt, ap);
	va_end(ap);
	exit(eval);
}

#if defined(_WIN32) || defined(WIN32)
void
ws2warn(const char *fmt, ...)
{
	int code, ws2c;
	va_list ap;

	code = errno;
	ws2c = WSAGetLastError();
	va_start(ap, fmt);
	vrpl_err(2, code, ws2c, fmt, ap);
	va_end(ap);
}
#endif

void
warn(const char *fmt, ...)
{
	int code;
	va_list ap;

	code = errno;
	va_start(ap, fmt);
	vrpl_err(1, code, 0, fmt, ap);
	va_end(ap);
}

void
warnx(const char *fmt, ...)
{
	va_list ap;

	va_start(ap, fmt);
	vrpl_err(0, 0, 0, fmt, ap);
	va_end(ap);
}

#if defined(_WIN32) || defined(WIN32)
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

		goto first;
		do {
			if (*buf == L'/' || *buf == L'\\') {
				if (*++buf) {
 first:
					progname = buf;
				}
			} else
				++buf;
		} while (*buf);
		if ((buf = wcsrchr(progname, L'.')) &&
		    _wcsicmp(buf, L".exe") == 0)
			*buf = L'\0';
	}
	return (progname);
}
#endif
