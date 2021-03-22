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

/* building the library itself, additional compatibility/utilities */
#if !(defined(_WIN32) || defined(WIN32))
#define WSAEAFNOSUPPORT	EAFNOSUPPORT
#endif

ECNBITS_EXPORTAPI int
ecnbits_example_ndo(void)
{
#if defined(_WIN32) || defined(WIN32)
	WSASetLastError(WSAEAFNOSUPPORT);
#endif
	errno = WSAEAFNOSUPPORT;
	return (2);
}
