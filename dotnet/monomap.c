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
0
Error_SUCCESS	0	SUCCESS	SocketError.Success	0
EACCES
Error_EACCES	0x10002	EACCES	AccessDenied	10013
EADDRINUSE
Error_EADDRINUSE	0x10003	EADDRINUSE	AddressAlreadyInUse	10048
EADDRNOTAVAIL
Error_EADDRNOTAVAIL	0x10004	EADDRNOTAVAIL	AddressNotAvailable	10049
EAFNOSUPPORT
Error_EAFNOSUPPORT	0x10005	EAFNOSUPPORT	AddressFamilyNotSupported	10047
EAGAIN
Error_EAGAIN	0x10006	EAGAIN	WouldBlock	10035
EALREADY
Error_EALREADY	0x10007	EALREADY	AlreadyInProgress	10037
EBADF
Error_EBADF	0x10008	EBADF	OperationAborted	995	10038
EBUSY
Error_EBUSY	0x1000A	EBUSY	ERROR_LOCK_VIOLATION	33
ECANCELED
Error_ECANCELED	0x1000B	ECANCELED	OperationAborted	995
ECONNABORTED
Error_ECONNABORTED	0x1000D	ECONNABORTED	ConnectionAborted	10053
ECONNREFUSED
Error_ECONNREFUSED	0x1000E	ECONNREFUSED	ConnectionRefused	10061
ECONNRESET
Error_ECONNRESET	0x1000F	ECONNRESET	ConnectionReset	10054
EDESTADDRREQ
Error_EDESTADDRREQ	0x10011	EDESTADDRREQ	DestinationAddressRequired	10039
// not a precise match, best wecan do.
EDOM
Error_EDOM	0x10012	EDOM		10022
EEXIST
Error_EEXIST	0x10014	EEXIST	ERROR_FILE_EXISTS	80
EFAULT
Error_EFAULT	0x10015	EFAULT	Fault	10014
EHOSTUNREACH
Error_EHOSTUNREACH	0x10017	EHOSTUNREACH	HostUnreachable	10065
EINPROGRESS
Error_EINPROGRESS	0x1001A	EINPROGRESS	InProgress	10036
EINTR
Error_EINTR	0x1001B	EINTR	Interrupted	10004
EINVAL
Error_EINVAL	0x1001C	EINVAL	InvalidArgument	10022
// not a precise match, best we can do.
EIO
Error_EIO	0x1001D	EIO	ERROR_INVALID_HANDLE	6
EISCONN
Error_EISCONN	0x1001E	EISCONN	IsConnected	10056
EISDIR
Error_EISDIR	0x1001F	EISDIR	ERROR_CANNOT_MAKE	82
ELOOP
Error_ELOOP	0x10020	ELOOP	ERROR_CANT_RESOLVE_FILENAME	1921	10062
EMFILE
Error_EMFILE	0x10021	EMFILE	TooManyOpenSockets	10024
EMSGSIZE
Error_EMSGSIZE	0x10023	EMSGSIZE	MessageSize	10040
ENAMETOOLONG
Error_ENAMETOOLONG	0x10025	ENAMETOOLONG	ERROR_FILENAME_EXCED_RANGE	206	10063
ENETDOWN
Error_ENETDOWN	0x10026	ENETDOWN	NetworkDown	10050
ENETRESET
Error_ENETRESET	0x10027	ENETRESET	NetworkReset	10052
ENETUNREACH
Error_ENETUNREACH	0x10028	ENETUNREACH	NetworkUnreachable	10051
ENFILE
Error_ENFILE	0x10029	ENFILE	TooManyOpenSockets	10024
ENOBUFS
Error_ENOBUFS	0x1002A	ENOBUFS	NoBufferSpaceAvailable	10055
ENODEV
Error_ENODEV	0x1002C	ENODEV	ERROR_DEV_NOT_EXIST	55
ENOENT
Error_ENOENT	0x1002D	ENOENT	AddressNotAvailable	10049
ENOEXEC
Error_ENOEXEC	0x1002E	ENOEXEC	ERROR_BAD_FORMAT	11
ENOKEY
…		…	…		10051
ENOMEM
Error_ENOMEM	0x10031	ENOMEM		10055
ENONET
…		…	…		10051
ENOPROTOOPT
Error_ENOPROTOOPT	0x10033	ENOPROTOOPT	ProtocolOption	10042
ENOSPC
Error_ENOSPC	0x10034	ENOSPC	ERROR_HANDLE_DISK_FULL	39
ENOSR
…		…	…	…			10050
ENOSYS
Error_ENOSYS	0x10037	ENOSYS	ERROR_NOT_SUPPORTED	50
ENOTCONN
Error_ENOTCONN	0x10038	ENOTCONN	NotConnected	10057
ENOTDIR
Error_ENOTDIR	0x10039	ENOTDIR	ERROR_FILE_NOT_FOUND	2
#if ENOTEMPTY != EEXIST
// AIX defines this
ENOTEMPTY
Error_ENOTEMPTY	0x1003A	ENOTEMPTY	ERROR_DIR_NOT_EMPTY	145
#endif
ENOTSOCK
Error_ENOTSOCK	0x1003C	ENOTSOCK	NotSocket	10038
ENOTSUP
Error_ENOTSUP	0x1003D	ENOTSUP	OperationNotSupported	10045
ENOTTY
Error_ENOTTY	0x1003E	ENOTTY		10038
// not perfect, but closest match available
ENXIO
Error_ENXIO	0x1003F	ENXIO	HostNotFound	11001
EOPNOTSUPP
…		…	…	10045
EPERM
Error_EPERM	0x10042	EPERM	AccessDenied	10013
EPIPE
Error_EPIPE	0x10043	EPIPE	Shutdown	10058
EPROTONOSUPPORT
Error_EPROTONOSUPPORT	0x10045	EPROTONOSUPPORT	ProtocolNotSupported	10043
EPROTOTYPE
Error_EPROTOTYPE	0x10046	EPROTOTYPE	ProtocolType	10041
// best match I could find
ERESTART
…		…	…	ERROR_IO_PENDING	997
EROFS
Error_EROFS	0x10048	EROFS	ERROR_ACCESS_DENIED	5
ESOCKTNOSUPPORT
…		…	…	…		10044
ESPIPE
Error_ESPIPE	0x10049	ESPIPE	ERROR_SEEK	25
ETIMEDOUT
Error_ETIMEDOUT	0x1004D	ETIMEDOUT	TimedOut	10060
#ifdef ESOCKTNOSUPPORT
ESOCKTNOSUPPORT
Error_ESOCKTNOSUPPORT	0x1005E	ESOCKTNOSUPPORT	SocketNotSupported	10044
#endif
EPFNOSUPPORT
Error_EPFNOSUPPORT	0x10060	EPFNOSUPPORT	ProtocolFamilyNotSupported	10046
ESHUTDOWN
Error_ESHUTDOWN	0x1006C	ESHUTDOWN	Disconnecting	10101
EHOSTDOWN
Error_EHOSTDOWN	0x10070	EHOSTDOWN	HostDown	10064
ENODATA
Error_ENODATA	0x10071	ENODATA	NoData	11004
#if EOPNOTSUPP != ENOTSUP
EOPNOTSUPP
Error_EOPNOTSUPP	Error_ENOTSUP	0x1003D	(EOPNOTSUPP)	ENOTSUP	OperationNotSupported	10045
#endif
#if EWOULDBLOCK != EAGAIN
EWOULDBLOCK
Error_EWOULDBLOCK	Error_EAGAIN	0x10006	(EWOULDBLOCK)	EAGAIN	SocketError.WouldBlock	10035
#endif
default:
Error_ENONSTANDARD	0x1FFFF			SocketError.SocketError		(-1)



#endif
	return (e);
}
