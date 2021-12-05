/*-
 * Copyright © 2021
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

// incurs a performance penalty, alternative is not available everywhere though
#define PROPER_DISPOSED_CHECK

using System;
using System.Net;
using System.Net.Sockets;
using System.Runtime.InteropServices;

namespace ECNBits {

#region ECNUtil
public static class ECNUtil {
	public readonly static System.Collections.Generic.IReadOnlyList<string> shortnames = new []{
		"no ECN", "ECT(1)", "ECT(0)", "ECN CE"
	};

	public static String Desc(Nullable<Byte> iptos) {
		if (iptos == null)
			return "??ECN?";
		return shortnames[(int)iptos & 3];
	}
}
#endregion

public static class ECNBits {
	#region wrappers
	/*
	 * Managed code wrapping native code in proper exception handling
	 */
	public static int Prepare(Socket socket) {
		int af;
		int rv;
		var sockfd = SocketHandle(socket);

		switch (socket.AddressFamily) {
		case AddressFamily.InterNetwork:
			af = 4;
			break;
		case AddressFamily.InterNetworkV6:
			af = 6;
			break;
		default:
			// native code will throw proper error
			af = 0;
			break;
		}

		rv = Unmanaged.ecnhll_prep(sockfd, af);
		if (rv >= 2)
			ThrowSocketException(socket);
		return rv;
	}

	public static int ReceiveFrom(Socket socket, Span<Byte> buffer,
	    SocketFlags flags, out EndPoint remoteEP,
	    out Nullable<Byte> iptos) {
#if !__MonoCS__
		Span<Unmanaged.ecnhll_rcv> p = stackalloc Unmanaged.ecnhll_rcv[1];
#else
		Span<Unmanaged.ecnhll_rcv> p;
		unsafe {
			Unmanaged.ecnhll_rcv *tmp = stackalloc Unmanaged.ecnhll_rcv[1];
			p = new Span<Unmanaged.ecnhll_rcv>(tmp, 1);
		}
#endif

		p.Clear();
		p[0].nbytes = (UInt32)buffer.Length;
		if ((flags & SocketFlags.OutOfBand) == SocketFlags.OutOfBand)
			p[0].flags |= 1;
		if ((flags & SocketFlags.Peek) == SocketFlags.Peek)
			p[0].flags |= 2;

		int rv = Unmanaged.ecnhll_recv(SocketHandle(socket),
		    ref buffer[0], ref p[0]);

		switch (rv) {
		case 4:
			remoteEP = new IPEndPoint((Int64)p[0].ipscope,
			    p[0].port);
			break;
		case 6:
			var b = MemoryMarshal.Cast<Unmanaged.ecnhll_rcv, Byte>(p);
#if !__MonoCS__
			var paddr = b.Slice(16, 16);
#else
			var paddr = b.Slice(16, 16).ToArray();
#endif
			var addr = new IPAddress(paddr, (Int64)p[0].ipscope);
			remoteEP = new IPEndPoint(addr, p[0].port);
			break;
		default:
			/* 0 = bad address family, -1 = error */
			ThrowSocketException(socket);
			// for Roslyn
			remoteEP = null;
			break;
		}
		if (p[0].tosvalid == 1)
			iptos = p[0].tosbyte;
		else
			iptos = null;
		return (int)p[0].nbytes;
	}
	#endregion

	#region helpers
	internal static IntPtr SocketHandle(Socket socket) {
#if PROPER_DISPOSED_CHECK
		// this calls ThrowIfDisposed(); the backtrace will show
		// GetSocketOption as first cause, but better than not doing it
		socket.GetSocketOption(SocketOptionLevel.Socket, SocketOptionName.Type);
		return socket.Handle;
#else
		SafeSocketHandle handle = socket.SafeHandle;

		// Socket.Disposed is unfortunately internal/private ☹
		if (handle.IsInvalid /* || socket.Disposed */)
			throw new ObjectDisposedException(socket.GetType().FullName);
		return handle.DangerousGetHandle();
#endif
	}

	internal static void ThrowSocketException(Socket socket) {
		var e = MonoSocketException.NewSocketException();

		// internal/private as well…
		//if (NetEventSource.Log.IsEnabled())
		//	NetEventSource.Error(socket, e);
		// here, we *really* must do…
		//socket.UpdateStatusAfterSocketError(e.SocketErrorCode);
		// … which we cannot because it’s internal/private ☹
		throw e;
	}
	#endregion
}

