// Copyright © 2021
//      Mihail Luchian <m.luchian@tarent.de>
// Licensor: Deutsche Telekom
//
// Provided that these terms and disclaimer and all copyright notices
// are retained or reproduced in an accompanying document, permission
// is granted to deal in this work without restriction, including un‐
// limited rights to use, publicly perform, distribute, sell, modify,
// merge, give away, or sublicence.
//
// This work is provided “AS IS” and WITHOUT WARRANTY of any kind, to
// the utmost extent permitted by applicable law, neither express nor
// implied; without malicious intent or gross negligence. In no event
// may a licensor, author or contributor be held liable for indirect,
// direct, other damage, loss, or other issues arising in any way out
// of dealing in the work, even if advised of the possibility of such
// damage or existence of a defect, except proven that it results out
// of said person’s immediate fault when using the work as intended.

using System;
using System.Net.Sockets;
using System.Runtime.InteropServices;

namespace ECNBits.Windows
{
    /// <summary>
    /// Constants defined in the ws2/inc/ecn-bitw.h header file
    /// </summary>
    public static class Constants
    {
        public const ushort ECNBITS_INVALID_BIT = (ushort)0x0100U;
        public const ushort ECNBITS_ISVALID_BIT = (ushort)0x0200U;

        public const byte ECNBITS_NON = 0;
        public const byte ECNBITS_ECT0 = 2;
        public const byte ECNBITS_ECT1 = 1;
        public const byte ECNBITS_CE = 3;

        public const string UNKNOWN = "??ECN?";

        public static readonly string[] ECNBITS_MEANINGS =
        {
            "nōn-ECN-capable transport",
            "ECN-capable; L4S: L4S-aware transport",
            "ECN-capable; L4S: legacy transport",
            "congestion experienced"
        };

        public static readonly string[] ECNBITS_SHORTNAMES =
        {
            "no ECN",
            "ECT(1)",
            "ECT(0)",
            "ECN CE"
        };
    }

    public static class Api
    {
        #region utils
        // Utility functions defined as Macros in ws2/inc/ecn-bitw.h

        public static bool IsValid(ushort ecnResult) => (ecnResult >> 8) == 0x02U;
        public static ushort GetBits(ushort ecnResult) => (ushort)(ecnResult & 0x03U);
        public static ushort GetDscp(ushort ecnResult) => (ushort)(ecnResult & 0xFCU);

        public static string GetDescription(ushort ecnResult) =>
            IsValid(ecnResult)
                ? Constants.ECNBITS_SHORTNAMES[GetBits(ecnResult)]
                : Constants.UNKNOWN;

        public static bool PrepFatal(int rv) => rv >= 2;

        #endregion
        #region wrappers
        // As the original C Api uses platform dependent types (types whose size depend on the current platform),
        // the decision was taken to wrap the original C functions in a more C# friendly Api.

        public static int Prep(Socket socket) {
            // Unfortunately, .net hardcodes the Winsock address family enum numbers
            // instead of using the native ones for the platform, so we’ll translate.
            int af;
            int rv;

            switch (socket.AddressFamily) {
            case AddressFamily.InterNetwork:
                af = 4;
                break;
            case AddressFamily.InterNetworkV6:
                af = 6;
                break;
            default:
                // the underlying library will throw WSAEAFNOSUPPORT
                af = 0;
                break;
            }

            rv = ecnhll_prep(socket.Handle, af);
            if (rv >= 2) {
                // this will use WSAGetLastError() to retrieve the error code
                throw new SocketException();
            }
            return rv;
        }

        public static int SocketToAddressFamily(Socket socket) =>
            ecnbits_stoaf(socket.Handle);

        public static long ReadMessage(Socket socket, ref Message message, int flags, ref ushort ecnResult) =>
            ecnbits_rdmsg(socket.Handle, ref message, flags, ref ecnResult).ToInt64();

        public static long ReceiveMessage(Socket socket, ref Message message, int flags, ref ushort ecnResult) =>
            ecnbits_recvmsg(socket.Handle, ref message, flags, ref ecnResult).ToInt64();

        public static long ReceiveFrom(
            Socket socket, IntPtr buffer, uint buflen, int flags,
            ref SockAddr srcAddr, ref int addrLen, ref ushort ecnResult) =>
            ecnbits_recvfrom(socket.Handle, buffer, new UIntPtr(buflen), flags, ref srcAddr, ref addrLen, ref ecnResult).ToInt64();

