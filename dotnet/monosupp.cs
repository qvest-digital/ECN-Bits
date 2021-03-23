using System;
using System.Net.Sockets;
using System.Runtime.InteropServices;

namespace ECNBits.DotNet.Experiment {

public class MonoSocketException : SocketException {
	#region IsRunningOnMono
	private static readonly Lazy<bool> IsThisMono = new Lazy<bool>(() => {
		return Type.GetType("Mono.Runtime") != null;
	});

	public static bool IsRunningOnMono() {
		return IsThisMono.Value;
	}
	#endregion

	#region native
	[DllImport(Unmanaged.LIB, CallingConvention=CallingConvention.Cdecl)]
	internal static extern int monosupp_errnomap(int errno);
	#endregion

	#region factory
	internal static SocketException NewSocketException() {
		if (!IsRunningOnMono())
			return new SocketException();
		int errno = Marshal.GetLastWin32Error();
		int winerr = monosupp_errnomap(errno);
		return new MonoSocketException(winerr);
	}
	#endregion

	#region implementation
	internal MonoSocketException(int errorCode) : base(errorCode) {
	}
	#endregion
}

}