#region Socket extension
public static class SocketExtension {
	internal static EndPoint dummyEP;

	public static int ECNBitsPrepare(this Socket socket) =>
		ECNBits.Prepare(socket);

	public static int Receive(this Socket socket, Byte[] buffer,
	    Int32 offset, Int32 size, SocketFlags socketFlags,
	    out Nullable<Byte> iptos) =>
		ECNBits.ReceiveFrom(socket, new Span<Byte>(buffer, offset, size),
		    socketFlags, out dummyEP, out iptos);

	public static int Receive(this Socket socket, Byte[] buffer,
	    Int32 size, SocketFlags socketFlags,
	    out Nullable<Byte> iptos) =>
		ECNBits.ReceiveFrom(socket, new Span<Byte>(buffer, 0, size),
		    socketFlags, out dummyEP, out iptos);

	public static int Receive(this Socket socket, Byte[] buffer,
	    SocketFlags socketFlags,
	    out Nullable<Byte> iptos) =>
		ECNBits.ReceiveFrom(socket, new Span<Byte>(buffer),
		    socketFlags, out dummyEP, out iptos);

	public static int Receive(this Socket socket, Span<Byte> buffer,
	    out Nullable<Byte> iptos) =>
		ECNBits.ReceiveFrom(socket, buffer,
		    SocketFlags.None, out dummyEP, out iptos);

	public static int Receive(this Socket socket, Span<Byte> buffer,
	    SocketFlags socketFlags,
	    out Nullable<Byte> iptos) =>
		ECNBits.ReceiveFrom(socket, buffer,
		    socketFlags, out dummyEP, out iptos);

	public static int ReceiveFrom(this Socket socket, Byte[] buffer,
	    ref EndPoint remoteEP,
	    out Nullable<Byte> iptos) =>
		ECNBits.ReceiveFrom(socket, new Span<Byte>(buffer),
		    SocketFlags.None, out remoteEP, out iptos);

	public static int ReceiveFrom(this Socket socket, Byte[] buffer,
	    Int32 offset, Int32 size, SocketFlags socketFlags,
	    ref EndPoint remoteEP,
	    out Nullable<Byte> iptos) =>
		ECNBits.ReceiveFrom(socket, new Span<Byte>(buffer, offset, size),
		    socketFlags, out remoteEP, out iptos);

	public static int ReceiveFrom(this Socket socket, Byte[] buffer,
	    Int32 size, SocketFlags socketFlags, ref EndPoint remoteEP,
	    out Nullable<Byte> iptos) =>
		ECNBits.ReceiveFrom(socket, new Span<Byte>(buffer, 0, size),
		    socketFlags, out remoteEP, out iptos);

	public static int ReceiveFrom(this Socket socket, Byte[] buffer,
	    SocketFlags socketFlags, ref EndPoint remoteEP,
	    out Nullable<Byte> iptos) =>
		ECNBits.ReceiveFrom(socket, new Span<Byte>(buffer),
		    socketFlags, out remoteEP, out iptos);
}
#endregion

#region UdpClient extension
public static class UdpClientExtension {
	private const int MaxUDPSize = 0x10000;

	public static int ECNBitsPrepare(this UdpClient client) =>
		ECNBits.Prepare(client.Client);

	public static byte[] Receive(this UdpClient client,
	    ref IPEndPoint remoteEP, out Nullable<Byte> iptos) {
		EndPoint tempRemoteEP;
		byte[] buf = new byte[MaxUDPSize];
		int len;

		len = ECNBits.ReceiveFrom(client.Client, new Span<Byte>(buf),
		    SocketFlags.None, out tempRemoteEP, out iptos);
		if (len < MaxUDPSize)
			Array.Resize(ref buf, len);
		remoteEP = (IPEndPoint)tempRemoteEP;
		return buf;
	}
}
#endregion

/*
 * Mapping native (unmanaged) code; raw and unfiltered
 */