        public static long Receive(Socket socket, IntPtr buffer, uint bufLen, int flags, ref ushort ecnResult) =>
            ecnbits_recv(socket.Handle, buffer, new UIntPtr(bufLen), flags, ref ecnResult).ToInt64();

        public static long SendMessage(Socket socket, ref Message message, int flags) =>
            ecnws2_sendmsg(socket.Handle, ref message, flags).ToInt64();

        public static long ReceiveMessage(Socket socket, ref Message message, int flags) =>
            ecnws2_recvmsg(socket.Handle, ref message, flags).ToInt64();

        public static long Read(Socket socket, IntPtr buffer, uint bufLen, ref ushort ecnResult) =>
            Receive(socket, buffer, bufLen, 0, ref ecnResult);

        #endregion
        #region native
        // Note: the C# types UIntPtr and IntPtr represent the easiest way to wrap in C# size_t/SIZE_T and SSIZE_T
        // hence their use in the native api portion.

        private const string LIB_NAME = "ecn-bitw";

        [DllImport(LIB_NAME, CallingConvention = CallingConvention.Cdecl)]
        public static extern int ecnhll_prep(IntPtr socketHandle, int af);

        [DllImport(LIB_NAME, CallingConvention = CallingConvention.Cdecl)]
        public static extern int ecnbits_stoaf(IntPtr socketHandle);

        [DllImport(LIB_NAME, CallingConvention = CallingConvention.Cdecl)]
        public static extern IntPtr ecnbits_rdmsg(
            IntPtr socketHandle, ref Message message, int flags, ref ushort ecnResult);

        [DllImport(LIB_NAME, CallingConvention = CallingConvention.Cdecl)]
        public static extern IntPtr ecnbits_recvmsg
            (IntPtr socketHandle, ref Message message, int flags, ref ushort ecnResult);

        [DllImport(LIB_NAME, CallingConvention = CallingConvention.Cdecl)]
        public static extern IntPtr ecnbits_recvfrom(
            IntPtr socketHandle, IntPtr buffer, UIntPtr bufLen, int flags,
            ref SockAddr srcAddr, ref int addrLen, ref ushort ecnResult);

        [DllImport(LIB_NAME, CallingConvention = CallingConvention.Cdecl)]
        public static extern IntPtr ecnbits_recv(
            IntPtr socketHandle, IntPtr buffer, UIntPtr bufLen, int flags, ref ushort ecnResult);

        [DllImport(LIB_NAME, CallingConvention = CallingConvention.Cdecl)]
        public static extern IntPtr ecnws2_sendmsg(IntPtr socketHandle, ref Message message, int flags);

        [DllImport(LIB_NAME, CallingConvention = CallingConvention.Cdecl)]
        public static extern IntPtr ecnws2_recvmsg(IntPtr socketHandle, ref Message message, int flags);

        #endregion
    }

    /// <summary>
    /// Represents the C# version of the WSAMSG struct
    /// <see cref="https://docs.microsoft.com/en-us/windows/win32/api/ws2def/ns-ws2def-wsamsg"/>
    /// </summary>
    [StructLayout(LayoutKind.Sequential)]
    public struct Message
    {
        public IntPtr socketAddress;
        public uint addressLength;

        public IntPtr buffers;
        public uint count;

        public Buffer controlBuffer;
        public uint flags;
    }

    /// <summary>
    /// Represents the C# version of the WSABUF struct
    /// <see cref="https://docs.microsoft.com/en-us/windows/win32/api/ws2def/ns-ws2def-wsabuf"/>
    /// </summary>
    [StructLayout(LayoutKind.Sequential)]
    public struct Buffer
    {
        public uint length;
        public IntPtr pointer;
    }

    /// <summary>
    /// Represents the C# version of the sockaddr struct
    /// <see cref="https://docs.microsoft.com/en-us/windows/win32/winsock/sockaddr-2"/>
    /// </summary>
    [StructLayout(LayoutKind.Sequential)]
    public struct SockAddr
    {
        public const int DATA_SIZE = 14;

        public ushort family;
        [MarshalAs(UnmanagedType.ByValArray, SizeConst = DATA_SIZE)]
        public byte[] data;

        public SockAddr(ushort family)
        {
            this.family = family;
            data = new byte[DATA_SIZE];
        }
    }
}
