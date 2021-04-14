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

using System;
using System.Net.Sockets;
using System.Runtime.InteropServices;

namespace ECNBits {

#region wrappers
/*
 * Managed code wrapping native code in proper exception handling
 */
public static class ECNBits {
	public static int Prepare(Socket socket) {
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
			// native code will throw proper error
			af = 0;
			break;
		}

		rv = Unmanaged.ecnhll_prep(socket.Handle, af);
		if (rv >= 2)
			throw MonoSocketException.NewSocketException();
		return rv;
	}
}
#endregion

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

	#region native
	/*
	 * Mapper for actual production code
	 */

	internal const string LIB = "ecn-bitw";

	[DllImport(LIB, ExactSpelling=true, CallingConvention=CallingConvention.Cdecl, SetLastError=true)]
	internal static extern int ecnhll_prep(IntPtr socketfd, int af);
	#endregion
}

}
