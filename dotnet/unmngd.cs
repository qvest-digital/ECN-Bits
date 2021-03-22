using System.Net.Sockets;
using System.Runtime.InteropServices;

namespace ECNBits.DotNet.Experiment {

public class API {
	#region wrappers
	public static int Do() {
		int rv;

		rv = Unmanaged.ecnbits_example_ndo();
		if (rv >= 2) {
			// this will use WSAGetLastError() to retrieve the error code
			throw new SocketException();
		}
		return rv;
	}
	#endregion
}

internal class Unmanaged {
	#region native
	private const string LIB = "cLib";

	[DllImport(LIB, CallingConvention=CallingConvention.Cdecl)]
	internal static extern int ecnbits_example_ndo();

	#endregion
}

}
