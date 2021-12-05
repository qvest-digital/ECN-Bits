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

	public static int ReceiveFrom(Socket socket, Span<byte> buffer,
	    SocketFlags flags, out EndPoint remoteEP) {
		Span<Unmanaged.ecnhll_rcv> p = stackalloc Unmanaged.ecnhll_rcv[1];

p[0].addrD=1;

Console.WriteLine("nbytes<"+p[0].nbytes+"> flags<"+p[0].flags+"> ipscope<"+p[0].ipscope+
"> port=<"+p[0].port+"> tos<"+p[0].tosvalid+"|"+p[0].tosbyte+"> addr=<"+
p[0].addr0+" "+p[0].addr1+" "+p[0].addr2+" "+p[0].addr3+" "+
p[0].addr4+" "+p[0].addr5+" "+p[0].addr6+" "+p[0].addr7+" "+
p[0].addr8+" "+p[0].addr9+" "+p[0].addrA+" "+p[0].addrB+" "+
p[0].addrC+" "+p[0].addrD+" "+p[0].addrE+" "+p[0].addrF+">");

		p.Clear();

Console.WriteLine("nbytes<"+p[0].nbytes+"> flags<"+p[0].flags+"> ipscope<"+p[0].ipscope+
"> port=<"+p[0].port+"> tos<"+p[0].tosvalid+"|"+p[0].tosbyte+"> addr=<"+
p[0].addr0+" "+p[0].addr1+" "+p[0].addr2+" "+p[0].addr3+" "+
p[0].addr4+" "+p[0].addr5+" "+p[0].addr6+" "+p[0].addr7+" "+
p[0].addr8+" "+p[0].addr9+" "+p[0].addrA+" "+p[0].addrB+" "+
p[0].addrC+" "+p[0].addrD+" "+p[0].addrE+" "+p[0].addrF+">");

		p[0].addr0=6;

Console.WriteLine("nbytes<"+p[0].nbytes+"> flags<"+p[0].flags+"> ipscope<"+p[0].ipscope+
"> port=<"+p[0].port+"> tos<"+p[0].tosvalid+"|"+p[0].tosbyte+"> addr=<"+
p[0].addr0+" "+p[0].addr1+" "+p[0].addr2+" "+p[0].addr3+" "+
p[0].addr4+" "+p[0].addr5+" "+p[0].addr6+" "+p[0].addr7+" "+
p[0].addr8+" "+p[0].addr9+" "+p[0].addrA+" "+p[0].addrB+" "+
p[0].addrC+" "+p[0].addrD+" "+p[0].addrE+" "+p[0].addrF+">");

		Unmanaged.ecnhll_recv(SocketHandle(socket), ref buffer[0], ref p[0]);

Console.WriteLine("nbytes<"+p[0].nbytes+"> flags<"+p[0].flags+"> ipscope<"+p[0].ipscope+
"> port=<"+p[0].port+"> tos<"+p[0].tosvalid+"|"+p[0].tosbyte+"> addr=<"+
p[0].addr0+" "+p[0].addr1+" "+p[0].addr2+" "+p[0].addr3+" "+
p[0].addr4+" "+p[0].addr5+" "+p[0].addr6+" "+p[0].addr7+" "+
p[0].addr8+" "+p[0].addr9+" "+p[0].addrA+" "+p[0].addrB+" "+
p[0].addrC+" "+p[0].addrD+" "+p[0].addrE+" "+p[0].addrF+">");

		// v4
		//remoteEP = new IPEndPoint((Int64)p[0].ipscope, p[0].port);
		// v6
		var pbytes = MemoryMarshal.Cast<Unmanaged.ecnhll_rcv, byte>(p);
		var paddr = pbytes.Slice(16, 16);
		remoteEP = new IPEndPoint(new IPAddress(paddr, (Int64)p[0].ipscope), p[0].port);

		return -1;
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
	public static int ECNBitsPrepare(this Socket socket) =>
		ECNBits.Prepare(socket);
}
#endregion

#region UdpClient extension
public static class UdpClientExtension {
	public static int ECNBitsPrepare(this UdpClient client) =>
		ECNBits.Prepare(client.Client);
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
