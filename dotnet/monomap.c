/*-
 * Copyright (c) .NET Foundation and Contributors
 * Copyright 2016 Microsoft
 * Copyright 2011 Xamarin Inc
 * Copyright © 2021
 *	mirabilos <t.glaser@tarent.de>
 * Licensor: Deutsche Telekom LLCTO
 *
 * All rights reserved.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a
 * copy of this software and associated documentation files (the "Software"),
 * to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
 * DEALINGS IN THE SOFTWARE.
 */

#include <sys/types.h>
#if defined(_WIN32) || defined(WIN32)
#pragma warning(push,1)
#include <winsock2.h>
#include <ws2tcpip.h>
#pragma warning(pop)
#else
#include <sys/socket.h>
#include <netinet/in.h>
#include <netinet/ip.h>
#endif
#include <errno.h>

/* stuff to make DLLs work; we offer the cdecl calling convention */
#if !defined(ECNBITS_WIN32_DLL)
#define ECNBITS_EXPORTAPI	/* nothing */
#elif !defined(ECNBITS_INTERNAL)
#define ECNBITS_EXPORTAPI	__declspec(dllimport)
#else
#define ECNBITS_EXPORTAPI	__declspec(dllexport)
#endif

/* map errno to Winsock error code */
ECNBITS_EXPORTAPI int
monosupp_errnomap(int e)
{
#if !(defined(_WIN32) || defined(WIN32))
	/*
	 * error code map derived from the .NET Runtime
	 * • src/libraries/Native/Unix/Common/pal_error_common.h
	 * • src/libraries/Common/src/Interop/Unix/Interop.Errors.cs
	 * • src/libraries/Common/src/System/Net/Sockets/SocketErrorPal.Unix.cs
	 * • src/libraries/System.Net.Primitives/src/System/Net/Sockets/SocketError.cs
	 * • src/mono/mono/metadata/w32error-unix.c
	 * • src/mono/mono/metadata/w32error.h (commit bc821616706 up to here)
	 * • src/mono/mono/metadata/w32socket-unix.c (commit 2b55b103e1d)
	 */

	// ENODATA is not defined in FreeBSD 10.3 but is defined in 11.0
	#if defined(__FreeBSD__) && !defined(ENODATA)
	#define ENODATA ENOATTR
	#endif

	switch (e) {
	case 0:
		return (0);
	case EACCES:
		return (10013);
	case EADDRINUSE:
		return (10048);
	case EADDRNOTAVAIL:
		return (10049);
	case EAFNOSUPPORT:
		return (10047);
	case EAGAIN:
		return (10035);
	case EALREADY:
		return (10037);
	case EBADF:
		return (10038);
	case EBUSY:
		return (33);
	case ECANCELED:
		return (995);
	case ECONNABORTED:
		return (10053);
	case ECONNREFUSED:
		return (10061);
	case ECONNRESET:
		return (10054);
	case EDESTADDRREQ:
		return (10039);
	case EDOM:
		// not a precise match, best wecan do.
		return (10022);
	case EEXIST:
		return (80);
	case EFAULT:
		return (10014);
	case EHOSTDOWN:
		return (10064);
	case EHOSTUNREACH:
		return (10065);
	case EINPROGRESS:
		return (10036);
	case EINTR:
		return (10004);
	case EINVAL:
		return (10022);
	case EIO:
		// not a precise match, best we can do.
		return (6);
	case EISCONN:
		return (10056);
	case EISDIR:
		return (82);
	case ELOOP:
		return (10062);
	case EMFILE:
		return (10024);
	case EMSGSIZE:
		return (10040);
	case ENAMETOOLONG:
		return (10063);
	case ENETDOWN:
		return (10050);
	case ENETRESET:
		return (10052);
	case ENETUNREACH:
		return (10051);
	case ENFILE:
		return (10024);
	case ENOBUFS:
		return (10055);
	case ENODATA:
		return (11004);
	case ENODEV:
		return (55);
	case ENOENT:
		return (10049);
	case ENOEXEC:
		return (11);
#ifdef ENOKEY
	case ENOKEY:
		return (10051);
#endif
	case ENOMEM:
		return (10055);
	case ENONET:
		return (10051);
	case ENOPROTOOPT:
		return (10042);
	case ENOSPC:
		return (39);
	case ENOSR:
		return (10050);
	case ENOSYS:
		return (50);
	case ENOTCONN:
		return (10057);
	case ENOTDIR:
		return (2);
#if ENOTEMPTY != EEXIST
	// AIX defines this
	case ENOTEMPTY:
		return (145);
#endif
	case ENOTSOCK:
		return (10038);
	case ENOTSUP:
		return (10045);
	case ENOTTY:
		return (10038);
	case ENXIO:
		// not perfect, but closest match available
		return (11001);
#if EOPNOTSUPP != ENOTSUP
	case EOPNOTSUPP:
		return (10045);
#endif
	case EPERM:
		return (10013);
	case EPFNOSUPPORT:
		return (10046);
	case EPIPE:
		return (10058);
	case EPROTONOSUPPORT:
		return (10043);
	case EPROTOTYPE:
		return (10041);
	case ERESTART:
		// best match I could find
		return (997);
	case EROFS:
		return (5);
	case ESHUTDOWN:
		return (10101);
#ifdef ESOCKTNOSUPPORT
	case ESOCKTNOSUPPORT:
		return (10044);
#endif
	case ESPIPE:
		return (25);
	case ETIMEDOUT:
		return (10060);
#if EWOULDBLOCK != EAGAIN
	case EWOULDBLOCK:
		return (10035);
#endif
	default:
		return (-1);
	}
#else
	return (e);
#endif
}