internal static class Unmanaged {
	#region windllpath
	[DllImport("kernel32", CharSet=CharSet.Unicode, SetLastError=true)]
	private static extern bool SetDllDirectory(string path);

	/*
	 * Once-only setup to load the correct DLL on Windows®
	 */
	static Unmanaged() {
		// assert correct types
		CheckTypes();

		// if not Windows assume LD_LIBRARY_PATH was set up properly
		if (!RuntimeInformation.IsOSPlatform(OSPlatform.Windows))
			return;
		// figure out arch-specific subdirectory
		string arch;
		string mydir = System.IO.Path.GetDirectoryName(System.Reflection.Assembly.GetEntryAssembly().Location);
		switch (RuntimeInformation.ProcessArchitecture) {
		case Architecture.X86:
			arch = "i386";
			break;
		case Architecture.X64:
			arch = "amd64";
			break;
		case Architecture.Arm:
			arch = "armhf";
			break;
		case Architecture.Arm64:
			arch = "arm64";
			break;
		default:
			System.Console.Write("ECNBits: unknown process architecture: ");
			System.Console.WriteLine(RuntimeInformation.ProcessArchitecture);
			return;
		}
		// search for Unmanaged.LIB (ecn-bitw.dll) in a subdirectory
		// below where this (managed) library (ecn-bitn.dll) is located
		if (!SetDllDirectory(System.IO.Path.Combine(mydir, arch)))
			throw new System.ComponentModel.Win32Exception();
	}
	#endregion

	#region interoptype
	/*
	 * Parameter block for ecnhll_recv
	 */
	[StructLayout(LayoutKind.Sequential)]
	internal struct ecnhll_rcv {
		public UInt32 nbytes;	// in+out
		public UInt32 flags;	// in
		public UInt32 ipscope;	// out (v4: address, v6: scope)
		public UInt16 port;	// out
		public Byte tosvalid;	// out (1 or 0)
		public Byte tosbyte;	// out
		public Byte addr0;	// out, [16]
		public Byte addr1;
		public Byte addr2;
		public Byte addr3;
		public Byte addr4;
		public Byte addr5;
		public Byte addr6;
		public Byte addr7;
		public Byte addr8;
		public Byte addr9;
		public Byte addrA;
		public Byte addrB;
		public Byte addrC;
		public Byte addrD;
		public Byte addrE;
		public Byte addrF;
	};

	/*
	 * Checks for that
	 */
#if !__MonoCS__
	internal static class BlittableHelper<T> where T : unmanaged {
		public static readonly bool IsBlittable = true;
	}
#endif

	private static void CheckTypes() {
		var t = typeof(ecnhll_rcv);

		if (
#if !__MonoCS__
		    !BlittableHelper<ecnhll_rcv>.IsBlittable ||
#endif
		    Marshal.OffsetOf(t, "nbytes") != (IntPtr)0 ||
		    Marshal.OffsetOf(t, "flags") != (IntPtr)4 ||
		    Marshal.OffsetOf(t, "ipscope") != (IntPtr)8 ||
		    Marshal.OffsetOf(t, "port") != (IntPtr)12 ||
		    Marshal.OffsetOf(t, "tosvalid") != (IntPtr)14 ||
		    Marshal.OffsetOf(t, "tosbyte") != (IntPtr)15 ||
		    Marshal.OffsetOf(t, "addr0") != (IntPtr)16 ||
		    Marshal.OffsetOf(t, "addrF") != (IntPtr)31 ||
		    Marshal.SizeOf(t) != 32)
			throw new BadImageFormatException("ecnhll_rcv size mismatch");
	}
	#endregion

	#region native
	/*
	 * Mapper for actual production code
	 */

	internal const string LIB = "ecn-bitw";

	[DllImport(LIB, ExactSpelling=true, CallingConvention=CallingConvention.Cdecl, SetLastError=true)]
	internal static extern int ecnhll_prep(IntPtr socketfd, int af);
	[DllImport(LIB, ExactSpelling=true, CallingConvention=CallingConvention.Cdecl, SetLastError=true)]
	internal static extern int ecnhll_recv(IntPtr socketfd, ref byte buf, ref ecnhll_rcv p);
	#endregion
}

}
