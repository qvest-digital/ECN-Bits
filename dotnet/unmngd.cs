using System.Net.Sockets;
using System.Runtime.InteropServices;

namespace ECNBits.DotNet.Experiment {

public class API {
	#region wrappers
	public static int Do() {
		int rv;

		rv = Unmanaged.ecnbits_example_ndo();
		if (rv >= 2) {
			// this will show 97 on Mono but clear the last error too…
			//System.Console.WriteLine(Marshal.GetLastWin32Error());
			// and _this_ fails on Mono because it needs Winsock codes, dotnet/Linux works, Windows works…
			//throw new SocketException(Marshal.GetLastWin32Error());
			// this will use WSAGetLastError() to retrieve the error code
			// (except on Mono…)
			throw new SocketException();
		}
		return rv;
	}
	#endregion
}

internal class Unmanaged {
	#region native
	private const string LIB = "cLib";

	[DllImport(LIB, CallingConvention=CallingConvention.Cdecl, SetLastError=true)]
	internal static extern int ecnbits_example_ndo();

	#endregion
}

}
